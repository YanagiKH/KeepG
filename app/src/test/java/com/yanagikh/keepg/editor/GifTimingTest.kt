package com.yanagikh.keepg.editor

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class GifTimingTest {
    @Test fun shortFinalFrameDoesNotExtendTheRequestedClip() {
        val timing = GifTiming(0, 90, fps = 12).validate(90)
        assertEquals(2, timing.frames)
        assertEquals(9, (0 until timing.frames).sumOf(timing::delayCs))
        assertEquals(listOf(7, 2), (0 until timing.frames).map(timing::delayCs))
    }

    @Test fun quantizedDurationIsPreservedAcrossShortClipsAndSpeeds() {
        for (ms in listOf(1, 5, 19, 20, 25, 50, 90, 101, 499, 1001)) {
            for (fps in listOf(1, 7, 12, 24)) for (speed in listOf(.25f, .5f, 1f, 2f, 4f)) {
                val timing = GifTiming(100, 100 + ms, speed, fps).validate(100 + ms)
                val delays = (0 until timing.frames).map(timing::delayCs)
                assertEquals(timing.toString(), (ms / speed / 10.0).roundToInt().coerceAtLeast(2), delays.sum())
                assertTrue(delays.all { it >= 2 })
                repeat(timing.frames) {
                    assertTrue(timing.sourceTime(it) in 100 until 100 + ms)
                    assertTrue(timing.copy(reverse = true).sourceTime(it) in 100 until 100 + ms)
                }
            }
        }
    }

    @Test fun delayRejectsFramesOutsideTheClip() {
        val timing = GifTiming(0, 90).validate(90)
        assertTrue(runCatching { timing.delayCs(-1) }.isFailure)
        assertTrue(runCatching { timing.delayCs(timing.frames) }.isFailure)
    }

    @Test fun timeAndFrameBudgetRemainEnforced() {
        listOf(GifTiming(0, 31_000), GifTiming(0, 30_000, fps = 24),
            GifTiming(0, 1000, speed = 0f), GifTiming(0, 1000, speed = Float.NaN),
            GifTiming(-1, 1000), GifTiming(1000, 1000), GifTiming(0, 1000, fps = 0)
        ).forEach { assertTrue(it.toString(), runCatching { it.validate(31_000) }.isFailure) }
    }
}
