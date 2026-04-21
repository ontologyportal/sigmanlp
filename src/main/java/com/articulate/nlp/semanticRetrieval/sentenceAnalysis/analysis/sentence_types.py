from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal


HeadType = Literal["entity", "event"]
MentionType = Literal["pronoun", "noun", "verb", "adjective", "adverb"]
HeadMentionSource = Literal[
    "named_entity",
    "pronoun_rule",
    "noun_chunk",
    "noun_token",
    "root_verb",
    "verb_token",
]
ModifierMentionSource = Literal[
    "adjective_token",
    "predicate_adjective",
    "adverb_token",
]
MentionSource = HeadMentionSource | ModifierMentionSource


@dataclass(frozen=True)
class NounChunk:
    """One noun-phrase span detected by the sentence analyzer."""

    text: str
    token_start: int
    token_end: int
    root_token_index: int


@dataclass(frozen=True)
class NamedEntity:
    """One spaCy named-entity span aligned to token offsets."""

    text: str
    label: str
    token_start: int
    token_end: int


@dataclass(frozen=True)
class AnalyzedSentence:
    """Typed, inspection-friendly output of one sentence analysis pass."""

    text: str
    tokens: list[str]
    lemmas: list[str]
    pos_tags: list[str]
    dependency_labels: list[str]
    head_token_indices: list[int]
    noun_chunks: list[NounChunk]
    named_entities: list[NamedEntity]
    root_token_index: int

    def __post_init__(self) -> None:
        token_count = len(self.tokens)
        if token_count == 0:
            raise ValueError("AnalyzedSentence.tokens cannot be empty.")
        if len(self.lemmas) != token_count:
            raise ValueError("AnalyzedSentence.lemmas must align with tokens.")
        if len(self.pos_tags) != token_count:
            raise ValueError("AnalyzedSentence.pos_tags must align with tokens.")
        if len(self.dependency_labels) != token_count:
            raise ValueError("AnalyzedSentence.dependency_labels must align with tokens.")
        if len(self.head_token_indices) != token_count:
            raise ValueError("AnalyzedSentence.head_token_indices must align with tokens.")
        if self.root_token_index < 0 or self.root_token_index >= token_count:
            raise ValueError("AnalyzedSentence.root_token_index is out of bounds.")
        for head_token_index in self.head_token_indices:
            if head_token_index < 0 or head_token_index >= token_count:
                raise ValueError("AnalyzedSentence.head_token_indices contains an out-of-bounds index.")
        for named_entity in self.named_entities:
            if named_entity.token_start < 0 or named_entity.token_start >= token_count:
                raise ValueError("AnalyzedSentence.named_entities contains an out-of-bounds start.")
            if named_entity.token_end <= named_entity.token_start or named_entity.token_end > token_count:
                raise ValueError("AnalyzedSentence.named_entities contains an invalid end.")


@dataclass(frozen=True)
class ModifierMention:
    """One semantic modifier attached to a noun or verb head."""

    text: str
    normalized_text: str
    lemma: str | None
    mention_type: MentionType
    source: MentionSource
    token_start: int
    token_end: int
    modifiers: tuple["ModifierMention", ...] = field(default_factory=tuple)

    def span_key(self) -> tuple[int, int]:
        """Return the half-open token span used for deterministic deduplication."""

        return (self.token_start, self.token_end)


@dataclass(frozen=True)
class Mention:
    """One head mention ready for retrieval query construction."""

    text: str
    normalized_text: str
    lemma: str | None
    retrieval_target: str | None
    named_entity_label: str | None
    mention_type: MentionType
    source: MentionSource
    token_start: int
    token_end: int
    head_type: HeadType
    modifiers: tuple[ModifierMention, ...] = field(default_factory=tuple)

    def span_key(self) -> tuple[int, int]:
        """Return the half-open token span used for deterministic deduplication."""

        return (self.token_start, self.token_end)


def flatten_modifier_texts(modifiers: tuple[ModifierMention, ...]) -> list[str]:
    """Return modifier surface texts in stable depth-first order."""

    flattened: list[str] = []
    for modifier in modifiers:
        flattened.append(modifier.text)
        flattened.extend(flatten_modifier_texts(modifier.modifiers))
    return flattened
