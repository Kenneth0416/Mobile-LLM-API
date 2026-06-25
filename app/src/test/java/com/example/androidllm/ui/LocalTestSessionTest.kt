package com.example.androidllm.ui

import com.example.androidllm.api.ChatCompletionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTestSessionTest {
    @Test
    fun buildRequestUsesPromptAndGenerationSettings() {
        val session = LocalTestSession()

        val request = session.buildRequest(
            prompt = "Explain why on-device testing matters.",
            settings = ConsoleGenerationSettings(
                modelId = "qwen3-0.6b-dynamic-int8",
                temperature = 0.2,
                topP = 0.8,
                topK = 24,
                maxTokens = 96,
            ),
        )

        assertEquals("qwen3-0.6b-dynamic-int8", request.model)
        assertEquals("user", request.messages.single().role)
        assertEquals("Explain why on-device testing matters.", request.messages.single().content)
        assertEquals(0.2, request.temperature ?: -1.0, 0.0)
        assertEquals(0.8, request.topP ?: -1.0, 0.0)
        assertEquals(24, request.topK)
        assertEquals(96, request.maxTokens)
        assertFalse(request.stream)
    }

    @Test(expected = IllegalArgumentException::class)
    fun buildRequestRejectsBlankPrompt() {
        LocalTestSession().buildRequest(
            prompt = "   ",
            settings = ConsoleGenerationSettings(modelId = "qwen3-0.6b-mixed-int4"),
        )
    }

    @Test
    fun recordSuccessStoresNewestRunFirstAndCanClearHistory() {
        val session = LocalTestSession()

        val first = session.recordSuccess(
            prompt = "one",
            result = ChatCompletionResult(model = "qwen3-0.6b-mixed-int4", text = "first"),
            elapsedMs = 15L,
            createdAtMillis = 100L,
        )
        val second = session.recordSuccess(
            prompt = "two",
            result = ChatCompletionResult(model = "qwen3-0.6b-mixed-int4", text = "second"),
            elapsedMs = 20L,
            createdAtMillis = 200L,
        )

        assertEquals("first", first.response)
        assertEquals("second", session.history().first().response)
        assertEquals("first", session.history()[1].response)
        assertEquals(2, session.history().size)

        session.clearHistory()

        assertTrue(session.history().isEmpty())
        assertEquals("two", second.prompt)
    }

    @Test
    fun recordErrorStoresErrorMessageInsteadOfResponse() {
        val run = LocalTestSession().recordError(
            prompt = "ping",
            modelId = "qwen3-0.6b-mixed-int4",
            message = "model file missing",
            elapsedMs = 5L,
            createdAtMillis = 10L,
        )

        assertEquals("", run.response)
        assertEquals("model file missing", run.error)
        assertFalse(run.succeeded)
    }
}
