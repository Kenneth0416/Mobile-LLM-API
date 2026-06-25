#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
MODEL_DIR="$ROOT_DIR/models"
MODEL_FILE="$MODEL_DIR/qwen3_0_6b_mixed_int4.litertlm"
MODEL_URL="https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm"
MIN_BYTES=450000000

mkdir -p "$MODEL_DIR"
curl -L -C - -o "$MODEL_FILE" "$MODEL_URL"

SIZE="$(wc -c < "$MODEL_FILE" | tr -d ' ')"
if [ "$SIZE" -lt "$MIN_BYTES" ]; then
  echo "Downloaded model is too small: $SIZE bytes" >&2
  exit 1
fi

echo "$MODEL_FILE"

