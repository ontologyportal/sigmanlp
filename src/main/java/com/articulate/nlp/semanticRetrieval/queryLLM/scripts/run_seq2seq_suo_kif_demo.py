from __future__ import annotations

import argparse
import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
QUERY_LLM_DIR = SCRIPT_DIR.parent
LLM_DIR = QUERY_LLM_DIR / "llm"
PROMPT_DIR = QUERY_LLM_DIR.parent / "promptConstruction" / "prompt"
RETRIEVAL_DIR = QUERY_LLM_DIR.parent / "retrievalOrchestration" / "retrieval"
SENTENCE_ANALYSIS_DIR = QUERY_LLM_DIR.parent / "sentenceAnalysis" / "analysis"
for path in [LLM_DIR, PROMPT_DIR, RETRIEVAL_DIR, SENTENCE_ANALYSIS_DIR, SCRIPT_DIR]:
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from candidate_selection import CandidateSelectionConfig
from demo_common import print_available_spacy_models, print_query_result
from faiss_candidate_search import FaissCandidateSearcher
from llm_query_orchestrator import LLMQueryOrchestrator
from llm_types import (
    DEFAULT_SEQ2SEQ_MODEL,
    DEFAULT_SEQ2SEQ_PROMPT_VERBOSITY,
    Seq2SeqConfig,
)
from prompt_types import VALID_PROMPT_VERBOSITIES
from sentence_analyzer import DEFAULT_SPACY_MODEL


