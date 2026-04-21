from __future__ import annotations

import argparse
import importlib.util
import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
ANALYSIS_DIR = SCRIPT_DIR.parent / "analysis"
if str(ANALYSIS_DIR) not in sys.path:
    sys.path.insert(0, str(ANALYSIS_DIR))

from mention_extractor import RuleBasedMentionExtractor
from retrieval_query_builder import build_context_aware_retrieval_query
from sentence_analyzer import DEFAULT_SPACY_MODEL, SpacySentenceAnalyzer
from sentence_types import AnalyzedSentence, Mention, ModifierMention, flatten_modifier_texts


PROMPT = 'What sentence would you like to analyze? (or "help" for options)'
KNOWN_SPACY_MODELS = [
    "en_core_web_sm",
    "en_core_web_md",
    "en_core_web_lg",
    "en_core_web_trf",
]


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments for the sentence-analysis demo."""

    parser = argparse.ArgumentParser(
        description=(
            "Analyze one sentence, extract entity/event heads with modifiers, "
            "and print context-aware retrieval queries."
        )
    )
    parser.add_argument(
        "sentence",
        nargs="*",
        help="Optional sentence to analyze. If omitted, starts interactive mode.",
    )
    parser.add_argument(
        "--spacy-model",
        default=DEFAULT_SPACY_MODEL,
        help="spaCy model name to load. Default: %(default)s",
    )
    return parser.parse_args()


def main() -> int:
    """CLI entrypoint for the sentence-analysis demo script."""

    args = parse_args()
    session = InteractiveSentenceAnalysisSession(initial_model=args.spacy_model)

    try:
        if args.sentence:
            sentence = " ".join(args.sentence).strip()
            return session.run_one_shot(sentence)
        return session.run()
    except Exception as exc:
        print("Error: {0}".format(exc), file=sys.stderr)
        return 1


class InteractiveSentenceAnalysisSession:
    """Interactive sentence-analysis REPL with model switching support."""

    def __init__(self, initial_model: str) -> None:
        self.active_model = initial_model
        self._extractor = RuleBasedMentionExtractor()
        self._analyzers: dict[str, SpacySentenceAnalyzer] = {}

    def run(self) -> int:
        """Start the interactive sentence-analysis loop."""

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
        """Analyze one sentence and exit."""

        self._analyze_and_print(sentence)
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

        self._analyze_and_print(command)
        return True

    def _print_startup(self) -> None:
        self._print_current_state()

    def _print_current_state(self) -> None:
        print("Active spaCy model: {0}".format(self.active_model))

    def _print_help(self) -> None:
        self._print_current_state()
        print("Commands:")
        print("  help")
        print("  list models")
        print("  set model=<spacy_model_name>")
        print("  exit")
        print("  quit")
        print("Any other non-empty input is treated as a sentence to analyze.")

    def _print_available_models(self) -> None:
        print("spaCy package installed: {0}".format("yes" if _is_package_available("spacy") else "no"))
        print("Known English spaCy models:")
        for model_name in _iter_known_model_names(self.active_model):
            installed = "installed" if _is_package_available(model_name) else "not installed"
            active = " active" if model_name == self.active_model else ""
            print(
                "  {0} ({1}{2})".format(
                    model_name,
                    installed,
                    active,
                )
            )

    def _set_model(self, model_name: str) -> None:
        if not model_name:
            raise ValueError("spaCy model name cannot be empty.")
        self._load_analyzer(model_name)
        self.active_model = model_name
        self._print_current_state()

    def _analyze_and_print(self, sentence: str) -> None:
        trimmed_sentence = sentence.strip()
        if not trimmed_sentence:
            raise ValueError("Sentence must be a non-empty string.")

        analyzer = self._load_analyzer(self.active_model)
        analyzed_sentence = analyzer.analyze_sentence(trimmed_sentence)
        mentions = self._extractor.extract_mentions(analyzed_sentence)
        _print_analyzed_sentence(analyzed_sentence)
        _print_mentions(mentions, analyzed_sentence)

    def _load_analyzer(self, model_name: str) -> SpacySentenceAnalyzer:
        cached_analyzer = self._analyzers.get(model_name)
        if cached_analyzer is not None:
            return cached_analyzer

        analyzer = SpacySentenceAnalyzer(model_name=model_name)
        analyzer.analyze_sentence("This is a validation sentence.")
        self._analyzers[model_name] = analyzer
        return analyzer


def _print_analyzed_sentence(analyzed_sentence: AnalyzedSentence) -> None:
    print("Sentence:")
    print("  {0}".format(analyzed_sentence.text))
    print("Root token index: {0}".format(analyzed_sentence.root_token_index))
    print("Tokens:")
    for index, (token, lemma, pos_tag, dependency_label) in enumerate(
        zip(
            analyzed_sentence.tokens,
            analyzed_sentence.lemmas,
            analyzed_sentence.pos_tags,
            analyzed_sentence.dependency_labels,
        )
    ):
        print(
            "  [{0}] text={1} lemma={2} pos={3} dep={4} head={5}".format(
                index,
                token,
                lemma,
                pos_tag,
                dependency_label,
                analyzed_sentence.head_token_indices[index],
            )
        )

    print("Noun chunks:")
    if not analyzed_sentence.noun_chunks:
        print("  (none)")
    else:
        for noun_chunk in analyzed_sentence.noun_chunks:
            print(
                "  [{0}, {1}) text={2} root={3}".format(
                    noun_chunk.token_start,
                    noun_chunk.token_end,
                    noun_chunk.text,
                    noun_chunk.root_token_index,
                )
            )

    print("Named entities:")
    if not analyzed_sentence.named_entities:
        print("  (none)")
        return

    for named_entity in analyzed_sentence.named_entities:
        print(
            "  [{0}, {1}) text={2} label={3}".format(
                named_entity.token_start,
                named_entity.token_end,
                named_entity.text,
                named_entity.label,
            )
        )


def _print_mentions(mentions: list[Mention], analyzed_sentence: AnalyzedSentence) -> None:
    entities = [mention for mention in mentions if mention.head_type == "entity"]
    events = [mention for mention in mentions if mention.head_type == "event"]

    print("Entities:")
    if not entities:
        print("  (none)")
    else:
        for mention in entities:
            print("  - {0}{1}".format(_format_head_label(mention), _format_modifier_suffix(mention.modifiers)))

    print("Events:")
    if not events:
        print("  (none)")
    else:
        for mention in events:
            print("  - {0}{1}".format(_format_head_label(mention), _format_modifier_suffix(mention.modifiers)))

    print("Head mentions:")
    if not mentions:
        print("  (none)")
        return

    for mention in mentions:
        print(
            "  [{0}, {1}) text={2} normalized={3} lemma={4} retrieval_target={5} type={6} head_type={7} source={8} named_entity={9}".format(
                mention.token_start,
                mention.token_end,
                mention.text,
                mention.normalized_text,
                mention.lemma,
                mention.retrieval_target,
                mention.mention_type,
                mention.head_type,
                mention.source,
                mention.named_entity_label,
            )
        )

    print("Context-aware retrieval queries:")
    for mention in mentions:
        print("---")
        print(build_context_aware_retrieval_query(mention, analyzed_sentence))


def _format_head_label(mention: Mention) -> str:
    if mention.retrieval_target is not None and mention.retrieval_target.strip():
        return mention.retrieval_target
    return mention.text


def _format_modifier_suffix(modifiers: tuple[ModifierMention, ...]) -> str:
    modifier_texts = flatten_modifier_texts(modifiers)
    if not modifier_texts:
        return ""
    return " (modifiers: {0})".format(", ".join(modifier_texts))


def _iter_known_model_names(active_model: str) -> list[str]:
    model_names = list(KNOWN_SPACY_MODELS)
    if active_model not in model_names:
        model_names.append(active_model)
    return model_names


def _is_package_available(package_name: str) -> bool:
    return importlib.util.find_spec(package_name) is not None


if __name__ == "__main__":
    raise SystemExit(main())
