# Android Mobile Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a phone-first Android_LLM console with multiple pages and an on-device local model test bench.

**Architecture:** Add a shared application-owned local LLM controller around a switching runner. Keep the HTTP service, and replace the single `TextView` activity with a native bottom-nav console whose Test page calls the shared runner directly.

**Tech Stack:** Kotlin, Android Views, LiteRT-LM Android AAR, Gson, JUnit 4, Gradle.

---

### Task 1: Console Tests

**Files:**
- Create: `app/src/test/java/com/example/androidllm/ui/LocalTestSessionTest.kt`
- Modify: `app/src/test/java/com/example/androidllm/llm/SwitchingLlmRunnerTest.kt`

- [ ] Write failing tests for request building, history recording, clearing history, and switching model runtime status.
- [ ] Run `./gradlew testDebugUnitTest --tests com.example.androidllm.ui.LocalTestSessionTest --tests com.example.androidllm.llm.SwitchingLlmRunnerTest` and confirm missing production classes fail.

### Task 2: Shared Runtime Core

**Files:**
- Create: `app/src/main/java/com/example/androidllm/llm/SwitchingLlmRunner.kt`
- Create: `app/src/main/java/com/example/androidllm/ui/LocalTestSession.kt`

- [ ] Implement `SwitchingLlmRunner` so requests can select installed catalog models and status reports include installed, active, ready, and model path.
- [ ] Implement `LocalTestSession` so the phone UI can build `ChatCompletionRequest` values and keep bounded in-memory history.
- [ ] Run the focused tests until they pass.

### Task 3: Android Controller

**Files:**
- Create: `app/src/main/java/com/example/androidllm/AndroidLlmApp.kt`
- Create: `app/src/main/java/com/example/androidllm/LocalLlmController.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/androidllm/LlmApiService.kt`

- [ ] Add an application-owned controller with catalog, device profile, model directory, and shared switching runner.
- [ ] Wire `LlmApiService` to use the shared runner instead of creating its own isolated LiteRT runner.
- [ ] Keep the existing API routes and foreground notification behavior.

### Task 4: Multi-Page Phone UI

**Files:**
- Modify: `app/src/main/java/com/example/androidllm/MainActivity.kt`
- Modify: `app/src/main/res/values/styles.xml`

- [ ] Replace the single status `TextView` with a bottom-navigation native console.
- [ ] Build Status, Models, Test Bench, and API pages.
- [ ] Run test-bench generation on a background executor and update UI with answer, elapsed time, errors, copy, clear, and history.

### Task 5: Verification

**Files:**
- No source edits expected unless verification finds a defect.

- [ ] Run `./gradlew testDebugUnitTest`.
- [ ] Run `./gradlew assembleDebug`.
- [ ] Audit the objective: mobile UI optimized, multiple pages present, and local phone test bench independent from computer access.
