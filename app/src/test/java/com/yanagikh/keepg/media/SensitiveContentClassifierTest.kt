package com.yanagikh.keepg.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveContentClassifierTest {
    @Test
    fun sampleSize_isPowerOfTwoAndRespectsDefaultLimits() {
        val width = 20_000
        val height = 12_000

        val sampleSize = sensitivePreviewSampleSize(width, height)
        val sampledWidth = ceilDivide(width, sampleSize)
        val sampledHeight = ceilDivide(height, sampleSize)

        assertTrue(sampleSize > 0 && sampleSize and (sampleSize - 1) == 0)
        assertTrue(sampledWidth <= SENSITIVE_PREVIEW_MAX_SIDE)
        assertTrue(sampledHeight <= SENSITIVE_PREVIEW_MAX_SIDE)
        assertTrue(sampledWidth.toLong() * sampledHeight <= SENSITIVE_PREVIEW_MAX_PIXELS)
    }

    @Test
    fun sampleSize_returnsSmallestValidPowerOfTwo() {
        assertEquals(1, sensitivePreviewSampleSize(2_000, 2_000))
        assertEquals(2, sensitivePreviewSampleSize(4_001, 2_000))
        assertEquals(4, sensitivePreviewSampleSize(8_192, 8_192))
    }

    @Test
    fun sampleSize_handlesInvalidBoundsWithoutLooping() {
        assertEquals(1, sensitivePreviewSampleSize(0, 12_000))
        assertEquals(1, sensitivePreviewSampleSize(-1, -1))
    }

    private fun ceilDivide(value: Int, divisor: Int): Int =
        ((value.toLong() + divisor - 1L) / divisor).toInt()
}
