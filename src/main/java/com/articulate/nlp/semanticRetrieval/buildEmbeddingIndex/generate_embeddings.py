from __future__ import annotations

import argparse
import logging
import pickle
import re
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from embedding_entry import build_canonical_text
from SUMOExportLoader import load_concept_store


SCRIPT_DIR = Path(__file__).resolve().parent
UTILS_DIR = SCRIPT_DIR.parent / "utils"
if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from ollama_client import DEFAULT_OLLAMA_BASE_URL, OllamaEmbeddingClient


LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True)
class EmbeddingArtifact:
    """Serialized output saved after embedding generation."""

    input_jsonl: str
    embedding_model: str
    ollama_base_url: str
    created_at_utc: str
    sumo_types: list[str]
    canonical_texts: list[str]
    embeddings: list[list[float]]
    embedding_dimensions: int

    def to_dict(self) -> dict[str, Any]:
        """Convert the artifact to a pickle-ready plain dictionary."""

        return {
            "input_jsonl": self.input_jsonl,
            "embedding_model": self.embedding_model,
            "ollama_base_url": self.ollama_base_url,
            "created_at_utc": self.created_at_utc,
            "sumo_types": self.sumo_types,
            "canonical_texts": self.canonical_texts,
            "embeddings": self.embeddings,
            "embedding_dimensions": self.embedding_dimensions,
        }


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments for embedding generation."""

    parser = argparse.ArgumentParser(
        description=(
            "Generate Ollama embeddings for SUMO concept JSONL rows and save"
            + " a reusable artifact for later FAISS indexing."
        )
    )
    parser.add_argument("--input-jsonl", required=True, help="Path to the concept JSONL file.")
    parser.add_argument(
        "--embedding-model",
        required=True,
        help="Ollama embedding model name, e.g. nomic-embed-text.",
    )
    parser.add_argument(
        "--ollama-base-url",
        default=DEFAULT_OLLAMA_BASE_URL,
        help="Ollama base URL. Default: %(default)s",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=None,
        help="Optional limit on number of concepts to embed for testing.",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=32,
        help="Number of canonical texts to embed per Ollama request. Default: %(default)s",
    )
    parser.add_argument(
        "--output-artifact",
        default=None,
        help="Optional pickle output path. Default derives from input file and model name.",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=float,
        default=120.0,
        help="HTTP timeout for Ollama requests. Default: %(default)s",
    )
    parser.add_argument(
        "--max-retries",
        type=int,
        default=3,
        help="Maximum retries for Ollama requests. Default: %(default)s",
    )
    parser.add_argument(
        "--retry-backoff-seconds",
        type=float,
        default=1.0,
        help="Base retry backoff in seconds. Default: %(default)s",
    )
    return parser.parse_args()


def configure_logging() -> None:
    """Configure readable progress logging for the embedding generation script."""

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
    )


def generate_embedding_artifact(args: argparse.Namespace) -> Path:
    """Run the full JSONL -> concept store -> embeddings -> artifact flow."""

    concept_store = load_concept_store(args.input_jsonl)
    ordered_concepts = list(concept_store.values())
    if args.limit is not None:
        ordered_concepts = ordered_concepts[: args.limit]

    LOGGER.info("Loaded %d concepts from %s", len(concept_store), args.input_jsonl)
    LOGGER.info("Preparing %d concepts for embedding", len(ordered_concepts))

    canonical_texts = [build_canonical_text(concept) for concept in ordered_concepts]
    ordered_sumo_types = [concept.sumo_type for concept in ordered_concepts]

    client = OllamaEmbeddingClient(
        base_url=args.ollama_base_url,
        timeout_seconds=args.timeout_seconds,
        max_retries=args.max_retries,
        retry_backoff_seconds=args.retry_backoff_seconds,
    )

    embeddings: list[list[float]] = []
    total = len(canonical_texts)
    for batch_start in range(0, total, args.batch_size):
        batch_end = min(batch_start + args.batch_size, total)
        batch_texts = canonical_texts[batch_start:batch_end]
        batch_embeddings = client.embed_texts(batch_texts, args.embedding_model)
        embeddings.extend(batch_embeddings)
        LOGGER.info(
            "Embedded concepts %d-%d of %d",
            batch_start + 1,
            batch_end,
            total,
        )

    dimensions = len(embeddings[0]) if embeddings else 0
    artifact = EmbeddingArtifact(
        input_jsonl=str(Path(args.input_jsonl).resolve()),
        embedding_model=args.embedding_model,
        ollama_base_url=args.ollama_base_url,
        created_at_utc=datetime.now(timezone.utc).isoformat(),
        sumo_types=ordered_sumo_types,
        canonical_texts=canonical_texts,
        embeddings=embeddings,
        embedding_dimensions=dimensions,
    )

    output_path = (
        Path(args.output_artifact)
        if args.output_artifact is not None
        else derive_default_output_artifact_path(args.input_jsonl, args.embedding_model)
    )
    save_embedding_artifact(output_path, artifact)
    LOGGER.info("Saved embedding artifact to %s", output_path)
    return output_path


def derive_default_output_artifact_path(input_jsonl: str, embedding_model: str) -> Path:
    """Derive a readable default pickle output path from input file and model name."""

    input_path = Path(input_jsonl)
    sanitized_model_name = re.sub(r"[^A-Za-z0-9._-]+", "_", embedding_model)
    return input_path.with_name(
        "{0}.{1}.embeddings.pkl".format(input_path.stem, sanitized_model_name)
    )


def save_embedding_artifact(output_path: Path, artifact: EmbeddingArtifact) -> None:
    """Persist the embedding artifact to a pickle file for later FAISS indexing."""

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("wb") as handle:
        pickle.dump(artifact.to_dict(), handle, protocol=pickle.HIGHEST_PROTOCOL)


def _validate_cli_args(args: argparse.Namespace) -> None:
    if args.limit is not None and args.limit <= 0:
        raise ValueError("--limit must be greater than zero when provided.")
    if args.batch_size <= 0:
        raise ValueError("--batch-size must be greater than zero.")
    if args.timeout_seconds <= 0:
        raise ValueError("--timeout-seconds must be greater than zero.")
    if args.max_retries <= 0:
        raise ValueError("--max-retries must be greater than zero.")
    if args.retry_backoff_seconds < 0:
        raise ValueError("--retry-backoff-seconds cannot be negative.")


def main() -> int:
    """CLI entrypoint for generating SUMO concept embeddings with Ollama."""

    configure_logging()
    args = parse_args()
    try:
        _validate_cli_args(args)
        generate_embedding_artifact(args)
        return 0
    except Exception as exc:
        LOGGER.error("%s", exc)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
