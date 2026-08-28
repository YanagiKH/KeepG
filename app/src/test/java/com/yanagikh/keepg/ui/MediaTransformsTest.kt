package com.yanagikh.keepg.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MediaTransformsTest {
    @Test
    fun scaleOneAlwaysCentersMedia() {
        assertEquals(
            Offset.Zero,
            clampMediaTranslation(1080, 1920, 4000, 3000, 1f, Offset(500f, -900f)),
        )
    }

    @Test
    fun landscapeImagePanIsBoundedAfterZoom() {
        val clamped = clampMediaTranslation(1080, 1920, 4000, 2000, 3f, Offset(5000f, -5000f))
        assertEquals(1080f, clamped.x, 0.01f)
        assertEquals(0f, clamped.y, 0.01f)
    }

    @Test
    fun portraitRotationReclampsExistingPan() {
        val before = clampMediaTranslation(1080, 1920, 1080, 1920, 4f, Offset(1400f, 2400f))
        val after = clampMediaTranslation(1920, 1080, 1080, 1920, 4f, before)
        assertEquals(255f, after.x, 0.01f)
        assertEquals(1620f, after.y, 0.01f)
    }

    @Test
    fun normalizedZoomNeverDropsBelowOneOrAboveMaximum() {
        assertEquals(1f, normalizedZoom(1f, 0.01f, 12f), 0f)
        assertEquals(12f, normalizedZoom(6f, 10f, 12f), 0f)
    }

    @Test
    fun videoAspectRatioPreservesLandscapeAndPortrait() {
        assertEquals(16f / 9f, safeMediaAspectRatio(1920, 1080), 0.0001f)
        assertEquals(9f / 16f, safeMediaAspectRatio(1080, 1920), 0.0001f)
        assertEquals(1f, safeMediaAspectRatio(0, 0), 0f)
    }

    @Test
    fun previewSwipeRequiresDeliberateHorizontalMovement() {
        assertEquals(1, previewSwipeDirection(-260f, 20f, 1000f))
        assertEquals(-1, previewSwipeDirection(260f, 20f, 1000f))
        assertEquals(0, previewSwipeDirection(100f, 5f, 1000f))
        assertEquals(0, previewSwipeDirection(250f, 240f, 1000f))
    }

    @Test
    fun continuousGridPinchStepsBothDirections() {
        assertEquals(2, steppedGridColumns(3, 1.2f))
        assertEquals(4, steppedGridColumns(3, 0.8f))
        assertEquals(3, steppedGridColumns(3, 1.02f))
        assertEquals(2, steppedGridColumns(2, 1.5f))
        assertEquals(8, steppedGridColumns(8, 0.5f))
    }

    @Test
    fun originalCropExcludesViewportLetterboxing() {
        assertArrayEquals(
            floatArrayOf(.375f, 0f, .625f, 1f),
            cropRectV2("Original", 2000, 1000, 1000, 2000),
            0.0001f,
        )
    }

    @Test
    fun aspectPresetRemainsInsideFittedImage() {
        assertArrayEquals(
            floatArrayOf(.375f, .25f, .625f, .75f),
            cropRectV2("Square", 2000, 1000, 1000, 2000),
            0.0001f,
        )
        assertArrayEquals(
            floatArrayOf(1f / 6f, .375f, 5f / 6f, .625f),
            cropRectV2("4:3", 1000, 2000, 2000, 1000),
            0.0001f,
        )
    }
}
