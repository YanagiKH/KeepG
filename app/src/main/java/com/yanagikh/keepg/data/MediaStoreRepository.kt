package com.yanagikh.keepg.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.yanagikh.keepg.debug.KeepGLog
import com.yanagikh.keepg.media.MediaFormatRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(
    private val context: Context,
    private val dao: KeepGDao,
    private val log: KeepGLog? = null,
) {
    suspend fun refresh(): Int = withContext(Dispatchers.IO) {
        val scanStartedAt = System.currentTimeMillis()
        val imageScan = scanCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, scanStartedAt, isVideo = false)
        val videoScan = scanCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, scanStartedAt, isVideo = true)
        val media = imageScan.media + videoScan.media
        val completeScan = imageScan.succeeded && videoScan.succeeded
        dao.replaceScannedPhotos(media, scanStartedAt, pruneMissing = completeScan)
        if (!completeScan) log?.warn("MediaStore", "Partial scan retained the previous library instead of pruning entries")
        log?.info("MediaStore", "Indexed ${media.size} media items")
        media.size
    }

    suspend fun listTrashed(): List<PhotoEntity> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 30) return@withContext emptyList()
        val scanStartedAt = System.currentTimeMillis()
        buildList {
            addAll(scanCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, scanStartedAt, isVideo = false, trashedOnly = true).media)
            addAll(scanCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, scanStartedAt, isVideo = true, trashedOnly = true).media)
        }.sortedByDescending { it.dateTaken }
    }

    private fun scanCollection(
        collection: Uri,
        scanStartedAt: Long,
        isVideo: Boolean,
        trashedOnly: Boolean = false,
    ): ScanResult {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.Images.ImageColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.Images.ImageColumns.BUCKET_ID)
            add(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()
        val result = mutableListOf<PhotoEntity>()
        val succeeded = runCatching {
            val cursor = if (trashedOnly && Build.VERSION.SDK_INT >= 30) {
                val args = Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                }
                context.contentResolver.query(collection, projection, args, null)
            } else {
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.ImageColumns.DATE_TAKEN} DESC",
                )
            }
            requireNotNull(cursor) { "MediaStore returned no cursor for $collection" }.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN)
                val dateAddedCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val widthCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val bucketIdCol = it.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
                val bucketNameCol = it.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                val durationCol = if (isVideo) it.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
                while (it.moveToNext()) {
                    val rawId = it.getLong(idCol)
                    val stableId = if (isVideo) -(rawId + 1L) else rawId
                    val name = it.getString(nameCol) ?: "Media $rawId"
                    val reportedMime = it.getString(mimeCol)
                    val mime = MediaFormatRegistry.normalizedMime(name, reportedMime)
                    if (!MediaFormatRegistry.isSupportedMedia(name, mime)) continue
                    val taken = it.getLong(dateCol).takeIf { value -> value > 0L }
                        ?: (it.getLong(dateAddedCol) * 1_000L).takeIf { value -> value > 0L }
                        ?: scanStartedAt
                    result += PhotoEntity(
                        mediaId = stableId,
                        uri = ContentUris.withAppendedId(collection, rawId).toString(),
                        bucketId = it.getLong(bucketIdCol),
                        bucketName = it.getString(bucketNameCol) ?: "Unsorted",
                        displayName = name,
                        mimeType = mime,
                        dateTaken = taken,
                        width = it.getInt(widthCol),
                        height = it.getInt(heightCol),
                        sizeBytes = it.getLong(sizeCol).coerceAtLeast(0L),
                        durationMs = if (durationCol >= 0) it.getLong(durationCol).coerceAtLeast(0L) else 0L,
                        lastScannedAt = scanStartedAt,
                    )
                }
            }
        }.onFailure { log?.warn("MediaStore", "Unable to scan $collection", it) }.isSuccess
        return ScanResult(result, succeeded)
    }

    private data class ScanResult(val media: List<PhotoEntity>, val succeeded: Boolean)
}
