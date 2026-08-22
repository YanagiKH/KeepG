package com.yanagikh.keepg.data

import android.content.Context
import android.net.Uri
import com.yanagikh.keepg.security.VaultCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VaultRepository(private val context: Context, private val dao: KeepGDao, private val cipher: VaultCipher) {
    suspend fun importEncrypted(photo: PhotoEntity): Long = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "vault").apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}_${photo.mediaId}.kgv")
        context.contentResolver.openInputStream(Uri.parse(photo.uri)).use { input ->
            requireNotNull(input) { "Unable to open source image" }
            target.outputStream().use { output -> cipher.encrypt(input, output) }
        }
        dao.insertVaultItem(VaultItemEntity(originalMediaId = photo.mediaId, originalUri = photo.uri, displayName = photo.displayName, mimeType = photo.mimeType, encryptedPath = target.absolutePath))
    }

    suspend fun decryptToCache(item: VaultItemEntity): File = withContext(Dispatchers.IO) {
        val source = File(item.encryptedPath)
        require(source.exists()) { "Encrypted vault file is missing" }
        val target = File(context.cacheDir, "vault-preview-${item.id}")
        source.inputStream().use { input -> target.outputStream().use { output -> cipher.decrypt(input, output) } }
        target
    }

    suspend fun remove(item: VaultItemEntity) = withContext(Dispatchers.IO) {
        File(item.encryptedPath).delete()
        dao.deleteVaultItem(item.id)
    }
}
