package com.yanagikh.keepg.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
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
        val media = buildList {
            addAll(scanCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, scanStartedAt, isVideo = false))
            addAll(scanCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, scanStartedAt, isVideo = true))
        }
        if (media.isNotEmpty()) dao.upsertPhotos(media)
        dao.prunePhotos(scanStartedAt)
        log?.info("MediaStore", "Indexed ${media.size} media items")
        media.size
    }

    private fun scanCollection(collection: Uri, scanStartedAt: Long, isVideo: Boolean): List<PhotoEntity> {
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
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.ImageColumns.DATE_TAKEN} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                val durationCol = if (isVideo) cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
                while (cursor.moveToNext()) {
                    val rawId = cursor.getLong(idCol)
                    val stableId = if (isVideo) -(rawId + 1L) else rawId
                    val name = cursor.getString(nameCol) ?: "Media $rawId"
                    val reportedMime = cursor.getString(mimeCol)
                    val mime = MediaFormatRegistry.normalizedMime(name, reportedMime)
                    if (!MediaFormatRegistry.isSupportedMedia(name, mime)) continue
                    val taken = cursor.getLong(dateCol).takeIf { it > 0L }
                        ?: (cursor.getLong(dateAddedCol) * 1_000L).takeIf { it > 0L }
                        ?: scanStartedAt
                    result += PhotoEntity(
                        mediaId = stableId,
                        uri = ContentUris.withAppendedId(collection, rawId).toString(),
                        bucketId = cursor.getLong(bucketIdCol),
                        bucketName = cursor.getString(bucketNameCol) ?: "Unsorted",
                        displayName = name,
                        mimeType = mime,
                        dateTaken = taken,
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L),
                        durationMs = if (durationCol >= 0) cursor.getLong(durationCol).coerceAtLeast(0L) else 0L,
                        lastScannedAt = scanStartedAt,
                    )
                }
            }
        }.onFailure { log?.warn("MediaStore", "Unable to scan $collection", it) }
        return result
    }
}
