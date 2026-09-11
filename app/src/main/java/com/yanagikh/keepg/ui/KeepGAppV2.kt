package com.yanagikh.keepg.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yanagikh.keepg.agent.*
import com.yanagikh.keepg.advanced.MediaEditOperation
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yanagikh.keepg.KeepGApplication
import com.yanagikh.keepg.MainViewModel
import com.yanagikh.keepg.data.*
import com.yanagikh.keepg.widget.KeepGWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private enum class TabV2(val labelKey: String, val icon: ImageVector) {
    PHOTOS("Photos", Icons.Default.PhotoLibrary),
    ALBUMS("Albums", Icons.Default.Folder),
    SMART("Smart", Icons.Default.AutoAwesome),
    VAULT("Vault", Icons.Default.Lock),
    SETTINGS("Settings", Icons.Default.Settings),
}

private data class LockTargetV2(val type: String, val id: String, val label: String, val photo: PhotoEntity? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepGAppV2(
    viewModel: MainViewModel,
    requestDeviceAuthentication: (String, () -> Unit, (String) -> Unit) -> Unit,
    mediaPermissions: Array<String>,
    initialDestination: String? = null,
) {
    val context = LocalContext.current
    val agent: AgentViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val container = remember(context) { (context.applicationContext as KeepGApplication).container }
    val scope = rememberCoroutineScope()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val visiblePhotos by viewModel.visiblePhotos.collectAsStateWithLifecycle()
    val albumPhotos by viewModel.albumPhotos.collectAsStateWithLifecycle()
    val locks by viewModel.locks.collectAsStateWithLifecycle()
    val faces by viewModel.faces.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val vault by viewModel.vault.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val collectionItems by viewModel.collectionItems.collectAsStateWithLifecycle()
    val unlocked by viewModel.sessionUnlocked.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val detectedLinks by viewModel.detectedLinks.collectAsStateWithLifecycle()
    val debugEnabled by viewModel.debugEnabled.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedMediaIds.collectAsStateWithLifecycle()
    val selectedPhotos by viewModel.selectedPhotos.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val typeFilter by viewModel.typeFilter.collectAsStateWithLifecycle()
    val sizeFilter by viewModel.sizeFilter.collectAsStateWithLifecycle()
    val extensionFilter by viewModel.extensionFilter.collectAsStateWithLifecycle()
    val includeImageText by viewModel.includeImageText.collectAsStateWithLifecycle()
    val searchBucketId by viewModel.searchBucketId.collectAsStateWithLifecycle()
    val textIndexing by viewModel.textIndexing.collectAsStateWithLifecycle()
    val textIndexProgress by viewModel.textIndexProgress.collectAsStateWithLifecycle()
    val mediaTextIndexCount by viewModel.mediaTextIndexCount.collectAsStateWithLifecycle()
    val fullFeatures = viewModel.fullFeatures

    CompositionLocalProvider(LocalAppLanguage provides settings.language, LocalAgentLauncher provides if (settings.agentEnabled) ({ agent.open() }) else null) {
        fun localized(key: String): String = UiLocalizer.text(settings.language, key)
        fun localizedFormat(key: String, vararg args: Any): String = String.format(Locale.ROOT, localized(key), *args)
        val tabs = if (fullFeatures) TabV2.entries else listOf(TabV2.PHOTOS, TabV2.ALBUMS, TabV2.SETTINGS)
        val initialTab = when (initialDestination) {
            KeepGWidgetProvider.DEST_ALBUMS -> TabV2.ALBUMS
            KeepGWidgetProvider.DEST_VAULT -> if (fullFeatures) TabV2.VAULT else TabV2.PHOTOS
            else -> TabV2.PHOTOS
        }
        val incorrectPasswordMessage = tr("Incorrect password")
        val minimumPasswordMessage = tr("Use at least 6 characters")
        var tabName by rememberSaveable { mutableStateOf(initialTab.name) }
        val tab = tabs.firstOrNull { it.name == tabName } ?: tabs.first()
        var cameraOpen by rememberSaveable { mutableStateOf(initialDestination == KeepGWidgetProvider.DEST_CAMERA) }
        var showLaunch by rememberSaveable { mutableStateOf(true) }
        var previewSession by remember { mutableStateOf<PreviewSession?>(null) }
        val photosById = remember(photos) { photos.associateBy { it.mediaId } }
        val activePreviewSession = previewSession?.retainAvailable(photosById.keys)
        val preview = activePreviewSession?.currentMediaId?.let(photosById::get)
        var unlockLock by remember { mutableStateOf<LockEntity?>(null) }
        var pendingPreviewAfterUnlockSession by remember { mutableStateOf<PreviewSession?>(null) }
        var unlockPassword by remember { mutableStateOf("") }
        var lockTarget by remember { mutableStateOf<LockTargetV2?>(null) }
        var passwordTarget by remember { mutableStateOf<LockTargetV2?>(null) }
        var newPassword by remember { mutableStateOf("") }
        var shareTarget by remember { mutableStateOf<List<PhotoEntity>?>(null) }
        var deleteTarget by remember { mutableStateOf<List<PhotoEntity>?>(null) }
        var batchCollection by remember { mutableStateOf(false) }
        var batchProtect by remember { mutableStateOf(false) }
        var batchProtectPassword by remember { mutableStateOf("") }
        var trashMedia by remember { mutableStateOf<List<PhotoEntity>>(emptyList()) }
        var pendingMediaActionLabel by remember { mutableStateOf("Media action") }
        val snackbar = remember { SnackbarHostState() }
        val latestLanguage by rememberUpdatedState(settings.language)

        fun reloadTrash() {
            scope.launch { trashMedia = container.mediaStore.listTrashed() }
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
        val logExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(viewModel::exportDebugLog) }
        val mediaActionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                previewSession = null
                viewModel.clearSelection()
                viewModel.refresh()
                reloadTrash()
                viewModel.notifyUser(localizedFormat("%s complete", localized(pendingMediaActionLabel)))
            } else {
                viewModel.notifyUser(localizedFormat("%s cancelled", localized(pendingMediaActionLabel)))
            }
        }

        LaunchedEffect(Unit) {
            permissionLauncher.launch(mediaPermissions)
            trashMedia = container.mediaStore.listTrashed()
            if (showLaunch) {
                delay(1_050)
                showLaunch = false
            }
        }
        LaunchedEffect(initialDestination) {
            when (initialDestination) {
                KeepGWidgetProvider.DEST_CAMERA -> cameraOpen = true
                KeepGWidgetProvider.DEST_ALBUMS -> { cameraOpen = false; tabName = TabV2.ALBUMS.name }
                KeepGWidgetProvider.DEST_VAULT -> { cameraOpen = false; tabName = if (fullFeatures) TabV2.VAULT.name else TabV2.PHOTOS.name }
                KeepGWidgetProvider.DEST_PHOTOS -> { cameraOpen = false; tabName = TabV2.PHOTOS.name }
            }
        }
        LaunchedEffect(viewModel, snackbar) {
            viewModel.message.collect { raw ->
                snackbar.showSnackbar(UiLocalizer.message(latestLanguage, raw))
            }
        }
        LaunchedEffect(previewSession, activePreviewSession) {
            if (previewSession != activePreviewSession) previewSession = activePreviewSession
        }

        fun unlock(lock: LockEntity, afterPreview: PreviewSession? = null) {
            if (lock.authType == "DEVICE") {
                requestDeviceAuthentication(
                    localized("Unlock protected media"),
                    {
                        viewModel.unlockForSession(lock)
                        afterPreview?.let { previewSession = it }
                    },
                    viewModel::notifyUser,
                )
            } else {
                unlockLock = lock
                pendingPreviewAfterUnlockSession = afterPreview
                unlockPassword = ""
            }
        }

        fun requestPreview(session: PreviewSession) {
            val retainedSession = session.retainAvailable(photosById.keys) ?: return
            val media = photosById[retainedSession.currentMediaId] ?: return
            val lock = if (fullFeatures) findLock(media, locks) else null
            if (lock == null || isUnlocked(lock, unlocked)) previewSession = retainedSession else unlock(lock, retainedSession)
        }

        fun openMedia(media: PhotoEntity, orderedMediaIds: List<Long>) {
            createPreviewSession(media.mediaId, orderedMediaIds)?.let(::requestPreview)
        }

        fun navigatePreview(delta: Int) {
            activePreviewSession?.moveBy(delta)?.let(::requestPreview)
        }

        fun shareMedia(media: List<PhotoEntity>, password: String?) {
            if (media.isEmpty()) return
            viewModel.prepareShare(media, password) { chooser ->
                runCatching { context.startActivity(chooser) }.onFailure { viewModel.notifyUser(localized("No compatible share target is available")) }
            }
        }

        fun launchMediaAction(label: String, sender: android.content.IntentSender?) {
            if (sender == null) {
                viewModel.notifyUser("$label is unavailable on this Android version")
                return
            }
            pendingMediaActionLabel = label
            mediaActionLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }

        fun removeMedia(media: List<PhotoEntity>) {
            if (media.isEmpty()) return
            val sender = viewModel.createRemovalIntentSender(media)
            if (sender != null) {
                launchMediaAction(if (settings.deleteToTrash) "Move to trash" else "Delete", sender)
            } else {
                previewSession = null
                viewModel.removeLegacy(media)
                reloadTrash()
            }
        }

        fun restoreTrash(media: List<PhotoEntity>) {
            if (media.isEmpty()) return
            launchMediaAction("Restore from trash", container.mediaActions.createTrashStateIntentSender(media, false))
        }

        fun permanentlyDeleteTrash(media: List<PhotoEntity>) {
            if (media.isEmpty()) return
            launchMediaAction("Permanent delete", container.mediaActions.createPermanentDeleteIntentSender(media))
        }

        fun selectMedia(media: List<PhotoEntity>) {
            viewModel.selectMedia(media.filter { mediaItem ->
                val mediaLock = findLock(mediaItem, locks)
                mediaLock == null || isUnlocked(mediaLock, unlocked)
            }.map { it.mediaId })
        }

        fun favoriteMedia(media: List<PhotoEntity>) {
            viewModel.favoriteMedia(media.map { it.mediaId })
        }

        fun vaultMedia(media: List<PhotoEntity>) {
            if (!fullFeatures || media.isEmpty()) return
            scope.launch(Dispatchers.IO) {
                val unique = media.distinctBy { it.mediaId }
                var stored = 0
                unique.forEach { item -> if (runCatching { container.vault.importEncrypted(item) }.isSuccess) stored++ }
                viewModel.notifyUser(localizedFormat("Stored %s/%s encrypted Vault copies", stored, unique.size))
            }
        }

        fun analyzeMedia(media: List<PhotoEntity>) {
            if (!fullFeatures) return
            scope.launch(Dispatchers.IO) {
                val images = media.distinctBy { it.mediaId }.filter { it.mimeType.startsWith("image/") }
                var analyzed = 0
                images.forEach { if (runCatching { container.faceAnalysis.analyze(it) }.isSuccess) analyzed++ }
                viewModel.notifyUser(localizedFormat("Analyzed %s/%s selected images", analyzed, images.size))
            }
        }

        fun protectSelectedWithDevice() {
            val target = selectedPhotos.distinctBy { it.mediaId }
            if (target.isEmpty()) return
            requestDeviceAuthentication(
                localized("Protect selected media"),
                {
                    viewModel.lockPhotosWithDevice(target.map { it.mediaId })
                    batchProtect = false
                },
                viewModel::notifyUser,
            )
        }

        fun protectSelectedWithPassword(password: String) {
            val target = selectedPhotos.distinctBy { it.mediaId }
            if (target.isEmpty() || password.length < 6) return
            viewModel.lockPhotosWithPassword(target.map { it.mediaId }, password)
            batchProtect = false
            batchProtectPassword = ""
        }

        fun openExternal(url: String) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { viewModel.notifyUser(localized("No browser could open this link")) }
        }

        Box(Modifier.fillMaxSize()) {
            if (cameraOpen) {
                KeepGCameraScreen(
                    onClose = { cameraOpen = false },
                    onCaptured = {
                        viewModel.refresh()
                        viewModel.notifyUser(localized("Media captured"))
                    },
                    initialGridEnabled = settings.cameraGridEnabled,
                    recordAudioEnabled = settings.cameraAudioEnabled,
                    onGridEnabledChange = viewModel::setCameraGridEnabled,
                    onRecordAudioEnabledChange = viewModel::setCameraAudioEnabled,
                )
            } else {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) {
                                        Box(contentAlignment = Alignment.Center) { Text("K", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black) }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(if (fullFeatures) "KeepG" else "KeepG Lite", fontWeight = FontWeight.Bold)
                                }
                            },
                            actions = {
                                AgentEntryButton()
                                if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                IconButton({ cameraOpen = true }) { Icon(Icons.Default.PhotoCamera, tr("KeepG Camera")) }
                                IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, tr("Refresh")) }
                            },
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            tabs.forEach { item ->
                                NavigationBarItem(
                                    selected = tab == item,
                                    onClick = { tabName = item.name; viewModel.clearSelection() },
                                    icon = { Icon(item.icon, tr(item.labelKey)) },
                                    label = { Text(tr(item.labelKey)) },
                                )
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val enabled = settings.animationsEnabled
                                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                (slideInHorizontally(tween(if (enabled) 180 else 0)) { if (enabled) direction * it / 8 else 0 } +
                                    fadeIn(tween(if (enabled) 180 else 0))) togetherWith
                                    (slideOutHorizontally(tween(if (enabled) 140 else 0)) { if (enabled) -direction * it / 8 else 0 } +
                                        fadeOut(tween(if (enabled) 140 else 0)))
                            },
                            label = "main-tab",
                        ) { activeTab ->
                        when (activeTab) {
                            TabV2.PHOTOS -> LibraryScreenV2(
                                photos = visiblePhotos,
                                availablePhotos = albumPhotos,
                                locks = locks,
                                unlocked = unlocked,
                                favorites = favorites,
                                selectedIds = selectedIds,
                                settings = settings,
                                query = query,
                                typeFilter = typeFilter,
                                sizeFilter = sizeFilter,
                                extensionFilter = extensionFilter,
                                includeImageText = includeImageText,
                                searchBucketId = searchBucketId,
                                textIndexing = textIndexing,
                                textIndexProgress = textIndexProgress,
                                indexedImageCount = mediaTextIndexCount,
                                onQuery = viewModel::setSearchQuery,
                                onTypeFilter = viewModel::setTypeFilter,
                                onSizeFilter = viewModel::setSizeFilter,
                                onExtensionFilter = viewModel::setExtensionFilter,
                                onIncludeImageText = viewModel::setImageTextSearchEnabled,
                                onSearchBucketId = viewModel::setSearchBucketId,
                                onIndexImageText = viewModel::indexImageText,
                                onClearImageTextIndex = viewModel::clearImageTextIndex,
                                onSort = viewModel::setSortMode,
                                onSortDescending = viewModel::setSortDescending,
                                onGridColumns = viewModel::setGridColumns,
                                onPhoto = ::openMedia,
                                onToggleSelection = viewModel::toggleSelection,
                                onClearSelection = viewModel::clearSelection,
                                onSelectAll = { selectMedia(visiblePhotos) },
                                onFavoriteSelected = viewModel::favoriteSelected,
                                onShareSelected = { if (selectedPhotos.isNotEmpty()) shareTarget = selectedPhotos },
                                onCollectionSelected = { if (selectedPhotos.isNotEmpty() && collections.isNotEmpty()) batchCollection = true },
                                onProtectSelected = { if (selectedPhotos.isNotEmpty()) batchProtect = true },
                                onVaultSelected = { vaultMedia(selectedPhotos) },
                                onAnalyzeSelected = { analyzeMedia(selectedPhotos) },
                                onDeleteSelected = { if (selectedPhotos.isNotEmpty()) deleteTarget = selectedPhotos },
                                fullFeatures = fullFeatures,
                            )
                            TabV2.ALBUMS -> AlbumsScreenV2(
                                photos = albumPhotos,
                                trash = trashMedia,
                                locks = locks,
                                unlocked = unlocked,
                                collections = collections,
                                collectionItems = collectionItems,
                                favorites = favorites,
                                selectedIds = selectedIds,
                                settings = settings,
                                onPhoto = ::openMedia,
                                onToggleSelection = viewModel::toggleSelection,
                                onGridColumns = viewModel::setGridColumns,
                                onCreateCollection = viewModel::createCollection,
                                onRenameCollection = viewModel::renameCollection,
                                onClearCollection = viewModel::clearCollection,
                                onDeleteCollection = viewModel::deleteCollection,
                                onRemoveFromCollection = viewModel::removeFromCollection,
                                onLockAlbum = { id, name -> lockTarget = LockTargetV2("ALBUM", id.toString(), name) },
                                onUnlock = { unlock(it) },
                                onSelectMedia = ::selectMedia,
                                onFavoriteMedia = ::favoriteMedia,
                                onShareMedia = { shareTarget = it },
                                onDeleteMedia = { deleteTarget = it },
                                onVaultMedia = ::vaultMedia,
                                onRestoreTrash = ::restoreTrash,
                                onDeleteTrash = ::permanentlyDeleteTrash,
                                allowProtection = fullFeatures,
                                onClearSelection = viewModel::clearSelection,
                                onCollectionSelected = { if (selectedPhotos.isNotEmpty()) batchCollection = true },
                                onProtectSelected = { if (selectedPhotos.isNotEmpty()) batchProtect = true },
                                onAnalyzeSelected = { analyzeMedia(selectedPhotos) },
                            )
                            TabV2.SMART -> SmartScreen(photos, faces, people, rules, busy, viewModel)
                            TabV2.VAULT -> VaultScreen(vault, viewModel::removeVaultItem)
                            TabV2.SETTINGS -> GallerySettingsScreen(
                                photoCount = photos.size,
                                faceCount = faces.size,
                                lockCount = locks.size,
                                fullFeatures = fullFeatures,
                                debugEnabled = debugEnabled,
                                settings = settings,
                                hasDeletionPassword = viewModel.hasDeletionPassword(),
                                onVideoPreviewAutoPlay = viewModel::setVideoPreviewAutoPlay,
                                onPreviewSwipeNavigation = container.preferences::setPreviewSwipeNavigation,
                                onGridColumns = viewModel::setGridColumns,
                                onGridLayoutMode = viewModel::setGridLayoutMode,
                                onThumbnailScaleMode = viewModel::setThumbnailScaleMode,
                                onPreviewScaleMode = viewModel::setPreviewScaleMode,
                                onShowMediaBadges = viewModel::setShowMediaBadges,
                                onAnimationsEnabled = viewModel::setAnimationsEnabled,
                                onCameraGridEnabled = viewModel::setCameraGridEnabled,
                                onCameraAudioEnabled = viewModel::setCameraAudioEnabled,
                                onDeleteToTrash = viewModel::setDeleteToTrash,
                                onHideSensitiveContent = viewModel::setHideSensitiveContent,
                                onLanguage = viewModel::setLanguage,
                                onDeletionPassword = viewModel::setDeletionPassword,
                                onDebugEnabled = viewModel::setDebugEnabled,
                                onPermissions = { permissionLauncher.launch(mediaPermissions) },
                                onExportLog = { logExportLauncher.launch("keepg-debug.log") },
                                onClearLog = viewModel::clearDebugLog,
                                onShowLog = viewModel::debugSnapshot,
                                preferences = container.preferences,
                                onClearThumbnails = {
                                    scope.launch(Dispatchers.IO) {
                                        coil.Coil.imageLoader(context).memoryCache?.clear()
                                        coil.Coil.imageLoader(context).diskCache?.clear()
                                    }
                                },
                                onClearImageIndex = viewModel::clearImageTextIndex,
                                onOpenAgent = { agent.open() },
                            )
                        }
                        }
                    }
                }
            }

            if (cameraOpen && settings.agentEnabled) Box(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 8.dp, top = 64.dp)) { AgentEntryButton() }
            if (showLaunch) KeepGLaunchAnimation(Modifier.fillMaxSize())
        }

        if (preview != null && activePreviewSession != null) {
            val previousSession = activePreviewSession.moveBy(-1)
            val nextSession = activePreviewSession.moveBy(1)
            MediaPreviewDialogV2(
                photo = preview,
                lock = findLock(preview, locks),
                collections = collections,
                isFavorite = preview.mediaId in favorites,
                fullFeatures = fullFeatures,
                swipeNavigationEnabled = settings.previewSwipeNavigation,
                previewScaleMode = settings.previewScaleMode,
                canNavigatePrevious = previousSession != null,
                canNavigateNext = nextSession != null,
                onPrevious = { navigatePreview(-1) },
                onNext = { navigatePreview(1) },
                onDismiss = { previewSession = null },
                onFavorite = { viewModel.toggleFavorite(preview.mediaId) },
                onShare = { password -> shareMedia(listOf(preview), password) },
                onDelete = { removeMedia(listOf(preview)) },
                hasDeletionPassword = viewModel.hasDeletionPassword(),
                verifyDeletionPassword = viewModel::verifyDeletionPassword,
                setDeletionPassword = viewModel::setDeletionPassword,
                onLock = { lockTarget = LockTargetV2("PHOTO", preview.mediaId.toString(), preview.displayName, preview) },
                onRemoveLock = viewModel::removeLock,
                onVault = { viewModel.importToVault(preview) },
                onAnalyze = { viewModel.analyze(preview) },
                onCollection = { viewModel.addToCollection(it, preview.mediaId) },
                onEdit = { operation, strength -> viewModel.editMedia(preview, operation, strength) },
                onAdvancedEdit = { request -> viewModel.editAdvanced(preview, request) },
                onDetectLinks = { x, y -> viewModel.detectLinks(preview, x, y) },
                onRepair = { useCurrentTime -> viewModel.repairMedia(preview, useCurrentTime) },
                onRename = { newName -> viewModel.renameMedia(preview, newName) },
                onRefresh = viewModel::refresh,
            )
        }

        shareTarget?.let { media ->
            ShareDialog({ shareTarget = null }) { password ->
                shareTarget = null
                shareMedia(media, password)
            }
        }
        deleteTarget?.let { media ->
            DeletePasswordDialog(
                hasPassword = viewModel.hasDeletionPassword(),
                verify = viewModel::verifyDeletionPassword,
                setPassword = viewModel::setDeletionPassword,
                onDismiss = { deleteTarget = null },
                onAuthorized = {
                    deleteTarget = null
                    removeMedia(media)
                },
            )
        }
        if (batchCollection) {
            AlertDialog(
                onDismissRequest = { batchCollection = false },
                title = { Text(tr("Collection")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        collections.forEach { collection ->
                            TextButton(
                                onClick = {
                                    viewModel.addMediaToCollection(collection.id, selectedPhotos.map { it.mediaId })
                                    viewModel.notifyUser(localizedFormat("Added %s items to %s", selectedPhotos.size, collection.name))
                                    batchCollection = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(collection.name, Modifier.weight(1f)) }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton({ batchCollection = false }) { Text(tr("Cancel")) } },
            )
        }
        if (batchProtect) {
            AlertDialog(
                onDismissRequest = { batchProtect = false },
                title = { Text(trf("Protect %s selected items", selectedPhotos.size)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(::protectSelectedWithDevice, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text(tr("Device credential"))
                        }
                        OutlinedTextField(batchProtectPassword, { batchProtectPassword = it }, Modifier.fillMaxWidth(), label = { Text(tr("Password")) }, singleLine = true)
                        OutlinedButton(
                            enabled = batchProtectPassword.length >= 6,
                            onClick = { protectSelectedWithPassword(batchProtectPassword) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Icon(Icons.Default.Password, null); Spacer(Modifier.width(8.dp)); Text(tr("KeepG password")) }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton({ batchProtect = false }) { Text(tr("Cancel")) } },
            )
        }

        if (detectedLinks.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = viewModel::clearDetectedLinks,
                title = { Text(tr("Links found")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tr("KeepG detected these HTTP(S) links. Opening a link leaves KeepG and uses your external browser."))
                        detectedLinks.forEach { link ->
                            OutlinedButton({ openExternal(link.value) }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.OpenInBrowser, null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) { Text(link.value, maxLines = 2); Text(link.source, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(viewModel::clearDetectedLinks) { Text(tr("Close")) } },
            )
        }

        unlockLock?.let { lock ->
            PasswordDialog(
                tr("Unlock protected media"),
                unlockPassword,
                { unlockPassword = it },
                tr("Unlock"),
                {
                    if (viewModel.verifyPassword(lock, unlockPassword)) {
                        viewModel.unlockForSession(lock)
                        pendingPreviewAfterUnlockSession?.let { previewSession = it }
                        pendingPreviewAfterUnlockSession = null
                        unlockLock = null
                        unlockPassword = ""
                    } else viewModel.notifyUser(incorrectPasswordMessage)
                },
                { unlockLock = null; pendingPreviewAfterUnlockSession = null },
            )
        }

        lockTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { lockTarget = null },
                title = { Text(trf("Protect %s", target.label)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr("Choose a KeepG unlock method."))
                        FilledTonalButton(
                            {
                                requestDeviceAuthentication(
                                    localized("Confirm device lock"),
                                    {
                                        if (target.type == "PHOTO") viewModel.lockPhotoWithDevice(requireNotNull(target.photo)) else viewModel.lockAlbumWithDevice(target.id.toLong())
                                        lockTarget = null
                                    },
                                    viewModel::notifyUser,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text(tr("Device credential")) }
                        OutlinedButton({ passwordTarget = target; newPassword = ""; lockTarget = null }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Password, null); Spacer(Modifier.width(8.dp)); Text(tr("KeepG password"))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton({ lockTarget = null }) { Text(tr("Cancel")) } },
            )
        }

        passwordTarget?.let { target ->
            PasswordDialog(
                trf("Set password for %s", target.label),
                newPassword,
                { newPassword = it },
                tr("Protect"),
                {
                    if (newPassword.length < 6) viewModel.notifyUser(minimumPasswordMessage)
                    else {
                        if (target.type == "PHOTO") viewModel.lockPhotoWithPassword(requireNotNull(target.photo), newPassword) else viewModel.lockAlbumWithPassword(target.id.toLong(), newPassword)
                        passwordTarget = null
                        newPassword = ""
                    }
                },
                { passwordTarget = null },
            )
        }
        val agentMedia = photos.filter { isGridMediaVisible(it, locks, unlocked) }
        val agentCatalogMedia = (agentMedia.filter { it.mediaId in selectedIds } + agentMedia).distinctBy { it.mediaId }.take(100)
        val agentCatalog = org.json.JSONObject()
            .put("media", org.json.JSONArray().also { array -> agentCatalogMedia.forEach { item ->
                array.put(org.json.JSONObject().put("id", item.mediaId).put("name", item.displayName.take(120)).put("album", item.bucketName.take(80)).put("mime", item.mimeType))
            } })
            .put("collections", org.json.JSONArray().also { array -> collections.take(50).forEach { item -> array.put(org.json.JSONObject().put("id", item.id).put("name", item.name)) } })
            .toString()
        AgentHost(agent, settings, agentCatalog, agentCatalogMedia.map { it.mediaId }.toSet(), agentMedia.associate { it.mediaId to it.displayName }) { action ->
            try {
                require(settings.agentAllowTools)
                val media = action.mediaIds.map { id -> photosById[id]?.takeIf { isGridMediaVisible(it, locks, unlocked) } ?: error("Unavailable media") }
                require(!action.type.mediaRequired || media.isNotEmpty())
                val fullOnly = setOf(AgentActionType.PROTECT, AgentActionType.VAULT, AgentActionType.ANALYZE, AgentActionType.EDIT, AgentActionType.REPAIR, AgentActionType.INDEX_TEXT, AgentActionType.CLEAR_INDEX)
                require(fullFeatures || action.type !in fullOnly)
                fun targetCollection() = collections.firstOrNull { it.id == action.collectionId } ?: error("Unavailable collection")
                fun name() = action.argument.trim().also { require(it.isNotBlank() && it.length <= 160 && '/' !in it && '\\' !in it) }
                when (action.type) {
                    AgentActionType.NAVIGATE -> { val destination = TabV2.valueOf(action.argument); require(destination in tabs); cameraOpen = false; tabName = destination.name; viewModel.clearSelection() }
                    AgentActionType.SEARCH -> { viewModel.setSearchQuery(action.argument); viewModel.setTypeFilter(MediaTypeFilter.valueOf(action.value.ifBlank { "ALL" })); tabName = TabV2.PHOTOS.name }
                    AgentActionType.SELECT -> { selectMedia(media); tabName = TabV2.PHOTOS.name }
                    AgentActionType.FAVORITE -> favoriteMedia(media)
                    AgentActionType.SHARE -> shareTarget = media
                    AgentActionType.DELETE -> deleteTarget = media
                    AgentActionType.PROTECT -> { selectMedia(media); tabName = TabV2.PHOTOS.name; batchProtect = true }
                    AgentActionType.VAULT -> vaultMedia(media)
                    AgentActionType.ANALYZE -> analyzeMedia(media)
                    AgentActionType.OPEN -> { require(media.size == 1); openMedia(media.single(), media.map { it.mediaId }) }
                    AgentActionType.EDIT -> { require(media.size == 1); viewModel.editMedia(media.single(), MediaEditOperation.valueOf(action.argument), .35f) }
                    AgentActionType.RENAME -> { require(media.size == 1); viewModel.renameMedia(media.single(), name()) }
                    AgentActionType.REPAIR -> { require(media.size == 1); viewModel.repairMedia(media.single(), false) }
                    AgentActionType.CREATE_COLLECTION -> viewModel.createCollection(name())
                    AgentActionType.ADD_TO_COLLECTION -> viewModel.addMediaToCollection(targetCollection().id, media.map { it.mediaId })
                    AgentActionType.REMOVE_FROM_COLLECTION -> media.forEach { viewModel.removeFromCollection(targetCollection().id, it.mediaId) }
                    AgentActionType.RENAME_COLLECTION -> viewModel.renameCollection(targetCollection().id, name())
                    AgentActionType.CLEAR_COLLECTION -> viewModel.clearCollection(targetCollection().id)
                    AgentActionType.DELETE_COLLECTION -> viewModel.deleteCollection(targetCollection().id)
                    AgentActionType.REFRESH -> viewModel.refresh()
                    AgentActionType.CAMERA -> cameraOpen = true
                    AgentActionType.INDEX_TEXT -> viewModel.indexImageText(false)
                    AgentActionType.CLEAR_INDEX -> viewModel.clearImageTextIndex()
                    AgentActionType.SETTINGS -> when (action.argument) {
                        "theme" -> container.preferences.setThemeMode(ThemeMode.valueOf(action.value))
                        "columns" -> viewModel.setGridColumns(action.value.toInt().also { require(it in 2..8) })
                        "autoplay" -> viewModel.setVideoPreviewAutoPlay(action.value.toBooleanStrict())
                        "animations" -> viewModel.setAnimationsEnabled(action.value.toBooleanStrict())
                        "language" -> viewModel.setLanguage(AppLanguage.valueOf(action.value))
                        "sort" -> viewModel.setSortMode(MediaSortMode.valueOf(action.value))
                        "descending" -> viewModel.setSortDescending(action.value.toBooleanStrict())
                        else -> error("Unsupported setting")
                    }
                }
                viewModel.notifyUser(localized("Action requested; review any further confirmation"))
            } catch (error: Exception) {
                viewModel.notifyUser(localized("Action rejected: unavailable target, permission or arguments"))
            }
        }
    }
}
