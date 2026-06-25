package com.example.androidllm

import com.example.androidllm.api.HttpRequest
import com.example.androidllm.api.HttpResponse
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

interface ChatHttpHandlerContract {
    fun handle(request: HttpRequest): HttpResponse
}

class HttpApiServer(
    private val port: Int,
    private val handler: ChatHttpHandlerContract,
    bindAddress: InetAddress = InetAddress.getByName("0.0.0.0"),
) : AutoCloseable {
    private val socketAddress = InetSocketAddress(bindAddress, port)

    @Volatile
    private var running = false

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    val actualPort: Int
        get() = serverSocket?.localPort ?: port

    fun start() {
        if (running) return
        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(socketAddress)
        }
        serverSocket = socket
        running = true
        acceptThread = thread(name = "android-llm-http", isDaemon = true) {
            acceptLoop(socket)
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            try {
                socket.accept().use { client -> handleClient(client) }
            } catch (_: Exception) {
                if (running) {
                    continue
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        val response = try {
            val request = readRequest(client)
            handler.handle(request)
        } catch (error: Exception) {
            HttpResponse(
                statusCode = 400,
                body = """{"error":{"message":"${escapeJson(error.message ?: "bad request")}"}}""",
            )
        }
        writeResponse(client, response)
    }

    private fun readRequest(client: Socket): HttpRequest {
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readAsciiLine(input) ?: throw IllegalArgumentException("missing request line")
        val parts = requestLine.split(" ")
        if (parts.size < 2) throw IllegalArgumentException("invalid request line")

        val headers = linkedMapOf<String, String>()
        while (true) {
            val line = readAsciiLine(input) ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }

        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) {
            String(readFixedBytes(input, contentLength), StandardCharsets.UTF_8)
        } else {
            ""
        }

        return HttpRequest(
            method = parts[0],
            path = parts[1],
            headers = headers,
            body = body,
        )
    }

    private fun writeResponse(client: Socket, response: HttpResponse) {
        val bodyBytes = response.body.toByteArray(StandardCharsets.UTF_8)
        val output = client.getOutputStream()
        val statusText = statusText(response.statusCode)
        val headers = response.headers + mapOf(
            "content-length" to bodyBytes.size.toString(),
            "connection" to "close",
        )
        val head = buildString {
            append("HTTP/1.1 ")
            append(response.statusCode)
            append(' ')
            append(statusText)
            append("\r\n")
            headers.forEach { (name, value) ->
                append(name)
                append(": ")
                append(value)
                append("\r\n")
            }
            append("\r\n")
        }
        output.write(head.toByteArray(StandardCharsets.US_ASCII))
        output.write(bodyBytes)
        output.flush()
    }

    override fun close() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        acceptThread?.join(500)
        acceptThread = null
        serverSocket = null
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>(64)
        while (true) {
            val next = input.read()
            if (next == -1) {
                return if (bytes.isEmpty()) null else String(bytes.toByteArray(), StandardCharsets.US_ASCII)
            }
            if (next == '\n'.code) {
                if (bytes.lastOrNull() == '\r'.code.toByte()) {
                    bytes.removeAt(bytes.lastIndex)
                }
                return String(bytes.toByteArray(), StandardCharsets.US_ASCII)
            }
            bytes.add(next.toByte())
        }
    }

    private fun readFixedBytes(input: BufferedInputStream, length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read == -1) break
            offset += read
        }
        return if (offset == length) buffer else buffer.copyOf(offset)
    }

    private fun statusText(statusCode: Int): String = when (statusCode) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        404 -> "Not Found"
        500 -> "Internal Server Error"
        else -> "OK"
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}

