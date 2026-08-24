package com.yanagikh.keepg.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.yanagikh.keepg.KeepGApplication
import com.yanagikh.keepg.MainViewModel
import com.yanagikh.keepg.data.*
import com.yanagikh.keepg.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as KeepGApplication).container }
    val scope = rememberCoroutineScope()
    val photos by viewModel.photos.collectAsState()
    val visiblePhotos by viewModel.visiblePhotos.collectAsState()
    val locks by viewModel.locks.collectAsState()
    val faces by viewModel.faces.collectAsState()
    val people by viewModel.people.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val vault by viewModel.vault.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val collectionItems by viewModel.collectionItems.collectAsState()
    val unlocked by viewModel.sessionUnlocked.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val detectedLinks by viewModel.detectedLinks.collectAsState()
    val debugEnabled by viewModel.debugEnabled.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val selectedIds by viewModel.selectedMediaIds.collectAsState()
    val selectedPhotos by viewModel.selectedPhotos.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val sizeFilter by viewModel.sizeFilter.collectAsState()
    val extensionFilter by viewModel.extensionFilter.collectAsState()
    val fullFeatures = viewModel.fullFeatures

    CompositionLocalProvider(LocalAppLanguage provides settings.language) {
        val tabs = if (fullFeatures) TabV2.entries else listOf(TabV2.PHOTOS, TabV2.ALBUMS, TabV2.SETTINGS)
        val incorrectPasswordMessage = tr("Incorrect password")
        val minimumPasswordMessage = tr("Use at least 6 characters")
        var tabName by rememberSaveable { mutableStateOf(TabV2.PHOTOS.name) }
        val tab = tabs.firstOrNull { it.name == tabName } ?: tabs.first()
        var previewId by rememberSaveable { mutableStateOf<Long?>(null) }
        val preview = previewId?.let { id -> photos.firstOrNull { it.mediaId == id } }
        var unlockLock by remember { mutableStateOf<LockEntity?>(null) }
        var pendingPreviewAfterUnlockId by rememberSaveable { mutableStateOf<Long?>(null) }
        var unlockPassword by remember { mutableStateOf("") }
        var lockTarget by remember { mutableStateOf<LockTargetV2?>(null) }
        var passwordTarget by remember { mutableStateOf<LockTargetV2?>(null) }
        var newPassword by remember { mutableStateOf("") }
        var localMessage by remember { mutableStateOf<String?>(null) }
        var shareTarget by remember { mutableStateOf<List<PhotoEntity>?>(null) }
        var deleteTarget by remember { mutableStateOf<List<PhotoEntity>?>(null) }
        var batchCollection by remember { mutableStateOf(false) }
        var batchProtect by remember { mutableStateOf(false) }
        var batchProtectPassword by remember { mutableStateOf("") }
        val snackbar = remember { SnackbarHostState() }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
        val logExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(viewModel::exportDebugLog) }
        val removalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                previewId = null
                viewModel.clearSelection()
                viewModel.refresh()
            } else {
                localMessage = "Delete request cancelled"
            }
        }

        LaunchedEffect(Unit) { permissionLauncher.launch(mediaPermissions) }
        LaunchedEffect(message, localMessage) {
            (localMessage ?: message)?.let {
                snackbar.showSnackbar(it)
                localMessage = null
                viewModel.clearMessage()
            }
        }

        fun unlock(lock: LockEntity, afterPreview: PhotoEntity? = null) {
            if (lock.authType == "DEVICE") {
                requestDeviceAuthentication(
                    "Unlock protected media",
                    {
                        viewModel.unlockForSession(lock)
                        afterPreview?.let { previewId = it.mediaId }
                    },
                    { localMessage = it },
                )
            } else {
                unlockLock = lock
                pendingPreviewAfterUnlockId = afterPreview?.mediaId
                unlockPassword = ""
            }
        }

        fun openMedia(media: PhotoEntity) {
            val lock = if (fullFeatures) findLock(media, locks) else null
            if (lock == null || isUnlocked(lock, unlocked)) previewId = media.mediaId else unlock(lock, media)
        }

        fun shareMedia(media: List<PhotoEntity>, password: String?) {
            if (media.isEmpty()) return
            viewModel.prepareShare(media, password) { chooser ->
                runCatching { context.startActivity(chooser) }.onFailure { localMessage = "No compatible share target is available" }
            }
        }

        fun removeMedia(media: List<PhotoEntity>) {
            if (media.isEmpty()) return
            val sender = viewModel.createRemovalIntentSender(media)
            if (sender != null) removalLauncher.launch(IntentSenderRequest.Builder(sender).build())
            else {
                previewId = null
                viewModel.removeLegacy(media)
            }
        }

        fun selectMedia(media: List<PhotoEntity>) {
            viewModel.clearSelection()
            media.distinctBy { it.mediaId }.forEach { viewModel.toggleSelection(it.mediaId) }
            tabName = TabV2.PHOTOS.name
        }

        fun favoriteMedia(media: List<PhotoEntity>) {
            val existing = favorites
            media.distinctBy { it.mediaId }.forEach { if (it.mediaId !in existing) viewModel.toggleFavorite(it.mediaId) }
        }

        fun vaultMedia(media: List<PhotoEntity>) {
            if (!fullFeatures || media.isEmpty()) return
            scope.launch(Dispatchers.IO) {
                val unique = media.distinctBy { it.mediaId }
                var stored = 0
                unique.forEach { item -> if (runCatching { container.vault.importEncrypted(item) }.isSuccess) stored++ }
                localMessage = "Stored $stored/${unique.size} encrypted Vault copies"
            }
        }

        fun analyzeMedia(media: List<PhotoEntity>) {
            if (!fullFeatures) return
            scope.launch(Dispatchers.IO) {
                val images = media.distinctBy { it.mediaId }.filter { it.mimeType.startsWith("image/") }
                var analyzed = 0
                images.forEach { if (runCatching { container.faceAnalysis.analyze(it) }.isSuccess) analyzed++ }
                localMessage = "Analyzed $analyzed/${images.size} selected images"
            }
        }

        fun protectSelectedWithDevice() {
            val target = selectedPhotos.distinctBy { it.mediaId }
            if (target.isEmpty()) return
            requestDeviceAuthentication(
                "Protect selected media",
                {
                    scope.launch(Dispatchers.IO) {
                        target.forEach { media -> container.dao.upsertLock(LockEntity("PHOTO", media.mediaId.toString(), "DEVICE")) }
                        localMessage = "Protected ${target.size} items"
                    }
                    batchProtect = false
                },
                { localMessage = it },
            )
        }

        fun protectSelectedWithPassword(password: String) {
            val target = selectedPhotos.distinctBy { it.mediaId }
            if (target.isEmpty() || password.length < 6) return
            scope.launch(Dispatchers.Default) {
                val encoded = PasswordHasher.create(password.toCharArray())
                target.forEach { media ->
                    container.dao.upsertLock(
                        LockEntity(
                            targetType = "PHOTO",
                            targetId = media.mediaId.toString(),
                            authType = "PASSWORD",
                            passwordHash = encoded.hash,
                            passwordSalt = encoded.salt,
                        )
                    )
                }
                localMessage = "Protected ${target.size} items"
            }
            batchProtect = false
            batchProtectPassword = ""
        }

        fun openExternal(url: String) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { localMessage = "No browser could open this link" }
        }

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
                        if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
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
                when (tab) {
                    TabV2.PHOTOS -> LibraryScreenV2(
                        photos = visiblePhotos,
                        locks = locks,
                        unlocked = unlocked,
                        favorites = favorites,
                        selectedIds = selectedIds,
                        settings = settings,
                        query = query,
                        typeFilter = typeFilter,
                        sizeFilter = sizeFilter,
                        extensionFilter = extensionFilter,
                        onQuery = viewModel::setSearchQuery,
                        onTypeFilter = viewModel::setTypeFilter,
                        onSizeFilter = viewModel::setSizeFilter,
                        onExtensionFilter = viewModel::setExtensionFilter,
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
                        photos = visiblePhotos,
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
                        onLockAlbum = { id, name -> lockTarget = LockTargetV2("ALBUM", id.toString(), name) },
                        onUnlock = { unlock(it) },
                        onSelectMedia = ::selectMedia,
                        onFavoriteMedia = ::favoriteMedia,
                        onShareMedia = { shareTarget = it },
                        onDeleteMedia = { deleteTarget = it },
                        onVaultMedia = ::vaultMedia,
                        allowProtection = fullFeatures,
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
                        onDeleteToTrash = viewModel::setDeleteToTrash,
                        onHideSensitiveContent = viewModel::setHideSensitiveContent,
                        onLanguage = viewModel::setLanguage,
                        onDeletionPassword = viewModel::setDeletionPassword,
                        onDebugEnabled = viewModel::setDebugEnabled,
                        onPermissions = { permissionLauncher.launch(mediaPermissions) },
                        onExportLog = { logExportLauncher.launch("keepg-debug.log") },
                        onClearLog = viewModel::clearDebugLog,
                        onShowLog = viewModel::debugSnapshot,
                    )
                }
            }
        }

        preview?.let { media ->
            MediaPreviewDialogV2(
                photo = media,
                lock = findLock(media, locks),
                collections = collections,
                isFavorite = media.mediaId in favorites,
                fullFeatures = fullFeatures,
                onDismiss = { previewId = null },
                onFavorite = { viewModel.toggleFavorite(media.mediaId) },
                onShare = { password -> shareMedia(listOf(media), password) },
                onDelete = { removeMedia(listOf(media)) },
                hasDeletionPassword = viewModel.hasDeletionPassword(),
                verifyDeletionPassword = viewModel::verifyDeletionPassword,
                setDeletionPassword = viewModel::setDeletionPassword,
                onLock = { lockTarget = LockTargetV2("PHOTO", media.mediaId.toString(), media.displayName, media) },
                onRemoveLock = viewModel::removeLock,
                onVault = { viewModel.importToVault(media) },
                onAnalyze = { viewModel.analyze(media) },
                onCollection = { viewModel.addToCollection(it, media.mediaId) },
                onEdit = { operation, strength -> viewModel.editMedia(media, operation, strength) },
                onAdvancedEdit = { request -> viewModel.editAdvanced(media, request) },
                onDetectLinks = { x, y -> viewModel.detectLinks(media, x, y) },
                onRepair = { useCurrentTime -> viewModel.repairMedia(media, useCurrentTime) },
                onRename = { newName -> viewModel.renameMedia(media, newName) },
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
                                    selectedPhotos.distinctBy { it.mediaId }.forEach { viewModel.addToCollection(collection.id, it.mediaId) }
                                    localMessage = "Added ${selectedPhotos.size} items to ${collection.name}"
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
                title = { Text("Protect ${selectedPhotos.size} selected items") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(::protectSelectedWithDevice, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Device credential")
                        }
                        OutlinedTextField(batchProtectPassword, { batchProtectPassword = it }, Modifier.fillMaxWidth(), label = { Text(tr("Password")) }, singleLine = true)
                        OutlinedButton(
                            enabled = batchProtectPassword.length >= 6,
                            onClick = { protectSelectedWithPassword(batchProtectPassword) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Icon(Icons.Default.Password, null); Spacer(Modifier.width(8.dp)); Text("KeepG password") }
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
                        Text("KeepG detected these HTTP(S) links. Opening a link leaves KeepG and uses your external browser.")
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
                "Unlock protected media",
                unlockPassword,
                { unlockPassword = it },
                tr("Unlock"),
                {
                    if (viewModel.verifyPassword(lock, unlockPassword)) {
                        viewModel.unlockForSession(lock)
                        pendingPreviewAfterUnlockId?.let { previewId = it }
                        pendingPreviewAfterUnlockId = null
                        unlockLock = null
                        unlockPassword = ""
                    } else localMessage = incorrectPasswordMessage
                },
                { unlockLock = null; pendingPreviewAfterUnlockId = null },
            )
        }

        lockTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { lockTarget = null },
                title = { Text("Protect ${target.label}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Choose a KeepG unlock method.")
                        FilledTonalButton(
                            {
                                requestDeviceAuthentication(
                                    "Confirm device lock",
                                    {
                                        if (target.type == "PHOTO") viewModel.lockPhotoWithDevice(requireNotNull(target.photo)) else viewModel.lockAlbumWithDevice(target.id.toLong())
                                        lockTarget = null
                                    },
                                    { localMessage = it },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Device credential") }
                        OutlinedButton({ passwordTarget = target; newPassword = ""; lockTarget = null }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Password, null); Spacer(Modifier.width(8.dp)); Text("KeepG password")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton({ lockTarget = null }) { Text(tr("Cancel")) } },
            )
        }

        passwordTarget?.let { target ->
            PasswordDialog(
                "Set password for ${target.label}",
                newPassword,
                { newPassword = it },
                tr("Protect"),
                {
                    if (newPassword.length < 6) localMessage = minimumPasswordMessage
                    else {
                        if (target.type == "PHOTO") viewModel.lockPhotoWithPassword(requireNotNull(target.photo), newPassword) else viewModel.lockAlbumWithPassword(target.id.toLong(), newPassword)
                        passwordTarget = null
                        newPassword = ""
                    }
                },
                { passwordTarget = null },
            )
        }
    }
}
