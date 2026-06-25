package com.example.androidllm.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenEstimatorTest {
    @Test
    fun roughCountReturnsZeroForBlankText() {
        assertEquals(0, TokenEstimator.roughCount("   "))
    }

    @Test
    fun roughCountCountsEnglishWords() {
        assertTrue(TokenEstimator.roughCount("hello local phone model") >= 4)
    }

    @Test
    fun roughCountCountsChineseCharactersInsteadOfOnlyWhitespace() {
        assertTrue(TokenEstimator.roughCount("手机本地模型适合原型开发。") >= 8)
    }
}
