package com.example.androidllm.benchmark

import com.example.androidllm.llm.TokenEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkPromptFactoryTest {
    @Test
    fun promptGenerationIsDeterministicForSameTarget() {
        val first = BenchmarkPromptFactory.promptForPrefill(256)
        val second = BenchmarkPromptFactory.promptForPrefill(256)

        assertEquals(first, second)
    }

    @Test
    fun promptGenerationApproximatesRequestedTokenCount() {
        val prompt = BenchmarkPromptFactory.promptForPrefill(512)
        val roughTokens = TokenEstimator.roughCount(prompt)

        assertTrue("rough token count was $roughTokens", roughTokens in 460..580)
    }

    @Test
    fun promptGenerationInstructsModelToReturnShortBenchmarkAnswer() {
        val prompt = BenchmarkPromptFactory.promptForPrefill(128)

        assertTrue(prompt.contains("Reply exactly: OK"))
        assertTrue(prompt.contains("/no_think"))
    }
}
