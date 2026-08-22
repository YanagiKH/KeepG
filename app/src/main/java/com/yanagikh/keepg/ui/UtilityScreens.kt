package com.yanagikh.keepg.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.yanagikh.keepg.data.*
import java.text.DateFormat
import java.util.Date

@Composable
internal fun VaultScreen(vault: List<VaultItemEntity>, onRemove: (VaultItemEntity) -> Unit) {
    if (vault.isEmpty()) { EmptyState(Icons.Default.EnhancedEncryption, "Vault is empty", "Use Vault copy from a photo to create an AES-GCM encrypted private copy."); return }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item { AssistChip({}, { Text("Encrypted app-private storage") }, leadingIcon = { Icon(Icons.Default.Security, null) }) }
        items(vault, key = { it.id }) { item -> ListItem({ Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text("Encrypted ${DateFormat.getDateTimeInstance().format(Date(item.createdAt))}") }, leadingContent = { Icon(Icons.Default.Lock, null) }, trailingContent = { IconButton({ onRemove(item) }) { Icon(Icons.Default.Delete, "Remove") } }) }
    }
}

@Composable
internal fun SettingsScreen(photoCount: Int, faceCount: Int, lockCount: Int, onPermissions: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ElevatedCard { Column(Modifier.padding(16.dp)) { Text("Privacy first", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("KeepG needs no account and requests no Internet permission. Smart analysis and lock metadata stay on-device.") } } }
        item { Stat("Indexed photos", photoCount, Icons.Default.PhotoLibrary) }; item { Stat("Detected faces", faceCount, Icons.Default.Face) }; item { Stat("Protected targets", lockCount, Icons.Default.Lock) }
        item { ListItem({ Text("Media permissions") }, supportingContent = { Text("Grant or review Android photo and media-location access.") }, leadingContent = { Icon(Icons.Default.AdminPanelSettings, null) }, trailingContent = { TextButton(onPermissions) { Text("Grant") } }) }
        item { Text("A KeepG lock controls display inside KeepG. For confidentiality from other gallery apps, also create a Vault copy and remove the original through Android system controls. See SECURITY.md.") }
    }
}

@Composable internal fun Stat(label: String, value: Int, icon: ImageVector) = ListItem({ Text(label) }, leadingContent = { Icon(icon, null) }, trailingContent = { Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) })

@Composable
internal fun PhotoDialog(photo: PhotoEntity, lock: LockEntity?, collections: List<CollectionEntity>, onDismiss: () -> Unit, onLock: () -> Unit, onRemoveLock: (LockEntity) -> Unit, onVault: () -> Unit, onAnalyze: () -> Unit, onCollection: (Long) -> Unit) {
    var pickCollection by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            AsyncImage(Uri.parse(photo.uri), photo.displayName, Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
            Text(photo.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${photo.bucketName} · ${photo.width}×${photo.height} · ${DateFormat.getDateTimeInstance().format(Date(photo.dateTaken))}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton({ if (lock == null) onLock() else onRemoveLock(lock) }) { Icon(if (lock == null) Icons.Default.Lock else Icons.Default.LockOpen, null); Text(if (lock == null) " Protect" else " Remove lock") }
                FilledTonalButton(onVault) { Icon(Icons.Default.EnhancedEncryption, null); Text(" Vault copy") }
            }
            Row { TextButton(onAnalyze) { Text("Analyze") }; TextButton({ pickCollection = true }, enabled = collections.isNotEmpty()) { Text("Collection") }; Spacer(Modifier.weight(1f)); TextButton(onDismiss) { Text("Close") } }
        } }
    }
    if (pickCollection) AlertDialog(onDismissRequest = { pickCollection = false }, title = { Text("Add to collection") }, text = { LazyColumn { items(collections, key = { it.id }) { collection -> ListItem({ Text(collection.name) }, modifier = Modifier.clickable { onCollection(collection.id); pickCollection = false }) } } }, confirmButton = {}, dismissButton = { TextButton({ pickCollection = false }) { Text("Cancel") } })
}

@Composable
internal fun PasswordDialog(title: String, value: String, onValue: (String) -> Unit, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, onValue, label = { Text("Password") }, singleLine = true) }, confirmButton = { TextButton(onConfirm) { Text(confirm) } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

internal fun findLock(photo: PhotoEntity, locks: List<LockEntity>): LockEntity? = locks.firstOrNull { it.targetType == "PHOTO" && it.targetId == photo.mediaId.toString() } ?: locks.firstOrNull { it.targetType == "ALBUM" && it.targetId == photo.bucketId.toString() }
internal fun isUnlocked(lock: LockEntity, unlocked: Set<String>) = "${lock.targetType}:${lock.targetId}" in unlocked
internal fun ruleIcon(kind: String): ImageVector = when (kind) { "PERSON" -> Icons.Default.Person; "TIME" -> Icons.Default.Schedule; "LOCATION" -> Icons.Default.Place; "EXPRESSION" -> Icons.Default.SentimentSatisfied; else -> Icons.Default.AutoAwesome }
