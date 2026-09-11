@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.yanagikh.keepg.editor

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.*
import com.yanagikh.keepg.data.PhotoEntity
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Native Media3 decoding/re-encoding for precise trims; an original is never opened for writing. */
class VideoExporter(private val context: Context) {
    companion object {
        fun spatialEffects(spec: VideoEditSpec): List<Effect> = buildList {
            spec.crop.let { c -> if (c != CropBounds()) add(Crop(c.left * 2 - 1, c.right * 2 - 1, 1 - c.bottom * 2, 1 - c.top * 2)) }
            // Always request a render pass, including a zero-degree edit: no keyframe-only fast trim.
            add(ScaleAndRotateTransformation.Builder().setRotationDegrees(spec.rotation)
                .setScale(if (spec.flipX) -1f else 1f, if (spec.flipY) -1f else 1f).build())
            if (spec.height > 0) add(Presentation.createForHeight(spec.height))
        }
    }
    suspend fun export(photo: PhotoEntity, spec: VideoEditSpec, progress: (Float) -> Unit): Uri {
        require(photo.mimeType.startsWith("video/"))
        val (duration, hasAudio) = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(photo.uri))
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: photo.durationMs) to
                    (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes")
            } finally { retriever.release() }
        }
        spec.validate(duration)
        val temp = withContext(Dispatchers.IO) { File.createTempFile("keepg_video_", ".mp4", context.cacheDir).also { check(it.delete()) } }
        try {
            withTimeout(20 * 60 * 1000L) { transform(photo, spec, hasAudio, temp, progress) }
            return publish(photo, spec, temp)
        } finally { withContext(NonCancellable + Dispatchers.IO) { temp.delete() } }
    }
    private suspend fun transform(photo: PhotoEntity, spec: VideoEditSpec, hasAudio: Boolean, temp: File, progress: (Float) -> Unit) = withContext(Dispatchers.Main.immediate) {
        coroutineScope {
            val audio = mutableListOf<AudioProcessor>()
            val effects = spatialEffects(spec).toMutableList()
            if (spec.speed != 1f) {
                if (hasAudio && !spec.mute) {
                    val pair = Effects.createExperimentalSpeedChangingEffect(object : SpeedProvider {
                        override fun getSpeed(timeUs: Long): Float = spec.speed
                        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
                    })
                    audio.add(pair.first); effects.add(pair.second)
                } else effects.add(SpeedChangeEffect(spec.speed))
            }
            val media = MediaItem.Builder().setUri(photo.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(spec.startMs).setEndPositionMs(spec.endMs).build()).build()
            val edited = EditedMediaItem.Builder(media).setRemoveAudio(spec.mute).setEffects(Effects(audio, effects)).build()
            var transformer: Transformer? = null
            var poll: Job? = null
            try {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val instance = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (continuation.isActive) continuation.resume(Unit)
                            }
                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (continuation.isActive) continuation.resumeWithException(exportException)
                            }
                        }).build()
                    transformer = instance
                    try {
                        instance.start(edited, temp.absolutePath)
                        poll = launch {
                            val holder = ProgressHolder()
                            while (isActive) {
                                if (instance.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress / 100f)
                                delay(250)
                            }
                        }
                    } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            } finally {
                // Main dispatcher ensures cancellation finishes before the temporary file is deleted.
                poll?.cancel(); transformer?.cancel()
            }
        }
    }
    private suspend fun publish(photo: PhotoEntity, spec: VideoEditSpec, file: File): Uri = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive(); check(file.length() > 0)
        val resolver = context.contentResolver
        val clean = spec.outputName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim('.', ' ').take(100).ifBlank { "KeepG_video" }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$clean.mp4"); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, photo.dateTaken)
            if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/KeepG"); put(MediaStore.Video.Media.IS_PENDING, 1) }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Unable to create edited video")
        var complete = false
        try {
            requireNotNull(resolver.openOutputStream(uri, "w")).use { output -> file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { currentCoroutineContext().ensureActive(); val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n) }
                output.flush()
            } }
            currentCoroutineContext().ensureActive()
            if (Build.VERSION.SDK_INT >= 29) check(resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) == 1)
            complete = true; uri
        } finally { if (!complete) runCatching { resolver.delete(uri, null, null) } }
    }
}
