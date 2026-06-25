package com.example.androidllm.llm

import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult

data class RunnerStatus(
    val ready: Boolean,
    val modelPath: String?,
    val message: String,
)

interface LlmRunner {
    fun status(): RunnerStatus

    fun generate(request: ChatCompletionRequest): ChatCompletionResult
}

