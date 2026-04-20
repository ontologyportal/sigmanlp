from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path


THIS_DIR = Path(__file__).resolve().parent
SEMANTIC_RETRIEVAL_DIR = THIS_DIR.parent.parent
SENTENCE_ANALYSIS_DIR = SEMANTIC_RETRIEVAL_DIR / "sentenceAnalysis" / "analysis"
UTILS_DIR = SEMANTIC_RETRIEVAL_DIR / "utils"
for path in [SENTENCE_ANALYSIS_DIR, UTILS_DIR]:
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from mention_extractor import MentionExtractor, RuleBasedMentionExtractor
from normalization import EnglishTextNormalizer
from ollama_client import DEFAULT_OLLAMA_BASE_URL, OllamaEmbeddingClient
from retrieval_query_builder import build_context_aware_retrieval_query
from sentence_analyzer import DEFAULT_SPACY_MODEL, SentenceAnalyzer, SpacySentenceAnalyzer
from sentence_types import AnalyzedSentence, Mention, ModifierMention

from candidate_selection import (
    CandidateSelectionConfig,
    build_direct_map_trace,
    select_candidates,
)
from faiss_candidate_search import FaissCandidateSearcher
from retrieval_types import CandidateSelectionTrace, RetrievedCandidate, RetrievedMention, SentenceRetrievalResult


SemanticMention = Mention | ModifierMention
NAMED_ENTITY_SUMO_TYPE_MAP = {
    "PERSON": "Human",
}
PRONOUN_SUMO_TYPE_MAP = {
    "i": "Human",
    "me": "Human",
    "myself": "Human",
    "you": "Human",
    "yourself": "Human",
    "he": "Human",
    "him": "Human",
    "himself": "Human",
    "she": "Human",
    "her": "Human",
    "herself": "Human",
    "we": "GroupOfPeople",
    "us": "GroupOfPeople",
    "ourselves": "GroupOfPeople",
    "they": "GroupOfPeople",
    "them": "GroupOfPeople",
    "themselves": "GroupOfPeople",
    "yourselves": "GroupOfPeople",
}


class SemanticRetrievalOrchestrationError(RuntimeError):
    """Raised when structured sentence retrieval cannot be completed."""


