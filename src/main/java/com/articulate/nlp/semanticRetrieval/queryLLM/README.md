# Query LLM

## Overview

`queryLLM` is the final stage of the SUMO semantic grounding pipeline. It starts from a raw sentence, reuses the existing retrieval and prompt-construction stages, and then sends the final SUO-KIF conversion prompt to one of three backend families:

- `hosted_chat_api`
  - providers: `openai`, `anthropic`, `openrouter`
- `seq2seq`
  - local Hugging Face seq2seq models such as `google/flan-t5-base`
- `ollama`
  - local Ollama chat/generation models

The internal flow is:

```text
Sentence
  |
  v
retrievalOrchestration
  |
  v
promptConstruction
  |
  v
queryLLM backend client
  |
  v
Generated SUO-KIF text + metadata
```

This stage does not redesign sentence analysis, retrieval, or prompt construction. It consumes those earlier modules as-is and normalizes the final model output into one typed result shape.

## Module Layout

```text
queryLLM/
  llm/
    llm_types.py
    hosted_chat_client.py
    seq2seq_client.py
    ollama_generation_client.py
    llm_query_orchestrator.py
  scripts/
    demo_common.py
    run_hosted_chat_suo_kif_demo.py
    run_seq2seq_suo_kif_demo.py
    run_ollama_suo_kif_demo.py
  README.md
```

## Shared Result Shape

All three backends return a normalized `LLMQueryResult` with:

- `backend`
- `provider`
- `model`
- `sentence`
- `prompt_verbosity`
- `output_text`
- `finish_reason`
- `raw_response_text`
- `prompt_payload`

`prompt_payload` is the exact two-message SUO-KIF prompt that was sent to the backend.

## Backends

### Hosted Chat API

Supported providers:

- `openai`
- `anthropic`
- `openrouter`

Environment variables:

- `OPENAI_API_KEY`
- `ANTHROPIC_API_KEY`
- `OPENROUTER_API_KEY`

An API key is a secret token that identifies your account when this script sends a request to a hosted model provider. You usually get one by:

- creating an account with the provider
- opening that provider's API or developer dashboard
- generating a new API key there
- exporting it in your shell before running the demo

Example:

```bash
export OPENROUTER_API_KEY="your-key-here"
```

Defaults:

- prompt verbosity: `high`
- transport: direct HTTP
- mode: synchronous, non-streaming

### Seq2Seq

This backend uses local Hugging Face seq2seq generation.

Requirements:

- `transformers`
- `torch`
- `sentencepiece`

Defaults:

- model example: `google/flan-t5-base`
- prompt verbosity: `micro`

The two-message prompt payload is flattened deterministically into:

```text
System: ...

User: ...
```

before generation.

### Ollama

This backend uses Ollama's chat API directly.

Defaults:

- prompt verbosity: `high`
- mode: synchronous, non-streaming
- base URL: `http://localhost:11434`

## Running It

All three demos support:

- one-shot sentence mode
- interactive REPL mode
- `text|json` output
- shared retrieval settings
- prompt verbosity control

### Hosted Chat Demo

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/queryLLM/scripts/run_hosted_chat_suo_kif_demo.py \
  "John kicked the ball." \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --embedding-model nomic-embed-text \
  --provider openrouter \
  --model openai/gpt-4.1-mini \
  --verbosity high
```

Interactive mode:

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/queryLLM/scripts/run_hosted_chat_suo_kif_demo.py \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --embedding-model nomic-embed-text \
  --provider openrouter \
  --model openai/gpt-4.1-mini
```

### Seq2Seq Demo

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/queryLLM/scripts/run_seq2seq_suo_kif_demo.py \
  "John kicked the ball." \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --embedding-model nomic-embed-text \
  --model google/flan-t5-base
```

### Ollama Demo

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/queryLLM/scripts/run_ollama_suo_kif_demo.py \
  "John kicked the ball." \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --embedding-model nomic-embed-text \
  --model llama3.1
```

## Interactive Commands

The scripts are intentionally similar to the other semantic-retrieval demos. Depending on backend, the supported commands include:

- `help`
- `list models`
- `set spacy_model=<name>`
- `set verbosity=micro|low|medium|high`
- `set format=text|json`
- retrieval selection controls:
  - `set k=<number>`
  - `set final_k=<number>`
  - `set pool=<number>`
  - `set mode=fixed_k|score_band|gap_drop`
  - `set min_k=<number>`
  - `set max_k=<number>`
  - `set score_band_delta=<number>`
  - `set gap_drop_threshold=<number>`

Backend-specific commands:

- hosted chat:
  - `set provider=openai|anthropic|openrouter`
  - `set llm_model=<provider_model_name>`
  - `set timeout=<seconds>`
  - `set max_output_tokens=<number>`
  - `set base_url=<url>`
- seq2seq:
  - `set model=<seq2seq_model_name>`
  - `set max_new_tokens=<number>`
- ollama:
  - `set model=<ollama_model_name>`
  - `set timeout=<seconds>`
  - `set base_url=<url>`

## Output

### Text Mode

Text mode prints:

- sentence
- backend / provider / model metadata
- prompt verbosity
- finish reason when available
- the exact prompt payload
- generated SUO-KIF text

### JSON Mode

JSON mode prints the normalized `LLMQueryResult`, including:

- `output_text`
- `finish_reason`
- `raw_response_text`
- nested `prompt_payload`

This is useful for debugging prompt shape and backend output together.

## Error Handling

The module fails clearly for:

- missing hosted API keys
- unsupported hosted provider names
- malformed provider responses with no extractable text
- Ollama connectivity or HTTP failures
- seq2seq model load / generation failures
- invalid candidate-selection settings
- embedding-model / FAISS metadata mismatches surfaced from retrieval orchestration

## Notes

- OpenRouter is treated as a provider inside `hosted_chat_api`, not as a fourth backend.
- V1 is synchronous and non-streaming for all backends.
- Hosted chat and Ollama default to the most verbose prompt style: `high`.
- Seq2seq defaults to `micro` to keep the input compact for smaller local models.
