from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path

from faiss_builder import build_faiss_index, validate_and_prepare_embeddings
from faiss_query import search_index
from faiss_store import (
    build_metadata_from_artifact,
    load_embedding_artifact,
    load_faiss_index,
    load_metadata,
    save_faiss_index,
    save_metadata,
)


LOGGER = logging.getLogger(__name__)


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments for FAISS build and demo query modes."""

    parser = argparse.ArgumentParser(
        description=(
            "Build or query a FAISS IndexFlatIP for SUMO concept embeddings."
        )
    )
    parser.add_argument(
        "--demo-query",
        action="store_true",
        help="Run demo query mode instead of build mode.",
    )
    parser.add_argument("--input-embeddings", help="Input embedding artifact pickle.")
    parser.add_argument("--output-index", help="Output FAISS index path.")
    parser.add_argument("--output-metadata", help="Output FAISS metadata JSON path.")
    parser.add_argument(
        "--input-index",
        help="Existing FAISS index path for demo query mode.",
    )
    parser.add_argument(
        "--input-metadata",
        help="Existing FAISS metadata JSON path for demo query mode.",
    )
    parser.add_argument(
        "--top-k",
        type=int,
        default=5,
        help="Top-k nearest neighbors to return. Default: %(default)s",
    )
    parser.add_argument(
        "--query-embedding-artifact",
        help="Embedding artifact pickle to source a demo query vector from.",
    )
    parser.add_argument(
        "--query-row",
        type=int,
        help="Row position in --query-embedding-artifact to use as the query vector.",
    )
    parser.add_argument(
        "--query-vector-json",
        help="Path to a JSON file containing a single numeric query vector list.",
    )
    return parser.parse_args()


def configure_logging() -> None:
    """Configure readable progress logging for FAISS operations."""

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )


def build_mode(args: argparse.Namespace) -> int:
    """Load embeddings, validate them, build IndexFlatIP, and save outputs."""

    if not args.input_embeddings:
        raise ValueError("--input-embeddings is required in build mode.")
    if not args.output_index:
        raise ValueError("--output-index is required in build mode.")
    if not args.output_metadata:
        raise ValueError("--output-metadata is required in build mode.")

    artifact = load_embedding_artifact(args.input_embeddings)
    prepared = validate_and_prepare_embeddings(artifact)

    LOGGER.info(
        "Building FAISS index for %d vectors with dimension %d",
        prepared.vector_count,
        prepared.dimension,
    )
    index = build_faiss_index(prepared.matrix)
    metadata = build_metadata_from_artifact(artifact, prepared.dimension)

    save_faiss_index(index, args.output_index)
    save_metadata(metadata, args.output_metadata)

    LOGGER.info("Saved FAISS index to %s", args.output_index)
    LOGGER.info("Saved FAISS metadata to %s", args.output_metadata)
    return 0


def demo_query_mode(args: argparse.Namespace) -> int:
    """Load an existing FAISS index and run a small top-k demo query."""

    if not args.input_index:
        raise ValueError("--input-index is required in demo query mode.")
    if not args.input_metadata:
        raise ValueError("--input-metadata is required in demo query mode.")
    if args.top_k <= 0:
        raise ValueError("--top-k must be greater than zero.")

    query_embedding = _load_demo_query_embedding(args)
    index = load_faiss_index(args.input_index)
    metadata = load_metadata(args.input_metadata)

    results = search_index(index, metadata, query_embedding, args.top_k)
    for result in results:
        print(
            "{0}. score={1:.6f} sumo_type={2}".format(
                result.rank,
                result.score,
                result.sumo_type,
            )
        )
        print("   canonical_text={0}".format(result.canonical_text))
    return 0


def _load_demo_query_embedding(args: argparse.Namespace) -> list[float]:
    if args.query_vector_json:
        return _load_query_vector_json(args.query_vector_json)

    if args.query_embedding_artifact:
        if args.query_row is None:
            raise ValueError(
                "--query-row is required when using --query-embedding-artifact."
            )
        artifact = load_embedding_artifact(args.query_embedding_artifact)
        if args.query_row < 0 or args.query_row >= len(artifact.embeddings):
            raise ValueError(
                "--query-row {0} is out of bounds for artifact with {1} vectors.".format(
                    args.query_row,
                    len(artifact.embeddings),
                )
            )
        return artifact.embeddings[args.query_row]

    raise ValueError(
        "Demo query mode requires either --query-vector-json or"
        + " --query-embedding-artifact plus --query-row."
    )


def _load_query_vector_json(path_str: str) -> list[float]:
    path = Path(path_str)
    if not path.exists():
        raise ValueError("Query vector file does not exist: {0}".format(path))
    if not path.is_file():
        raise ValueError("Query vector path is not a file: {0}".format(path))

    with path.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)
    if not isinstance(payload, list) or not payload:
        raise ValueError("Query vector JSON must be a non-empty numeric list.")

    query_vector: list[float] = []
    for index, item in enumerate(payload):
        if not isinstance(item, (int, float)) or isinstance(item, bool):
            raise ValueError(
                "Query vector item {0} must be numeric.".format(index)
            )
        query_vector.append(float(item))
    return query_vector


def _validate_args(args: argparse.Namespace) -> None:
    if args.demo_query:
        if args.input_embeddings or args.output_index or args.output_metadata:
            raise ValueError(
                "Build-mode arguments cannot be combined with --demo-query."
            )
    else:
        if args.input_index or args.input_metadata or args.query_embedding_artifact or args.query_row is not None or args.query_vector_json:
            raise ValueError(
                "Demo-query arguments cannot be used without --demo-query."
            )


def main() -> int:
    """CLI entrypoint for FAISS build and demo query workflows."""

    configure_logging()
    args = parse_args()
    try:
        _validate_args(args)
        if args.demo_query:
            return demo_query_mode(args)
        return build_mode(args)
    except Exception as exc:
        LOGGER.error("%s", exc)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
