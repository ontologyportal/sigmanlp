from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path


THIS_DIR = Path(__file__).resolve().parent
SEMANTIC_RETRIEVAL_DIR = THIS_DIR.parent.parent
FAISS_DIR = SEMANTIC_RETRIEVAL_DIR / "buildEmbeddingIndex" / "faiss_index"
if str(FAISS_DIR) not in sys.path:
    sys.path.insert(0, str(FAISS_DIR))

from faiss_query import search_index
from faiss_store import load_faiss_index, load_metadata

from retrieval_types import RetrievedCandidate


class FaissCandidateSearchError(RuntimeError):
    """Raised when FAISS candidate search setup or execution is invalid."""


@dataclass
class FaissCandidateSearcher:
    """Load a FAISS index plus sidecar metadata and expose validated search."""

    index_path: str
    metadata_path: str

    def __post_init__(self) -> None:
        self._index = load_faiss_index(self.index_path)
        self._metadata = load_metadata(self.metadata_path)
        self._validate_loaded_resources()

    @property
    def embedding_dimension(self) -> int:
        return int(self._metadata.embedding_dimension)

    @property
    def embedding_model(self) -> str:
        return self._metadata.embedding_model

    @property
    def vector_count(self) -> int:
        return self._metadata.vector_count

    def search_candidates(
        self,
        query_embedding: list[float],
        top_k: int,
    ) -> list[RetrievedCandidate]:
        """Search the FAISS index and map results back to typed candidates."""

        if top_k <= 0:
            raise FaissCandidateSearchError("top_k must be greater than zero.")
        if len(query_embedding) != self.embedding_dimension:
            raise FaissCandidateSearchError(
                "Query embedding dimension {0} does not match FAISS index dimension {1}.".format(
                    len(query_embedding),
                    self.embedding_dimension,
                )
            )

        try:
            results = search_index(self._index, self._metadata, query_embedding, top_k)
        except Exception as exc:
            raise FaissCandidateSearchError(
                "Unable to search the FAISS index: {0}".format(exc)
            ) from exc

        return [
            RetrievedCandidate(
                sumo_type=result.sumo_type,
                score=result.score,
                term_formats=_extract_term_formats(result.canonical_text),
                definition=_extract_definition(result.canonical_text),
                rank=result.rank,
            )
            for result in results
        ]

    def _validate_loaded_resources(self) -> None:
        index_dimension = int(getattr(self._index, "d"))
        if index_dimension != self._metadata.embedding_dimension:
            raise FaissCandidateSearchError(
                "FAISS index dimension {0} does not match metadata embedding_dimension {1}.".format(
                    index_dimension,
                    self._metadata.embedding_dimension,
                )
            )
        vector_count = int(getattr(self._index, "ntotal"))
        if vector_count != self._metadata.vector_count:
            raise FaissCandidateSearchError(
                "FAISS index row count {0} does not match metadata vector_count {1}.".format(
                    vector_count,
                    self._metadata.vector_count,
                )
            )

        if len(self._metadata.rows) != vector_count:
            raise FaissCandidateSearchError(
                "Metadata row count {0} does not match FAISS row count {1}.".format(
                    len(self._metadata.rows),
                    vector_count,
                )
            )


def _extract_term_formats(canonical_text: str) -> list[str]:
    prefix = "termFormats:"
    for line in canonical_text.splitlines():
        if not line.startswith(prefix):
            continue
        raw_value = line[len(prefix):].strip()
        if not raw_value:
            return []
        return [
            term_format.strip()
            for term_format in raw_value.split(";")
            if term_format.strip()
        ]
    return []


def _extract_definition(canonical_text: str) -> str:
    prefix = "Definition:"
    for line in canonical_text.splitlines():
        if line.startswith(prefix):
            return line[len(prefix):].strip()
    return ""
