package com.example.androidllm.llm

import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult
import com.example.androidllm.api.ChatMessage
import com.example.androidllm.model.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SwitchingLlmRunnerTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun generateLazilyCreatesRunnerForRequestedInstalledModelAndSwitchesActiveModel() {
        val catalog = ModelCatalog.defaultCatalog()
        val modelDir = folder.newFolder("models")
        File(modelDir, "qwen3_0_6b_mixed_int4.litertlm").writeText("int4")
        File(modelDir, "Qwen3-0.6B.litertlm").writeText("int8")
        val created = mutableListOf<RecordingRunner>()
        val runner = SwitchingLlmRunner(
            catalog = catalog,
            defaultModel = catalog.requireById("qwen3-0.6b-mixed-int4"),
            modelDir = modelDir,
            createRunner = { model, file ->
                RecordingRunner(model.id, file.absolutePath).also { created.add(it) }
            },
        )

        val first = runner.generate(request("qwen3-0.6b-mixed-int4"))
        val second = runner.generate(request("qwen3-0.6b-dynamic-int8"))

        assertEquals("qwen3-0.6b-mixed-int4", first.model)
        assertEquals("qwen3-0.6b-dynamic-int8", second.model)
        assertEquals("qwen3-0.6b-dynamic-int8", runner.status().modelId)
        assertEquals(2, created.size)
        assertTrue(created.first().closed)
        assertFalse(created.last().closed)
    }

    @Test
    fun modelStatusesShowInstalledAndActiveModels() {
        val catalog = ModelCatalog.defaultCatalog()
        val modelDir = folder.newFolder("models")
        File(modelDir, "qwen3_0_6b_mixed_int4.litertlm").writeText("int4")
        val runner = SwitchingLlmRunner(
            catalog = catalog,
            defaultModel = catalog.requireById("qwen3-0.6b-mixed-int4"),
            modelDir = modelDir,
            createRunner = { model, file -> RecordingRunner(model.id, file.absolutePath) },
        )

        val statuses = runner.modelStatuses()

        assertTrue(statuses.first { it.modelId == "qwen3-0.6b-mixed-int4" }.installed)
        assertFalse(statuses.first { it.modelId == "qwen3-0.6b-dynamic-int8" }.installed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun generateRejectsMissingSelectedModelFile() {
        val catalog = ModelCatalog.defaultCatalog()
        val runner = SwitchingLlmRunner(
            catalog = catalog,
            defaultModel = catalog.requireById("qwen3-0.6b-mixed-int4"),
            modelDir = folder.newFolder("models"),
            createRunner = { model, file -> RecordingRunner(model.id, file.absolutePath) },
        )

        runner.generate(request("qwen3-0.6b-dynamic-int8"))
    }

    private fun request(model: String): ChatCompletionRequest =
        ChatCompletionRequest(
            model = model,
            messages = listOf(ChatMessage("user", "ping")),
            temperature = null,
            topP = null,
            topK = null,
            maxTokens = null,
            stream = false,
        )
}

private class RecordingRunner(
    private val modelId: String,
    private val path: String,
) : LlmRunner, AutoCloseable {
    var closed: Boolean = false

    override fun status(): RunnerStatus = RunnerStatus(
        ready = true,
        modelPath = path,
        message = "recording runner ready",
        modelId = modelId,
    )

    override fun generate(request: ChatCompletionRequest): ChatCompletionResult =
        ChatCompletionResult(model = modelId, text = "ok")

    override fun close() {
        closed = true
    }
}
