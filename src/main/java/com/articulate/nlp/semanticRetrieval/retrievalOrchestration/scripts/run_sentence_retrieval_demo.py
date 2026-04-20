from __future__ import annotations

import argparse
import importlib.util
import json
import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
RETRIEVAL_DIR = SCRIPT_DIR.parent / "retrieval"
SENTENCE_ANALYSIS_DIR = SCRIPT_DIR.parent.parent / "sentenceAnalysis" / "analysis"
for path in [RETRIEVAL_DIR, SENTENCE_ANALYSIS_DIR]:
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from faiss_candidate_search import FaissCandidateSearcher
from candidate_selection import CandidateSelectionConfig
from semantic_retrieval_orchestrator import SemanticRetrievalOrchestrator
from sentence_analyzer import DEFAULT_SPACY_MODEL, SpacySentenceAnalyzer
from retrieval_types import RetrievedCandidate, RetrievedMention, SentenceRetrievalResult


PROMPT = 'What sentence would you like to retrieve? (or "help" for options)'
KNOWN_SPACY_MODELS = [
    "en_core_web_sm",
    "en_core_web_md",
    "en_core_web_lg",
    "en_core_web_trf",
]


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments for the sentence retrieval demo."""

    parser = argparse.ArgumentParser(
        description=(
            "Analyze one sentence, retrieve top-k SUMO candidates for heads and modifiers, "
            "and print structured retrieval output."
        )
    )
    parser.add_argument(
        "sentence",
        nargs="*",
        help="Optional sentence to analyze and retrieve over. If omitted, starts interactive mode.",
    )
    parser.add_argument("--input-index", required=True, help="Input FAISS index path.")
    parser.add_argument("--input-metadata", required=True, help="Input FAISS metadata path.")
    parser.add_argument(
        "--embedding-model",
        required=True,
        help="Ollama embedding model name. Must match the model stored in the metadata.",
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
        help="Output format for retrieval results. Default: %(default)s",
    )
    return parser.parse_args()


def main() -> int:
    """CLI entrypoint for structured sentence retrieval demo output."""

    args = parse_args()
    initial_final_k = args.top_k if args.top_k is not None else args.final_k
    try:
        session = InteractiveSentenceRetrievalSession(
            index_path=args.input_index,
            metadata_path=args.input_metadata,
            embedding_model=args.embedding_model,
            ollama_base_url=args.ollama_base_url,
            initial_spacy_model=args.spacy_model,
            initial_final_k=initial_final_k,
            initial_pool_k=args.initial_pool_k,
            initial_selection_mode=args.selection_mode,
            initial_min_k=args.min_k,
            initial_max_k=args.max_k,
            initial_score_band_delta=args.score_band_delta,
            initial_gap_drop_threshold=args.gap_drop_threshold,
            initial_output_format=args.output_format,
        )
        if args.sentence:
            sentence = " ".join(args.sentence).strip()
            return session.run_one_shot(sentence)
        return session.run()
    except Exception as exc:
        print("Error: {0}".format(exc), file=sys.stderr)
        return 1


class InteractiveSentenceRetrievalSession:
    """Interactive REPL for structured sentence retrieval."""

    def __init__(
        self,
        *,
        index_path: str,
        metadata_path: str,
        embedding_model: str,
        ollama_base_url: str,
        initial_spacy_model: str,
        initial_final_k: int,
        initial_pool_k: int,
        initial_selection_mode: str,
        initial_min_k: int,
        initial_max_k: int,
        initial_score_band_delta: float,
        initial_gap_drop_threshold: float,
        initial_output_format: str,
    ) -> None:
        self.index_path = index_path
        self.metadata_path = metadata_path
        self.embedding_model = embedding_model
        self.ollama_base_url = ollama_base_url
        self.active_spacy_model = initial_spacy_model
        self.initial_pool_k = initial_pool_k
        self.selection_mode = initial_selection_mode
        self.final_k = initial_final_k
        self.min_k = initial_min_k
        self.max_k = initial_max_k
        self.score_band_delta = initial_score_band_delta
        self.gap_drop_threshold = initial_gap_drop_threshold
        self.output_format = initial_output_format
        self._faiss_searcher = FaissCandidateSearcher(
            index_path=index_path,
            metadata_path=metadata_path,
        )
        self._orchestrators: dict[str, SemanticRetrievalOrchestrator] = {}

    def run(self) -> int:
        """Start the interactive sentence-retrieval loop."""

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
        """Retrieve one sentence and exit."""

        self._retrieve_and_print(sentence)
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
        if lowered_command.startswith("set format="):
            self._set_output_format(command[len("set format="):].strip())
            return True

        self._retrieve_and_print(command)
        return True

    def _print_startup(self) -> None:
        self._print_current_state()

    def _print_current_state(self) -> None:
        print("Active spaCy model: {0}".format(self.active_spacy_model))
        print("Embedding model: {0}".format(self.embedding_model))
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
        print("  set k=<number>")
        print("  set final_k=<number>")
        print("  set pool=<number>")
        print("  set mode=fixed_k|score_band|gap_drop")
        print("  set min_k=<number>")
        print("  set max_k=<number>")
        print("  set score_band_delta=<number>")
        print("  set gap_drop_threshold=<number>")
        print("  set format=text|json")
        print("  exit")
        print("  quit")
        print("Any other non-empty input is treated as a sentence to retrieve.")

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

    def _set_output_format(self, value: str) -> None:
        normalized_value = value.strip().lower()
        if normalized_value not in {"text", "json"}:
            raise ValueError("Output format must be either 'text' or 'json'.")
        self.output_format = normalized_value
        self._print_current_state()

    def _retrieve_and_print(self, sentence: str) -> None:
        trimmed_sentence = sentence.strip()
        if not trimmed_sentence:
            raise ValueError("Sentence must be a non-empty string.")

        orchestrator = self._load_orchestrator(self.active_spacy_model)
        result = orchestrator.retrieve_sentence_with_selection(
            trimmed_sentence,
            selection_config=self._build_selection_config(),
        )
        _print_sentence_retrieval_result(result, output_format=self.output_format)

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


def _print_sentence_retrieval_result(
    result: SentenceRetrievalResult,
    *,
    output_format: str,
) -> None:
    if output_format == "json":
        print(json.dumps(_build_sentence_retrieval_payload(result), indent=2))
        return

    mention_by_id = {mention.mention_id: mention for mention in result.retrieved_mentions}

    print("Sentence:")
    print("  {0}".format(result.sentence))
    print("Retrieved mentions:")
    if not result.retrieved_mentions:
        print("  (none)")
        return

    for mention in result.retrieved_mentions:
        if mention.is_head:
            header = "- head {0}: {1}".format(mention.head_type or "unknown", mention.text)
        else:
            header = "- modifier {0}: {1}".format(mention.mention_type, mention.text)
        print(header)

        attached_to = "none"
        if mention.attached_to is not None:
            parent = mention_by_id.get(mention.attached_to)
            attached_to = parent.text if parent is not None else mention.attached_to
        print("  attached_to: {0}".format(attached_to))
        if mention.named_entity_label is not None:
            print("  named_entity: {0}".format(mention.named_entity_label))
        print("  retrieval: {0}".format(_describe_retrieval_mode(mention)))

        modifier_texts = _collect_attached_modifier_texts(
            mention_id=mention.mention_id,
            mention_by_id=mention_by_id,
        )
        if modifier_texts:
            print("  modifiers: {0}".format(", ".join(modifier_texts)))

        print("  retrieval query:")
        _print_indented_block(mention.retrieval_query, indent="    ")

        print("  candidate selection:")
        print("    mode: {0}".format(mention.selection_trace.mode))
        print("    raw pool size: {0}".format(mention.selection_trace.raw_candidate_count))
        print("    selected count: {0}".format(mention.selection_trace.selected_candidate_count))
        print("    reason: {0}".format(mention.selection_trace.stop_reason))
        print("    detail: {0}".format(mention.selection_trace.detail))

        print("  candidates:")
        if not mention.candidates:
            print("    (none)")
            continue
        for candidate in mention.candidates:
            print(
                "    {0}. score={1:.6f} sumo_type={2}".format(
                    candidate.rank,
                    candidate.score,
                    candidate.sumo_type,
                )
            )
            print("       termFormats={0}".format(candidate.term_formats))
            print("       definition={0}".format(candidate.definition))


def _collect_attached_modifier_texts(
    *,
    mention_id: str,
    mention_by_id: dict[str, RetrievedMention],
) -> list[str]:
    direct_children = [
        child
        for child in mention_by_id.values()
        if child.attached_to == mention_id
    ]
    direct_children.sort(key=_retrieved_mention_sort_key)

    flattened: list[str] = []
    for child in direct_children:
        flattened.append(child.text)
        flattened.extend(
            _collect_attached_modifier_texts(
                mention_id=child.mention_id,
                mention_by_id=mention_by_id,
            )
        )
    return flattened


def _build_sentence_retrieval_payload(result: SentenceRetrievalResult) -> dict[str, object]:
    child_mentions: dict[str | None, list[RetrievedMention]] = {}
    for mention in result.retrieved_mentions:
        child_mentions.setdefault(mention.attached_to, []).append(mention)

    for mentions in child_mentions.values():
        mentions.sort(key=_retrieved_mention_sort_key)

    head_mentions = [
        mention
        for mention in child_mentions.get(None, [])
        if mention.is_head
    ]
    return {
        "sentence": result.sentence,
        "retrieved_mentions": [
            _serialize_retrieved_mention(
                mention=mention,
                child_mentions=child_mentions,
            )
            for mention in head_mentions
        ],
    }


def _serialize_retrieved_mention(
    *,
    mention: RetrievedMention,
    child_mentions: dict[str | None, list[RetrievedMention]],
) -> dict[str, object]:
    payload = {
        "mention_id": mention.mention_id,
        "text": mention.text,
        "normalized_text": mention.normalized_text,
        "lemma": mention.lemma,
        "named_entity_label": mention.named_entity_label,
        "mention_type": mention.mention_type,
        "source": mention.source,
        "retrieval_query": mention.retrieval_query,
        "candidates": [
            _serialize_retrieved_candidate(candidate) for candidate in mention.candidates
        ],
        "selection_trace": {
            "mode": mention.selection_trace.mode,
            "initial_pool_k": mention.selection_trace.initial_pool_k,
            "raw_candidate_count": mention.selection_trace.raw_candidate_count,
            "selected_candidate_count": mention.selection_trace.selected_candidate_count,
            "stop_reason": mention.selection_trace.stop_reason,
            "detail": mention.selection_trace.detail,
        },
        "attached_to": mention.attached_to,
        "is_head": mention.is_head,
        "head_type": mention.head_type,
        "token_start": mention.token_start,
        "token_end": mention.token_end,
    }
    payload["modifiers"] = [
        _serialize_retrieved_mention(
            mention=child_mention,
            child_mentions=child_mentions,
        )
        for child_mention in child_mentions.get(mention.mention_id, [])
    ]
    return payload


def _serialize_retrieved_candidate(candidate: RetrievedCandidate) -> dict[str, object]:
    return {
        "sumo_type": candidate.sumo_type,
        "score": candidate.score,
        "termFormats": candidate.term_formats,
        "definition": candidate.definition,
        "rank": candidate.rank,
    }


def _print_indented_block(text: str, *, indent: str) -> None:
    for line in text.splitlines():
        print("{0}{1}".format(indent, line))


def _retrieved_mention_sort_key(mention: RetrievedMention) -> tuple[int, str]:
    return (mention.token_start, mention.token_end, mention.mention_id)


def _describe_retrieval_mode(mention: RetrievedMention) -> str:
    if mention.selection_trace.mode == "direct_map":
        return "bypassed (direct_map)"
    return "embedding+faiss"


def _iter_known_model_names(active_model: str) -> list[str]:
    model_names = list(KNOWN_SPACY_MODELS)
    if active_model not in model_names:
        model_names.append(active_model)
    return model_names


def _is_package_available(package_name: str) -> bool:
    return importlib.util.find_spec(package_name) is not None


if __name__ == "__main__":
    raise SystemExit(main())
