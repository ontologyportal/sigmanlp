#!/bin/bash

#SBATCH --job-name=gen-morphodb-par
#SBATCH --output=slurm-%x-%j.out
#SBATCH --partition=fsg
#SBATCH --nodes=1
#SBATCH --ntasks-per-node=1
#SBATCH --cpus-per-task=256
#SBATCH --mem=0
#SBATCH --gpus-per-node=8
#SBATCH --time=96:00:00

set -euo pipefail

LOG_FILE=${LOG_FILE:-$SLURM_SUBMIT_DIR/gen_morphodb_all_models_parallel.log}
mkdir -p "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE") 2>&1

#############################################
### 0. Port Allocation (job-specific)
#############################################

# Avoid 11434, avoid collisions, bind to job ID
BASE_JOB_PORT=$((12000 + SLURM_JOB_ID % 10000))

# 8 single-GPU servers
SMALL_PORTS=()
for GPU in $(seq 0 7); do
    SMALL_PORTS[$GPU]=$((BASE_JOB_PORT + GPU))
done

# Big-model multi-GPU server port (offset 100)
BIG_PORT=$((BASE_JOB_PORT + 100))

#############################################
### 1. Setup / Cleanup
#############################################

echo "Stopping any existing Ollama server..."
pkill -f "ollama serve" 2>/dev/null || true
sleep 3

export OLLAMA_THREADS=$SLURM_CPUS_PER_TASK
export OLLAMA_MAX_LOADED_MODELS=1
export OLLAMA_KV_CACHE_TYPE=f16
export OLLAMA_GPU_OVERLAP=1

echo "Ollama version:"
ollama --version

#############################################
### Helper functions
#############################################

is_big_model() {
    local name="$1"
    [[ "$name" == *"405b"* || "$name" == *"235b"* || "$name" == *"120b"* ]]
}

sanitize_model_name() {
    echo "$1" | tr '/:.' '___'
}

#############################################
### 2. GPU Health Monitor
#############################################

GPU_MON_LOG="gpu_monitor.log"
echo "=== Starting GPU Health Monitor in background ==="
(
    echo "Timestamp, GPU, Temp(C), MemUsed(MB), MemTotal(MB), Util(%), Power(W)" > "$GPU_MON_LOG"
    while true; do
        TS=$(date +"%Y-%m-%d %H:%M:%S")
        nvidia-smi --query-gpu=index,temperature.gpu,memory.used,memory.total,utilization.gpu,power.draw \
                   --format=csv,noheader,nounits |
        while IFS=',' read -r idx temp used total util power; do
            echo "$TS, $idx, $temp, $used, $total, $util, $power" >> "$GPU_MON_LOG"
        done
        sleep 60
    done
) &
GPU_MON_PID=$!

#############################################
### 3. Model / Flags
#############################################

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

#############################################
### 4. Build task queues
#############################################

TASK_FILE="tasks_${SLURM_JOB_ID}.txt"
BIG_TASK_FILE="big_tasks_${SLURM_JOB_ID}.txt"
SMALL_TASK_FILE="small_tasks_${SLURM_JOB_ID}.txt"

: > "$TASK_FILE"
: > "$BIG_TASK_FILE"
: > "$SMALL_TASK_FILE"

declare -A MODEL_TASK_TOTAL

for model in "${MODELS[@]}"; do
    for word_type in "${!WORD_TYPE_FLAGS[@]}"; do
        for flag in ${WORD_TYPE_FLAGS[$word_type]}; do
            echo "$model $word_type $flag" >> "$TASK_FILE"

            if is_big_model "$model"; then
                echo "$model $word_type $flag" >> "$BIG_TASK_FILE"
            else
                echo "$model $word_type $flag" >> "$SMALL_TASK_FILE"
            fi

            MODEL_TASK_TOTAL["$model"]=$(( MODEL_TASK_TOTAL["$model"] + 1 ))
        done
    done
done

#############################################
### 5. Progress tracking
#############################################

for model in "${MODELS[@]}"; do
    san=$(sanitize_model_name "$model")
    echo "0 ${MODEL_TASK_TOTAL[$model]}" > "progress_${san}.dat"
done

