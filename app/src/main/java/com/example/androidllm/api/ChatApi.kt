package com.example.androidllm.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class ChatMessage(
    val role: String,
    val content: String,
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double?,
    val topP: Double?,
    val topK: Int?,
    val maxTokens: Int?,
    val stream: Boolean,
) {
    val systemInstruction: String?
        get() = messages.firstOrNull { it.role == "system" }?.content

    val latestUserMessage: String
        get() = messages.lastOrNull { it.role == "user" && it.content.isNotBlank() }?.content
            ?: throw IllegalArgumentException("chat request must include a non-empty user message")
}

data class ChatCompletionResult(
    val model: String,
    val text: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
)

object ChatApi {
    private const val DefaultModel = "qwen3-0.6b-mixed-int4"
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    fun parseChatRequest(json: String): ChatCompletionRequest {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException("request body must be valid JSON", error)
        }

        val messagesElement = root.get("messages")
            ?: throw IllegalArgumentException("chat request must include messages")
        if (!messagesElement.isJsonArray) {
            throw IllegalArgumentException("messages must be an array")
        }

        val messages = messagesElement.asJsonArray.mapIndexed { index, element ->
            if (!element.isJsonObject) {
                throw IllegalArgumentException("message at index $index must be an object")
            }
            val message = element.asJsonObject
            val role = message.stringOrNull("role")?.lowercase()
                ?: throw IllegalArgumentException("message at index $index must include role")
            val content = readContent(message.get("content"))
            ChatMessage(role, content)
        }

        val request = ChatCompletionRequest(
            model = root.stringOrNull("model") ?: DefaultModel,
            messages = messages,
            temperature = root.doubleOrNull("temperature"),
            topP = root.doubleOrNull("top_p"),
            topK = root.intOrNull("top_k"),
            maxTokens = root.intOrNull("max_tokens"),
            stream = root.booleanOrDefault("stream", false),
        )
        request.latestUserMessage
        return request
    }

    fun toOpenAiChatResponse(result: ChatCompletionResult): String {
        val message = JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", result.text)
        }
        val choice = JsonObject().apply {
            addProperty("index", 0)
            add("message", message)
            addProperty("finish_reason", "stop")
        }
        val choices = JsonArray().apply { add(choice) }
        val usage = JsonObject().apply {
            addProperty("prompt_tokens", result.promptTokens)
            addProperty("completion_tokens", result.completionTokens)
            addProperty("total_tokens", result.promptTokens + result.completionTokens)
        }
        val response = JsonObject().apply {
            addProperty("id", "chatcmpl-phone-${System.currentTimeMillis()}")
            addProperty("object", "chat.completion")
            addProperty("created", System.currentTimeMillis() / 1000)
            addProperty("model", result.model)
            add("choices", choices)
            add("usage", usage)
        }
        return gson.toJson(response)
    }

    fun errorJson(message: String, type: String = "invalid_request_error"): String {
        val error = JsonObject().apply {
            addProperty("message", message)
            addProperty("type", type)
        }
        return gson.toJson(JsonObject().apply { add("error", error) })
    }

    private fun readContent(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        if (element.isJsonPrimitive) return element.asString
        if (element.isJsonArray) {
            return element.asJsonArray.joinToString(separator = "") { part ->
                if (!part.isJsonObject) {
                    ""
                } else {
                    val obj = part.asJsonObject
                    when (obj.stringOrNull("type")) {
                        "text" -> obj.stringOrNull("text").orEmpty()
                        else -> ""
                    }
                }
            }
        }
        throw IllegalArgumentException("message content must be a string or text content array")
    }
}

private fun JsonObject.stringOrNull(name: String): String? =
    get(name)?.takeIf { !it.isJsonNull }?.asString

private fun JsonObject.doubleOrNull(name: String): Double? =
    get(name)?.takeIf { !it.isJsonNull }?.asDouble

private fun JsonObject.intOrNull(name: String): Int? =
    get(name)?.takeIf { !it.isJsonNull }?.asInt

private fun JsonObject.booleanOrDefault(name: String, defaultValue: Boolean): Boolean =
    get(name)?.takeIf { !it.isJsonNull }?.asBoolean ?: defaultValue

