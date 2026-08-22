package com.yanagikh.keepg.media

import java.util.Locale

object MediaFormatRegistry {
    private val imageExtensions = setOf(
        "jpg", "jpeg", "jpe", "jfif", "png", "webp", "gif", "bmp", "dib", "heic", "heif", "avif", "dng", "tif", "tiff", "ico", "wbmp", "svg"
    )
    private val videoExtensions = setOf(
        "mp4", "m4v", "mov", "3gp", "3gpp", "3g2", "mkv", "webm", "avi", "ts", "mts", "m2ts", "mpg", "mpeg", "ogv"
    )

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    fun isKnownImage(name: String, mimeType: String?): Boolean = mimeType?.startsWith("image/") == true || extension(name) in imageExtensions
    fun isKnownVideo(name: String, mimeType: String?): Boolean = mimeType?.startsWith("video/") == true || extension(name) in videoExtensions
    fun isSupportedMedia(name: String, mimeType: String?): Boolean = isKnownImage(name, mimeType) || isKnownVideo(name, mimeType)

    fun normalizedMime(name: String, reported: String?): String {
        if (!reported.isNullOrBlank() && reported != "application/octet-stream") return reported
        return when (extension(name)) {
            "jpg", "jpeg", "jpe", "jfif" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp", "dib" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "avif" -> "image/avif"
            "dng" -> "image/x-adobe-dng"
            "tif", "tiff" -> "image/tiff"
            "ico" -> "image/x-icon"
            "svg" -> "image/svg+xml"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "3gp", "3gpp" -> "video/3gpp"
            "3g2" -> "video/3gpp2"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "ts", "mts", "m2ts" -> "video/mp2t"
            "mpg", "mpeg" -> "video/mpeg"
            "ogv" -> "video/ogg"
            else -> reported ?: "application/octet-stream"
        }
    }
}
