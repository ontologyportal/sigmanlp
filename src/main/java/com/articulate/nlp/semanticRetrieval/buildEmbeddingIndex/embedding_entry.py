from __future__ import annotations

from SUMOExportValidator import Concept

MAX_DEFINITION_CHARS = 2000


def build_canonical_text(concept: Concept) -> str:
    """Build the exact canonical embedding text block for one concept."""

    term_formats_value = "; ".join(_require_normalized_term_formats(concept))
    definition_value = _truncate_definition(concept.definition)
    return (
        "SUMO type: {0}\n".format(concept.sumo_type)
        + "termFormats: {0}\n".format(term_formats_value)
        + "Definition: {0}".format(definition_value)
    )


def _require_normalized_term_formats(concept: Concept) -> list[str]:
    """Require normalized aliases for embedding generation."""

    normalized_term_formats = concept.normalized_english_equivalents
    if normalized_term_formats is None:
        raise ValueError(
            "Embedding generation requires 'normalized_english_equivalents' for"
            + " sumo_type '{0}'. Run normalizeSUMOExport.py first.".format(concept.sumo_type)
        )

    for index, value in enumerate(normalized_term_formats):
        if not isinstance(value, str) or not value.strip():
            raise ValueError(
                "Field 'normalized_english_equivalents' contains an invalid value"
                + " at item {0} for sumo_type '{1}'.".format(index, concept.sumo_type)
            )
    return normalized_term_formats


def _truncate_definition(definition: str) -> str:
    """Trim long documentation strings to a deterministic embedding-safe size."""

    stripped_definition = definition.strip()
    if not stripped_definition:
        return ""
    if len(stripped_definition) <= MAX_DEFINITION_CHARS:
        return stripped_definition
    return stripped_definition[: MAX_DEFINITION_CHARS - 3].rstrip() + "..."
