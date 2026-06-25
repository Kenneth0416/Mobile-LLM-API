package com.example.androidllm.llm

import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult

data class RunnerStatus(
    val ready: Boolean,
    val modelPath: String?,
    val message: String,
    val modelId: String? = null,
)

data class ModelRuntimeStatus(
    val modelId: String,
    val installed: Boolean,
    val active: Boolean,
    val ready: Boolean,
    val modelPath: String,
    val message: String,
)

interface LlmRunner {
    fun status(): RunnerStatus

    fun modelStatuses(): List<ModelRuntimeStatus> = emptyList()

    fun generate(request: ChatCompletionRequest): ChatCompletionResult
}
