package com.yanagikh.keepg.editor

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class EditGeometryTest {
    @Test fun dragStaysInsideAndPreservesMinimumSize() {
        val random = Random(470)
        var crop = CropBounds(.2f, .2f, .8f, .8f)
        repeat(10_000) {
            crop = EditGeometry.drag(crop, CropHandle.entries[random.nextInt(5)], random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1)
            crop.validate()
        }
    }
    @Test fun ratioLockAndPresetUseViewportCoordinates() {
        val preset = EditGeometry.preset(1f, 2f)
        assertEquals(.5f, preset.width, .0001f)
        assertEquals(1f, preset.height, .0001f)
        val resized = EditGeometry.drag(CropBounds(.2f, .2f, .6f, .6f), CropHandle.BOTTOM_RIGHT, .2f, .3f, 1f)
        assertEquals(resized.width, resized.height, .0001f)
        resized.validate()
    }
    @Test fun oneGestureIsOneUndoAndNewEditDropsRedo() {
        var history = EditHistory(0).begin()
        repeat(100) { history = history.preview(it) }
        history = history.finish()
        assertEquals(listOf(0), history.past)
        assertEquals(0, history.undo().value)
        assertEquals(99, history.undo().redo().value)
        assertTrue(history.undo().change(5).future.isEmpty())
        repeat(100) { history = history.change(it) }
        assertTrue(history.past.size <= 40)
    }
    @Test fun nonFiniteGesturesAreIgnored() {
        val crop = CropBounds()
        assertEquals(crop, EditGeometry.drag(crop, CropHandle.MOVE, Float.NaN, 0f))
    }
    @Test fun videoBoundsAreValidatedAtExport() {
        VideoEditSpec(endMs = 1000).validate(1000)
        listOf(VideoEditSpec(endMs = 1001), VideoEditSpec(endMs = 0), VideoEditSpec(endMs = 1000, speed = Float.NaN),
            VideoEditSpec(endMs = 1000, height = 100), VideoEditSpec(endMs = 1000, crop = CropBounds(-1f))).forEach {
            assertTrue(runCatching { it.validate(1000) }.isFailure)
        }
    }
}
