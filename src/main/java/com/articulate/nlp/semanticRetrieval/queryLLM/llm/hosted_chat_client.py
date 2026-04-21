from __future__ import annotations

import json
import os
import socket
import time
from dataclasses import dataclass
from typing import Any, Mapping
from urllib import error, request

from llm_types import BackendTextResponse, HostedChatConfig
from prompt_types import SUOKIFPromptPayload


OPENAI_CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions"
OPENROUTER_CHAT_COMPLETIONS_URL = "https://openrouter.ai/api/v1/chat/completions"
ANTHROPIC_MESSAGES_URL = "https://api.anthropic.com/v1/messages"
DEFAULT_ANTHROPIC_MAX_OUTPUT_TOKENS = 1024


class HostedChatClientError(RuntimeError):
    """Raised when a hosted LLM request or response is invalid."""


@dataclass(frozen=True)
class HostedChatClient:
    """Direct HTTP client for OpenAI, Anthropic, and OpenRouter chat APIs."""

    max_retries: int = 3
    retry_backoff_seconds: float = 1.0

    def complete(
        self,
        prompt_payload: SUOKIFPromptPayload,
        config: HostedChatConfig,
    ) -> BackendTextResponse:
        """Send one prompt payload to the configured hosted provider."""

        if config.provider == "anthropic":
            return self._complete_anthropic(prompt_payload, config)
        if config.provider in {"openai", "openrouter"}:
            return self._complete_openai_compatible(prompt_payload, config)
        raise HostedChatClientError(
            "Unsupported hosted provider '{0}'.".format(config.provider)
        )

    def _complete_openai_compatible(
        self,
        prompt_payload: SUOKIFPromptPayload,
        config: HostedChatConfig,
    ) -> BackendTextResponse:
        api_key = _get_required_api_key(config.provider)
        endpoint = _resolve_openai_compatible_endpoint(config)
        payload: dict[str, Any] = {
            "model": config.model,
            "messages": [
                {
                    "role": message.role,
                    "content": message.content,
                }
                for message in prompt_payload.messages
            ],
        }
        if config.max_output_tokens is not None:
            if config.provider == "openai":
                payload["max_completion_tokens"] = config.max_output_tokens
            else:
                payload["max_tokens"] = config.max_output_tokens

        headers = {
            "Authorization": "Bearer {0}".format(api_key),
            "Content-Type": "application/json",
        }
        decoded_response = self._post_json(
            endpoint=endpoint,
            headers=headers,
            payload=payload,
            timeout_seconds=config.timeout_seconds,
        )
        return _parse_openai_compatible_response(decoded_response)

    def _complete_anthropic(
        self,
        prompt_payload: SUOKIFPromptPayload,
        config: HostedChatConfig,
    ) -> BackendTextResponse:
        api_key = _get_required_api_key(config.provider)
        system_message = prompt_payload.messages[0].content
        user_message = prompt_payload.messages[1].content
        endpoint = config.base_url or ANTHROPIC_MESSAGES_URL
        payload = {
            "model": config.model,
            "system": system_message,
            "messages": [
                {
                    "role": "user",
                    "content": user_message,
                }
            ],
            "max_tokens": config.max_output_tokens or DEFAULT_ANTHROPIC_MAX_OUTPUT_TOKENS,
        }
        headers = {
            "x-api-key": api_key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        }
        decoded_response = self._post_json(
            endpoint=endpoint,
            headers=headers,
            payload=payload,
            timeout_seconds=config.timeout_seconds,
        )
        return _parse_anthropic_response(decoded_response)

    def _post_json(
        self,
        *,
        endpoint: str,
        headers: Mapping[str, str],
        payload: Mapping[str, Any],
        timeout_seconds: float,
    ) -> Mapping[str, Any]:
        request_body = json.dumps(payload).encode("utf-8")

        for attempt in range(1, self.max_retries + 1):
            try:
                http_request = request.Request(
                    endpoint,
                    data=request_body,
                    headers=dict(headers),
                    method="POST",
                )
                with request.urlopen(http_request, timeout=timeout_seconds) as response:
                    response_body = response.read().decode("utf-8")
                decoded_response = json.loads(response_body)
                if not isinstance(decoded_response, dict):
                    raise HostedChatClientError("Hosted provider response must be a JSON object.")
                return decoded_response
            except error.HTTPError as exc:
                response_text = _read_http_error_body(exc)
                if not _should_retry_http_error(exc.code) or attempt == self.max_retries:
                    raise HostedChatClientError(
                        "Hosted provider request failed with HTTP {0}: {1}".format(
                            exc.code,
                            response_text,
                        )
                    ) from exc
            except (error.URLError, TimeoutError, socket.timeout, json.JSONDecodeError) as exc:
                if attempt == self.max_retries:
                    raise HostedChatClientError(
                        "Hosted provider request failed after {0} attempts: {1}".format(
                            attempt,
                            exc,
                        )
                    ) from exc

            time.sleep(self.retry_backoff_seconds * attempt)

        raise HostedChatClientError("Hosted provider request failed unexpectedly.")


