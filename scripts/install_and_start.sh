#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.example.androidllm"
PORT="18080"
MODEL_DIR="$ROOT_DIR/models"
DEVICE_MODEL_DIR="/sdcard/Android/data/$PKG/files/models"

if [ ! -f "$APK" ]; then
  echo "Missing APK: $APK" >&2
  exit 1
fi

if ! compgen -G "$MODEL_DIR/*.litertlm" >/dev/null; then
  echo "Missing LiteRT-LM models in: $MODEL_DIR" >&2
  echo "Run scripts/download_model.sh first." >&2
  exit 1
fi

"$ADB" install -r "$APK"
"$ADB" shell "mkdir -p '$DEVICE_MODEL_DIR'"
for model_file in "$MODEL_DIR"/*.litertlm; do
  "$ADB" push "$model_file" "$DEVICE_MODEL_DIR/"
done
"$ADB" reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
"$ADB" reverse --remove tcp:8080 >/dev/null 2>&1 || true
"$ADB" forward "tcp:$PORT" "tcp:$PORT"
"$ADB" shell am force-stop "$PKG" || true
"$ADB" shell input keyevent BACK || true
"$ADB" shell monkey -p "$PKG" 1 >/dev/null
sleep 5
curl -fsS "http://127.0.0.1:$PORT/health"
echo
