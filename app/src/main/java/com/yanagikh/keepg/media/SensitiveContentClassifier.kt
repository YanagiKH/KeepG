package com.yanagikh.keepg.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import com.yanagikh.keepg.data.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight, fully on-device safety heuristic used only when the user enables
 * sensitive-content hiding. It intentionally favors recall over precision and
 * must not be treated as a definitive moderation classifier.
 */
class SensitiveContentClassifier(private val context: Context) {
    suspend fun isLikelySensitive(media: PhotoEntity): Boolean = withContext(Dispatchers.IO) {
        val bitmap = loadPreview(media) ?: return@withContext false
        runCatching { score(bitmap) >= 0.58f }.getOrDefault(false)
    }

    private fun loadPreview(media: PhotoEntity): Bitmap? {
        val uri = Uri.parse(media.uri)
        return if (media.mimeType.startsWith("video/")) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } finally {
                runCatching { retriever.release() }
            }
        } else if (Build.VERSION.SDK_INT >= 29) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(192, 192), null) }.getOrNull()
        } else {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) null else BitmapFactory.decodeStream(input)
            }
        }
    }

    private fun score(source: Bitmap): Float {
        val maxSide = max(source.width, source.height).coerceAtLeast(1)
        val scale = min(1f, 128f / maxSide.toFloat())
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
        } else source

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
    }
}
