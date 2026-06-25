# Android Benchmark Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a phone-local benchmark and stress-test mode that can select an installed model, run configured prompt sizes and timed stress loops, and report inference metrics.

**Architecture:** Add a pure Kotlin benchmark core under `com.example.androidllm.benchmark` that builds deterministic prompts, validates benchmark configuration, runs single-request and stress cases through the existing `LocalLlmController`, and summarizes metrics. Extend the native Android Views UI with a new `Bench` page between `Test` and `API`.

**Tech Stack:** Kotlin, Android Views, JUnit 4, existing LiteRT-LM runner abstraction.

---

### Task 1: Benchmark Core Tests

**Files:**
- Create: `app/src/test/java/com/example/androidllm/benchmark/BenchmarkConfigTest.kt`
- Create: `app/src/test/java/com/example/androidllm/benchmark/BenchmarkPromptFactoryTest.kt`
- Create: `app/src/test/java/com/example/androidllm/benchmark/BenchmarkRunnerTest.kt`

- [ ] Write failing JVM tests for configuration validation: installed model required, selected prompt-prefill plus generation length cannot exceed model context, and duration must be positive.
- [ ] Write failing JVM tests for deterministic prompt generation: generated prompt has a rough token count near the requested prefill size.
- [ ] Write failing JVM tests for single request metrics and timed stress execution using a fake generator.
- [ ] Run `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests com.example.androidllm.benchmark.*` and confirm benchmark classes are missing.

### Task 2: Benchmark Core Implementation

**Files:**
- Create: `app/src/main/java/com/example/androidllm/benchmark/BenchmarkConfig.kt`
- Create: `app/src/main/java/com/example/androidllm/benchmark/BenchmarkPromptFactory.kt`
- Create: `app/src/main/java/com/example/androidllm/benchmark/BenchmarkRunner.kt`

- [ ] Implement `BenchmarkConfig`, `BenchmarkCase`, `BenchmarkResult`, `BenchmarkSummary`, and validation helpers.
- [ ] Implement deterministic prompt generation from repeated plain text segments.
- [ ] Implement a runner that executes warmup, single request cases, and timed stress loops with cancellation support.
- [ ] Run focused benchmark tests until they pass.

### Task 3: Phone UI Integration

**Files:**
- Modify: `app/src/main/java/com/example/androidllm/MainActivity.kt`

- [ ] Add `Bench` to bottom navigation.
- [ ] Add model selector, duration selector, generation-length selector, single request checkboxes, stress batch checkboxes, Run/Stop buttons, progress text, and result cards.
- [ ] Run benchmark work on the existing background executor and update UI on the main thread.
- [ ] Allow copying summary results as text.

### Task 4: Verification

**Files:**
- No source edits expected unless verification finds a defect.

- [ ] Run `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest`.
- [ ] Run `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew assembleDebug`.
