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

# Optional: load site-specific modules here, e.g.
# module load java/17

: "${SIGMANLP_CP:?Set SIGMANLP_CP to the SigmaNLP classpath (see README).}"
: "${SIGMA_HOME:?Set SIGMA_HOME to the root of your SIGMA installation.}"

JAVA_HEAP=${JAVA_HEAP:-40g}
REPO_ROOT=${REPO_ROOT:-$SLURM_SUBMIT_DIR}
cd "$REPO_ROOT"

MODELS=(${OLLAMA_MODELS:-"llama3.2 mistral phi3 gemma2"})

if ! command -v ollama >/dev/null 2>&1; then
    echo "ERROR: ollama command not found but required to pull models." >&2
    exit 1
fi

declare -A WORD_TYPE_FLAGS=(
    ["noun"]="-i -c -p -h -a -l"
    ["verb"]="-v -c -r -p -a -t"
    ["adjective"]="-c"
    ["adverb"]="-c"
)

run_generation() {
    local word_type=$1
    local flag=$2
    local model=$3

    echo "[$(date '+%Y-%m-%d %H:%M:%S')] word_type=${word_type} flag=${flag} model=${model}"
    java -Xmx"$JAVA_HEAP" -classpath "$SIGMANLP_CP" \
        com.articulate.nlp.morphodb.GenMorphoDB \
        "$word_type" "$flag" "$model"
}

for model in "${MODELS[@]}"; do
    echo "Ensuring model '${model}' is available via ollama pull..."
    ollama pull "$model"
done

for model in "${MODELS[@]}"; do
    for word_type in "${!WORD_TYPE_FLAGS[@]}"; do
        for flag in ${WORD_TYPE_FLAGS[$word_type]}; do
            run_generation "$word_type" "$flag" "$model"
        done
    done
done
