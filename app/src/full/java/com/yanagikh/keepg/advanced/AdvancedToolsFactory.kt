package com.yanagikh.keepg.advanced

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yanagikh.keepg.data.PhotoEntity
import com.yanagikh.keepg.media.MediaFormatRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object AdvancedToolsFactory {
    fun create(context: Context): AdvancedFeatureTools = FullAdvancedFeatureTools(context.applicationContext)
}

private class FullAdvancedFeatureTools(private val context: Context) : AdvancedFeatureTools {
    override val available: Boolean = true
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }
    private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val searchTextRecognizers by lazy {
        listOf(
            textRecognizer,
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
        )
    }
    private val segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
        )
    }

    override suspend fun recognizeText(media: PhotoEntity): String = withContext(Dispatchers.IO) {
        require(media.mimeType.startsWith("image/")) { "Text recognition requires an image" }
        val bitmap = decodeBitmap(Uri.parse(media.uri), maxPixels = 4_194_304)
        try {
            val input = InputImage.fromBitmap(bitmap, 0)
            val recognized = mutableListOf<String>()
            for (recognizer in searchTextRecognizers) {
                recognizer.process(input).await().text.trim().takeIf(String::isNotBlank)?.let(recognized::add)
            }
            recognized.distinct().joinToString("\n")
                .replace(Regex("[\\t\\x0B\\f\\r ]+"), " ")
                .trim()
                .take(16_384)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    override suspend fun detectExternalLinks(
        media: PhotoEntity,
        normalizedX: Float?,
        normalizedY: Float?,
    ): List<DetectedExternalLink> = withContext(Dispatchers.IO) {
        require(media.mimeType.startsWith("image/")) { "Link detection currently requires an image or GIF frame" }
        val bitmap = decodeBitmap(Uri.parse(media.uri))
        try {
            val input = InputImage.fromBitmap(bitmap, 0)
            val pointX = normalizedX?.coerceIn(0f, 1f)?.times(bitmap.width)?.toInt()
            val pointY = normalizedY?.coerceIn(0f, 1f)?.times(bitmap.height)?.toInt()
            val localized = pointX != null && pointY != null
            val detected = mutableListOf<DetectedExternalLink>()

            barcodeScanner.process(input).await().forEach { barcode ->
                if (localized && barcode.boundingBox?.contains(requireNotNull(pointX), requireNotNull(pointY)) != true) return@forEach
                val candidate = barcode.url?.url ?: barcode.rawValue
                normalizeWebUrl(candidate)?.let { detected += DetectedExternalLink(it, "QR/barcode") }
            }

            val recognized = textRecognizer.process(input).await()
            recognized.textBlocks.flatMap { it.lines }.forEach { line ->
                if (localized && line.boundingBox?.contains(requireNotNull(pointX), requireNotNull(pointY)) != true) return@forEach
                URL_REGEX.findAll(line.text).forEach { match ->
                    normalizeWebUrl(match.value)?.let { detected += DetectedExternalLink(it, "visible text") }
                }
            }
            detected.distinctBy { it.value }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    override suspend fun edit(media: PhotoEntity, operation: MediaEditOperation, strength: Float): String = withContext(Dispatchers.IO) {
        when {
            media.mimeType.startsWith("video/") -> editVideo(media, operation)
            media.mimeType.startsWith("image/") -> editImage(media, operation, strength)
            else -> error("Unsupported media type: ${media.mimeType}")
        }
    }

    override suspend fun previewSource(media: PhotoEntity, mode: BackgroundRemovalMode, strength: Float): Bitmap = withContext(Dispatchers.IO) {
        val decoded = decodeBitmap(Uri.parse(media.uri), 1_048_576)
        try {
            when (mode) {
                BackgroundRemovalMode.NONE -> decoded
                BackgroundRemovalMode.AUTO -> autoRemoveBackground(decoded)
                BackgroundRemovalMode.MANUAL -> manualRemoveBackground(decoded, strength.coerceIn(.05f, .95f))
            }.also { if (it !== decoded) decoded.recycle() }
        } catch (error: Throwable) { decoded.recycle(); throw error }
    }

    override suspend fun editAdvanced(media: PhotoEntity, request: AdvancedEditRequest): String = withContext(Dispatchers.IO) {
        request.validate()
        require(media.mimeType.startsWith("image/")) { "The layer editor currently exports image and GIF frames" }
        val decoded = decodeBitmap(Uri.parse(media.uri))
        var source: Bitmap? = decoded
        var output: Bitmap? = null
        try {
            source = when (request.backgroundRemoval) {
                BackgroundRemovalMode.NONE -> decoded
                BackgroundRemovalMode.AUTO -> autoRemoveBackground(decoded)
                BackgroundRemovalMode.MANUAL -> manualRemoveBackground(decoded, request.backgroundStrength)
            }
            if (source !== decoded && !decoded.isRecycled) decoded.recycle()
            output = com.yanagikh.keepg.editor.ImageRenderer.render(requireNotNull(source), request)
            com.yanagikh.keepg.editor.ImageExport(context).save(requireNotNull(output), request.outputName, request.exportFormat, request.exportQuality, sanitizeTimestamp(media.dateTaken)).toString()
        } finally { recycleDistinctBitmaps(decoded, source, output) }
    }

    override suspend fun repair(media: PhotoEntity, preferredTimestamp: Long?): RepairReport = withContext(Dispatchers.IO) {
        val timestamp = sanitizeTimestamp(preferredTimestamp ?: media.dateTaken)
        val normalized = MediaFormatRegistry.normalizedMime(media.displayName, media.mimeType)
        return@withContext try {
            val output = if (media.mimeType.startsWith("video/")) {
                remuxVideo(media, trimEndUs = Long.MAX_VALUE, mute = false, suffix = "repaired", dateTaken = timestamp)
            } else {
                val bitmap = decodeBitmap(Uri.parse(media.uri))
                try {
                    saveBitmap(bitmap, media.displayName, "repaired", preserveAlpha = bitmap.hasAlpha(), dateTaken = timestamp)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
            RepairReport(true, "Recovered copy created: $output · MIME $normalized · date $timestamp")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            RepairReport(false, "Repair could not recover this file: ${error.message ?: error::class.java.simpleName}")
        }
    }

    private suspend fun editImage(media: PhotoEntity, operation: MediaEditOperation, strength: Float): String {
        val source = decodeBitmap(Uri.parse(media.uri))
        var edited: Bitmap? = null
        return try {
            edited = when (operation) {
                MediaEditOperation.ROTATE_RIGHT -> transform(source, Matrix().apply { postRotate(90f) })
                MediaEditOperation.FLIP_HORIZONTAL -> transform(source, Matrix().apply { postScale(-1f, 1f) })
                MediaEditOperation.GRAYSCALE -> grayscale(source)
                MediaEditOperation.CROP_SQUARE -> cropSquare(source)
                MediaEditOperation.AUTO_BACKGROUND_REMOVAL -> autoRemoveBackground(source)
                MediaEditOperation.MANUAL_BACKGROUND_REMOVAL -> manualRemoveBackground(source, strength.coerceIn(0.05f, 0.95f))
                MediaEditOperation.EXTRACT_GIF_FRAME -> source.copy(Bitmap.Config.ARGB_8888, false)
                MediaEditOperation.VIDEO_TRIM_FIRST_5_SECONDS,
                MediaEditOperation.VIDEO_MUTE -> error("Video operation selected for an image")
            }
            val alpha = operation == MediaEditOperation.AUTO_BACKGROUND_REMOVAL || operation == MediaEditOperation.MANUAL_BACKGROUND_REMOVAL || operation == MediaEditOperation.EXTRACT_GIF_FRAME
            saveBitmap(requireNotNull(edited), media.displayName, operation.name.lowercase(), alpha, sanitizeTimestamp(media.dateTaken)).toString()
        } finally {
            recycleDistinctBitmaps(source, edited)
        }
    }

    private suspend fun editVideo(media: PhotoEntity, operation: MediaEditOperation): String = when (operation) {
        MediaEditOperation.VIDEO_TRIM_FIRST_5_SECONDS -> remuxVideo(media, trimEndUs = 5_000_000L, mute = false, suffix = "trim5s", dateTaken = sanitizeTimestamp(media.dateTaken)).toString()
        MediaEditOperation.VIDEO_MUTE -> remuxVideo(media, trimEndUs = Long.MAX_VALUE, mute = true, suffix = "muted", dateTaken = sanitizeTimestamp(media.dateTaken)).toString()
        else -> error("Choose Trim first 5 seconds or Mute for video media")
    }

    private fun decodeBitmap(uri: Uri, maxPixels: Int = 8_388_608): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open $uri" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Android could not read this image format" }
        var sample = 1
        while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample).toLong() > maxPixels) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open $uri" }
            BitmapFactory.decodeStream(input, null, options) ?: error("Android could not decode this image format")
        }
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot read metadata for $uri" }
                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val orientationMatrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> { setRotate(180f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        if (orientationMatrix.isIdentity) return decoded
        return try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, orientationMatrix, true).also {
                if (it !== decoded && !decoded.isRecycled) decoded.recycle()
            }
        } catch (error: Throwable) {
            if (!decoded.isRecycled) decoded.recycle()
            throw error
        }
    }

    private fun transform(source: Bitmap, matrix: Matrix): Bitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)

    private fun cropSquare(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        return Bitmap.createBitmap(source, (source.width - size) / 2, (source.height - size) / 2, size, size)
    }

    private fun grayscale(source: Bitmap): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val pixels = IntArray(output.width * output.height)
            output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
            for (i in pixels.indices) {
                val c = pixels[i]
                val y = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).toInt().coerceIn(0, 255)
                pixels[i] = Color.argb(Color.alpha(c), y, y, y)
            }
            output.setPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
            return output
        } catch (error: Throwable) {
            if (!output.isRecycled) output.recycle()
            throw error
        }
    }

    private suspend fun autoRemoveBackground(source: Bitmap): Bitmap {
        val mask = segmenter.process(InputImage.fromBitmap(source, 0)).await()
        val buffer = mask.buffer.order(ByteOrder.nativeOrder())
        buffer.rewind()
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val width = min(output.width, mask.width)
            val height = min(output.height, mask.height)
            val pixels = IntArray(output.width * output.height)
            output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
            for (y in 0 until mask.height) {
                for (x in 0 until mask.width) {
                    val confidence = buffer.float
                    if (x < width && y < height) {
                        val index = y * output.width + x
                        val alpha = (((confidence - 0.15f) / 0.70f).coerceIn(0f, 1f) * 255f).toInt()
                        pixels[index] = (pixels[index] and 0x00FFFFFF) or (alpha shl 24)
                    }
                }
            }
            output.setPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
            return output
        } catch (error: Throwable) {
            if (!output.isRecycled) output.recycle()
            throw error
        }
    }

    private fun manualRemoveBackground(source: Bitmap, strength: Float): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val sample = output.getPixel(0, 0)
            val threshold = 441.7 * strength
            val pixels = IntArray(output.width * output.height)
            output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
            val sr = Color.red(sample)
            val sg = Color.green(sample)
            val sb = Color.blue(sample)
            for (i in pixels.indices) {
                val c = pixels[i]
                val dr = Color.red(c) - sr
                val dg = Color.green(c) - sg
                val db = Color.blue(c) - sb
                val distance = sqrt((dr * dr + dg * dg + db * db).toDouble())
                if (distance <= threshold) pixels[i] = c and 0x00FFFFFF
            }
            output.setPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
            return output
        } catch (error: Throwable) {
            if (!output.isRecycled) output.recycle()
            throw error
        }
    }

    private fun saveBitmap(bitmap: Bitmap, originalName: String, suffix: String, preserveAlpha: Boolean, dateTaken: Long): Uri {
        val sanitized = sanitizeDisplayName(originalName).ifBlank { "KeepG" }
        val base = sanitized.substringBeforeLast('.', sanitized).take(80)
        val mime = if (preserveAlpha) "image/png" else "image/jpeg"
        val extension = if (preserveAlpha) "png" else "jpg"
        return saveBitmapInternal(bitmap, "${base}_$suffix.$extension", mime, preserveAlpha, dateTaken)
    }

    private fun saveBitmapNamed(bitmap: Bitmap, requestedName: String, dateTaken: Long): Uri {
        val sanitized = sanitizeDisplayName(requestedName).ifBlank { "KeepG_edit" }
        val base = sanitized.substringBeforeLast('.', sanitized).take(100)
        return saveBitmapInternal(bitmap, "$base.png", "image/png", true, dateTaken)
    }

    private fun saveBitmapInternal(bitmap: Bitmap, displayName: String, mime: String, png: Boolean, dateTaken: Long): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.DATE_TAKEN, dateTaken)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KeepG")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Unable to create edited image")
        var published = false
        try {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output)
                val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                check(bitmap.compress(format, 94, output)) { "Image encoder failed" }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                check(context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1) { "Unable to publish edited image" }
            }
            published = true
            return uri
        } finally {
            if (!published) runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    private suspend fun remuxVideo(media: PhotoEntity, trimEndUs: Long, mute: Boolean, suffix: String, dateTaken: Long): Uri {
        val inputUri = Uri.parse(media.uri)
        val temp = File.createTempFile("keepg_", ".mp4", context.cacheDir)
        try {
            val extractor = MediaExtractor()
            try {
                val afd = context.contentResolver.openAssetFileDescriptor(inputUri, "r") ?: error("Cannot open video")
                try {
                    extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    val muxer = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    var muxerStarted = false
                    var muxCompleted = false
                    try {
                        val trackMap = mutableMapOf<Int, Int>()
                        for (i in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(i)
                            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                            if (mute && mime.startsWith("audio/")) continue
                            if (!mime.startsWith("audio/") && !mime.startsWith("video/")) continue
                            extractor.selectTrack(i)
                            trackMap[i] = muxer.addTrack(format)
                        }
                        require(trackMap.isNotEmpty()) { "No compatible audio/video tracks" }
                        muxer.start()
                        muxerStarted = true
                        extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                        val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
                        val info = MediaCodec.BufferInfo()
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val sourceTrack = extractor.sampleTrackIndex
                            if (sourceTrack < 0) break
                            val timeUs = extractor.sampleTime
                            if (timeUs < 0 || timeUs > trimEndUs) break
                            buffer.clear()
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) break
                            trackMap[sourceTrack]?.let { destinationTrack ->
                                info.set(0, size, max(0L, timeUs), extractorFlagsToCodecFlags(extractor.sampleFlags))
                                muxer.writeSampleData(destinationTrack, buffer, info)
                            }
                            extractor.advance()
                        }
                        muxCompleted = true
                    } finally {
                        val stopError = if (muxerStarted) runCatching { muxer.stop() }.exceptionOrNull() else null
                        val releaseError = runCatching { muxer.release() }.exceptionOrNull()
                        if (muxCompleted) {
                            if (stopError != null) {
                                if (releaseError != null) stopError.addSuppressed(releaseError)
                                throw stopError
                            }
                            if (releaseError != null) throw releaseError
                        }
                    }
                } finally {
                    afd.close()
                }
            } finally {
                extractor.release()
            }

            val sanitized = sanitizeDisplayName(media.displayName).ifBlank { "KeepG_video" }
            val base = sanitized.substringBeforeLast('.', sanitized).take(80).ifBlank { "KeepG_video" }
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${base}_$suffix.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_TAKEN, dateTaken)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/KeepG")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            currentCoroutineContext().ensureActive()
            val outputUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Unable to create edited video")
            var published = false
            try {
                context.contentResolver.openOutputStream(outputUri, "w").use { output ->
                    requireNotNull(output)
                    temp.inputStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                    }
                }
                currentCoroutineContext().ensureActive()
                if (Build.VERSION.SDK_INT >= 29) {
                    check(context.contentResolver.update(outputUri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) == 1) { "Unable to publish edited video" }
                }
                published = true
                return outputUri
            } finally {
                if (!published) runCatching { context.contentResolver.delete(outputUri, null, null) }
            }
        } finally {
            temp.delete()
        }
    }

    private fun recycleDistinctBitmaps(vararg bitmaps: Bitmap?) {
        bitmaps.forEachIndexed { index, bitmap ->
            if (bitmap != null && !bitmap.isRecycled && bitmaps.take(index).none { previous -> previous === bitmap }) {
                bitmap.recycle()
            }
        }
    }

    private fun extractorFlagsToCodecFlags(sampleFlags: Int): Int {
        var codecFlags = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        return codecFlags
    }

    private fun sanitizeTimestamp(value: Long): Long {
        val now = System.currentTimeMillis()
        val earliest = 631_152_000_000L
        return if (value in earliest..(now + 86_400_000L)) value else now
    }

    private fun sanitizeDisplayName(value: String): String = value
        .replace(Regex("[\\p{Cc}\\\\/:*?\"<>|]"), "_")
        .trim()
        .take(110)
        .trimStart('.', ' ')
        .trimEnd('.', ' ')
        .takeUnless { it == "." || it == ".." }
        .orEmpty()

    private fun normalizeWebUrl(raw: String?): String? {
        val trimmed = raw?.trim()?.trimEnd('.', ',', ';', ')', ']', '}') ?: return null
        val normalized = if (trimmed.startsWith("www.", ignoreCase = true)) "https://$trimmed" else trimmed
        val uri = runCatching { Uri.parse(normalized) }.getOrNull() ?: return null
        return if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) normalized else null
    }

    companion object {
        private val URL_REGEX = Regex("(?i)\\b(?:https?://|www\\.)[^\\s<>{}\\[\\]\\\"']+")
    }
}
