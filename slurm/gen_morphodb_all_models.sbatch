#!/bin/bash

#SBATCH --job-name=gen-morphodb
#SBATCH --output=slurm-%x-%j.out
#SBATCH --nodes=${NODES:-1}          # Number of computers (nodes)
#SBATCH --ntasks-per-node=1
#SBATCH --cpus-per-task=${CPUS:-8}
#SBATCH --mem=${MEMORY:-64G}         # Memory per node
#SBATCH --gpus-per-node=${GPUS:-0}   # GPUs per node (0 for CPU-only jobs)
#SBATCH --time=24:00:00
#SBATCH --partition=${PARTITION:-standard}

set -euo pipefail

LOG_FILE=${LOG_FILE:-$SLURM_SUBMIT_DIR/gen_morphodb_all_models.log}
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE")
exec 2>&1


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
    "qwen3:8b
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
