# Android Benchmark Accuracy Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the phone benchmark results distinguish native decode measurements from pressure stress measurements, with accurate token-source and elapsed-time semantics.

**Architecture:** Keep the existing Bench page and `BenchmarkRunner`, but split execution by `BenchmarkMode`. Native decode runs LiteRT-LM's native benchmark API for selected `pp/tg` cases. Pressure stress uses the chat path with named worker counts, records configured vs actual wall time, and labels tokens as estimated.

**Tech Stack:** Kotlin, Android native views, LiteRT-LM 0.13.1, JUnit JVM tests, Gradle Android plugin.

---

### Task 1: Result Semantics

**Files:**
- Modify: `app/src/main/java/com/example/androidllm/benchmark/BenchmarkConfig.kt`
- Test: `app/src/test/java/com/example/androidllm/benchmark/BenchmarkConfigTest.kt`

- [ ] Add `BenchmarkMode`, `BenchmarkTokenSource`, and result fields for configured duration, wall elapsed, native prefill/decode TPS, TTFT, and token source.
- [ ] Add validation tests for decode mode requiring selected `pp` tests and stress mode requiring selected workers.
- [ ] Run `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest --tests 'com.example.androidllm.benchmark.BenchmarkConfigTest'`.

### Task 2: Native Decode Benchmark

**Files:**
- Modify: `app/src/main/java/com/example/androidllm/benchmark/BenchmarkRunner.kt`
- Modify: `app/src/main/java/com/example/androidllm/LocalLlmController.kt`
- Test: `app/src/test/java/com/example/androidllm/benchmark/BenchmarkRunnerTest.kt`

- [ ] Add `NativeBenchmarkMetrics` and a native benchmark callback dependency to `BenchmarkRunner`.
- [ ] Add tests proving decode mode uses native metrics, labels token source as native, and reports real decode token count rather than short-answer output.
- [ ] Wire `LocalLlmController.runNativeBenchmark()` to LiteRT-LM's native `benchmark(modelPath, Backend.CPU, pp, tg, cacheDir)` API.

### Task 3: Pressure Workers

**Files:**
- Create: `app/src/main/java/com/example/androidllm/benchmark/StressScheduler.kt`
- Test: `app/src/test/java/com/example/androidllm/benchmark/StressSchedulerTest.kt`

- [ ] Add a scheduler that starts up to `workerCount` concurrent request loops until the deadline.
- [ ] Add tests proving two workers overlap and no new requests start after the duration window.
- [ ] Keep graceful stop semantics: in-flight requests finish and actual wall time is reported separately.

### Task 4: UI And Export

**Files:**
- Modify: `app/src/main/java/com/example/androidllm/MainActivity.kt`

- [ ] Add a mode selector with `Decode Benchmark` and `Pressure Stress`.
- [ ] Rename batch checkboxes to worker checkboxes.
- [ ] Show token source, configured duration, wall elapsed, TTFT, prefill TPS, and decode TPS in result cards when available.
- [ ] Update CSV export headers and values.
- [ ] Verify with `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew testDebugUnitTest assembleDebug`.
- [ ] Install on device with adb, run one decode benchmark and one pressure stress run, and record final results.
