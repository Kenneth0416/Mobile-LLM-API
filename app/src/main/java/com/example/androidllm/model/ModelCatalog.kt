package com.example.androidllm.model

data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val socModel: String,
    val totalRamMb: Int,
    val supportedAbis: List<String>,
) {
    companion object {
        fun unknown(): DeviceProfile = DeviceProfile(
            manufacturer = "unknown",
            model = "unknown",
            socModel = "unknown",
            totalRamMb = 0,
            supportedAbis = emptyList(),
        )
    }
}

enum class ModelBackendType(val wireName: String) {
    LITERTLM_TEXT("litertlm_text"),
    LITERTLM_VISION("litertlm_vision"),
    MLKIT_OCR("mlkit_ocr"),
}

enum class HardwareTarget(val wireName: String) {
    GENERIC_CPU("generic_cpu"),
    QUALCOMM_SM8750("qualcomm_sm8750"),
    QUALCOMM_SM8850("qualcomm_sm8850"),
    QUALCOMM_QCS8275("qualcomm_qcs8275"),
    MEDIATEK_MT6993("mediatek_mt6993"),
    GOOGLE_TENSOR_G5("google_tensor_g5"),
    INTEL_LNL("intel_lnl"),
    INTEL_PTL("intel_ptl"),
}

enum class PerformanceTier(val wireName: String, val rank: Int) {
    FAST("fast", 0),
    BALANCED("balanced", 1),
    QUALITY("quality", 2),
    STRESS("stress", 3),
}

data class ModelOption(
    val id: String,
    val displayName: String,
    val repo: String,
    val fileName: String,
    val sizeBytes: Long,
    val license: String,
    val contextTokens: Int,
    val recommendedOutputTokens: Int,
    val maxOutputTokens: Int,
    val backendType: ModelBackendType,
    val hardwareTarget: HardwareTarget,
    val performanceTier: PerformanceTier,
    val minRamMb: Int,
    val recommendedSocModels: List<String>,
    val avoidSocModels: List<String>,
    val downloadUrl: String,
    val reason: String,
)

