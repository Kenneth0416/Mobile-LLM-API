package com.example.androidllm.benchmark

import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class StressRun(
    val latencies: List<Long>,
    val promptTokens: Int,
    val completionTokens: Int,
    val errors: Int,
    val cancelled: Boolean,
    val wallElapsedMs: Long,
)

class StressScheduler(
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    fun run(
        workerCount: Int,
        durationMs: Long,
        requestFactory: () -> ChatCompletionRequest,
        generate: (ChatCompletionRequest) -> ChatCompletionResult,
        shouldCancel: () -> Boolean,
    ): StressRun {
        require(workerCount > 0) { "worker count must be positive" }
        require(durationMs > 0L) { "duration must be positive" }

        val started = nowMillis()
        val deadline = started + durationMs
        val executor = Executors.newFixedThreadPool(workerCount)
        val latencies = Collections.synchronizedList(mutableListOf<Long>())
        val promptTokens = AtomicInteger(0)
        val completionTokens = AtomicInteger(0)
        val errors = AtomicInteger(0)
        val cancelled = AtomicBoolean(false)

        repeat(workerCount) {
            executor.execute {
                while (nowMillis() < deadline) {
                    if (shouldCancel()) {
                        cancelled.set(true)
                        break
                    }
                    val requestStarted = nowMillis()
                    try {
                        val result = generate(requestFactory())
                        promptTokens.addAndGet(result.promptTokens)
                        completionTokens.addAndGet(result.completionTokens)
                    } catch (_: Exception) {
                        errors.incrementAndGet()
                    }
                    latencies.add((nowMillis() - requestStarted).coerceAtLeast(0L))
                }
            }
        }

        executor.shutdown()
        while (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
            if (shouldCancel()) {
                cancelled.set(true)
            }
        }

        return StressRun(
            latencies = latencies.toList(),
            promptTokens = promptTokens.get(),
            completionTokens = completionTokens.get(),
            errors = errors.get(),
            cancelled = cancelled.get(),
            wallElapsedMs = (nowMillis() - started).coerceAtLeast(0L),
        )
    }
}
