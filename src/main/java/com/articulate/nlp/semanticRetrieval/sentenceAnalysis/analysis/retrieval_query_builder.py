from __future__ import annotations

from sentence_types import AnalyzedSentence, Mention, ModifierMention


SemanticMention = Mention | ModifierMention


def build_context_aware_retrieval_query(
    mention: SemanticMention,
    analyzed_sentence: AnalyzedSentence,
) -> str:
    """Build the term-only retrieval string for one mention."""

    del analyzed_sentence

    if mention.mention_type == "verb":
        return _resolve_verb_target(mention)
    return _resolve_non_verb_target(mention)


def build_verb_retrieval_target(
    *,
    lemma: str | None,
    normalized_text: str,
) -> str:
    """Build a gerund-oriented retrieval target to better match SUMO termFormats."""

    base_text = _select_verb_base_text(lemma=lemma, normalized_text=normalized_text)
    if not base_text:
        return normalized_text

    if " " in base_text:
        return base_text

    if base_text.endswith("ing"):
        return base_text
    if base_text.endswith("ie"):
        return base_text[:-2] + "ying"
    if base_text.endswith("ee") or base_text.endswith("ye") or base_text.endswith("oe"):
        return base_text + "ing"
    if base_text.endswith("e") and len(base_text) > 2:
        return base_text[:-1] + "ing"
    if _should_double_final_consonant(base_text):
        return base_text + base_text[-1] + "ing"
    return base_text + "ing"


def _resolve_verb_target(mention: Mention) -> str:
    if mention.retrieval_target is not None and mention.retrieval_target.strip():
        return mention.retrieval_target
    if mention.normalized_text:
        return mention.normalized_text
    return mention.text


def _resolve_non_verb_target(mention: SemanticMention) -> str:
    retrieval_target = _get_retrieval_target(mention)
    if retrieval_target is not None and retrieval_target.strip():
        return retrieval_target
    return mention.text


def _select_verb_base_text(*, lemma: str | None, normalized_text: str) -> str:
    if lemma is not None and lemma.strip():
        return lemma.strip().lower()
    if normalized_text:
        return normalized_text
    return ""


def _get_retrieval_target(mention: SemanticMention) -> str | None:
    if isinstance(mention, Mention):
        return mention.retrieval_target
    return None


def _should_double_final_consonant(base_text: str) -> bool:
    if len(base_text) < 3:
        return False

    final_letter = base_text[-1]
    if final_letter in {"w", "x", "y"}:
        return False

    vowels = {"a", "e", "i", "o", "u"}
    return (
        base_text[-1] not in vowels
        and base_text[-2] in vowels
        and base_text[-3] not in vowels
    )
