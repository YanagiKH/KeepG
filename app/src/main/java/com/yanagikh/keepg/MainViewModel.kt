package com.yanagikh.keepg

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yanagikh.keepg.advanced.AdvancedEditRequest
import com.yanagikh.keepg.advanced.DetectedExternalLink
import com.yanagikh.keepg.advanced.MediaEditOperation
import com.yanagikh.keepg.data.*
import com.yanagikh.keepg.security.PasswordHasher
import com.yanagikh.keepg.smart.SmartRuleEvaluator
import com.yanagikh.keepg.ui.UiLocalizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

data class TextIndexProgress(
    val processed: Int,
    val total: Int,
    val failures: Int = 0,
)

private data class MediaFilterState(
    val query: String,
    val type: MediaTypeFilter,
    val size: MediaSizeFilter,
    val extension: String,
    val includeImageText: Boolean,
    val bucketId: Long?,
)

class MainViewModel(private val container: AppContainer) : ViewModel() {
    val photos = container.dao.observePhotos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locks = container.dao.observeLocks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val faces = container.dao.observeFaces().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val people = container.dao.observePeople().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rules = container.dao.observeRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val vault = container.dao.observeVault().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collections = container.dao.observeCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collectionItems = container.dao.observeCollectionItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val mediaTextIndexCount = container.dao.observeMediaTextIndexCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val settings = container.preferences.settings
    val favorites = container.preferences.favorites
    val sensitiveIds = container.preferences.sensitiveIds
    val fullFeatures: Boolean = BuildConfig.FULL_FEATURES

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val activeTaskCount = AtomicInteger(0)
    private val messageChannel = Channel<String>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val message = messageChannel.receiveAsFlow()
    private val _sessionUnlocked = MutableStateFlow<Set<String>>(emptySet())
    val sessionUnlocked = _sessionUnlocked.asStateFlow()
    private val _detectedLinks = MutableStateFlow<List<DetectedExternalLink>>(emptyList())
    val detectedLinks = _detectedLinks.asStateFlow()
    private val _debugEnabled = MutableStateFlow(container.log.enabled)
    val debugEnabled = _debugEnabled.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _typeFilter = MutableStateFlow(MediaTypeFilter.ALL)
    val typeFilter = _typeFilter.asStateFlow()
    private val _sizeFilter = MutableStateFlow(MediaSizeFilter.ANY)
    val sizeFilter = _sizeFilter.asStateFlow()
    private val _extensionFilter = MutableStateFlow("")
    val extensionFilter = _extensionFilter.asStateFlow()
    private val _includeImageText = MutableStateFlow(false)
    val includeImageText = _includeImageText.asStateFlow()
    private val _searchBucketId = MutableStateFlow<Long?>(null)
    val searchBucketId = _searchBucketId.asStateFlow()
    private val _textIndexing = MutableStateFlow(false)
    val textIndexing = _textIndexing.asStateFlow()
    private val _textIndexProgress = MutableStateFlow<TextIndexProgress?>(null)
    val textIndexProgress = _textIndexProgress.asStateFlow()
    private val _selectedMediaIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMediaIds = _selectedMediaIds.asStateFlow()
    private val classifiedThisSession = mutableSetOf<Long>()

    private val filterState = combine(
        combine(_searchQuery, _typeFilter, _sizeFilter, _extensionFilter) { query, type, size, extension ->
            MediaFilterState(query, type, size, extension, includeImageText = false, bucketId = null)
        },
        _includeImageText,
        _searchBucketId,
    ) { filters, includeImageText, bucketId ->
        filters.copy(includeImageText = includeImageText, bucketId = bucketId)
    }

