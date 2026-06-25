#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MODEL_DIR="$ROOT_DIR/models"

mkdir -p "$MODEL_DIR"

DEFAULT_MODELS=(
  "qwen3-0.6b-mixed-int4"
  "qwen3-0.6b-dynamic-int8"
)

ALL_MODELS=(
  "qwen3-0.6b-mixed-int4"
  "qwen3-0.6b-dynamic-int8"
  "gemma4-e2b-it"
  "gemma4-e4b-it"
)

usage() {
  cat <<EOF
Usage: scripts/download_model.sh [model-id ...]

Default downloads the lightweight Qwen models already used by the test bench.
Pass one or more ids to download larger candidates explicitly:

  qwen3-0.6b-mixed-int4
  qwen3-0.6b-dynamic-int8
  gemma4-e2b-it
  gemma4-e4b-it

Use "all" to download every known LiteRT-LM model.
EOF
}

download_model() {
  local file_name="$1"
  local url="$2"
  local min_bytes="$3"
  local model_file="$MODEL_DIR/$file_name"

  curl -L -C - -o "$model_file" "$url"

  local size
  size="$(wc -c < "$model_file" | tr -d ' ')"
  if [ "$size" -lt "$min_bytes" ]; then
    echo "Downloaded model is too small: $model_file ($size bytes)" >&2
    exit 1
  fi

  echo "$model_file"
}

download_by_id() {
  local model_id="$1"

  case "$model_id" in
    qwen3-0.6b-mixed-int4)
      download_model \
        "qwen3_0_6b_mixed_int4.litertlm" \
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm" \
        450000000
      ;;
    qwen3-0.6b-dynamic-int8)
      download_model \
        "Qwen3-0.6B.litertlm" \
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm" \
        600000000
      ;;
    gemma4-e2b-it)
      download_model \
        "gemma-4-E2B-it.litertlm" \
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm" \
        2500000000
      ;;
    gemma4-e4b-it)
      download_model \
        "gemma-4-E4B-it.litertlm" \
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm" \
        3500000000
      ;;
    *)
      echo "Unknown model id: $model_id" >&2
      usage >&2
      exit 1
      ;;
  esac
}

if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
  usage
  exit 0
fi

if [ "$#" -eq 0 ]; then
  model_ids=("${DEFAULT_MODELS[@]}")
elif [ "$#" -eq 1 ] && [ "$1" = "all" ]; then
  model_ids=("${ALL_MODELS[@]}")
else
  model_ids=("$@")
fi

for model_id in "${model_ids[@]}"; do
  download_by_id "$model_id"
done
