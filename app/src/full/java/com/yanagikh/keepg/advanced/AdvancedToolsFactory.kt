package com.yanagikh.keepg.advanced

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.yanagikh.keepg.data.PhotoEntity
import com.yanagikh.keepg.media.MediaFormatRegistry
import kotlinx.coroutines.Dispatchers
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
    private val segmenter by lazy {
        Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build()
        )
    }

    override suspend fun detectExternalLinks(
        media: PhotoEntity,
        normalizedX: Float?,
        normalizedY: Float?,
    ): List<DetectedExternalLink> = withContext(Dispatchers.IO) {
        require(media.mimeType.startsWith("image/")) { "Link detection currently requires an image or GIF frame" }
        val bitmap = decodeBitmap(Uri.parse(media.uri))
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
    }

    override suspend fun edit(media: PhotoEntity, operation: MediaEditOperation, strength: Float): String = withContext(Dispatchers.IO) {
        when {
            media.mimeType.startsWith("video/") -> editVideo(media, operation)
            media.mimeType.startsWith("image/") -> editImage(media, operation, strength)
            else -> error("Unsupported media type: ${media.mimeType}")
        }
    }

    override suspend fun editAdvanced(media: PhotoEntity, request: AdvancedEditRequest): String = withContext(Dispatchers.IO) {
        require(media.mimeType.startsWith("image/")) { "The layer editor currently exports image and GIF frames" }
        var source = decodeBitmap(Uri.parse(media.uri))
        source = when (request.backgroundRemoval) {
            BackgroundRemovalMode.NONE -> source
            BackgroundRemovalMode.AUTO -> autoRemoveBackground(source)
            BackgroundRemovalMode.MANUAL -> manualRemoveBackground(source, request.backgroundStrength.coerceIn(0.05f, 0.95f))
        }

        val left = (request.cropLeft.coerceIn(0f, 0.95f) * source.width).toInt()
        val top = (request.cropTop.coerceIn(0f, 0.95f) * source.height).toInt()
        val right = (request.cropRight.coerceIn(request.cropLeft + 0.02f, 1f) * source.width).toInt().coerceAtMost(source.width)
        val bottom = (request.cropBottom.coerceIn(request.cropTop + 0.02f, 1f) * source.height).toInt().coerceAtMost(source.height)
        val cropped = Bitmap.createBitmap(source, left, top, (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1))
        val output = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val matrix = Matrix().apply {
            postTranslate(-cropped.width / 2f, -cropped.height / 2f)
            postScale(request.scale.coerceIn(0.05f, 20f), request.scale.coerceIn(0.05f, 20f))
            postRotate(request.rotation)
            postTranslate(
                cropped.width / 2f + request.offsetX.coerceIn(-2f, 2f) * cropped.width,
                cropped.height / 2f + request.offsetY.coerceIn(-2f, 2f) * cropped.height,
            )
        }
        canvas.drawBitmap(cropped, matrix, imagePaint)
        request.textLayers.filter { it.text.isNotBlank() }.forEach { layer ->
            val x = layer.x.coerceIn(-1f, 2f) * output.width
            val y = layer.y.coerceIn(-1f, 2f) * output.height
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (output.width.coerceAtMost(output.height) * 0.075f * layer.scale.coerceIn(0.2f, 5f)).coerceAtLeast(16f)
                setShadowLayer(max(2f, textSize * 0.04f), 0f, max(1f, textSize * 0.02f), Color.BLACK)
            }
            canvas.save()
            canvas.rotate(layer.rotation, x, y)
            canvas.drawText(layer.text.take(120), x, y, paint)
            canvas.restore()
        }
        saveBitmapNamed(output, request.outputName, sanitizeTimestamp(media.dateTaken)).toString()
    }

    override suspend fun repair(media: PhotoEntity, preferredTimestamp: Long?): RepairReport = withContext(Dispatchers.IO) {
        val timestamp = sanitizeTimestamp(preferredTimestamp ?: media.dateTaken)
        val normalized = MediaFormatRegistry.normalizedMime(media.displayName, media.mimeType)
        return@withContext runCatching {
            val output = if (media.mimeType.startsWith("video/")) {
                remuxVideo(media, trimEndUs = Long.MAX_VALUE, mute = false, suffix = "repaired", dateTaken = timestamp)
            } else {
                val bitmap = decodeBitmap(Uri.parse(media.uri))
                saveBitmap(bitmap, media.displayName, "repaired", preserveAlpha = bitmap.hasAlpha(), dateTaken = timestamp)
            }
            RepairReport(true, "Recovered copy created: $output · MIME $normalized · date $timestamp")
        }.getOrElse { error ->
            RepairReport(false, "Repair could not recover this file: ${error.message ?: error::class.java.simpleName}")
        }
    }

    private suspend fun editImage(media: PhotoEntity, operation: MediaEditOperation, strength: Float): String {
        val source = decodeBitmap(Uri.parse(media.uri))
        val edited = when (operation) {
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
        return saveBitmap(edited, media.displayName, operation.name.lowercase(), alpha, sanitizeTimestamp(media.dateTaken)).toString()
    }

    private fun editVideo(media: PhotoEntity, operation: MediaEditOperation): String = when (operation) {
        MediaEditOperation.VIDEO_TRIM_FIRST_5_SECONDS -> remuxVideo(media, trimEndUs = 5_000_000L, mute = false, suffix = "trim5s", dateTaken = sanitizeTimestamp(media.dateTaken)).toString()
        MediaEditOperation.VIDEO_MUTE -> remuxVideo(media, trimEndUs = Long.MAX_VALUE, mute = true, suffix = "muted", dateTaken = sanitizeTimestamp(media.dateTaken)).toString()
        else -> error("Choose Trim first 5 seconds or Mute for video media")
    }

    private fun decodeBitmap(uri: Uri): Bitmap = context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Cannot open $uri" }
        BitmapFactory.decodeStream(input) ?: error("Android could not decode this image format")
    }

    private fun transform(source: Bitmap, matrix: Matrix): Bitmap = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)

    private fun cropSquare(source: Bitmap): Bitmap {
        val size = min(source.width, source.height)
        return Bitmap.createBitmap(source, (source.width - size) / 2, (source.height - size) / 2, size, size)
    }

    private fun grayscale(source: Bitmap): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(output.width * output.height)
        output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val y = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)).toInt().coerceIn(0, 255)
            pixels[i] = Color.argb(Color.alpha(c), y, y, y)
        }
        output.setPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
        return output
    }

    private suspend fun autoRemoveBackground(source: Bitmap): Bitmap {
        val mask = segmenter.process(InputImage.fromBitmap(source, 0)).await()
        val buffer = mask.buffer.order(ByteOrder.nativeOrder())
        buffer.rewind()
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
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
    }

    private fun manualRemoveBackground(source: Bitmap, strength: Float): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
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
    }

    private fun saveBitmap(bitmap: Bitmap, originalName: String, suffix: String, preserveAlpha: Boolean, dateTaken: Long): Uri {
        val base = originalName.substringBeforeLast('.', originalName).take(80)
        val mime = if (preserveAlpha) "image/png" else "image/jpeg"
        val extension = if (preserveAlpha) "png" else "jpg"
        return saveBitmapInternal(bitmap, "${base}_$suffix.$extension", mime, preserveAlpha, dateTaken)
    }

    private fun saveBitmapNamed(bitmap: Bitmap, requestedName: String, dateTaken: Long): Uri {
        val base = sanitizeDisplayName(requestedName).ifBlank { "KeepG_edit" }.substringBeforeLast('.', requestedName).take(100)
        return saveBitmapInternal(bitmap, "$base.png", "image/png", true, dateTaken)
    }

    private fun saveBitmapInternal(bitmap: Bitmap, displayName: String, mime: String, png: Boolean, dateTaken: Long): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.DATE_TAKEN, dateTaken)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KeepG")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Unable to create edited image")
        runCatching {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output)
                val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                check(bitmap.compress(format, 94, output)) { "Image encoder failed" }
            }
        }.onFailure { context.contentResolver.delete(uri, null, null) }.getOrThrow()
        return uri
    }

    private fun remuxVideo(media: PhotoEntity, trimEndUs: Long, mute: Boolean, suffix: String, dateTaken: Long): Uri {
        val inputUri = Uri.parse(media.uri)
        val temp = File.createTempFile("keepg_", ".mp4", context.cacheDir)
        val extractor = MediaExtractor()
        val afd = context.contentResolver.openAssetFileDescriptor(inputUri, "r") ?: error("Cannot open video")
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val muxer = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = mutableMapOf<Int, Int>()
            try {
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
                extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
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
            } finally {
                runCatching { muxer.stop() }
                muxer.release()
            }
        } finally {
            extractor.release()
            afd.close()
        }

        val base = media.displayName.substringBeforeLast('.', media.displayName).take(80)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${base}_$suffix.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, dateTaken)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/KeepG")
        }
        val outputUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Unable to create edited video")
        runCatching {
            context.contentResolver.openOutputStream(outputUri, "w").use { output ->
                requireNotNull(output)
                temp.inputStream().use { it.copyTo(output) }
            }
        }.onFailure { context.contentResolver.delete(outputUri, null, null) }.getOrThrow()
        temp.delete()
        return outputUri
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

    private fun sanitizeDisplayName(value: String): String = value.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").take(110)

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
