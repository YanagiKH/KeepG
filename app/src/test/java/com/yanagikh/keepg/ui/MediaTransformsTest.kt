package com.yanagikh.keepg.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
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
        assertEquals(-270f, clamped.y, 0.01f)
    }

    @Test
    fun portraitRotationReclampsExistingPan() {
        val before = clampMediaTranslation(1080, 1920, 1080, 1920, 4f, Offset(1400f, 2400f))
        val after = clampMediaTranslation(1920, 1080, 1080, 1920, 4f, before)
        assertEquals(1080f, after.x, 0.01f)
        assertEquals(1620f, after.y, 0.01f)
    }

    @Test
    fun normalizedZoomNeverDropsBelowOneOrAboveMaximum() {
        assertEquals(1f, normalizedZoom(1f, 0.01f, 12f), 0f)
        assertEquals(12f, normalizedZoom(6f, 10f, 12f), 0f)
    }
}
