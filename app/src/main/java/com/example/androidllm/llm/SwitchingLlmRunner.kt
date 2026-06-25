package com.example.androidllm.llm

import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult
import com.example.androidllm.model.ModelCatalog
import com.example.androidllm.model.ModelOption
import java.io.File

class SwitchingLlmRunner(
    private val catalog: ModelCatalog,
    private val defaultModel: ModelOption,
    private val modelDir: File,
    private val createRunner: (ModelOption, File) -> LlmRunner,
) : LlmRunner, AutoCloseable {
    private val lock = Any()
    private var active: ActiveRunner? = null

    override fun status(): RunnerStatus = synchronized(lock) {
        val current = active
        if (current != null) {
            return@synchronized current.runner.status().copy(modelId = current.model.id)
        }

        val file = fileFor(defaultModel)
        RunnerStatus(
            ready = false,
            modelPath = file.absolutePath,
            message = if (file.exists()) {
                "model present; runner will initialize on first request"
            } else {
                "model file missing"
            },
            modelId = defaultModel.id,
        )
    }

    override fun modelStatuses(): List<ModelRuntimeStatus> = synchronized(lock) {
        val current = active
        val currentStatus = current?.runner?.status()
        catalog.options.map { model ->
            val file = fileFor(model)
            val isActive = current?.model?.id == model.id
            ModelRuntimeStatus(
                modelId = model.id,
                installed = file.exists(),
                active = isActive,
                ready = isActive && currentStatus?.ready == true,
                modelPath = file.absolutePath,
                message = when {
                    isActive -> currentStatus?.message ?: "active model"
                    file.exists() -> "model present"
                    else -> "model file missing"
                },
            )
        }
    }

    override fun generate(request: ChatCompletionRequest): ChatCompletionResult {
        val target = catalog.requireById(request.model.ifBlank { defaultModel.id })
        val runner = synchronized(lock) {
            val file = fileFor(target)
            require(file.exists()) { "model file missing: ${file.absolutePath}" }

            val current = active
            if (current != null && current.model.id == target.id) {
                current.runner
            } else {
                current?.close()
                createRunner(target, file).also { active = ActiveRunner(target, it) }
            }
        }
        return runner.generate(request)
    }

    override fun close() {
        synchronized(lock) {
            active?.close()
            active = null
        }
    }

    private fun fileFor(model: ModelOption): File = File(modelDir, model.fileName)

    private data class ActiveRunner(
        val model: ModelOption,
        val runner: LlmRunner,
    ) {
        fun close() {
            (runner as? AutoCloseable)?.close()
        }
    }
}
