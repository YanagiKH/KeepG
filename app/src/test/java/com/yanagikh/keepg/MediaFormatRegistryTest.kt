package com.yanagikh.keepg

import com.yanagikh.keepg.media.MediaFormatRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFormatRegistryTest {
    @Test fun recognizesExtendedImageFormats() {
        listOf("photo.jfif", "photo.heic", "photo.avif", "raw.dng", "scan.tiff", "icon.svg").forEach {
            assertTrue(it, MediaFormatRegistry.isKnownImage(it, null))
        }
    }

    @Test fun recognizesExtendedVideoFormats() {
        listOf("clip.mov", "clip.mkv", "clip.webm", "clip.avi", "clip.m2ts", "clip.ogv").forEach {
            assertTrue(it, MediaFormatRegistry.isKnownVideo(it, null))
        }
    }

    @Test fun normalizesMissingMimeTypes() {
        assertEquals("image/avif", MediaFormatRegistry.normalizedMime("sample.avif", null))
        assertEquals("video/x-matroska", MediaFormatRegistry.normalizedMime("sample.mkv", "application/octet-stream"))
        assertFalse(MediaFormatRegistry.isSupportedMedia("notes.txt", "text/plain"))
    }
}
