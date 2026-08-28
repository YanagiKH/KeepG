package com.yanagikh.keepg.ui

internal data class PreviewSession(
    val currentMediaId: Long,
    val orderedMediaIds: List<Long>,
)

internal fun createPreviewSession(
    currentMediaId: Long,
    orderedMediaIds: Iterable<Long>,
): PreviewSession? {
    val scope = orderedMediaIds.distinct()
    return if (currentMediaId in scope) PreviewSession(currentMediaId, scope) else null
}

internal fun PreviewSession.moveBy(delta: Int): PreviewSession? {
    val currentIndex = orderedMediaIds.indexOf(currentMediaId)
    if (currentIndex < 0) return null
    val targetMediaId = orderedMediaIds.getOrNull(currentIndex + delta) ?: return null
    return copy(currentMediaId = targetMediaId)
}

internal fun PreviewSession.retainAvailable(availableMediaIds: Set<Long>): PreviewSession? =
    createPreviewSession(currentMediaId, orderedMediaIds.filter(availableMediaIds::contains))
