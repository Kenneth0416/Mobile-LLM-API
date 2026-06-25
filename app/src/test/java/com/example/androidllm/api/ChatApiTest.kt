package com.example.androidllm.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatApiTest {
    @Test
    fun parseChatCompletionRequestReadsOpenAiMessagesAndSamplingOptions() {
        val request = ChatApi.parseChatRequest(
            """
            {
              "model": "qwen3-0.6b-mixed-int4",
              "messages": [
                {"role": "system", "content": "You are concise."},
                {"role": "user", "content": "Say hello"}
              ],
              "temperature": 0.4,
              "top_p": 0.8,
              "max_tokens": 64,
              "stream": false
            }
            """.trimIndent()
        )

        assertEquals("qwen3-0.6b-mixed-int4", request.model)
        assertEquals(2, request.messages.size)
        assertEquals("system", request.messages[0].role)
        assertEquals("You are concise.", request.systemInstruction)
        assertEquals("Say hello", request.latestUserMessage)
        assertEquals(0.4, request.temperature!!, 0.0001)
        assertEquals(0.8, request.topP!!, 0.0001)
        assertEquals(64, request.maxTokens)
        assertFalse(request.stream)
    }

    @Test
    fun toOpenAiResponseWrapsAssistantTextAndModelName() {
        val responseJson = ChatApi.toOpenAiChatResponse(
            ChatCompletionResult(
                model = "qwen3-0.6b-mixed-int4",
                text = "Hello from the phone.",
                promptTokens = 5,
                completionTokens = 4,
            )
        )

        assertTrue(responseJson.contains("\"object\":\"chat.completion\""))
        assertTrue(responseJson.contains("\"model\":\"qwen3-0.6b-mixed-int4\""))
        assertTrue(responseJson.contains("\"role\":\"assistant\""))
        assertTrue(responseJson.contains("Hello from the phone."))
        assertTrue(responseJson.contains("\"total_tokens\":9"))
    }

    @Test
    fun parseChatCompletionRequestRejectsMissingUserMessage() {
        try {
            ChatApi.parseChatRequest(
                """{"messages":[{"role":"system","content":"Only system"}]}"""
            )
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("user message"))
            return
        }

        throw AssertionError("Expected missing user message to fail")
    }
}

