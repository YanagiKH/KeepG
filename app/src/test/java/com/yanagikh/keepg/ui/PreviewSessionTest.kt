package com.yanagikh.keepg.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewSessionTest {
    @Test
    fun albumNavigationUsesOnlyTheDisplayedAlbumOrder() {
        val globalOrder = listOf(101L, 202L, 103L)
        val albumMediaIds = setOf(101L, 103L)
        val displayedAlbumOrder = globalOrder.filter(albumMediaIds::contains)
        val session = requireNotNull(createPreviewSession(101L, displayedAlbumOrder))

        val next = requireNotNull(session.moveBy(1))

        assertEquals(103L, next.currentMediaId)
        assertEquals(listOf(101L, 103L), next.orderedMediaIds)
        assertNull(next.moveBy(1))
    }

    @Test
    fun favoriteAndCollectionScopesRetainTheirOwnOrder() {
        val favorites = requireNotNull(createPreviewSession(8L, listOf(8L, 3L, 12L)))
        val collection = requireNotNull(createPreviewSession(40L, listOf(40L, 10L, 30L)))

        assertEquals(3L, favorites.moveBy(1)?.currentMediaId)
        assertEquals(10L, collection.moveBy(1)?.currentMediaId)
        assertEquals(40L, collection.moveBy(1)?.moveBy(-1)?.currentMediaId)
    }

    @Test
    fun movingRetainsTheCompleteSessionForUnlockContinuation() {
        val session = requireNotNull(createPreviewSession(1L, listOf(1L, 2L, 3L)))

        val lockedTargetSession = requireNotNull(session.moveBy(1))

        assertEquals(2L, lockedTargetSession.currentMediaId)
        assertEquals(session.orderedMediaIds, lockedTargetSession.orderedMediaIds)
    }

    @Test
    fun unavailableItemsAreSkippedAndMissingCurrentItemClosesSession() {
        val session = requireNotNull(createPreviewSession(2L, listOf(1L, 2L, 3L, 4L)))

        val retained = requireNotNull(session.retainAvailable(setOf(2L, 4L)))

        assertEquals(listOf(2L, 4L), retained.orderedMediaIds)
        assertEquals(4L, retained.moveBy(1)?.currentMediaId)
        assertNull(session.retainAvailable(setOf(1L, 3L, 4L)))
    }

    @Test
    fun invalidOrDuplicateScopeIsNormalizedSafely() {
        assertNull(createPreviewSession(9L, listOf(1L, 2L, 3L)))

        val session = requireNotNull(createPreviewSession(2L, listOf(1L, 2L, 2L, 3L)))

        assertEquals(listOf(1L, 2L, 3L), session.orderedMediaIds)
        assertEquals(1L, session.moveBy(-1)?.currentMediaId)
    }
}
