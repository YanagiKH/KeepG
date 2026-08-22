package com.yanagikh.keepg.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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

private enum class Tab(val label: String, val icon: ImageVector) {
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
    val fullFeatures = viewModel.fullFeatures

    val tabs = if (fullFeatures) Tab.entries else listOf(Tab.PHOTOS, Tab.ALBUMS, Tab.SETTINGS)
    var tab by remember { mutableStateOf(Tab.PHOTOS) }
    var photoDetail by remember { mutableStateOf<PhotoEntity?>(null) }
    var unlockLock by remember { mutableStateOf<LockEntity?>(null) }
    var unlockPassword by remember { mutableStateOf("") }
    var lockTarget by remember { mutableStateOf<LockTarget?>(null) }
    var passwordTarget by remember { mutableStateOf<LockTarget?>(null) }
    var newPassword by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
    val logExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let(viewModel::exportDebugLog)
    }

    LaunchedEffect(Unit) { permissionLauncher.launch(mediaPermissions) }
    LaunchedEffect(message, localMessage) {
        (localMessage ?: message)?.let {
            snackbar.showSnackbar(it)
            localMessage = null
            viewModel.clearMessage()
        }
    }

    fun unlock(lock: LockEntity, after: () -> Unit = {}) {
        if (lock.authType == "DEVICE") {
            requestDeviceAuthentication(
                "Unlock protected media",
                { viewModel.unlockForSession(lock); after() },
                { localMessage = it },
            )
        } else {
            unlockLock = lock
            unlockPassword = ""
        }
    }

    fun openPhoto(photo: PhotoEntity) {
        if (!fullFeatures) {
            photoDetail = photo
            return
        }
        val lock = findLock(photo, locks)
        when {
            lock == null || isUnlocked(lock, unlocked) -> photoDetail = photo
            else -> unlock(lock) { photoDetail = photo }
        }
    }

    fun scanLinks(photo: PhotoEntity) {
        if (fullFeatures) viewModel.detectLinks(photo)
    }

    fun openExternal(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { localMessage = "No browser could open this link" }
    }

    fun openVideo(photo: PhotoEntity) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(photo.uri), photo.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }.onFailure { localMessage = "No compatible video player is installed" }
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
                    IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.PHOTOS -> PhotoGrid(photos, locks, unlocked, ::openPhoto, ::scanLinks)
                Tab.ALBUMS -> AlbumsScreen(
                    photos = photos,
                    locks = locks,
                    unlocked = unlocked,
                    collections = collections,
                    collectionItems = collectionItems,
                    onPhoto = ::openPhoto,
                    onLongPress = ::scanLinks,
                    onCreateCollection = viewModel::createCollection,
                    onLockAlbum = { id, name -> lockTarget = LockTarget("ALBUM", id.toString(), name) },
                    onUnlock = ::unlock,
                    allowProtection = fullFeatures,
                )
                Tab.SMART -> SmartScreen(photos, faces, people, rules, busy, viewModel)
                Tab.VAULT -> VaultScreen(vault, viewModel::removeVaultItem)
                Tab.SETTINGS -> SettingsScreen(
                    photoCount = photos.size,
                    faceCount = faces.size,
                    lockCount = locks.size,
                    fullFeatures = fullFeatures,
                    debugEnabled = debugEnabled,
                    onDebugEnabled = viewModel::setDebugEnabled,
                    onPermissions = { permissionLauncher.launch(mediaPermissions) },
                    onExportLog = { logExportLauncher.launch("keepg-debug.log") },
                    onClearLog = viewModel::clearDebugLog,
                    onShowLog = viewModel::debugSnapshot,
                )
            }
        }
    }

    photoDetail?.let { photo ->
        PhotoDialog(
            photo = photo,
            lock = findLock(photo, locks),
            collections = collections,
            fullFeatures = fullFeatures,
            onDismiss = { photoDetail = null },
            onLock = { lockTarget = LockTarget("PHOTO", photo.mediaId.toString(), photo.displayName, photo) },
            onRemoveLock = viewModel::removeLock,
            onVault = { viewModel.importToVault(photo) },
            onAnalyze = { viewModel.analyze(photo) },
            onCollection = { viewModel.addToCollection(it, photo.mediaId) },
            onEdit = { operation, strength -> viewModel.editMedia(photo, operation, strength) },
            onDetectLinks = { viewModel.detectLinks(photo) },
            onRepair = { useCurrentTime -> viewModel.repairMedia(photo, useCurrentTime) },
            onOpenVideo = { openVideo(photo) },
        )
    }

    if (detectedLinks.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::clearDetectedLinks,
            title = { Text("Links found") },
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
            confirmButton = { TextButton(viewModel::clearDetectedLinks) { Text("Close") } },
        )
    }

    unlockLock?.let { lock ->
        PasswordDialog(
            "Unlock protected media",
            unlockPassword,
            { unlockPassword = it },
            "Unlock",
            {
                if (viewModel.verifyPassword(lock, unlockPassword)) {
                    viewModel.unlockForSession(lock)
                    unlockLock = null
                    unlockPassword = ""
                } else localMessage = "Incorrect password"
            },
            { unlockLock = null },
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
            dismissButton = { TextButton({ lockTarget = null }) { Text("Cancel") } },
        )
    }

    passwordTarget?.let { target ->
        PasswordDialog(
            "Set password for ${target.label}",
            newPassword,
            { newPassword = it },
            "Protect",
            {
                if (newPassword.length < 6) localMessage = "Use at least 6 characters"
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
