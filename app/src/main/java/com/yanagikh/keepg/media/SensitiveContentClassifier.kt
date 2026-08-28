package com.yanagikh.keepg.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.yanagikh.keepg.data.PhotoEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.coroutines.coroutineContext

internal const val SENSITIVE_PREVIEW_MAX_PIXELS = 4_194_304L
internal const val SENSITIVE_PREVIEW_MAX_SIDE = 4_096

/** Returns the smallest power-of-two sample that keeps decoded bounds within the limits. */
internal fun sensitivePreviewSampleSize(
    width: Int,
    height: Int,
    maxPixels: Long = SENSITIVE_PREVIEW_MAX_PIXELS,
    maxSide: Int = SENSITIVE_PREVIEW_MAX_SIDE,
): Int {
    if (width <= 0 || height <= 0 || maxPixels <= 0L || maxSide <= 0) return 1
    var sampleSize = 1
    while (true) {
        val sampledWidth = (width.toLong() + sampleSize - 1L) / sampleSize
        val sampledHeight = (height.toLong() + sampleSize - 1L) / sampleSize
        if (
            sampledWidth <= maxSide &&
            sampledHeight <= maxSide &&
            sampledWidth * sampledHeight <= maxPixels
        ) {
            return sampleSize
        }
        if (sampleSize >= (1 shl 30)) return sampleSize
        sampleSize = sampleSize shl 1
    }
}

/**
 * Lightweight, fully on-device safety heuristic used only when the user enables
 * sensitive-content hiding. It intentionally favors recall over precision and
 * must not be treated as a definitive moderation classifier.
 */
class SensitiveContentClassifier(private val context: Context) {
    suspend fun isLikelySensitive(media: PhotoEntity): Boolean = withContext(Dispatchers.IO) {
        var source: Bitmap? = null
        try {
            coroutineContext.ensureActive()
            source = loadPreview(media) ?: return@withContext false
            coroutineContext.ensureActive()
            score(source) >= 0.58f
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: OutOfMemoryError) {
            false
        } catch (_: Exception) {
            false
        } finally {
            source?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun loadPreview(media: PhotoEntity): Bitmap? {
        val uri = Uri.parse(media.uri)
        return if (media.mimeType.startsWith("video/")) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val sampleSize = sensitivePreviewSampleSize(width, height)
                val targetWidth = ((width.toLong() + sampleSize - 1L) / sampleSize).toInt().coerceAtLeast(1)
                val targetHeight = ((height.toLong() + sampleSize - 1L) / sampleSize).toInt().coerceAtLeast(1)
                if (Build.VERSION.SDK_INT >= 27 && width > 0 && height > 0) {
                    retriever.getScaledFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        targetWidth,
                        targetHeight,
                    )
                } else {
                    constrainPreview(retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
                }
            } finally {
                runCatching { retriever.release() }
            }
        } else {
            loadSampledImage(uri)
        }
    }

    private fun loadSampledImage(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sensitivePreviewSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun constrainPreview(source: Bitmap?): Bitmap? {
        source ?: return null
        val sampleSize = sensitivePreviewSampleSize(source.width, source.height)
        if (sampleSize == 1) return source
        var scaled: Bitmap? = null
        return try {
            scaled = Bitmap.createScaledBitmap(
                source,
                ((source.width.toLong() + sampleSize - 1L) / sampleSize).toInt().coerceAtLeast(1),
                ((source.height.toLong() + sampleSize - 1L) / sampleSize).toInt().coerceAtLeast(1),
                true,
            )
            scaled
        } finally {
            if (scaled !== source && !source.isRecycled) source.recycle()
        }
    }

    private fun score(source: Bitmap): Float {
        val maxSide = max(source.width, source.height).coerceAtLeast(1)
        val scale = min(1f, 128f / maxSide.toFloat())
        var scaled: Bitmap? = null
        try {
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    source,
                    (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1),
                    true,
                ).also { scaled = it }
            } else {
                source
            }

            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var skinLike = 0
            var deepRed = 0
            var darkRed = 0
            var valid = 0
            pixels.forEach { color ->
                val a = color ushr 24 and 0xff
                if (a < 32) return@forEach
                valid++
                val r = color ushr 16 and 0xff
                val g = color ushr 8 and 0xff
                val b = color and 0xff
                val hi = max(r, max(g, b))
                val lo = min(r, min(g, b))
                val skin = r > 95 && g > 38 && b > 20 && hi - lo > 15 && abs(r - g) > 12 && r > g && r > b
                if (skin) skinLike++
                if (r > 135 && r > g * 1.35f && r > b * 1.35f && g < 125) deepRed++
                if (r > 75 && r > g * 1.45f && r > b * 1.35f && hi - lo > 35) darkRed++
            }
            if (valid == 0) return 0f
            val skinRatio = skinLike.toFloat() / valid
            val goreRatio = (deepRed + darkRed * 0.55f) / valid.toFloat()
            val skinRisk = ((skinRatio - 0.22f) / 0.42f).coerceIn(0f, 1f)
            val goreRisk = ((goreRatio - 0.10f) / 0.32f).coerceIn(0f, 1f)
            return max(skinRisk, goreRisk)
        } finally {
            scaled?.let { if (!it.isRecycled) it.recycle() }
        }
    }
}
