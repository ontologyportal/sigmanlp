from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path

from faiss_query import search_index
from faiss_store import FaissMetadata, load_faiss_index, load_metadata
from index_registry import IndexRegistry, discover_index_registry


SCRIPT_DIR = Path(__file__).resolve().parent
UTILS_DIR = SCRIPT_DIR.parent.parent / "utils"
if str(UTILS_DIR) not in sys.path:
    sys.path.insert(0, str(UTILS_DIR))

from normalization import EnglishTextNormalizer
from ollama_client import DEFAULT_OLLAMA_BASE_URL, OllamaEmbeddingClient


DEFAULT_TOP_K = 5
PROMPT = 'What word would you like to query? (or "help" for options)'


@dataclass(frozen=True)
class LoadedIndex:
    """Loaded FAISS index plus aligned metadata for one embedding model."""

    index: object
    metadata: FaissMetadata


class InteractiveQuerySession:
    """Stateful REPL session for searching SUMO FAISS indexes by raw text."""

    def __init__(self, registry: IndexRegistry) -> None:
        self.registry = registry
        self.current_top_k = DEFAULT_TOP_K
        self.active_model = registry.get_default_active_model()
        self._client = OllamaEmbeddingClient(base_url=DEFAULT_OLLAMA_BASE_URL)
        self._normalizer = EnglishTextNormalizer()
        self._loaded_indexes: dict[str, LoadedIndex] = {}

    def run(self) -> int:
        """Start the interactive query loop."""

        self._print_startup()
        while True:
            try:
                user_input = input(PROMPT + "\n> ")
            except EOFError:
                print()
                print("Exiting.")
                return 0
            except KeyboardInterrupt:
                print()
                print("Exiting.")
                return 0

            try:
                if not self._handle_input(user_input):
                    return 0
            except Exception as exc:
                print("Error: {0}".format(exc))

    def _handle_input(self, raw_input: str) -> bool:
        command = raw_input.strip()
        if not command:
            print("Please enter a word, phrase, or command.")
            return True

        lowered_command = command.lower()
        if lowered_command in {"exit", "quit"}:
            print("Exiting.")
            return False
        if lowered_command == "help":
            self._print_help()
            return True
        if lowered_command == "list indexes":
            self._print_available_indexes()
            return True
        if lowered_command.startswith("set k="):
            self._set_top_k(command[len("set k="):].strip())
            return True
        if lowered_command.startswith("set index="):
            self._set_active_index(command[len("set index="):].strip())
            return True

        self._run_query(command)
        return True

    def _print_startup(self) -> None:
        for warning in self.registry.warnings:
            print("Warning: {0}".format(warning))

        self._print_current_state()
        if self.active_model is None:
            print("No active index is selected. Run 'list indexes' and then 'set index=<model>'.")

    def _print_current_state(self) -> None:
        if self.active_model is None:
            print("Active index model: none selected")
        else:
            print("Active index model: {0}".format(self.active_model))
        print("Current top-k: {0}".format(self.current_top_k))

    def _print_help(self) -> None:
        self._print_current_state()
        print("Commands:")
        print("  help")
        print("  list indexes")
        print("  set k=<number>")
        print("  set index=<embedding_model>")
        print("  exit")
        print("  quit")
        print("Any other non-empty input is treated as a query phrase.")

    def _print_available_indexes(self) -> None:
        print("Available indexes:")
        for entry in self.registry.all_entries():
            status = "ambiguous" if self.registry.is_ambiguous(entry.embedding_model) else "inactive"
            if self.active_model == entry.embedding_model and not self.registry.is_ambiguous(entry.embedding_model):
                status = "active"
            print(
                "  model={0} status={1} path={2} vectors={3} dimension={4}".format(
                    entry.embedding_model,
                    status,
                    entry.faiss_path,
                    entry.vector_count,
                    entry.embedding_dimension,
                )
            )

    def _set_top_k(self, value: str) -> None:
        try:
            parsed_top_k = int(value)
        except ValueError as exc:
            raise ValueError("Top-k must be an integer greater than zero.") from exc
        if parsed_top_k <= 0:
            raise ValueError("Top-k must be greater than zero.")
        self.current_top_k = parsed_top_k
        self._print_current_state()

    def _set_active_index(self, model_name: str) -> None:
        if not model_name:
            raise ValueError("Index model name cannot be empty.")
        if not self.registry.has_model(model_name):
            available_models = ", ".join(self.registry.model_names())
            raise ValueError(
                "Unknown embedding model '{0}'. Available models: {1}".format(
                    model_name,
                    available_models,
                )
            )
        if self.registry.is_ambiguous(model_name):
            candidate_paths = [
                str(entry.faiss_path)
                for entry in self.registry.get_entries(model_name)
            ]
            raise ValueError(
                "Embedding model '{0}' is ambiguous across multiple indexes: {1}".format(
                    model_name,
                    ", ".join(candidate_paths),
                )
            )
        self.active_model = model_name
        self._print_current_state()

    def _run_query(self, query_text: str) -> None:
        if self.active_model is None:
            raise ValueError(
                "No active index selected. Run 'list indexes' and then 'set index=<model>'."
            )

        trimmed_query = query_text.strip()
        if not trimmed_query:
            raise ValueError("Query text must be a non-empty string.")

        active_index = self._load_active_index(self.active_model)
        normalized_query = self._normalizer.normalize_mention(trimmed_query)
        if not normalized_query.normalized_text:
            raise ValueError(
                "Query text is empty after normalization. Please enter a word or phrase."
            )

        query_embedding = self._client.embed_text(
            normalized_query.normalized_text,
            self.active_model,
        )
        results = search_index(
            active_index.index,
            active_index.metadata,
            query_embedding,
            self.current_top_k,
        )

        if not results:
            print("No results found.")
            return

        for result in results:
            print(
                "{0}. score={1:.6f} sumo_type={2}".format(
                    result.rank,
                    result.score,
                    result.sumo_type,
                )
            )
            print("   canonical_text={0}".format(result.canonical_text))

    def _load_active_index(self, model_name: str) -> LoadedIndex:
        cached_index = self._loaded_indexes.get(model_name)
        if cached_index is not None:
            return cached_index

        entry = self.registry.get_unambiguous_entry(model_name)
        loaded_index = LoadedIndex(
            index=load_faiss_index(str(entry.faiss_path)),
            metadata=load_metadata(str(entry.metadata_path)),
        )
        self._loaded_indexes[model_name] = loaded_index
        return loaded_index


def parse_args() -> argparse.Namespace:
    """Parse the startup CLI arguments for the interactive query session."""

    parser = argparse.ArgumentParser(
        description="Interactive query CLI for SUMO FAISS indexes."
    )
    parser.add_argument(
        "index_directory",
        help="Directory containing .faiss and .metadata.json files.",
    )
    return parser.parse_args()


def main() -> int:
    """CLI entrypoint for the interactive SUMO query tool."""

    args = parse_args()
    try:
        registry = discover_index_registry(args.index_directory)
        session = InteractiveQuerySession(registry)
        return session.run()
    except Exception as exc:
        print("Error: {0}".format(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
