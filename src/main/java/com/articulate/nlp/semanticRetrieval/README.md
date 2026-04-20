# Semantic Retrieval

## Overview

`semanticRetrieval` is the end-to-end SUMO grounding pipeline for converting an English sentence into a constrained SUO-KIF query task.

At a high level, the pipeline does five things:

1. export and embed SUMO concepts
2. analyze an input sentence into structured semantic heads and modifiers
3. retrieve SUMO candidates for those semantic units
4. build an LLM prompt that exposes the grounded candidate terms
5. query an LLM to convert the sentence into SUO-KIF

The code is organized into five main modules:

- `buildEmbeddingIndex`
  - exports SUMO to JSONL
  - normalizes English termFormats
  - generates embeddings with Ollama
  - builds the FAISS index and metadata sidecar (FAISS only uses numbers, not words, so the sidecar translates between numbers and words)
- `sentenceAnalysis`
  - analyzes a sentence with spaCy
  - extracts entity heads, event heads, and modifiers. (John kicked the ball -> Human, kicking, ball)
  - keeps attachment structure
- `retrievalOrchestration`
  - embeds terms from the sentence analysis portion.
  - searches FAISS
  - applies post-FAISS candidate selection
  - preserves head/modifier relations in the retrieval result
- `promptConstruction`
  - turns structured retrieval output into a SUO-KIF conversion prompt
  - supports `micro`, `low`, `medium`, and `high` verbosity
- `queryLLM`
  - runs the full pipeline from sentence to final model output
  - supports hosted APIs, local seq2seq, and Ollama

Supporting utilities live in:

- `utils`
  - text normalization
  - Ollama embedding client

## Data Flow

There are really two connected pipelines:

1. an offline index-building pipeline, that takes every SUMO term, and places it into an embedding space.
2. an online sentence-to-SUO-KIF pipeline

### Offline Index Build

```text
SUMO KB
  |
  v
SUMO JSONL export
  |
  v
normalized SUMO JSONL
  |
  v
concept embeddings
  |
  v
FAISS index + metadata sidecar
```

### Online Sentence Query

```text
Sentence
  |
  v
sentenceAnalysis
  |
  v
structured heads + modifiers
  |
  v
retrievalOrchestration
  |
  v
SUMO candidates for heads and modifiers
  |
  v
promptConstruction
  |
  v
SUO-KIF conversion prompt
  |
  v
queryLLM
  |
  v
LLM output
```

## Module Guide

### `buildEmbeddingIndex`

This is the first stage. It prepares the retrieval resources used by everything later.

Outputs:

- raw SUMO export JSONL
- normalized SUMO export JSONL
- embedding artifact
- FAISS index
- metadata JSON

See:

- [buildEmbeddingIndex/README.md](/home/sumorich/workspace/sigmanlp/src/main/java/com/articulate/nlp/semanticRetrieval/buildEmbeddingIndex/README.md:1)

### `sentenceAnalysis`

This stage converts a sentence into a structured semantic representation.

It identifies:

- entity heads
- event heads
- modifiers attached to those heads
- named entities

It keeps syntactic structure because downstream grounding and SUO-KIF translation need more than a flat token list.

See:

- [sentenceAnalysis/README.md](/home/sumorich/workspace/sigmanlp/src/main/java/com/articulate/nlp/semanticRetrieval/sentenceAnalysis/README.md:1)

### `retrievalOrchestration`

This stage takes the sentence-analysis output and grounds it against the SUMO FAISS index.

It:

- builds retrieval strings
- embeds them with Ollama
- retrieves an initial FAISS pool
- applies candidate-selection policies such as `score_band`
- preserves attachment relations between heads and modifiers
- bypasses dense retrieval for certain direct-mapping cases like `PERSON -> Human`

See:

- [retrievalOrchestration/README.md](/home/sumorich/workspace/sigmanlp/src/main/java/com/articulate/nlp/semanticRetrieval/retrievalOrchestration/README.md:1)

### `promptConstruction`

This stage converts structured retrieval output into the final SUO-KIF conversion prompt.

It does not call a model by itself. It only builds the prompt payload.

See:

- [promptConstruction/README.md](/home/sumorich/workspace/sigmanlp/src/main/java/com/articulate/nlp/semanticRetrieval/promptConstruction/README.md:1)

### `queryLLM`

