from __future__ import annotations

import json
from pathlib import Path

from SUMOExportValidator import Concept, ConceptValidationError


class ConceptStoreLoadError(ValueError):
    """Raised when a concept store JSONL file cannot be loaded safely."""


def load_concept_store(jsonl_path: str) -> dict[str, Concept]:
    """Load a SUMO concept store from a JSONL file keyed by exact sumo_type."""

    path = Path(jsonl_path)
    if not path.exists():
        raise ConceptStoreLoadError("Concept JSONL file does not exist: {0}".format(path))
    if not path.is_file():
        raise ConceptStoreLoadError("Concept JSONL path is not a file: {0}".format(path))

    concept_store: dict[str, Concept] = {}
    first_seen_line_by_sumo_type: dict[str, int] = {}

    with path.open("r", encoding="utf-8") as handle:
        for line_number, raw_line in enumerate(handle, start=1):
            stripped_line = raw_line.strip()
            if not stripped_line:
                continue

            try:
                row = json.loads(stripped_line)
            except json.JSONDecodeError as exc:
                raise ConceptStoreLoadError(
                    "Invalid JSON on line {0} of {1}: {2}".format(
                        line_number,
                        path,
                        exc.msg,
                    )
                ) from exc

            try:
                concept = Concept.from_json_row(row)
            except ConceptValidationError as exc:
                raise ConceptStoreLoadError(
                    "Malformed concept row on line {0} of {1}: {2}".format(
                        line_number,
                        path,
                        exc,
                    )
                ) from exc

            if concept.sumo_type in concept_store:
                raise ConceptStoreLoadError(
                    "Duplicate sumo_type '{0}' on line {1} of {2};".format(
                        concept.sumo_type,
                        line_number,
                        path,
                    )
                    + " first seen on line {0}.".format(
                        first_seen_line_by_sumo_type[concept.sumo_type]
                    )
                )

            concept_store[concept.sumo_type] = concept
            first_seen_line_by_sumo_type[concept.sumo_type] = line_number

    return concept_store
