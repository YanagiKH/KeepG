package com.yanagikh.keepg.editor

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.yanagikh.keepg.advanced.AdvancedEditRequest
import com.yanagikh.keepg.data.ImageExportFormat
import com.yanagikh.keepg.data.PhotoEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Real Android codecs and MediaStore; only fixtures created by this test are removed. */
@RunWith(AndroidJUnit4::class)
class MediaExportInstrumentedTest {
    @get:Rule val storage = GrantPermissionRule.grant(*if (Build.VERSION.SDK_INT < 29)
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE) else emptyArray<String>())
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val outputs = mutableListOf<Uri>()
    private val fixtures = mutableListOf<File>()

    @After fun cleanup() {
        outputs.forEach { context.contentResolver.delete(it, null, null) }
        fixtures.forEach { it.delete() }
    }
    private fun photo(file: File, mime: String, width: Int, height: Int) = PhotoEntity(
        99001, Uri.fromFile(file).toString(), 9900, "Export test", file.name, mime, 0, width, height)
    private fun fixture(suffix: String) = File.createTempFile("keepg-export-", suffix, context.cacheDir).also { fixtures += it }
    private fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())

    @Test fun allImageFormatsAreDecodableCopiesAndJpegFlattensAlpha() {
        val original = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        try {
            Canvas(original).drawRect(40f, 20f, 100f, 60f, Paint().apply { color = Color.RED })
            for (format in ImageExportFormat.entries) {
                val uri = ImageExport(context).save(original, "keepg_test_${UUID.randomUUID()}", format, 90, 0).also { outputs += it }
                val decoded = requireNotNull(context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) })
                try {
                    assertEquals(120, decoded.width); assertEquals(80, decoded.height)
                    assertTrue(Color.red(decoded.getPixel(70, 40)) > 200)
                    if (format == ImageExportFormat.JPEG) {
                        val corner = decoded.getPixel(5, 5)
                        assertTrue(Color.red(corner) > 240 && Color.green(corner) > 240 && Color.blue(corner) > 240)
                    } else assertEquals(0, Color.alpha(decoded.getPixel(5, 5)))
                } finally { decoded.recycle() }
            }
            assertEquals(0, Color.alpha(original.getPixel(5, 5)))
            assertEquals(Color.RED, original.getPixel(70, 40))
        } finally { original.recycle() }
    }

    @Test fun failedImageWriteLeavesNoPublishedOrPendingItem() {
        val name = "keepg_rollback_${UUID.randomUUID()}"
        try {
            ImageExport(context).write(name, "png", "image/png", 0) { it.write(byteArrayOf(1, 2, 3)); throw IOException("test writer failure") }
            fail("Writer failure must propagate")
        } catch (expected: IOException) { assertEquals("test writer failure", expected.message) }
        context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?", arrayOf("$name.png"), null).use { assertEquals(0, requireNotNull(it).count) }
    }

    @Test fun gifExportPreservesAnimationAndAppliesCropWithoutChangingSource() = runBlocking {
        val file = fixture(".gif")
        file.outputStream().use { stream -> GifEncoder(stream, 64, 64, 0).use { encoder ->
            encoder.frame(IntArray(64 * 64) { Color.RED }, 10)
            encoder.frame(IntArray(64 * 64) { Color.BLUE }, 10)
        } }
        val before = digest(file)
        val input = photo(file, "image/gif", 64, 64)
        val repository = GifEditRepository(context)
        val source = repository.open(input)
        val uri = repository.export(input, source, GifTiming(0, 200, fps = 10, reverse = true),
            AdvancedEditRequest(outputName = "keepg_gif_${UUID.randomUUID()}", cropLeft = .25f, cropRight = .75f), 240, {}).also { outputs += it }
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
        GifSafety.validate(bytes)
        @Suppress("DEPRECATION") val movie = requireNotNull(android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size))
        assertEquals(32, movie.width()); assertEquals(64, movie.height())
        assertTrue(movie.duration() >= 180)
        val result = repository.open(input.copy(uri = uri.toString()))
        val first = result.frame(0, 64); val second = result.frame(110, 64)
        try {
            assertTrue(Color.blue(first.getPixel(16, 32)) > 200)
            assertTrue(Color.red(second.getPixel(16, 32)) > 200)
        } finally { first.recycle(); second.recycle() }
        assertArrayEquals(before, digest(file))
    }

    @Test fun videoExporterTrimsCropsChangesSpeedAndRemovesAudio() = runBlocking {
        val file = fixture(".mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("editor-sample.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        val before = digest(file)
        val uri = withTimeout(120_000) {
            VideoExporter(context).export(photo(file, "video/mp4", 160, 120), VideoEditSpec(
                startMs = 250, endMs = 1750, crop = CropBounds(.25f, 0f, .75f, 1f),
                rotation = 90f, speed = 2f, mute = true, outputName = "keepg_video_${UUID.randomUUID()}"), {})
        }.also { outputs += it }
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(context, uri)
            val duration = requireNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
            assertTrue("Trimmed 2x duration: $duration", duration in 500L..1000L)
            assertNotEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            val frame = requireNotNull(metadata.getFrameAtTime(0))
            try { assertEquals(setOf(80, 120), setOf(frame.width, frame.height)) } finally { frame.recycle() }
        } finally { metadata.release() }
        assertArrayEquals(before, digest(file))
    }
}
