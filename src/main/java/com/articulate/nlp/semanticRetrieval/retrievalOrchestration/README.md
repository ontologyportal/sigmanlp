# Retrieval Orchestration

## Overview

This folder contains the retrieval orchestration stage for the SUMO semantic grounding pipeline. It takes a sentence, runs the existing sentence-analysis stage, walks the structured head/modifier representation, builds retrieval strings for every semantically meaningful mention, embeds those strings with Ollama when needed, searches the existing FAISS index, applies a candidate-selection policy over a larger raw FAISS pool, and returns structured SUMO candidates with attachment relations preserved. Recognized named entities can bypass dense retrieval and map directly to fixed SUMO types.

Overview block diagram:

```text
Sentence
  |
  v
sentenceAnalysis
  |
  v
Head mentions + modifiers + named-entity labels
  |
  v
retrieval strings / direct NER mappings
  |
  v
Ollama query embeddings
  |
  v
FAISS search + metadata lookup
  |
  v
Structured retrieval result
```

This stage does not build the final LLM prompt yet. Its output is the structured candidate-retrieval layer that later prompt construction will consume.

## Running It

Before running the demo:

- `python3` must be available
- `spaCy` must be installed
- the spaCy English model `en_core_web_sm` must be installed
- Ollama must be running and the embedding model must already be available locally
- a FAISS index and matching metadata JSON must already exist

Install the default spaCy English model with:

```bash
python3 -m spacy download en_core_web_sm
```

Interactive demo command:

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/retrievalOrchestration/scripts/run_sentence_retrieval_demo.py \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --embedding-model nomic-embed-text
```

One-shot demo command:

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/retrievalOrchestration/scripts/run_sentence_retrieval_demo.py \
  "The very quick fox moved silently." \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --embedding-model nomic-embed-text \
  --top-k 3
```

Inside the interactive session:

- type a sentence to retrieve immediately
- `help` shows the available commands
- `list models` shows the known English spaCy models and whether they are installed
- `set model=en_core_web_sm` changes the active spaCy model
- `set k=3` changes `final_k`
- `set pool=50` changes the raw FAISS pool size
- `set mode=score_band` changes the selection policy
- `set min_k=5` and `set max_k=15` adjust the dynamic selection window
- `set score_band_delta=0.04` adjusts the score-band threshold
- `set gap_drop_threshold=0.03` adjusts the gap-drop threshold
- `set format=text|json` changes the output format
- `quit` or `exit` leaves the session

Optional:

- `--initial-pool-k` sets the initial raw FAISS pool size
- `--selection-mode` chooses `fixed_k`, `score_band`, or `gap_drop`
- `--final-k` sets the fixed-k output size
- `--top-k` is an alias for `--final-k`
- `--min-k`, `--max-k`, `--score-band-delta`, and `--gap-drop-threshold` configure dynamic selection
- `--ollama-base-url` overrides the Ollama server URL
- `--spacy-model` sets the initial spaCy model before one-shot or interactive execution
- `--output-format text|json` chooses between the readable text renderer and structured JSON output

## Load Retrieval Resources

The retrieval layer uses:

- the existing FAISS index from `buildEmbeddingIndex/faiss_index`
- the matching metadata sidecar JSON
- the existing Ollama embedding client from `utils/ollama_client.py`

`FaissCandidateSearcher` validates:

- FAISS index dimension vs metadata embedding dimension
- FAISS row count vs metadata row count
- metadata row alignment
- valid `top-k`

The embedding model passed to the CLI must match the embedding model stored in the metadata.

## Orchestrate Structured Retrieval

`SemanticRetrievalOrchestrator` coordinates:

- sentence analysis with `SpacySentenceAnalyzer`
- structured head/modifier extraction with `RuleBasedMentionExtractor`
- retrieval string construction
- Ollama query embedding
- raw FAISS candidate search
- post-FAISS candidate selection
- conversion into structured retrieval result objects

Candidate selection happens inside retrieval orchestration, not inside FAISS and not inside prompt construction. The default settings are:

- `initial_pool_k = 50`
- `selection_mode = score_band`
- `min_k = 5`
- `max_k = 15`
- `score_band_delta = 0.04`

Retrieval runs for:

- entity heads
- event heads
- adjectival/property modifiers
- adverbial/manner modifiers

Named-entity bypass rules currently include:

- `PERSON` -> `Human`

Pronoun bypass rules also include:

- singular human personal pronouns -> `Human`
- plural human personal pronouns -> `GroupOfPeople`
- bare `you` defaults to `Human`

When a head mention matches any recognized direct-mapping rule:

- Ollama embedding is skipped
- FAISS search is skipped
- the mention receives one direct candidate with score `1.0`
- the selection trace records that dense retrieval was bypassed

The orchestration layer preserves attachment structure. A modifier keeps an `attached_to` link to the parent mention id, so downstream code can still distinguish:

- what is an entity
- what is an event
- what is a modifier
- what each modifier attaches to

## Retrieval Result Types

The main typed result models are:

- `RetrievedCandidate`
  - `sumo_type`
  - `score`
  - `termFormats`
  - `definition`
  - `rank`
- `RetrievedMention`
  - `mention_id`
  - `text`
  - `normalized_text`
  - `lemma`
  - `named_entity_label`
  - `mention_type`
  - `source`
  - `retrieval_query`
  - `candidates`
  - `selection_trace`
  - `attached_to`
  - `is_head`
  - `head_type`
  - `token_start`
  - `token_end`
- `SentenceRetrievalResult`
  - `sentence`
  - `retrieved_mentions`

These types live in `retrieval/retrieval_types.py`.

## Example Output

Example sentence:

```text
The very quick fox moved silently.
```

Expected structured retrieval output includes:

- head entity `fox`
  - modifiers `quick`, `very`
- modifier adjective `quick`
  - attached to `fox`
- modifier adverb `very`
  - attached to `quick`
- head event `moved`
  - modifier `silently`
- modifier adverb `silently`
  - attached to `moved`

Example retrieval behavior:

Named-entity head:

```text
John
```

Text-mode output for that mention includes:

```text
named_entity: PERSON
retrieval: bypassed (direct_map)
1. score=1.000000 sumo_type=Human
```

Pronoun behavior:

```text
I kicked the ball.
```

- `I` is a pronoun head, not a named entity
- retrieval is bypassed through a rule-based direct mapping
- the mention receives `Human`

```text
We kicked the ball.
```

- `We` is grounded directly to `GroupOfPeople`

Event head:

```text
kicking
```

Entity head:

```text
ball
```

The JSON output preserves the same information with nested modifiers, `named_entity_label`, and split candidate fields for `termFormats` and `definition`.

In text mode, each mention also shows:

- the retrieval query
- the candidate-selection mode and stop reason
- the raw FAISS pool size and top few raw candidates
- the final selected candidates after the policy is applied

## Current Constraints

- this stage reuses the existing sentence-analysis output rather than redesigning that layer
- lexical scoring is not implemented in this stage
- prompt construction is not implemented in this stage
- candidate retrieval depends on a working Ollama server and a FAISS index built with the same embedding model
- retrieval is structured, but ranking is still dense-vector-only at this point
- only recognized named entities with explicit rule mappings bypass dense retrieval right now
- the current direct-mapping table is intentionally small and is designed to be extended later for labels such as `ORG` and `GPE`
