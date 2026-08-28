package com.yanagikh.keepg.data

import android.content.Context
import com.yanagikh.keepg.security.PasswordHasher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

enum class AppLanguage { AUTO, ENGLISH, CHINESE, JAPANESE, KOREAN }
enum class MediaSortMode { DATE, NAME, SIZE, EXTENSION }
enum class MediaTypeFilter { ALL, IMAGES, VIDEOS, GIFS }
enum class MediaSizeFilter { ANY, SMALL, MEDIUM, LARGE }
enum class GridLayoutMode { SQUARE, PORTRAIT, ADAPTIVE }
enum class ThumbnailScaleMode { CROP, FIT }
enum class PreviewScaleMode { FIT, FILL }

data class GallerySettings(
    val videoPreviewAutoPlay: Boolean = true,
    val previewSwipeNavigation: Boolean = true,
    val deleteToTrash: Boolean = true,
    val hideSensitiveContent: Boolean = false,
    val language: AppLanguage = AppLanguage.AUTO,
    val sortMode: MediaSortMode = MediaSortMode.DATE,
    val sortDescending: Boolean = true,
    val gridColumns: Int = 3,
    val gridLayoutMode: GridLayoutMode = GridLayoutMode.SQUARE,
    val thumbnailScaleMode: ThumbnailScaleMode = ThumbnailScaleMode.CROP,
    val previewScaleMode: PreviewScaleMode = PreviewScaleMode.FIT,
    val showMediaBadges: Boolean = true,
    val animationsEnabled: Boolean = true,
    val cameraGridEnabled: Boolean = true,
    val cameraAudioEnabled: Boolean = true,
)

class GalleryPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("keepg_gallery", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<GallerySettings> = _settings
    private val _favorites = MutableStateFlow(loadLongSet(KEY_FAVORITES))
    val favorites: StateFlow<Set<Long>> = _favorites
    private val _sensitiveIds = MutableStateFlow(loadLongSet(KEY_SENSITIVE))
    val sensitiveIds: StateFlow<Set<Long>> = _sensitiveIds

    fun setVideoPreviewAutoPlay(value: Boolean) = updateSettings { it.copy(videoPreviewAutoPlay = value) }
    fun setPreviewSwipeNavigation(value: Boolean) = updateSettings { it.copy(previewSwipeNavigation = value) }
    fun setDeleteToTrash(value: Boolean) = updateSettings { it.copy(deleteToTrash = value) }
    fun setHideSensitiveContent(value: Boolean) = updateSettings { it.copy(hideSensitiveContent = value) }
    fun setLanguage(value: AppLanguage) = updateSettings { it.copy(language = value) }
    fun setSort(mode: MediaSortMode, descending: Boolean = _settings.value.sortDescending) = updateSettings { it.copy(sortMode = mode, sortDescending = descending) }
    fun setSortDescending(value: Boolean) = updateSettings { it.copy(sortDescending = value) }
    fun setGridColumns(value: Int) = updateSettings { it.copy(gridColumns = value.coerceIn(2, 8)) }
    fun setGridLayoutMode(value: GridLayoutMode) = updateSettings { it.copy(gridLayoutMode = value) }
    fun setThumbnailScaleMode(value: ThumbnailScaleMode) = updateSettings { it.copy(thumbnailScaleMode = value) }
    fun setPreviewScaleMode(value: PreviewScaleMode) = updateSettings { it.copy(previewScaleMode = value) }
    fun setShowMediaBadges(value: Boolean) = updateSettings { it.copy(showMediaBadges = value) }
    fun setAnimationsEnabled(value: Boolean) = updateSettings { it.copy(animationsEnabled = value) }
    fun setCameraGridEnabled(value: Boolean) = updateSettings { it.copy(cameraGridEnabled = value) }
    fun setCameraAudioEnabled(value: Boolean) = updateSettings { it.copy(cameraAudioEnabled = value) }

    fun toggleFavorite(mediaId: Long) {
        val next = _favorites.value.toMutableSet().apply { if (!add(mediaId)) remove(mediaId) }
        _favorites.value = next
        saveLongSet(KEY_FAVORITES, next)
    }

    fun addFavorites(mediaIds: Collection<Long>) {
        if (mediaIds.isEmpty()) return
        val next = _favorites.value + mediaIds
        if (next != _favorites.value) {
            _favorites.value = next
            saveLongSet(KEY_FAVORITES, next)
        }
    }

    fun setSensitive(mediaId: Long, sensitive: Boolean) {
        val next = _sensitiveIds.value.toMutableSet().apply { if (sensitive) add(mediaId) else remove(mediaId) }
        if (next != _sensitiveIds.value) {
            _sensitiveIds.value = next
            saveLongSet(KEY_SENSITIVE, next)
        }
    }

    fun hasDeletionPassword(): Boolean = prefs.contains(KEY_DELETE_HASH) && prefs.contains(KEY_DELETE_SALT)

    fun setDeletionPassword(password: String) {
        require(password.length >= 6) { "Deletion password must be at least 6 characters" }
        val encoded = PasswordHasher.create(password.toCharArray())
        prefs.edit().putString(KEY_DELETE_HASH, encoded.hash).putString(KEY_DELETE_SALT, encoded.salt).apply()
    }

    fun verifyDeletionPassword(password: String): Boolean {
        val hash = prefs.getString(KEY_DELETE_HASH, null) ?: return false
        val salt = prefs.getString(KEY_DELETE_SALT, null) ?: return false
        return runCatching { PasswordHasher.verify(password.toCharArray(), hash, salt) }.getOrDefault(false)
    }

    fun resolvedLanguage(): String = when (_settings.value.language) {
        AppLanguage.AUTO -> when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
            "zh" -> "zh"
            "ja" -> "ja"
            "ko" -> "ko"
            else -> "en"
        }
        AppLanguage.ENGLISH -> "en"
        AppLanguage.CHINESE -> "zh"
        AppLanguage.JAPANESE -> "ja"
        AppLanguage.KOREAN -> "ko"
    }

    private fun updateSettings(block: (GallerySettings) -> GallerySettings) {
        val next = block(_settings.value)
        _settings.value = next
        prefs.edit()
            .putBoolean(KEY_VIDEO_PREVIEW, next.videoPreviewAutoPlay)
            .putBoolean(KEY_PREVIEW_SWIPE, next.previewSwipeNavigation)
            .putBoolean(KEY_DELETE_TRASH, next.deleteToTrash)
            .putBoolean(KEY_HIDE_SENSITIVE, next.hideSensitiveContent)
            .putString(KEY_LANGUAGE, next.language.name)
            .putString(KEY_SORT_MODE, next.sortMode.name)
            .putBoolean(KEY_SORT_DESC, next.sortDescending)
            .putInt(KEY_GRID_COLUMNS, next.gridColumns)
            .putString(KEY_GRID_LAYOUT, next.gridLayoutMode.name)
            .putString(KEY_THUMBNAIL_SCALE, next.thumbnailScaleMode.name)
            .putString(KEY_PREVIEW_SCALE, next.previewScaleMode.name)
            .putBoolean(KEY_MEDIA_BADGES, next.showMediaBadges)
            .putBoolean(KEY_ANIMATIONS, next.animationsEnabled)
            .putBoolean(KEY_CAMERA_GRID, next.cameraGridEnabled)
            .putBoolean(KEY_CAMERA_AUDIO, next.cameraAudioEnabled)
            .apply()
    }

    private fun loadSettings() = GallerySettings(
        videoPreviewAutoPlay = prefs.getBoolean(KEY_VIDEO_PREVIEW, true),
        previewSwipeNavigation = prefs.getBoolean(KEY_PREVIEW_SWIPE, true),
        deleteToTrash = prefs.getBoolean(KEY_DELETE_TRASH, true),
        hideSensitiveContent = prefs.getBoolean(KEY_HIDE_SENSITIVE, false),
        language = enumValueOrDefault(prefs.getString(KEY_LANGUAGE, null), AppLanguage.AUTO),
        sortMode = enumValueOrDefault(prefs.getString(KEY_SORT_MODE, null), MediaSortMode.DATE),
        sortDescending = prefs.getBoolean(KEY_SORT_DESC, true),
        gridColumns = prefs.getInt(KEY_GRID_COLUMNS, 3).coerceIn(2, 8),
        gridLayoutMode = enumValueOrDefault(prefs.getString(KEY_GRID_LAYOUT, null), GridLayoutMode.SQUARE),
        thumbnailScaleMode = enumValueOrDefault(prefs.getString(KEY_THUMBNAIL_SCALE, null), ThumbnailScaleMode.CROP),
        previewScaleMode = enumValueOrDefault(prefs.getString(KEY_PREVIEW_SCALE, null), PreviewScaleMode.FIT),
        showMediaBadges = prefs.getBoolean(KEY_MEDIA_BADGES, true),
        animationsEnabled = prefs.getBoolean(KEY_ANIMATIONS, true),
        cameraGridEnabled = prefs.getBoolean(KEY_CAMERA_GRID, true),
        cameraAudioEnabled = prefs.getBoolean(KEY_CAMERA_AUDIO, true),
    )

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
        raw?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback

    private fun loadLongSet(key: String): Set<Long> = prefs.getStringSet(key, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    private fun saveLongSet(key: String, values: Set<Long>) = prefs.edit().putStringSet(key, values.map(Long::toString).toSet()).apply()

    private companion object {
        const val KEY_VIDEO_PREVIEW = "video_preview_auto_play"
        const val KEY_PREVIEW_SWIPE = "preview_swipe_navigation"
        const val KEY_DELETE_TRASH = "delete_to_trash"
        const val KEY_HIDE_SENSITIVE = "hide_sensitive"
        const val KEY_LANGUAGE = "language"
        const val KEY_SORT_MODE = "sort_mode"
        const val KEY_SORT_DESC = "sort_desc"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_GRID_LAYOUT = "grid_layout"
        const val KEY_THUMBNAIL_SCALE = "thumbnail_scale"
        const val KEY_PREVIEW_SCALE = "preview_scale"
        const val KEY_MEDIA_BADGES = "media_badges"
        const val KEY_ANIMATIONS = "animations"
        const val KEY_CAMERA_GRID = "camera_grid"
        const val KEY_CAMERA_AUDIO = "camera_audio"
        const val KEY_FAVORITES = "favorites"
        const val KEY_SENSITIVE = "sensitive_ids"
        const val KEY_DELETE_HASH = "delete_password_hash"
        const val KEY_DELETE_SALT = "delete_password_salt"
    }
}
