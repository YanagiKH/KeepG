package com.yanagikh.keepg.advanced

import com.yanagikh.keepg.data.PhotoEntity

data class DetectedExternalLink(
    val value: String,
    val source: String,
)

data class RepairReport(
    val changed: Boolean,
    val summary: String,
)

enum class MediaEditOperation {
    ROTATE_RIGHT,
    FLIP_HORIZONTAL,
    GRAYSCALE,
    CROP_SQUARE,
    AUTO_BACKGROUND_REMOVAL,
    MANUAL_BACKGROUND_REMOVAL,
    EXTRACT_GIF_FRAME,
    VIDEO_TRIM_FIRST_5_SECONDS,
    VIDEO_MUTE,
}

interface AdvancedFeatureTools {
    val available: Boolean
    suspend fun detectExternalLinks(media: PhotoEntity): List<DetectedExternalLink>
    suspend fun edit(media: PhotoEntity, operation: MediaEditOperation, strength: Float = 0.35f): String
    suspend fun repair(media: PhotoEntity, preferredTimestamp: Long? = null): RepairReport
}
