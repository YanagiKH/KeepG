package com.yanagikh.keepg.debug

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KeepGLog(private val context: Context) {
    private val preferences = context.getSharedPreferences("keepg_debug", Context.MODE_PRIVATE)
    private val logFile = File(context.filesDir, "debug/keepg.log")
    val enabled: Boolean get() = preferences.getBoolean("enabled", false)

    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean("enabled", value).apply()
        if (value) info("Debug", "Debug logging enabled")
    }

    fun debug(tag: String, message: String) = write("D", tag, message, null)
    fun info(tag: String, message: String) = write("I", tag, message, null)
    fun warn(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)
    fun error(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    fun snapshot(maxLines: Int = 250): String {
        if (!logFile.exists()) return "No KeepG debug log has been written yet."
        return logFile.readLines().takeLast(maxLines).joinToString("\n")
    }

    fun exportTo(resolver: ContentResolver, destination: Uri) {
        destination.let { uri ->
            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "Unable to open debug log destination" }
                if (logFile.exists()) logFile.inputStream().use { it.copyTo(output) }
            }
        }
    }

    fun clear() {
        if (logFile.exists()) logFile.writeText("")
    }

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        when (level) {
            "E" -> Log.e("KeepG/$tag", message, error)
            "W" -> Log.w("KeepG/$tag", message, error)
            "I" -> Log.i("KeepG/$tag", message)
            else -> Log.d("KeepG/$tag", message)
        }
        if (!enabled && level != "E") return
        runCatching {
            logFile.parentFile?.mkdirs()
            rotateIfNeeded()
            val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
            val suffix = error?.let { " | ${it::class.java.simpleName}: ${it.message}" }.orEmpty()
            logFile.appendText("$stamp $level/$tag: $message$suffix\n")
        }
    }

    private fun rotateIfNeeded() {
        if (logFile.exists() && logFile.length() > 2L * 1024L * 1024L) {
            val old = File(logFile.parentFile, "keepg.log.1")
            if (old.exists()) old.delete()
            logFile.renameTo(old)
        }
    }
}
