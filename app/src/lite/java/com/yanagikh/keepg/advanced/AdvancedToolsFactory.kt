package com.yanagikh.keepg.advanced

import android.content.Context
import com.yanagikh.keepg.data.PhotoEntity

object AdvancedToolsFactory {
    fun create(context: Context): AdvancedFeatureTools = object : AdvancedFeatureTools {
        override val available: Boolean = false
        override suspend fun detectExternalLinks(media: PhotoEntity): List<DetectedExternalLink> = emptyList()
        override suspend fun edit(media: PhotoEntity, operation: MediaEditOperation, strength: Float): String =
            error("This operation is available in KeepG Full")
        override suspend fun repair(media: PhotoEntity, preferredTimestamp: Long?): RepairReport =
            RepairReport(false, "Advanced repair is available in KeepG Full")
    }
}
