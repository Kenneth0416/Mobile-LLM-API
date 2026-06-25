package com.example.androidllm.api

import com.example.androidllm.llm.FakeLlmRunner
import com.example.androidllm.llm.ModelRuntimeStatus
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
        assertTrue(response.body.contains("\"context_tokens\":2048"))
        assertTrue(response.body.contains("\"recommended_output_tokens\":256"))
        assertTrue(response.body.contains("\"max_output_tokens\":2048"))
        assertTrue(response.body.contains("SM8350"))
    }

    @Test
    fun modelsReturnsInstallAndActiveStatusForSelectableModels() {
        val handler = ChatHttpHandler(
            runner = FakeLlmRunner(
                reply = "unused",
                modelStatuses = listOf(
                    ModelRuntimeStatus(
                        modelId = "qwen3-0.6b-mixed-int4",
                        installed = true,
                        active = true,
                        ready = true,
                        modelPath = "/fake/qwen3_0_6b_mixed_int4.litertlm",
                        message = "fake active",
                    ),
                    ModelRuntimeStatus(
                        modelId = "qwen3-0.6b-dynamic-int8",
                        installed = false,
                        active = false,
                        ready = false,
                        modelPath = "/fake/Qwen3-0.6B.litertlm",
                        message = "model file missing",
                    ),
                ),
            ),
            modelCatalog = ModelCatalog.defaultCatalog(),
            deviceProfile = DeviceProfile(
                manufacturer = "Sony",
                model = "XQ-BE72",
                socModel = "SM8350",
                totalRamMb = 11176,
                supportedAbis = listOf("arm64-v8a"),
            ),
        )

        val response = handler.handle(HttpRequest("GET", "/v1/models", emptyMap(), ""))

        assertEquals(200, response.statusCode)
        assertTrue(response.body.contains("\"id\":\"qwen3-0.6b-mixed-int4\""))
        assertTrue(response.body.contains("\"id\":\"qwen3-0.6b-dynamic-int8\""))
        assertTrue(response.body.contains("\"id\":\"gemma4-e2b-it\""))
        assertTrue(response.body.contains("\"id\":\"gemma4-e4b-it\""))
        assertTrue(response.body.contains("\"installed\":true"))
        assertTrue(response.body.contains("\"active\":true"))
        assertTrue(response.body.contains("\"installed\":false"))
        assertTrue(response.body.contains("\"backend_type\":\"litertlm_text\""))
        assertTrue(response.body.contains("\"hardware_target\":\"generic_cpu\""))
        assertTrue(response.body.contains("\"performance_tier\":\"quality\""))
        assertTrue(response.body.contains("\"min_ram_mb\":10240"))
        assertTrue(response.body.contains("\"compatible\":false"))
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
                body = """{"model":"qwen3-0.6b-dynamic-int8","messages":[{"role":"user","content":"ping"}]}""",
            )
        )

        assertEquals(200, response.statusCode)
        assertTrue(response.body.contains("\"object\":\"chat.completion\""))
        assertTrue(response.body.contains("\"model\":\"qwen3-0.6b-dynamic-int8\""))
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
