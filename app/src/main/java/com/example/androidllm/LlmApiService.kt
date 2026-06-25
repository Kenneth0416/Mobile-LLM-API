package com.example.androidllm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.androidllm.api.ChatHttpHandler

class LlmApiService : Service() {
    private var server: HttpApiServer? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        startApiServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startApiServer()
        return START_STICKY
    }

    override fun onDestroy() {
        server?.close()
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startApiServer() {
        if (server != null) return

        val controller = (application as AndroidLlmApp).controller
        val httpServer = HttpApiServer(
            port = ApiConfig.Port,
            handler = ChatHttpHandler(
                runner = controller.runner,
                modelCatalog = controller.catalog,
                deviceProfile = controller.deviceProfile,
            ),
        )

        httpServer.start()
        server = httpServer
        Log.i(Tag, "Android LLM API listening on port ${httpServer.actualPort}, modelDir=${controller.modelDir.absolutePath}")
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ChannelId,
                "Android LLM API",
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, ChannelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle("Android LLM API")
            .setContentText("Listening on port ${ApiConfig.Port}")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .build()

        startForeground(NotificationId, notification)
    }

    companion object {
        private const val Tag = "AndroidLlmApi"
        private const val ChannelId = "android_llm_api"
        private const val NotificationId = 1001
    }
}
