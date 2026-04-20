from __future__ import annotations

import json
import socket
import time
from dataclasses import dataclass
from typing import Any, Mapping
from urllib import error, request


DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434"


class OllamaEmbeddingError(RuntimeError):
    """Raised when the Ollama embedding API request or response is invalid."""


@dataclass(frozen=True)
class OllamaEmbeddingClient:
    """Direct HTTP client for Ollama's embedding API."""

    base_url: str = DEFAULT_OLLAMA_BASE_URL
    timeout_seconds: float = 120.0
    max_retries: int = 3
    retry_backoff_seconds: float = 1.0

    def embed_text(self, text: str, model: str) -> list[float]:
        """Embed a single text string with the requested Ollama model."""

        embeddings = self._embed_input(text, model)
        if len(embeddings) != 1:
            raise OllamaEmbeddingError(
                "Expected exactly 1 embedding for single-text request,"
                + " received {0}.".format(len(embeddings))
            )
        return embeddings[0]

    def embed_texts(self, texts: list[str], model: str) -> list[list[float]]:
        """Embed a batch of text strings with the requested Ollama model."""

        if not texts:
            return []
        return self._embed_input(texts, model)

    def _embed_input(self, input_value: str | list[str], model: str) -> list[list[float]]:
        _validate_model_name(model)
        _validate_input_value(input_value)

        payload = {
            "model": model,
            "input": input_value,
        }
        response = self._post_embed(payload)
        embeddings = response.get("embeddings")
        if not isinstance(embeddings, list):
            raise OllamaEmbeddingError(
                "Ollama response missing 'embeddings' list."
            )

        parsed_embeddings: list[list[float]] = []
        for vector_index, vector in enumerate(embeddings):
            if not isinstance(vector, list):
                raise OllamaEmbeddingError(
                    "Embedding {0} is not a list.".format(vector_index)
                )
            parsed_vector: list[float] = []
            for dimension_index, value in enumerate(vector):
                if not isinstance(value, (int, float)) or isinstance(value, bool):
                    raise OllamaEmbeddingError(
                        "Embedding {0} dimension {1} is not numeric.".format(
                            vector_index,
                            dimension_index,
                        )
                    )
                parsed_vector.append(float(value))
            parsed_embeddings.append(parsed_vector)

        expected_count = 1 if isinstance(input_value, str) else len(input_value)
        if len(parsed_embeddings) != expected_count:
            raise OllamaEmbeddingError(
                "Expected {0} embeddings, received {1}.".format(
                    expected_count,
                    len(parsed_embeddings),
                )
            )

        return parsed_embeddings

    def _post_embed(self, payload: Mapping[str, Any]) -> Mapping[str, Any]:
        endpoint = self.base_url.rstrip("/") + "/api/embed"
        request_body = json.dumps(payload).encode("utf-8")

        for attempt in range(1, self.max_retries + 1):
            try:
                http_request = request.Request(
                    endpoint,
                    data=request_body,
                    headers={"Content-Type": "application/json"},
                    method="POST",
                )
                with request.urlopen(http_request, timeout=self.timeout_seconds) as response:
                    response_body = response.read().decode("utf-8")
                decoded_response = json.loads(response_body)
                if not isinstance(decoded_response, dict):
                    raise OllamaEmbeddingError("Ollama response must be a JSON object.")
                return decoded_response
            except error.HTTPError as exc:
                response_text = _read_http_error_body(exc)
                if not _should_retry_http_error(exc.code) or attempt == self.max_retries:
                    raise OllamaEmbeddingError(
                        "Ollama embedding request failed with HTTP {0}: {1}".format(
                            exc.code,
                            response_text,
                        )
                    ) from exc
            except (error.URLError, TimeoutError, socket.timeout, json.JSONDecodeError) as exc:
                if attempt == self.max_retries:
                    raise OllamaEmbeddingError(
                        "Ollama embedding request failed after {0} attempts: {1}".format(
                            attempt,
                            exc,
                        )
                    ) from exc

            time.sleep(self.retry_backoff_seconds * attempt)

        raise OllamaEmbeddingError("Ollama embedding request failed unexpectedly.")


def embed_text(
    text: str,
    model: str,
    *,
    base_url: str = DEFAULT_OLLAMA_BASE_URL,
    timeout_seconds: float = 120.0,
    max_retries: int = 3,
    retry_backoff_seconds: float = 1.0,
) -> list[float]:
    """Convenience wrapper for embedding a single text string."""

    client = OllamaEmbeddingClient(
        base_url=base_url,
        timeout_seconds=timeout_seconds,
        max_retries=max_retries,
        retry_backoff_seconds=retry_backoff_seconds,
    )
    return client.embed_text(text, model)


def embed_texts(
    texts: list[str],
    model: str,
    *,
    base_url: str = DEFAULT_OLLAMA_BASE_URL,
    timeout_seconds: float = 120.0,
    max_retries: int = 3,
    retry_backoff_seconds: float = 1.0,
) -> list[list[float]]:
    """Convenience wrapper for embedding a batch of text strings."""

    client = OllamaEmbeddingClient(
        base_url=base_url,
        timeout_seconds=timeout_seconds,
        max_retries=max_retries,
        retry_backoff_seconds=retry_backoff_seconds,
    )
    return client.embed_texts(texts, model)


def _validate_model_name(model: str) -> None:
    if not isinstance(model, str) or not model.strip():
        raise OllamaEmbeddingError("Embedding model name must be a non-empty string.")


def _validate_input_value(input_value: str | list[str]) -> None:
    if isinstance(input_value, str):
        if not input_value:
            raise OllamaEmbeddingError("Input text must be a non-empty string.")
        return

    if not isinstance(input_value, list):
        raise OllamaEmbeddingError("Embedding input must be a string or list of strings.")

    for index, text in enumerate(input_value):
        if not isinstance(text, str):
            raise OllamaEmbeddingError(
                "Batch input item {0} is not a string.".format(index)
            )
        if not text:
            raise OllamaEmbeddingError(
                "Batch input item {0} is an empty string.".format(index)
            )


def _read_http_error_body(exc: error.HTTPError) -> str:
    try:
        payload = exc.read().decode("utf-8")
    except Exception:
        payload = exc.reason if isinstance(exc.reason, str) else str(exc.reason)
    return payload or "No response body returned."


def _should_retry_http_error(status_code: int) -> bool:
    return status_code in {408, 429, 500, 502, 503, 504}
