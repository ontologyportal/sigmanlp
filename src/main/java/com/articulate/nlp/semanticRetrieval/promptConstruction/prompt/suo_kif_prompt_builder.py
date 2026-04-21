from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path


THIS_DIR = Path(__file__).resolve().parent
SEMANTIC_RETRIEVAL_DIR = THIS_DIR.parent.parent
RETRIEVAL_DIR = SEMANTIC_RETRIEVAL_DIR / "retrievalOrchestration" / "retrieval"
if str(RETRIEVAL_DIR) not in sys.path:
    sys.path.insert(0, str(RETRIEVAL_DIR))

from prompt_types import (
    PromptMessage,
    PromptVerbosity,
    SUOKIFPromptPayload,
    VALID_PROMPT_VERBOSITIES,
)
from retrieval_types import RetrievedCandidate, RetrievedMention, SentenceRetrievalResult


class SUOKIFPromptBuilderError(RuntimeError):
    """Raised when an SUO-KIF prompt payload cannot be built."""


@dataclass
class SUOKIFPromptBuilder:
    """Build a verbosity-aware SUO-KIF conversion prompt from retrieval output."""

    def build_prompt_payload(
        self,
        retrieval_result: SentenceRetrievalResult,
        verbosity: PromptVerbosity,
    ) -> SUOKIFPromptPayload:
        """Build the final two-message prompt payload."""

        if verbosity not in VALID_PROMPT_VERBOSITIES:
            raise SUOKIFPromptBuilderError(
                "verbosity must be one of: {0}.".format(
                    ", ".join(sorted(VALID_PROMPT_VERBOSITIES))
                )
            )
        if not isinstance(retrieval_result, SentenceRetrievalResult):
            raise SUOKIFPromptBuilderError(
                "retrieval_result must be a SentenceRetrievalResult instance."
            )

        system_message = _build_system_message(verbosity)
        user_message = _build_user_message(retrieval_result, verbosity)
        return SUOKIFPromptPayload(
            sentence=retrieval_result.sentence,
            verbosity=verbosity,
            messages=(
                PromptMessage(role="system", content=system_message),
                PromptMessage(role="user", content=user_message),
            ),
        )


def _build_system_message(verbosity: PromptVerbosity) -> str:
    if verbosity == "micro":
        return (
            "Convert English sentences into SUO-KIF. "
            "Use only the provided SUMO terms. "
            "Output only the final SUO-KIF."
        )
    return (
        "Convert English sentences into SUO-KIF.\n"
        "Use the provided SUMO terms when grounding mentions.\n"
        "Output only the final SUO-KIF."
    )


def _build_user_message(
    retrieval_result: SentenceRetrievalResult,
    verbosity: PromptVerbosity,
) -> str:
    if verbosity == "micro":
        return _build_micro_user_message(retrieval_result)

    semantic_structure = _build_semantic_structure_block(retrieval_result)
    candidate_terms = _build_candidate_terms_block(retrieval_result, verbosity)
    base_message = (
        "Convert the following sentence to SUO-KIF.\n\n"
        "Sentence:\n"
        "{0}\n\n"
        "Semantic structure:\n"
        "{1}\n\n"
        "Candidate formal terms:\n"
        "{2}"
    ).format(
        retrieval_result.sentence,
        semantic_structure,
        candidate_terms,
    )
    if verbosity == "low":
        return base_message
    return (
        base_message
        + "\n\n"
        + "Output requirements:\n"
        + "- Produce valid SUO-KIF.\n"
        + "- Use the candidate formal terms when grounding mentions.\n"
        + "- Preserve the entity/event distinction and modifier attachments when they matter semantically.\n"
        + "- Output only the final SUO-KIF."
    )


def _build_micro_user_message(retrieval_result: SentenceRetrievalResult) -> str:
    flat_terms = _build_micro_term_list(retrieval_result)
    escaped_sentence = _escape_micro_sentence(retrieval_result.sentence)
    return 'Convert the following sentence to SUO-KIF: Sentence:"{0}" Use terms from this list:[{1}]'.format(
        escaped_sentence,
        flat_terms,
    )


