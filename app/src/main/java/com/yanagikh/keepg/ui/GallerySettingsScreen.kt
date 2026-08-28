package com.yanagikh.keepg.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yanagikh.keepg.data.*

@Composable
internal fun GallerySettingsScreen(
    photoCount: Int,
    faceCount: Int,
    lockCount: Int,
    fullFeatures: Boolean,
    debugEnabled: Boolean,
    settings: GallerySettings,
    hasDeletionPassword: Boolean,
    onVideoPreviewAutoPlay: (Boolean) -> Unit,
    onPreviewSwipeNavigation: (Boolean) -> Unit,
    onGridColumns: (Int) -> Unit,
    onGridLayoutMode: (GridLayoutMode) -> Unit,
    onThumbnailScaleMode: (ThumbnailScaleMode) -> Unit,
    onPreviewScaleMode: (PreviewScaleMode) -> Unit,
    onShowMediaBadges: (Boolean) -> Unit,
    onAnimationsEnabled: (Boolean) -> Unit,
    onCameraGridEnabled: (Boolean) -> Unit,
    onCameraAudioEnabled: (Boolean) -> Unit,
    onDeleteToTrash: (Boolean) -> Unit,
    onHideSensitiveContent: (Boolean) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    onDeletionPassword: (String) -> Boolean,
    onDebugEnabled: (Boolean) -> Unit,
    onPermissions: () -> Unit,
    onExportLog: () -> Unit,
    onClearLog: () -> Unit,
    onShowLog: () -> String,
) {
    var showLog by remember { mutableStateOf(false) }
    var languageDialog by remember { mutableStateOf(false) }
    var passwordDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    val useAtLeastSix = tr("Use at least 6 characters")

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ElevatedCard {
                Column(Modifier.padding(16.dp)) {
                    Text(if (fullFeatures) "KeepG Full" else "KeepG Lite", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(tr(if (fullFeatures) "Local smart analysis, secure Vault, native playback, editing, QR/URL detection and repair tools are enabled." else "Lightweight edition: gallery, native playback, albums, search and collections."))
                }
            }
        }
        item { Stat(tr("Indexed media"), photoCount, Icons.Default.PhotoLibrary) }
        if (fullFeatures) item { Stat(tr("Detected faces"), faceCount, Icons.Default.Face) }
        if (fullFeatures) item { Stat(tr("Protected targets"), lockCount, Icons.Default.Lock) }
        item {
            ListItem(
                { Text(tr("Video preview autoplay")) },
                supportingContent = { Text(tr("Play muted looping video previews in the media grid.")) },
                leadingContent = { Icon(Icons.Default.PlayCircle, null) },
                trailingContent = { Switch(settings.videoPreviewAutoPlay, onVideoPreviewAutoPlay) },
            )
        }
        item {
            ListItem(
                { Text(tr("Swipe between previews")) },
                supportingContent = { Text(tr("Swipe left or right inside the current album or search order. Navigation is disabled while media is zoomed in.")) },
                leadingContent = { Icon(Icons.Default.SwapHoriz, null) },
                trailingContent = { Switch(settings.previewSwipeNavigation, onPreviewSwipeNavigation) },
            )
        }
        item {
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(tr("Grid columns"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(settings.gridColumns.toString())
                    }
                    Slider(
                        value = settings.gridColumns.toFloat(),
                        onValueChange = { onGridColumns(it.toInt()) },
                        valueRange = 2f..8f,
                        steps = 5,
                    )
                    Text(tr("Grid layout"), fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GridLayoutMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.gridLayoutMode == mode,
                                onClick = { onGridLayoutMode(mode) },
                                label = { Text(tr(gridLayoutLabel(mode))) },
                            )
                        }
                    }
                    Text(tr("Thumbnail framing"), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThumbnailScaleMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.thumbnailScaleMode == mode,
                                onClick = { onThumbnailScaleMode(mode) },
                                label = { Text(tr(if (mode == ThumbnailScaleMode.CROP) "Fill" else "Fit")) },
                            )
                        }
                    }
                    Text(tr("Full-screen preview framing"), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PreviewScaleMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.previewScaleMode == mode,
                                onClick = { onPreviewScaleMode(mode) },
                                label = { Text(tr(if (mode == PreviewScaleMode.FILL) "Fill" else "Fit")) },
                            )
                        }
                    }
                }
            }
        }
        item {
            ListItem(
                { Text(tr("Media badges")) },
                supportingContent = { Text(tr("Show video duration, GIF, favorite, and selection badges on thumbnails.")) },
                leadingContent = { Icon(Icons.Default.Label, null) },
                trailingContent = { Switch(settings.showMediaBadges, onShowMediaBadges) },
            )
        }
        item {
            ListItem(
                { Text(tr("Interface animations")) },
                supportingContent = { Text(tr("Animate tab, grid, and preview transitions.")) },
                leadingContent = { Icon(Icons.Default.Animation, null) },
                trailingContent = { Switch(settings.animationsEnabled, onAnimationsEnabled) },
            )
        }
        item {
            ListItem(
                { Text(tr("Camera composition grid")) },
                supportingContent = { Text(tr("Show a rule-of-thirds grid when KeepG Camera opens.")) },
                leadingContent = { Icon(Icons.Default.GridOn, null) },
                trailingContent = { Switch(settings.cameraGridEnabled, onCameraGridEnabled) },
            )
        }
        item {
            ListItem(
                { Text(tr("Record camera audio")) },
                supportingContent = { Text(tr("Include microphone audio in new videos after permission is granted.")) },
                leadingContent = { Icon(Icons.Default.Mic, null) },
                trailingContent = { Switch(settings.cameraAudioEnabled, onCameraAudioEnabled) },
            )
        }
        item {
            ListItem(
                { Text(tr("Delete to recoverable trash")) },
                supportingContent = { Text(tr("Use Android MediaStore trash when available instead of immediate permanent deletion.")) },
                leadingContent = { Icon(Icons.Default.DeleteSweep, null) },
                trailingContent = { Switch(settings.deleteToTrash, onDeleteToTrash) },
            )
        }
        item {
            ListItem(
                { Text(tr("Hide likely NSFW / graphic content")) },
                supportingContent = { Text(tr("Uses an on-device visual heuristic. It can make mistakes; hidden media is never deleted.")) },
                leadingContent = { Icon(Icons.Default.VisibilityOff, null) },
                trailingContent = { Switch(settings.hideSensitiveContent, onHideSensitiveContent) },
            )
        }
        item {
            ListItem(
                { Text(tr("Language")) },
                supportingContent = { Text(languageLabel(settings.language)) },
                leadingContent = { Icon(Icons.Default.Language, null) },
                trailingContent = { TextButton({ languageDialog = true }) { Text(languageLabel(settings.language)) } },
            )
        }
        item {
            ListItem(
                { Text(tr("Deletion password")) },
                supportingContent = { Text(tr(if (hasDeletionPassword) "Replace the password required before deleting media." else "Set the password required before deleting media.")) },
                leadingContent = { Icon(Icons.Default.Password, null) },
                trailingContent = { TextButton({ password = ""; passwordError = false; passwordDialog = true }) { Text(tr("Set password")) } },
            )
        }
        item {
            ListItem(
                { Text(tr("Media permissions")) },
                supportingContent = { Text(tr("Grant or review Android photo, video and media-location access. Camera access is requested only when opening KeepG Camera.")) },
                leadingContent = { Icon(Icons.Default.AdminPanelSettings, null) },
                trailingContent = { TextButton(onPermissions) { Text(tr("Grant")) } },
            )
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Broad media support"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(tr("JPEG/JFIF, PNG, WebP, GIF, BMP, HEIC/HEIF, AVIF, DNG, TIFF, ICO, SVG and Android-indexed MP4/MOV/3GP/MKV/WebM/AVI/MPEG/TS media are recognized when a device decoder/provider exposes them."))
                    Text(tr("The home-screen KeepG widget provides Photos, Albums, Camera, and Vault shortcuts when supported by the launcher."))
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
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(tr("Language")) },
            text = {
                Column {
                    AppLanguage.entries.forEach { language ->
                        ListItem(
                            headlineContent = { Text(languageLabel(language)) },
                            leadingContent = { RadioButton(settings.language == language, { onLanguage(language); languageDialog = false }) },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ languageDialog = false }) { Text(tr("Cancel")) } },
        )
    }

    if (passwordDialog) {
        AlertDialog(
            onDismissRequest = { passwordDialog = false },
            title = { Text(tr("Deletion password")) },
            text = {
                Column {
                    OutlinedTextField(password, { password = it; passwordError = false }, label = { Text(tr("Password")) }, singleLine = true, isError = passwordError)
                    if (passwordError) Text(useAtLeastSix, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button({
                    if (password.length < 6) passwordError = true
                    else if (onDeletionPassword(password)) passwordDialog = false
                    else passwordError = true
                }) { Text(tr("Save")) }
            },
            dismissButton = { TextButton({ passwordDialog = false }) { Text(tr("Cancel")) } },
        )
    }

    if (showLog) {
        AlertDialog(
            onDismissRequest = { showLog = false },
            title = { Text(tr("KeepG debug log")) },
            text = { SelectionContainer { Text(onShowLog(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 420.dp)) } },
            confirmButton = { TextButton({ showLog = false }) { Text(tr("Close")) } },
        )
    }
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.AUTO -> tr("Automatic")
    AppLanguage.ENGLISH -> tr("English")
    AppLanguage.CHINESE -> tr("Chinese")
    AppLanguage.JAPANESE -> tr("Japanese")
    AppLanguage.KOREAN -> tr("Korean")
}

private fun gridLayoutLabel(mode: GridLayoutMode): String = when (mode) {
    GridLayoutMode.SQUARE -> "Square"
    GridLayoutMode.PORTRAIT -> "Portrait"
    GridLayoutMode.ADAPTIVE -> "Adaptive"
}