@dataclass
class SemanticRetrievalOrchestrator:
    """Coordinate sentence analysis, query embedding, and FAISS retrieval."""

    faiss_searcher: FaissCandidateSearcher
    embedding_model: str
    ollama_base_url: str = DEFAULT_OLLAMA_BASE_URL
    sentence_analyzer: SentenceAnalyzer | None = None
    mention_extractor: MentionExtractor | None = None
    embedding_client: OllamaEmbeddingClient | None = None

    def __post_init__(self) -> None:
        if not self.embedding_model or not self.embedding_model.strip():
            raise SemanticRetrievalOrchestrationError(
                "embedding_model must be a non-empty string."
            )

        if self.embedding_model != self.faiss_searcher.embedding_model:
            raise SemanticRetrievalOrchestrationError(
                "Embedding model '{0}' does not match FAISS metadata model '{1}'.".format(
                    self.embedding_model,
                    self.faiss_searcher.embedding_model,
                )
            )

        self._sentence_analyzer = self.sentence_analyzer or SpacySentenceAnalyzer(
            model_name=DEFAULT_SPACY_MODEL
        )
        self._mention_extractor = self.mention_extractor or RuleBasedMentionExtractor()
        self._embedding_client = self.embedding_client or OllamaEmbeddingClient(
            base_url=self.ollama_base_url
        )
        self._normalizer = EnglishTextNormalizer()

    def retrieve_sentence(self, sentence: str, top_k: int) -> SentenceRetrievalResult:
        """Analyze one sentence and retrieve top-k SUMO candidates for heads and modifiers."""

        if not isinstance(sentence, str) or not sentence.strip():
            raise SemanticRetrievalOrchestrationError(
                "sentence must be a non-empty string."
            )

        selection_config = CandidateSelectionConfig(final_k=top_k)
        return self.retrieve_sentence_with_selection(
            sentence,
            selection_config=selection_config,
        )

    def retrieve_sentence_with_selection(
        self,
        sentence: str,
        *,
        selection_config: CandidateSelectionConfig,
    ) -> SentenceRetrievalResult:
        """Analyze one sentence and retrieve structured SUMO candidates with policy selection."""

        if not isinstance(sentence, str) or not sentence.strip():
            raise SemanticRetrievalOrchestrationError(
                "sentence must be a non-empty string."
            )

        analyzed_sentence = self._analyze_sentence(sentence)
        head_mentions = self._mention_extractor.extract_mentions(analyzed_sentence)

        retrieved_mentions: list[RetrievedMention] = []
        for head_mention in head_mentions:
            retrieved_mentions.extend(
                self._retrieve_head_and_modifiers(
                    semantic_mention=head_mention,
                    analyzed_sentence=analyzed_sentence,
                    attached_to=None,
                    selection_config=selection_config,
                )
            )

        return SentenceRetrievalResult(
            sentence=analyzed_sentence.text,
            retrieved_mentions=retrieved_mentions,
        )

    def _analyze_sentence(self, sentence: str) -> AnalyzedSentence:
        try:
            return self._sentence_analyzer.analyze_sentence(sentence)
        except Exception as exc:
            raise SemanticRetrievalOrchestrationError(
                "Unable to analyze sentence: {0}".format(exc)
            ) from exc

    def _retrieve_head_and_modifiers(
        self,
        *,
        semantic_mention: Mention,
        analyzed_sentence: AnalyzedSentence,
        attached_to: str | None,
        selection_config: CandidateSelectionConfig,
    ) -> list[RetrievedMention]:
        mention_id = _build_mention_id(semantic_mention)
        current = self._retrieve_one_mention(
            semantic_mention=semantic_mention,
            analyzed_sentence=analyzed_sentence,
            mention_id=mention_id,
            attached_to=attached_to,
            is_head=True,
            head_type=semantic_mention.head_type,
            selection_config=selection_config,
        )

        results = [current]
        for modifier in semantic_mention.modifiers:
            results.extend(
                self._retrieve_modifier_and_descendants(
                    semantic_mention=modifier,
                    analyzed_sentence=analyzed_sentence,
                    attached_to=mention_id,
                    selection_config=selection_config,
                )
            )
        return results

    def _retrieve_modifier_and_descendants(
        self,
        *,
        semantic_mention: ModifierMention,
        analyzed_sentence: AnalyzedSentence,
        attached_to: str,
        selection_config: CandidateSelectionConfig,
    ) -> list[RetrievedMention]:
        mention_id = _build_modifier_id(semantic_mention)
        current = self._retrieve_one_mention(
            semantic_mention=semantic_mention,
            analyzed_sentence=analyzed_sentence,
            mention_id=mention_id,
            attached_to=attached_to,
            is_head=False,
            head_type=None,
            selection_config=selection_config,
        )

        results = [current]
        for child_modifier in semantic_mention.modifiers:
            results.extend(
                self._retrieve_modifier_and_descendants(
                    semantic_mention=child_modifier,
                    analyzed_sentence=analyzed_sentence,
                    attached_to=mention_id,
                    selection_config=selection_config,
                )
            )
        return results

    def _retrieve_one_mention(
        self,
        *,
        semantic_mention: SemanticMention,
        analyzed_sentence: AnalyzedSentence,
        mention_id: str,
        attached_to: str | None,
        is_head: bool,
        head_type: str | None,
        selection_config: CandidateSelectionConfig,
    ) -> RetrievedMention:
        retrieval_query = build_context_aware_retrieval_query(semantic_mention, analyzed_sentence)
        candidates, selection_trace = self._resolve_candidates(
            semantic_mention=semantic_mention,
            retrieval_query=retrieval_query,
            selection_config=selection_config,
        )
        display_text = _resolve_display_text(semantic_mention)
        normalized_text = self._normalizer.normalize_mention(display_text).normalized_text

        return RetrievedMention(
            mention_id=mention_id,
            text=display_text,
            normalized_text=normalized_text,
            lemma=semantic_mention.lemma,
            named_entity_label=_get_named_entity_label(semantic_mention),
            mention_type=semantic_mention.mention_type,
            source=semantic_mention.source,
            retrieval_query=retrieval_query,
            candidates=candidates,
            selection_trace=selection_trace,
            attached_to=attached_to,
            is_head=is_head,
            head_type=head_type,
            token_start=semantic_mention.token_start,
            token_end=semantic_mention.token_end,
        )

    def _resolve_candidates(
        self,
        *,
        semantic_mention: SemanticMention,
        retrieval_query: str,
        selection_config: CandidateSelectionConfig,
    ) -> tuple[list[RetrievedCandidate], CandidateSelectionTrace]:
        direct_candidates = _build_direct_map_candidates(semantic_mention)
        if direct_candidates is not None:
            selection_trace = build_direct_map_trace(raw_candidate_count=len(direct_candidates))
            return direct_candidates, selection_trace

        query_embedding = self._embed_query(retrieval_query)
        raw_candidates = self.faiss_searcher.search_candidates(
            query_embedding,
            selection_config.initial_pool_k,
        )
        selected_candidates, selection_trace = select_candidates(raw_candidates, selection_config)
        return selected_candidates, selection_trace

    def _embed_query(self, retrieval_query: str) -> list[float]:
        try:
            query_embedding = self._embedding_client.embed_text(
                retrieval_query,
                self.embedding_model,
            )
        except Exception as exc:
            raise SemanticRetrievalOrchestrationError(
                "Unable to embed retrieval query: {0}".format(exc)
            ) from exc

        if len(query_embedding) != self.faiss_searcher.embedding_dimension:
            raise SemanticRetrievalOrchestrationError(
                "Query embedding dimension {0} does not match FAISS index dimension {1}.".format(
                    len(query_embedding),
                    self.faiss_searcher.embedding_dimension,
                )
            )
        return query_embedding


