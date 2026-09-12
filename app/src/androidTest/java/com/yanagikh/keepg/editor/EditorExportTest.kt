package com.yanagikh.keepg.editor

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Movie
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yanagikh.keepg.advanced.AdvancedEditRequest
import com.yanagikh.keepg.data.ImageExportFormat
import com.yanagikh.keepg.data.PhotoEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Real encoder + MediaStore round trips. Only this test's own URIs are ever deleted. */
@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class EditorExportTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val created = mutableListOf<Uri>()
    private val inputs = mutableListOf<File>()
    private fun name() = "KeepG_QA_${UUID.randomUUID()}"
    private fun digest(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())

    @Before fun grantLegacyWriteForTestCopies() {
        if (Build.VERSION.SDK_INT <= 28) {
            for (permission in listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("pm grant ${context.packageName} $permission")).use { it.readBytes() }
            }
        }
    }
    @After fun removeOnlyTestCopies() {
        created.forEach { context.contentResolver.delete(it, null, null) }
        inputs.forEach { it.delete() }
    }
    private fun fixture(extension: String): File = File(context.cacheDir, "${name()}.$extension").also { inputs += it }
    private fun photo(file: File, mime: String, width: Int, height: Int) = PhotoEntity(
        9_000_001, Uri.fromFile(file).toString(), 9_000_000, "QA", file.name, mime, 0, width, height)
    private fun imageCount(displayName: String): Int = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
        "${MediaStore.Images.Media.DISPLAY_NAME} = ?", arrayOf(displayName), null
    )!!.use { it.count }

    @Test fun imageFormatsAreDecodableAndJpegFlattensAlphaToWhite() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.TRANSPARENT)
        source.setPixel(0, 0, Color.RED)
        try {
            for (format in ImageExportFormat.entries) {
                val uri = ImageExport(context).save(source, name(), format, 92, 0).also { created += it }
                val decoded = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }!!
                try {
                    assertEquals(64, decoded.width); assertEquals(64, decoded.height)
                    if (format == ImageExportFormat.JPEG) {
                        val pixel = decoded.getPixel(63, 63)
                        assertTrue(Color.red(pixel) >= 245 && Color.green(pixel) >= 245 && Color.blue(pixel) >= 245)
                    } else assertEquals(0, Color.alpha(decoded.getPixel(63, 63)))
                } finally { decoded.recycle() }
            }
            assertEquals(Color.TRANSPARENT, source.getPixel(63, 63))
        } finally { source.recycle() }
    }

    @Test fun failedAndCancelledWritesLeaveNoPublishedOrPendingImage() {
        for (cancel in listOf(false, true)) {
            val outputName = name()
            assertEquals(0, imageCount("$outputName.png"))
            val result = runCatching {
                ImageExport(context).write(outputName, "png", "image/png", 0) {
                    it.write(byteArrayOf(1, 2, 3))
                    if (cancel) throw CancellationException("QA cancellation") else throw IOException("QA write failure")
                }
            }
            assertTrue(result.isFailure)
            assertEquals(cancel, result.exceptionOrNull() is CancellationException)
            assertEquals(0, imageCount("$outputName.png"))
        }
    }

    @Test fun animatedGifExportPreservesShortDurationCropAndOriginal() = runBlocking {
        val file = fixture("gif")
        file.outputStream().use { output ->
            GifEncoder(output, 128, 96, 0).use { encoder ->
                repeat(12) { frame -> encoder.frame(IntArray(128 * 96) { if (frame % 2 == 0) Color.RED else Color.BLUE }, 10) }
            }
        }
        val before = digest(file)
        val media = photo(file, "image/gif", 128, 96)
        val repository = GifEditRepository(context)
        val source = repository.open(media)
        val request = AdvancedEditRequest(name(), viewportAspectRatio = 128f / 96f)
            .withCrop(CropBounds(.25f, 0f, .75f, 1f))
        val progress = mutableListOf<Float>()
        val uri = repository.export(media, source, GifTiming(0, 90, fps = 12), request, 128) { progress += it }.also { created += it }
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val decoded = requireNotNull(Movie.decodeByteArray(bytes, 0, bytes.size))
        assertEquals(2, GifSafety.validate(bytes))
        assertEquals(90, decoded.duration())
        assertEquals(64, decoded.width()); assertEquals(96, decoded.height())
        assertEquals(1f, progress.last(), .0001f)
        assertArrayEquals(before, digest(file))
    }

    @Test fun videoTrimMuteRotationExportsPlayableCopyAndPreservesOriginal() = runBlocking {
        val file = fixture("mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("editor-sample.mp4").use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        val before = digest(file)
        val media = photo(file, "video/mp4", 160, 120)
        val uri = VideoExporter(context).export(media,
            VideoEditSpec(startMs = 250, endMs = 1250, rotation = 90f, mute = true, outputName = name())) {}.also { created += it }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("Unexpected exported duration $duration", duration in 800L..1200L)
            assertNotEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            val frame = requireNotNull(retriever.getFrameAtTime(0))
            assertTrue(frame.width > 0 && frame.height > 0)
            frame.recycle()
        } finally { retriever.release() }
        assertArrayEquals(before, digest(file))
    }
}
