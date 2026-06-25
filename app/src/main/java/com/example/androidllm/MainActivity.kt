package com.example.androidllm

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.example.androidllm.benchmark.BenchmarkConfig
import com.example.androidllm.benchmark.BenchmarkMode
import com.example.androidllm.benchmark.BenchmarkModel
import com.example.androidllm.benchmark.BenchmarkResult
import com.example.androidllm.benchmark.BenchmarkRunner
import com.example.androidllm.benchmark.BenchmarkSummary
import com.example.androidllm.benchmark.BenchmarkType
import com.example.androidllm.model.ModelOption
import com.example.androidllm.ui.ConsoleGenerationSettings
import com.example.androidllm.ui.ConsolePromptRun
import com.example.androidllm.ui.LocalTestSession
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var controller: LocalLlmController
    private lateinit var contentHost: FrameLayout
    private lateinit var navButtons: Map<Page, Button>
    private val session = LocalTestSession()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var latestOutput: String = ""
    private var currentPage: Page = Page.Status
    @Volatile
    private var benchmarkCancelRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, LlmApiService::class.java))
        controller = (application as AndroidLlmApp).controller
        setContentView(buildRoot())
        showPage(Page.Status)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildRoot(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Colors.Background)
        }

        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(20), dp(18), dp(20), dp(12))
                setBackgroundColor(Color.WHITE)
                addView(text("Android LLM", 22f, Colors.Text, Typeface.BOLD))
                addView(text("Phone local model console", 13f, Colors.Muted, Typeface.NORMAL))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        contentHost = FrameLayout(this)
        root.addView(
            contentHost,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            setBackgroundColor(Color.WHITE)
        }
        navButtons = Page.entries.associateWith { page ->
            Button(this).apply {
                text = page.label
                textSize = 12f
                isAllCaps = false
                minHeight = 0
                minWidth = 0
                setPadding(dp(4), dp(8), dp(4), dp(8))
                setOnClickListener { showPage(page) }
            }.also {
                nav.addView(
                    it,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(3), 0, dp(3), 0)
                    },
                )
            }
        }
        root.addView(nav)
        return root
    }

    private fun showPage(page: Page) {
        currentPage = page
        navButtons.forEach { (item, button) ->
            val selected = item == page
            button.setTextColor(if (selected) Color.WHITE else Colors.Text)
            button.background = rounded(
                fillColor = if (selected) Colors.Primary else Colors.Panel,
                strokeColor = if (selected) Colors.Primary else Colors.Border,
                radiusDp = 12,
            )
        }

        contentHost.removeAllViews()
        when (page) {
            Page.Status -> renderStatusPage()
            Page.Models -> renderModelsPage()
            Page.Test -> renderTestPage()
            Page.Bench -> renderBenchmarkPage()
            Page.Api -> renderApiPage()
        }
    }

    private fun renderStatusPage() {
        val page = pageBody()
        val status = controller.status()
        val profile = controller.deviceProfile
        val activeModel = status.modelId?.let { controller.catalog.findById(it) }
            ?: controller.catalog.recommend(profile)

        page.addView(pageTitle("Status", "Service and local runtime health"))
        page.addView(
            card(
                "Runtime",
                listOf(
                    "HTTP service" to "Port ${ApiConfig.Port}",
                    "Runner" to if (status.ready) "Ready" else "Not ready",
                    "Message" to status.message,
                    "Active model" to activeModel.displayName,
                    "Model path" to (status.modelPath ?: "unknown"),
                ),
            )
        )
        page.addView(
            card(
                "Device",
                listOf(
                    "Phone" to "${profile.manufacturer} ${profile.model}",
                    "SoC" to profile.socModel,
                    "RAM" to "${profile.totalRamMb} MB",
                    "ABI" to profile.supportedAbis.joinToString().ifBlank { "unknown" },
                ),
            )
        )
        page.addView(primaryButton("Refresh") { showPage(Page.Status) })
    }

    private fun renderModelsPage() {
        val page = pageBody()
        val recommended = controller.catalog.recommend(controller.deviceProfile)
        val compatibleIds = controller.catalog.compatibleOptions(controller.deviceProfile).map { it.id }.toSet()
        val runtimeById = controller.runner.modelStatuses().associateBy { it.modelId }

        page.addView(pageTitle("Models", "Installed files and runtime selection"))
        controller.catalog.options.forEach { model ->
            val runtime = runtimeById[model.id]
            val lines = listOf(
                "State" to when {
                    runtime?.active == true -> "Active"
                    runtime?.installed == true -> "Installed"
                    else -> "Missing file"
                },
                "Recommended" to if (model.id == recommended.id) "Yes" else "No",
                "Compatible" to if (model.id in compatibleIds) "Yes" else "No",
                "Tier" to model.performanceTier.wireName,
                "Backend" to model.backendType.wireName,
                "Hardware" to model.hardwareTarget.wireName,
                "Min RAM" to "${model.minRamMb} MB",
                "SoC picks" to model.recommendedSocModels.joinToString().ifBlank { "none" },
                "File" to model.fileName,
                "Path" to (runtime?.modelPath ?: "unknown"),
                "Size" to formatBytes(model.sizeBytes),
                "Context" to "${model.contextTokens} tokens",
                "Output" to "Recommended ${model.recommendedOutputTokens}, max ${model.maxOutputTokens}",
                "License" to model.license,
            )
            page.addView(card(model.displayName, lines))
        }
    }

    private fun renderTestPage() {
        val page = pageBody()
        val models = controller.catalog.options
        val compatibleIds = controller.catalog.compatibleOptions(controller.deviceProfile).map { it.id }.toSet()
        val runtimeById = controller.runner.modelStatuses().associateBy { it.modelId }

        page.addView(pageTitle("Test Bench", "Run prompts directly on this phone"))

        val modelSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                models.map { model ->
                    val installed = runtimeById[model.id]?.installed == true
                    "${model.displayName} (${modelStateLabel(model, installed, model.id in compatibleIds)})"
                },
            )
            val activeIndex = models.indexOfFirst { runtimeById[it.id]?.active == true }
                .takeIf { it >= 0 }
                ?: models.indexOfFirst { it.id == controller.catalog.recommend(controller.deviceProfile).id }
            if (activeIndex >= 0) setSelection(activeIndex)
        }
        val promptInput = EditText(this).apply {
            hint = "Type a prompt for the phone model"
            minLines = 5
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Colors.Text)
            setHintTextColor(Colors.Muted)
            background = rounded(Colors.Panel, Colors.Border, 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val temperatureInput = numberInput("0.7")
        val topPInput = numberInput("0.95")
        val maxTokensInput = numberInput(controller.catalog.recommend(controller.deviceProfile).recommendedOutputTokens.toString())
        val output = TextView(this).apply {
            text = "Run a prompt to see the local model response here."
            textSize = 14f
            setTextColor(Colors.Text)
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Colors.Panel, Colors.Border, 10)
        }
        val history = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        lateinit var runButton: Button

        page.addView(label("Model"))
        page.addView(modelSpinner, matchWrap())
        page.addView(space(10))
        page.addView(label("Prompt"))
        page.addView(promptInput, matchWrap())
        page.addView(space(10))
        page.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(inputBlock("Temp", temperatureInput), weight())
                addView(inputBlock("Top-p", topPInput), weight())
                addView(inputBlock("Max", maxTokensInput), weight())
            }
        )
        page.addView(space(12))
        page.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                runButton = primaryButton("Run") {
                    runPrompt(
                        selectedModel = models[modelSpinner.selectedItemPosition],
                        prompt = promptInput.text.toString(),
                        temperatureText = temperatureInput.text.toString(),
                        topPText = topPInput.text.toString(),
                        maxTokensText = maxTokensInput.text.toString(),
                        output = output,
                        history = history,
                        runButtonProvider = { runButton },
                    )
                }
                addView(runButton, weight())
                addView(secondaryButton("Copy") { copyLatestOutput() }, weight())
                addView(secondaryButton("Clear") {
                    latestOutput = ""
                    output.text = "Cleared. Run a prompt to test the phone model again."
                    session.clearHistory()
                    renderHistory(history)
                }, weight())
            }
        )
        page.addView(space(12))
        page.addView(label("Output"))
        page.addView(output, matchWrap())
        page.addView(space(14))
        page.addView(label("History"))
        page.addView(history, matchWrap())
        renderHistory(history)
    }

    private fun runPrompt(
        selectedModel: ModelOption,
        prompt: String,
        temperatureText: String,
        topPText: String,
        maxTokensText: String,
        output: TextView,
        history: LinearLayout,
        runButtonProvider: () -> Button,
    ) {
        val settings = ConsoleGenerationSettings(
            modelId = selectedModel.id,
            temperature = temperatureText.toDoubleOrNull(),
            topP = topPText.toDoubleOrNull(),
            maxTokens = maxTokensText.toIntOrNull(),
        )
        val request = try {
            session.buildRequest(prompt, settings)
        } catch (error: IllegalArgumentException) {
            Toast.makeText(this, error.message ?: "Invalid prompt", Toast.LENGTH_SHORT).show()
            return
        }

        val runButton = runButtonProvider()
        runButton.isEnabled = false
        output.text = "Running ${selectedModel.displayName} on this phone..."
        val startedAt = System.currentTimeMillis()
        executor.execute {
            try {
                val result = controller.generate(request)
                val elapsed = System.currentTimeMillis() - startedAt
                mainHandler.post {
                    val run = session.recordSuccess(prompt, result, elapsed)
                    latestOutput = result.text
                    output.text = formatRun(run)
                    runButton.isEnabled = true
                    renderHistory(history)
                }
            } catch (error: Exception) {
                val elapsed = System.currentTimeMillis() - startedAt
                mainHandler.post {
                    val run = session.recordError(
                        prompt = prompt,
                        modelId = selectedModel.id,
                        message = error.message ?: "local generation failed",
                        elapsedMs = elapsed,
                    )
                    latestOutput = run.error.orEmpty()
                    output.text = formatRun(run)
                    runButton.isEnabled = true
                    renderHistory(history)
                }
            }
        }
    }

    private fun renderBenchmarkPage() {
        val page = pageBody()
        val models = controller.catalog.options
        val compatibleIds = controller.catalog.compatibleOptions(controller.deviceProfile).map { it.id }.toSet()
        val runtimeById = controller.runner.modelStatuses().associateBy { it.modelId }

        page.addView(pageTitle("Performance Benchmark", "Measure phone-local inference speed and stress load"))

        val modelSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                models.map { model ->
                    val installed = runtimeById[model.id]?.installed == true
                    "${model.displayName} (${formatBytes(model.sizeBytes)}, ${modelStateLabel(model, installed, model.id in compatibleIds)})"
                },
            )
            val recommended = controller.catalog.recommend(controller.deviceProfile)
            val defaultIndex = models.indexOfFirst { it.id == recommended.id }.coerceAtLeast(0)
            setSelection(defaultIndex)
        }
        val durationSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("10 seconds", "30 seconds", "1 minute", "3 minutes", "5 minutes"),
            )
            setSelection(1)
        }
        val modeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("Decode Benchmark", "Pressure Stress"),
            )
            setSelection(0)
        }
        val generationSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("32 tokens", "64 tokens", "128 tokens", "256 tokens"),
            )
            setSelection(2)
        }
        val prefillChecks = listOf(128, 512, 1024, 2048, 4096).map { tokens ->
            tokens to checkBox("pp$tokens", checked = tokens == 128 || tokens == 512)
        }
        val workerChecks = listOf(1, 2, 4, 8).map { workerCount ->
            workerCount to checkBox("$workerCount worker${if (workerCount == 1) "" else "s"}", checked = workerCount == 1 || workerCount == 2)
        }
        val progress = TextView(this).apply {
            text = "Ready."
            textSize = 13f
            setTextColor(Colors.Muted)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Colors.Panel, Colors.Border, 10)
        }
        val results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        lateinit var runButton: Button
        lateinit var stopButton: Button

        page.addView(card("Configuration", emptyList()))
        page.addView(label("Model"))
        page.addView(modelSpinner, matchWrap())
        page.addView(space(10))
        page.addView(label("Mode"))
        page.addView(modeSpinner, matchWrap())
        page.addView(space(10))
        page.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(spinnerBlock("Duration", durationSpinner), weight())
                addView(spinnerBlock("Generation", generationSpinner), weight())
            }
        )
        page.addView(space(12))
        page.addView(label("Single Request Tests"))
        page.addView(flowChecks(prefillChecks.map { it.second }))
        page.addView(text("Decode Benchmark uses native pp/tg metrics from LiteRT-LM.", 12f, Colors.Muted, Typeface.NORMAL))
        page.addView(space(12))
        page.addView(label("Pressure Workers"))
        page.addView(flowChecks(workerChecks.map { it.second }))
        page.addView(text("Pressure Stress runs concurrent short-answer workers until the selected duration window closes.", 12f, Colors.Muted, Typeface.NORMAL))
        page.addView(space(12))
        page.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                runButton = primaryButton("Run Benchmark") {
                    val selectedModel = models[modelSpinner.selectedItemPosition]
                    val mode = if (modeSpinner.selectedItem.toString().startsWith("Decode")) {
                        BenchmarkMode.DecodeBenchmark
                    } else {
                        BenchmarkMode.PressureStress
                    }
                    val config = BenchmarkConfig(
                        model = benchmarkModel(selectedModel, runtimeById[selectedModel.id]?.installed == true),
                        mode = mode,
                        durationSeconds = durationSeconds(durationSpinner.selectedItem.toString()),
                        generationTokens = leadingInt(generationSpinner.selectedItem.toString()),
                        prefillTokens = if (mode == BenchmarkMode.DecodeBenchmark) {
                            prefillChecks.filter { it.second.isChecked }.map { it.first }
                        } else {
                            emptyList()
                        },
                        stressWorkerCounts = if (mode == BenchmarkMode.PressureStress) {
                            workerChecks.filter { it.second.isChecked }.map { it.first }
                        } else {
                            emptyList()
                        },
                        warmupEnabled = mode == BenchmarkMode.PressureStress,
                    )
                    runBenchmark(config, progress, results) { runButton to stopButton }
                }
                stopButton = secondaryButton("Stop") {
                    benchmarkCancelRequested = true
                    progress.text = "Stopping after the current request..."
                }.apply { isEnabled = false }
                addView(runButton, weight())
                addView(stopButton, weight())
            }
        )
        page.addView(space(12))
        page.addView(label("Progress"))
        page.addView(progress, matchWrap())
        page.addView(space(12))
        page.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(secondaryButton("Copy Results") { copyLatestOutput() }, weight())
                addView(secondaryButton("Clear") {
                    latestOutput = ""
                    progress.text = "Ready."
                    results.removeAllViews()
                }, weight())
            }
        )
        page.addView(space(12))
        page.addView(label("Results"))
        page.addView(results, matchWrap())
    }

    private fun runBenchmark(
        config: BenchmarkConfig,
        progress: TextView,
        results: LinearLayout,
        buttons: () -> Pair<Button, Button>,
    ) {
        val validationErrors = config.validationErrors()
        if (validationErrors.isNotEmpty()) {
            Toast.makeText(this, validationErrors.joinToString("\n"), Toast.LENGTH_LONG).show()
            return
        }

        val (runButton, stopButton) = buttons()
        benchmarkCancelRequested = false
        runButton.isEnabled = false
        stopButton.isEnabled = true
        results.removeAllViews()
        progress.text = "Starting benchmark for ${config.model.displayName}..."

        executor.execute {
            val runner = BenchmarkRunner(
                generate = { request -> controller.generate(request) },
                nativeBenchmark = { nativeConfig, prefillTokens, generationTokens ->
                    controller.runNativeBenchmark(nativeConfig.model.id, prefillTokens, generationTokens)
                },
                onProgress = { item ->
                    mainHandler.post {
                        progress.text = "Running ${item.label} (${item.completedResults} result groups complete)"
                    }
                },
            )
            val summary = try {
                runner.run(config) { benchmarkCancelRequested }
            } catch (error: Exception) {
                mainHandler.post {
                    progress.text = "Benchmark failed: ${error.message ?: "unknown error"}"
                    runButton.isEnabled = true
                    stopButton.isEnabled = false
                }
                return@execute
            }

            mainHandler.post {
                renderBenchmarkResults(results, summary)
                latestOutput = benchmarkSummaryText(summary)
                progress.text = if (summary.cancelled) {
                    "Stopped. ${summary.results.size} result groups collected."
                } else {
                    "Finished. ${summary.results.size} result groups collected."
                }
                runButton.isEnabled = true
                stopButton.isEnabled = false
            }
        }
    }

    private fun renderBenchmarkResults(container: LinearLayout, summary: BenchmarkSummary) {
        container.removeAllViews()
        if (summary.results.isEmpty()) {
            container.addView(text("No benchmark results yet.", 13f, Colors.Muted, Typeface.NORMAL))
            return
        }
        summary.results.forEach { result ->
            container.addView(
                card(
                    result.name,
                    listOfNotNull(
                        "Type" to typeLabel(result.type),
                        "Token source" to result.tokenSource.name.lowercase(Locale.US),
                        "Requests" to "${result.requestsCompleted} ok / ${result.errors} errors",
                        result.configuredDurationSeconds?.let { "Target duration" to "${it}s" },
                        "Wall elapsed" to "${result.wallElapsedMs} ms",
                        "Latency" to "avg ${formatDecimal(result.averageLatencyMs)} ms, p95 ${formatDecimal(result.p95LatencyMs)} ms",
                        "Tokens" to "prompt ${result.promptTokens}, output ${result.completionTokens}",
                        result.timeToFirstTokenMs?.let { "TTFT" to "${formatDecimal(it)} ms" },
                        result.prefillTokensPerSecond?.let { "Prefill TPS" to "${formatDecimal(it)} tok/s" },
                        result.decodeTokensPerSecond?.let { "Decode TPS" to "${formatDecimal(it)} tok/s" },
                        "Throughput" to "${formatDecimal(result.totalTokensPerSecond)} total tok/s, ${formatDecimal(result.outputTokensPerSecond)} out tok/s",
                    ) + listOfNotNull(result.errorMessage?.let { "Note" to it }),
                )
            )
        }
    }

    private fun benchmarkSummaryText(summary: BenchmarkSummary): String =
        buildString {
            appendLine("model,${summary.modelId}")
            appendLine("warmup_requests,${summary.warmupRequests}")
            appendLine("cancelled,${summary.cancelled}")
            appendLine("type,name,workers,pp,tg,configured_duration_s,requests,errors,latency_sum_ms,wall_elapsed_ms,avg_latency_ms,p95_latency_ms,prompt_tokens,completion_tokens,token_source,ttft_ms,prefill_tps,decode_tps,total_tps,output_tps,note")
            summary.results.forEach { result ->
                appendLine(
                    listOf(
                        result.type.name,
                        result.name,
                        result.batchSize,
                        result.prefillTokens,
                        result.targetGenerationTokens,
                        result.configuredDurationSeconds ?: "",
                        result.requestsCompleted,
                        result.errors,
                        result.elapsedMs,
                        result.wallElapsedMs,
                        formatDecimal(result.averageLatencyMs),
                        formatDecimal(result.p95LatencyMs),
                        result.promptTokens,
                        result.completionTokens,
                        result.tokenSource.name,
                        result.timeToFirstTokenMs?.let { formatDecimal(it) } ?: "",
                        result.prefillTokensPerSecond?.let { formatDecimal(it) } ?: "",
                        result.decodeTokensPerSecond?.let { formatDecimal(it) } ?: "",
                        formatDecimal(result.totalTokensPerSecond),
                        formatDecimal(result.outputTokensPerSecond),
                        result.errorMessage.orEmpty(),
                    ).joinToString(",")
                )
            }
        }

    private fun typeLabel(type: BenchmarkType): String =
        when (type) {
            BenchmarkType.NativeDecode -> "Native decode"
            BenchmarkType.PressureStress -> "Pressure stress"
        }

    private fun benchmarkModel(model: ModelOption, installed: Boolean): BenchmarkModel =
        BenchmarkModel(
            id = model.id,
            displayName = model.displayName,
            contextTokens = model.contextTokens,
            installed = installed,
        )

    private fun durationSeconds(label: String): Int =
        when {
            label.startsWith("10") -> 10
            label.startsWith("30") -> 30
            label.startsWith("1 minute") -> 60
            label.startsWith("3") -> 180
            label.startsWith("5") -> 300
            else -> 30
        }

    private fun leadingInt(label: String): Int =
        label.substringBefore(" ").toIntOrNull() ?: 128

    private fun flowChecks(checks: List<CheckBox>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            checks.chunked(2).forEach { rowChecks ->
                addView(
                    LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        rowChecks.forEach { addView(it, weight()) }
                    }
                )
            }
        }

    private fun renderApiPage() {
        val page = pageBody()
        page.addView(pageTitle("API", "Computer access remains available"))
        page.addView(
            card(
                "Endpoint",
                listOf(
                    "Health" to "GET http://127.0.0.1:${ApiConfig.Port}/health",
                    "Models" to "GET http://127.0.0.1:${ApiConfig.Port}/v1/models",
                    "Chat" to "POST http://127.0.0.1:${ApiConfig.Port}/v1/chat/completions",
                ),
            )
        )
        page.addView(codeBlock("adb forward tcp:${ApiConfig.Port} tcp:${ApiConfig.Port}"))
        page.addView(
            codeBlock(
                """
                curl http://127.0.0.1:${ApiConfig.Port}/v1/chat/completions \
                  -H 'content-type: application/json' \
                  -d '{"messages":[{"role":"user","content":"ping"}]}'
                """.trimIndent()
            )
        )
    }

    private fun renderHistory(container: LinearLayout) {
        container.removeAllViews()
        val runs = session.history()
        if (runs.isEmpty()) {
            container.addView(text("No local runs yet.", 13f, Colors.Muted, Typeface.NORMAL))
            return
        }
        runs.forEach { run ->
            container.addView(
                TextView(this).apply {
                    text = formatHistory(run)
                    textSize = 13f
                    setTextColor(Colors.Text)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded(Colors.Panel, Colors.Border, 10)
                    setTextIsSelectable(true)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, 0, 0, dp(8)) },
            )
        }
    }

    private fun copyLatestOutput() {
        if (latestOutput.isBlank()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Android LLM output", latestOutput))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun pageBody(): LinearLayout {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(20))
        }
        val scroll = ScrollView(this).apply { addView(body) }
        contentHost.addView(scroll)
        return body
    }

    private fun pageTitle(title: String, subtitle: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
            addView(text(title, 20f, Colors.Text, Typeface.BOLD))
            addView(text(subtitle, 13f, Colors.Muted, Typeface.NORMAL))
        }

    private fun card(title: String, rows: List<Pair<String, String>>): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, Colors.Border, 12)
            addView(text(title, 16f, Colors.Text, Typeface.BOLD))
            rows.forEach { (label, value) ->
                addView(
                    TextView(this@MainActivity).apply {
                        text = "$label\n$value"
                        textSize = 13f
                        setTextColor(Colors.Text)
                        setPadding(0, dp(8), 0, 0)
                        setTextIsSelectable(true)
                    }
                )
            }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(12)) }
        }

    private fun codeBlock(value: String): TextView =
        TextView(this).apply {
            text = value
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Colors.Text)
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Colors.Panel, Colors.Border, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, 0, 0, dp(12)) }
        }

    private fun inputBlock(title: String, input: EditText): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(8), 0)
            addView(label(title))
            addView(input, matchWrap())
        }

    private fun spinnerBlock(title: String, spinner: Spinner): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(8), 0)
            addView(label(title))
            addView(spinner, matchWrap())
        }

    private fun numberInput(defaultValue: String): EditText =
        EditText(this).apply {
            setText(defaultValue)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
            setTextColor(Colors.Text)
            background = rounded(Colors.Panel, Colors.Border, 10)
            setPadding(dp(10), 0, dp(10), 0)
        }

    private fun primaryButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Colors.Primary, Colors.Primary, 12)
            setOnClickListener { onClick() }
        }

    private fun checkBox(label: String, checked: Boolean): CheckBox =
        CheckBox(this).apply {
            text = label
            isChecked = checked
            textSize = 13f
            setTextColor(Colors.Text)
        }

    private fun secondaryButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Colors.Text)
            background = rounded(Colors.Panel, Colors.Border, 12)
            setOnClickListener { onClick() }
        }

    private fun text(value: String, size: Float, color: Int, style: Int): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.DEFAULT_BOLD.takeIf { style == Typeface.BOLD } ?: Typeface.DEFAULT
        }

    private fun label(value: String): TextView =
        text(value, 12f, Colors.Muted, Typeface.BOLD).apply {
            setPadding(0, 0, 0, dp(4))
        }

    private fun space(heightDp: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
        }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

    private fun weight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(0, 0, dp(6), 0)
        }

    private fun rounded(fillColor: Int, strokeColor: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fillColor)
            setStroke(dp(1), strokeColor)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun formatRun(run: ConsolePromptRun): String =
        if (run.succeeded) {
            "${run.response}\n\nModel: ${run.modelId}\nElapsed: ${run.elapsedMs} ms"
        } else {
            "Error: ${run.error}\n\nModel: ${run.modelId}\nElapsed: ${run.elapsedMs} ms"
        }

    private fun formatHistory(run: ConsolePromptRun): String =
        if (run.succeeded) {
            "Prompt: ${run.prompt}\nAnswer: ${run.response}\n${run.elapsedMs} ms"
        } else {
            "Prompt: ${run.prompt}\nError: ${run.error}\n${run.elapsedMs} ms"
        }

    private fun formatDecimal(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun modelStateLabel(model: ModelOption, installed: Boolean, compatible: Boolean): String {
        val installState = if (installed) "installed" else "missing"
        val compatibility = if (compatible) "compatible" else "not recommended"
        return "$installState, $compatibility, ${model.performanceTier.wireName}"
    }

    private fun formatBytes(bytes: Long): String =
        when {
            bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
            else -> "$bytes B"
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private enum class Page(val label: String) {
        Status("Status"),
        Models("Models"),
        Test("Test"),
        Bench("Bench"),
        Api("API"),
    }

    private object Colors {
        const val Background = 0xFFF4F6F8.toInt()
        const val Panel = 0xFFF8FAFC.toInt()
        const val Border = 0xFFD7DEE8.toInt()
        const val Text = 0xFF172033.toInt()
        const val Muted = 0xFF637083.toInt()
        const val Primary = 0xFF2563EB.toInt()
    }
}
