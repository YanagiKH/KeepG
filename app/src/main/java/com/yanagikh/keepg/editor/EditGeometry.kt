package com.yanagikh.keepg.editor

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Normalized viewport coordinates, independent of preview density and export resolution. */
data class CropBounds(val left: Float = 0f, val top: Float = 0f, val right: Float = 1f, val bottom: Float = 1f) {
    val width get() = right - left
    val height get() = bottom - top
    fun validate(): CropBounds = apply {
        require(listOf(left, top, right, bottom).all { it.isFinite() })
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f && width >= .0199f && height >= .0199f)
    }
    fun translated(dx: Float, dy: Float): CropBounds {
        val x = dx.coerceIn(-left, 1f - right)
        val y = dy.coerceIn(-top, 1f - bottom)
        return copy(left = (left + x).coerceIn(0f, 1f), right = (right + x).coerceIn(0f, 1f), top = (top + y).coerceIn(0f, 1f), bottom = (bottom + y).coerceIn(0f, 1f))
    }
}

enum class CropHandle { MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

object EditGeometry {
    fun preset(aspect: Float, viewportAspect: Float): CropBounds {
        require(aspect.isFinite() && viewportAspect.isFinite() && aspect > 0 && viewportAspect > 0)
        val ratio = aspect / viewportAspect
        val width = min(1f, ratio)
        val height = min(1f, 1f / ratio)
        return CropBounds((1 - width) / 2, (1 - height) / 2, (1 + width) / 2, (1 + height) / 2).validate()
    }
    fun hit(crop: CropBounds, x: Float, y: Float, slopX: Float, slopY: Float): CropHandle {
        val corners = listOf(CropHandle.TOP_LEFT to (crop.left to crop.top), CropHandle.TOP_RIGHT to (crop.right to crop.top),
            CropHandle.BOTTOM_LEFT to (crop.left to crop.bottom), CropHandle.BOTTOM_RIGHT to (crop.right to crop.bottom))
        return corners.firstOrNull { abs(x - it.second.first) <= slopX && abs(y - it.second.second) <= slopY }?.first ?: CropHandle.MOVE
    }
    fun drag(crop: CropBounds, handle: CropHandle, dx: Float, dy: Float, normalizedAspect: Float? = null): CropBounds {
        if (!dx.isFinite() || !dy.isFinite()) return crop
        if (handle == CropHandle.MOVE) return crop.translated(dx, dy)
        val left = handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT
        val top = handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT
        val anchorX = if (left) crop.right else crop.left
        val anchorY = if (top) crop.bottom else crop.top
        val availableWidth = if (left) anchorX else 1f - anchorX
        val availableHeight = if (top) anchorY else 1f - anchorY
        var width = (crop.width + if (left) -dx else dx).coerceIn(min(.02f, availableWidth), availableWidth)
        var height = (crop.height + if (top) -dy else dy).coerceIn(min(.02f, availableHeight), availableHeight)
        if (normalizedAspect != null && normalizedAspect.isFinite() && normalizedAspect in .05f..20f) {
            val maxWidth = if (left) anchorX else 1f - anchorX
            val maxHeight = if (top) anchorY else 1f - anchorY
            val minimumWidth = max(.02f, .02f * normalizedAspect)
            val maximumWidth = min(maxWidth, maxHeight * normalizedAspect)
            if (maximumWidth < minimumWidth) return crop
            width = (if (abs(dx) >= abs(dy) * normalizedAspect) width else height * normalizedAspect).coerceIn(minimumWidth, maximumWidth)
            height = width / normalizedAspect
        }
        return CropBounds(if (left) anchorX - width else anchorX, if (top) anchorY - height else anchorY,
            if (left) anchorX else anchorX + width, if (top) anchorY else anchorY + height)
    }
}

/** One gesture/slider movement is one history entry. Never stores decoded bitmaps. */
data class EditHistory<T>(val value: T, val past: List<T> = emptyList(), val future: List<T> = emptyList(), val beforeGesture: T? = null) {
    fun begin() = if (beforeGesture != null) this else copy(beforeGesture = value)
    fun preview(next: T) = copy(value = next)
    fun finish(): EditHistory<T> {
        val before = beforeGesture ?: return this
        return if (before == value) copy(beforeGesture = null) else copy(past = (past + before).takeLast(40), future = emptyList(), beforeGesture = null)
    }
    fun change(next: T): EditHistory<T> = finish().let { if (it.value == next) it else it.copy(value = next, past = (it.past + it.value).takeLast(40), future = emptyList()) }
    fun undo(): EditHistory<T> = finish().let { if (it.past.isEmpty()) it else it.copy(value = it.past.last(), past = it.past.dropLast(1), future = listOf(it.value) + it.future) }
    fun redo(): EditHistory<T> = finish().let { if (it.future.isEmpty()) it else it.copy(value = it.future.first(), past = (it.past + it.value).takeLast(40), future = it.future.drop(1)) }
}
