package com.example.androidllm.benchmark

import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkRunnerTest {
    @Test
    fun runDecodeBenchmarkUsesNativeMetricsWithoutChatGeneration() {
        var now = 1_000L
        var generateCalls = 0
        val runner = BenchmarkRunner(
            generate = {
                generateCalls += 1
                error("decode benchmark must not use chat generation")
            },
            nativeBenchmark = { config, prefillTokens, generationTokens ->
                now += 500L
                assertEquals("qwen3-0.6b-mixed-int4", config.model.id)
                assertEquals(512, prefillTokens)
                assertEquals(128, generationTokens)
                NativeBenchmarkMetrics(
                    initTimeMs = 120.0,
                    timeToFirstTokenMs = 45.0,
                    prefillTokens = 512,
                    decodeTokens = 128,
                    prefillTokensPerSecond = 256.0,
                    decodeTokensPerSecond = 32.0,
                )
            },
            nowMillis = { now },
        )

        val summary = runner.run(
            config = config(
                mode = BenchmarkMode.DecodeBenchmark,
                durationSeconds = 1,
                generationTokens = 128,
                prefillTokens = listOf(512),
                stressWorkerCounts = emptyList(),
                warmupEnabled = false,
            )
        )

        assertFalse(summary.cancelled)
        assertEquals(0, summary.warmupRequests)
        assertEquals(0, generateCalls)
        val result = summary.results.single()
        assertEquals(BenchmarkType.NativeDecode, result.type)
        assertEquals(BenchmarkTokenSource.Native, result.tokenSource)
        assertEquals(512, result.promptTokens)
        assertEquals(128, result.completionTokens)
        assertEquals(45.0, result.timeToFirstTokenMs!!, 0.01)
        assertEquals(256.0, result.prefillTokensPerSecond!!, 0.01)
        assertEquals(32.0, result.decodeTokensPerSecond!!, 0.01)
        assertEquals(500L, result.wallElapsedMs)
    }

    @Test
    fun runPressureStressUsesEstimatedChatMetricsAndWorkerLabel() {
        var now = 0L
        val requests = mutableListOf<ChatCompletionRequest>()
        val runner = BenchmarkRunner(
            generate = { request ->
                requests.add(request)
                now += 100L
                ChatCompletionResult(
                    model = request.model,
                    text = "ok",
                    promptTokens = 1024,
                    completionTokens = request.maxTokens ?: 0,
                )
            },
            nowMillis = { now },
        )

        val summary = runner.run(
            config = config(
                mode = BenchmarkMode.PressureStress,
                durationSeconds = 1,
                generationTokens = 128,
                prefillTokens = emptyList(),
                stressWorkerCounts = listOf(1),
                warmupEnabled = false,
            )
        )

        val result = summary.results.single()
        assertEquals(BenchmarkType.PressureStress, result.type)
        assertEquals(BenchmarkTokenSource.Estimated, result.tokenSource)
        assertEquals("1 worker / pp1024 / tg128", result.name)
        assertEquals(1, result.batchSize)
        assertEquals(10, result.requestsCompleted)
        assertEquals(1_000L, result.elapsedMs)
        assertEquals(1, result.configuredDurationSeconds)
        assertEquals(0, result.errors)
        assertEquals("system", requests.first().messages.first().role)
        assertTrue(requests.first().messages.first().content.contains("Reply exactly OK"))
        assertTrue(result.totalTokensPerSecond > result.outputTokensPerSecond)
    }

    @Test
    fun runCanBeCancelledBeforeStressLoopStarts() {
        var calls = 0
        val runner = BenchmarkRunner(
            generate = {
                calls += 1
                ChatCompletionResult(
                    model = it.model,
                    text = "ok",
                    promptTokens = 128,
                    completionTokens = it.maxTokens ?: 0,
                )
            },
            nowMillis = { 0L },
        )

        val summary = runner.run(
            config = config(
                mode = BenchmarkMode.PressureStress,
                durationSeconds = 10,
                generationTokens = 32,
                prefillTokens = emptyList(),
                stressWorkerCounts = listOf(1),
                warmupEnabled = false,
            ),
            shouldCancel = { calls >= 1 },
        )

        assertTrue(summary.cancelled)
        assertEquals(1, calls)
        assertEquals(1, summary.results.size)
    }

    private fun config(
        mode: BenchmarkMode,
        durationSeconds: Int,
        generationTokens: Int,
        prefillTokens: List<Int>,
        stressWorkerCounts: List<Int>,
        warmupEnabled: Boolean,
    ): BenchmarkConfig =
        BenchmarkConfig(
            model = BenchmarkModel(
                id = "qwen3-0.6b-mixed-int4",
                displayName = "Qwen3 0.6B mixed int4",
                contextTokens = 2048,
                installed = true,
            ),
            mode = mode,
            durationSeconds = durationSeconds,
            generationTokens = generationTokens,
            prefillTokens = prefillTokens,
            stressWorkerCounts = stressWorkerCounts,
            warmupEnabled = warmupEnabled,
        )
}
