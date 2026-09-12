package com.yanagikh.keepg.agent

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import androidx.work.*
import com.yanagikh.keepg.agent.AgentPolicy.hex
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class ModelFile(
    val repository: String, val fileName: String, val revision: String,
    val size: Long, val sha256: String,
) {
    val id: String get() = AgentPolicy.id("$repository@$revision/$fileName")
    fun validated(): ModelFile = apply {
        require(AgentPolicy.validRepository(repository) && AgentPolicy.validModelPath(fileName))
        require(AgentPolicy.validRevision(revision) && AgentPolicy.validHash(sha256))
        require(size in 1024..AgentPolicy.MAX_MODEL_BYTES)
    }
    fun toJson() = JSONObject().put("repository", repository).put("fileName", fileName)
        .put("revision", revision).put("size", size).put("sha256", sha256)
    companion object {
        fun fromJson(json: JSONObject) = ModelFile(json.getString("repository"), json.getString("fileName"),
            json.getString("revision"), json.getLong("size"), json.getString("sha256")).validated()
    }
}

internal data class InstalledModel(val id: String, val name: String, val path: String, val size: Long, val sha256: String)
internal data class ModelPreset(val label: String, val repository: String, val file: String)
internal class AgentFailure(val key: String) : IOException(key)

internal class ModelRepository(context: Context) {
    private val context = context.applicationContext
    val root = File(this.context.filesDir, "agent/models").apply { mkdirs() }
    private val preferences = this.context.getSharedPreferences("keepg_agent", Context.MODE_PRIVATE)
    val presets = listOf(
        ModelPreset("Gemma 4 E2B · Light", "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm"),
        ModelPreset("Gemma 4 E4B · Standard", "litert-community/gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm"),
    )
    var selectedId: String
        get() = preferences.getString("selected", "").orEmpty()
        set(value) { preferences.edit().putString("selected", value).apply() }
    var gpu: Boolean
        get() = preferences.getBoolean("gpu", false)
        set(value) { preferences.edit().putBoolean("gpu", value).apply() }
    var vision: Boolean
        get() = preferences.getBoolean("vision", false)
        set(value) { preferences.edit().putBoolean("vision", value).apply() }
    var temperature: Float
        get() = preferences.getFloat("temperature", .5f).coerceIn(0f, 1.5f)
        set(value) { preferences.edit().putFloat("temperature", value.coerceIn(0f, 1.5f)).apply() }

    fun directory(id: String): File {
        require(AgentPolicy.validHash(id))
        return File(root, id).apply { mkdirs() }
    }
    fun installed(): List<InstalledModel> = root.listFiles().orEmpty().filter { it.isDirectory && AgentPolicy.validHash(it.name) }.mapNotNull { dir ->
        runCatching {
            val json = JSONObject(File(dir, "installed.json").readText())
            val file = File(dir, "model.litertlm")
            val length = json.getLong("size")
            require(file.isFile && length in 1024..AgentPolicy.MAX_MODEL_BYTES && file.length() == length)
            InstalledModel(dir.name, json.getString("name"), file.absolutePath, length, json.getString("sha256"))
        }.getOrNull()
    }.sortedBy { it.name }

    fun finishInstall(id: String, name: String, file: File, size: Long, digest: String) {
        require(file.length() == size && AgentPolicy.validHash(digest))
        val dir = directory(id)
        val destination = File(dir, "model.litertlm")
        if (file != destination && !file.renameTo(destination)) throw AgentFailure("Unable to install model")
        writeAtomic(File(dir, "installed.json"), JSONObject().put("name", name.take(200)).put("size", size).put("sha256", digest).toString())
        if (selectedId.isBlank()) selectedId = id
    }

    fun remove(model: InstalledModel) {
        WorkManager.getInstance(context).cancelUniqueWork("model-${model.id}")
        directory(model.id).deleteRecursively()
        if (selectedId == model.id) selectedId = ""
    }

