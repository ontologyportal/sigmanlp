from __future__ import annotations

from dataclasses import dataclass

from faiss_builder import normalize_query_vector
from faiss_store import FaissMetadata, FaissMetadataRow, convert_query_vector


@dataclass(frozen=True)
class FaissQueryResult:
    """One ranked nearest-neighbor result mapped back to SUMO metadata."""

    rank: int
    score: float
    row: int
    sumo_type: str
    canonical_text: str


def search_index(
    index: object,
    metadata: FaissMetadata,
    query_embedding: list[float],
    top_k: int,
) -> list[FaissQueryResult]:
    """Search a FAISS index with one query embedding and return ranked metadata rows."""

    if top_k <= 0:
        raise ValueError("top_k must be greater than zero.")

    query_matrix = convert_query_vector(query_embedding)
    normalized_query = normalize_query_vector(query_matrix, metadata.embedding_dimension)

    scores, row_indices = index.search(normalized_query, top_k)
    results: list[FaissQueryResult] = []
    for rank, (score, row_index) in enumerate(zip(scores[0], row_indices[0]), start=1):
        if row_index < 0:
            continue
        metadata_row = _get_metadata_row(metadata, int(row_index))
        results.append(
            FaissQueryResult(
                rank=rank,
                score=float(score),
                row=metadata_row.row,
                sumo_type=metadata_row.sumo_type,
                canonical_text=metadata_row.canonical_text,
            )
        )
    return results


def _get_metadata_row(metadata: FaissMetadata, row_index: int) -> FaissMetadataRow:
    if row_index < 0 or row_index >= len(metadata.rows):
        raise ValueError(
            "FAISS returned row {0}, but metadata only has {1} rows.".format(
                row_index,
                len(metadata.rows),
            )
        )
    metadata_row = metadata.rows[row_index]
    if metadata_row.row != row_index:
        raise ValueError(
            "Metadata row alignment mismatch at FAISS row {0}; sidecar row is {1}.".format(
                row_index,
                metadata_row.row,
            )
        )
    return metadata_row
