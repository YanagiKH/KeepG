package com.yanagikh.keepg.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.yanagikh.keepg.data.ImageExportFormat
import java.io.OutputStream

/** Publish only a complete copy, never open the original for writing. */
class ImageExport(private val context: Context) {
    fun save(bitmap: Bitmap, name: String, format: ImageExportFormat, quality: Int, date: Long): Uri {
        require(quality in 40..100)
        val (extension, mime) = when (format) {
            ImageExportFormat.PNG -> "png" to "image/png"
            ImageExportFormat.JPEG -> "jpg" to "image/jpeg"
            ImageExportFormat.WEBP -> "webp" to "image/webp"
        }
        return write(name, extension, mime, date) { output ->
            // JPEG has no alpha channel. White is explicit, not a decoder-dependent black fill.
            val flattened = if (format == ImageExportFormat.JPEG) Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
            } else bitmap
            try {
                val encoder = when (format) {
                    ImageExportFormat.PNG -> Bitmap.CompressFormat.PNG
                    ImageExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    ImageExportFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                }
                check(flattened.compress(encoder, quality, output)) { "Image encoder failed" }
            } finally { if (flattened !== bitmap) flattened.recycle() }
        }
    }
    fun write(name: String, extension: String, mime: String, date: Long, writer: (OutputStream) -> Unit): Uri {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().substringBeforeLast('.').take(100).ifBlank { "KeepG_edit" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$clean.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.DATE_TAKEN, date.coerceAtLeast(0))
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KeepG"); put(MediaStore.Images.Media.IS_PENDING, 1) }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Unable to create edited image")
        var complete = false
        try {
            requireNotNull(resolver.openOutputStream(uri, "w")).use { writer(it); it.flush() }
            if (Build.VERSION.SDK_INT >= 29) check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
            complete = true
            return uri
        } finally { if (!complete) runCatching { resolver.delete(uri, null, null) } }
    }
}
