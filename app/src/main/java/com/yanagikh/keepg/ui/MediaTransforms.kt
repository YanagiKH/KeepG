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
): Offset {
    if (viewportWidth <= 0 || viewportHeight <= 0 || mediaWidth <= 0 || mediaHeight <= 0) return Offset.Zero
    val safeScale = scale.coerceAtLeast(1f)
    val fitScale = min(viewportWidth / mediaWidth.toFloat(), viewportHeight / mediaHeight.toFloat())
    val drawnWidth = mediaWidth * fitScale * safeScale
    val drawnHeight = mediaHeight * fitScale * safeScale
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
