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
import java.io.IOException
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
        val requestedStartUs = millisecondsToMicroseconds(startMs.coerceAtLeast(0L))
        val requestedEndUs = millisecondsToMicroseconds(endMs)
        require(requestedEndUs > requestedStartUs) { "Trim end must be after trim start" }
        val inputUri = Uri.parse(media.uri)
        val temp = File.createTempFile("keepg_edit_", ".mp4", context.cacheDir)
        val resolver = context.contentResolver
        var outputUri: Uri? = null
        var operationFailure: Throwable? = null
        try {
            remuxToTemp(inputUri, temp, requestedStartUs, requestedEndUs, mute)
            val base = sanitizeName(media.displayName.substringBeforeLast('.', media.displayName)).ifBlank { "KeepG_video" }
            val suffix = buildString {
                append("trim_${startMs / 1000}-${endMs / 1000}s")
                if (mute) append("_muted")
            }
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${base}_$suffix.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_TAKEN, media.dateTaken)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/KeepG")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            outputUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create edited video")
            resolver.openOutputStream(requireNotNull(outputUri), "w").use { output ->
                requireNotNull(output) { "Unable to open edited video destination" }
                temp.inputStream().use { input -> input.copyTo(output) }
                output.flush()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val published = resolver.update(
                    requireNotNull(outputUri),
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
                check(published > 0) { "Unable to publish edited video" }
            }
        } catch (t: Throwable) {
            operationFailure = t
        }

        val tempCleanupFailure = runCatching {
            if (temp.exists() && !temp.delete()) throw IOException("Unable to delete temporary video ${temp.name}")
        }.exceptionOrNull()
        operationFailure = combineFailures(operationFailure, tempCleanupFailure)

        operationFailure?.let { failure ->
            outputUri?.let { uri ->
                val outputCleanupFailure = runCatching {
                    if (resolver.delete(uri, null, null) <= 0) {
                        throw IOException("Unable to delete incomplete edited video")
                    }
                }.exceptionOrNull()
                combineFailures(failure, outputCleanupFailure)
            }
            throw failure
        }
        requireNotNull(outputUri) { "Edited video was not created" }
    }

    private fun remuxToTemp(
        inputUri: Uri,
        temp: File,
        requestedStartUs: Long,
        requestedEndUs: Long,
        mute: Boolean,
    ) {
        val afd = context.contentResolver.openAssetFileDescriptor(inputUri, "r") ?: error("Cannot open video")
        afd.use { descriptor ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                remuxSelectedTracks(extractor, temp, requestedStartUs, requestedEndUs, mute)
            } finally {
                extractor.release()
            }
        }
    }

    private fun remuxSelectedTracks(
        extractor: MediaExtractor,
        temp: File,
        requestedStartUs: Long,
        requestedEndUs: Long,
        mute: Boolean,
    ) {
        val muxer = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var operationFailure: Throwable? = null
        try {
            val trackMap = mutableMapOf<Int, Int>()
            var declaredMaxInputSize = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mute && mime.startsWith("audio/")) continue
                if (!mime.startsWith("audio/") && !mime.startsWith("video/")) continue
                extractor.selectTrack(i)
                trackMap[i] = muxer.addTrack(format)
                declaredMaxInputSize = max(declaredMaxInputSize, format.maxInputSizeOrNull() ?: 0L)
            }
            require(trackMap.isNotEmpty()) { "No compatible MP4 audio/video tracks" }

            var buffer = allocateSampleBuffer(declaredMaxInputSize)
            val info = MediaCodec.BufferInfo()
            muxer.start()
            muxerStarted = true
            extractor.seekTo(requestedStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val actualStartUs = extractor.sampleTime.takeIf { it >= 0L } ?: requestedStartUs
            while (true) {
                val sourceTrack = extractor.sampleTrackIndex
                if (sourceTrack < 0) break
                val timeUs = extractor.sampleTime
                if (timeUs < 0L || timeUs > requestedEndUs) break
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val sampleSize = extractor.sampleSize
                    if (sampleSize > buffer.capacity().toLong()) buffer = allocateSampleBuffer(sampleSize)
                }
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                check(size <= buffer.capacity()) { "Video sample exceeds the allocated buffer" }
                trackMap[sourceTrack]?.let { destinationTrack ->
                    info.set(
                        0,
                        size,
                        max(0L, timeUs - actualStartUs),
                        extractorFlagsToCodecFlags(extractor.sampleFlags),
                    )
                    muxer.writeSampleData(destinationTrack, buffer, info)
                }
                if (!extractor.advance()) break
            }
        } catch (t: Throwable) {
            operationFailure = t
        }

        if (muxerStarted) {
            operationFailure = combineFailures(operationFailure, runCatching { muxer.stop() }.exceptionOrNull())
        }
        operationFailure = combineFailures(operationFailure, runCatching { muxer.release() }.exceptionOrNull())
        operationFailure?.let { throw it }
    }

    private fun MediaFormat.maxInputSizeOrNull(): Long? {
        if (!containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) return null
        return runCatching { getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).toLong() }
            .getOrNull()
            ?.takeIf { it > 0L }
    }

    private fun allocateSampleBuffer(requiredBytes: Long): ByteBuffer {
        val requestedBytes = requiredBytes.takeIf { it > 0L } ?: DEFAULT_SAMPLE_BUFFER_BYTES.toLong()
        require(requestedBytes <= MAX_SAMPLE_BUFFER_BYTES.toLong()) {
            "Video sample requires $requestedBytes bytes; the safe limit is $MAX_SAMPLE_BUFFER_BYTES bytes"
        }
        val capacity = requestedBytes.coerceAtLeast(MIN_SAMPLE_BUFFER_BYTES.toLong()).toInt()
        return ByteBuffer.allocate(capacity)
    }

    private fun combineFailures(primary: Throwable?, secondary: Throwable?): Throwable? {
        if (secondary == null) return primary
        if (primary == null) return secondary
        if (primary !== secondary) primary.addSuppressed(secondary)
        return primary
    }

    private fun millisecondsToMicroseconds(milliseconds: Long): Long =
        Math.multiplyExact(milliseconds, 1_000L)

    private fun extractorFlagsToCodecFlags(sampleFlags: Int): Int {
        var result = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) result = result or MediaCodec.BUFFER_FLAG_KEY_FRAME
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) result = result or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        return result
    }

    private fun sanitizeName(value: String): String = value
        .replace(Regex("[\\p{Cc}\\\\/:*?\"<>|]"), "_")
        .trim()
        .take(80)
        .trimStart('.', ' ')
        .trimEnd('.', ' ')
        .takeUnless { it == "." || it == ".." }
        .orEmpty()

    private companion object {
        const val MIN_SAMPLE_BUFFER_BYTES = 64 * 1024
        const val DEFAULT_SAMPLE_BUFFER_BYTES = 4 * 1024 * 1024
        const val MAX_SAMPLE_BUFFER_BYTES = 32 * 1024 * 1024
    }
}
