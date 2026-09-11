package com.yanagikh.keepg.editor

import kotlin.math.ceil
import kotlin.math.roundToInt

data class GifTiming(val startMs: Int, val endMs: Int, val speed: Float = 1f, val fps: Int = 12, val reverse: Boolean = false, val loops: Int = 0) {
    val frames get() = ceil((endMs - startMs) / speed * fps / 1000.0).toInt().coerceAtLeast(1)
    fun delayCs(frame: Int) = (((frame + 1) * 100.0 / fps).roundToInt() - (frame * 100.0 / fps).roundToInt()).coerceAtLeast(2)
    fun validate(duration: Int): GifTiming = apply {
        require(duration > 0 && startMs >= 0 && endMs > startMs && endMs <= duration)
        require(speed.isFinite() && speed in .25f..4f && fps in 1..24 && loops in 0..65535)
        require((endMs - startMs) / speed <= 30_000f && frames <= 360)
    }
    fun sourceTime(frame: Int): Int {
        require(frame in 0 until frames)
        val offset = (frame * 1000.0 * speed / fps).toInt().coerceAtMost(endMs - startMs - 1)
        return if (reverse) endMs - 1 - offset else startMs + offset
    }
}
