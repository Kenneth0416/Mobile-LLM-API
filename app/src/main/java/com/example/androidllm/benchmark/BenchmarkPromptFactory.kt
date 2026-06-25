package com.example.androidllm.benchmark

import com.example.androidllm.llm.TokenEstimator

object BenchmarkPromptFactory {
    private const val ShortAnswerInstruction =
        "Benchmark probe. Reply exactly: OK. Do not explain. /no_think"
    const val SystemInstruction =
        "You are running a local Android benchmark. Reply exactly OK. " +
            "Do not include reasoning, markdown, or extra words. /no_think"

    private val words = listOf(
        "phone",
        "local",
        "benchmark",
        "measures",
        "steady",
        "inference",
        "throughput",
        "latency",
        "tokens",
        "android",
        "runtime",
        "model",
        "prompt",
        "response",
        "stress",
        "result",
    )

    fun promptForPrefill(targetTokens: Int): String {
        require(targetTokens > 0) { "prefill tokens must be positive" }
        val builder = StringBuilder(ShortAnswerInstruction)
        var index = 0
        val suffix = "\n\nEnd of benchmark filler. Reply exactly: OK. Stop."
        while (TokenEstimator.roughCount(builder.toString() + suffix) < targetTokens) {
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(words[index % words.size])
            index += 1
        }
        return builder.append(suffix).toString()
    }
}
