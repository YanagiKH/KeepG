package com.yanagikh.keepg.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

/** Non-destructive video trim/mute editor used by the interactive preview editor. */
class VideoEditRepository(private val context: Context) {
    suspend fun createEditedCopy(
        media: PhotoEntity,
        startMs: Long,
        endMs: Long,
        mute: Boolean,
    ): Uri = withContext(Dispatchers.IO) {
        require(media.mimeType.startsWith("video/")) { "A video is required" }
        require(endMs > startMs) { "Trim end must be after trim start" }
        val requestedStartUs = startMs.coerceAtLeast(0L) * 1_000L
        val requestedEndUs = endMs.coerceAtLeast(startMs + 1L) * 1_000L
        val inputUri = Uri.parse(media.uri)
        val temp = File.createTempFile("keepg_edit_", ".mp4", context.cacheDir)
        val extractor = MediaExtractor()
        val afd = context.contentResolver.openAssetFileDescriptor(inputUri, "r") ?: error("Cannot open video")
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val muxer = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = mutableMapOf<Int, Int>()
            var muxerStarted = false
            try {
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mute && mime.startsWith("audio/")) continue
                    if (!mime.startsWith("audio/") && !mime.startsWith("video/")) continue
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(format)
                }
                require(trackMap.isNotEmpty()) { "No compatible MP4 audio/video tracks" }
                muxer.start()
                muxerStarted = true
                extractor.seekTo(requestedStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val actualStartUs = extractor.sampleTime.takeIf { it >= 0L } ?: requestedStartUs
                val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    val sourceTrack = extractor.sampleTrackIndex
                    if (sourceTrack < 0) break
                    val timeUs = extractor.sampleTime
                    if (timeUs < 0L || timeUs > requestedEndUs) break
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    trackMap[sourceTrack]?.let { destinationTrack ->
                        info.set(
                            0,
                            size,
                            max(0L, timeUs - actualStartUs),
                            extractorFlagsToCodecFlags(extractor.sampleFlags),
                        )
                        muxer.writeSampleData(destinationTrack, buffer, info)
                    }
                    extractor.advance()
                }
            } finally {
                if (muxerStarted) runCatching { muxer.stop() }
                muxer.release()
            }
        } finally {
            extractor.release()
            afd.close()
        }

        val base = sanitizeName(media.displayName.substringBeforeLast('.', media.displayName)).ifBlank { "KeepG_video" }
        val suffix = buildString {
            append("trim_${startMs / 1000}-${endMs / 1000}s")
            if (mute) append("_muted")
        }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${base}_$suffix.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, media.dateTaken)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/KeepG")
        }
        val outputUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create edited video")
        try {
            context.contentResolver.openOutputStream(outputUri, "w").use { output ->
                requireNotNull(output) { "Unable to open edited video destination" }
                temp.inputStream().use { input -> input.copyTo(output) }
            }
        } catch (t: Throwable) {
            context.contentResolver.delete(outputUri, null, null)
            throw t
        } finally {
            temp.delete()
        }
        outputUri
    }

    private fun extractorFlagsToCodecFlags(sampleFlags: Int): Int {
        var result = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) result = result or MediaCodec.BUFFER_FLAG_KEY_FRAME
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) result = result or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        return result
    }

    private fun sanitizeName(value: String): String =
        value.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
}
