package com.example.androidllm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, LlmApiService::class.java))

        val text = TextView(this).apply {
            text = """
                Android LLM API

                Service: starting on port ${ApiConfig.Port}
                Mac access: adb forward tcp:${ApiConfig.Port} tcp:${ApiConfig.Port}
                Endpoint: POST http://127.0.0.1:${ApiConfig.Port}/v1/chat/completions
            """.trimIndent()
            textSize = 16f
            setPadding(32, 48, 32, 32)
        }
        setContentView(text)
    }
}