update_and_log_progress() {
    local model="$1"
    local gpu="$2"
    local san
    san=$(sanitize_model_name "$model")
    local file="progress_${san}.dat"

    {
        flock 9
        read -r done total < "$file"
        done=$((done + 1))
        echo "$done $total" > "$file"
        pct=$(awk "BEGIN { if ($total == 0) print 100; else printf \"%.1f\", ($done/$total)*100 }")
        echo "[${model}] ${done}/${total} (${pct}%) on GPU ${gpu}"
    } 9>"${file}.lock" >> progress.log
}

#############################################
### 6. Phase 1 — SMALL MODELS FIRST
### Start 8 single-GPU servers w/ job-specific ports
#############################################

for GPU in $(seq 0 7); do
    PORT=${SMALL_PORTS[$GPU]}
    echo "Starting small-model server GPU=$GPU PORT=$PORT"

    CUDA_VISIBLE_DEVICES=$GPU \
    OLLAMA_PORT=$PORT \
    OLLAMA_NUM_GPU=1 \
        nohup ollama serve --port "$PORT" >> "ollama_small_gpu${GPU}.log" 2>&1 &
done

sleep 15

#############################################
### 7. Pull models (GPU0, with correct job-port)
#############################################

export OLLAMA_PORT=${SMALL_PORTS[0]}
export OLLAMA_HOST="127.0.0.1:${OLLAMA_PORT}"

echo "Pulling all models on port $OLLAMA_PORT..."
for model in "${MODELS[@]}"; do
    echo "Pulling model: $model"
    ollama pull "$model"
done

#############################################
### 8. Execute small models
#############################################

TASK_ID=0
if [[ -s "$SMALL_TASK_FILE" ]]; then
    while IFS=" " read -r model word_type flag; do
        GPU=$((TASK_ID % 8))
        PORT=${SMALL_PORTS[$GPU]}

        (
            export OLLAMA_PORT=$PORT
            export OLLAMA_HOST="127.0.0.1:${PORT}"

            java -Xmx128g -XX:+UseG1GC \
                 -classpath "$SIGMANLP_CP" \
                 com.articulate.nlp.morphodb.GenMorphoDB \
                 "$word_type" "$flag" "$model" "$PORT"

            update_and_log_progress "$model" "$GPU"
        ) >> "java_small_gpu${GPU}.log" 2>&1 &

        TASK_ID=$((TASK_ID + 1))
        if (( TASK_ID % 8 == 0 )); then wait; fi

    done < "$SMALL_TASK_FILE"
    wait
fi

#############################################
### 9. Shutdown small servers
#############################################

pkill -f "ollama serve" 2>/dev/null || true
sleep 10

#############################################
### 10. Phase 2 — BIG MODELS LAST
### Start multi-GPU server on job-specific port
#############################################

echo "Starting BIG multi-GPU server on port $BIG_PORT"

CUDA_VISIBLE_DEVICES=0,1,2,3,4,5,6,7 \
OLLAMA_PORT=$BIG_PORT \
OLLAMA_NUM_GPU=8 \
    nohup ollama serve --port "$BIG_PORT" >> ollama_big.log 2>&1 &

BIG_OLLAMA_PID=$!
sleep 15

#############################################
### 11. Pull models again (big server gets index)
#############################################

export OLLAMA_PORT=$BIG_PORT
export OLLAMA_HOST="127.0.0.1:${BIG_PORT}"

for model in "${MODELS[@]}"; do
    ollama pull "$model"
done

#############################################
### 12. Execute big models
#############################################

if [[ -s "$BIG_TASK_FILE" ]]; then
    while IFS=" " read -r model word_type flag; do

        export OLLAMA_PORT=$BIG_PORT
        export OLLAMA_HOST="127.0.0.1:${BIG_PORT}"

        java -Xmx128g -XX:+UseG1GC \
             -classpath "$SIGMANLP_CP" \
             com.articulate.nlp.morphodb.GenMorphoDB \
             "$word_type" "$flag" "$model" "$BIG_PORT"

        update_and_log_progress "$model" "all"

    done < "$BIG_TASK_FILE"
fi

#############################################
### 13. Cleanup
#############################################

kill "$BIG_OLLAMA_PID" 2>/dev/null || true
pkill -f "ollama serve" 2>/dev/null || true

kill "$GPU_MON_PID" 2>/dev/null || true

echo "=== DONE ==="
