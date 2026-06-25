#!/usr/bin/env bash
set -euo pipefail

PORT="${PORT:-18080}"

curl -fsS "http://127.0.0.1:$PORT/v1/chat/completions" \
  -H 'content-type: application/json' \
  -d '{
    "model": "qwen3-0.6b-mixed-int4",
    "messages": [
      {"role": "system", "content": "You are running locally on an Android phone. Answer briefly."},
      {"role": "user", "content": "Say one short sentence proving the phone LLM API works."}
    ],
    "temperature": 0.2,
    "max_tokens": 64
  }'
echo
