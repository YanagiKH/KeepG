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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class MainViewModel(private val container: AppContainer) : ViewModel() {
    val photos = container.dao.observePhotos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locks = container.dao.observeLocks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val faces = container.dao.observeFaces().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val people = container.dao.observePeople().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rules = container.dao.observeRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val vault = container.dao.observeVault().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collections = container.dao.observeCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collectionItems = container.dao.observeCollectionItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = container.preferences.settings
    val favorites = container.preferences.favorites
    val sensitiveIds = container.preferences.sensitiveIds
    val fullFeatures: Boolean = BuildConfig.FULL_FEATURES

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
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
    private val _selectedMediaIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMediaIds = _selectedMediaIds.asStateFlow()
    private val classifiedThisSession = mutableSetOf<Long>()

    private val baseVisiblePhotos = combine(photos, settings, sensitiveIds, _searchQuery, _typeFilter) { all, pref, sensitive, query, type ->
        all.filter { media ->
            (!pref.hideSensitiveContent || media.mediaId !in sensitive) &&
                matchesType(media, type) &&
                fuzzyMatches(media, query)
        }.let { sortMedia(it, pref) }
    }

    val visiblePhotos = combine(baseVisiblePhotos, _sizeFilter, _extensionFilter) { all, size, extension ->
        all.filter { media -> matchesSize(media, size) && matchesExtension(media, extension) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    fun setSortMode(value: MediaSortMode) = container.preferences.setSort(value)
    fun setSortDescending(value: Boolean) = container.preferences.setSortDescending(value)
    fun setGridColumns(value: Int) = container.preferences.setGridColumns(value)
    fun setVideoPreviewAutoPlay(value: Boolean) = container.preferences.setVideoPreviewAutoPlay(value)
    fun setDeleteToTrash(value: Boolean) = container.preferences.setDeleteToTrash(value)
    fun setLanguage(value: AppLanguage) = container.preferences.setLanguage(value)
    fun setHideSensitiveContent(value: Boolean) {
        container.preferences.setHideSensitiveContent(value)
        if (value) viewModelScope.launch { classifySensitive(photos.value) }
    }

    fun toggleFavorite(mediaId: Long) = container.preferences.toggleFavorite(mediaId)
    fun favoriteSelected() {
        val current = favorites.value
        _selectedMediaIds.value.forEach { if (it !in current) container.preferences.toggleFavorite(it) }
    }

    fun toggleSelection(mediaId: Long) {
        _selectedMediaIds.update { selected -> selected.toMutableSet().apply { if (!add(mediaId)) remove(mediaId) } }
    }
    fun clearSelection() { _selectedMediaIds.value = emptySet() }
    fun selectOnly(mediaId: Long) { _selectedMediaIds.value = setOf(mediaId) }

    fun lockPhotoWithDevice(photo: PhotoEntity) = lock("PHOTO", photo.mediaId.toString(), "DEVICE", null)
    fun lockAlbumWithDevice(bucketId: Long) = lock("ALBUM", bucketId.toString(), "DEVICE", null)
    fun lockPhotoWithPassword(photo: PhotoEntity, password: String) = lock("PHOTO", photo.mediaId.toString(), "PASSWORD", password)
    fun lockAlbumWithPassword(bucketId: Long, password: String) = lock("ALBUM", bucketId.toString(), "PASSWORD", password)

    private fun lock(targetType: String, targetId: String, authType: String, password: String?) {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val encoded = password?.let { PasswordHasher.create(it.toCharArray()) }
                container.dao.upsertLock(LockEntity(targetType = targetType, targetId = targetId, authType = authType, passwordHash = encoded?.hash, passwordSalt = encoded?.salt))
                _sessionUnlocked.update { it - "$targetType:$targetId" }
                container.log.info("Security", "Protected $targetType:$targetId with $authType")
            }.onFailure {
                container.log.error("Security", "Unable to create lock", it)
                _message.value = it.message ?: "Unable to create lock"
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
            _message.value = "Face analysis is available for images in KeepG Full"
            return
        }
        launchTask("Analysis complete") { container.faceAnalysis.analyze(photo) }
    }

    fun analyzeLibrary() {
        if (!fullFeatures) {
            _message.value = "Smart analysis is available in KeepG Full"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                var facesFound = 0
                val snapshot = photos.value.filter { it.mimeType.startsWith("image/") }
                snapshot.forEachIndexed { index, photo ->
                    _message.value = "Analyzing ${index + 1}/${snapshot.size}: ${photo.displayName}"
                    facesFound += runCatching { container.faceAnalysis.analyze(photo) }.getOrDefault(0)
                }
                _message.value = "Smart analysis complete: $facesFound faces processed"
            } finally { _busy.value = false }
        }
    }

    fun detectLinks(photo: PhotoEntity, normalizedX: Float? = null, normalizedY: Float? = null) = launchTask("Link scan complete") {
        val links = container.advanced.detectExternalLinks(photo, normalizedX, normalizedY)
        _detectedLinks.value = links
        if (links.isEmpty()) _message.value = "No web links or QR URLs found"
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

    fun repairMedia(photo: PhotoEntity, useCurrentTime: Boolean = false) = launchTask("Repair finished") {
        val report = container.advanced.repair(photo, if (useCurrentTime) System.currentTimeMillis() else null)
        container.log.info("Repair", "${photo.displayName}: ${report.summary}")
        container.mediaStore.refresh()
        _message.value = report.summary
    }

    fun prepareShare(media: List<PhotoEntity>, password: String?, onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val intent = withContext(Dispatchers.IO) { container.mediaActions.prepareShare(media, password) }
                onReady(Intent.createChooser(intent, "Share media"))
            } catch (t: Throwable) {
                container.log.error("Share", "Unable to prepare share", t)
                _message.value = t.message ?: "Unable to share media"
            } finally { _busy.value = false }
        }
    }

    fun createRemovalIntentSender(media: List<PhotoEntity>): IntentSender? =
        container.mediaActions.createRemovalIntentSender(media, settings.value.deleteToTrash)

    fun removeLegacy(media: List<PhotoEntity>) = launchTask("Media removed") {
        val removed = container.mediaActions.removeLegacy(media)
        container.mediaStore.refresh()
        clearSelection()
        _message.value = "Removed $removed item(s)"
    }

    fun renameMedia(photo: PhotoEntity, name: String) = launchTask("Media renamed") {
        check(container.mediaActions.rename(photo, name)) { "Android denied this rename request" }
        container.mediaStore.refresh()
    }

    fun hasDeletionPassword(): Boolean = container.preferences.hasDeletionPassword()
    fun setDeletionPassword(password: String) = runCatching { container.preferences.setDeletionPassword(password) }
        .onSuccess { _message.value = "Deletion password updated" }
        .onFailure { _message.value = it.message }
        .isSuccess
    fun verifyDeletionPassword(password: String): Boolean = container.preferences.verifyDeletionPassword(password)

    fun setDebugEnabled(enabled: Boolean) {
        container.log.setEnabled(enabled)
        _debugEnabled.value = enabled
    }

    fun exportDebugLog(destination: Uri) = launchTask("Debug log exported") { container.log.exportTo(destination) }
    fun clearDebugLog() { container.log.clear(); _message.value = "Debug log cleared" }
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
            }.onFailure { _message.value = "Dates must use YYYY-MM-DD" }
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
    fun clearMessage() { _message.value = null }

    private suspend fun classifySensitive(media: List<PhotoEntity>) {
        media.forEach { item ->
            if (item.mediaId in classifiedThisSession) return@forEach
            classifiedThisSession += item.mediaId
            val sensitive = runCatching { container.sensitiveContent.isLikelySensitive(item) }.getOrDefault(false)
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

    private fun fuzzyMatches(media: PhotoEntity, query: String): Boolean {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return true
        val fields = listOf(
            media.displayName,
            media.bucketName,
            media.mimeType,
            media.displayName.substringAfterLast('.', ""),
            "${media.width}x${media.height}",
            media.sizeBytes.toString(),
        )
        return fields.any { fuzzyScore(it.lowercase(Locale.ROOT), q) > 0 }
    }

    private fun fuzzyScore(text: String, query: String): Int {
        if (text == query) return 1000
        if (text.startsWith(query)) return 800 - (text.length - query.length).coerceAtMost(200)
        val direct = text.indexOf(query)
        if (direct >= 0) return 600 - direct.coerceAtMost(200)
        var qi = 0
        var gap = 0
        var last = -1
        text.forEachIndexed { index, c ->
            if (qi < query.length && c == query[qi]) {
                if (last >= 0) gap += index - last - 1
                last = index
                qi++
            }
        }
        return if (qi == query.length) (350 - gap).coerceAtLeast(1) else 0
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

    private fun <T> launchTask(successMessage: String, block: suspend () -> T) {
        viewModelScope.launch {
            _busy.value = true
            try {
                withContext(Dispatchers.IO) { block() }
                if (_message.value == null) _message.value = successMessage
            } catch (t: Throwable) {
                container.log.error("Operation", successMessage, t)
                _message.value = t.message ?: "Operation failed"
            } finally {
                _busy.value = false
            }
        }
    }
}

class MainViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
}
