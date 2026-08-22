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
            addAll(scanCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, scanStartedAt))
            addAll(scanCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, scanStartedAt))
        }.distinctBy { it.mediaId }
        if (media.isNotEmpty()) dao.upsertPhotos(media)
        dao.prunePhotos(scanStartedAt)
        log?.info("MediaStore", "Indexed ${media.size} media items")
        media.size
    }

    private fun scanCollection(collection: Uri, scanStartedAt: Long): List<PhotoEntity> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.Images.ImageColumns.DATE_TAKEN,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Images.ImageColumns.BUCKET_ID,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
        )
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
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Media $id"
                    val reportedMime = cursor.getString(mimeCol)
                    val mime = MediaFormatRegistry.normalizedMime(name, reportedMime)
                    if (!MediaFormatRegistry.isSupportedMedia(name, mime)) continue
                    result += PhotoEntity(
                        mediaId = id,
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        bucketId = cursor.getLong(bucketIdCol),
                        bucketName = cursor.getString(bucketNameCol) ?: "Unsorted",
                        displayName = name,
                        mimeType = mime,
                        dateTaken = cursor.getLong(dateCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        lastScannedAt = scanStartedAt,
                    )
                }
            }
        }.onFailure { log?.warn("MediaStore", "Unable to scan $collection", it) }
        return result
    }
}
