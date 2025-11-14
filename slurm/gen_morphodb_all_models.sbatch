#!/bin/bash

#SBATCH --job-name=gen-morphodb
#SBATCH --output=slurm-%x-%j.out
#SBATCH --partition=fsg               # Use the FSG partition directly
#SBATCH --nodes=1                     # fsg partition has 1 node
#SBATCH --ntasks-per-node=1
#SBATCH --cpus-per-task=256           # Use all 256 CPUs
#SBATCH --mem=0                       # 0 means "use all available memory"
#SBATCH --gpus-per-node=8             # Use all 8 GPUs (l40s)
#SBATCH --time=96:00:00               # Allow long run (fsg has infinite limit)

set -euo pipefail

LOG_FILE=${LOG_FILE:-$SLURM_SUBMIT_DIR/gen_morphodb_all_models.log}
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1

# Use a unique port based on job ID to avoid conflicts
export OLLAMA_PORT=$((12000 + SLURM_JOB_ID % 10000))
export OLLAMA_HOST="127.0.0.1:${OLLAMA_PORT}"
echo "Using Ollama on custom port: $OLLAMA_PORT"

echo "Stopping any existing Ollama server..."
pkill -f "ollama serve" 2>/dev/null || true
sleep 3

ollama --version

echo "=== Starting Ollama server on port $OLLAMA_PORT==="
ollama serve >> "$LOG_FILE" 2>&1 &
OLLAMA_PID=$!

# Give it time to start
sleep 10

# Stop Ollama automatically on exit
trap 'echo "=== Stopping Ollama server ==="; kill $OLLAMA_PID' EXIT

MODELS=(
    "gpt-oss:20b"
    "gpt-oss:120b"
    "gemma3:270m"
    "gemma3:27b"
    "llama3.2"
    "llama3.1:8b"
    "llama3.1:405b"
    "mistral"
    "phi3:3.8b"
    "phi3:14b"
    "gemma2:2b"
    "gemma3:4b"
    "gemma2:27b"
    "qwen3:0.6b"
    "qwen3:8b"
    "qwen3:32b"
    "qwen3:235b"
)

declare -A WORD_TYPE_FLAGS=(
    ["noun"]="-i -c -p -h -a -l"
    ["verb"]="-v -c -r -p -a -t"
    ["adjective"]="-c"
    ["adverb"]="-c"
)

for model in "${MODELS[@]}"; do
    echo "Ensuring model '${model}' is available via ollama pull..."
    ollama pull "$model"
done

for model in "${MODELS[@]}"; do
    for word_type in "${!WORD_TYPE_FLAGS[@]}"; do
        for flag in ${WORD_TYPE_FLAGS[$word_type]}; do
            java -Xmx40g -classpath "$SIGMANLP_CP" \
                com.articulate.nlp.morphodb.GenMorphoDB \
                "$word_type" "$flag" "$model"
        done
    done
done