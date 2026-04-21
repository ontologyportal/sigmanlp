from __future__ import annotations

import json
import socket
import time
from dataclasses import dataclass
from typing import Any, Mapping
from urllib import error, request

from llm_types import BackendTextResponse, OllamaGenerationConfig
from prompt_types import SUOKIFPromptPayload


class OllamaGenerationClientError(RuntimeError):
    """Raised when the Ollama generation API request or response is invalid."""


@dataclass(frozen=True)
class OllamaGenerationClient:
    """Direct HTTP client for Ollama's chat generation API."""

    max_retries: int = 3
    retry_backoff_seconds: float = 1.0

    def chat(
        self,
        prompt_payload: SUOKIFPromptPayload,
        config: OllamaGenerationConfig,
    ) -> BackendTextResponse:
        """Generate one assistant response from the supplied prompt payload."""

        payload = {
            "model": config.model,
            "messages": [
                {
                    "role": message.role,
                    "content": message.content,
                }
                for message in prompt_payload.messages
            ],
            "stream": False,
        }
        decoded_response = self._post_chat(
            endpoint=config.base_url.rstrip("/") + "/api/chat",
            payload=payload,
            timeout_seconds=config.timeout_seconds,
        )
        return _parse_ollama_chat_response(decoded_response)

    def _post_chat(
        self,
        *,
        endpoint: str,
        payload: Mapping[str, Any],
        timeout_seconds: float,
    ) -> Mapping[str, Any]:
        request_body = json.dumps(payload).encode("utf-8")

        for attempt in range(1, self.max_retries + 1):
            try:
                http_request = request.Request(
                    endpoint,
                    data=request_body,
                    headers={"Content-Type": "application/json"},
                    method="POST",
                )
                with request.urlopen(http_request, timeout=timeout_seconds) as response:
                    response_body = response.read().decode("utf-8")
                decoded_response = json.loads(response_body)
                if not isinstance(decoded_response, dict):
                    raise OllamaGenerationClientError(
                        "Ollama response must be a JSON object."
                    )
                return decoded_response
            except error.HTTPError as exc:
                response_text = _read_http_error_body(exc)
                if not _should_retry_http_error(exc.code) or attempt == self.max_retries:
                    raise OllamaGenerationClientError(
                        "Ollama generation request failed with HTTP {0}: {1}".format(
                            exc.code,
                            response_text,
                        )
                    ) from exc
            except (error.URLError, TimeoutError, socket.timeout, json.JSONDecodeError) as exc:
                if attempt == self.max_retries:
                    raise OllamaGenerationClientError(
                        "Ollama generation request failed after {0} attempts: {1}".format(
                            attempt,
                            exc,
                        )
                    ) from exc

            time.sleep(self.retry_backoff_seconds * attempt)

        raise OllamaGenerationClientError("Ollama generation request failed unexpectedly.")


def _parse_ollama_chat_response(
    decoded_response: Mapping[str, Any],
) -> BackendTextResponse:
    message = decoded_response.get("message")
    if not isinstance(message, Mapping):
        raise OllamaGenerationClientError("Ollama response is missing 'message'.")

    content = message.get("content")
    if not isinstance(content, str) or not content.strip():
        raise OllamaGenerationClientError(
            "Ollama response is missing non-empty assistant content."
        )

    finish_reason = None
    if isinstance(decoded_response.get("done_reason"), str):
        finish_reason = decoded_response["done_reason"]
    elif isinstance(decoded_response.get("done"), bool):
        finish_reason = "done" if decoded_response["done"] else "incomplete"

    return BackendTextResponse(
        output_text=content.strip(),
        finish_reason=finish_reason,
        raw_response_text=json.dumps(decoded_response),
    )


def _read_http_error_body(exc: error.HTTPError) -> str:
    try:
        payload = exc.read().decode("utf-8")
    except Exception:
        payload = exc.reason if isinstance(exc.reason, str) else str(exc.reason)
    return payload or "No response body returned."


def _should_retry_http_error(status_code: int) -> bool:
    return status_code in {408, 429, 500, 502, 503, 504}
