# Build Embedding Index

## Overview

This folder contains the full SUMO embedding-index workflow. The single entry point is:

`SUMOEmbeddingIndexRunner.sh`

Overview block diagram:

```text
SUMO KB
  |
  v
SUMOJsonlExporter.java
  |
  v
ontology-export.jsonl
  |
  v
normalizeSUMOExport.py
  |
  v
ontology-export.normalized.jsonl
  |
  v
generate_embeddings.py
  |
  v
ontology-export.normalized.<embedding-model>.embeddings.pkl
  |
  v
faiss_index/build_faiss_index.py
  |
  v
FAISS index (.faiss) + metadata sidecar (.metadata.json)
```

The runner executes four stages in this exact order:

1. Export SUMO to JSONL
2. Normalize the exported term formats
3. Generate embeddings and save the embedding artifact
4. Build the FAISS index and aligned metadata sidecar

## Running It

Before running the pipeline:

- `SIGMANLP_CP` must be set so the Java exporter can run
- Ollama must be running
- the requested Ollama embedding model must already be available
- Python dependencies used by the pipeline, including `numpy` and `faiss`, must be installed

Minimal example:

```bash
src/main/java/com/articulate/nlp/semanticRetrieval/buildEmbeddingIndex/SUMOEmbeddingIndexRunner.sh \
  --embedding-model nomic-embed-text
```

More explicit example:

```bash
src/main/java/com/articulate/nlp/semanticRetrieval/buildEmbeddingIndex/SUMOEmbeddingIndexRunner.sh \
  --embedding-model nomic-embed-text \
  --batch-size 32 \
  --output-jsonl /tmp/ontology-export.jsonl \
  --normalized-jsonl /tmp/ontology-export.normalized.jsonl \
  --output-artifact /tmp/ontology-export.normalized.nomic-embed-text.embeddings.pkl \
  --output-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --output-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json
```

Useful runner options:

- `--embedding-model` required Ollama embedding model name
- `--output-jsonl` raw SUMO export path
- `--normalized-jsonl` normalized SUMO export path
- `--output-artifact` saved embedding artifact path
- `--output-index` FAISS index output path
- `--output-metadata` FAISS metadata sidecar path
- `--kb-name` knowledge base name for the Java exporter, default `SUMO`
- `--ollama-base-url` Ollama base URL, default `http://localhost:11434`
- `--limit` optional smaller concept subset for testing
- `--batch-size` embedding batch size
- `--timeout-seconds`, `--max-retries`, `--retry-backoff-seconds` Ollama request controls

## Export SUMO JSONL

Runner command:

```bash
java -cp "${SIGMANLP_CP}" \
  com.articulate.nlp.semanticRetrieval.buildEmbeddingIndex.SUMOJsonlExporter \
  "${OUTPUT_JSONL}" \
  "${KB_NAME}"
```

This stage reads the SUMO knowledge base through `KBLite` and writes one JSONL row per unique `sumo_type`. The exporter preserves formal ontology symbols exactly, including `sumo_type` and `parent_class`, and writes the raw `ontology-export.jsonl` file used by the rest of the pipeline. `KBLite` only retains English-language `documentation` and English-language `termFormat` entries, so the exported definitions and term formats are already English-only.

Primary output:

- raw SUMO export JSONL

## Normalize SUMO Export

Runner command:

```bash
python3 "${SCRIPT_DIR}/normalizeSUMOExport.py" \
  "${OUTPUT_JSONL}" \
  "${NORMALIZED_JSONL}"
```

This stage reads the raw export and normalizes the surface-language `english_equivalents` values for retrieval use. It preserves the original values alongside the normalized ones, and it does not modify `sumo_type` or `parent_class`.

Primary output:

- normalized SUMO export JSONL

## Generate Embedding Artifact

Runner command:

```bash
python3 "${SCRIPT_DIR}/generate_embeddings.py" ...
```

This stage loads the normalized JSONL into an in-memory concept store keyed by exact `sumo_type`, requires `normalized_english_equivalents` to be present, formats canonical embedding text from normalized aliases, truncates very long definitions deterministically before embedding, calls Ollama directly over HTTP, and saves a reusable embedding artifact for later indexing.

Saved artifact contents:

- ordered list of `sumo_type` values
- ordered list of canonical texts
- ordered list of embedding vectors
- embedding model and run metadata

Primary output:

- `*.embeddings.pkl`

## Build FAISS Index

Runner command:

```bash
python3 "${SCRIPT_DIR}/faiss_index/build_faiss_index.py" \
  --input-embeddings "${EFFECTIVE_OUTPUT_ARTIFACT}" \
  --output-index "${EFFECTIVE_OUTPUT_INDEX}" \
  --output-metadata "${EFFECTIVE_OUTPUT_METADATA}"
```

This stage loads the saved embedding artifact, validates vector counts and dimensions, converts the embeddings into contiguous row-major `float32` NumPy arrays, L2-normalizes the vectors, builds an exact `IndexFlatIP`, and saves both the FAISS index and a sidecar metadata file aligned to FAISS row order (FAISS only uses numbers, but we want SUMO terms, so this file does the translation).

Primary outputs:

- `*.faiss`
- `*.metadata.json`

Metadata alignment is explicit:

```text
FAISS row 0 -> sumo_type + canonical_text
FAISS row 1 -> sumo_type + canonical_text
...
```

## Demo Queries After It Has Run

After the runner has completed, you can run demo nearest-neighbor searches against the saved FAISS index.

Interactive query CLI:

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/buildEmbeddingIndex/faiss_index/interactive_query.py \
  /tmp
```

Inside the interactive session:

- type a word or phrase to query the active index
- `help` shows the available commands
- `set k=10` changes the top-k result count
- `set index=nomic-embed-text` switches the active embedding model
- `list indexes` shows the discovered indexes in the directory
- raw query text is normalized before it is embedded and searched

Important:

- after switching embedding generation to normalized aliases, regenerate the embedding artifact and FAISS index
- `generate_embeddings.py` now expects normalized JSONL input produced by `normalizeSUMOExport.py`

Example using one row from the saved embedding artifact as the query vector:

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/buildEmbeddingIndex/faiss_index/build_faiss_index.py \
  --demo-query \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --query-embedding-artifact /tmp/ontology-export.normalized.nomic-embed-text.embeddings.pkl \
  --query-row 0 \
  --top-k 5
```

Example using a query vector saved as JSON:

```bash
python3 src/main/java/com/articulate/nlp/semanticRetrieval/buildEmbeddingIndex/faiss_index/build_faiss_index.py \
  --demo-query \
  --input-index /tmp/ontology-export.normalized.nomic-embed-text.embeddings.faiss \
  --input-metadata /tmp/ontology-export.normalized.nomic-embed-text.embeddings.metadata.json \
  --query-vector-json /tmp/query_vector.json \
  --top-k 5
```
