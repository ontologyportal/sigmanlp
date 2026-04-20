from __future__ import annotations

import sys
from abc import ABC, abstractmethod
from dataclasses import replace
from pathlib import Path

from sentence_types import (
    AnalyzedSentence,
    HeadType,
    HeadMentionSource,
    Mention,
    NamedEntity,
    ModifierMention,
    ModifierMentionSource,
)


THIS_DIR = Path(__file__).resolve().parent
UTILS_DIR = THIS_DIR.parent.parent / "utils"
if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from normalization import EnglishTextNormalizer
from retrieval_query_builder import build_verb_retrieval_target


PRONOUN_POS_TAGS = {"PRON"}
NOUN_POS_TAGS = {"NOUN", "PROPN"}
VERB_POS_TAGS = {"VERB", "AUX"}
ADJECTIVE_POS_TAGS = {"ADJ"}
ADVERB_POS_TAGS = {"ADV"}
VERB_DEPENDENCY_LABELS = {"conj", "xcomp", "ccomp", "advcl", "relcl"}
ATTRIBUTIVE_ADJECTIVE_DEPENDENCY_LABELS = {"amod"}
PREDICATE_ADJECTIVE_DEPENDENCY_LABELS = {"acomp"}
ADVERB_DEPENDENCY_LABELS = {"advmod"}
ENTITY_SUBJECT_DEPENDENCY_LABELS = {"nsubj", "nsubjpass"}
WEAK_ADVERB_NORMALIZED_VALUES = {"not"}
HEAD_SOURCE_PRIORITY = {
    "named_entity": 0,
    "root_verb": 0,
    "verb_token": 1,
    "pronoun_rule": 2,
    "noun_chunk": 3,
    "noun_token": 4,
}


class MentionExtractor(ABC):
    """Stable mention-extraction interface for downstream retrieval code."""

    @abstractmethod
    def extract_mentions(self, analyzed_sentence: AnalyzedSentence) -> list[Mention]:
        """Extract retrieval-ready head mentions from an analyzed sentence."""


