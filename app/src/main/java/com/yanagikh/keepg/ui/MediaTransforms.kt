package com.yanagikh.keepg.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Pure geometry helpers shared by image and video previews. */
internal fun clampMediaTranslation(
    viewportWidth: Int,
    viewportHeight: Int,
    mediaWidth: Int,
    mediaHeight: Int,
    scale: Float,
    translation: Offset,
    fillViewport: Boolean = false,
): Offset {
    if (viewportWidth <= 0 || viewportHeight <= 0 || mediaWidth <= 0 || mediaHeight <= 0) return Offset.Zero
    val safeScale = scale.coerceAtLeast(1f)
    val baseScale = if (fillViewport) {
        max(viewportWidth / mediaWidth.toFloat(), viewportHeight / mediaHeight.toFloat())
    } else {
        min(viewportWidth / mediaWidth.toFloat(), viewportHeight / mediaHeight.toFloat())
    }
    val drawnWidth = mediaWidth * baseScale * safeScale
    val drawnHeight = mediaHeight * baseScale * safeScale
    val maxX = max(0f, (drawnWidth - viewportWidth) / 2f)
    val maxY = max(0f, (drawnHeight - viewportHeight) / 2f)
    return Offset(
        if (maxX == 0f) 0f else translation.x.coerceIn(-maxX, maxX),
        if (maxY == 0f) 0f else translation.y.coerceIn(-maxY, maxY),
    )
}

internal fun normalizedZoom(current: Float, factor: Float, maximum: Float = 12f): Float =
    (current * factor).coerceIn(1f, maximum.coerceAtLeast(1f))

internal fun safeMediaAspectRatio(width: Int, height: Int, pixelRatio: Float = 1f): Float {
    if (width <= 0 || height <= 0 || !pixelRatio.isFinite() || pixelRatio <= 0f) return 1f
    return ((width.toFloat() * pixelRatio) / height.toFloat()).coerceIn(0.05f, 20f)
}

/** Returns -1 for previous, +1 for next, and 0 when a gesture is not a deliberate horizontal swipe. */
internal fun previewSwipeDirection(deltaX: Float, deltaY: Float, viewportWidth: Float): Int {
    if (viewportWidth <= 0f) return 0
    val horizontal = abs(deltaX)
    if (horizontal < viewportWidth * 0.18f || horizontal <= abs(deltaY) * 1.35f) return 0
    return if (deltaX > 0f) -1 else 1
}

/** Continuous pinch helper: zooming in reduces columns; zooming out increases columns. */
internal fun steppedGridColumns(current: Int, accumulatedZoom: Float, minColumns: Int = 2, maxColumns: Int = 8): Int = when {
    accumulatedZoom >= 1.12f -> (current - 1).coerceIn(minColumns, maxColumns)
    accumulatedZoom <= 0.89f -> (current + 1).coerceIn(minColumns, maxColumns)
    else -> current.coerceIn(minColumns, maxColumns)
}

/** Returns a normalized crop rectangle contained inside the fitted source image. */
internal fun cropRectV2(
    mode: String,
    viewportWidth: Int,
    viewportHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int,
): FloatArray {
    if (viewportWidth <= 0 || viewportHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
        return floatArrayOf(0f, 0f, 1f, 1f)
    }
    val viewportAspect = viewportWidth.toFloat() / viewportHeight
    val sourceAspect = sourceWidth.toFloat() / sourceHeight
    val imageRect = if (viewportAspect > sourceAspect) {
        val width = sourceAspect / viewportAspect
        val inset = (1f - width) / 2f
        floatArrayOf(inset, 0f, 1f - inset, 1f)
    } else {
        val height = viewportAspect / sourceAspect
        val inset = (1f - height) / 2f
        floatArrayOf(0f, inset, 1f, 1f - inset)
    }
    if (mode == "Free" || mode == "Original") return imageRect
    val targetAspect = when (mode) {
        "Square" -> 1f
        "4:3" -> 4f / 3f
        "16:9" -> 16f / 9f
        else -> sourceAspect
    }
    val imageWidth = imageRect[2] - imageRect[0]
    val imageHeight = imageRect[3] - imageRect[1]
    return if (sourceAspect > targetAspect) {
        val cropWidth = imageHeight * viewportHeight * targetAspect / viewportWidth
        val left = (imageRect[0] + imageRect[2] - cropWidth) / 2f
        floatArrayOf(left, imageRect[1], left + cropWidth, imageRect[3])
    } else {
        val cropHeight = imageWidth * viewportWidth / targetAspect / viewportHeight
        val top = (imageRect[1] + imageRect[3] - cropHeight) / 2f
        floatArrayOf(imageRect[0], top, imageRect[2], top + cropHeight)
    }
}
