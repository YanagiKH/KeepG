package com.yanagikh.keepg.editor

import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class GifCodecTest {
    @Test fun encoderProducesDecodableFramesAndDelayMetadata() {
        val output = ByteArrayOutputStream()
        val encoder = GifEncoder(output, 128, 96, 2)
        val random = Random(123)
        repeat(5) { frame -> encoder.frame(IntArray(128 * 96) { if (frame == 0) 0xFFFF0000.toInt() else random.nextInt() or 0xFF000000.toInt() }, 10 + frame) }
        encoder.close()
        val bytes = output.toByteArray()
        assertEquals(5, GifSafety.validate(bytes))
        ImageIO.createImageInputStream(bytes.inputStream()).use { input ->
            val reader = ImageIO.getImageReadersByFormatName("gif").next()
            try {
                reader.input = input
                assertEquals(5, reader.getNumImages(true))
                assertEquals(128, reader.read(0).width)
                assertEquals(0xFFFF0000.toInt(), reader.read(0).getRGB(0, 0))
                repeat(5) { assertEquals(96, reader.read(it).height) }
            } finally { reader.dispose() }
        }
        assertTrue(runCatching { GifSafety.validate(bytes.copyOf(bytes.size - 1)) }.isFailure)
        val bomb = bytes.clone(); bomb[6] = -1; bomb[7] = 127; bomb[8] = -1; bomb[9] = 127
        assertTrue(runCatching { GifSafety.validate(bomb) }.isFailure)
    }
    @Test fun timingRoundoffDoesNotAccumulateAndReverseStaysInRange() {
        val timing = GifTiming(100, 1100, fps = 24).validate(1200)
        assertEquals(100, (0 until timing.frames).sumOf(timing::delayCs))
        assertEquals(100, timing.sourceTime(0))
        assertEquals(1099, timing.copy(reverse = true).sourceTime(0))
        repeat(timing.frames) { assertTrue(timing.sourceTime(it) in 100 until 1100) }
        assertTrue(runCatching { GifTiming(0, 31_000).validate(31_000) }.isFailure)
    }
}
