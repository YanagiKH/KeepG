package com.yanagikh.keepg.ui

import androidx.compose.ui.geometry.Offset
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
