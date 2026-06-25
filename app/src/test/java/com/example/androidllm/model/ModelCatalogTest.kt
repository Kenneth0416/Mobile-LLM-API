package com.example.androidllm.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun snapdragon888WithElevenGbRamRecommendsQwenThreePointSixBMixedInt4() {
        val catalog = ModelCatalog.defaultCatalog()
        val profile = DeviceProfile(
            manufacturer = "Sony",
            model = "XQ-BE72",
            socModel = "SM8350",
            totalRamMb = 11176,
            supportedAbis = listOf("arm64-v8a"),
        )

        val recommendation = catalog.recommend(profile)

        assertEquals("qwen3-0.6b-mixed-int4", recommendation.id)
        assertEquals("qwen3_0_6b_mixed_int4.litertlm", recommendation.fileName)
        assertTrue(recommendation.sizeBytes < 600L * 1024L * 1024L)
        assertTrue(recommendation.reason.contains("Snapdragon 888"))
    }

    @Test
    fun lowRamDeviceFallsBackToSmallerModel() {
        val catalog = ModelCatalog.defaultCatalog()
        val profile = DeviceProfile(
            manufacturer = "Generic",
            model = "low-ram",
            socModel = "unknown",
            totalRamMb = 4096,
            supportedAbis = listOf("arm64-v8a"),
        )

        val recommendation = catalog.recommend(profile)

        assertEquals("smollm-135m-q8", recommendation.id)
    }
}

