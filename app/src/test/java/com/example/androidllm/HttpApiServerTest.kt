package com.example.androidllm

import com.example.androidllm.api.ChatHttpHandler
import com.example.androidllm.api.HttpRequest
import com.example.androidllm.api.HttpResponse
import com.example.androidllm.llm.FakeLlmRunner
import com.example.androidllm.model.DeviceProfile
import com.example.androidllm.model.ModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HttpApiServerTest {
    @Test
    fun serverAcceptsHttpPostAndReturnsHandlerResponse() {
        val handler = ChatHttpHandler(
            runner = FakeLlmRunner("pong from fake model"),
            modelCatalog = ModelCatalog.defaultCatalog(),
            deviceProfile = DeviceProfile.unknown(),
        )
        val server = HttpApiServer(port = 0, handler = handler)

        server.use {
            it.start()
            val connection = (URL("http://127.0.0.1:${it.actualPort}/v1/chat/completions").openConnection() as HttpURLConnection)
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("content-type", "application/json")
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write("""{"messages":[{"role":"user","content":"ping"}]}""")
            }

            assertEquals(200, connection.responseCode)
            val body = connection.inputStream.bufferedReader().readText()
            assertTrue(body.contains("pong from fake model"))
            assertTrue(body.contains("\"chat.completion\""))
        }
    }

    @Test
    fun serverReturnsNotFoundForUnknownRoute() {
        val server = HttpApiServer(
            port = 0,
            handler = object : ChatHttpHandlerContract {
                override fun handle(request: HttpRequest): HttpResponse =
                    HttpResponse(404, """{"error":{"message":"route not found"}}""")
            },
        )

        server.use {
            it.start()
            val connection = (URL("http://127.0.0.1:${it.actualPort}/missing").openConnection() as HttpURLConnection)

            assertEquals(404, connection.responseCode)
            val body = connection.errorStream.bufferedReader().readText()
            assertTrue(body.contains("route not found"))
        }
    }
}

