package com.yanagikh.keepg.advanced

import com.yanagikh.keepg.data.PhotoEntity
import com.yanagikh.keepg.data.ImageExportFormat
import android.graphics.Bitmap
import com.yanagikh.keepg.editor.CropBounds

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
    val colorArgb: Int = -1,
    val bold: Boolean = false,
    val shadow: Boolean = true,
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
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val exportFormat: ImageExportFormat = ImageExportFormat.PNG,
    val exportQuality: Int = 92,
    val maxEdge: Int = 4096,
) {
    val crop get() = CropBounds(cropLeft, cropTop, cropRight, cropBottom)
    fun withCrop(bounds: CropBounds) = copy(cropLeft = bounds.left, cropTop = bounds.top, cropRight = bounds.right, cropBottom = bounds.bottom)
    fun validate(): AdvancedEditRequest = apply {
        require(listOf(offsetX, offsetY, scale, rotation, viewportAspectRatio, backgroundStrength, brightness, contrast, saturation).all { it.isFinite() })
        require(offsetX in -2f..2f && offsetY in -2f..2f && scale in .05f..20f && viewportAspectRatio in .05f..20f)
        require(brightness in -1f..1f && contrast in .25f..2.5f && saturation in 0f..2f && backgroundStrength in .05f.. .95f)
        require(exportQuality in 40..100 && maxEdge in 64..4096)
        crop.validate()
        require(textLayers.size <= 30)
        textLayers.forEach { require(it.text.length <= 120 && listOf(it.x, it.y, it.scale, it.rotation).all(Float::isFinite) && it.scale in .2f..5f) }
    }
}

interface AdvancedFeatureTools {
    val available: Boolean
    suspend fun recognizeText(media: PhotoEntity): String
    suspend fun detectExternalLinks(media: PhotoEntity, normalizedX: Float? = null, normalizedY: Float? = null): List<DetectedExternalLink>
    suspend fun edit(media: PhotoEntity, operation: MediaEditOperation, strength: Float = 0.35f): String
    suspend fun editAdvanced(media: PhotoEntity, request: AdvancedEditRequest): String
    suspend fun previewSource(media: PhotoEntity, mode: BackgroundRemovalMode, strength: Float): Bitmap? = null
    suspend fun repair(media: PhotoEntity, preferredTimestamp: Long? = null): RepairReport
}
