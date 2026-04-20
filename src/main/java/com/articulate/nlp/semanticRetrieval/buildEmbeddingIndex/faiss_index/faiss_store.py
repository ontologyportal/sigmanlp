from __future__ import annotations

import json
import pickle
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np


class EmbeddingArtifactError(ValueError):
    """Raised when the saved embedding artifact is missing or malformed."""


class FaissStoreError(RuntimeError):
    """Raised when a FAISS index or metadata sidecar cannot be read or written."""


@dataclass(frozen=True)
class EmbeddingArtifact:
    """Typed representation of the saved embedding artifact from the previous stage."""

    input_jsonl: str
    embedding_model: str
    ollama_base_url: str
    created_at_utc: str
    sumo_types: list[str]
    canonical_texts: list[str]
    embeddings: list[list[float]]
    embedding_dimensions: int


@dataclass(frozen=True)
class FaissMetadataRow:
    """Metadata aligned exactly with one FAISS row position."""

    row: int
    sumo_type: str
    canonical_text: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "row": self.row,
            "sumo_type": self.sumo_type,
            "canonical_text": self.canonical_text,
        }


@dataclass(frozen=True)
class FaissMetadata:
    """Sidecar metadata stored separately from the FAISS vector index."""

    vector_count: int
    embedding_dimension: int
    metric: str
    normalized: bool
    embedding_model: str
    rows: list[FaissMetadataRow]

    def to_dict(self) -> dict[str, Any]:
        return {
            "vector_count": self.vector_count,
            "embedding_dimension": self.embedding_dimension,
            "metric": self.metric,
            "normalized": self.normalized,
            "embedding_model": self.embedding_model,
            "rows": [row.to_dict() for row in self.rows],
        }


def load_embedding_artifact(artifact_path: str) -> EmbeddingArtifact:
    """Load and validate a saved embedding artifact pickle."""

    path = Path(artifact_path)
    if not path.exists():
        raise EmbeddingArtifactError("Embedding artifact does not exist: {0}".format(path))
    if not path.is_file():
        raise EmbeddingArtifactError("Embedding artifact path is not a file: {0}".format(path))

    try:
        with path.open("rb") as handle:
            payload = pickle.load(handle)
    except Exception as exc:
        raise EmbeddingArtifactError(
            "Unable to read embedding artifact {0}: {1}".format(path, exc)
        ) from exc

    if not isinstance(payload, dict):
        raise EmbeddingArtifactError("Embedding artifact must contain a top-level dictionary.")

    input_jsonl = _require_string(payload, "input_jsonl")
    embedding_model = _require_string(payload, "embedding_model")
    ollama_base_url = _require_string(payload, "ollama_base_url")
    created_at_utc = _require_string(payload, "created_at_utc")
    sumo_types = _require_string_list(payload, "sumo_types")
    canonical_texts = _require_string_list(payload, "canonical_texts")
    embeddings = _require_embedding_list(payload, "embeddings")
    embedding_dimensions = _require_int(payload, "embedding_dimensions")

    return EmbeddingArtifact(
        input_jsonl=input_jsonl,
        embedding_model=embedding_model,
        ollama_base_url=ollama_base_url,
        created_at_utc=created_at_utc,
        sumo_types=sumo_types,
        canonical_texts=canonical_texts,
        embeddings=embeddings,
        embedding_dimensions=embedding_dimensions,
    )


