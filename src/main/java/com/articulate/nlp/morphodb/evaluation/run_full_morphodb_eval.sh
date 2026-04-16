#!/bin/bash

set -euo pipefail

DB_ROOT="${HOME}/.sigmanlp/MorphoDB_Research"
HUMAN_GOLD_DIR="${HOME}/.sigmanlp/Gold/gold"
UNIMORPH_GOLD_DIR="${HOME}/.sigmanlp/Gold"
OUTPUT_ROOT="${HOME}/.sigmanlp/EvalOut"
REFERENCE_MODEL="openai__gpt-5_2"
REPORT_BUNDLE="${OUTPUT_ROOT}/report_bundle"
REPORT_BUNDLE_PREV="${OUTPUT_ROOT}/report_bundle_prev"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../../../../../../.." && pwd)"
PLOT_SCRIPT="${REPO_ROOT}/src/main/java/com/articulate/nlp/morphodb/evaluation/python/plot_figures.py"
LOG_DIR="${REPORT_BUNDLE}/logs"

banner() {
    local message="$1"
    echo
    echo "============================================================"
    echo "${message}"
    echo "============================================================"
}

require_command() {
    local command_name="$1"
    if ! command -v "${command_name}" >/dev/null 2>&1; then
        echo "Missing required command: ${command_name}" >&2
        exit 1
    fi
}

require_path() {
    local path="$1"
    local description="$2"
    if [[ ! -e "${path}" ]]; then
        echo "Missing ${description}: ${path}" >&2
        exit 1
    fi
}

run_step() {
    local step_number="$1"
    local label="$2"
    shift 2

    local log_file="${LOG_DIR}/${step_number}_${label}.log"
    banner "Step ${step_number}: ${label}"
    echo "Logging to ${log_file}"

    if ! "$@" 2>&1 | tee "${log_file}"; then
        echo "Step ${step_number} failed: ${label}" >&2
        exit 1
    fi
}

run_consolidate_step() {
    local log_file="${LOG_DIR}/07_consolidate.log"
    banner "Step 07: consolidate"
    echo "Logging to ${log_file}"

    if ! consolidate_reports 2>&1 | tee "${log_file}"; then
        echo "Step 07 failed: consolidate" >&2
        exit 1
    fi
}

copy_if_exists() {
    local source_path="$1"
    local destination_path="$2"
    if [[ -e "${source_path}" ]]; then
        mkdir -p "$(dirname "${destination_path}")"
        cp -R "${source_path}" "${destination_path}"
    fi
}

consolidate_reports() {
    local maintenance_dir="${REPORT_BUNDLE}/maintenance"
    local maintenance_compact_dir="${maintenance_dir}/compact"
    local unimorph_dir="${REPORT_BUNDLE}/unimorph"
    local verb_regularity_dir="${REPORT_BUNDLE}/verb_regularity"
    local evaluation_runner_dir="${REPORT_BUNDLE}/evaluation_runner"

    mkdir -p "${maintenance_compact_dir}" "${unimorph_dir}" "${verb_regularity_dir}" "${evaluation_runner_dir}"

    copy_if_exists "${DB_ROOT}/categorical-normalization-summary.txt" "${maintenance_dir}/categorical-normalization-summary.txt"
    copy_if_exists "${DB_ROOT}/error-percent-summary.txt" "${maintenance_dir}/error-percent-summary.txt"

    while IFS= read -r compact_file; do
        local model_name
        model_name="$(basename "$(dirname "${compact_file}")")"
        mkdir -p "${maintenance_compact_dir}/${model_name}"
        cp "${compact_file}" "${maintenance_compact_dir}/${model_name}/compact-summary.txt"
    done < <(find "${DB_ROOT}" -mindepth 2 -maxdepth 2 -type f -name 'compact-summary.txt' | sort)

    while IFS= read -r audit_file; do
        local model_name
        model_name="$(basename "$(dirname "${audit_file}")")"
        mkdir -p "${maintenance_compact_dir}/${model_name}"
        cp "${audit_file}" "${maintenance_compact_dir}/${model_name}/compact-required-field-recovery-audit.jsonl"
    done < <(find "${DB_ROOT}" -mindepth 2 -maxdepth 2 -type f -name 'compact-required-field-recovery-audit.jsonl' | sort)

    copy_if_exists "${OUTPUT_ROOT}/UniMorphEvaluation" "${unimorph_dir}/UniMorphEvaluation"
    copy_if_exists "${OUTPUT_ROOT}/VerbRegularityEvaluation" "${verb_regularity_dir}/VerbRegularityEvaluation"
    copy_if_exists "${OUTPUT_ROOT}/tables" "${evaluation_runner_dir}/tables"
    copy_if_exists "${OUTPUT_ROOT}/latex" "${evaluation_runner_dir}/latex"
    copy_if_exists "${OUTPUT_ROOT}/figures" "${evaluation_runner_dir}/figures"
}

