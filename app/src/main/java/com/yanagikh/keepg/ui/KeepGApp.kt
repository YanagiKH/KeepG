package com.yanagikh.keepg.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.yanagikh.keepg.MainViewModel
import com.yanagikh.keepg.data.*
import java.text.DateFormat
import java.util.Date

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

    var tab by remember { mutableStateOf(Tab.PHOTOS) }
    var photoDetail by remember { mutableStateOf<PhotoEntity?>(null) }
    var unlockLock by remember { mutableStateOf<LockEntity?>(null) }
    var unlockPassword by remember { mutableStateOf("") }
    var lockTarget by remember { mutableStateOf<LockTarget?>(null) }
    var passwordTarget by remember { mutableStateOf<LockTarget?>(null) }
    var newPassword by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refresh()
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
            requestDeviceAuthentication("Unlock protected media", {
                viewModel.unlockForSession(lock)
                after()
            }, { localMessage = it })
        } else {
            unlockLock = lock
            unlockPassword = ""
        }
    }

    fun openPhoto(photo: PhotoEntity) {
        val lock = findLock(photo, locks)
        when {
            lock == null || isUnlocked(lock, unlocked) -> photoDetail = photo
            else -> unlock(lock) { photoDetail = photo }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("K", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black) }
                        }
                        Spacer(Modifier.width(10.dp)); Text("KeepG", fontWeight = FontWeight.Bold)
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
                Tab.entries.forEach { item ->
                    NavigationBarItem(tab == item, { tab = item }, { Icon(item.icon, item.label) }, label = { Text(item.label) })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.PHOTOS -> PhotoGrid(photos, locks, unlocked, ::openPhoto)
                Tab.ALBUMS -> AlbumsScreen(photos, locks, unlocked, collections, collectionItems, ::openPhoto,
                    viewModel::createCollection,
                    { id, name -> lockTarget = LockTarget("ALBUM", id.toString(), name) },
                    ::unlock)
                Tab.SMART -> SmartScreen(photos, faces, people, rules, busy, viewModel)
                Tab.VAULT -> VaultScreen(vault, viewModel::removeVaultItem)
                Tab.SETTINGS -> SettingsScreen(photos.size, faces.size, locks.size) { permissionLauncher.launch(mediaPermissions) }
            }
        }
    }

    photoDetail?.let { photo ->
        PhotoDialog(photo, findLock(photo, locks), collections, { photoDetail = null },
            { lockTarget = LockTarget("PHOTO", photo.mediaId.toString(), photo.displayName, photo) },
            viewModel::removeLock,
            { viewModel.importToVault(photo) },
            { viewModel.analyze(photo) },
            { viewModel.addToCollection(it, photo.mediaId) })
    }

    unlockLock?.let { lock ->
        PasswordDialog("Unlock protected media", unlockPassword, { unlockPassword = it }, "Unlock", {
            if (viewModel.verifyPassword(lock, unlockPassword)) {
                viewModel.unlockForSession(lock); unlockLock = null; unlockPassword = ""
            } else localMessage = "Incorrect password"
        }, { unlockLock = null })
    }

    lockTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { lockTarget = null },
            title = { Text("Protect ${target.label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose a KeepG unlock method.")
                    FilledTonalButton({
                        requestDeviceAuthentication("Confirm device lock", {
                            if (target.type == "PHOTO") viewModel.lockPhotoWithDevice(requireNotNull(target.photo)) else viewModel.lockAlbumWithDevice(target.id.toLong())
                            lockTarget = null
                        }, { localMessage = it })
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Device credential") }
                    OutlinedButton({ passwordTarget = target; newPassword = ""; lockTarget = null }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Password, null); Spacer(Modifier.width(8.dp)); Text("KeepG password")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ lockTarget = null }) { Text("Cancel") } },
        )
    }

    passwordTarget?.let { target ->
        PasswordDialog("Set password for ${target.label}", newPassword, { newPassword = it }, "Protect", {
            if (newPassword.length < 6) localMessage = "Use at least 6 characters" else {
                if (target.type == "PHOTO") viewModel.lockPhotoWithPassword(requireNotNull(target.photo), newPassword) else viewModel.lockAlbumWithPassword(target.id.toLong(), newPassword)
                passwordTarget = null; newPassword = ""
            }
        }, { passwordTarget = null })
    }
}
