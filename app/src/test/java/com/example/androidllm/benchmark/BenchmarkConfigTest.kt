package com.example.androidllm.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkConfigTest {
    @Test
    fun validConfigHasNoValidationErrors() {
        val config = BenchmarkConfig(
            model = BenchmarkModel(
                id = "qwen3-0.6b-mixed-int4",
                displayName = "Qwen3 0.6B mixed int4",
                contextTokens = 2048,
                installed = true,
            ),
            mode = BenchmarkMode.DecodeBenchmark,
            durationSeconds = 60,
            generationTokens = 128,
            prefillTokens = listOf(128, 512),
            stressWorkerCounts = emptyList(),
            warmupEnabled = true,
        )

        assertTrue(config.validationErrors().isEmpty())
    }

    @Test
    fun validationRejectsMissingSelectedModel() {
        val config = BenchmarkConfig(
            model = BenchmarkModel(
                id = "qwen3-0.6b-dynamic-int8",
                displayName = "Qwen3 0.6B dynamic int8",
                contextTokens = 4096,
                installed = false,
            ),
            mode = BenchmarkMode.DecodeBenchmark,
            durationSeconds = 30,
            generationTokens = 128,
            prefillTokens = listOf(512),
            stressWorkerCounts = emptyList(),
            warmupEnabled = false,
        )

        assertEquals(listOf("selected model is not installed"), config.validationErrors())
    }

    @Test
    fun validationRejectsPromptPlusGenerationPastContext() {
        val config = BenchmarkConfig(
            model = BenchmarkModel(
                id = "qwen3-0.6b-mixed-int4",
                displayName = "Qwen3 0.6B mixed int4",
                contextTokens = 2048,
                installed = true,
            ),
            mode = BenchmarkMode.DecodeBenchmark,
            durationSeconds = 30,
            generationTokens = 256,
            prefillTokens = listOf(1024, 2048),
            stressWorkerCounts = emptyList(),
            warmupEnabled = false,
        )

        assertEquals(
            listOf("pp2048 + tg256 exceeds model context 2048"),
            config.validationErrors(),
        )
    }

    @Test
    fun validationRejectsNonPositiveDuration() {
        val config = BenchmarkConfig(
            model = BenchmarkModel(
                id = "qwen3-0.6b-mixed-int4",
                displayName = "Qwen3 0.6B mixed int4",
                contextTokens = 2048,
                installed = true,
            ),
            mode = BenchmarkMode.PressureStress,
            durationSeconds = 0,
            generationTokens = 128,
            prefillTokens = emptyList(),
            stressWorkerCounts = listOf(1),
            warmupEnabled = false,
        )

        assertEquals(listOf("duration must be greater than 0 seconds"), config.validationErrors())
    }

    @Test
    fun validationRejectsDecodeBenchmarkWithoutPrefillTests() {
        val config = installedConfig(
            mode = BenchmarkMode.DecodeBenchmark,
            prefillTokens = emptyList(),
            stressWorkerCounts = emptyList(),
        )

        assertEquals(listOf("select at least one pp test"), config.validationErrors())
    }

    @Test
    fun validationRejectsPressureStressWithoutWorkers() {
        val config = installedConfig(
            mode = BenchmarkMode.PressureStress,
            prefillTokens = emptyList(),
            stressWorkerCounts = emptyList(),
        )

        assertEquals(listOf("select at least one stress worker count"), config.validationErrors())
    }

    private fun installedConfig(
        mode: BenchmarkMode,
        prefillTokens: List<Int>,
        stressWorkerCounts: List<Int>,
    ): BenchmarkConfig =
        BenchmarkConfig(
            model = BenchmarkModel(
                id = "qwen3-0.6b-mixed-int4",
                displayName = "Qwen3 0.6B mixed int4",
                contextTokens = 2048,
                installed = true,
            ),
            mode = mode,
            durationSeconds = 30,
            generationTokens = 128,
            prefillTokens = prefillTokens,
            stressWorkerCounts = stressWorkerCounts,
            warmupEnabled = false,
        )
}
