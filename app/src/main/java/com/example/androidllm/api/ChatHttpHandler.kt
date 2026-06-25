package com.example.androidllm.api

import com.example.androidllm.ChatHttpHandlerContract
import com.example.androidllm.llm.LlmRunner
import com.example.androidllm.llm.ModelRuntimeStatus
import com.example.androidllm.model.DeviceProfile
import com.example.androidllm.model.ModelCatalog
import com.example.androidllm.model.ModelOption
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

data class HttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = JsonHeaders,
) {
    companion object {
        val JsonHeaders = mapOf(
            "content-type" to "application/json; charset=utf-8",
            "access-control-allow-origin" to "*",
            "access-control-allow-methods" to "GET, POST, OPTIONS",
            "access-control-allow-headers" to "content-type, authorization",
        )
    }
}

class ChatHttpHandler(
    private val runner: LlmRunner,
    private val modelCatalog: ModelCatalog,
    private val deviceProfile: DeviceProfile,
) : ChatHttpHandlerContract {
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    override fun handle(request: HttpRequest): HttpResponse {
        if (request.method.equals("OPTIONS", ignoreCase = true)) {
            return HttpResponse(204, "")
        }

        return try {
            when (request.method.uppercase() to request.path.substringBefore("?")) {
                "GET" to "/health" -> health()
                "GET" to "/v1/models" -> models()
                "POST" to "/v1/chat/completions" -> chatCompletions(request.body)
                else -> HttpResponse(404, ChatApi.errorJson("route not found"))
            }
        } catch (error: IllegalArgumentException) {
            HttpResponse(400, ChatApi.errorJson(error.message ?: "bad request"))
        } catch (error: Exception) {
            HttpResponse(500, ChatApi.errorJson(error.message ?: "internal error", "server_error"))
        }
    }

    private fun health(): HttpResponse {
        val status = runner.status()
        val activeModel = status.modelId?.let { modelCatalog.findById(it) } ?: modelCatalog.recommend(deviceProfile)
        val runtimeById = runner.modelStatuses().associateBy { it.modelId }
        val body = JsonObject().apply {
            addProperty("status", "ok")
            addProperty("service", "android-llm-api")
            add("runner", JsonObject().apply {
                addProperty("ready", status.ready)
                addProperty("model_path", status.modelPath)
                addProperty("message", status.message)
            })
            add("device", JsonObject().apply {
                addProperty("manufacturer", deviceProfile.manufacturer)
                addProperty("model", deviceProfile.model)
                addProperty("soc_model", deviceProfile.socModel)
                addProperty("total_ram_mb", deviceProfile.totalRamMb)
                add("supported_abis", JsonArray().apply {
                    deviceProfile.supportedAbis.forEach { add(it) }
                })
            })
            add("active_model", modelJson(activeModel, runtimeById[activeModel.id]))
        }
        return HttpResponse(200, gson.toJson(body))
    }

    private fun models(): HttpResponse {
        val activeModel = modelCatalog.recommend(deviceProfile)
        val runtimeById = runner.modelStatuses().associateBy { it.modelId }
        val data = JsonArray().apply {
            modelCatalog.options.forEach { add(modelJson(it, runtimeById[it.id])) }
        }
        val body = JsonObject().apply {
            addProperty("object", "list")
            addProperty("recommended_model", runner.status().modelId ?: activeModel.id)
            add("data", data)
        }
        return HttpResponse(200, gson.toJson(body))
    }

    private fun chatCompletions(body: String): HttpResponse {
        val request = ChatApi.parseChatRequest(body)
        if (request.stream) {
            throw IllegalArgumentException("streaming responses are not implemented in this build")
        }
        val result = runner.generate(request)
        return HttpResponse(200, ChatApi.toOpenAiChatResponse(result))
    }

    private fun modelJson(model: ModelOption, runtime: ModelRuntimeStatus? = null): JsonObject =
        JsonObject().apply {
            addProperty("id", model.id)
            addProperty("display_name", model.displayName)
            addProperty("repo", model.repo)
            addProperty("file_name", model.fileName)
            addProperty("size_bytes", model.sizeBytes)
            addProperty("license", model.license)
            addProperty("backend_type", model.backendType.wireName)
            addProperty("hardware_target", model.hardwareTarget.wireName)
            addProperty("performance_tier", model.performanceTier.wireName)
            addProperty("min_ram_mb", model.minRamMb)
            add("recommended_soc_models", JsonArray().apply {
                model.recommendedSocModels.forEach { add(it) }
            })
            add("avoid_soc_models", JsonArray().apply {
                model.avoidSocModels.forEach { add(it) }
            })
            addProperty("compatible", model in modelCatalog.compatibleOptions(deviceProfile))
            addProperty("context_tokens", model.contextTokens)
            addProperty("recommended_output_tokens", model.recommendedOutputTokens)
            addProperty("max_output_tokens", model.maxOutputTokens)
            addProperty("installed", runtime?.installed ?: false)
            addProperty("active", runtime?.active ?: false)
            addProperty("ready", runtime?.ready ?: false)
            addProperty("model_path", runtime?.modelPath)
            addProperty("runtime_message", runtime?.message ?: "runtime status unavailable")
            addProperty("download_url", model.downloadUrl)
            addProperty("reason", model.reason)
        }
}
