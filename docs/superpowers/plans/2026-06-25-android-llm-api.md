# Android LLM API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build, install, and verify an Android app that serves local LiteRT-LM chat completions from the connected phone.

**Architecture:** The Android app starts a foreground service with an embedded HTTP server on port 18080. HTTP handlers are pure Kotlin and tested on the JVM; the Android service wires them to a LiteRT-LM runner that loads a `.litertlm` model from app external storage.

**Tech Stack:** Kotlin, Android Gradle Plugin, LiteRT-LM Android AAR 0.13.1, Gson, JUnit 4, adb.

---

### Task 1: Project And Red Tests

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/test/java/com/example/androidllm/api/ChatApiTest.kt`
- Create: `app/src/test/java/com/example/androidllm/api/ChatHttpHandlerTest.kt`
- Create: `app/src/test/java/com/example/androidllm/model/ModelCatalogTest.kt`

- [x] **Step 1: Write failing tests**

The tests assert request parsing, OpenAI-compatible response formatting, HTTP handler behavior, and device-aware model recommendation.

- [ ] **Step 2: Run tests to verify failure**

Run: `./gradlew testDebugUnitTest`
Expected: failure because `ChatApi`, `ChatHttpHandler`, `FakeLlmRunner`, `ModelCatalog`, and `DeviceProfile` do not exist yet.

### Task 2: Pure Kotlin API Core

**Files:**
- Create: `app/src/main/java/com/example/androidllm/api/ChatApi.kt`
- Create: `app/src/main/java/com/example/androidllm/api/ChatHttpHandler.kt`
- Create: `app/src/main/java/com/example/androidllm/llm/LlmRunner.kt`
- Create: `app/src/main/java/com/example/androidllm/model/ModelCatalog.kt`

- [ ] **Step 1: Implement the smallest pure Kotlin API core**

Create data classes for chat messages, completion requests/results, HTTP request/response, model options, and device profile. Implement Gson parsing and response formatting.

- [ ] **Step 2: Run tests**

Run: `./gradlew testDebugUnitTest`
Expected: all JVM unit tests pass.

### Task 3: Android Service And LiteRT-LM Runner

**Files:**
- Create: `app/src/main/java/com/example/androidllm/HttpApiServer.kt`
- Create: `app/src/main/java/com/example/androidllm/LlmApiService.kt`
- Create: `app/src/main/java/com/example/androidllm/MainActivity.kt`
- Create: `app/src/main/java/com/example/androidllm/llm/LiteRtLlmRunner.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add embedded HTTP server**

Use `ServerSocket` to listen on port 18080, parse request line/headers/body, call `ChatHttpHandler`, and return JSON responses with CORS headers.

- [ ] **Step 2: Add Android wiring**

`MainActivity` starts `LlmApiService`. The service creates `LiteRtLlmRunner`, starts the HTTP server, and loads the active model from `getExternalFilesDir("models")`.

- [ ] **Step 3: Build APK**

Run: `./gradlew assembleDebug`
Expected: debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

### Task 4: Model Download, Install, And Runtime Verification

**Files:**
- Create: `scripts/download_model.sh`
- Create: `scripts/install_and_start.sh`
- Create: `scripts/call_api.sh`

- [ ] **Step 1: Download model**

Run: `scripts/download_model.sh`
Expected: `models/qwen3_0_6b_mixed_int4.litertlm` exists and is about 497 MB.

- [ ] **Step 2: Install app and push model**

Run: `scripts/install_and_start.sh`
Expected: APK installed, model pushed to `/sdcard/Android/data/com.example.androidllm/files/models/`, app launched, and `adb forward tcp:18080 tcp:18080` configured.

- [ ] **Step 3: Call local phone LLM API**

Run: `scripts/call_api.sh`
Expected: JSON response containing `object: chat.completion` and an assistant message produced by the phone model.
