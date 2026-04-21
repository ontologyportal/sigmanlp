from __future__ import annotations

import sys
from dataclasses import dataclass, field
from pathlib import Path


THIS_DIR = Path(__file__).resolve().parent
QUERY_LLM_DIR = THIS_DIR.parent
SEMANTIC_RETRIEVAL_DIR = QUERY_LLM_DIR.parent
PROMPT_DIR = SEMANTIC_RETRIEVAL_DIR / "promptConstruction" / "prompt"
RETRIEVAL_DIR = SEMANTIC_RETRIEVAL_DIR / "retrievalOrchestration" / "retrieval"
SENTENCE_ANALYSIS_DIR = SEMANTIC_RETRIEVAL_DIR / "sentenceAnalysis" / "analysis"
UTILS_DIR = SEMANTIC_RETRIEVAL_DIR / "utils"
for path in [PROMPT_DIR, RETRIEVAL_DIR, SENTENCE_ANALYSIS_DIR, UTILS_DIR]:
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from candidate_selection import CandidateSelectionConfig
from faiss_candidate_search import FaissCandidateSearcher
from hosted_chat_client import HostedChatClient
from llm_types import (
    DEFAULT_HOSTED_PROMPT_VERBOSITY,
    DEFAULT_OLLAMA_PROMPT_VERBOSITY,
    DEFAULT_SEQ2SEQ_PROMPT_VERBOSITY,
    HostedChatConfig,
    LLMQueryResult,
    OllamaGenerationConfig,
    Seq2SeqConfig,
)
from ollama_client import DEFAULT_OLLAMA_BASE_URL
from ollama_generation_client import OllamaGenerationClient
from prompt_types import PromptVerbosity, SUOKIFPromptPayload
from semantic_retrieval_orchestrator import SemanticRetrievalOrchestrator
from sentence_analyzer import DEFAULT_SPACY_MODEL, SpacySentenceAnalyzer
from seq2seq_client import Seq2SeqClient
from suo_kif_prompt_builder import SUOKIFPromptBuilder


class LLMQueryOrchestrationError(RuntimeError):
    """Raised when end-to-end prompt construction and query execution fails."""


