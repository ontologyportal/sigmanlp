from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from faiss_store import EmbeddingArtifact, EmbeddingArtifactError


@dataclass(frozen=True)
class NormalizedEmbeddings:
    """Validated float32 embedding matrix ready for FAISS."""

    matrix: np.ndarray
    dimension: int
    vector_count: int


def validate_and_prepare_embeddings(artifact: EmbeddingArtifact) -> NormalizedEmbeddings:
    """Validate the saved artifact and return normalized float32 embeddings."""

    vector_count = len(artifact.sumo_types)
    if vector_count == 0:
        raise EmbeddingArtifactError("Embedding artifact contains no vectors.")
    if len(artifact.canonical_texts) != vector_count:
        raise EmbeddingArtifactError(
            "Artifact canonical_texts length does not match sumo_types length."
        )
    if len(artifact.embeddings) != vector_count:
        raise EmbeddingArtifactError(
            "Artifact embeddings length does not match sumo_types length."
        )

    _validate_unique_sumo_types(artifact.sumo_types)

    rows: list[list[float]] = []
    expected_dimension: int | None = None
    for row_index, vector in enumerate(artifact.embeddings):
        if not vector:
            raise EmbeddingArtifactError(
                "Embedding vector {0} is empty.".format(row_index)
            )
        if expected_dimension is None:
            expected_dimension = len(vector)
        elif len(vector) != expected_dimension:
            raise EmbeddingArtifactError(
                "Embedding vector {0} has dimension {1}, expected {2}.".format(
                    row_index,
                    len(vector),
                    expected_dimension,
                )
            )
        rows.append(vector)

    assert expected_dimension is not None
    if artifact.embedding_dimensions != expected_dimension:
        raise EmbeddingArtifactError(
            "Artifact embedding_dimensions={0} does not match actual dimension {1}.".format(
                artifact.embedding_dimensions,
                expected_dimension,
            )
        )

    matrix = np.asarray(rows, dtype=np.float32)
    if matrix.ndim != 2 or matrix.shape[1] != expected_dimension:
        raise EmbeddingArtifactError("Embedding matrix could not be formed correctly.")

    _validate_non_zero_rows(matrix)
    normalized_matrix = _normalize_rows(matrix)

    return NormalizedEmbeddings(
        matrix=np.ascontiguousarray(normalized_matrix, dtype=np.float32),
        dimension=expected_dimension,
        vector_count=vector_count,
    )


def build_faiss_index(embedding_matrix: np.ndarray) -> object:
    """Build an exact FAISS IndexFlatIP over unit-normalized vectors."""

    if embedding_matrix.ndim != 2:
        raise EmbeddingArtifactError("Embedding matrix must be 2-dimensional.")
    if embedding_matrix.dtype != np.float32:
        raise EmbeddingArtifactError("Embedding matrix must use float32 dtype.")

    faiss = _import_faiss()
    index = faiss.IndexFlatIP(embedding_matrix.shape[1])
    index.add(embedding_matrix)
    return index


def normalize_query_vector(query_matrix: np.ndarray, expected_dimension: int) -> np.ndarray:
    """Normalize a single query vector for cosine-style IndexFlatIP search."""

    if query_matrix.ndim != 2 or query_matrix.shape[0] != 1:
        raise EmbeddingArtifactError("Query matrix must be a single-row 2D array.")
    if query_matrix.shape[1] != expected_dimension:
        raise EmbeddingArtifactError(
            "Query vector dimension {0} does not match index dimension {1}.".format(
                query_matrix.shape[1],
                expected_dimension,
            )
        )
    _validate_non_zero_rows(query_matrix)
    return np.ascontiguousarray(_normalize_rows(query_matrix), dtype=np.float32)


def _normalize_rows(matrix: np.ndarray) -> np.ndarray:
    norms = np.linalg.norm(matrix, axis=1, keepdims=True)
    return matrix / norms


def _validate_non_zero_rows(matrix: np.ndarray) -> None:
    norms = np.linalg.norm(matrix, axis=1)
    zero_rows = np.where(norms == 0.0)[0]
    if zero_rows.size > 0:
        raise EmbeddingArtifactError(
            "Embedding vector {0} is all zeros before normalization.".format(
                int(zero_rows[0])
            )
        )


def _validate_unique_sumo_types(sumo_types: list[str]) -> None:
    seen: set[str] = set()
    for row_index, sumo_type in enumerate(sumo_types):
        if sumo_type in seen:
            raise EmbeddingArtifactError(
                "Duplicate sumo_type '{0}' in embedding artifact at row {1}.".format(
                    sumo_type,
                    row_index,
                )
            )
        seen.add(sumo_type)


def _import_faiss() -> object:
    try:
        import faiss  # type: ignore[import-not-found]
    except ImportError as exc:
        raise EmbeddingArtifactError(
            "FAISS Python bindings are not installed. Install faiss-cpu or faiss-gpu"
            + " before building the index."
        ) from exc
    return faiss
