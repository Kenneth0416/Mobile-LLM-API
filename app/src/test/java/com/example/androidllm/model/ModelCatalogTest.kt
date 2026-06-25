package com.example.androidllm.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(2048, recommendation.contextTokens)
        assertEquals(256, recommendation.recommendedOutputTokens)
        assertEquals(2048, recommendation.maxOutputTokens)
        assertTrue(recommendation.sizeBytes < 600L * 1024L * 1024L)
        assertTrue(recommendation.reason.contains("Snapdragon 888"))
    }

    @Test
    fun catalogIncludesDynamicInt8QwenModelForThisDevice() {
        val catalog = ModelCatalog.defaultCatalog()

        val model = catalog.requireById("qwen3-0.6b-dynamic-int8")

        assertEquals("Qwen3-0.6B.litertlm", model.fileName)
        assertEquals(4096, model.contextTokens)
        assertEquals(256, model.recommendedOutputTokens)
        assertTrue(model.sizeBytes > 600L * 1000L * 1000L)
    }

    @Test
    fun catalogIncludesGemmaFourCandidatesForQualityAndStressTesting() {
        val catalog = ModelCatalog.defaultCatalog()

        val e2b = catalog.requireById("gemma4-e2b-it")
        val e4b = catalog.requireById("gemma4-e4b-it")

        assertEquals("gemma-4-E2B-it.litertlm", e2b.fileName)
        assertEquals(2_588_147_712L, e2b.sizeBytes)
        assertEquals(2048, e2b.contextTokens)
        assertEquals(256, e2b.recommendedOutputTokens)
        assertEquals(512, e2b.maxOutputTokens)
        assertTrue(e2b.reason.contains("quality"))

        assertEquals("gemma-4-E4B-it.litertlm", e4b.fileName)
        assertEquals(3_659_530_240L, e4b.sizeBytes)
        assertEquals(2048, e4b.contextTokens)
        assertEquals(128, e4b.recommendedOutputTokens)
        assertEquals(256, e4b.maxOutputTokens)
        assertTrue(e4b.reason.contains("stress"))
    }

    @Test
    fun modelOptionsIncludeHardwareCompatibilityMetadata() {
        val catalog = ModelCatalog.defaultCatalog()

        val fastDefault = catalog.requireById("qwen3-0.6b-mixed-int4")
        val qualityCandidate = catalog.requireById("gemma4-e2b-it")

        assertEquals(ModelBackendType.LITERTLM_TEXT, fastDefault.backendType)
        assertEquals(HardwareTarget.GENERIC_CPU, fastDefault.hardwareTarget)
        assertEquals(PerformanceTier.FAST, fastDefault.performanceTier)
        assertEquals(4096, fastDefault.minRamMb)
        assertTrue(fastDefault.recommendedSocModels.contains("SM8350"))

        assertEquals(ModelBackendType.LITERTLM_TEXT, qualityCandidate.backendType)
        assertEquals(HardwareTarget.GENERIC_CPU, qualityCandidate.hardwareTarget)
        assertEquals(PerformanceTier.QUALITY, qualityCandidate.performanceTier)
        assertEquals(10_240, qualityCandidate.minRamMb)
        assertTrue(qualityCandidate.recommendedSocModels.contains("SM8750"))
    }

    @Test
    fun lowRamGenericArm64DeviceOnlySeesFastGenericCpuModelsAsCompatible() {
        val catalog = ModelCatalog.defaultCatalog()
        val profile = DeviceProfile(
            manufacturer = "Generic",
            model = "low-ram",
            socModel = "unknown",
            totalRamMb = 4096,
            supportedAbis = listOf("arm64-v8a"),
        )

        val compatibleIds = catalog.compatibleOptions(profile).map { it.id }

        assertTrue(compatibleIds.contains("qwen3-0.6b-mixed-int4"))
        assertFalse(compatibleIds.contains("gemma4-e2b-it"))
        assertEquals("qwen3-0.6b-mixed-int4", catalog.recommend(profile).id)
    }

    @Test
    fun snapdragon888CanTestGemmaFourE2BWithoutMakingItTheDefault() {
        val catalog = ModelCatalog.defaultCatalog()
        val profile = DeviceProfile(
            manufacturer = "Sony",
            model = "XQ-BE72",
            socModel = "SM8350",
            totalRamMb = 11176,
            supportedAbis = listOf("arm64-v8a"),
        )

        val compatibleIds = catalog.compatibleOptions(profile).map { it.id }

        assertTrue(compatibleIds.contains("gemma4-e2b-it"))
        assertFalse(compatibleIds.contains("gemma4-e4b-it"))
        assertEquals("qwen3-0.6b-mixed-int4", catalog.recommend(profile).id)
    }

    @Test
    fun midrangeGenericArm64DeviceRecommendsBalancedLongContextQwen() {
        val catalog = ModelCatalog.defaultCatalog()
        val profile = DeviceProfile(
            manufacturer = "Generic",
            model = "midrange",
            socModel = "unknown",
            totalRamMb = 8192,
            supportedAbis = listOf("arm64-v8a"),
        )

        val recommendation = catalog.recommend(profile)

        assertEquals("qwen3-0.6b-dynamic-int8", recommendation.id)
        assertEquals(PerformanceTier.BALANCED, recommendation.performanceTier)
    }

    @Test
    fun futureHighEndQualcommDeviceCanRecommendGemmaFourE2B() {
        val catalog = ModelCatalog.defaultCatalog()
        val profile = DeviceProfile(
            manufacturer = "Future",
            model = "high-end",
            socModel = "SM8750",
            totalRamMb = 16_384,
            supportedAbis = listOf("arm64-v8a"),
        )

        val recommendation = catalog.recommend(profile)

        assertEquals("gemma4-e2b-it", recommendation.id)
        assertTrue(recommendation.reason.contains("SM8750"))
    }
}
