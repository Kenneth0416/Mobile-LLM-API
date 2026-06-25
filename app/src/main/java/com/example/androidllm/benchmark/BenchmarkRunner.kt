package com.example.androidllm.benchmark

import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult
import com.example.androidllm.api.ChatMessage
import kotlin.math.ceil

class BenchmarkRunner(
    private val generate: (ChatCompletionRequest) -> ChatCompletionResult,
    private val nativeBenchmark: ((BenchmarkConfig, Int, Int) -> NativeBenchmarkMetrics)? = null,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val onProgress: (BenchmarkProgress) -> Unit = {},
    private val stressScheduler: StressScheduler = StressScheduler(nowMillis),
) {
    fun run(
        config: BenchmarkConfig,
        shouldCancel: () -> Boolean = { false },
    ): BenchmarkSummary {
        val validationErrors = config.validationErrors()
        require(validationErrors.isEmpty()) { validationErrors.joinToString("; ") }

        val results = mutableListOf<BenchmarkResult>()
        var warmupRequests = 0
        var cancelled = false

        if (config.mode == BenchmarkMode.DecodeBenchmark) {
            val native = nativeBenchmark
                ?: throw IllegalStateException("native benchmark engine is not available")
            config.prefillTokens.forEach { prefill ->
                if (shouldCancel()) {
                    cancelled = true
                    return@forEach
                }
                onProgress(BenchmarkProgress("native decode pp$prefill", results.size))
                results.add(runNativeDecode(config, prefill, native))
            }
            return BenchmarkSummary(
                modelId = config.model.id,
                warmupRequests = 0,
                results = results,
                cancelled = cancelled,
            )
        }

        if (config.warmupEnabled && !shouldCancel()) {
            val warmupPrefill = minOf(128, config.model.contextTokens - config.generationTokens)
                .coerceAtLeast(1)
            generate(request(config, warmupPrefill))
            warmupRequests += 1
        }

        config.prefillTokens.forEach { prefill ->
            if (shouldCancel()) {
                cancelled = true
                return@forEach
            }
            onProgress(BenchmarkProgress("single pp$prefill", results.size))
            results.add(runSingle(config, prefill))
        }

        config.stressWorkerCounts.forEach { workerCount ->
            if (shouldCancel()) {
                cancelled = true
                return@forEach
            }
            onProgress(BenchmarkProgress("stress ${workerLabel(workerCount)}", results.size))
            results.add(runStress(config, workerCount, shouldCancel))
            if (results.last().errorMessage == "cancelled") {
                cancelled = true
            }
        }

        return BenchmarkSummary(
            modelId = config.model.id,
            warmupRequests = warmupRequests,
            results = results,
            cancelled = cancelled,
        )
    }

    private fun runNativeDecode(
        config: BenchmarkConfig,
        prefill: Int,
        nativeBenchmark: (BenchmarkConfig, Int, Int) -> NativeBenchmarkMetrics,
    ): BenchmarkResult {
        val started = nowMillis()
        return try {
            val metrics = nativeBenchmark(config, prefill, config.generationTokens)
            val wallElapsed = (nowMillis() - started).coerceAtLeast(0L)
            val seconds = wallElapsed / 1_000.0
            val totalTps = if (seconds > 0.0) {
                (metrics.prefillTokens + metrics.decodeTokens) / seconds
            } else {
                0.0
            }
            BenchmarkResult(
                type = BenchmarkType.NativeDecode,
                name = "decode pp$prefill / tg${config.generationTokens}",
                modelId = config.model.id,
                prefillTokens = prefill,
                targetGenerationTokens = config.generationTokens,
                batchSize = 1,
                requestsCompleted = 1,
                errors = 0,
                elapsedMs = wallElapsed,
                promptTokens = metrics.prefillTokens,
                completionTokens = metrics.decodeTokens,
                averageLatencyMs = wallElapsed.toDouble(),
                p95LatencyMs = wallElapsed.toDouble(),
                totalTokensPerSecond = totalTps,
                outputTokensPerSecond = metrics.decodeTokensPerSecond,
                tokenSource = BenchmarkTokenSource.Native,
                wallElapsedMs = wallElapsed,
                timeToFirstTokenMs = metrics.timeToFirstTokenMs,
                prefillTokensPerSecond = metrics.prefillTokensPerSecond,
                decodeTokensPerSecond = metrics.decodeTokensPerSecond,
            )
        } catch (error: Exception) {
            buildResult(
                type = BenchmarkType.NativeDecode,
                name = "decode pp$prefill / tg${config.generationTokens}",
                modelId = config.model.id,
                prefillTokens = prefill,
                targetGenerationTokens = config.generationTokens,
                batchSize = 1,
                latencies = listOf((nowMillis() - started).coerceAtLeast(0L)),
                promptTokens = 0,
                completionTokens = 0,
                errors = 1,
                tokenSource = BenchmarkTokenSource.Native,
                errorMessage = error.message ?: "native benchmark failed",
            )
        }
    }

    private fun runSingle(config: BenchmarkConfig, prefill: Int): BenchmarkResult {
        val started = nowMillis()
        return try {
            val result = generate(request(config, prefill))
            val elapsed = (nowMillis() - started).coerceAtLeast(0L)
            buildResult(
                type = BenchmarkType.PressureStress,
                name = "pp$prefill / tg${config.generationTokens}",
                modelId = config.model.id,
                prefillTokens = prefill,
                targetGenerationTokens = config.generationTokens,
                batchSize = 1,
                latencies = listOf(elapsed),
                promptTokens = result.promptTokens,
                completionTokens = result.completionTokens,
                errors = 0,
            )
        } catch (error: Exception) {
            buildResult(
                type = BenchmarkType.PressureStress,
                name = "pp$prefill / tg${config.generationTokens}",
                modelId = config.model.id,
                prefillTokens = prefill,
                targetGenerationTokens = config.generationTokens,
                batchSize = 1,
                latencies = listOf((nowMillis() - started).coerceAtLeast(0L)),
                promptTokens = 0,
                completionTokens = 0,
                errors = 1,
                errorMessage = error.message ?: "benchmark request failed",
            )
        }
    }

    private fun runStress(
        config: BenchmarkConfig,
        workerCount: Int,
        shouldCancel: () -> Boolean,
    ): BenchmarkResult {
        val stressRun = stressScheduler.run(
            workerCount = workerCount,
            durationMs = config.durationSeconds * 1_000L,
            requestFactory = { request(config, StressPrefillTokens) },
            generate = generate,
            shouldCancel = shouldCancel,
        )

        return buildResult(
            type = BenchmarkType.PressureStress,
            name = "${workerLabel(workerCount)} / pp$StressPrefillTokens / tg${config.generationTokens}",
            modelId = config.model.id,
            prefillTokens = StressPrefillTokens,
            targetGenerationTokens = config.generationTokens,
            batchSize = workerCount,
            latencies = stressRun.latencies,
            promptTokens = stressRun.promptTokens,
            completionTokens = stressRun.completionTokens,
            errors = stressRun.errors,
            configuredDurationSeconds = config.durationSeconds,
            wallElapsedMs = stressRun.wallElapsedMs,
            errorMessage = "cancelled".takeIf { stressRun.cancelled },
        )
    }

    private fun request(config: BenchmarkConfig, prefill: Int): ChatCompletionRequest =
        ChatCompletionRequest(
            model = config.model.id,
            messages = listOf(
                ChatMessage("system", BenchmarkPromptFactory.SystemInstruction),
                ChatMessage("user", BenchmarkPromptFactory.promptForPrefill(prefill)),
            ),
            temperature = 0.0,
            topP = 1.0,
            topK = null,
            maxTokens = config.generationTokens,
            stream = false,
        )

    private fun buildResult(
        type: BenchmarkType,
        name: String,
        modelId: String,
        prefillTokens: Int,
        targetGenerationTokens: Int,
        batchSize: Int,
        latencies: List<Long>,
        promptTokens: Int,
        completionTokens: Int,
        errors: Int,
        tokenSource: BenchmarkTokenSource = BenchmarkTokenSource.Estimated,
        configuredDurationSeconds: Int? = null,
        wallElapsedMs: Long? = null,
        errorMessage: String? = null,
    ): BenchmarkResult {
        val requestsCompleted = latencies.size - errors
        val elapsedMs = latencies.sum().coerceAtLeast(0L)
        val averageLatency = if (latencies.isEmpty()) 0.0 else latencies.average()
        val sorted = latencies.sorted()
        val p95 = if (sorted.isEmpty()) {
            0.0
        } else {
            val index = (ceil(sorted.size * 0.95).toInt() - 1).coerceIn(0, sorted.lastIndex)
            sorted[index].toDouble()
        }
        val wallElapsed = (wallElapsedMs ?: elapsedMs).coerceAtLeast(0L)
        val seconds = wallElapsed / 1_000.0
        val totalTps = if (seconds > 0.0) (promptTokens + completionTokens) / seconds else 0.0
        val outputTps = if (seconds > 0.0) completionTokens / seconds else 0.0

        return BenchmarkResult(
            type = type,
            name = name,
            modelId = modelId,
            prefillTokens = prefillTokens,
            targetGenerationTokens = targetGenerationTokens,
            batchSize = batchSize,
            requestsCompleted = requestsCompleted.coerceAtLeast(0),
            errors = errors,
            elapsedMs = elapsedMs,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            averageLatencyMs = averageLatency,
            p95LatencyMs = p95,
            totalTokensPerSecond = totalTps,
            outputTokensPerSecond = outputTps,
            tokenSource = tokenSource,
            configuredDurationSeconds = configuredDurationSeconds,
            wallElapsedMs = wallElapsed,
            errorMessage = errorMessage,
        )
    }

    companion object {
        const val StressPrefillTokens = 1024

        private fun workerLabel(workerCount: Int): String =
            "$workerCount worker${if (workerCount == 1) "" else "s"}"
    }
}
