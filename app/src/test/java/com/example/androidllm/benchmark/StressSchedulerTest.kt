package com.example.androidllm.benchmark

import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult
import com.example.androidllm.api.ChatMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StressSchedulerTest {
    @Test
    fun runStartsWorkerLoopsConcurrently() {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val entered = CountDownLatch(2)
        val scheduler = StressScheduler()

        val run = scheduler.run(
            workerCount = 2,
            durationMs = 80L,
            requestFactory = { request() },
            generate = {
                val current = active.incrementAndGet()
                maxActive.updateAndGet { previous -> maxOf(previous, current) }
                entered.countDown()
                entered.await(500, TimeUnit.MILLISECONDS)
                Thread.sleep(30)
                active.decrementAndGet()
                result()
            },
            shouldCancel = { false },
        )

        assertTrue("expected at least two active workers", maxActive.get() >= 2)
        assertTrue(run.latencies.size >= 2)
        assertEquals(0, run.errors)
    }

    @Test
    fun runDoesNotStartNewRequestsAfterDurationWindow() {
        val calls = AtomicInteger(0)
        val scheduler = StressScheduler()

        val run = scheduler.run(
            workerCount = 1,
            durationMs = 50L,
            requestFactory = { request() },
            generate = {
                calls.incrementAndGet()
                Thread.sleep(80)
                result()
            },
            shouldCancel = { false },
        )

        assertEquals(1, calls.get())
        assertEquals(1, run.latencies.size)
        assertTrue(run.wallElapsedMs >= 80L)
    }

    private fun request(): ChatCompletionRequest =
        ChatCompletionRequest(
            model = "qwen3-0.6b-mixed-int4",
            messages = listOf(ChatMessage("user", "benchmark")),
            temperature = 0.0,
            topP = 1.0,
            topK = null,
            maxTokens = 8,
            stream = false,
        )

    private fun result(): ChatCompletionResult =
        ChatCompletionResult(
            model = "qwen3-0.6b-mixed-int4",
            text = "OK",
            promptTokens = 1024,
            completionTokens = 8,
        )
}
