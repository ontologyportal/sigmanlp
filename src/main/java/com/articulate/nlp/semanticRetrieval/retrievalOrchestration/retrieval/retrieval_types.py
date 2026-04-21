from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class RetrievedCandidate:
    """One ranked SUMO candidate returned by FAISS search."""

    sumo_type: str
    score: float
    term_formats: list[str]
    definition: str
    rank: int

    @property
    def canonical_text(self) -> str:
        """Backward-compatible formatted block for text renderers."""

        return (
            "SUMO type: {0}\n".format(self.sumo_type)
            + "termFormats: {0}\n".format("; ".join(self.term_formats))
            + "Definition: {0}".format(self.definition)
        )


@dataclass(frozen=True)
class CandidateSelectionTrace:
    """Debug-friendly summary of post-FAISS candidate selection."""

    mode: str
    initial_pool_k: int
    raw_candidate_count: int
    selected_candidate_count: int
    stop_reason: str
    detail: str


@dataclass(frozen=True)
class RetrievedMention:
    """One retrieved semantic mention with preserved attachment structure."""

    mention_id: str
    text: str
    normalized_text: str
    lemma: str | None
    named_entity_label: str | None
    mention_type: str
    source: str
    retrieval_query: str
    candidates: list[RetrievedCandidate]
    selection_trace: CandidateSelectionTrace
    attached_to: str | None
    is_head: bool
    head_type: str | None
    token_start: int
    token_end: int


@dataclass(frozen=True)
class SentenceRetrievalResult:
    """Structured retrieval output for one analyzed sentence."""

    sentence: str
    retrieved_mentions: list[RetrievedMention]