    /** This executes on an IO dispatcher. The SAF grant is used only for this explicit import. */
    fun importModel(uri: Uri, name: String): InstalledModel {
        require(name.endsWith(".litertlm", true)) { "Unsupported model format" }
        val staging = File.createTempFile("import-", ".part", root)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(staging).use { output ->
                    val limit = minOf(AgentPolicy.MAX_MODEL_BYTES, (root.usableSpace - 256L * 1024 * 1024).coerceAtLeast(0))
                    AgentPolicy.copyLimited(input, output, limit)
                    output.fd.sync()
                }
            } ?: throw AgentFailure("Unable to read attachment")
            require(staging.length() >= 1024) { "Unsupported model format" }
            val hash = hashFile(staging)
            val id = AgentPolicy.id("local:$hash")
            finishInstall(id, name, staging, staging.length(), hash)
            return installed().first { it.id == id }
        } finally { staging.delete() }
    }

    fun search(query: String): List<String> {
        val json = JSONArray(getJson("https://huggingface.co/api/models?search=${encode(query.take(160))}&limit=25&sort=downloads&direction=-1"))
        return (0 until json.length()).mapNotNull { json.optJSONObject(it)?.optString("id") }.filter(AgentPolicy::validRepository)
    }

    fun files(repository: String): List<ModelFile> {
        require(AgentPolicy.validRepository(repository))
        val json = JSONObject(getJson("https://huggingface.co/api/models/$repository?blobs=true"))
        val revision = json.getString("sha")
        require(AgentPolicy.validRevision(revision))
        val siblings = json.optJSONArray("siblings") ?: return emptyList()
        return (0 until siblings.length()).mapNotNull { index ->
            runCatching {
                val item = siblings.getJSONObject(index)
                val lfs = item.getJSONObject("lfs")
                ModelFile(repository, item.getString("rfilename"), revision, lfs.getLong("size"), lfs.getString("sha256")).validated()
            }.getOrNull()
        }
    }

    fun download(file: ModelFile, unmetered: Boolean) {
        file.validated()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setInputData(workDataOf("model" to file.toJson().toString()))
            .addTag("keepg-model").build()
        WorkManager.getInstance(context).enqueueUniqueWork("model-${file.id}", ExistingWorkPolicy.KEEP, request)
    }

    fun getJson(url: String): String {
        val connection = connection(url)
        try {
            if (connection.responseCode in listOf(401, 403)) throw AgentFailure("Model repository access denied")
            if (connection.responseCode != 200) throw AgentFailure("Model repository unavailable")
            return connection.inputStream.use { input -> ByteArrayOutputStream().also { AgentPolicy.copyLimited(input, it, AgentPolicy.MAX_METADATA_BYTES.toLong()) }.toString("UTF-8") }
        } finally { connection.disconnect() }
    }

    /** Never forward credentials to CDN redirects. Never follow clear-text redirects. */
    fun connection(url: String, offset: Long = 0): HttpURLConnection {
        var target = URL(url)
        repeat(8) {
            require(target.protocol == "https" && target.userInfo == null) { "HTTPS required" }
            val connection = (target.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 30_000
                setRequestProperty("Accept-Encoding", "identity")
                if (offset > 0) setRequestProperty("Range", "bytes=$offset-")
                if (target.host == "huggingface.co") token()?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            if (location == null) throw AgentFailure("Model repository unavailable")
            target = URL(target, location)
        }
        throw AgentFailure("Model repository unavailable")
    }

    fun saveToken(token: String) {
        if (token.isBlank()) { preferences.edit().remove("token").apply(); return }
        require(token.length <= 512 && token.startsWith("hf_") && token.none { it.isWhitespace() })
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, tokenKey()) }
        val bytes = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit().putString("token", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }
    fun hasToken() = preferences.contains("token")
    private fun token(): String? = runCatching {
        val raw = preferences.getString("token", null) ?: return null
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, tokenKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()
    private fun tokenKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("keepg-hf-token", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("keepg-hf-token", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object {
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        fun hashFile(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(128 * 1024); while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) } }
            return digest.digest().hex()
        }
        fun writeAtomic(file: File, text: String) {
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try { output.write(text.toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
            catch (error: Throwable) { atomic.failWrite(output); throw error }
        }
    }
}