class RuleBasedMentionExtractor(MentionExtractor):
    """Deterministic head/modifier extractor for the sentence-analysis stage."""

    def __init__(self, normalizer: EnglishTextNormalizer | None = None) -> None:
        self._normalizer = normalizer or EnglishTextNormalizer()

    def extract_mentions(self, analyzed_sentence: AnalyzedSentence) -> list[Mention]:
        head_candidates: list[Mention] = []
        head_candidates.extend(self._extract_pronoun_heads(analyzed_sentence))
        head_candidates.extend(self._extract_noun_heads_from_chunks(analyzed_sentence))
        head_candidates.extend(self._extract_uncovered_noun_heads(analyzed_sentence))
        head_candidates.extend(self._extract_verb_heads(analyzed_sentence))

        deduplicated_heads = _deduplicate_head_mentions(head_candidates)
        return [self._attach_modifiers(analyzed_sentence, mention) for mention in deduplicated_heads]

    def _extract_pronoun_heads(self, analyzed_sentence: AnalyzedSentence) -> list[Mention]:
        mentions: list[Mention] = []
        for token_index, pos_tag in enumerate(analyzed_sentence.pos_tags):
            if pos_tag not in PRONOUN_POS_TAGS:
                continue
            mentions.append(
                self._build_head_mention(
                    analyzed_sentence=analyzed_sentence,
                    token_index=token_index,
                    mention_type="pronoun",
                    source="pronoun_rule",
                    head_type="entity",
                )
            )
        return mentions

    def _extract_noun_heads_from_chunks(self, analyzed_sentence: AnalyzedSentence) -> list[Mention]:
        mentions: list[Mention] = []
        seen_root_indices: set[int] = set()
        for noun_chunk in analyzed_sentence.noun_chunks:
            root_index = noun_chunk.root_token_index
            if root_index in seen_root_indices:
                continue
            if analyzed_sentence.pos_tags[root_index] not in NOUN_POS_TAGS:
                continue
            seen_root_indices.add(root_index)
            mentions.append(
                self._build_head_mention(
                    analyzed_sentence=analyzed_sentence,
                    token_index=root_index,
                    mention_type="noun",
                    source="noun_chunk",
                    head_type="entity",
                )
            )
        return mentions

    def _extract_uncovered_noun_heads(self, analyzed_sentence: AnalyzedSentence) -> list[Mention]:
        covered_token_indices = set()
        for noun_chunk in analyzed_sentence.noun_chunks:
            covered_token_indices.update(range(noun_chunk.token_start, noun_chunk.token_end))

        mentions: list[Mention] = []
        for token_index, pos_tag in enumerate(analyzed_sentence.pos_tags):
            if pos_tag not in NOUN_POS_TAGS:
                continue
            if token_index in covered_token_indices:
                continue
            mentions.append(
                self._build_head_mention(
                    analyzed_sentence=analyzed_sentence,
                    token_index=token_index,
                    mention_type="noun",
                    source="noun_token",
                    head_type="entity",
                )
            )
        return mentions

    def _extract_verb_heads(self, analyzed_sentence: AnalyzedSentence) -> list[Mention]:
        mentions: list[Mention] = []
        for token_index, pos_tag in enumerate(analyzed_sentence.pos_tags):
            if pos_tag not in VERB_POS_TAGS:
                continue
            if _is_semantically_weak_support_verb(analyzed_sentence, token_index):
                continue

            source = _resolve_verb_source(analyzed_sentence, token_index)
            if source is None:
                continue

            mentions.append(
                self._build_head_mention(
                    analyzed_sentence=analyzed_sentence,
                    token_index=token_index,
                    mention_type="verb",
                    source=source,
                    head_type="event",
                )
            )
        return mentions

    def _attach_modifiers(
        self,
        analyzed_sentence: AnalyzedSentence,
        mention: Mention,
    ) -> Mention:
        if mention.head_type == "entity":
            modifiers = self._collect_entity_modifiers(analyzed_sentence, mention)
        else:
            modifiers = self._collect_event_modifiers(analyzed_sentence, mention)
        return replace(mention, modifiers=tuple(modifiers))

    def _collect_entity_modifiers(
        self,
        analyzed_sentence: AnalyzedSentence,
        mention: Mention,
    ) -> list[ModifierMention]:
        head_token_index = mention.token_start
        modifiers: list[ModifierMention] = []
        seen_spans: set[tuple[int, int]] = set()

        for token_index in _iter_token_children(analyzed_sentence, head_token_index):
            if analyzed_sentence.pos_tags[token_index] not in ADJECTIVE_POS_TAGS:
                continue
            if analyzed_sentence.dependency_labels[token_index] not in ATTRIBUTIVE_ADJECTIVE_DEPENDENCY_LABELS:
                continue
            adjective_modifier = self._build_adjective_modifier(
                analyzed_sentence=analyzed_sentence,
                token_index=token_index,
                source="adjective_token",
            )
            modifiers.append(adjective_modifier)
            seen_spans.add(adjective_modifier.span_key())

        for adjective_token_index in _iter_predicate_adjectives_for_entity(analyzed_sentence, head_token_index):
            adjective_modifier = self._build_adjective_modifier(
                analyzed_sentence=analyzed_sentence,
                token_index=adjective_token_index,
                source="predicate_adjective",
            )
            if adjective_modifier.span_key() in seen_spans:
                continue
            modifiers.append(adjective_modifier)
            seen_spans.add(adjective_modifier.span_key())

        return sorted(modifiers, key=_modifier_sort_key)

    def _collect_event_modifiers(
        self,
        analyzed_sentence: AnalyzedSentence,
        mention: Mention,
    ) -> list[ModifierMention]:
        head_token_index = mention.token_start
        modifiers: list[ModifierMention] = []
        for token_index in _iter_token_children(analyzed_sentence, head_token_index):
            if analyzed_sentence.pos_tags[token_index] not in ADVERB_POS_TAGS:
                continue
            if not self._should_include_adverb(analyzed_sentence, token_index):
                continue
            modifiers.append(
                self._build_modifier_mention(
                    analyzed_sentence=analyzed_sentence,
                    token_index=token_index,
                    mention_type="adverb",
                    source="adverb_token",
                    modifiers=(),
                )
            )
        return sorted(modifiers, key=_modifier_sort_key)

    def _build_adjective_modifier(
        self,
        *,
        analyzed_sentence: AnalyzedSentence,
        token_index: int,
        source: ModifierMentionSource,
    ) -> ModifierMention:
        nested_modifiers: list[ModifierMention] = []
        for child_index in _iter_token_children(analyzed_sentence, token_index):
            if analyzed_sentence.pos_tags[child_index] not in ADVERB_POS_TAGS:
                continue
            if not self._should_include_adverb(analyzed_sentence, child_index):
                continue
            nested_modifiers.append(
                self._build_modifier_mention(
                    analyzed_sentence=analyzed_sentence,
                    token_index=child_index,
                    mention_type="adverb",
                    source="adverb_token",
                    modifiers=(),
                )
            )

        return self._build_modifier_mention(
            analyzed_sentence=analyzed_sentence,
            token_index=token_index,
            mention_type="adjective",
            source=source,
            modifiers=tuple(sorted(nested_modifiers, key=_modifier_sort_key)),
        )

    def _build_head_mention(
        self,
        *,
        analyzed_sentence: AnalyzedSentence,
        token_index: int,
        mention_type: str,
        source: HeadMentionSource,
        head_type: HeadType,
    ) -> Mention:
        token_text = analyzed_sentence.tokens[token_index]
        normalized_text = self._normalizer.normalize_mention(token_text).normalized_text
        token_lemma = analyzed_sentence.lemmas[token_index] or None
        overlapping_named_entity = _find_overlapping_named_entity(
            analyzed_sentence,
            token_start=token_index,
            token_end=token_index + 1,
        )
        retrieval_target = None
        if mention_type == "noun":
            retrieval_target = _build_noun_retrieval_target(analyzed_sentence, token_index)
        elif mention_type == "verb":
            retrieval_target = build_verb_retrieval_target(
                lemma=token_lemma,
                normalized_text=normalized_text,
            )
        mention_source = source
        named_entity_label = None
        if overlapping_named_entity is not None:
            named_entity_label = overlapping_named_entity.label
            mention_source = "named_entity"
            if head_type == "entity":
                retrieval_target = overlapping_named_entity.text
        return Mention(
            text=token_text,
            normalized_text=normalized_text,
            lemma=token_lemma,
            retrieval_target=retrieval_target,
            named_entity_label=named_entity_label,
            mention_type=mention_type,
            source=mention_source,
            token_start=token_index,
            token_end=token_index + 1,
            head_type=head_type,
        )

    def _build_modifier_mention(
        self,
        *,
        analyzed_sentence: AnalyzedSentence,
        token_index: int,
        mention_type: str,
        source: ModifierMentionSource,
        modifiers: tuple[ModifierMention, ...],
    ) -> ModifierMention:
        token_text = analyzed_sentence.tokens[token_index]
        normalized_text = self._normalizer.normalize_mention(token_text).normalized_text
        token_lemma = analyzed_sentence.lemmas[token_index] or None
        return ModifierMention(
            text=token_text,
            normalized_text=normalized_text,
            lemma=token_lemma,
            mention_type=mention_type,
            source=source,
            token_start=token_index,
            token_end=token_index + 1,
            modifiers=modifiers,
        )

    def _should_include_adverb(
        self,
        analyzed_sentence: AnalyzedSentence,
        token_index: int,
    ) -> bool:
        dependency_label = analyzed_sentence.dependency_labels[token_index]
        if dependency_label not in ADVERB_DEPENDENCY_LABELS:
            return False

        normalized_text = self._normalizer.normalize_mention(
            analyzed_sentence.tokens[token_index]
        ).normalized_text
        if not normalized_text:
            return False
        if normalized_text in WEAK_ADVERB_NORMALIZED_VALUES:
            return False
        return True


