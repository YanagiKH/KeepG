package com.yanagikh.keepg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yanagikh.keepg.data.*
import com.yanagikh.keepg.security.PasswordHasher
import com.yanagikh.keepg.smart.SmartRuleEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class MainViewModel(private val container: AppContainer) : ViewModel() {
    val photos = container.dao.observePhotos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val locks = container.dao.observeLocks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val faces = container.dao.observeFaces().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val people = container.dao.observePeople().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val rules = container.dao.observeRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val vault = container.dao.observeVault().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collections = container.dao.observeCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collectionItems = container.dao.observeCollectionItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _sessionUnlocked = MutableStateFlow<Set<String>>(emptySet())
    val sessionUnlocked = _sessionUnlocked.asStateFlow()

    fun refresh() = launchTask("Library refreshed") { container.mediaStore.refresh() }

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
            }.onFailure { _message.value = it.message ?: "Unable to create lock" }
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

    fun analyze(photo: PhotoEntity) = launchTask("Analysis complete") { container.faceAnalysis.analyze(photo) }

    fun analyzeLibrary() {
        viewModelScope.launch {
            _busy.value = true
            try {
                var facesFound = 0
                val snapshot = photos.value
                snapshot.forEachIndexed { index, photo ->
                    _message.value = "Analyzing ${index + 1}/${snapshot.size}: ${photo.displayName}"
                    facesFound += runCatching { container.faceAnalysis.analyze(photo) }.getOrDefault(0)
                }
                _message.value = "Smart analysis complete: $facesFound faces processed"
            } finally { _busy.value = false }
        }
    }

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

    private fun <T> launchTask(successMessage: String, block: suspend () -> T) {
        viewModelScope.launch {
            _busy.value = true
            try { withContext(Dispatchers.IO) { block() }; _message.value = successMessage }
            catch (t: Throwable) { _message.value = t.message ?: "Operation failed" }
            finally { _busy.value = false }
        }
    }
}

class MainViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
}
