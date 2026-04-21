from __future__ import annotations

import importlib.util
import json
import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
QUERY_LLM_DIR = SCRIPT_DIR.parent
LLM_DIR = QUERY_LLM_DIR / "llm"
PROMPT_DIR = QUERY_LLM_DIR.parent / "promptConstruction" / "prompt"
RETRIEVAL_DIR = QUERY_LLM_DIR.parent / "retrievalOrchestration" / "retrieval"
SENTENCE_ANALYSIS_DIR = QUERY_LLM_DIR.parent / "sentenceAnalysis" / "analysis"
for path in [LLM_DIR, PROMPT_DIR, RETRIEVAL_DIR, SENTENCE_ANALYSIS_DIR]:
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from llm_types import LLMQueryResult


KNOWN_SPACY_MODELS = [
    "en_core_web_sm",
    "en_core_web_md",
    "en_core_web_lg",
    "en_core_web_trf",
]


def print_query_result(
    result: LLMQueryResult,
    *,
    output_format: str,
) -> None:
    """Print an LLM query result as text or JSON."""

    if output_format == "json":
        print(json.dumps(serialize_query_result(result), indent=2))
        return

    print("Sentence:")
    print("  {0}".format(result.sentence))
    print("Backend:")
    print("  {0}".format(result.backend))
    if result.provider is not None:
        print("Provider:")
        print("  {0}".format(result.provider))
    print("Model:")
    print("  {0}".format(result.model))
    print("Prompt verbosity:")
    print("  {0}".format(result.prompt_verbosity))
    if result.finish_reason is not None:
        print("Finish reason:")
        print("  {0}".format(result.finish_reason))
    print("Prompt payload:")
    for message in result.prompt_payload.messages:
        print("[{0}]".format(message.role))
        print_indented_block(message.content, indent="  ")
    print("Generated SUO-KIF:")
    print_indented_block(result.output_text, indent="  ")


def serialize_query_result(result: LLMQueryResult) -> dict[str, object]:
    """Serialize an LLMQueryResult into JSON-friendly nested data."""

    return {
        "backend": result.backend,
        "provider": result.provider,
        "model": result.model,
        "sentence": result.sentence,
        "prompt_verbosity": result.prompt_verbosity,
        "output_text": result.output_text,
        "finish_reason": result.finish_reason,
        "raw_response_text": result.raw_response_text,
        "prompt_payload": {
            "sentence": result.prompt_payload.sentence,
            "verbosity": result.prompt_payload.verbosity,
            "messages": [
                {
                    "role": message.role,
                    "content": message.content,
                }
                for message in result.prompt_payload.messages
            ],
        },
    }


def print_indented_block(text: str, *, indent: str) -> None:
    """Print one text block with consistent indentation."""

    for line in text.splitlines():
        print("{0}{1}".format(indent, line))


def print_available_spacy_models(active_model: str) -> None:
    """Print known English spaCy models plus installation status."""

    print("spaCy package installed: {0}".format("yes" if is_package_available("spacy") else "no"))
    print("Known English spaCy models:")
    for model_name in iter_known_spacy_model_names(active_model):
        installed = "installed" if is_package_available(model_name) else "not installed"
        active = " active" if model_name == active_model else ""
        print("  {0} ({1}{2})".format(model_name, installed, active))


def iter_known_spacy_model_names(active_model: str) -> list[str]:
    """Return known spaCy model names with the active model included."""

    model_names = list(KNOWN_SPACY_MODELS)
    if active_model not in model_names:
        model_names.append(active_model)
    return model_names


def is_package_available(package_name: str) -> bool:
    """Return whether the given import target is installed and importable."""

    return importlib.util.find_spec(package_name) is not None
