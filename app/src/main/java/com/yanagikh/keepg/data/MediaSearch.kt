package com.yanagikh.keepg.data

import java.util.Locale

internal fun matchesMediaSearch(
    media: PhotoEntity,
    query: String,
    recognizedText: String? = null,
    includeRecognizedText: Boolean = false,
): Boolean {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    if (normalizedQuery.isBlank()) return true
    val metadata = sequenceOf(
        media.displayName,
        media.bucketName,
        media.mimeType,
        media.displayName.substringAfterLast('.', ""),
        "${media.width}x${media.height}",
        media.sizeBytes.toString(),
    )
    if (metadata.any { fuzzyMatchScore(it.lowercase(Locale.ROOT), normalizedQuery) > 0 }) return true
    return includeRecognizedText && fuzzyMatchScore(recognizedText.orEmpty().lowercase(Locale.ROOT), normalizedQuery) > 0
}

internal fun fuzzyMatchScore(text: String, query: String): Int {
    if (query.isBlank()) return 1
    if (text == query) return 1_000
    if (text.startsWith(query)) return 800 - (text.length - query.length).coerceAtMost(200)
    val direct = text.indexOf(query)
    if (direct >= 0) return 600 - direct.coerceAtMost(200)
    var queryIndex = 0
    var gap = 0
    var lastMatch = -1
    text.forEachIndexed { index, character ->
        if (queryIndex < query.length && character == query[queryIndex]) {
            if (lastMatch >= 0) gap += index - lastMatch - 1
            lastMatch = index
            queryIndex++
        }
    }
    return if (queryIndex == query.length) (350 - gap).coerceAtLeast(1) else 0
}
