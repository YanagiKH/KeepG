package com.yanagikh.keepg.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import com.yanagikh.keepg.advanced.MediaEditOperation
import com.yanagikh.keepg.data.*
import java.text.DateFormat
import java.util.Date

@Composable
internal fun VaultScreen(vault: List<VaultItemEntity>, onRemove: (VaultItemEntity) -> Unit) {
    if (vault.isEmpty()) {
        EmptyState(Icons.Default.EnhancedEncryption, tr("Vault is empty"), tr("Use Vault copy from a media item to create an AES-GCM encrypted private copy."))
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp)) {
        item { AssistChip({}, { Text(tr("Encrypted app-private storage")) }, leadingIcon = { Icon(Icons.Default.Security, null) }) }
        items(vault, key = { it.id }) { item ->
            ListItem(
                { Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(trf("Encrypted %s", DateFormat.getDateTimeInstance().format(Date(item.createdAt)))) },
                leadingContent = { Icon(Icons.Default.Lock, null) },
                trailingContent = { IconButton({ onRemove(item) }) { Icon(Icons.Default.Delete, tr("Remove")) } },
            )
        }
    }
}

@Composable
internal fun SettingsScreen(
    photoCount: Int,
    faceCount: Int,
    lockCount: Int,
    fullFeatures: Boolean,
    debugEnabled: Boolean,
    onDebugEnabled: (Boolean) -> Unit,
    onPermissions: () -> Unit,
    onExportLog: () -> Unit,
    onClearLog: () -> Unit,
    onShowLog: () -> String,
) {
    var showLog by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ElevatedCard {
                Column(Modifier.padding(16.dp)) {
                    Text(if (fullFeatures) "KeepG Full" else "KeepG Lite", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(tr(if (fullFeatures) "Local smart analysis, secure Vault, editing, QR/URL detection and repair tools are enabled." else "Lightweight edition: gallery, video browsing, albums and collections only."))
                }
            }
        }
        item { Stat(tr("Indexed media"), photoCount, Icons.Default.PhotoLibrary) }
        if (fullFeatures) item { Stat(tr("Detected faces"), faceCount, Icons.Default.Face) }
        if (fullFeatures) item { Stat(tr("Protected targets"), lockCount, Icons.Default.Lock) }
        item {
            ListItem(
                { Text(tr("Media permissions")) },
                supportingContent = { Text(tr("Grant or review Android photo, video and media-location access.")) },
                leadingContent = { Icon(Icons.Default.AdminPanelSettings, null) },
                trailingContent = { TextButton(onPermissions) { Text(tr("Grant")) } },
            )
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Broad media support"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(tr("JPEG/JFIF, PNG, WebP, GIF, BMP, HEIC/HEIF, AVIF, DNG, TIFF, ICO, SVG and Android-indexed MP4/MOV/3GP/MKV/WebM/AVI/MPEG/TS media are recognized when a device decoder/provider exposes them."))
                }
            }
        }
        item { HorizontalDivider() }
        item {
            ListItem(
                { Text(tr("Debug logging")) },
                supportingContent = { Text(tr("Write a rotating app-private KeepG log. Error lines are still sent to Logcat when disabled.")) },
                leadingContent = { Icon(Icons.Default.BugReport, null) },
                trailingContent = { Switch(debugEnabled, onDebugEnabled) },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onExportLog, Modifier.weight(1f)) { Text(tr("Export log")) }
                OutlinedButton({ showLog = true }, Modifier.weight(1f)) { Text(tr("View log")) }
                TextButton(onClearLog) { Text(tr("Clear")) }
            }
        }
        if (fullFeatures) {
            item { Text(tr("A KeepG lock controls display inside KeepG. For confidentiality from other gallery apps, also create a Vault copy and remove the original through Android system controls. See SECURITY.md.")) }
        }
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text(tr("KeepG debug log")) },
            text = {
                SelectionContainer {
                    Text(onShowLog(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 420.dp))
                }
            },
            confirmButton = { TextButton({ showLog = false }) { Text(tr("Close")) } },
        )
    }
}

