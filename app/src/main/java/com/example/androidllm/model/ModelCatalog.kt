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

data class ModelOption(
    val id: String,
    val displayName: String,
    val repo: String,
    val fileName: String,
    val sizeBytes: Long,
    val license: String,
    val downloadUrl: String,
    val reason: String,
)

class ModelCatalog private constructor(
    val options: List<ModelOption>,
) {
    fun recommend(profile: DeviceProfile): ModelOption {
        val hasArm64 = profile.supportedAbis.any { it == "arm64-v8a" }
        if (hasArm64 && profile.totalRamMb in 1 until 6144) {
            return options.first { it.id == "smollm-135m-q8" }
        }

        if (profile.socModel.equals("SM8350", ignoreCase = true)) {
            return options.first { it.id == "qwen3-0.6b-mixed-int4" }.copy(
                reason = "Snapdragon 888 with about ${profile.totalRamMb} MB RAM is a good fit for the 0.6B mixed int4 LiteRT-LM model.",
            )
        }

        return options.first { it.id == "qwen3-0.6b-mixed-int4" }
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
                    downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm",
                    reason = "Conservative default for modern arm64 Android phones with at least 6 GB RAM.",
                ),
                ModelOption(
                    id = "smollm-135m-q8",
                    displayName = "SmolLM 135M q8 task fallback",
                    repo = "litert-community/SmolLM-135M-Instruct",
                    fileName = "SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
                    sizeBytes = 220_000_000L,
                    license = "Apache-2.0",
                    downloadUrl = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task",
                    reason = "Fallback option for low RAM devices; this first build installs Qwen3 on the connected Sony phone.",
                ),
            )
        )
    }
}

