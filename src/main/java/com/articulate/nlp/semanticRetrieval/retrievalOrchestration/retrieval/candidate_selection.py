from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from retrieval_types import CandidateSelectionTrace, RetrievedCandidate


CandidateSelectionMode = Literal["fixed_k", "score_band", "gap_drop"]
VALID_SELECTION_MODES = {"fixed_k", "score_band", "gap_drop"}


@dataclass(frozen=True)
class CandidateSelectionConfig:
    """Configuration for post-FAISS candidate selection."""

    initial_pool_k: int = 50
    selection_mode: CandidateSelectionMode = "score_band"
    final_k: int = 5
    min_k: int = 5
    max_k: int = 15
    score_band_delta: float = 0.04
    gap_drop_threshold: float = 0.03

    def __post_init__(self) -> None:
        if self.selection_mode not in VALID_SELECTION_MODES:
            raise ValueError(
                "selection_mode must be one of: {0}.".format(
                    ", ".join(sorted(VALID_SELECTION_MODES))
                )
            )
        if self.initial_pool_k <= 0:
            raise ValueError("initial_pool_k must be greater than zero.")
        if self.final_k <= 0:
            raise ValueError("final_k must be greater than zero.")
        if self.min_k <= 0:
            raise ValueError("min_k must be greater than zero.")
        if self.max_k < self.min_k:
            raise ValueError("max_k must be greater than or equal to min_k.")
        if self.score_band_delta < 0:
            raise ValueError("score_band_delta must be greater than or equal to zero.")
        if self.gap_drop_threshold < 0:
            raise ValueError("gap_drop_threshold must be greater than or equal to zero.")


def select_candidates(
    raw_candidates: list[RetrievedCandidate],
    config: CandidateSelectionConfig,
) -> tuple[list[RetrievedCandidate], CandidateSelectionTrace]:
    """Select final candidates from a larger raw FAISS pool."""

    if not raw_candidates:
        return [], CandidateSelectionTrace(
            mode=config.selection_mode,
            initial_pool_k=config.initial_pool_k,
            raw_candidate_count=0,
            selected_candidate_count=0,
            stop_reason="No raw candidates returned.",
            detail="FAISS returned an empty candidate pool.",
        )

    if config.selection_mode == "fixed_k":
        return _select_fixed_k(raw_candidates, config)
    if config.selection_mode == "score_band":
        return _select_score_band(raw_candidates, config)
    if config.selection_mode == "gap_drop":
        return _select_gap_drop(raw_candidates, config)
    raise ValueError("Unsupported selection_mode: {0}".format(config.selection_mode))


def build_direct_map_trace(
    *,
    raw_candidate_count: int,
) -> CandidateSelectionTrace:
    """Build a selection trace for non-FAISS direct mappings such as PERSON or pronoun rules."""

    return CandidateSelectionTrace(
        mode="direct_map",
        initial_pool_k=raw_candidate_count,
        raw_candidate_count=raw_candidate_count,
        selected_candidate_count=raw_candidate_count,
        stop_reason="Dense retrieval bypassed.",
        detail="Direct rule-based mapping returned the final candidate set.",
    )


def _select_fixed_k(
    raw_candidates: list[RetrievedCandidate],
    config: CandidateSelectionConfig,
) -> tuple[list[RetrievedCandidate], CandidateSelectionTrace]:
    selected_candidates = raw_candidates[: config.final_k]
    stop_reason = "Reached fixed_k limit."
    if len(raw_candidates) < config.final_k:
        stop_reason = "Raw candidate pool exhausted before fixed_k limit."
    return selected_candidates, CandidateSelectionTrace(
        mode="fixed_k",
        initial_pool_k=config.initial_pool_k,
        raw_candidate_count=len(raw_candidates),
        selected_candidate_count=len(selected_candidates),
        stop_reason=stop_reason,
        detail="Kept the top {0} candidates.".format(len(selected_candidates)),
    )


def _select_score_band(
    raw_candidates: list[RetrievedCandidate],
    config: CandidateSelectionConfig,
) -> tuple[list[RetrievedCandidate], CandidateSelectionTrace]:
    selected_count = min(len(raw_candidates), config.min_k)
    top_score = raw_candidates[0].score
    cutoff_score = top_score - config.score_band_delta
    stop_reason = "Raw candidate pool exhausted."

    while selected_count < len(raw_candidates) and selected_count < config.max_k:
        candidate = raw_candidates[selected_count]
        if candidate.score < cutoff_score:
            stop_reason = (
                "Score band cutoff reached at rank {0}.".format(candidate.rank)
            )
            break
        selected_count += 1

    if selected_count == config.max_k and selected_count < len(raw_candidates):
        stop_reason = "Reached max_k before score band cutoff."

    selected_candidates = raw_candidates[:selected_count]
    return selected_candidates, CandidateSelectionTrace(
        mode="score_band",
        initial_pool_k=config.initial_pool_k,
        raw_candidate_count=len(raw_candidates),
        selected_candidate_count=len(selected_candidates),
        stop_reason=stop_reason,
        detail=(
            "Kept at least {0}; retained candidates with score >= {1:.6f} up to max_k={2}."
        ).format(config.min_k, cutoff_score, config.max_k),
    )


def _select_gap_drop(
    raw_candidates: list[RetrievedCandidate],
    config: CandidateSelectionConfig,
) -> tuple[list[RetrievedCandidate], CandidateSelectionTrace]:
    selected_count = min(len(raw_candidates), config.min_k)
    stop_reason = "Raw candidate pool exhausted."
    observed_gap = None

    while selected_count < len(raw_candidates) and selected_count < config.max_k:
        previous_candidate = raw_candidates[selected_count - 1]
        current_candidate = raw_candidates[selected_count]
        gap = previous_candidate.score - current_candidate.score
        observed_gap = gap
        if gap > config.gap_drop_threshold:
            stop_reason = (
                "Gap drop cutoff reached after rank {0}.".format(previous_candidate.rank)
            )
            break
        selected_count += 1

    if selected_count == config.max_k and selected_count < len(raw_candidates):
        stop_reason = "Reached max_k before gap drop cutoff."

    selected_candidates = raw_candidates[:selected_count]
    if observed_gap is None:
        detail = "Kept the available candidates up to min_k={0}.".format(config.min_k)
    else:
        detail = (
            "Kept at least {0}; stopped when score gap exceeded {1:.6f}."
        ).format(config.min_k, config.gap_drop_threshold)
    return selected_candidates, CandidateSelectionTrace(
        mode="gap_drop",
        initial_pool_k=config.initial_pool_k,
        raw_candidate_count=len(raw_candidates),
        selected_candidate_count=len(selected_candidates),
        stop_reason=stop_reason,
        detail=detail,
    )