    private val imageTextMatches = filterState.mapLatest { filters ->
        val query = filters.query.trim()
        if (!fullFeatures || !filters.includeImageText || query.isEmpty()) {
            emptySet()
        } else {
            withContext(Dispatchers.IO) { container.dao.searchMediaText(query, filters.bucketId).toSet() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val albumPhotos = combine(photos, settings, sensitiveIds) { all, pref, sensitive ->
        all.filter { !pref.hideSensitiveContent || it.mediaId !in sensitive }.let { sortMedia(it, pref) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visiblePhotos = combine(photos, settings, sensitiveIds, imageTextMatches, filterState) { all, pref, sensitive, textMatches, filters ->
        all.filter { media ->
            (!pref.hideSensitiveContent || media.mediaId !in sensitive) &&
                (filters.bucketId == null || media.bucketId == filters.bucketId) &&
                matchesType(media, filters.type) &&
                matchesSize(media, filters.size) &&
                matchesExtension(media, filters.extension) &&
                (
                    matchesMediaSearch(media, filters.query) ||
                        (filters.includeImageText && media.mediaId in textMatches)
                )
        }.let { sortMedia(it, pref) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedPhotos = combine(photos, _selectedMediaIds) { all, ids -> all.filter { it.mediaId in ids } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            combine(photos, settings) { media, pref -> media to pref.hideSensitiveContent }
                .collectLatest { (media, hideSensitive) ->
                    if (hideSensitive) classifySensitive(media)
                }
        }
    }

    fun refresh() = launchTask("Library refreshed") { container.mediaStore.refresh() }

    fun setSearchQuery(value: String) { _searchQuery.value = value }
    fun setTypeFilter(value: MediaTypeFilter) { _typeFilter.value = value }
    fun setSizeFilter(value: MediaSizeFilter) { _sizeFilter.value = value }
    fun setExtensionFilter(value: String) { _extensionFilter.value = value.trim().removePrefix(".").take(12) }
    fun setImageTextSearchEnabled(value: Boolean) { _includeImageText.value = value && fullFeatures }
    fun setSearchBucketId(value: Long?) { _searchBucketId.value = value }
    fun setSortMode(value: MediaSortMode) = container.preferences.setSort(value)
    fun setSortDescending(value: Boolean) = container.preferences.setSortDescending(value)
    fun setGridColumns(value: Int) = container.preferences.setGridColumns(value)
    fun setGridLayoutMode(value: GridLayoutMode) = container.preferences.setGridLayoutMode(value)
    fun setThumbnailScaleMode(value: ThumbnailScaleMode) = container.preferences.setThumbnailScaleMode(value)
    fun setPreviewScaleMode(value: PreviewScaleMode) = container.preferences.setPreviewScaleMode(value)
    fun setShowMediaBadges(value: Boolean) = container.preferences.setShowMediaBadges(value)
    fun setAnimationsEnabled(value: Boolean) = container.preferences.setAnimationsEnabled(value)
    fun setCameraGridEnabled(value: Boolean) = container.preferences.setCameraGridEnabled(value)
    fun setCameraAudioEnabled(value: Boolean) = container.preferences.setCameraAudioEnabled(value)
    fun setVideoPreviewAutoPlay(value: Boolean) = container.preferences.setVideoPreviewAutoPlay(value)
    fun setDeleteToTrash(value: Boolean) = container.preferences.setDeleteToTrash(value)
    fun setLanguage(value: AppLanguage) = container.preferences.setLanguage(value)
    fun setHideSensitiveContent(value: Boolean) {
        container.preferences.setHideSensitiveContent(value)
        if (value) viewModelScope.launch { classifySensitive(photos.value) }
    }

    fun indexImageText(force: Boolean = false) {
        if (!fullFeatures) {
            postMessage("Image text search is available in KeepG Full")
            return
        }
        if (!_textIndexing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            var failures = 0
            try {
                val bucketId = _searchBucketId.value
                val existingIds = if (force) emptySet() else container.dao.getIndexedMediaIds().toSet()
                val lockedPhotoIds = locks.value.asSequence().filter { it.targetType == "PHOTO" }.mapNotNull { it.targetId.toLongOrNull() }.toSet()
                val lockedAlbumIds = locks.value.asSequence().filter { it.targetType == "ALBUM" }.mapNotNull { it.targetId.toLongOrNull() }.toSet()
                val targets = photos.value.asSequence()
                    .filter { it.mimeType.startsWith("image/") }
                    .filter { bucketId == null || it.bucketId == bucketId }
                    .filter { it.mediaId !in lockedPhotoIds && it.bucketId !in lockedAlbumIds }
                    .filter { it.mediaId !in existingIds }
                    .toList()
                _textIndexProgress.value = TextIndexProgress(0, targets.size)
                targets.forEachIndexed { index, media ->
                    try {
                        val text = container.advanced.recognizeText(media)
                        container.dao.upsertMediaTextIndex(MediaTextIndexEntity(media.mediaId, text))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        failures++
                        container.log.warn("OCR", "Unable to index ${media.displayName}", error)
                    }
                    _textIndexProgress.value = TextIndexProgress(index + 1, targets.size, failures)
                }
                postMessage(if (targets.isEmpty()) "Image text index is already up to date" else "Indexed ${targets.size - failures}/${targets.size} images")
            } finally {
                _textIndexing.value = false
            }
        }
    }

    fun clearImageTextIndex() {
        viewModelScope.launch {
            container.dao.clearMediaTextIndex()
            _textIndexProgress.value = null
            postMessage("Image text index cleared")
        }
    }

    fun toggleFavorite(mediaId: Long) = container.preferences.toggleFavorite(mediaId)
    fun favoriteSelected() {
        container.preferences.addFavorites(_selectedMediaIds.value)
    }
    fun favoriteMedia(mediaIds: Collection<Long>) = container.preferences.addFavorites(mediaIds)

    fun toggleSelection(mediaId: Long) {
        _selectedMediaIds.update { selected -> selected.toMutableSet().apply { if (!add(mediaId)) remove(mediaId) } }
    }
    fun clearSelection() { _selectedMediaIds.value = emptySet() }
    fun selectOnly(mediaId: Long) { _selectedMediaIds.value = setOf(mediaId) }
    fun selectMedia(mediaIds: Collection<Long>) { _selectedMediaIds.value = mediaIds.toSet() }

    fun lockPhotoWithDevice(photo: PhotoEntity) = lock("PHOTO", photo.mediaId.toString(), "DEVICE", null)
    fun lockAlbumWithDevice(bucketId: Long) = lock("ALBUM", bucketId.toString(), "DEVICE", null)
    fun lockPhotoWithPassword(photo: PhotoEntity, password: String) = lock("PHOTO", photo.mediaId.toString(), "PASSWORD", password)
    fun lockAlbumWithPassword(bucketId: Long, password: String) = lock("ALBUM", bucketId.toString(), "PASSWORD", password)

    fun lockPhotosWithDevice(mediaIds: Collection<Long>) = lockPhotos(mediaIds, "DEVICE", null)
    fun lockPhotosWithPassword(mediaIds: Collection<Long>, password: String) = lockPhotos(mediaIds, "PASSWORD", password)

    private fun lockPhotos(mediaIds: Collection<Long>, authType: String, password: String?) {
        val uniqueIds = mediaIds.distinct()
        if (uniqueIds.isEmpty() || (authType == "PASSWORD" && password.orEmpty().length < 6)) return
        viewModelScope.launch(Dispatchers.Default) {
            beginBusy()
            try {
                val encoded = password?.let { PasswordHasher.create(it.toCharArray()) }
                uniqueIds.forEach { mediaId ->
                    coroutineContext.ensureActive()
                    container.dao.upsertLock(
                        LockEntity(
                            targetType = "PHOTO",
                            targetId = mediaId.toString(),
                            authType = authType,
                            passwordHash = encoded?.hash,
                            passwordSalt = encoded?.salt,
                        )
                    )
                    container.dao.deleteMediaTextIndex(mediaId)
                }
                _sessionUnlocked.update { unlocked -> unlocked - uniqueIds.map { "PHOTO:$it" }.toSet() }
                postMessage("Protected ${uniqueIds.size} items")
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                container.log.error("Security", "Unable to protect selected media", error)
                postMessage("Unable to protect selected media")
            } finally {
                endBusy()
            }
        }
    }

    private fun lock(targetType: String, targetId: String, authType: String, password: String?) {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val encoded = password?.let { PasswordHasher.create(it.toCharArray()) }
                container.dao.upsertLock(LockEntity(targetType = targetType, targetId = targetId, authType = authType, passwordHash = encoded?.hash, passwordSalt = encoded?.salt))
                if (targetType == "PHOTO") targetId.toLongOrNull()?.let { container.dao.deleteMediaTextIndex(it) }
                if (targetType == "ALBUM") targetId.toLongOrNull()?.let { container.dao.deleteAlbumTextIndex(it) }
                _sessionUnlocked.update { it - "$targetType:$targetId" }
                container.log.info("Security", "Protected $targetType:$targetId with $authType")
            }.onFailure {
                if (it is CancellationException) throw it
                container.log.error("Security", "Unable to create lock", it)
                postMessage("Unable to create lock")
            }
        }
    }

    fun verifyPassword(lock: LockEntity, password: String): Boolean {
        val hash = lock.passwordHash ?: return false
        val salt = lock.passwordSalt ?: return false
        return runCatching { PasswordHasher.verify(password.toCharArray(), hash, salt) }.getOrDefault(false)
    }

    fun unlockForSession(lock: LockEntity) { _sessionUnlocked.update { it + "${lock.targetType}:${lock.targetId}" } }

    fun removeLock(lock: LockEntity) {
        viewModelScope.launch {
            container.dao.deleteLock(lock.targetType, lock.targetId)
            _sessionUnlocked.update { it - "${lock.targetType}:${lock.targetId}" }
        }
    }

    fun analyze(photo: PhotoEntity) {
        if (!fullFeatures || !photo.mimeType.startsWith("image/")) {
            postMessage("Face analysis is available for images in KeepG Full")
            return
        }
        launchTask("Analysis complete") { container.faceAnalysis.analyze(photo) }
    }

    fun analyzeLibrary() {
        if (!fullFeatures) {
            postMessage("Smart analysis is available in KeepG Full")
            return
        }
        viewModelScope.launch {
            beginBusy()
            try {
                var facesFound = 0
                val snapshot = photos.value.filter { it.mimeType.startsWith("image/") }
                snapshot.forEachIndexed { index, photo ->
                    coroutineContext.ensureActive()
                    facesFound += try {
                        container.faceAnalysis.analyze(photo)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Throwable) {
                        container.log.error("Smart", "Unable to analyze ${photo.displayName}", error)
                        0
                    }
                }
                postMessage("Smart analysis complete: $facesFound faces processed")
            } finally { endBusy() }
        }
    }

    fun detectLinks(photo: PhotoEntity, normalizedX: Float? = null, normalizedY: Float? = null) = launchTask(null) {
        val links = container.advanced.detectExternalLinks(photo, normalizedX, normalizedY)
        _detectedLinks.value = links
        postMessage(if (links.isEmpty()) "No web links or QR URLs found" else "Link scan complete")
    }

    fun clearDetectedLinks() { _detectedLinks.value = emptyList() }

    fun editMedia(photo: PhotoEntity, operation: MediaEditOperation, strength: Float = 0.35f) = launchTask("Edited copy created") {
        val result = container.advanced.edit(photo, operation, strength)
        container.log.info("Editor", "${operation.name}: ${photo.displayName} -> $result")
        container.mediaStore.refresh()
    }

    fun editAdvanced(photo: PhotoEntity, request: AdvancedEditRequest) = launchTask("Edited copy created") {
        val result = container.advanced.editAdvanced(photo, request)
        container.log.info("Editor", "Advanced edit: ${photo.displayName} -> $result")
        container.mediaStore.refresh()
    }

    fun repairMedia(photo: PhotoEntity, useCurrentTime: Boolean = false) = launchTask(null) {
        val report = container.advanced.repair(photo, if (useCurrentTime) System.currentTimeMillis() else null)
        container.log.info("Repair", "${photo.displayName}: ${report.summary}")
        container.mediaStore.refresh()
        postMessage(report.summary)
    }

    fun prepareShare(media: List<PhotoEntity>, password: String?, onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            beginBusy()
            try {
                val intent = withContext(Dispatchers.IO) { container.mediaActions.prepareShare(media, password) }
                onReady(Intent.createChooser(intent, UiLocalizer.text(settings.value.language, "Share media")))
            } catch (t: Throwable) {
                container.log.error("Share", "Unable to prepare share", t)
                postMessage("Unable to share media")
            } finally { endBusy() }
        }
    }

    fun createRemovalIntentSender(media: List<PhotoEntity>): IntentSender? =
        container.mediaActions.createRemovalIntentSender(media, settings.value.deleteToTrash)

    fun removeLegacy(media: List<PhotoEntity>) = launchTask(null) {
        val removed = container.mediaActions.removeLegacy(media)
        container.mediaStore.refresh()
        clearSelection()
        postMessage("Removed $removed item(s)")
    }

    fun renameMedia(photo: PhotoEntity, name: String) = launchTask("Media renamed") {
        check(container.mediaActions.rename(photo, name)) { "Android denied this rename request" }
        container.mediaStore.refresh()
    }

    fun hasDeletionPassword(): Boolean = container.preferences.hasDeletionPassword()
    fun setDeletionPassword(password: String) = runCatching { container.preferences.setDeletionPassword(password) }
        .onSuccess { postMessage("Deletion password updated") }
        .onFailure {
            container.log.error("Security", "Unable to update deletion password", it)
            postMessage("Operation failed")
        }
        .isSuccess
    fun verifyDeletionPassword(password: String): Boolean = container.preferences.verifyDeletionPassword(password)

    fun setDebugEnabled(enabled: Boolean) {
        container.log.setEnabled(enabled)
        _debugEnabled.value = enabled
    }

    fun exportDebugLog(destination: Uri) = launchTask("Debug log exported") { container.log.exportTo(destination) }
    fun clearDebugLog() { container.log.clear(); postMessage("Debug log cleared") }
    fun debugSnapshot(): String = container.log.snapshot()

    fun namePerson(clusterId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val existing = container.dao.getPersonForCluster(clusterId)
            container.dao.upsertPerson(existing?.copy(displayName = name.trim()) ?: PersonProfileEntity(clusterId = clusterId, displayName = name.trim()))
        }
    }

    fun addPersonRule(name: String, clusterId: Long) = addRule(SmartRuleEntity(name = name.ifBlank { "Person $clusterId" }, kind = "PERSON", personClusterId = clusterId))
    fun addExpressionRule(name: String, expression: String, threshold: Float) = addRule(SmartRuleEntity(name = name.ifBlank { expression.replace('_', ' ') }, kind = "EXPRESSION", expression = expression, threshold = threshold))

    fun addTimeRule(name: String, start: String, end: String) {
        viewModelScope.launch {
            runCatching {
                val zone = ZoneId.systemDefault()
                val startMs = LocalDate.parse(start).atStartOfDay(zone).toInstant().toEpochMilli()
                val endMs = LocalDate.parse(end).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                container.dao.upsertRule(SmartRuleEntity(name = name.ifBlank { "$start – $end" }, kind = "TIME", startTime = startMs, endTime = endMs))
            }.onFailure {
                if (it is CancellationException) throw it
                postMessage("Dates must use YYYY-MM-DD")
            }
        }
    }

    fun addLocationRule(name: String, lat: Double, lon: Double, radiusMeters: Double) = addRule(SmartRuleEntity(name = name.ifBlank { "Location" }, kind = "LOCATION", latitude = lat, longitude = lon, radiusMeters = radiusMeters))
    private fun addRule(rule: SmartRuleEntity) { viewModelScope.launch { container.dao.upsertRule(rule) } }
    fun deleteRule(id: Long) { viewModelScope.launch { container.dao.deleteRule(id) } }

    fun matchingCount(rule: SmartRuleEntity): Int {
        val facesByPhoto = faces.value.groupBy { it.mediaId }
        return photos.value.count { SmartRuleEvaluator.matches(it, facesByPhoto[it.mediaId].orEmpty(), rule) }
    }

    fun importToVault(photo: PhotoEntity) = launchTask("Encrypted copy stored in Vault") { container.vault.importEncrypted(photo) }
    fun removeVaultItem(item: VaultItemEntity) = launchTask("Vault item removed") { container.vault.remove(item) }
    fun createCollection(name: String) { if (name.isNotBlank()) viewModelScope.launch { container.dao.insertCollection(CollectionEntity(name = name.trim())) } }
    fun addToCollection(collectionId: Long, mediaId: Long) { viewModelScope.launch { container.dao.addCollectionItem(CollectionItemEntity(collectionId, mediaId)) } }
    fun addMediaToCollection(collectionId: Long, mediaIds: Collection<Long>) {
        val uniqueIds = mediaIds.distinct()
        if (uniqueIds.isEmpty()) return
        viewModelScope.launch {
            uniqueIds.forEach { mediaId ->
                coroutineContext.ensureActive()
                container.dao.addCollectionItem(CollectionItemEntity(collectionId, mediaId))
            }
            postMessage("Added ${uniqueIds.size} items to collection")
        }
    }
    fun removeFromCollection(collectionId: Long, mediaId: Long) { viewModelScope.launch { container.dao.removeCollectionItem(collectionId, mediaId) } }
    fun clearCollection(collectionId: Long) { viewModelScope.launch { container.dao.clearCollectionItems(collectionId) } }
    fun renameCollection(collectionId: Long, name: String) {
        if (name.isNotBlank()) viewModelScope.launch { container.dao.renameCollection(collectionId, name.trim()) }
    }
    fun deleteCollection(collectionId: Long) { viewModelScope.launch { container.dao.deleteCollection(collectionId) } }
    fun notifyUser(message: String) { postMessage(message) }
    private suspend fun classifySensitive(media: List<PhotoEntity>) {
        media.forEach { item ->
            if (item.mediaId in classifiedThisSession) return@forEach
            classifiedThisSession += item.mediaId
            coroutineContext.ensureActive()
            val sensitive = try {
                container.sensitiveContent.isLikelySensitive(item)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                container.log.error("SensitiveContent", "Unable to classify ${item.displayName}", error)
                false
            }
            container.preferences.setSensitive(item.mediaId, sensitive)
        }
    }

    private fun matchesType(media: PhotoEntity, filter: MediaTypeFilter): Boolean = when (filter) {
        MediaTypeFilter.ALL -> true
        MediaTypeFilter.IMAGES -> media.mimeType.startsWith("image/") && media.mimeType != "image/gif"
        MediaTypeFilter.VIDEOS -> media.mimeType.startsWith("video/")
        MediaTypeFilter.GIFS -> media.mimeType == "image/gif" || media.displayName.endsWith(".gif", true)
    }

    private fun matchesSize(media: PhotoEntity, filter: MediaSizeFilter): Boolean = when (filter) {
        MediaSizeFilter.ANY -> true
        MediaSizeFilter.SMALL -> media.sizeBytes in 0 until 1_048_576L
        MediaSizeFilter.MEDIUM -> media.sizeBytes in 1_048_576L..10_485_760L
        MediaSizeFilter.LARGE -> media.sizeBytes > 10_485_760L
    }

    private fun matchesExtension(media: PhotoEntity, extension: String): Boolean {
        if (extension.isBlank()) return true
        return media.displayName.substringAfterLast('.', "").equals(extension, ignoreCase = true)
    }

    private fun sortMedia(media: List<PhotoEntity>, pref: GallerySettings): List<PhotoEntity> {
        val comparator = when (pref.sortMode) {
            MediaSortMode.DATE -> compareBy<PhotoEntity> { it.dateTaken }.thenBy { it.mediaId }
            MediaSortMode.NAME -> compareBy<PhotoEntity> { it.displayName.lowercase(Locale.ROOT) }.thenBy { it.mediaId }
            MediaSortMode.SIZE -> compareBy<PhotoEntity> { it.sizeBytes }.thenBy { it.displayName.lowercase(Locale.ROOT) }
            MediaSortMode.EXTENSION -> compareBy<PhotoEntity> { it.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT) }.thenBy { it.displayName.lowercase(Locale.ROOT) }
        }
        return media.sortedWith(if (pref.sortDescending) comparator.reversed() else comparator)
    }

    private fun postMessage(message: String?) {
        if (!message.isNullOrBlank()) messageChannel.trySend(message)
    }

    private fun <T> launchTask(successMessage: String?, block: suspend () -> T) {
        viewModelScope.launch {
            beginBusy()
            try {
                withContext(Dispatchers.IO) { block() }
                postMessage(successMessage)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                container.log.error("Operation", successMessage ?: "Operation failed", t)
                postMessage("Operation failed")
            } finally {
                endBusy()
            }
        }
    }

    private fun beginBusy() {
        activeTaskCount.incrementAndGet()
        _busy.value = true
    }

    private fun endBusy() {
        if (activeTaskCount.updateAndGet { count -> (count - 1).coerceAtLeast(0) } == 0) _busy.value = false
    }
}

class MainViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
}
