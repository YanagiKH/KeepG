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

enum class BackgroundRemovalMode { NONE, AUTO, MANUAL }

data class TextLayerSpec(
    val text: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

data class AdvancedEditRequest(
    val outputName: String,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val viewportAspectRatio: Float = 1f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val backgroundRemoval: BackgroundRemovalMode = BackgroundRemovalMode.NONE,
    val backgroundStrength: Float = 0.28f,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val textLayers: List<TextLayerSpec> = emptyList(),
)

interface AdvancedFeatureTools {
    val available: Boolean
    suspend fun recognizeText(media: PhotoEntity): String
    suspend fun detectExternalLinks(media: PhotoEntity, normalizedX: Float? = null, normalizedY: Float? = null): List<DetectedExternalLink>
    suspend fun edit(media: PhotoEntity, operation: MediaEditOperation, strength: Float = 0.35f): String
    suspend fun editAdvanced(media: PhotoEntity, request: AdvancedEditRequest): String
    suspend fun repair(media: PhotoEntity, preferredTimestamp: Long? = null): RepairReport
}
