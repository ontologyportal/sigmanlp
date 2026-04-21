#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

OUTPUT_JSONL="ontology-export.jsonl"
NORMALIZED_JSONL="ontology-export.normalized.jsonl"
OUTPUT_ARTIFACT=""
OUTPUT_INDEX=""
OUTPUT_METADATA=""
EMBEDDING_MODEL=""
KB_NAME="SUMO"
OLLAMA_BASE_URL="http://localhost:11434"
LIMIT=""
BATCH_SIZE=""
TIMEOUT_SECONDS=""
MAX_RETRIES=""
RETRY_BACKOFF_SECONDS=""

usage() {
  cat <<'EOF'
Usage:
  SUMOEmbeddingIndexRunner.sh --embedding-model MODEL [options]

Required:
  --embedding-model MODEL         Ollama embedding model name.

Optional:
  --output-jsonl PATH             Raw SUMO export JSONL path.
                                  Default: ontology-export.jsonl
  --normalized-jsonl PATH         Normalized SUMO export JSONL path.
                                  Default: ontology-export.normalized.jsonl
  --output-artifact PATH          Output pickle artifact path.
                                  Default: derived by generate_embeddings.py
  --output-index PATH             Output FAISS index path.
                                  Default: derived from the embedding artifact path
  --output-metadata PATH          Output FAISS metadata path.
                                  Default: derived from the embedding artifact path
  --kb-name NAME                  Knowledge base name for the Java exporter.
                                  Default: SUMO
  --ollama-base-url URL           Ollama base URL.
                                  Default: http://localhost:11434
  --limit N                       Optional concept limit for embedding.
  --batch-size N                  Batch size for embedding requests.
  --timeout-seconds N             HTTP timeout for Ollama requests.
  --max-retries N                 Max retries for Ollama requests.
  --retry-backoff-seconds N       Retry backoff base in seconds.
  --help                          Show this help message.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --embedding-model)
      EMBEDDING_MODEL="${2:-}"
      shift 2
      ;;
    --output-jsonl)
      OUTPUT_JSONL="${2:-}"
      shift 2
      ;;
    --normalized-jsonl)
      NORMALIZED_JSONL="${2:-}"
      shift 2
      ;;
    --output-artifact)
      OUTPUT_ARTIFACT="${2:-}"
      shift 2
      ;;
    --output-index)
      OUTPUT_INDEX="${2:-}"
      shift 2
      ;;
    --output-metadata)
      OUTPUT_METADATA="${2:-}"
      shift 2
      ;;
    --kb-name)
      KB_NAME="${2:-}"
      shift 2
      ;;
    --ollama-base-url)
      OLLAMA_BASE_URL="${2:-}"
      shift 2
      ;;
    --limit)
      LIMIT="${2:-}"
      shift 2
      ;;
    --batch-size)
      BATCH_SIZE="${2:-}"
      shift 2
      ;;
    --timeout-seconds)
      TIMEOUT_SECONDS="${2:-}"
      shift 2
      ;;
    --max-retries)
      MAX_RETRIES="${2:-}"
      shift 2
      ;;
    --retry-backoff-seconds)
      RETRY_BACKOFF_SECONDS="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "${SIGMANLP_CP:-}" ]]; then
  echo "SIGMANLP_CP is not set." >&2
  exit 1
fi

if [[ -z "${EMBEDDING_MODEL}" ]]; then
  echo "--embedding-model is required." >&2
  usage >&2
  exit 1
fi

EFFECTIVE_OUTPUT_ARTIFACT="${OUTPUT_ARTIFACT}"
if [[ -z "${EFFECTIVE_OUTPUT_ARTIFACT}" ]]; then
  EFFECTIVE_OUTPUT_ARTIFACT="$(python3 - "${NORMALIZED_JSONL}" "${EMBEDDING_MODEL}" <<'PY'
import re
import sys
from pathlib import Path

input_jsonl = Path(sys.argv[1])
embedding_model = sys.argv[2]
sanitized_model_name = re.sub(r"[^A-Za-z0-9._-]+", "_", embedding_model)
print(input_jsonl.with_name(f"{input_jsonl.stem}.{sanitized_model_name}.embeddings.pkl"))
PY
)"
fi

EFFECTIVE_OUTPUT_INDEX="${OUTPUT_INDEX:-${EFFECTIVE_OUTPUT_ARTIFACT%.pkl}.faiss}"
EFFECTIVE_OUTPUT_METADATA="${OUTPUT_METADATA:-${EFFECTIVE_OUTPUT_ARTIFACT%.pkl}.metadata.json}"

echo "Exporting SUMO to ${OUTPUT_JSONL}"
java -cp "${SIGMANLP_CP}" \
  com.articulate.nlp.semanticRetrieval.buildEmbeddingIndex.SUMOJsonlExporter \
  "${OUTPUT_JSONL}" \
  "${KB_NAME}"

echo "Normalizing SUMO export to ${NORMALIZED_JSONL}"
python3 "${SCRIPT_DIR}/normalizeSUMOExport.py" \
  "${OUTPUT_JSONL}" \
  "${NORMALIZED_JSONL}"

EMBEDDING_ARGS=(
  --input-jsonl "${NORMALIZED_JSONL}"
  --embedding-model "${EMBEDDING_MODEL}"
  --ollama-base-url "${OLLAMA_BASE_URL}"
  --output-artifact "${EFFECTIVE_OUTPUT_ARTIFACT}"
)

if [[ -n "${LIMIT}" ]]; then
  EMBEDDING_ARGS+=(--limit "${LIMIT}")
fi
if [[ -n "${BATCH_SIZE}" ]]; then
  EMBEDDING_ARGS+=(--batch-size "${BATCH_SIZE}")
fi
if [[ -n "${TIMEOUT_SECONDS}" ]]; then
  EMBEDDING_ARGS+=(--timeout-seconds "${TIMEOUT_SECONDS}")
fi
if [[ -n "${MAX_RETRIES}" ]]; then
  EMBEDDING_ARGS+=(--max-retries "${MAX_RETRIES}")
fi
if [[ -n "${RETRY_BACKOFF_SECONDS}" ]]; then
  EMBEDDING_ARGS+=(--retry-backoff-seconds "${RETRY_BACKOFF_SECONDS}")
fi

echo "Generating embedding artifact"
python3 "${SCRIPT_DIR}/generate_embeddings.py" "${EMBEDDING_ARGS[@]}"

echo "Building FAISS index at ${EFFECTIVE_OUTPUT_INDEX}"
python3 "${SCRIPT_DIR}/faiss_index/build_faiss_index.py" \
  --input-embeddings "${EFFECTIVE_OUTPUT_ARTIFACT}" \
  --output-index "${EFFECTIVE_OUTPUT_INDEX}" \
  --output-metadata "${EFFECTIVE_OUTPUT_METADATA}"