def _get_required_api_key(provider: str) -> str:
    env_var = {
        "openai": "OPENAI_API_KEY",
        "openrouter": "OPENROUTER_API_KEY",
        "anthropic": "ANTHROPIC_API_KEY",
    }.get(provider)
    if env_var is None:
        raise HostedChatClientError(
            "Unsupported hosted provider '{0}'.".format(provider)
        )

    api_key = os.environ.get(env_var)
    if api_key is None or not api_key.strip():
        raise HostedChatClientError(
            "Missing required API key environment variable: {0}.".format(env_var)
        )
    return api_key.strip()


def _resolve_openai_compatible_endpoint(config: HostedChatConfig) -> str:
    if config.base_url is not None and config.base_url.strip():
        return config.base_url.strip()
    if config.provider == "openai":
        return OPENAI_CHAT_COMPLETIONS_URL
    if config.provider == "openrouter":
        return OPENROUTER_CHAT_COMPLETIONS_URL
    raise HostedChatClientError(
        "Provider '{0}' does not use an OpenAI-compatible endpoint.".format(config.provider)
    )


def _parse_openai_compatible_response(
    decoded_response: Mapping[str, Any],
) -> BackendTextResponse:
    choices = decoded_response.get("choices")
    if not isinstance(choices, list) or not choices:
        raise HostedChatClientError(
            "Hosted provider response is missing a non-empty 'choices' list."
        )

    first_choice = choices[0]
    if not isinstance(first_choice, Mapping):
        raise HostedChatClientError("Hosted provider response choice is not an object.")

    message = first_choice.get("message")
    if not isinstance(message, Mapping):
        raise HostedChatClientError(
            "Hosted provider response is missing 'choices[0].message'."
        )

    output_text = _extract_openai_compatible_text(message.get("content"))
    finish_reason = _coerce_optional_string(first_choice.get("finish_reason"))
    return BackendTextResponse(
        output_text=output_text,
        finish_reason=finish_reason,
        raw_response_text=json.dumps(decoded_response),
    )


def _parse_anthropic_response(decoded_response: Mapping[str, Any]) -> BackendTextResponse:
    content_blocks = decoded_response.get("content")
    if not isinstance(content_blocks, list) or not content_blocks:
        raise HostedChatClientError(
            "Anthropic response is missing a non-empty 'content' list."
        )

    text_chunks: list[str] = []
    for block in content_blocks:
        if not isinstance(block, Mapping):
            continue
        if block.get("type") != "text":
            continue
        text_value = block.get("text")
        if isinstance(text_value, str) and text_value.strip():
            text_chunks.append(text_value)

    if not text_chunks:
        raise HostedChatClientError(
            "Anthropic response contained no text content blocks."
        )

    return BackendTextResponse(
        output_text="\n".join(text_chunks).strip(),
        finish_reason=_coerce_optional_string(decoded_response.get("stop_reason")),
        raw_response_text=json.dumps(decoded_response),
    )


def _extract_openai_compatible_text(content: Any) -> str:
    if isinstance(content, str) and content.strip():
        return content.strip()

    if isinstance(content, list):
        text_chunks: list[str] = []
        for block in content:
            if not isinstance(block, Mapping):
                continue
            block_type = block.get("type")
            if block_type == "text":
                text_value = block.get("text")
                if isinstance(text_value, str) and text_value.strip():
                    text_chunks.append(text_value)
                continue
            if "text" in block and isinstance(block["text"], str) and block["text"].strip():
                text_chunks.append(block["text"])
        joined_text = "\n".join(text_chunks).strip()
        if joined_text:
            return joined_text

    raise HostedChatClientError(
        "Hosted provider response contained no extractable assistant text."
    )


def _coerce_optional_string(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, str):
        return value
    return str(value)


def _read_http_error_body(exc: error.HTTPError) -> str:
    try:
        payload = exc.read().decode("utf-8")
    except Exception:
        payload = exc.reason if isinstance(exc.reason, str) else str(exc.reason)
    return payload or "No response body returned."


def _should_retry_http_error(status_code: int) -> bool:
    return status_code in {408, 429, 500, 502, 503, 504}