def _build_semantic_structure_block(retrieval_result: SentenceRetrievalResult) -> str:
    mention_by_id = {mention.mention_id: mention for mention in retrieval_result.retrieved_mentions}
    lines: list[str] = []
    for mention in sorted(retrieval_result.retrieved_mentions, key=_mention_sort_key):
        if mention.is_head:
            header = "- head {0}: {1}".format(mention.head_type or "unknown", mention.text)
        else:
            header = "- modifier {0}: {1}".format(mention.mention_type, mention.text)
        lines.append(header)

        if mention.attached_to is not None:
            parent = mention_by_id.get(mention.attached_to)
            attached_to = parent.text if parent is not None else mention.attached_to
            lines.append("  attached_to: {0}".format(attached_to))

        if mention.named_entity_label is not None:
            lines.append("  named_entity: {0}".format(mention.named_entity_label))

        if mention.is_head:
            modifier_texts = _collect_attached_modifier_texts(
                mention_id=mention.mention_id,
                mention_by_id=mention_by_id,
            )
            if modifier_texts:
                lines.append("  modifiers: {0}".format(", ".join(modifier_texts)))

    return "\n".join(lines) if lines else "(none)"


def _build_candidate_terms_block(
    retrieval_result: SentenceRetrievalResult,
    verbosity: PromptVerbosity,
) -> str:
    lines: list[str] = []
    for mention in sorted(retrieval_result.retrieved_mentions, key=_mention_sort_key):
        mention_header = "- mention: {0}".format(mention.text)
        if mention.is_head:
            mention_header += " ({0})".format(mention.head_type or "unknown")
        else:
            mention_header += " ({0} modifier)".format(mention.mention_type)
        lines.append(mention_header)

        if not mention.candidates:
            lines.append("  - (no candidates)")
            continue

        for candidate in mention.candidates:
            lines.append(_format_candidate_line(mention, candidate, verbosity))

    return "\n".join(lines) if lines else "(none)"


def _build_micro_term_list(retrieval_result: SentenceRetrievalResult) -> str:
    seen_sumo_types: set[str] = set()
    sumo_types: list[str] = []
    for mention in sorted(retrieval_result.retrieved_mentions, key=_mention_sort_key):
        for candidate in mention.candidates:
            if candidate.sumo_type in seen_sumo_types:
                continue
            seen_sumo_types.add(candidate.sumo_type)
            sumo_types.append(candidate.sumo_type)
    return ",".join(sumo_types)


def _escape_micro_sentence(sentence: str) -> str:
    return sentence.replace("\\", "\\\\").replace('"', '\\"')


def _format_candidate_line(
    mention: RetrievedMention,
    candidate: RetrievedCandidate,
    verbosity: PromptVerbosity,
) -> str:
    if verbosity == "low":
        return "  - {0}".format(candidate.sumo_type)

    if verbosity == "medium":
        return "  - {0} -> {1}".format(mention.text, candidate.sumo_type)

    term_formats = "; ".join(candidate.term_formats) if candidate.term_formats else "(none)"
    definition = candidate.definition or "(none)"
    return (
        "  - word: {0} | sumo_type: {1} | termFormats: {2} | definition: {3}"
    ).format(
        mention.text,
        candidate.sumo_type,
        term_formats,
        definition,
    )


def _collect_attached_modifier_texts(
    *,
    mention_id: str,
    mention_by_id: dict[str, RetrievedMention],
) -> list[str]:
    direct_children = [
        child
        for child in mention_by_id.values()
        if child.attached_to == mention_id
    ]
    direct_children.sort(key=_mention_sort_key)

    flattened: list[str] = []
    for child in direct_children:
        flattened.append(child.text)
        flattened.extend(
            _collect_attached_modifier_texts(
                mention_id=child.mention_id,
                mention_by_id=mention_by_id,
            )
        )
    return flattened


def _mention_sort_key(mention: RetrievedMention) -> tuple[int, int, str]:
    return (mention.token_start, mention.token_end, mention.mention_id)
