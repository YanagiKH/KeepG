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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yanagikh.keepg.MainViewModel
import com.yanagikh.keepg.data.*

private enum class Tab(val labelKey: String, val icon: ImageVector) {
    PHOTOS("Photos", Icons.Default.PhotoLibrary),
    ALBUMS("Albums", Icons.Default.Folder),
    SMART("Smart", Icons.Default.AutoAwesome),
    VAULT("Vault", Icons.Default.Lock),
    SETTINGS("Settings", Icons.Default.Settings),
}

private data class LockTarget(val type: String, val id: String, val label: String, val photo: PhotoEntity? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepGApp(
    viewModel: MainViewModel,
    requestDeviceAuthentication: (String, () -> Unit, (String) -> Unit) -> Unit,
    mediaPermissions: Array<String>,
) {
    val context = LocalContext.current
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
        val tabs = if (fullFeatures) Tab.entries else listOf(Tab.PHOTOS, Tab.ALBUMS, Tab.SETTINGS)
        val incorrectPasswordMessage = tr("Incorrect password")
        val minimumPasswordMessage = tr("Use at least 6 characters")
        var tab by remember { mutableStateOf(Tab.PHOTOS) }
        var preview by remember { mutableStateOf<PhotoEntity?>(null) }
        var unlockLock by remember { mutableStateOf<LockEntity?>(null) }
        var pendingPreviewAfterUnlock by remember { mutableStateOf<PhotoEntity?>(null) }
        var unlockPassword by remember { mutableStateOf("") }
        var lockTarget by remember { mutableStateOf<LockTarget?>(null) }
        var passwordTarget by remember { mutableStateOf<LockTarget?>(null) }
        var newPassword by remember { mutableStateOf("") }
        var localMessage by remember { mutableStateOf<String?>(null) }
        var batchShare by remember { mutableStateOf(false) }
        var batchDelete by remember { mutableStateOf(false) }
        val snackbar = remember { SnackbarHostState() }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
        val logExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let(viewModel::exportDebugLog) }
        val removalLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                preview = null
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
                        afterPreview?.let { preview = it }
                    },
                    { localMessage = it },
                )
            } else {
                unlockLock = lock
                pendingPreviewAfterUnlock = afterPreview
                unlockPassword = ""
            }
        }

        fun openMedia(media: PhotoEntity) {
            val lock = if (fullFeatures) findLock(media, locks) else null
            when {
                lock == null || isUnlocked(lock, unlocked) -> preview = media
                else -> unlock(lock, media)
            }
        }

        fun shareMedia(media: List<PhotoEntity>, password: String?) {
            if (media.isEmpty()) return
            viewModel.prepareShare(media, password) { chooser ->
                runCatching { context.startActivity(chooser) }
                    .onFailure { localMessage = "No compatible share target is available" }
            }
        }

        fun removeMedia(media: List<PhotoEntity>) {
            if (media.isEmpty()) return
            val sender = viewModel.createRemovalIntentSender(media)
            if (sender != null) {
                removalLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } else {
                preview = null
                viewModel.removeLegacy(media)
            }
        }

        fun openExternal(url: String) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { localMessage = "No browser could open this link" }
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
                            onClick = { tab = item; viewModel.clearSelection() },
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
                    Tab.PHOTOS -> LibraryScreen(
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
                        onFavoriteSelected = viewModel::favoriteSelected,
                        onShareSelected = { if (selectedPhotos.isNotEmpty()) batchShare = true },
                        onDeleteSelected = { if (selectedPhotos.isNotEmpty()) batchDelete = true },
                    )
                    Tab.ALBUMS -> AlbumsScreen(
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
                        onLockAlbum = { id, name -> lockTarget = LockTarget("ALBUM", id.toString(), name) },
                        onUnlock = { unlock(it) },
                        allowProtection = fullFeatures,
                    )
                    Tab.SMART -> SmartScreen(photos, faces, people, rules, busy, viewModel)
                    Tab.VAULT -> VaultScreen(vault, viewModel::removeVaultItem)
                    Tab.SETTINGS -> GallerySettingsScreen(
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
            MediaPreviewDialog(
                photo = media,
                lock = findLock(media, locks),
                collections = collections,
                isFavorite = media.mediaId in favorites,
                fullFeatures = fullFeatures,
                onDismiss = { preview = null },
                onFavorite = { viewModel.toggleFavorite(media.mediaId) },
                onShare = { password -> shareMedia(listOf(media), password) },
                onDelete = { removeMedia(listOf(media)) },
                hasDeletionPassword = viewModel.hasDeletionPassword(),
                verifyDeletionPassword = viewModel::verifyDeletionPassword,
                setDeletionPassword = viewModel::setDeletionPassword,
                onLock = { lockTarget = LockTarget("PHOTO", media.mediaId.toString(), media.displayName, media) },
                onRemoveLock = viewModel::removeLock,
                onVault = { viewModel.importToVault(media) },
                onAnalyze = { viewModel.analyze(media) },
                onCollection = { viewModel.addToCollection(it, media.mediaId) },
                onEdit = { operation, strength -> viewModel.editMedia(media, operation, strength) },
                onAdvancedEdit = { request -> viewModel.editAdvanced(media, request) },
                onDetectLinks = { x, y -> viewModel.detectLinks(media, x, y) },
                onRepair = { useCurrentTime -> viewModel.repairMedia(media, useCurrentTime) },
                onRename = { newName -> viewModel.renameMedia(media, newName) },
            )
        }

        if (batchShare) {
            ShareDialog({ batchShare = false }) { password ->
                batchShare = false
                shareMedia(selectedPhotos, password)
            }
        }
        if (batchDelete) {
            DeletePasswordDialog(
                hasPassword = viewModel.hasDeletionPassword(),
                verify = viewModel::verifyDeletionPassword,
                setPassword = viewModel::setDeletionPassword,
                onDismiss = { batchDelete = false },
                onAuthorized = {
                    batchDelete = false
                    removeMedia(selectedPhotos)
                },
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
                                Column(Modifier.weight(1f)) {
                                    Text(link.value, maxLines = 2)
                                    Text(link.source, style = MaterialTheme.typography.labelSmall)
                                }
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
                        pendingPreviewAfterUnlock?.let { preview = it }
                        pendingPreviewAfterUnlock = null
                        unlockLock = null
                        unlockPassword = ""
                    } else localMessage = incorrectPasswordMessage
                },
                { unlockLock = null; pendingPreviewAfterUnlock = null },
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
                                        if (target.type == "PHOTO") viewModel.lockPhotoWithDevice(requireNotNull(target.photo))
                                        else viewModel.lockAlbumWithDevice(target.id.toLong())
                                        lockTarget = null
                                    },
                                    { localMessage = it },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Device credential") }
                        OutlinedButton(
                            { passwordTarget = target; newPassword = ""; lockTarget = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Icon(Icons.Default.Password, null); Spacer(Modifier.width(8.dp)); Text("KeepG password") }
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
                        if (target.type == "PHOTO") viewModel.lockPhotoWithPassword(requireNotNull(target.photo), newPassword)
                        else viewModel.lockAlbumWithPassword(target.id.toLong(), newPassword)
                        passwordTarget = null
                        newPassword = ""
                    }
                },
                { passwordTarget = null },
            )
        }
    }
}