banner "Preflight"

require_command java
require_command tee
require_command find
require_command cp
require_command mv

if [[ -z "${SIGMANLP_CP:-}" ]]; then
    echo "SIGMANLP_CP is not set." >&2
    exit 1
fi

require_path "${DB_ROOT}" "MorphoDB root"
require_path "${HUMAN_GOLD_DIR}" "human gold directory"
require_path "${UNIMORPH_GOLD_DIR}" "UniMorph gold directory"
require_path "${HUMAN_GOLD_DIR}/conjugation_audit.jsonl" "conjugation audit gold file"
require_path "${HUMAN_GOLD_DIR}/plural_audit.jsonl" "plural audit gold file"
require_path "${UNIMORPH_GOLD_DIR}/unimorph_noun_plurals.jsonl" "UniMorph noun gold file"
require_path "${UNIMORPH_GOLD_DIR}/unimorph_verb_conjugations.jsonl" "UniMorph verb gold file"
mkdir -p "${OUTPUT_ROOT}"

if [[ -e "${REPORT_BUNDLE_PREV}" ]]; then
    ARCHIVED_PREV="${OUTPUT_ROOT}/report_bundle_prev_$(date +%Y%m%d_%H%M%S)"
    mv "${REPORT_BUNDLE_PREV}" "${ARCHIVED_PREV}"
    echo "Archived previous bundle backup to ${ARCHIVED_PREV}"
fi

if [[ -e "${REPORT_BUNDLE}" ]]; then
    mv "${REPORT_BUNDLE}" "${REPORT_BUNDLE_PREV}"
    echo "Moved existing report bundle to ${REPORT_BUNDLE_PREV}"
fi

mkdir -p "${LOG_DIR}"

if [[ -e "${PLOT_SCRIPT}" ]]; then
    export MORPHODB_PLOT_SCRIPT="${PLOT_SCRIPT}"
else
    echo "Plot script not found; figure generation may be skipped."
fi

echo "Repo root:        ${REPO_ROOT}"
echo "DB root:          ${DB_ROOT}"
echo "Human gold dir:   ${HUMAN_GOLD_DIR}"
echo "UniMorph gold dir:${UNIMORPH_GOLD_DIR}"
echo "Output root:      ${OUTPUT_ROOT}"
echo "Report bundle:    ${REPORT_BUNDLE}"
echo "Previous bundle:  ${REPORT_BUNDLE_PREV}"

run_step "01" "maintenance" \
    java -cp "${SIGMANLP_CP}" \
    com.articulate.nlp.morphodb.GenMorphoDB \
    --compact --normalize-categorical --all-models --db-path "${DB_ROOT}"

run_step "02" "ensemble" \
    java -cp "${SIGMANLP_CP}" \
    com.articulate.nlp.morphodb.MorphoDBEnsemblePostprocessor

run_step "03" "error_percent" \
    java -cp "${SIGMANLP_CP}" \
    com.articulate.nlp.morphodb.GenMorphoDB \
    --getErrorPercent --all-models --db-path "${DB_ROOT}"

run_step "04" "unimorph" \
    java -cp "${SIGMANLP_CP}" \
    com.articulate.nlp.morphodb.evaluation.UniMorphEvaluationRunner \
    --input "${DB_ROOT}" \
    --gold-dir "${UNIMORPH_GOLD_DIR}" \
    --output "${OUTPUT_ROOT}"

run_step "05" "verb_regularity" \
    java -cp "${SIGMANLP_CP}" \
    com.articulate.nlp.morphodb.evaluation.VerbRegularityEvaluationRunner \
    --input "${DB_ROOT}" \
    --gold-file "${HUMAN_GOLD_DIR}/conjugation_audit.jsonl" \
    --output "${OUTPUT_ROOT}"

run_step "06" "evaluation_runner" \
    java -cp "${SIGMANLP_CP}" \
    com.articulate.nlp.morphodb.evaluation.EvaluationRunner \
    --input "${DB_ROOT}" \
    --gold-dir "${HUMAN_GOLD_DIR}" \
    --output "${OUTPUT_ROOT}" \
    --reference-model "${REFERENCE_MODEL}"

run_consolidate_step

banner "Done"
echo "Maintenance summaries:"
echo "  ${DB_ROOT}/categorical-normalization-summary.txt"
echo "  ${DB_ROOT}/error-percent-summary.txt"
echo "Evaluation outputs:"
echo "  ${OUTPUT_ROOT}/UniMorphEvaluation"
echo "  ${OUTPUT_ROOT}/VerbRegularityEvaluation"
echo "  ${OUTPUT_ROOT}/tables"
echo "  ${OUTPUT_ROOT}/latex"
echo "  ${OUTPUT_ROOT}/figures"
echo "Consolidated bundle:"
echo "  ${REPORT_BUNDLE}"
