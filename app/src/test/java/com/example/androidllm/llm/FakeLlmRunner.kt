package com.example.androidllm.llm

import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult

class FakeLlmRunner(
    private val reply: String,
) : LlmRunner {
    override fun status(): RunnerStatus = RunnerStatus(
        ready = true,
        modelPath = "/fake/model.litertlm",
        message = "fake runner ready",
    )

    override fun generate(request: ChatCompletionRequest): ChatCompletionResult =
        ChatCompletionResult(
            model = request.model,
            text = reply,
            promptTokens = request.latestUserMessage.split(Regex("\\s+")).filter { it.isNotBlank() }.size,
            completionTokens = reply.split(Regex("\\s+")).filter { it.isNotBlank() }.size,
        )
}