def extract_mentions(analyzed_sentence: AnalyzedSentence) -> list[Mention]:
    """Convenience wrapper for the default rule-based mention extractor."""

    return RuleBasedMentionExtractor().extract_mentions(analyzed_sentence)


def _resolve_verb_source(
    analyzed_sentence: AnalyzedSentence,
    token_index: int,
) -> str | None:
    if token_index == analyzed_sentence.root_token_index:
        return "root_verb"

    dependency_label = analyzed_sentence.dependency_labels[token_index]
    if dependency_label in VERB_DEPENDENCY_LABELS:
        return "verb_token"
    return None


def _find_overlapping_named_entity(
    analyzed_sentence: AnalyzedSentence,
    *,
    token_start: int,
    token_end: int,
) -> NamedEntity | None:
    for named_entity in analyzed_sentence.named_entities:
        if named_entity.token_start < token_end and token_start < named_entity.token_end:
            return named_entity
    return None


def _iter_predicate_adjectives_for_entity(
    analyzed_sentence: AnalyzedSentence,
    entity_token_index: int,
) -> list[int]:
    dependency_label = analyzed_sentence.dependency_labels[entity_token_index]
    if dependency_label not in ENTITY_SUBJECT_DEPENDENCY_LABELS:
        return []

    governing_token_index = analyzed_sentence.head_token_indices[entity_token_index]
    if governing_token_index == entity_token_index:
        return []
    if analyzed_sentence.lemmas[governing_token_index] != "be":
        return []

    adjective_token_indices: list[int] = []
    for token_index in _iter_token_children(analyzed_sentence, governing_token_index):
        if analyzed_sentence.pos_tags[token_index] not in ADJECTIVE_POS_TAGS:
            continue
        if analyzed_sentence.dependency_labels[token_index] not in PREDICATE_ADJECTIVE_DEPENDENCY_LABELS:
            continue
        adjective_token_indices.append(token_index)
    return adjective_token_indices


