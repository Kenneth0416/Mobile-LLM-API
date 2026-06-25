package com.example.androidllm.llm

import android.content.Context
import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult
import com.example.androidllm.api.ChatMessage
import com.example.androidllm.model.ModelOption
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

class LiteRtLlmRunner(
    context: Context,
    private val modelOption: ModelOption,
    private val modelFile: File,
    private val cpuThreads: Int,
) : LlmRunner, AutoCloseable {
    private val cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }
    private val lock = Any()
    private var engine: Engine? = null

    override fun status(): RunnerStatus {
        val initialized = synchronized(lock) { engine?.isInitialized() == true }
        return RunnerStatus(
            ready = modelFile.exists() && initialized,
            modelPath = modelFile.absolutePath,
            message = when {
                !modelFile.exists() -> "model file missing"
                initialized -> "LiteRT-LM engine initialized"
                else -> "model present; engine will initialize on first request"
            },
            modelId = modelOption.id,
        )
    }

    override fun generate(request: ChatCompletionRequest): ChatCompletionResult {
        if (!modelFile.exists()) {
            throw IllegalStateException("model file missing: ${modelFile.absolutePath}")
        }

        val engine = ensureEngine()
        val conversationConfig = ConversationConfig(
            systemInstruction = request.systemInstruction?.let { Contents.of(it) },
            initialMessages = request.historyBeforeLatestUser().mapNotNull { it.toLiteRtMessage() },
            samplerConfig = SamplerConfig(
                topK = request.topK ?: 40,
                topP = request.topP ?: 0.95,
                temperature = request.temperature ?: 0.7,
                seed = 0,
            ),
        )

        engine.createConversation(conversationConfig).use { conversation ->
            val message = conversation.sendMessage(request.latestUserMessage)
            val text = message.textContent().ifBlank { message.toString() }
            return ChatCompletionResult(
                model = request.model.ifBlank { modelOption.id },
                text = text,
                promptTokens = TokenEstimator.roughCount(request.latestUserMessage),
                completionTokens = TokenEstimator.roughCount(text),
            )
        }
    }

    private fun ensureEngine(): Engine = synchronized(lock) {
        val current = engine
        if (current != null && current.isInitialized()) {
            return@synchronized current
        }

        val created = Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU(numOfThreads = cpuThreads),
                maxNumTokens = modelOption.contextTokens,
                cacheDir = cacheDir.absolutePath,
            )
        )
        created.initialize()
        engine = created
        created
    }

    override fun close() {
        synchronized(lock) {
            engine?.close()
            engine = null
        }
    }

    private fun ChatCompletionRequest.historyBeforeLatestUser(): List<ChatMessage> {
        val latestUserIndex = messages.indexOfLast { it.role == "user" && it.content.isNotBlank() }
        if (latestUserIndex <= 0) return emptyList()
        return messages.take(latestUserIndex).filter { it.role != "system" && it.content.isNotBlank() }
    }

    private fun ChatMessage.toLiteRtMessage(): Message? = when (role) {
        "user" -> Message.user(content)
        "assistant", "model" -> Message.model(content)
        else -> null
    }

    private fun Message.textContent(): String =
        contents.contents.joinToString(separator = "") { content ->
            when (content) {
                is Content.Text -> content.text
                else -> content.toString()
            }
        }

}
