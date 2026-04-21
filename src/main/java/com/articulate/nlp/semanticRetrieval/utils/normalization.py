from __future__ import annotations

import json
import re
import string
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, Iterator, Mapping, Union


PathLike = Union[str, Path]
_BOUNDARY_PUNCTUATION = re.escape(string.punctuation)
_BOUNDARY_PUNCTUATION_RE = re.compile(
    r"^[" + _BOUNDARY_PUNCTUATION + r"]+|[" + _BOUNDARY_PUNCTUATION + r"]+$"
)
_WHITESPACE_RE = re.compile(r"\s+")


@dataclass(frozen=True)
class NormalizationOptions:
    enable_lemmatization: bool = False
    unicode_normalization_form: str | None = None
    remove_stopwords: bool = False


@dataclass(frozen=True)
class NormalizedText:
    original_text: str
    normalized_text: str

    def to_dict(self) -> Dict[str, str]:
        return {
            "original_text": self.original_text,
            "normalized_text": self.normalized_text,
        }


class EnglishTextNormalizer:

    def __init__(self, options: NormalizationOptions | None = None) -> None:
        self.options = options or NormalizationOptions()
        self._validate_supported_options()

    def normalize_text(self, text: str) -> NormalizedText:
        if not isinstance(text, str):
            raise TypeError("normalize_text() expects a string input.")
        return NormalizedText(
            original_text=text,
            normalized_text=self._normalize_value(text),
        )

    def normalize_mention(self, mention: str) -> NormalizedText:
        return self.normalize_text(mention)

    def normalize_aliases(self, aliases: Iterable[str]) -> list[NormalizedText]:
        normalized_aliases: list[NormalizedText] = []
        seen_normalized_values: set[str] = set()
        for alias in aliases:
            normalized_alias = self.normalize_text(alias)
            if normalized_alias.normalized_text in seen_normalized_values:
                continue
            seen_normalized_values.add(normalized_alias.normalized_text)
            normalized_aliases.append(normalized_alias)
        return normalized_aliases

    def normalize_ontology_row(self, row: Mapping[str, Any]) -> Dict[str, Any]:
        if "english_equivalents" not in row:
            raise ValueError("Ontology row is missing 'english_equivalents'.")

        english_equivalents = row["english_equivalents"]
        if not isinstance(english_equivalents, list):
            raise TypeError("'english_equivalents' must be a list of strings.")

        normalized_row = dict(row)
        normalized_row["normalized_english_equivalents"] = [
            normalized_text.to_dict()
            for normalized_text in self.normalize_aliases(english_equivalents)
        ]
        return normalized_row

    def iter_normalized_ontology_rows(self, input_path: PathLike) -> Iterator[Dict[str, Any]]:
        path = Path(input_path)
        with path.open("r", encoding="utf-8") as handle:
            for line_number, raw_line in enumerate(handle, start=1):
                stripped_line = raw_line.strip()
                if not stripped_line:
                    continue
                try:
                    row = json.loads(stripped_line)
                except json.JSONDecodeError as exc:
                    raise ValueError(
                        "Invalid JSON on line {0} of {1}: {2}".format(
                            line_number,
                            path,
                            exc.msg,
                        )
                    ) from exc
                if not isinstance(row, dict):
                    raise TypeError(
                        "Expected a JSON object on line {0} of {1}.".format(
                            line_number,
                            path,
                        )
                    )
                yield self.normalize_ontology_row(row)

    def write_normalized_ontology_jsonl(self, input_path: PathLike, output_path: PathLike) -> None:
        output = Path(output_path)
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("w", encoding="utf-8") as handle:
            for row in self.iter_normalized_ontology_rows(input_path):
                handle.write(json.dumps(row, ensure_ascii=False))
                handle.write("\n")

    def _normalize_value(self, text: str) -> str:
        normalized = text.lower()
        normalized = normalized.strip()
        normalized = self._collapse_whitespace(normalized)
        normalized = _BOUNDARY_PUNCTUATION_RE.sub("", normalized)
        normalized = normalized.replace("-", " ").replace("_", " ")
        normalized = self._collapse_whitespace(normalized)
        return normalized

    @staticmethod
    def _collapse_whitespace(text: str) -> str:
        return _WHITESPACE_RE.sub(" ", text).strip()

    def _validate_supported_options(self) -> None:
        if self.options.enable_lemmatization:
            raise NotImplementedError("Lemmatization is not implemented yet.")
        if self.options.unicode_normalization_form is not None:
            raise NotImplementedError("Unicode normalization is not implemented yet.")
        if self.options.remove_stopwords:
            raise NotImplementedError("Stopword removal is not implemented yet.")
