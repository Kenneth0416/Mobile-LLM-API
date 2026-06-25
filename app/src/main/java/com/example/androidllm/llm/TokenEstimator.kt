package com.example.androidllm.llm

import kotlin.math.roundToInt

object TokenEstimator {
    private val cjk = Regex("[\\u3400-\\u9fff]")
    private val words = Regex("[A-Za-z0-9_]+(?:[-'][A-Za-z0-9_]+)*")
    private val punctuation = Regex("[^\\sA-Za-z0-9_\\u3400-\\u9fff]")

    fun roughCount(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return 0

        val estimate =
            cjk.findAll(trimmed).count() * 0.75 +
                words.findAll(trimmed).count() * 1.25 +
                punctuation.findAll(trimmed).count() * 0.25

        return maxOf(1, estimate.roundToInt())
    }
}
