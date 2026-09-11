package com.yanagikh.keepg.editor

import java.io.ByteArrayOutputStream
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
        // Android's Kotlin compile API excludes java.desktop; the host JVM still has
        // ImageIO. Reflection preserves this independent decoder oracle in the JVM test.
        val imageIO = Class.forName("javax.imageio.ImageIO")
        val input = imageIO.getMethod("createImageInputStream", Any::class.java).invoke(null, bytes.inputStream())
        val readers = imageIO.getMethod("getImageReadersByFormatName", String::class.java).invoke(null, "gif") as Iterator<*>
        val reader = requireNotNull(readers.next())
        val readerApi = Class.forName("javax.imageio.ImageReader")
        val imageApi = Class.forName("java.awt.image.BufferedImage")
        try {
            readerApi.getMethod("setInput", Any::class.java).invoke(reader, input)
            assertEquals(5, readerApi.getMethod("getNumImages", Boolean::class.javaPrimitiveType).invoke(reader, true))
            val read = readerApi.getMethod("read", Int::class.javaPrimitiveType)
            val first = read.invoke(reader, 0)
            assertEquals(128, imageApi.getMethod("getWidth").invoke(first))
            assertEquals(0xFFFF0000.toInt(), imageApi.getMethod("getRGB", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(first, 0, 0))
            repeat(5) { assertEquals(96, imageApi.getMethod("getHeight").invoke(read.invoke(reader, it))) }
        } finally {
            readerApi.getMethod("dispose").invoke(reader)
            (input as java.io.Closeable).close()
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
