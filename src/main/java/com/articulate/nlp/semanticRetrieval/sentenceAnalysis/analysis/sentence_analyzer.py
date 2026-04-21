from __future__ import annotations

from abc import ABC, abstractmethod

from sentence_types import AnalyzedSentence, NamedEntity, NounChunk


DEFAULT_SPACY_MODEL = "en_core_web_sm"


class SentenceAnalyzer(ABC):
    """Stable sentence-analysis interface for downstream mention extraction."""

    @abstractmethod
    def analyze_sentence(self, sentence: str) -> AnalyzedSentence:
        """Analyze one sentence and return a typed debug-friendly structure."""


class SpacySentenceAnalyzer(SentenceAnalyzer):
    """spaCy-backed implementation of the sentence analysis interface."""

    def __init__(self, model_name: str = DEFAULT_SPACY_MODEL) -> None:
        self.model_name = model_name
        self._nlp = None

    def analyze_sentence(self, sentence: str) -> AnalyzedSentence:
        if not isinstance(sentence, str):
            raise TypeError("sentence must be a string.")
        if not sentence.strip():
            raise ValueError("sentence must be a non-empty string.")

        doc = self._get_nlp()(sentence)
        if len(doc) == 0:
            raise ValueError("spaCy returned no tokens for the input sentence.")

        noun_chunks = [
            NounChunk(
                text=chunk.text,
                token_start=chunk.start,
                token_end=chunk.end,
                root_token_index=chunk.root.i,
            )
            for chunk in _iter_noun_chunks(doc)
        ]
        named_entities = [
            NamedEntity(
                text=entity.text,
                label=entity.label_,
                token_start=entity.start,
                token_end=entity.end,
            )
            for entity in doc.ents
        ]
        root_token_index = next(
            (token.i for token in doc if token.dep_ == "ROOT"),
            0,
        )
        return AnalyzedSentence(
            text=sentence,
            tokens=[token.text for token in doc],
            lemmas=[token.lemma_ for token in doc],
            pos_tags=[token.pos_ for token in doc],
            dependency_labels=[token.dep_ for token in doc],
            head_token_indices=[token.head.i for token in doc],
            noun_chunks=noun_chunks,
            named_entities=named_entities,
            root_token_index=root_token_index,
        )

    def _get_nlp(self):
        if self._nlp is None:
            self._nlp = _load_spacy_pipeline(self.model_name)
        return self._nlp


def analyze_sentence(
    sentence: str,
    *,
    model_name: str = DEFAULT_SPACY_MODEL,
) -> AnalyzedSentence:
    """Convenience wrapper for the default spaCy sentence-analysis backend."""

    return SpacySentenceAnalyzer(model_name=model_name).analyze_sentence(sentence)


def _load_spacy_pipeline(model_name: str):
    try:
        import spacy  # type: ignore[import-not-found]
    except ImportError as exc:
        raise RuntimeError(
            "spaCy is not installed. Install spaCy and an English model such as"
            + " '{0}' before running sentence analysis.".format(model_name)
        ) from exc

    try:
        nlp = spacy.load(model_name)
    except OSError as exc:
        raise RuntimeError(
            (
                "spaCy model '{0}' is not installed. Install it with "
                "`python3 -m spacy download {0}`."
            ).format(model_name)
        ) from exc

    if "parser" not in nlp.pipe_names:
        raise RuntimeError(
            "spaCy model '{0}' must include the parser pipe for dependency labels"
            + " and noun chunks.".format(model_name)
        )
    if "ner" not in nlp.pipe_names:
        raise RuntimeError(
            "spaCy model '{0}' must include the ner pipe for named-entity handling.".format(
                model_name
            )
        )
    return nlp


def _iter_noun_chunks(doc):
    try:
        return list(doc.noun_chunks)
    except Exception as exc:
        raise RuntimeError(
            "The active spaCy pipeline cannot produce noun chunks."
        ) from exc