@dataclass
class LLMQueryOrchestrator:
    """Run retrieval, prompt construction, and backend query as one final stage."""

    faiss_searcher: FaissCandidateSearcher
    embedding_model: str
    ollama_base_url: str = DEFAULT_OLLAMA_BASE_URL
    prompt_builder: SUOKIFPromptBuilder | None = None
    hosted_chat_client: HostedChatClient | None = None
    seq2seq_client: Seq2SeqClient | None = None
    ollama_generation_client: OllamaGenerationClient | None = None
    _retrieval_orchestrators: dict[str, SemanticRetrievalOrchestrator] = field(
        init=False,
        default_factory=dict,
        repr=False,
    )

    def __post_init__(self) -> None:
        if not isinstance(self.embedding_model, str) or not self.embedding_model.strip():
            raise LLMQueryOrchestrationError(
                "embedding_model must be a non-empty string."
            )
        self._prompt_builder = self.prompt_builder or SUOKIFPromptBuilder()
        self._hosted_chat_client = self.hosted_chat_client or HostedChatClient()
        self._seq2seq_client = self.seq2seq_client or Seq2SeqClient()
        self._ollama_generation_client = (
            self.ollama_generation_client or OllamaGenerationClient()
        )

    def build_prompt_payload(
        self,
        sentence: str,
        *,
        spacy_model: str = DEFAULT_SPACY_MODEL,
        prompt_verbosity: PromptVerbosity,
        selection_config: CandidateSelectionConfig,
    ) -> SUOKIFPromptPayload:
        """Build a final prompt payload from a raw sentence."""

        retrieval_orchestrator = self._load_retrieval_orchestrator(spacy_model)
        retrieval_result = retrieval_orchestrator.retrieve_sentence_with_selection(
            sentence,
            selection_config=selection_config,
        )
        return self._prompt_builder.build_prompt_payload(
            retrieval_result=retrieval_result,
            verbosity=prompt_verbosity,
        )

    def query_hosted_chat(
        self,
        sentence: str,
        *,
        hosted_config: HostedChatConfig,
        selection_config: CandidateSelectionConfig,
        prompt_verbosity: PromptVerbosity = DEFAULT_HOSTED_PROMPT_VERBOSITY,
        spacy_model: str = DEFAULT_SPACY_MODEL,
    ) -> LLMQueryResult:
        """Run the full pipeline and query a hosted chat provider."""

        prompt_payload = self.build_prompt_payload(
            sentence,
            spacy_model=spacy_model,
            prompt_verbosity=prompt_verbosity,
            selection_config=selection_config,
        )
        backend_response = self._hosted_chat_client.complete(prompt_payload, hosted_config)
        return LLMQueryResult(
            backend="hosted_chat_api",
            provider=hosted_config.provider,
            model=hosted_config.model,
            sentence=prompt_payload.sentence,
            prompt_verbosity=prompt_payload.verbosity,
            output_text=backend_response.output_text,
            finish_reason=backend_response.finish_reason,
            raw_response_text=backend_response.raw_response_text,
            prompt_payload=prompt_payload,
        )

    def query_seq2seq(
        self,
        sentence: str,
        *,
        seq2seq_config: Seq2SeqConfig,
        selection_config: CandidateSelectionConfig,
        prompt_verbosity: PromptVerbosity = DEFAULT_SEQ2SEQ_PROMPT_VERBOSITY,
        spacy_model: str = DEFAULT_SPACY_MODEL,
    ) -> LLMQueryResult:
        """Run the full pipeline and query a local seq2seq model."""

        prompt_payload = self.build_prompt_payload(
            sentence,
            spacy_model=spacy_model,
            prompt_verbosity=prompt_verbosity,
            selection_config=selection_config,
        )
        backend_response = self._seq2seq_client.generate(prompt_payload, seq2seq_config)
        return LLMQueryResult(
            backend="seq2seq",
            provider=None,
            model=seq2seq_config.model,
            sentence=prompt_payload.sentence,
            prompt_verbosity=prompt_payload.verbosity,
            output_text=backend_response.output_text,
            finish_reason=backend_response.finish_reason,
            raw_response_text=backend_response.raw_response_text,
            prompt_payload=prompt_payload,
        )

    def query_ollama(
        self,
        sentence: str,
        *,
        ollama_config: OllamaGenerationConfig,
        selection_config: CandidateSelectionConfig,
        prompt_verbosity: PromptVerbosity = DEFAULT_OLLAMA_PROMPT_VERBOSITY,
        spacy_model: str = DEFAULT_SPACY_MODEL,
    ) -> LLMQueryResult:
        """Run the full pipeline and query an Ollama chat model."""

        prompt_payload = self.build_prompt_payload(
            sentence,
            spacy_model=spacy_model,
            prompt_verbosity=prompt_verbosity,
            selection_config=selection_config,
        )
        backend_response = self._ollama_generation_client.chat(prompt_payload, ollama_config)
        return LLMQueryResult(
            backend="ollama",
            provider=None,
            model=ollama_config.model,
            sentence=prompt_payload.sentence,
            prompt_verbosity=prompt_payload.verbosity,
            output_text=backend_response.output_text,
            finish_reason=backend_response.finish_reason,
            raw_response_text=backend_response.raw_response_text,
            prompt_payload=prompt_payload,
        )

    def _load_retrieval_orchestrator(self, spacy_model: str) -> SemanticRetrievalOrchestrator:
        cached_orchestrator = self._retrieval_orchestrators.get(spacy_model)
        if cached_orchestrator is not None:
            return cached_orchestrator

        sentence_analyzer = SpacySentenceAnalyzer(model_name=spacy_model)
        try:
            sentence_analyzer.analyze_sentence("This is a validation sentence.")
        except Exception as exc:
            raise LLMQueryOrchestrationError(
                "Unable to initialize spaCy model '{0}': {1}".format(spacy_model, exc)
            ) from exc

        retrieval_orchestrator = SemanticRetrievalOrchestrator(
            faiss_searcher=self.faiss_searcher,
            embedding_model=self.embedding_model,
            ollama_base_url=self.ollama_base_url,
            sentence_analyzer=sentence_analyzer,
        )
        self._retrieval_orchestrators[spacy_model] = retrieval_orchestrator
        return retrieval_orchestrator