This is the final execution stage. It runs the whole online pipeline from sentence input to model output.

Supported backend families:

- hosted chat APIs
- local seq2seq models
- Ollama

For getting started, Ollama is the simplest default because it keeps both embedding and generation local.

See:

- [queryLLM/README.md](/home/sumorich/workspace/sigmanlp/src/main/java/com/articulate/nlp/semanticRetrieval/queryLLM/README.md:1)

## Getting Started

### Requirements

For the full local workflow shown below, you should have:

- `python3`
- Java available for the SUMO exporter
- `SIGMANLP_CP` set so the Java SUMO exporter can run
- `spaCy` installed
- the spaCy English model `en_core_web_sm` installed
- Python packages used by the retrieval pipeline, including:
  - `numpy`
  - `faiss`
- Ollama running locally
- an Ollama embedding model available locally
  - example: `nomic-embed-text`
- an Ollama generation model available locally
  - example: `gemma4:e4b`

Install the default spaCy model with:

```bash
python3 -m spacy download en_core_web_sm
```

### Step 1: Build the Embedding Index

Run the index-building pipeline first.

Minimal example:

```bash
src/main/java/com/articulate/nlp/semanticRetrieval/buildEmbeddingIndex/SUMOEmbeddingIndexRunner.sh \
  --embedding-model nomic-embed-text
```

More explicit example:

```bash
src/main/java/com/articulate/nlp/semanticRetrieval/buildEmbeddingIndex/SUMOEmbeddingIndexRunner.sh \
  --embedding-model nomic-embed-text \
  --output-jsonl /tmp/ontology-export.jsonl \
  --normalized-jsonl /tmp/ontology-export.normalized.jsonl \
  --output-artifact /tmp/ontology-export.normalized.nomic-embed-text.embeddings.pkl \
  --output-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --output-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json
```

This produces the two files that the online pipeline needs most directly:

- `ontology-export.normalized.nomic-embed-text.embeddings.faiss`
- `ontology-export.normalized.nomic-embed-text.embeddings.metadata.json`

### Step 2: Query an LLM End to End

Once the FAISS index and metadata exist, you can run the final pipeline with `queryLLM`.

Ollama example:

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/queryLLM/scripts/run_ollama_suo_kif_demo.py \
  "John kicked the ball." \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --embedding-model nomic-embed-text \
  --model gemma4:e4b
```

That command runs:

1. sentence analysis
2. structured retrieval
3. prompt construction
4. Ollama generation

and then prints:

- the sentence
- backend/model metadata
- the exact prompt payload
- the generated SUO-KIF output

### Interactive Querying

If you want to try several sentences in one session:

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/queryLLM/scripts/run_ollama_suo_kif_demo.py \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --embedding-model nomic-embed-text \
  --model gemma4:e4b
```

Inside the session you can:

- type a sentence to run the full pipeline
- `help` to see commands
- `set verbosity=micro|low|medium|high`
- `set format=text|json`
- `set k=<number>`
- `set mode=fixed_k|score_band|gap_drop`
- `exit` or `quit`

## Recommended Reading Order

If you are new to the pipeline, the easiest order is:

1. [buildEmbeddingIndex/README.md](/home/sumorich/workspace/sigmanlp/src/main/java/com/articulate/nlp/semanticRetrieval/buildEmbeddingIndex/README.md:1)
2. [sentenceAnalysis/README.md](/home/sumorich/workspace/sigmanlp/src/main/java/com/articulate/nlp/semanticRetrieval/sentenceAnalysis/README.md:1)
3. [retrievalOrchestration/README.md](/home/sumorich/workspace/sigmanlp/src/main/java/com/articulate/nlp/semanticRetrieval/retrievalOrchestration/README.md:1)
4. [promptConstruction/README.md](/home/sumorich/workspace/sigmanlp/src/main/java/com/articulate/nlp/semanticRetrieval/promptConstruction/README.md:1)
5. [queryLLM/README.md](/home/sumorich/workspace/sigmanlp/src/main/java/com/articulate/nlp/semanticRetrieval/queryLLM/README.md:1)

## Current Scope

This directory now covers the full path from:

- SUMO concept export and indexing
- sentence analysis
- structured grounding
- prompt construction
- final LLM query

The remaining quality work is mostly about:

- retrieval quality
- prompt quality
- model choice
- SUO-KIF accuracy and validation