def _build_noun_retrieval_target(
    analyzed_sentence: AnalyzedSentence,
    head_token_index: int,
) -> str | None:
    noun_chunk = _find_noun_chunk_for_head(analyzed_sentence, head_token_index)
    if noun_chunk is None:
        return None

    compound_indices = _collect_left_compound_block(
        analyzed_sentence,
        head_token_index=head_token_index,
        chunk_start=noun_chunk.token_start,
    )
    if not compound_indices:
        return None

    target_indices = compound_indices + [head_token_index]
    return " ".join(analyzed_sentence.tokens[token_index] for token_index in target_indices)


def _find_noun_chunk_for_head(
    analyzed_sentence: AnalyzedSentence,
    head_token_index: int,
):
    for noun_chunk in analyzed_sentence.noun_chunks:
        if noun_chunk.root_token_index == head_token_index:
            return noun_chunk
    return None


def _collect_left_compound_block(
    analyzed_sentence: AnalyzedSentence,
    *,
    head_token_index: int,
    chunk_start: int,
) -> list[int]:
    compound_indices: list[int] = []
    accepted_head_indices = {head_token_index}
    cursor = head_token_index - 1

    while cursor >= chunk_start:
        if analyzed_sentence.pos_tags[cursor] not in NOUN_POS_TAGS:
            break
        if analyzed_sentence.dependency_labels[cursor] != "compound":
            break
        if analyzed_sentence.head_token_indices[cursor] not in accepted_head_indices:
            break

        compound_indices.append(cursor)
        accepted_head_indices.add(cursor)
        cursor -= 1

    compound_indices.reverse()
    return compound_indices


def _is_semantically_weak_support_verb(
    analyzed_sentence: AnalyzedSentence,
    token_index: int,
) -> bool:
    lemma = analyzed_sentence.lemmas[token_index]
    if lemma != "be":
        return False

    dependency_label = analyzed_sentence.dependency_labels[token_index]
    if dependency_label in {"aux", "auxpass"}:
        return True
    return _token_has_predicate_adjective_child(analyzed_sentence, token_index)


def _token_has_predicate_adjective_child(
    analyzed_sentence: AnalyzedSentence,
    token_index: int,
) -> bool:
    for child_index in _iter_token_children(analyzed_sentence, token_index):
        if analyzed_sentence.pos_tags[child_index] not in ADJECTIVE_POS_TAGS:
            continue
        if analyzed_sentence.dependency_labels[child_index] not in PREDICATE_ADJECTIVE_DEPENDENCY_LABELS:
            continue
        return True
    return False


def _iter_token_children(analyzed_sentence: AnalyzedSentence, head_token_index: int) -> list[int]:
    return [
        token_index
        for token_index, candidate_head_index in enumerate(analyzed_sentence.head_token_indices)
        if token_index != head_token_index and candidate_head_index == head_token_index
    ]


def _deduplicate_head_mentions(candidates: list[Mention]) -> list[Mention]:
    best_by_span: dict[tuple[int, int], Mention] = {}
    for mention in candidates:
        span_key = mention.span_key()
        existing = best_by_span.get(span_key)
        if existing is None:
            best_by_span[span_key] = mention
            continue
        if _head_mention_priority(mention) < _head_mention_priority(existing):
            best_by_span[span_key] = mention

    return sorted(best_by_span.values(), key=_head_mention_sort_key)


def _head_mention_priority(mention: Mention) -> int:
    return HEAD_SOURCE_PRIORITY.get(mention.source, 99)


def _head_mention_sort_key(mention: Mention) -> tuple[int, int, int, str]:
    return (
        mention.token_start,
        mention.token_end,
        _head_mention_priority(mention),
        mention.text,
    )


def _modifier_sort_key(modifier: ModifierMention) -> tuple[int, int, str]:
    return (
        modifier.token_start,
        modifier.token_end,
        modifier.text,
    )
