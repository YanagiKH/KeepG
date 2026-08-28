package com.yanagikh.keepg

import com.yanagikh.keepg.data.PhotoEntity
import com.yanagikh.keepg.data.matchesMediaSearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSearchTest {
    private val photo = PhotoEntity(
        mediaId = 7,
        uri = "content://media/7",
        bucketId = 3,
        bucketName = "Receipts",
        displayName = "IMG_20260828.jpg",
        mimeType = "image/jpeg",
        dateTaken = 1,
        width = 1200,
        height = 800,
        sizeBytes = 42_000,
    )

    @Test
    fun metadataSearchStillWorksWhenOcrIsDisabled() {
        assertTrue(matchesMediaSearch(photo, "receipts"))
        assertTrue(matchesMediaSearch(photo, "img2026"))
    }

    @Test
    fun recognizedTextOnlyParticipatesWhenEnabled() {
        val text = "Tokyo Station coffee receipt total 780 yen"
        assertFalse(matchesMediaSearch(photo, "coffee", text, includeRecognizedText = false))
        assertTrue(matchesMediaSearch(photo, "coffee", text, includeRecognizedText = true))
    }
}
