package com.yanagikh.keepg.editor

import kotlin.math.ceil
import kotlin.math.roundToInt

/** GIF time is quantized to centiseconds; 20ms is the minimum interoperable frame delay. */
data class GifTiming(val startMs: Int, val endMs: Int, val speed: Float = 1f, val fps: Int = 12, val reverse: Boolean = false, val loops: Int = 0) {
    private val durationCs get() = ((endMs - startMs).toDouble() / speed / 10).roundToInt().coerceAtLeast(2)
    val frames get() = ceil((endMs - startMs) / speed * fps / 1000.0).toInt()
        .coerceAtLeast(1).coerceAtMost((durationCs / 2).coerceAtLeast(1))

    fun delayCs(frame: Int): Int {
        require(frame in 0 until frames)
        // Reserve at least 2cs for every remaining frame. The final boundary is the
        // requested end, not the next full FPS period (which extended short clips).
        fun boundary(index: Int): Int = when (index) {
            0 -> 0
            frames -> durationCs
            else -> (index * 100.0 / fps).roundToInt().coerceIn(index * 2, durationCs - (frames - index) * 2)
        }
        return boundary(frame + 1) - boundary(frame)
    }

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
