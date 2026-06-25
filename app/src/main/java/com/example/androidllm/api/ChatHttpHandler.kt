package com.example.androidllm.api

import com.example.androidllm.ChatHttpHandlerContract
import com.example.androidllm.llm.LlmRunner
import com.example.androidllm.model.DeviceProfile
import com.example.androidllm.model.ModelCatalog
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
        val activeModel = modelCatalog.recommend(deviceProfile)
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
            add("active_model", modelJson(activeModel))
        }
        return HttpResponse(200, gson.toJson(body))
    }

    private fun models(): HttpResponse {
        val activeModel = modelCatalog.recommend(deviceProfile)
        val data = JsonArray().apply {
            modelCatalog.options.forEach { add(modelJson(it)) }
        }
        val body = JsonObject().apply {
            addProperty("object", "list")
            addProperty("recommended_model", activeModel.id)
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

    private fun modelJson(model: com.example.androidllm.model.ModelOption): JsonObject =
        JsonObject().apply {
            addProperty("id", model.id)
            addProperty("display_name", model.displayName)
            addProperty("repo", model.repo)
            addProperty("file_name", model.fileName)
            addProperty("size_bytes", model.sizeBytes)
            addProperty("license", model.license)
            addProperty("download_url", model.downloadUrl)
            addProperty("reason", model.reason)
        }
}
