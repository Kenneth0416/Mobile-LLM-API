package com.example.androidllm

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.example.androidllm.api.ChatCompletionRequest
import com.example.androidllm.api.ChatCompletionResult
import com.example.androidllm.benchmark.NativeBenchmarkMetrics
import com.example.androidllm.llm.LiteRtLlmRunner
import com.example.androidllm.llm.RunnerStatus
import com.example.androidllm.llm.SwitchingLlmRunner
import com.example.androidllm.model.DeviceProfile
import com.example.androidllm.model.ModelCatalog
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.benchmark as liteRtBenchmark
import java.io.File

class LocalLlmController(
    context: Context,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val cpuThreads = 4

    val catalog: ModelCatalog = ModelCatalog.defaultCatalog()
    val deviceProfile: DeviceProfile = readDeviceProfile(appContext)
    val modelDir: File = (
        appContext.getExternalFilesDir("models") ?: File(appContext.filesDir, "models")
        ).apply { mkdirs() }
    val runner: SwitchingLlmRunner = SwitchingLlmRunner(
        catalog = catalog,
        defaultModel = catalog.recommend(deviceProfile),
        modelDir = modelDir,
        createRunner = { model, file ->
            LiteRtLlmRunner(
                context = appContext,
                modelOption = model,
                modelFile = file,
                cpuThreads = cpuThreads,
            )
        },
    )

    fun status(): RunnerStatus = runner.status()

    fun generate(request: ChatCompletionRequest): ChatCompletionResult =
        runner.generate(request)

    @OptIn(ExperimentalApi::class)
    fun runNativeBenchmark(
        modelId: String,
        prefillTokens: Int,
        generationTokens: Int,
    ): NativeBenchmarkMetrics {
        val model = catalog.requireById(modelId)
        val modelFile = File(modelDir, model.fileName)
        require(modelFile.exists()) { "model file missing: ${modelFile.absolutePath}" }
        val benchmarkCacheDir = File(appContext.cacheDir, "litertlm-benchmark").apply { mkdirs() }
        val info = liteRtBenchmark(
            modelFile.absolutePath,
            Backend.CPU(numOfThreads = cpuThreads),
            prefillTokens,
            generationTokens,
            benchmarkCacheDir.absolutePath,
        )
        return NativeBenchmarkMetrics(
            initTimeMs = info.initTimeInSecond * 1_000.0,
            timeToFirstTokenMs = info.timeToFirstTokenInSecond * 1_000.0,
            prefillTokens = info.lastPrefillTokenCount,
            decodeTokens = info.lastDecodeTokenCount,
            prefillTokensPerSecond = info.lastPrefillTokensPerSecond,
            decodeTokensPerSecond = info.lastDecodeTokensPerSecond,
        )
    }

    override fun close() {
        runner.close()
    }

    private fun readDeviceProfile(context: Context): DeviceProfile {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
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
}
