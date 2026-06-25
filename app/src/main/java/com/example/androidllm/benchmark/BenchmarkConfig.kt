package com.example.androidllm.benchmark

data class BenchmarkModel(
    val id: String,
    val displayName: String,
    val contextTokens: Int,
    val installed: Boolean,
)

data class BenchmarkConfig(
    val model: BenchmarkModel,
    val mode: BenchmarkMode,
    val durationSeconds: Int,
    val generationTokens: Int,
    val prefillTokens: List<Int>,
    val stressWorkerCounts: List<Int>,
    val warmupEnabled: Boolean,
) {
    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (!model.installed) {
            errors.add("selected model is not installed")
        }
        if (durationSeconds <= 0) {
            errors.add("duration must be greater than 0 seconds")
        }
        if (generationTokens <= 0) {
            errors.add("generation length must be greater than 0 tokens")
        }
        prefillTokens.forEach { prefill ->
            if (prefill + generationTokens > model.contextTokens) {
                errors.add("pp$prefill + tg$generationTokens exceeds model context ${model.contextTokens}")
            }
        }
        if (mode == BenchmarkMode.DecodeBenchmark && prefillTokens.isEmpty()) {
            errors.add("select at least one pp test")
        }
        if (mode == BenchmarkMode.PressureStress && stressWorkerCounts.isEmpty()) {
            errors.add("select at least one stress worker count")
        }
        if (stressWorkerCounts.any { it <= 0 }) {
            errors.add("stress worker counts must be positive")
        }
        return errors
    }
}

enum class BenchmarkMode {
    DecodeBenchmark,
    PressureStress,
}

enum class BenchmarkTokenSource {
    Native,
    Estimated,
}

enum class BenchmarkType {
    NativeDecode,
    PressureStress,
}

data class BenchmarkResult(
    val type: BenchmarkType,
    val name: String,
    val modelId: String,
    val prefillTokens: Int,
    val targetGenerationTokens: Int,
    val batchSize: Int,
    val requestsCompleted: Int,
    val errors: Int,
    val elapsedMs: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val averageLatencyMs: Double,
    val p95LatencyMs: Double,
    val totalTokensPerSecond: Double,
    val outputTokensPerSecond: Double,
    val tokenSource: BenchmarkTokenSource = BenchmarkTokenSource.Estimated,
    val configuredDurationSeconds: Int? = null,
    val wallElapsedMs: Long = elapsedMs,
    val timeToFirstTokenMs: Double? = null,
    val prefillTokensPerSecond: Double? = null,
    val decodeTokensPerSecond: Double? = null,
    val errorMessage: String? = null,
)

data class BenchmarkProgress(
    val label: String,
    val completedResults: Int,
)

data class NativeBenchmarkMetrics(
    val initTimeMs: Double,
    val timeToFirstTokenMs: Double,
    val prefillTokens: Int,
    val decodeTokens: Int,
    val prefillTokensPerSecond: Double,
    val decodeTokensPerSecond: Double,
)

data class BenchmarkSummary(
    val modelId: String,
    val warmupRequests: Int,
    val results: List<BenchmarkResult>,
    val cancelled: Boolean,
)
