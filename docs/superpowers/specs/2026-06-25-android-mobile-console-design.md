# Android Mobile Console Design

## Goal

Turn the Android_LLM app from a single status text screen into a phone-first local LLM console with multiple pages and an on-device model test bench that does not require a computer, adb forwarding, or the external web chatbot.

## Product Shape

The phone app will use a native Android view UI with bottom navigation and four pages:

- Status: foreground service state, API port, runner readiness, active model path, device RAM, SoC, and ABI.
- Models: the recommended model, installed state, active state, file path, license, size, context tokens, and output-token limits for each catalog model.
- Test Bench: prompt input, model selection, temperature, top-p, max tokens, run button, answer/error output, elapsed time, copy, clear, and local run history.
- API: existing computer-facing endpoint details, adb forward command, and a curl example.

## Architecture

The existing HTTP API remains available. A new application-owned local LLM controller creates one shared switching runner and exposes the same model runtime to both the foreground service and the phone UI. This keeps the model loading path single-source: computer API requests and phone test-bench requests use the same catalog, model files, and LiteRT-LM runner.

The test bench builds `ChatCompletionRequest` objects directly and calls the shared runner in a background executor. It never depends on the embedded HTTP server, localhost networking, adb forwarding, or the Mac-side web chatbot.

## Implementation Constraints

- Keep the UI native Android Views to match the current lightweight project.
- Do not introduce Compose, AppCompat, or a large design system dependency.
- Preserve the existing OpenAI-style HTTP endpoints.
- Preserve existing dirty worktree changes and build on them.
- Use JVM unit tests for pure behavior: request construction, history, model switching, and runtime status.

## Verification

Completion requires:

- JVM tests pass with `./gradlew testDebugUnitTest`.
- Debug APK builds with `./gradlew assembleDebug`.
- `MainActivity` contains a multi-page phone UI rather than the old single `TextView`.
- The test bench calls the shared local runner directly.
- The foreground service still starts the HTTP API on port 18080.
