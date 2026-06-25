package com.example.androidllm

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.androidllm.api.ChatHttpHandler
import com.example.androidllm.llm.LiteRtLlmRunner
import com.example.androidllm.model.DeviceProfile
import com.example.androidllm.model.ModelCatalog
import java.io.File

class LlmApiService : Service() {
    private var server: HttpApiServer? = null
    private var runner: LiteRtLlmRunner? = null

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
        runner?.close()
        runner = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startApiServer() {
        if (server != null) return

        val catalog = ModelCatalog.defaultCatalog()
        val profile = readDeviceProfile()
        val model = catalog.recommend(profile)
        val modelDir = getExternalFilesDir("models") ?: File(filesDir, "models")
        val modelFile = File(modelDir, model.fileName)
        val liteRtRunner = LiteRtLlmRunner(
            context = this,
            modelOption = model,
            modelFile = modelFile,
            cpuThreads = 4,
        )
        val httpServer = HttpApiServer(
            port = ApiConfig.Port,
            handler = ChatHttpHandler(
                runner = liteRtRunner,
                modelCatalog = catalog,
                deviceProfile = profile,
            ),
        )

        modelDir.mkdirs()
        httpServer.start()
        runner = liteRtRunner
        server = httpServer
        Log.i(Tag, "Android LLM API listening on port ${httpServer.actualPort}, model=${modelFile.absolutePath}")
    }

    private fun readDeviceProfile(): DeviceProfile {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)

        return DeviceProfile(
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
            socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL ?: Build.HARDWARE ?: "unknown"
            } else {
                Build.HARDWARE ?: "unknown"
            },
            totalRamMb = (memoryInfo.totalMem / 1024L / 1024L).toInt(),
            supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
        )
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