class ModelCatalog private constructor(
    val options: List<ModelOption>,
) {
    fun findById(id: String): ModelOption? =
        options.firstOrNull { it.id == id }

    fun requireById(id: String): ModelOption =
        findById(id) ?: throw IllegalArgumentException("unknown model: $id")

    fun compatibleOptions(profile: DeviceProfile): List<ModelOption> =
        options.filter { it.isCompatibleWith(profile) }

    fun recommend(profile: DeviceProfile): ModelOption {
        val compatible = compatibleOptions(profile).ifEmpty {
            listOf(options.first { it.id == "qwen3-0.6b-mixed-int4" })
        }

        val socMatch = compatible
            .filter { it.recommendedForSoc(profile.socModel) }
            .filter { it.performanceTier != PerformanceTier.STRESS }
            .maxWithOrNull(compareBy<ModelOption> { it.performanceTier.rank }.thenBy { it.minRamMb })
        if (socMatch != null) {
            return socMatch.withDeviceReason(profile)
        }

        val preferredTier = when {
            profile.totalRamMb >= 12_288 -> PerformanceTier.QUALITY
            profile.totalRamMb >= 6_144 -> PerformanceTier.BALANCED
            else -> PerformanceTier.FAST
        }
        val ranked = compatible
            .filter { it.performanceTier != PerformanceTier.STRESS }
            .filter { it.performanceTier.rank <= preferredTier.rank }
            .maxWithOrNull(compareBy<ModelOption> { it.performanceTier.rank }.thenBy { it.minRamMb })

        return (ranked ?: compatible.first()).withDeviceReason(profile)
    }

    private fun ModelOption.isCompatibleWith(profile: DeviceProfile): Boolean {
        if (backendType != ModelBackendType.LITERTLM_TEXT) return false
        if (!hardwareTarget.matches(profile.socModel)) return false
        if (profile.totalRamMb > 0 && profile.totalRamMb < minRamMb) return false
        if (avoidSocModels.any { profile.socModel.equals(it, ignoreCase = true) }) return false
        if (profile.supportedAbis.isNotEmpty() && "arm64-v8a" !in profile.supportedAbis) return false
        return true
    }

    private fun HardwareTarget.matches(socModel: String): Boolean =
        when (this) {
            HardwareTarget.GENERIC_CPU -> true
            HardwareTarget.QUALCOMM_SM8750 -> socModel.equals("SM8750", ignoreCase = true)
            HardwareTarget.QUALCOMM_SM8850 -> socModel.equals("SM8850", ignoreCase = true)
            HardwareTarget.QUALCOMM_QCS8275 -> socModel.equals("QCS8275", ignoreCase = true)
            HardwareTarget.MEDIATEK_MT6993 -> socModel.equals("MT6993", ignoreCase = true)
            HardwareTarget.GOOGLE_TENSOR_G5 -> socModel.equals("Tensor G5", ignoreCase = true)
            HardwareTarget.INTEL_LNL -> socModel.equals("LNL", ignoreCase = true)
            HardwareTarget.INTEL_PTL -> socModel.equals("PTL", ignoreCase = true)
        }

    private fun ModelOption.recommendedForSoc(socModel: String): Boolean =
        recommendedSocModels.any { socModel.equals(it, ignoreCase = true) }

    private fun ModelOption.withDeviceReason(profile: DeviceProfile): ModelOption {
        val soc = when {
            profile.socModel.equals("SM8350", ignoreCase = true) -> "Snapdragon 888 (${profile.socModel})"
            profile.socModel.isBlank() -> "unknown SoC"
            else -> profile.socModel
        }
        val ram = if (profile.totalRamMb > 0) " with about ${profile.totalRamMb} MB RAM" else ""
        return copy(reason = "$reason Recommended for $soc$ram.")
    }

    companion object {
        fun defaultCatalog(): ModelCatalog = ModelCatalog(
            listOf(
                ModelOption(
                    id = "qwen3-0.6b-mixed-int4",
                    displayName = "Qwen3 0.6B mixed int4",
                    repo = "litert-community/Qwen3-0.6B",
                    fileName = "qwen3_0_6b_mixed_int4.litertlm",
                    sizeBytes = 497_664_000L,
                    license = "Apache-2.0",
                    contextTokens = 2048,
                    recommendedOutputTokens = 256,
                    maxOutputTokens = 2048,
                    backendType = ModelBackendType.LITERTLM_TEXT,
                    hardwareTarget = HardwareTarget.GENERIC_CPU,
                    performanceTier = PerformanceTier.FAST,
                    minRamMb = 4096,
                    recommendedSocModels = listOf("SM8350"),
                    avoidSocModels = emptyList(),
                    downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm",
                    reason = "Conservative default for modern arm64 Android phones with at least 6 GB RAM.",
                ),
                ModelOption(
                    id = "qwen3-0.6b-dynamic-int8",
                    displayName = "Qwen3 0.6B dynamic int8",
                    repo = "litert-community/Qwen3-0.6B",
                    fileName = "Qwen3-0.6B.litertlm",
                    sizeBytes = 614_236_160L,
                    license = "Apache-2.0",
                    contextTokens = 4096,
                    recommendedOutputTokens = 256,
                    maxOutputTokens = 4096,
                    backendType = ModelBackendType.LITERTLM_TEXT,
                    hardwareTarget = HardwareTarget.GENERIC_CPU,
                    performanceTier = PerformanceTier.BALANCED,
                    minRamMb = 6144,
                    recommendedSocModels = listOf("SM8550", "SM8650"),
                    avoidSocModels = emptyList(),
                    downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
                    reason = "Larger dynamic INT8 LiteRT-LM build with a 4096 token context; useful for balanced quality and longer-context comparison on modern CPU devices.",
                ),
                ModelOption(
                    id = "gemma4-e2b-it",
                    displayName = "Gemma 4 E2B IT",
                    repo = "litert-community/gemma-4-E2B-it-litert-lm",
                    fileName = "gemma-4-E2B-it.litertlm",
                    sizeBytes = 2_588_147_712L,
                    license = "Gemma Terms of Use",
                    contextTokens = 2048,
                    recommendedOutputTokens = 256,
                    maxOutputTokens = 512,
                    backendType = ModelBackendType.LITERTLM_TEXT,
                    hardwareTarget = HardwareTarget.GENERIC_CPU,
                    performanceTier = PerformanceTier.QUALITY,
                    minRamMb = 10_240,
                    recommendedSocModels = listOf("SM8750", "SM8850", "Tensor G5", "MT6993"),
                    avoidSocModels = emptyList(),
                    downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                    reason = "Gemma4 quality tier for testing stronger answers than 0.6B models on high-end CPU devices; expect slower first-token and decode speed on older SoCs.",
                ),
                ModelOption(
                    id = "gemma4-e4b-it",
                    displayName = "Gemma 4 E4B IT",
                    repo = "litert-community/gemma-4-E4B-it-litert-lm",
                    fileName = "gemma-4-E4B-it.litertlm",
                    sizeBytes = 3_659_530_240L,
                    license = "Gemma Terms of Use",
                    contextTokens = 2048,
                    recommendedOutputTokens = 128,
                    maxOutputTokens = 256,
                    backendType = ModelBackendType.LITERTLM_TEXT,
                    hardwareTarget = HardwareTarget.GENERIC_CPU,
                    performanceTier = PerformanceTier.STRESS,
                    minRamMb = 16_384,
                    recommendedSocModels = emptyList(),
                    avoidSocModels = listOf("SM8350"),
                    downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
                    reason = "Gemma4 stress test candidate for high-memory devices; keep output short because CPU-only inference will be slow and memory-heavy.",
                ),
            )
        )
    }
}
