# Prompt Construction

## Overview

This folder contains the final prompt-construction stage for the SUMO semantic grounding pipeline. It takes the structured retrieval output from `retrievalOrchestration`, keeps the head/modifier structure intact, and builds an LLM prompt payload that asks a model to convert an English sentence into SUO-KIF using retrieved SUMO candidates.

Overview block diagram:

```text
Sentence
  |
  v
retrievalOrchestration
  |
  v
Structured retrieved mentions + SUMO candidates
  |
  v
SUOKIFPromptBuilder
  |
  v
Verbosity-aware prompt payload
  |
  v
LLM
```

This stage does not call the LLM. It only builds the prompt payload.

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
python3 src/main/java/com/articulate/nlp/semanticRetrieval/promptConstruction/scripts/run_suo_kif_prompt_demo.py \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --embedding-model nomic-embed-text
```

One-shot demo command:

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/promptConstruction/scripts/run_suo_kif_prompt_demo.py \
  "He sat on the bank of the river." \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --embedding-model nomic-embed-text \
  --verbosity high
```

Inside the interactive session:

- type a sentence to build a prompt immediately
- `help` shows the available commands
- `list models` shows the known English spaCy models and whether they are installed
- `set model=en_core_web_sm` changes the active spaCy model
- `set verbosity=micro|low|medium|high` changes how much candidate detail is exposed
- `set format=text|json` changes the output format
- `set k=5`, `set pool=50`, `set mode=score_band`, and the other retrieval commands adjust candidate selection before prompt construction
- `quit` or `exit` leaves the session

## Verbosity Levels

The prompt builder supports four levels:

- `micro`
  - the bare minimum prompt
  - includes only:
    - `Convert the following sentence to SUO-KIF:`
    - `Sentence:"..."`
    - `Use terms from this list:[...]`
    - a flat deduplicated list of `sumo_type` values
  - no semantic structure
  - no mention labels
  - no term formats
  - no definitions
  - no embedded newline characters in prompt-message content

- `low`
  - only the `sumo_type` values are listed under each mention
  - no term formats
  - no definitions
  - no scores or rankings
  - no output-requirements block
- `medium`
  - each candidate line includes the word from the sentence and the `sumo_type`
  - example shape: `bank -> RiverBank`
- `high`
  - each candidate line includes:
    - the word from the sentence
    - the `sumo_type`
    - the `termFormats`
    - the `definition`

The retrieval layer still computes scores and rankings, but the prompt builder deliberately omits them from the prompt.

## Build Prompt Payload

`SUOKIFPromptBuilder` consumes a `SentenceRetrievalResult` and returns a typed `SUOKIFPromptPayload`.

The payload contains exactly two messages:

- `system`
  - minimal SUO-KIF conversion instructions
- `user`
  - the sentence
  - the semantic structure for `low`, `medium`, and `high`
  - the verbosity-filtered candidate formal terms
  - output requirements for `medium` and `high`

The user message always includes the original sentence and candidate formal terms.

For `low`, `medium`, and `high`, it also includes:

- a semantic-structure block with:
  - head entities
  - head events
  - modifiers
  - attachment relations
- a candidate-formal-terms block rendered according to the chosen verbosity

For `micro`, it uses only a flat term list with no mention grouping.
The sentence is wrapped in double quotes, and any internal `"` characters are escaped.

The payload is available in:

- text mode
- JSON mode as:
  - `sentence`
  - `verbosity`
  - `messages`

## Example Output

Example sentence:

```text
The very quick fox moved silently.
```

The prompt payload will include semantic structure like:

- head entity `fox`
- modifier adjective `quick`
  - attached to `fox`
- modifier adverb `very`
  - attached to `quick`
- head event `moved`
- modifier adverb `silently`
  - attached to `moved`

In `micro` verbosity, the prompt body looks like:

```text
Convert the following sentence to SUO-KIF: Sentence:"The very quick fox moved silently." Use terms from this list:[Fox,Canine,Walking]
```

In `low` verbosity, the candidate block looks like:

```text
- mention: fox (entity)
  - Fox
  - Canine
```

In `medium` verbosity, the candidate block looks like:

```text
- mention: fox (entity)
  - fox -> Fox
  - fox -> Canine
```

In `high` verbosity, the candidate block looks like:

```text
- mention: fox (entity)
  - word: fox | sumo_type: Fox | termFormats: fox | definition: (example)
```

## Current Constraints

- this stage builds prompts but does not call an LLM
- the prompt payload depends on retrieval quality from the previous stage
- only the selected final candidates are exposed to the prompt builder
- candidate scores and ranks are intentionally omitted from the prompt itself
- the prompt builder currently emits exactly one system message and one user message
