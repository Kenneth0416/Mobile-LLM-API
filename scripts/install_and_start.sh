#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.example.androidllm"
PORT="18080"
MODEL_FILE="$ROOT_DIR/models/qwen3_0_6b_mixed_int4.litertlm"
DEVICE_MODEL_DIR="/sdcard/Android/data/$PKG/files/models"

if [ ! -f "$APK" ]; then
  echo "Missing APK: $APK" >&2
  exit 1
fi

if [ ! -f "$MODEL_FILE" ]; then
  echo "Missing model: $MODEL_FILE" >&2
  echo "Run scripts/download_model.sh first." >&2
  exit 1
fi

"$ADB" install -r "$APK"
"$ADB" shell "mkdir -p '$DEVICE_MODEL_DIR'"
"$ADB" push "$MODEL_FILE" "$DEVICE_MODEL_DIR/"
"$ADB" reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
"$ADB" reverse --remove tcp:8080 >/dev/null 2>&1 || true
"$ADB" forward "tcp:$PORT" "tcp:$PORT"
"$ADB" shell am force-stop "$PKG" || true
"$ADB" shell input keyevent BACK || true
"$ADB" shell monkey -p "$PKG" 1 >/dev/null
sleep 5
curl -fsS "http://127.0.0.1:$PORT/health"
echo