PROMPT = 'What sentence would you like to query with the seq2seq model? (or "help" for options)'


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments for the seq2seq SUO-KIF query demo."""

    parser = argparse.ArgumentParser(
        description=(
            "Run the full SUO-KIF grounding pipeline and query a local seq2seq model "
            "such as Flan-T5."
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
        "--model",
        default=DEFAULT_SEQ2SEQ_MODEL,
        help="Seq2seq model name. Default: %(default)s",
    )
    parser.add_argument(
        "--max-new-tokens",
        type=int,
        default=256,
        help="Maximum new tokens to generate. Default: %(default)s",
    )
    parser.add_argument(
        "--verbosity",
        choices=sorted(VALID_PROMPT_VERBOSITIES),
        default=DEFAULT_SEQ2SEQ_PROMPT_VERBOSITY,
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
        help="Ollama base URL used for embedding retrieval queries. Default: %(default)s",
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
        help="Output format. Default: %(default)s",
    )
    return parser.parse_args()


def main() -> int:
    """CLI entrypoint for the seq2seq query demo."""

    args = parse_args()
    initial_final_k = args.top_k if args.top_k is not None else args.final_k
    try:
        session = Seq2SeqDemoSession(
            index_path=args.input_index,
            metadata_path=args.input_metadata,
            embedding_model=args.embedding_model,
            ollama_base_url=args.ollama_base_url,
            initial_spacy_model=args.spacy_model,
            initial_verbosity=args.verbosity,
            initial_output_format=args.output_format,
            initial_model=args.model,
            initial_max_new_tokens=args.max_new_tokens,
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


class Seq2SeqDemoSession:
    """Interactive REPL for seq2seq SUO-KIF querying."""

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
        initial_model: str,
        initial_max_new_tokens: int,
        initial_final_k: int,
        initial_pool_k: int,
        initial_selection_mode: str,
        initial_min_k: int,
        initial_max_k: int,
        initial_score_band_delta: float,
        initial_gap_drop_threshold: float,
    ) -> None:
        self.embedding_model = embedding_model
        self.active_spacy_model = initial_spacy_model
        self.verbosity = initial_verbosity
        self.output_format = initial_output_format
        self.model = initial_model
        self.max_new_tokens = initial_max_new_tokens
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
        self._orchestrator = LLMQueryOrchestrator(
            faiss_searcher=self._faiss_searcher,
            embedding_model=embedding_model,
            ollama_base_url=ollama_base_url,
        )

    def run(self) -> int:
        """Start the interactive seq2seq loop."""

        self._print_current_state()
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
        """Run one seq2seq query and exit."""

        self._query_and_print(sentence)
        return 0

    def _handle_input(self, raw_input: str) -> bool:
        command = raw_input.strip()
        if not command:
            print("Please enter a sentence or command.")
            return True

        lowered = command.lower()
        if lowered in {"exit", "quit"}:
            print("Exiting.")
            return False
        if lowered == "help":
            self._print_help()
            return True
        if lowered == "list models":
            self._print_models()
            return True
        if lowered.startswith("set spacy_model="):
            self._set_spacy_model(command[len("set spacy_model="):].strip())
            return True
        if lowered.startswith("set model="):
            self._set_model(command[len("set model="):].strip())
            return True
        if lowered.startswith("set max_new_tokens="):
            self._set_max_new_tokens(command[len("set max_new_tokens="):].strip())
            return True
        if lowered.startswith("set verbosity="):
            self._set_verbosity(command[len("set verbosity="):].strip())
            return True
        if lowered.startswith("set format="):
            self._set_output_format(command[len("set format="):].strip())
            return True
        if lowered.startswith("set k="):
            self._set_final_k(command[len("set k="):].strip())
            return True
        if lowered.startswith("set final_k="):
            self._set_final_k(command[len("set final_k="):].strip())
            return True
        if lowered.startswith("set pool="):
            self._set_initial_pool_k(command[len("set pool="):].strip())
            return True
        if lowered.startswith("set mode="):
            self._set_selection_mode(command[len("set mode="):].strip())
            return True
        if lowered.startswith("set min_k="):
            self._set_min_k(command[len("set min_k="):].strip())
            return True
        if lowered.startswith("set max_k="):
            self._set_max_k(command[len("set max_k="):].strip())
            return True
        if lowered.startswith("set score_band_delta="):
            self._set_score_band_delta(command[len("set score_band_delta="):].strip())
            return True
        if lowered.startswith("set gap_drop_threshold="):
            self._set_gap_drop_threshold(command[len("set gap_drop_threshold="):].strip())
            return True

        self._query_and_print(command)
        return True

    def _query_and_print(self, sentence: str) -> None:
        trimmed_sentence = sentence.strip()
        if not trimmed_sentence:
            raise ValueError("Sentence must be a non-empty string.")

        result = self._orchestrator.query_seq2seq(
            trimmed_sentence,
            seq2seq_config=Seq2SeqConfig(
                model=self.model,
                max_new_tokens=self.max_new_tokens,
            ),
            selection_config=self._build_selection_config(),
            prompt_verbosity=self.verbosity,
            spacy_model=self.active_spacy_model,
        )
        print_query_result(result, output_format=self.output_format)

    def _print_current_state(self) -> None:
        print("Active spaCy model: {0}".format(self.active_spacy_model))
        print("Embedding model: {0}".format(self.embedding_model))
        print("Seq2seq model: {0}".format(self.model))
        print("Prompt verbosity: {0}".format(self.verbosity))
        print("Max new tokens: {0}".format(self.max_new_tokens))
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
        print("  set spacy_model=<spacy_model_name>")
        print("  set model=<seq2seq_model_name>")
        print("  set max_new_tokens=<number>")
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
        print("Any other non-empty input is treated as a sentence to convert and query.")

    def _print_models(self) -> None:
        print_available_spacy_models(self.active_spacy_model)
        print("Seq2seq model:")
        print("  {0}".format(self.model))

    def _set_spacy_model(self, value: str) -> None:
        if not value:
            raise ValueError("spaCy model name cannot be empty.")
        self.active_spacy_model = value
        self._print_current_state()

    def _set_model(self, value: str) -> None:
        if not value:
            raise ValueError("Seq2seq model name cannot be empty.")
        self.model = value
        self._print_current_state()

    def _set_max_new_tokens(self, value: str) -> None:
        try:
            parsed_value = int(value)
        except ValueError as exc:
            raise ValueError("max_new_tokens must be an integer greater than zero.") from exc
        config = Seq2SeqConfig(model=self.model, max_new_tokens=parsed_value)
        self.max_new_tokens = config.max_new_tokens
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
        self._apply_selection_config_change(selection_mode=value.strip().lower())

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
            parsed_value = float(value)
        except ValueError as exc:
            raise ValueError(
                "score_band_delta must be a number greater than or equal to zero."
            ) from exc
        self._apply_selection_config_change(score_band_delta=parsed_value)

    def _set_gap_drop_threshold(self, value: str) -> None:
        try:
            parsed_value = float(value)
        except ValueError as exc:
            raise ValueError(
                "gap_drop_threshold must be a number greater than or equal to zero."
            ) from exc
        self._apply_selection_config_change(gap_drop_threshold=parsed_value)

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


if __name__ == "__main__":
    raise SystemExit(main())
