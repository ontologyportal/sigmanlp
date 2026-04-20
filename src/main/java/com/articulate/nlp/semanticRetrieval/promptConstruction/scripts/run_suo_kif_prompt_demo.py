from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
PROMPT_DIR = SCRIPT_DIR.parent / "prompt"
SEMANTIC_RETRIEVAL_DIR = SCRIPT_DIR.parent.parent
RETRIEVAL_DIR = SEMANTIC_RETRIEVAL_DIR / "retrievalOrchestration" / "retrieval"
SENTENCE_ANALYSIS_DIR = SEMANTIC_RETRIEVAL_DIR / "sentenceAnalysis" / "analysis"
for path in [PROMPT_DIR, RETRIEVAL_DIR, SENTENCE_ANALYSIS_DIR]:
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from candidate_selection import CandidateSelectionConfig
from faiss_candidate_search import FaissCandidateSearcher
from prompt_types import SUOKIFPromptPayload, VALID_PROMPT_VERBOSITIES
from semantic_retrieval_orchestrator import SemanticRetrievalOrchestrator
from sentence_analyzer import DEFAULT_SPACY_MODEL, SpacySentenceAnalyzer
from suo_kif_prompt_builder import SUOKIFPromptBuilder


PROMPT = 'What sentence would you like to build a prompt for? (or "help" for options)'
KNOWN_SPACY_MODELS = [
    "en_core_web_sm",
    "en_core_web_md",
    "en_core_web_lg",
    "en_core_web_trf",
]


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments for the SUO-KIF prompt-construction demo."""

    parser = argparse.ArgumentParser(
        description=(
            "Analyze one sentence, retrieve SUMO candidates, and build a verbosity-aware "
            "SUO-KIF prompt payload."
        )
    )
    parser.add_argument(
        "sentence",
        nargs="*",
        help="Optional sentence to process. If omitted, starts interactive mode.",
    )
    parser.add_argument("--input-index", required=True, help="Input FAISS index path.")
    parser.add_argument("--input-metadata", required=True, help="Input FAISS metadata path.")
    parser.add_argument(
        "--embedding-model",
        required=True,
        help="Ollama embedding model name. Must match the model stored in the metadata.",
    )
    parser.add_argument(
        "--verbosity",
        choices=sorted(VALID_PROMPT_VERBOSITIES),
        default="medium",
        help="Prompt verbosity level. Default: %(default)s",
    )
    parser.add_argument(
        "--top-k",
        type=int,
        default=None,
        help="Alias for --final-k.",
    )
    parser.add_argument(
        "--initial-pool-k",
        type=int,
        default=50,
        help="Initial raw candidate pool size to retrieve from FAISS. Default: %(default)s",
    )
    parser.add_argument(
        "--selection-mode",
        choices=["fixed_k", "score_band", "gap_drop"],
        default="score_band",
        help="Post-FAISS candidate selection policy. Default: %(default)s",
    )
    parser.add_argument(
        "--final-k",
        type=int,
        default=5,
        help="Final candidate count for fixed_k mode. Default: %(default)s",
    )
    parser.add_argument(
        "--min-k",
        type=int,
        default=5,
        help="Minimum candidate count for score_band or gap_drop mode. Default: %(default)s",
    )
    parser.add_argument(
        "--max-k",
        type=int,
        default=15,
        help="Maximum candidate count for score_band or gap_drop mode. Default: %(default)s",
    )
    parser.add_argument(
        "--score-band-delta",
        type=float,
        default=0.04,
        help="Allowed score drop from the top candidate in score_band mode. Default: %(default)s",
    )
    parser.add_argument(
        "--gap-drop-threshold",
        type=float,
        default=0.03,
        help="Gap threshold for stopping in gap_drop mode. Default: %(default)s",
    )
    parser.add_argument(
        "--ollama-base-url",
        default="http://localhost:11434",
        help="Ollama base URL. Default: %(default)s",
    )
    parser.add_argument(
        "--spacy-model",
        default=DEFAULT_SPACY_MODEL,
        help="spaCy model name to use for sentence analysis. Default: %(default)s",
    )
    parser.add_argument(
        "--output-format",
        choices=["text", "json"],
        default="text",
        help="Output format for the prompt payload. Default: %(default)s",
    )
    return parser.parse_args()


def main() -> int:
    """CLI entrypoint for SUO-KIF prompt construction."""

    args = parse_args()
    initial_final_k = args.top_k if args.top_k is not None else args.final_k
    try:
        session = InteractivePromptConstructionSession(
            index_path=args.input_index,
            metadata_path=args.input_metadata,
            embedding_model=args.embedding_model,
            ollama_base_url=args.ollama_base_url,
            initial_spacy_model=args.spacy_model,
            initial_verbosity=args.verbosity,
            initial_output_format=args.output_format,
            initial_final_k=initial_final_k,
            initial_pool_k=args.initial_pool_k,
            initial_selection_mode=args.selection_mode,
            initial_min_k=args.min_k,
            initial_max_k=args.max_k,
            initial_score_band_delta=args.score_band_delta,
            initial_gap_drop_threshold=args.gap_drop_threshold,
        )
        if args.sentence:
            sentence = " ".join(args.sentence).strip()
            return session.run_one_shot(sentence)
        return session.run()
    except Exception as exc:
        print("Error: {0}".format(exc), file=sys.stderr)
        return 1


class InteractivePromptConstructionSession:
    """Interactive REPL for SUO-KIF prompt construction."""

    def __init__(
        self,
        *,
        index_path: str,
        metadata_path: str,
        embedding_model: str,
        ollama_base_url: str,
        initial_spacy_model: str,
        initial_verbosity: str,
        initial_output_format: str,
        initial_final_k: int,
        initial_pool_k: int,
        initial_selection_mode: str,
        initial_min_k: int,
        initial_max_k: int,
        initial_score_band_delta: float,
        initial_gap_drop_threshold: float,
    ) -> None:
        self.index_path = index_path
        self.metadata_path = metadata_path
        self.embedding_model = embedding_model
        self.ollama_base_url = ollama_base_url
        self.active_spacy_model = initial_spacy_model
        self.verbosity = initial_verbosity
        self.output_format = initial_output_format
        self.initial_pool_k = initial_pool_k
        self.selection_mode = initial_selection_mode
        self.final_k = initial_final_k
        self.min_k = initial_min_k
        self.max_k = initial_max_k
        self.score_band_delta = initial_score_band_delta
        self.gap_drop_threshold = initial_gap_drop_threshold
        self._faiss_searcher = FaissCandidateSearcher(
            index_path=index_path,
            metadata_path=metadata_path,
        )
        self._orchestrators: dict[str, SemanticRetrievalOrchestrator] = {}
        self._prompt_builder = SUOKIFPromptBuilder()

    def run(self) -> int:
        """Start the interactive prompt-construction loop."""

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

    def run_one_shot(self, sentence: str) -> int:
        """Build one prompt and exit."""

        self._build_and_print_prompt(sentence)
        return 0

    def _handle_input(self, raw_input: str) -> bool:
        command = raw_input.strip()
        if not command:
            print("Please enter a sentence or command.")
            return True

        lowered_command = command.lower()
        if lowered_command in {"exit", "quit"}:
            print("Exiting.")
            return False
        if lowered_command == "help":
            self._print_help()
            return True
        if lowered_command == "list models":
            self._print_available_models()
            return True
        if lowered_command.startswith("set model="):
            self._set_model(command[len("set model="):].strip())
            return True
        if lowered_command.startswith("set verbosity="):
            self._set_verbosity(command[len("set verbosity="):].strip())
            return True
        if lowered_command.startswith("set format="):
            self._set_output_format(command[len("set format="):].strip())
            return True
        if lowered_command.startswith("set k="):
            self._set_top_k(command[len("set k="):].strip())
            return True
        if lowered_command.startswith("set final_k="):
            self._set_final_k(command[len("set final_k="):].strip())
            return True
        if lowered_command.startswith("set pool="):
            self._set_initial_pool_k(command[len("set pool="):].strip())
            return True
        if lowered_command.startswith("set mode="):
            self._set_selection_mode(command[len("set mode="):].strip())
            return True
        if lowered_command.startswith("set min_k="):
            self._set_min_k(command[len("set min_k="):].strip())
            return True
        if lowered_command.startswith("set max_k="):
            self._set_max_k(command[len("set max_k="):].strip())
            return True
        if lowered_command.startswith("set score_band_delta="):
            self._set_score_band_delta(command[len("set score_band_delta="):].strip())
            return True
        if lowered_command.startswith("set gap_drop_threshold="):
            self._set_gap_drop_threshold(command[len("set gap_drop_threshold="):].strip())
            return True

        self._build_and_print_prompt(command)
        return True

    def _print_startup(self) -> None:
        self._print_current_state()

    def _print_current_state(self) -> None:
        print("Active spaCy model: {0}".format(self.active_spacy_model))
        print("Embedding model: {0}".format(self.embedding_model))
        print("Verbosity: {0}".format(self.verbosity))
        print("Selection mode: {0}".format(self.selection_mode))
        print("Initial pool k: {0}".format(self.initial_pool_k))
        print("Final k: {0}".format(self.final_k))
        print("Min/Max k: {0}/{1}".format(self.min_k, self.max_k))
        print("Score band delta: {0}".format(self.score_band_delta))
        print("Gap drop threshold: {0}".format(self.gap_drop_threshold))
        print("Output format: {0}".format(self.output_format))

    def _print_help(self) -> None:
        self._print_current_state()
        print("Commands:")
        print("  help")
        print("  list models")
        print("  set model=<spacy_model_name>")
        print("  set verbosity=micro|low|medium|high")
        print("  set format=text|json")
        print("  set k=<number>")
        print("  set final_k=<number>")
        print("  set pool=<number>")
        print("  set mode=fixed_k|score_band|gap_drop")
        print("  set min_k=<number>")
        print("  set max_k=<number>")
        print("  set score_band_delta=<number>")
        print("  set gap_drop_threshold=<number>")
        print("  exit")
        print("  quit")
        print("Any other non-empty input is treated as a sentence to convert into a prompt.")

    def _print_available_models(self) -> None:
        print("spaCy package installed: {0}".format("yes" if _is_package_available("spacy") else "no"))
        print("Known English spaCy models:")
        for model_name in _iter_known_model_names(self.active_spacy_model):
            installed = "installed" if _is_package_available(model_name) else "not installed"
            active = " active" if model_name == self.active_spacy_model else ""
            print("  {0} ({1}{2})".format(model_name, installed, active))

    def _set_model(self, model_name: str) -> None:
        if not model_name:
            raise ValueError("spaCy model name cannot be empty.")
        self._load_orchestrator(model_name)
        self.active_spacy_model = model_name
        self._print_current_state()

    def _set_verbosity(self, value: str) -> None:
        normalized_value = value.strip().lower()
        if normalized_value not in VALID_PROMPT_VERBOSITIES:
            raise ValueError(
                "verbosity must be one of: {0}.".format(
                    ", ".join(sorted(VALID_PROMPT_VERBOSITIES))
                )
            )
        self.verbosity = normalized_value
        self._print_current_state()

    def _set_output_format(self, value: str) -> None:
        normalized_value = value.strip().lower()
        if normalized_value not in {"text", "json"}:
            raise ValueError("Output format must be either 'text' or 'json'.")
        self.output_format = normalized_value
        self._print_current_state()

    def _set_top_k(self, value: str) -> None:
        self._set_final_k(value)

    def _set_final_k(self, value: str) -> None:
        try:
            parsed_final_k = int(value)
        except ValueError as exc:
            raise ValueError("final_k must be an integer greater than zero.") from exc
        self._apply_selection_config_change(final_k=parsed_final_k)

    def _set_initial_pool_k(self, value: str) -> None:
        try:
            parsed_pool_k = int(value)
        except ValueError as exc:
            raise ValueError("initial_pool_k must be an integer greater than zero.") from exc
        self._apply_selection_config_change(initial_pool_k=parsed_pool_k)

    def _set_selection_mode(self, value: str) -> None:
        normalized_value = value.strip().lower()
        self._apply_selection_config_change(selection_mode=normalized_value)

    def _set_min_k(self, value: str) -> None:
        try:
            parsed_min_k = int(value)
        except ValueError as exc:
            raise ValueError("min_k must be an integer greater than zero.") from exc
        self._apply_selection_config_change(min_k=parsed_min_k)

    def _set_max_k(self, value: str) -> None:
        try:
            parsed_max_k = int(value)
        except ValueError as exc:
            raise ValueError("max_k must be an integer greater than zero.") from exc
        self._apply_selection_config_change(max_k=parsed_max_k)

    def _set_score_band_delta(self, value: str) -> None:
        try:
            parsed_delta = float(value)
        except ValueError as exc:
            raise ValueError("score_band_delta must be a number greater than or equal to zero.") from exc
        self._apply_selection_config_change(score_band_delta=parsed_delta)

    def _set_gap_drop_threshold(self, value: str) -> None:
        try:
            parsed_threshold = float(value)
        except ValueError as exc:
            raise ValueError("gap_drop_threshold must be a number greater than or equal to zero.") from exc
        self._apply_selection_config_change(gap_drop_threshold=parsed_threshold)

    def _build_and_print_prompt(self, sentence: str) -> None:
        trimmed_sentence = sentence.strip()
        if not trimmed_sentence:
            raise ValueError("Sentence must be a non-empty string.")

        orchestrator = self._load_orchestrator(self.active_spacy_model)
        retrieval_result = orchestrator.retrieve_sentence_with_selection(
            trimmed_sentence,
            selection_config=self._build_selection_config(),
        )
        payload = self._prompt_builder.build_prompt_payload(
            retrieval_result=retrieval_result,
            verbosity=self.verbosity,
        )
        _print_prompt_payload(payload, output_format=self.output_format)

    def _load_orchestrator(self, spacy_model: str) -> SemanticRetrievalOrchestrator:
        cached_orchestrator = self._orchestrators.get(spacy_model)
        if cached_orchestrator is not None:
            return cached_orchestrator

        sentence_analyzer = SpacySentenceAnalyzer(model_name=spacy_model)
        sentence_analyzer.analyze_sentence("This is a validation sentence.")
        orchestrator = SemanticRetrievalOrchestrator(
            faiss_searcher=self._faiss_searcher,
            embedding_model=self.embedding_model,
            ollama_base_url=self.ollama_base_url,
            sentence_analyzer=sentence_analyzer,
        )
        self._orchestrators[spacy_model] = orchestrator
        return orchestrator

    def _build_selection_config(self) -> CandidateSelectionConfig:
        return CandidateSelectionConfig(
            initial_pool_k=self.initial_pool_k,
            selection_mode=self.selection_mode,
            final_k=self.final_k,
            min_k=self.min_k,
            max_k=self.max_k,
            score_band_delta=self.score_band_delta,
            gap_drop_threshold=self.gap_drop_threshold,
        )

    def _apply_selection_config_change(self, **changes: object) -> None:
        current_values = {
            "initial_pool_k": self.initial_pool_k,
            "selection_mode": self.selection_mode,
            "final_k": self.final_k,
            "min_k": self.min_k,
            "max_k": self.max_k,
            "score_band_delta": self.score_band_delta,
            "gap_drop_threshold": self.gap_drop_threshold,
        }
        current_values.update(changes)
        config = CandidateSelectionConfig(**current_values)
        self.initial_pool_k = config.initial_pool_k
        self.selection_mode = config.selection_mode
        self.final_k = config.final_k
        self.min_k = config.min_k
        self.max_k = config.max_k
        self.score_band_delta = config.score_band_delta
        self.gap_drop_threshold = config.gap_drop_threshold
        self._print_current_state()


def _print_prompt_payload(
    payload: SUOKIFPromptPayload,
    *,
    output_format: str,
) -> None:
    if output_format == "json":
        print(json.dumps(_serialize_prompt_payload(payload), indent=2))
        return

    print("Sentence:")
    print("  {0}".format(payload.sentence))
    print("Verbosity:")
    print("  {0}".format(payload.verbosity))
    print("Prompt payload:")
    for message in payload.messages:
        print("[{0}]".format(message.role))
        _print_indented_block(message.content, indent="  ")


def _serialize_prompt_payload(payload: SUOKIFPromptPayload) -> dict[str, object]:
    return {
        "sentence": payload.sentence,
        "verbosity": payload.verbosity,
        "messages": [
            {
                "role": message.role,
                "content": message.content,
            }
            for message in payload.messages
        ],
    }


def _print_indented_block(text: str, *, indent: str) -> None:
    for line in text.splitlines():
        print("{0}{1}".format(indent, line))


def _iter_known_model_names(active_model: str) -> list[str]:
    model_names = list(KNOWN_SPACY_MODELS)
    if active_model not in model_names:
        model_names.append(active_model)
    return model_names


def _is_package_available(package_name: str) -> bool:
    return importlib.util.find_spec(package_name) is not None


if __name__ == "__main__":
    raise SystemExit(main())
