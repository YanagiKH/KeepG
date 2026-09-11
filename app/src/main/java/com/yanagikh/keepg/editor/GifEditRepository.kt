package com.yanagikh.keepg.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.net.Uri
import com.yanagikh.keepg.advanced.AdvancedEditRequest
import com.yanagikh.keepg.data.PhotoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

@Suppress("DEPRECATION")
class GifEditRepository(private val context: Context) {
    class Source internal constructor(private val movie: Movie) {
        val duration = max(100, movie.duration().takeIf { it > 0 } ?: 1000)
        val aspect = movie.width().toFloat() / movie.height()
        fun frame(time: Int, edge: Int): Bitmap {
            require(edge in 64..640)
            val scale = minOf(1f, edge.toFloat() / max(movie.width(), movie.height()))
            val bitmap = Bitmap.createBitmap(max(1, (movie.width() * scale).toInt()), max(1, (movie.height() * scale).toInt()), Bitmap.Config.ARGB_8888)
            synchronized(movie) { movie.setTime(time.coerceIn(0, duration - 1)); Canvas(bitmap).apply { scale(scale, scale); movie.draw(this, 0f, 0f) } }
            return bitmap
        }
    }
    suspend fun open(photo: PhotoEntity): Source = withContext(Dispatchers.IO) {
        require(photo.mimeType == "image/gif")
        val bytes = context.contentResolver.openInputStream(Uri.parse(photo.uri))!!.use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(32 * 1024)
            while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= 16 * 1024 * 1024); output.write(buffer, 0, n) }
            output.toByteArray()
        }
        GifSafety.validate(bytes)
        Source(requireNotNull(Movie.decodeByteArray(bytes, 0, bytes.size)))
    }
    suspend fun export(photo: PhotoEntity, source: Source, timing: GifTiming, edit: AdvancedEditRequest, edge: Int, progress: (Float) -> Unit): Uri = withContext(Dispatchers.Default) {
        timing.validate(source.duration); edit.validate(); require(edge in 64..640)
        val job = currentCoroutineContext()
        ImageExport(context).write(edit.outputName, "gif", "image/gif", photo.dateTaken) { output ->
            var encoder: GifEncoder? = null
            try {
                repeat(timing.frames) { index ->
                    job.ensureActive()
                    val input = source.frame(timing.sourceTime(index), edge)
                    var rendered: Bitmap? = null
                    try {
                        rendered = ImageRenderer.render(input, edit.copy(maxEdge = edge))
                        val pixels = IntArray(rendered.width * rendered.height)
                        rendered.getPixels(pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)
                        val writer = encoder ?: GifEncoder(output, rendered.width, rendered.height, timing.loops).also { encoder = it }
                        writer.frame(pixels, timing.delayCs(index))
                    } finally { input.recycle(); rendered?.recycle() }
                    progress((index + 1f) / timing.frames)
                }
                job.ensureActive(); requireNotNull(encoder).close()
            } catch (error: Throwable) { throw error } // ImageExport removes incomplete output on failure/cancellation.
        }
    }
}
