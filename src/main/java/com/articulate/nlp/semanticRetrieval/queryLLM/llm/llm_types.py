from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Literal


THIS_DIR = Path(__file__).resolve().parent
QUERY_LLM_DIR = THIS_DIR.parent
SEMANTIC_RETRIEVAL_DIR = QUERY_LLM_DIR.parent
PROMPT_DIR = SEMANTIC_RETRIEVAL_DIR / "promptConstruction" / "prompt"
if str(PROMPT_DIR) not in sys.path:
    sys.path.insert(0, str(PROMPT_DIR))

from prompt_types import PromptVerbosity, SUOKIFPromptPayload, VALID_PROMPT_VERBOSITIES


LLMBackend = Literal["hosted_chat_api", "seq2seq", "ollama"]
HostedProvider = Literal["openai", "anthropic", "openrouter"]
VALID_LLM_BACKENDS = {"hosted_chat_api", "seq2seq", "ollama"}
VALID_HOSTED_PROVIDERS = {"openai", "anthropic", "openrouter"}

DEFAULT_HOSTED_PROMPT_VERBOSITY: PromptVerbosity = "high"
DEFAULT_OLLAMA_PROMPT_VERBOSITY: PromptVerbosity = "high"
DEFAULT_SEQ2SEQ_PROMPT_VERBOSITY: PromptVerbosity = "micro"
DEFAULT_SEQ2SEQ_MODEL = "google/flan-t5-base"


@dataclass(frozen=True)
class HostedChatConfig:
    """Configuration for querying a hosted chat-completions style provider."""

    provider: HostedProvider
    model: str
    timeout_seconds: float = 120.0
    max_output_tokens: int | None = None
    base_url: str | None = None

    def __post_init__(self) -> None:
        if self.provider not in VALID_HOSTED_PROVIDERS:
            raise ValueError(
                "provider must be one of: {0}.".format(
                    ", ".join(sorted(VALID_HOSTED_PROVIDERS))
                )
            )
        if not isinstance(self.model, str) or not self.model.strip():
            raise ValueError("HostedChatConfig.model must be a non-empty string.")
        if self.timeout_seconds <= 0:
            raise ValueError("HostedChatConfig.timeout_seconds must be greater than zero.")
        if self.max_output_tokens is not None and self.max_output_tokens <= 0:
            raise ValueError("HostedChatConfig.max_output_tokens must be greater than zero.")


@dataclass(frozen=True)
class Seq2SeqConfig:
    """Configuration for local seq2seq generation."""

    model: str = DEFAULT_SEQ2SEQ_MODEL
    max_new_tokens: int = 256

    def __post_init__(self) -> None:
        if not isinstance(self.model, str) or not self.model.strip():
            raise ValueError("Seq2SeqConfig.model must be a non-empty string.")
        if self.max_new_tokens <= 0:
            raise ValueError("Seq2SeqConfig.max_new_tokens must be greater than zero.")


@dataclass(frozen=True)
class OllamaGenerationConfig:
    """Configuration for querying an Ollama chat/generation model."""

    model: str
    base_url: str = "http://localhost:11434"
    timeout_seconds: float = 120.0

    def __post_init__(self) -> None:
        if not isinstance(self.model, str) or not self.model.strip():
            raise ValueError("OllamaGenerationConfig.model must be a non-empty string.")
        if not isinstance(self.base_url, str) or not self.base_url.strip():
            raise ValueError("OllamaGenerationConfig.base_url must be a non-empty string.")
        if self.timeout_seconds <= 0:
            raise ValueError("OllamaGenerationConfig.timeout_seconds must be greater than zero.")


@dataclass(frozen=True)
class BackendTextResponse:
    """Normalized text output returned by one LLM backend client."""

    output_text: str
    finish_reason: str | None
    raw_response_text: str | None

    def __post_init__(self) -> None:
        if not isinstance(self.output_text, str) or not self.output_text.strip():
            raise ValueError("BackendTextResponse.output_text must be a non-empty string.")


@dataclass(frozen=True)
class LLMQueryResult:
    """Normalized final output of the queryLLM stage."""

    backend: LLMBackend
    provider: str | None
    model: str
    sentence: str
    prompt_verbosity: PromptVerbosity
    output_text: str
    finish_reason: str | None
    raw_response_text: str | None
    prompt_payload: SUOKIFPromptPayload

    def __post_init__(self) -> None:
        if self.backend not in VALID_LLM_BACKENDS:
            raise ValueError(
                "LLMQueryResult.backend must be one of: {0}.".format(
                    ", ".join(sorted(VALID_LLM_BACKENDS))
                )
            )
        if not isinstance(self.model, str) or not self.model.strip():
            raise ValueError("LLMQueryResult.model must be a non-empty string.")
        if not isinstance(self.sentence, str) or not self.sentence.strip():
            raise ValueError("LLMQueryResult.sentence must be a non-empty string.")
        if self.prompt_verbosity not in VALID_PROMPT_VERBOSITIES:
            raise ValueError(
                "LLMQueryResult.prompt_verbosity must be one of: {0}.".format(
                    ", ".join(sorted(VALID_PROMPT_VERBOSITIES))
                )
            )
        if not isinstance(self.output_text, str) or not self.output_text.strip():
            raise ValueError("LLMQueryResult.output_text must be a non-empty string.")
