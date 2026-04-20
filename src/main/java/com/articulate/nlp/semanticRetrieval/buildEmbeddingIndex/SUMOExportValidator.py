from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping


class ConceptValidationError(ValueError):
    """Raised when a JSONL ontology row cannot be converted into a Concept."""


@dataclass(frozen=True)
class Concept:
    """Typed in-memory representation of one SUMO concept row."""

    row_id: int
    parent_class: str | None
    sumo_type: str
    english_equivalents: list[str]
    normalized_english_equivalents: list[str] | None
    definition: str

    @classmethod
    def from_json_row(cls, row: Mapping[str, Any]) -> "Concept":
        """Validate and convert one decoded JSON object into a Concept."""

        if not isinstance(row, Mapping):
            raise ConceptValidationError("Concept row must be a JSON object.")

        return cls(
            row_id=_require_int(row, "row_id"),
            parent_class=_require_optional_symbol(row, "parent_class"),
            sumo_type=_require_symbol(row, "sumo_type"),
            english_equivalents=_require_string_list(row, "english_equivalents"),
            normalized_english_equivalents=_require_optional_normalized_aliases(
                row,
                "normalized_english_equivalents",
            ),
            definition=_require_definition(row, "definition"),
        )


def _require_int(row: Mapping[str, Any], field_name: str) -> int:
    if field_name not in row:
        raise ConceptValidationError("Missing required field '{0}'.".format(field_name))

    value = row[field_name]
    if isinstance(value, bool) or not isinstance(value, int):
        raise ConceptValidationError(
            "Field '{0}' must be an integer, got {1}.".format(
                field_name,
                type(value).__name__,
            )
        )
    return value


def _require_symbol(row: Mapping[str, Any], field_name: str) -> str:
    if field_name not in row:
        raise ConceptValidationError("Missing required field '{0}'.".format(field_name))

    value = row[field_name]
    if not isinstance(value, str):
        raise ConceptValidationError(
            "Field '{0}' must be a string, got {1}.".format(
                field_name,
                type(value).__name__,
            )
        )
    if not value:
        raise ConceptValidationError("Field '{0}' cannot be empty.".format(field_name))
    return value


def _require_optional_symbol(row: Mapping[str, Any], field_name: str) -> str | None:
    if field_name not in row:
        raise ConceptValidationError("Missing required field '{0}'.".format(field_name))

    value = row[field_name]
    if value is None:
        return None
    if not isinstance(value, str):
        raise ConceptValidationError(
            "Field '{0}' must be a string or null, got {1}.".format(
                field_name,
                type(value).__name__,
            )
        )
    if not value:
        raise ConceptValidationError("Field '{0}' cannot be an empty string.".format(field_name))
    return value


def _require_string_list(row: Mapping[str, Any], field_name: str) -> list[str]:
    if field_name not in row:
        raise ConceptValidationError("Missing required field '{0}'.".format(field_name))

    value = row[field_name]
    if not isinstance(value, list):
        raise ConceptValidationError("Field '{0}' must be a list of strings.".format(field_name))

    result: list[str] = []
    for index, item in enumerate(value):
        if not isinstance(item, str):
            raise ConceptValidationError(
                "Field '{0}' item {1} must be a string, got {2}.".format(
                    field_name,
                    index,
                    type(item).__name__,
                )
            )
        result.append(item)
    return result


def _require_optional_normalized_aliases(
    row: Mapping[str, Any],
    field_name: str,
) -> list[str] | None:
    if field_name not in row or row[field_name] is None:
        return None

    value = row[field_name]
    if not isinstance(value, list):
        raise ConceptValidationError(
            "Field '{0}' must be a list of strings when present.".format(field_name)
        )

    normalized_aliases: list[str] = []
    for index, item in enumerate(value):
        if isinstance(item, str):
            normalized_aliases.append(item)
            continue
        if isinstance(item, Mapping):
            normalized_text = item.get("normalized_text")
            if isinstance(normalized_text, str):
                normalized_aliases.append(normalized_text)
                continue
        raise ConceptValidationError(
            "Field '{0}' item {1} must be a string".format(field_name, index)
            + " or a mapping with 'normalized_text'."
        )
    return normalized_aliases


def _require_definition(row: Mapping[str, Any], field_name: str) -> str:
    if field_name not in row:
        raise ConceptValidationError("Missing required field '{0}'.".format(field_name))

    value = row[field_name]
    if value is None:
        return ""
    if not isinstance(value, str):
        raise ConceptValidationError(
            "Field '{0}' must be a string or null, got {1}.".format(
                field_name,
                type(value).__name__,
            )
        )
    return value
