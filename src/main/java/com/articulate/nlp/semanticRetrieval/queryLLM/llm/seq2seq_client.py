from __future__ import annotations

from dataclasses import dataclass

from llm_types import BackendTextResponse, Seq2SeqConfig
from prompt_types import SUOKIFPromptPayload


class Seq2SeqClientError(RuntimeError):
    """Raised when local seq2seq model loading or generation fails."""


@dataclass
class Seq2SeqClient:
    """Local Hugging Face seq2seq generator for SUO-KIF prompts."""

    _model_name: str | None = None
    _tokenizer: object | None = None
    _model: object | None = None

    def generate(
        self,
        prompt_payload: SUOKIFPromptPayload,
        config: Seq2SeqConfig,
    ) -> BackendTextResponse:
        """Generate text from a flattened prompt payload."""

        tokenizer, model = self._load_model(config.model)
        prompt_text = flatten_prompt_payload(prompt_payload)

        try:
            encoded_inputs = tokenizer(prompt_text, return_tensors="pt")
            generation_output = model.generate(
                **encoded_inputs,
                max_new_tokens=config.max_new_tokens,
            )
            decoded_text = tokenizer.decode(
                generation_output[0],
                skip_special_tokens=True,
            ).strip()
        except Exception as exc:
            raise Seq2SeqClientError(
                "Unable to generate text with seq2seq model '{0}': {1}".format(
                    config.model,
                    exc,
                )
            ) from exc

        if not decoded_text:
            raise Seq2SeqClientError(
                "Seq2seq model '{0}' returned an empty generation.".format(config.model)
            )

        return BackendTextResponse(
            output_text=decoded_text,
            finish_reason="generated",
            raw_response_text=decoded_text,
        )

    def _load_model(self, model_name: str) -> tuple[object, object]:
        if self._model_name == model_name and self._tokenizer is not None and self._model is not None:
            return self._tokenizer, self._model

        try:
            from transformers import AutoModelForSeq2SeqLM, AutoTokenizer  # type: ignore[import-not-found]
        except ImportError as exc:
            raise Seq2SeqClientError(
                "transformers is not installed. Install transformers, torch, and sentencepiece "
                "before using the seq2seq backend."
            ) from exc

        try:
            tokenizer = AutoTokenizer.from_pretrained(model_name)
            model = AutoModelForSeq2SeqLM.from_pretrained(model_name)
            model.eval()
        except Exception as exc:
            raise Seq2SeqClientError(
                "Unable to load seq2seq model '{0}': {1}".format(model_name, exc)
            ) from exc

        self._model_name = model_name
        self._tokenizer = tokenizer
        self._model = model
        return tokenizer, model


def flatten_prompt_payload(prompt_payload: SUOKIFPromptPayload) -> str:
    """Flatten a two-message prompt payload into a deterministic seq2seq prompt."""

    system_message = prompt_payload.messages[0].content
    user_message = prompt_payload.messages[1].content
    return "System: {0}\n\nUser: {1}".format(system_message, user_message)