def _resolve_display_text(semantic_mention: SemanticMention) -> str:
    if isinstance(semantic_mention, Mention):
        if (
            semantic_mention.head_type == "entity"
            and semantic_mention.retrieval_target is not None
            and semantic_mention.retrieval_target.strip()
        ):
            return semantic_mention.retrieval_target
    return semantic_mention.text


def _get_named_entity_label(semantic_mention: SemanticMention) -> str | None:
    if isinstance(semantic_mention, Mention):
        return semantic_mention.named_entity_label
    return None


def _build_named_entity_candidates(
    semantic_mention: SemanticMention,
) -> list[RetrievedCandidate] | None:
    if not isinstance(semantic_mention, Mention):
        return None

    named_entity_label = semantic_mention.named_entity_label
    if named_entity_label is None:
        return None

    mapped_sumo_type = NAMED_ENTITY_SUMO_TYPE_MAP.get(named_entity_label)
    if mapped_sumo_type is None:
        return None

    return [
        RetrievedCandidate(
            sumo_type=mapped_sumo_type,
            score=1.0,
            term_formats=[],
            definition="",
            rank=1,
        )
    ]


def _build_pronoun_candidates(
    semantic_mention: SemanticMention,
) -> list[RetrievedCandidate] | None:
    if not isinstance(semantic_mention, Mention):
        return None
    if semantic_mention.mention_type != "pronoun":
        return None

    mapped_sumo_type = PRONOUN_SUMO_TYPE_MAP.get(semantic_mention.normalized_text)
    if mapped_sumo_type is None:
        return None

    return [
        RetrievedCandidate(
            sumo_type=mapped_sumo_type,
            score=1.0,
            term_formats=[],
            definition="",
            rank=1,
        )
    ]


def _build_direct_map_candidates(
    semantic_mention: SemanticMention,
) -> list[RetrievedCandidate] | None:
    return _build_named_entity_candidates(semantic_mention) or _build_pronoun_candidates(
        semantic_mention
    )


def _build_mention_id(semantic_mention: Mention) -> str:
    display_text = _sanitize_id_text(_resolve_display_text(semantic_mention))
    return "{0}:{1}:{2}:{3}".format(
        semantic_mention.head_type,
        semantic_mention.token_start,
        semantic_mention.token_end,
        display_text,
    )


def _build_modifier_id(semantic_mention: ModifierMention) -> str:
    display_text = _sanitize_id_text(semantic_mention.text)
    return "modifier:{0}:{1}:{2}:{3}".format(
        semantic_mention.mention_type,
        semantic_mention.token_start,
        semantic_mention.token_end,
        display_text,
    )


def _sanitize_id_text(text: str) -> str:
    return "-".join(text.strip().split()) or "mention"