@Composable
internal fun Stat(label: String, value: Int, icon: ImageVector) = ListItem(
    { Text(label) },
    leadingContent = { Icon(icon, null) },
    trailingContent = { Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
)

@Composable
internal fun PhotoDialog(
    photo: PhotoEntity,
    lock: LockEntity?,
    collections: List<CollectionEntity>,
    fullFeatures: Boolean,
    onDismiss: () -> Unit,
    onLock: () -> Unit,
    onRemoveLock: (LockEntity) -> Unit,
    onVault: () -> Unit,
    onAnalyze: () -> Unit,
    onCollection: (Long) -> Unit,
    onEdit: (MediaEditOperation, Float) -> Unit,
    onDetectLinks: () -> Unit,
    onRepair: (Boolean) -> Unit,
    onOpenVideo: () -> Unit,
) {
    var pickCollection by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var repair by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp)) {
            LazyColumn(
                Modifier.padding(14.dp).heightIn(max = 720.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item {
                    AsyncImage(
                        Uri.parse(photo.uri),
                        photo.displayName,
                        Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 420.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                item { Text(photo.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                item { Text("${photo.bucketName} · ${photo.mimeType} · ${photo.width}×${photo.height} · ${DateFormat.getDateTimeInstance().format(Date(photo.dateTaken))}", style = MaterialTheme.typography.bodySmall) }
                if (photo.mimeType.startsWith("video/")) {
                    item { FilledTonalButton(onOpenVideo, Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(tr("Open video player")) } }
                }
                if (fullFeatures) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton({ if (lock == null) onLock() else onRemoveLock(lock) }, Modifier.weight(1f)) {
                                Icon(if (lock == null) Icons.Default.Lock else Icons.Default.LockOpen, null)
                                Text(" ${tr(if (lock == null) "Protect" else "Unlock")}")
                            }
                            FilledTonalButton(onVault, Modifier.weight(1f)) { Icon(Icons.Default.EnhancedEncryption, null); Text(" ${tr("Vault")}") }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton({ edit = true }, Modifier.weight(1f)) { Icon(Icons.Default.Edit, null); Text(" ${tr("Edit")}") }
                            FilledTonalButton({ repair = true }, Modifier.weight(1f)) { Icon(Icons.Default.Build, null); Text(" ${tr("Repair")}") }
                        }
                    }
                    if (photo.mimeType.startsWith("image/")) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onAnalyze, Modifier.weight(1f)) { Text(tr("Analyze")) }
                                OutlinedButton(onDetectLinks, Modifier.weight(1f)) { Icon(Icons.Default.QrCodeScanner, null); Text(" ${tr("Links")}") }
                            }
                        }
                    }
                }
                item {
                    Row {
                        TextButton({ pickCollection = true }, enabled = collections.isNotEmpty()) { Text(tr("Collection")) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onDismiss) { Text(tr("Close")) }
                    }
                }
            }
        }
    }

    if (pickCollection) {
        AlertDialog(
            onDismissRequest = { pickCollection = false },
            title = { Text(tr("Add to collection")) },
            text = { LazyColumn { items(collections, key = { it.id }) { collection -> ListItem({ Text(collection.name) }, modifier = Modifier.clickable { onCollection(collection.id); pickCollection = false }) } } },
            confirmButton = {},
            dismissButton = { TextButton({ pickCollection = false }) { Text(tr("Cancel")) } },
        )
    }

    if (edit) {
        MediaEditorDialog(photo, { edit = false }) { operation, strength ->
            onEdit(operation, strength)
            edit = false
        }
    }

    if (repair) {
        AlertDialog(
            onDismissRequest = { repair = false },
            title = { Text(tr("Repair media")) },
            text = { Text(tr("KeepG creates a recovered copy instead of destructively rewriting the original. Choose whether to preserve the current indexed date or replace an invalid date with the current time.")) },
            confirmButton = { TextButton({ onRepair(false); repair = false }) { Text(tr("Preserve date")) } },
            dismissButton = {
                Row {
                    TextButton({ onRepair(true); repair = false }) { Text(tr("Use current date")) }
                    TextButton({ repair = false }) { Text(tr("Cancel")) }
                }
            },
        )
    }
}

@Composable
private fun MediaEditorDialog(photo: PhotoEntity, onDismiss: () -> Unit, onApply: (MediaEditOperation, Float) -> Unit) {
    var strength by remember { mutableFloatStateOf(0.28f) }
    val image = photo.mimeType.startsWith("image/")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (image) "Image / GIF editor" else "Video editor")) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 480.dp)) {
                if (image) {
                    item { EditorAction(tr("Rotate 90° right"), MediaEditOperation.ROTATE_RIGHT, strength, onApply) }
                    item { EditorAction(tr("Flip horizontally"), MediaEditOperation.FLIP_HORIZONTAL, strength, onApply) }
                    item { EditorAction(tr("Grayscale"), MediaEditOperation.GRAYSCALE, strength, onApply) }
                    item { EditorAction(tr("Center square crop"), MediaEditOperation.CROP_SQUARE, strength, onApply) }
                    item { EditorAction(tr("Automatic person background removal"), MediaEditOperation.AUTO_BACKGROUND_REMOVAL, strength, onApply) }
                    item {
                        Text(trf("Manual background tolerance %s%%", (strength * 100).toInt()), style = MaterialTheme.typography.labelMedium)
                        Slider(strength, { strength = it }, valueRange = 0.05f..0.65f)
                        EditorAction(tr("Remove corner-sampled background"), MediaEditOperation.MANUAL_BACKGROUND_REMOVAL, strength, onApply)
                    }
                    if (photo.mimeType == "image/gif") item { EditorAction(tr("Extract GIF first frame as PNG"), MediaEditOperation.EXTRACT_GIF_FRAME, strength, onApply) }
                } else {
                    item { EditorAction(tr("Trim to first 5 seconds"), MediaEditOperation.VIDEO_TRIM_FIRST_5_SECONDS, strength, onApply) }
                    item { EditorAction(tr("Create muted copy"), MediaEditOperation.VIDEO_MUTE, strength, onApply) }
                }
                item { Text(tr("Edits are non-destructive: KeepG writes a new media item."), style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(tr("Close")) } },
    )
}

@Composable
private fun EditorAction(label: String, operation: MediaEditOperation, strength: Float, onApply: (MediaEditOperation, Float) -> Unit) {
    OutlinedButton({ onApply(operation, strength) }, Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
internal fun PasswordDialog(title: String, value: String, onValue: (String) -> Unit, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, onValue, label = { Text(tr("Password")) }, singleLine = true) },
        confirmButton = { TextButton(onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun findLock(photo: PhotoEntity, locks: List<LockEntity>): LockEntity? =
    locks.firstOrNull { it.targetType == "PHOTO" && it.targetId == photo.mediaId.toString() }
        ?: locks.firstOrNull { it.targetType == "ALBUM" && it.targetId == photo.bucketId.toString() }

internal fun isUnlocked(lock: LockEntity, unlocked: Set<String>) = "${lock.targetType}:${lock.targetId}" in unlocked
internal fun ruleIcon(kind: String): ImageVector = when (kind) {
    "PERSON" -> Icons.Default.Person
    "TIME" -> Icons.Default.Schedule
    "LOCATION" -> Icons.Default.Place
    "EXPRESSION" -> Icons.Default.SentimentSatisfied
    else -> Icons.Default.AutoAwesome
}
