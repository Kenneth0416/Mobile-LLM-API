package com.example.androidllm.api

import com.example.androidllm.llm.FakeLlmRunner
import com.example.androidllm.model.DeviceProfile
import com.example.androidllm.model.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHttpHandlerTest {
    @Test
    fun healthReturnsModelAndDeviceStatus() {
        val handler = ChatHttpHandler(
            runner = FakeLlmRunner("ready from fake"),
            modelCatalog = ModelCatalog.defaultCatalog(),
            deviceProfile = DeviceProfile(
                manufacturer = "Sony",
                model = "XQ-BE72",
                socModel = "SM8350",
                totalRamMb = 11176,
                supportedAbis = listOf("arm64-v8a"),
            )
        )

        val response = handler.handle(HttpRequest("GET", "/health", emptyMap(), ""))

        assertEquals(200, response.statusCode)
        assertTrue(response.body.contains("\"status\":\"ok\""))
        assertTrue(response.body.contains("\"active_model\""))
        assertTrue(response.body.contains("SM8350"))
    }

    @Test
    fun chatCompletionsCallsRunnerAndReturnsOpenAiCompatibleJson() {
        val handler = ChatHttpHandler(
            runner = FakeLlmRunner("answer from local model"),
            modelCatalog = ModelCatalog.defaultCatalog(),
            deviceProfile = DeviceProfile.unknown(),
        )

        val response = handler.handle(
            HttpRequest(
                method = "POST",
                path = "/v1/chat/completions",
                headers = mapOf("content-type" to "application/json"),
                body = """{"messages":[{"role":"user","content":"ping"}]}""",
            )
        )

        assertEquals(200, response.statusCode)
        assertTrue(response.body.contains("\"object\":\"chat.completion\""))
        assertTrue(response.body.contains("answer from local model"))
    }

    @Test
    fun chatCompletionsReportsBadJsonAsClientError() {
        val handler = ChatHttpHandler(
            runner = FakeLlmRunner("unused"),
            modelCatalog = ModelCatalog.defaultCatalog(),
            deviceProfile = DeviceProfile.unknown(),
        )

        val response = handler.handle(
            HttpRequest("POST", "/v1/chat/completions", emptyMap(), "{not json")
        )

        assertEquals(400, response.statusCode)
        assertTrue(response.body.contains("\"error\""))
    }
}

