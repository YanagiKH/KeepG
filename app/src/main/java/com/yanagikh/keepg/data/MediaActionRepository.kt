package com.yanagikh.keepg.data

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.File

class MediaActionRepository(private val context: Context) {
    suspend fun prepareShare(media: List<PhotoEntity>, password: String?): Intent = withContext(Dispatchers.IO) {
        require(media.isNotEmpty()) { "Nothing selected" }
        if (!password.isNullOrBlank()) return@withContext prepareEncryptedShare(media, password)
        val uris = ArrayList(media.map { Uri.parse(it.uri) })
        if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = media.first().mimeType.ifBlank { "application/octet-stream" }
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = commonMime(media)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun createRemovalIntentSender(media: List<PhotoEntity>, moveToTrash: Boolean): IntentSender? {
        if (Build.VERSION.SDK_INT < 30 || media.isEmpty()) return null
        val uris = media.map { Uri.parse(it.uri) }
        val request: PendingIntent = if (moveToTrash) {
            MediaStore.createTrashRequest(context.contentResolver, uris, true)
        } else {
            MediaStore.createDeleteRequest(context.contentResolver, uris)
        }
        return request.intentSender
    }

    fun createTrashStateIntentSender(media: List<PhotoEntity>, trashed: Boolean): IntentSender? {
        if (Build.VERSION.SDK_INT < 30 || media.isEmpty()) return null
        return MediaStore.createTrashRequest(context.contentResolver, media.map { Uri.parse(it.uri) }, trashed).intentSender
    }

    fun createPermanentDeleteIntentSender(media: List<PhotoEntity>): IntentSender? {
        if (Build.VERSION.SDK_INT < 30 || media.isEmpty()) return null
        return MediaStore.createDeleteRequest(context.contentResolver, media.map { Uri.parse(it.uri) }).intentSender
    }

    suspend fun removeLegacy(media: List<PhotoEntity>): Int = withContext(Dispatchers.IO) {
        media.count { item -> runCatching { context.contentResolver.delete(Uri.parse(item.uri), null, null) > 0 }.getOrDefault(false) }
    }

    suspend fun rename(media: PhotoEntity, newDisplayName: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = sanitizeName(newDisplayName)
        if (trimmed.isBlank()) return@withContext false
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, trimmed) }
        runCatching { context.contentResolver.update(Uri.parse(media.uri), values, null, null) > 0 }.getOrDefault(false)
    }

    private fun prepareEncryptedShare(media: List<PhotoEntity>, password: String): Intent {
        require(password.length >= 6) { "Share password must be at least 6 characters" }
        val root = File(context.cacheDir, "shares").apply { mkdirs() }
        root.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach(File::deleteRecursively)
        val job = File(root, System.currentTimeMillis().toString()).apply { mkdirs() }
        val copied = media.mapIndexed { index, item ->
            val file = File(job, "${index + 1}_${sanitizeName(item.displayName).ifBlank { "media" }}")
            context.contentResolver.openInputStream(Uri.parse(item.uri)).use { input ->
                requireNotNull(input) { "Unable to open ${item.displayName}" }
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file
        }
        val zip = File(root, "keepg-share-${System.currentTimeMillis()}.zip")
        val parameters = ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
        ZipFile(zip, password.toCharArray()).addFiles(copied, parameters)
        job.deleteRecursively()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", zip)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "KeepG password-protected media")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun commonMime(media: List<PhotoEntity>): String = when {
        media.all { it.mimeType.startsWith("image/") } -> "image/*"
        media.all { it.mimeType.startsWith("video/") } -> "video/*"
        else -> "*/*"
    }

    private fun sanitizeName(value: String): String = value
        .replace(Regex("[\\p{Cc}\\\\/:*?\"<>|]"), "_")
        .trim()
        .take(120)
        .trimStart('.', ' ')
        .trimEnd('.', ' ')
        .takeUnless { it == "." || it == ".." }
        .orEmpty()
}