def save_faiss_index(index: Any, output_index_path: str) -> None:
    """Persist a FAISS index to disk with clear errors."""

    faiss = _import_faiss()
    path = Path(output_index_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        faiss.write_index(index, str(path))
    except Exception as exc:
        raise FaissStoreError(
            "Unable to write FAISS index to {0}: {1}".format(path, exc)
        ) from exc


def load_faiss_index(index_path: str) -> Any:
    """Load a FAISS index from disk with clear errors."""

    faiss = _import_faiss()
    path = Path(index_path)
    if not path.exists():
        raise FaissStoreError("FAISS index file does not exist: {0}".format(path))
    if not path.is_file():
        raise FaissStoreError("FAISS index path is not a file: {0}".format(path))
    try:
        return faiss.read_index(str(path))
    except Exception as exc:
        raise FaissStoreError(
            "Unable to read FAISS index from {0}: {1}".format(path, exc)
        ) from exc


def save_metadata(metadata: FaissMetadata, output_metadata_path: str) -> None:
    """Save the FAISS sidecar metadata as readable JSON."""

    path = Path(output_metadata_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with path.open("w", encoding="utf-8") as handle:
            json.dump(metadata.to_dict(), handle, ensure_ascii=False, indent=2)
    except Exception as exc:
        raise FaissStoreError(
            "Unable to write FAISS metadata to {0}: {1}".format(path, exc)
        ) from exc


def load_metadata(metadata_path: str) -> FaissMetadata:
    """Load the FAISS sidecar metadata JSON."""

    path = Path(metadata_path)
    if not path.exists():
        raise FaissStoreError("FAISS metadata file does not exist: {0}".format(path))
    if not path.is_file():
        raise FaissStoreError("FAISS metadata path is not a file: {0}".format(path))

    try:
        with path.open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
    except Exception as exc:
        raise FaissStoreError(
            "Unable to read FAISS metadata from {0}: {1}".format(path, exc)
        ) from exc

    if not isinstance(payload, dict):
        raise FaissStoreError("FAISS metadata must be a top-level JSON object.")

    rows_payload = payload.get("rows")
    if not isinstance(rows_payload, list):
        raise FaissStoreError("FAISS metadata is missing a 'rows' list.")

    try:
        rows: list[FaissMetadataRow] = []
        for index, row_payload in enumerate(rows_payload):
            if not isinstance(row_payload, dict):
                raise FaissStoreError("Metadata row {0} must be a JSON object.".format(index))
            rows.append(
                FaissMetadataRow(
                    row=_require_int(row_payload, "row"),
                    sumo_type=_require_string(row_payload, "sumo_type"),
                    canonical_text=_require_string(row_payload, "canonical_text"),
                )
            )

        metadata = FaissMetadata(
            vector_count=_require_int(payload, "vector_count"),
            embedding_dimension=_require_int(payload, "embedding_dimension"),
            metric=_require_string(payload, "metric"),
            normalized=_require_bool(payload, "normalized"),
            embedding_model=_require_string(payload, "embedding_model"),
            rows=rows,
        )
    except EmbeddingArtifactError as exc:
        raise FaissStoreError(
            "FAISS metadata is malformed in {0}: {1}".format(path, exc)
        ) from exc

    if metadata.vector_count != len(metadata.rows):
        raise FaissStoreError(
            "FAISS metadata vector_count={0} does not match row count {1}.".format(
                metadata.vector_count,
                len(metadata.rows),
            )
        )
    return metadata


def build_metadata_from_artifact(
    artifact: EmbeddingArtifact,
    embedding_dimension: int,
) -> FaissMetadata:
    """Create ordered metadata rows aligned with FAISS row positions."""

    rows = [
        FaissMetadataRow(
            row=index,
            sumo_type=sumo_type,
            canonical_text=canonical_text,
        )
        for index, (sumo_type, canonical_text) in enumerate(
            zip(artifact.sumo_types, artifact.canonical_texts)
        )
    ]
    return FaissMetadata(
        vector_count=len(rows),
        embedding_dimension=embedding_dimension,
        metric="IndexFlatIP",
        normalized=True,
        embedding_model=artifact.embedding_model,
        rows=rows,
    )


def convert_query_vector(query_values: list[float]) -> np.ndarray:
    """Convert a query embedding list to a float32 row vector."""

    matrix = np.asarray([query_values], dtype=np.float32)
    if matrix.ndim != 2 or matrix.shape[0] != 1 or matrix.shape[1] == 0:
        raise FaissStoreError("Query embedding must be a non-empty 1D numeric vector.")
    return np.ascontiguousarray(matrix)


def _import_faiss() -> Any:
    try:
        import faiss  # type: ignore[import-not-found]
    except ImportError as exc:
        raise FaissStoreError(
            "FAISS Python bindings are not installed. Install faiss-cpu or faiss-gpu"
            + " before building or querying the index."
        ) from exc
    return faiss


def _require_string(payload: dict[str, Any], field_name: str) -> str:
    value = payload.get(field_name)
    if not isinstance(value, str):
        raise EmbeddingArtifactError(
            "Field '{0}' must be a string.".format(field_name)
        )
    return value


def _require_string_list(payload: dict[str, Any], field_name: str) -> list[str]:
    value = payload.get(field_name)
    if not isinstance(value, list):
        raise EmbeddingArtifactError(
            "Field '{0}' must be a list of strings.".format(field_name)
        )
    result: list[str] = []
    for index, item in enumerate(value):
        if not isinstance(item, str):
            raise EmbeddingArtifactError(
                "Field '{0}' item {1} must be a string.".format(field_name, index)
            )
        result.append(item)
    return result


def _require_embedding_list(payload: dict[str, Any], field_name: str) -> list[list[float]]:
    value = payload.get(field_name)
    if not isinstance(value, list):
        raise EmbeddingArtifactError(
            "Field '{0}' must be a list of embedding vectors.".format(field_name)
        )

    embeddings: list[list[float]] = []
    for vector_index, vector in enumerate(value):
        if not isinstance(vector, list):
            raise EmbeddingArtifactError(
                "Embedding {0} must be a list of numbers.".format(vector_index)
            )
        parsed_vector: list[float] = []
        for dimension_index, item in enumerate(vector):
            if not isinstance(item, (int, float)) or isinstance(item, bool):
                raise EmbeddingArtifactError(
                    "Embedding {0} dimension {1} must be numeric.".format(
                        vector_index,
                        dimension_index,
                    )
                )
            parsed_vector.append(float(item))
        embeddings.append(parsed_vector)
    return embeddings


def _require_int(payload: dict[str, Any], field_name: str) -> int:
    value = payload.get(field_name)
    if isinstance(value, bool) or not isinstance(value, int):
        raise EmbeddingArtifactError(
            "Field '{0}' must be an integer.".format(field_name)
        )
    return value


def _require_bool(payload: dict[str, Any], field_name: str) -> bool:
    value = payload.get(field_name)
    if not isinstance(value, bool):
        raise FaissStoreError("Field '{0}' must be a boolean.".format(field_name))
    return value
