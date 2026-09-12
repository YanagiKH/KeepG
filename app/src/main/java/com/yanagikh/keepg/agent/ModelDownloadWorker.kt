package com.yanagikh.keepg.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.yanagikh.keepg.R
import com.yanagikh.keepg.data.GalleryPreferences
import com.yanagikh.keepg.ui.UiLocalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import com.yanagikh.keepg.agent.AgentPolicy.hex

/** Foreground, constraint-aware, resumable download pinned to an immutable HF commit and SHA-256. */
class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val language = GalleryPreferences(applicationContext).settings.value.language
        val title = UiLocalizer.text(language, "Downloading model")
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("keepg-models", title, NotificationManager.IMPORTANCE_LOW))
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, "keepg-models")
            .setSmallIcon(R.drawable.ic_keepg).setContentTitle(title)
            .setContentText(UiLocalizer.text(language, "KeepG downloads only the model; your media stays on this device."))
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, UiLocalizer.text(language, "Cancel"), cancel).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id.hashCode(), notification)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = ModelRepository(applicationContext)
        var partial: File? = null
        try {
            val model = ModelFile.fromJson(JSONObject(inputData.getString("model") ?: return@withContext Result.failure()))
            if (repository.installed().any { it.id == model.id }) return@withContext Result.success()
            setForeground(getForegroundInfo())
            val dir = repository.directory(model.id)
            val file = File(dir, "download.part").also { partial = it }
            if (file.length() > model.size) file.delete()
            var offset = file.length()
            if (dir.usableSpace < model.size - offset + 256L * 1024 * 1024) throw AgentFailure("Not enough storage")
            val encodedPath = model.fileName.split('/').joinToString("/") { ModelRepository.encode(it) }
            if (offset < model.size) {
                val connection = repository.connection("https://huggingface.co/${model.repository}/resolve/${model.revision}/$encodedPath?download=true", offset)
                try {
                    val code = connection.responseCode
                    when {
                        code in listOf(401, 403) -> throw AgentFailure("Model repository access denied")
                        code == 200 -> { offset = 0; file.delete() }
                        code == 206 && AgentPolicy.validContentRange(connection.getHeaderField("Content-Range"), offset, model.size) -> Unit
                        code == 429 || code >= 500 -> throw IOException("Retry download")
                        else -> throw AgentFailure("Invalid model download response")
                    }
                    val advertised = connection.getHeaderFieldLong("Content-Length", -1)
                    if (advertised > model.size - offset) throw AgentFailure("Invalid model download response")
                    connection.inputStream.use { input ->
                        FileOutputStream(file, offset > 0).use { output ->
                            val buffer = ByteArray(128 * 1024)
                            var lastUpdate = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                if (isStopped) throw CancellationException()
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (offset > model.size - count) throw AgentFailure("Invalid model download response")
                                output.write(buffer, 0, count)
                                offset += count
                                if (System.currentTimeMillis() - lastUpdate >= 1000) {
                                    setProgress(workDataOf("bytes" to offset, "total" to model.size))
                                    lastUpdate = System.currentTimeMillis()
                                }
                            }
                            output.fd.sync()
                        }
                    }
                } finally { connection.disconnect() }
            }
            if (file.length() != model.size) throw IOException("Incomplete download")
            setProgress(workDataOf("bytes" to model.size, "total" to model.size, "verifying" to true))
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            if (!digest.digest().hex().equals(model.sha256, true)) { file.delete(); throw AgentFailure("Model checksum mismatch") }
            currentCoroutineContext().ensureActive()
            repository.finishInstall(model.id, "${model.repository}/${model.fileName}", file, model.size, model.sha256)
            Result.success()
        } catch (cancelled: CancellationException) {
            // Keep verified-prefix bytes for explicit retry or a constraint-triggered resume.
            throw cancelled
        } catch (failure: AgentFailure) {
            if (failure.key == "Invalid model download response") partial?.delete()
            Result.failure(workDataOf("error" to failure.key))
        } catch (network: IOException) {
            if (runAttemptCount < 5) Result.retry() else Result.failure(workDataOf("error" to "Download interrupted; retry to resume"))
        } catch (error: Exception) {
            Result.failure(workDataOf("error" to "Unable to install model"))
        }
    }
}
