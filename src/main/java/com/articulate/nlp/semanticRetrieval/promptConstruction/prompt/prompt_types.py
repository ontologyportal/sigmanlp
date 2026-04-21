from __future__ import annotations

from dataclasses import dataclass
from typing import Literal


PromptVerbosity = Literal["micro", "low", "medium", "high"]
PromptMessageRole = Literal["system", "user"]
VALID_PROMPT_VERBOSITIES = {"micro", "low", "medium", "high"}


@dataclass(frozen=True)
class PromptMessage:
    """One message entry in an LLM prompt payload."""

    role: PromptMessageRole
    content: str

    def __post_init__(self) -> None:
        if self.role not in {"system", "user"}:
            raise ValueError("PromptMessage.role must be either 'system' or 'user'.")
        if not isinstance(self.content, str) or not self.content.strip():
            raise ValueError("PromptMessage.content must be a non-empty string.")


@dataclass(frozen=True)
class SUOKIFPromptPayload:
    """Two-message prompt payload for SUO-KIF conversion."""

    sentence: str
    verbosity: PromptVerbosity
    messages: tuple[PromptMessage, ...]

    def __post_init__(self) -> None:
        if not isinstance(self.sentence, str) or not self.sentence.strip():
            raise ValueError("SUOKIFPromptPayload.sentence must be a non-empty string.")
        if self.verbosity not in VALID_PROMPT_VERBOSITIES:
            raise ValueError(
                "SUOKIFPromptPayload.verbosity must be one of: {0}.".format(
                    ", ".join(sorted(VALID_PROMPT_VERBOSITIES))
                )
            )
        if len(self.messages) != 2:
            raise ValueError("SUOKIFPromptPayload.messages must contain exactly two messages.")
        if self.messages[0].role != "system":
            raise ValueError("The first prompt message must have role='system'.")
        if self.messages[1].role != "user":
            raise ValueError("The second prompt message must have role='user'.")
