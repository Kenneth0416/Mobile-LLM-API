package com.example.androidllm.ui

import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult
import com.example.androidllm.api.ChatMessage

data class ConsoleGenerationSettings(
    val modelId: String,
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val maxTokens: Int? = null,
)

data class ConsolePromptRun(
    val id: Long,
    val prompt: String,
    val response: String,
    val modelId: String,
    val elapsedMs: Long,
    val createdAtMillis: Long,
    val error: String? = null,
) {
    val succeeded: Boolean
        get() = error == null
}

class LocalTestSession(
    private val historyLimit: Int = 20,
) {
    private val runs = mutableListOf<ConsolePromptRun>()
    private var nextId = 1L

    fun buildRequest(prompt: String, settings: ConsoleGenerationSettings): ChatCompletionRequest {
        val cleanPrompt = prompt.trim()
        require(cleanPrompt.isNotBlank()) { "prompt cannot be blank" }
        return ChatCompletionRequest(
            model = settings.modelId,
            messages = listOf(ChatMessage(role = "user", content = cleanPrompt)),
            temperature = settings.temperature,
            topP = settings.topP,
            topK = settings.topK,
            maxTokens = settings.maxTokens,
            stream = false,
        )
    }

    fun recordSuccess(
        prompt: String,
        result: ChatCompletionResult,
        elapsedMs: Long,
        createdAtMillis: Long = System.currentTimeMillis(),
    ): ConsolePromptRun =
        addRun(
            ConsolePromptRun(
                id = nextId++,
                prompt = prompt.trim(),
                response = result.text,
                modelId = result.model,
                elapsedMs = elapsedMs,
                createdAtMillis = createdAtMillis,
            )
        )

    fun recordError(
        prompt: String,
        modelId: String,
        message: String,
        elapsedMs: Long,
        createdAtMillis: Long = System.currentTimeMillis(),
    ): ConsolePromptRun =
        addRun(
            ConsolePromptRun(
                id = nextId++,
                prompt = prompt.trim(),
                response = "",
                modelId = modelId,
                elapsedMs = elapsedMs,
                createdAtMillis = createdAtMillis,
                error = message,
            )
        )

    fun history(): List<ConsolePromptRun> = runs.toList()

    fun clearHistory() {
        runs.clear()
    }

    private fun addRun(run: ConsolePromptRun): ConsolePromptRun {
        runs.add(0, run)
        while (runs.size > historyLimit) {
            runs.removeAt(runs.lastIndex)
        }
        return run
    }
}
