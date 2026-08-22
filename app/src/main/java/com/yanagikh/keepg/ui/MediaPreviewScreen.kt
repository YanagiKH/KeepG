package com.yanagikh.keepg.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yanagikh.keepg.advanced.AdvancedEditRequest
import com.yanagikh.keepg.advanced.BackgroundRemovalMode
import com.yanagikh.keepg.advanced.MediaEditOperation
import com.yanagikh.keepg.advanced.TextLayerSpec
import com.yanagikh.keepg.data.CollectionEntity
import com.yanagikh.keepg.data.LockEntity
import com.yanagikh.keepg.data.PhotoEntity
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPreviewDialog(
    photo: PhotoEntity,
    lock: LockEntity?,
    collections: List<CollectionEntity>,
    isFavorite: Boolean,
    fullFeatures: Boolean,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onShare: (String?) -> Unit,
    onDelete: () -> Unit,
    hasDeletionPassword: Boolean,
    verifyDeletionPassword: (String) -> Boolean,
    setDeletionPassword: (String) -> Boolean,
    onLock: () -> Unit,
    onRemoveLock: (LockEntity) -> Unit,
    onVault: () -> Unit,
    onAnalyze: () -> Unit,
    onCollection: (Long) -> Unit,
    onEdit: (MediaEditOperation, Float) -> Unit,
    onAdvancedEdit: (AdvancedEditRequest) -> Unit,
    onDetectLinks: (Float?, Float?) -> Unit,
    onRepair: (Boolean) -> Unit,
    onRename: (String) -> Unit,
) {
    var showShare by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var pickCollection by remember { mutableStateOf(false) }
    var repair by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(photo.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onDismiss) { Icon(Icons.Default.ArrowBack, tr("Back")) } },
                    actions = {
                        IconButton(onFavorite) {
                            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, tr(if (isFavorite) "Unfavorite" else "Favorite"))
                        }
                        IconButton({ showShare = true }) { Icon(Icons.Default.Share, tr("Share")) }
                        Box {
                            IconButton({ showMore = true }) { Icon(Icons.Default.MoreVert, null) }
                            DropdownMenu(showMore, { showMore = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (lock == null) tr("Protect") else tr("Unlock")) },
                                    leadingIcon = { Icon(if (lock == null) Icons.Default.Lock else Icons.Default.LockOpen, null) },
                                    onClick = { showMore = false; if (lock == null) onLock() else onRemoveLock(lock) },
                                )
                                if (fullFeatures) {
                                    DropdownMenuItem({ Text("Vault") }, { showMore = false; onVault() }, leadingIcon = { Icon(Icons.Default.EnhancedEncryption, null) })
                                    if (photo.mimeType.startsWith("image/")) {
                                        DropdownMenuItem({ Text(tr("Analyze")) }, { showMore = false; onAnalyze() }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) })
                                        DropdownMenuItem({ Text(tr("Links")) }, { showMore = false; onDetectLinks(null, null) }, leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) })
                                    }
                                    DropdownMenuItem({ Text(tr("Repair")) }, { showMore = false; repair = true }, leadingIcon = { Icon(Icons.Default.Build, null) })
                                }
                                DropdownMenuItem(
                                    { Text(tr("Collection")) },
                                    { showMore = false; pickCollection = true },
                                    leadingIcon = { Icon(Icons.Default.Collections, null) },
                                    enabled = collections.isNotEmpty(),
                                )
                            }
                        }
                    },
                )
                Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if (photo.mimeType.startsWith("video/")) ZoomableVideoPlayer(photo)
                    else ZoomableImagePreview(photo, fullFeatures, onDetectLinks)
                }
                NavigationBar {
                    NavigationBarItem(isFavorite, onFavorite, { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) }, label = { Text(tr("Favorite")) })
                    NavigationBarItem(false, { showShare = true }, { Icon(Icons.Default.Share, null) }, label = { Text(tr("Share")) })
                    NavigationBarItem(false, { showEditor = true }, { Icon(Icons.Default.Edit, null) }, label = { Text(tr("Edit")) }, enabled = fullFeatures)
                    NavigationBarItem(false, { showDelete = true }, { Icon(Icons.Default.Delete, null) }, label = { Text(tr("Delete")) })
                    NavigationBarItem(false, { showDetails = true }, { Icon(Icons.Default.Info, null) }, label = { Text(tr("Details")) })
                }
            }
        }
    }

    if (showShare) ShareDialog({ showShare = false }) { password -> showShare = false; onShare(password) }
    if (showDelete) {
        DeletePasswordDialog(
            hasPassword = hasDeletionPassword,
            verify = verifyDeletionPassword,
            setPassword = setDeletionPassword,
            onDismiss = { showDelete = false },
            onAuthorized = { showDelete = false; onDelete() },
        )
    }
    if (showDetails) MediaDetailsDialog(photo, { showDetails = false }, onRename)
    if (showEditor) {
        if (photo.mimeType.startsWith("image/")) {
            AdvancedImageEditorDialog(photo, { showEditor = false }) { request -> showEditor = false; onAdvancedEdit(request) }
        } else {
            VideoEditDialog({ showEditor = false }) { operation -> showEditor = false; onEdit(operation, .35f) }
        }
    }
    if (pickCollection) {
        AlertDialog(
            onDismissRequest = { pickCollection = false },
            title = { Text(tr("Collection")) },
            text = {
                LazyColumn {
                    items(collections, key = { it.id }) { collection ->
                        ListItem(
                            headlineContent = { Text(collection.name) },
                            modifier = Modifier.pointerInput(collection.id) {
                                detectTapGestures(onTap = { onCollection(collection.id); pickCollection = false })
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ pickCollection = false }) { Text(tr("Cancel")) } },
        )
    }
    if (repair) {
        AlertDialog(
            onDismissRequest = { repair = false },
            title = { Text(tr("Repair")) },
            text = { Text("KeepG creates a recovered copy instead of destructively rewriting the original.") },
            confirmButton = { TextButton({ onRepair(false); repair = false }) { Text("Preserve date") } },
            dismissButton = { Row { TextButton({ onRepair(true); repair = false }) { Text("Use current date") }; TextButton({ repair = false }) { Text(tr("Cancel")) } } },
        )
    }
}

@Composable
private fun ZoomableImagePreview(photo: PhotoEntity, linkDetection: Boolean, onDetectLinks: (Float?, Float?) -> Unit) {
    var scale by remember(photo.mediaId) { mutableFloatStateOf(1f) }
    var translation by remember(photo.mediaId) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(photo.mediaId) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 100f)
                    translation += pan
                }
            }
            .pointerInput(photo.mediaId, scale, translation, viewport, linkDetection) {
                detectTapGestures(
                    onDoubleTap = { scale = if (scale > 1.01f) 1f else 4f; translation = Offset.Zero },
                    onLongPress = { position ->
                        if (!linkDetection || viewport.width <= 0 || viewport.height <= 0) return@detectTapGestures
                        val center = Offset(viewport.width / 2f, viewport.height / 2f)
                        val unscaled = Offset(
                            (position.x - center.x - translation.x) / scale + center.x,
                            (position.y - center.y - translation.y) / scale + center.y,
                        )
                        fitNormalizedPoint(unscaled, viewport, photo.width, photo.height)?.let { (x, y) -> onDetectLinks(x, y) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            Uri.parse(photo.uri),
            photo.displayName,
            Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = translation.x, translationY = translation.y),
            contentScale = ContentScale.Fit,
        )
        if (linkDetection) {
            Surface(Modifier.align(Alignment.TopCenter).padding(8.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .78f)) {
                Text(tr("Long-press a URL or QR code in the image to open it."), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ZoomableVideoPlayer(photo: PhotoEntity) {
    val context = LocalContext.current
    val trackSelector = remember(photo.mediaId) { DefaultTrackSelector(context) }
    val player = remember(photo.mediaId) {
        ExoPlayer.Builder(context).setTrackSelector(trackSelector).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(Uri.parse(photo.uri)))
            prepare()
        }
    }
    var speed by remember { mutableFloatStateOf(1f) }
    var quality by remember { mutableStateOf("Auto") }
    var scale by remember { mutableFloatStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    var speedMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    DisposableEffect(player) { onDispose { player.release() } }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = translation.x, translationY = translation.y)
                .pointerInput(photo.mediaId) {
                    detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 40f); translation += pan }
                },
            factory = { ctx -> PlayerView(ctx).apply { useController = true; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; this.player = player } },
            update = { it.player = player },
        )
        Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                FilledTonalButton({ speedMenu = true }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("${speed}×") }
                DropdownMenu(speedMenu, { speedMenu = false }) {
                    listOf(.25f, .5f, 1f, 1.5f, 2f, 3f).forEach { value ->
                        DropdownMenuItem({ Text("${value}×") }, { speed = value; player.playbackParameters = PlaybackParameters(value); speedMenu = false })
                    }
                }
            }
            Box {
                FilledTonalButton({ qualityMenu = true }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text(quality) }
                DropdownMenu(qualityMenu, { qualityMenu = false }) {
                    listOf("Auto" to null, "1080p" to 1080, "720p" to 720, "480p" to 480).forEach { (label, height) ->
                        DropdownMenuItem({ Text(if (label == "Auto") tr("Auto quality") else label) }, {
                            quality = label
                            val builder = trackSelector.buildUponParameters()
                            if (height == null) builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE) else builder.setMaxVideoSize(height * 16 / 9, height)
                            trackSelector.setParameters(builder)
                            qualityMenu = false
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedImageEditorDialog(photo: PhotoEntity, onDismiss: () -> Unit, onApply: (AdvancedEditRequest) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var crop by remember { mutableStateOf("Original") }
    var background by remember { mutableStateOf(BackgroundRemovalMode.NONE) }
    var backgroundStrength by remember { mutableFloatStateOf(.28f) }
    var outputName by remember { mutableStateOf(photo.displayName.substringBeforeLast('.', photo.displayName) + "_edit") }
    var textInput by remember { mutableStateOf("") }
    var layers by remember { mutableStateOf<List<TextLayerSpec>>(emptyList()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDismiss) { Icon(Icons.Default.Close, tr("Close")) }
                    Text(tr("Image editor"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Button({
                        val cropRect = cropRect(crop, photo.width, photo.height)
                        onApply(
                            AdvancedEditRequest(
                                outputName = outputName,
                                offsetX = if (viewport.width > 0) offset.x / viewport.width else 0f,
                                offsetY = if (viewport.height > 0) offset.y / viewport.height else 0f,
                                scale = scale,
                                rotation = rotation,
                                cropLeft = cropRect[0], cropTop = cropRect[1], cropRight = cropRect[2], cropBottom = cropRect[3],
                                backgroundRemoval = background,
                                backgroundStrength = backgroundStrength,
                                textLayers = layers,
                            )
                        )
                    }) { Text(tr("Apply edit")) }
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant).onSizeChanged { viewport = it }
                        .pointerInput(photo.mediaId) {
                            detectTransformGestures { _, pan, zoom, gestureRotation ->
                                scale = (scale * zoom).coerceIn(.1f, 20f)
                                offset += pan
                                rotation += gestureRotation
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        Uri.parse(photo.uri), null,
                        Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y, rotationZ = rotation),
                        contentScale = ContentScale.Fit,
                    )
                    layers.forEachIndexed { index, layer ->
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.scrim.copy(alpha = .35f), modifier = Modifier.align(Alignment.Center).offset(y = (index * 28).dp)) {
                            Text(layer.text, Modifier.padding(4.dp), color = MaterialTheme.colorScheme.inverseOnSurface)
                        }
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 310.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(tr("Drag with one finger; pinch with two fingers to move and scale."), style = MaterialTheme.typography.bodySmall) }
                    item { OutlinedTextField(outputName, { outputName = it }, Modifier.fillMaxWidth(), label = { Text(tr("Output name")) }, singleLine = true) }
                    item {
                        Text(tr("Crop"), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Original", "Square", "4:3", "16:9").forEach { value -> FilterChip(crop == value, { crop = value }, { Text(tr(value)) }) } }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton({ rotation -= 90f }) { Text("↶ 90°") }
                            OutlinedButton({ rotation += 90f }) { Text("↷ 90°") }
                            TextButton({ scale = 1f; offset = Offset.Zero; rotation = 0f }) { Text("Reset") }
                        }
                    }
                    item {
                        Text(tr("Background"), fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(BackgroundRemovalMode.NONE to "Keep background", BackgroundRemovalMode.AUTO to "Auto remove", BackgroundRemovalMode.MANUAL to "Manual remove").forEach { (mode, label) ->
                                FilterChip(background == mode, { background = mode }, { Text(tr(label)) })
                            }
                        }
                        if (background == BackgroundRemovalMode.MANUAL) Slider(backgroundStrength, { backgroundStrength = it }, valueRange = .05f.. .75f)
                    }
                    item {
                        Text(tr("Layers"), fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(textInput, { textInput = it }, Modifier.weight(1f), label = { Text(tr("Text layer")) }, singleLine = true)
                            IconButton({
                                if (textInput.isNotBlank()) {
                                    val index = layers.size
                                    layers = layers + TextLayerSpec(textInput.trim(), x = .18f + (index % 3) * .2f, y = .35f + (index % 4) * .1f)
                                    textInput = ""
                                }
                            }) { Icon(Icons.Default.Add, tr("Add text layer")) }
                        }
                        layers.forEachIndexed { index, layer ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(layer.text, Modifier.weight(1f), maxLines = 1)
                                IconButton({ layers = layers.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Default.Delete, tr("Delete")) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoEditDialog(onDismiss: () -> Unit, onEdit: (MediaEditOperation) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Video editor")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ onEdit(MediaEditOperation.VIDEO_TRIM_FIRST_5_SECONDS) }, Modifier.fillMaxWidth()) { Text(tr("Trim first 5 seconds")) }
            OutlinedButton({ onEdit(MediaEditOperation.VIDEO_MUTE) }, Modifier.fillMaxWidth()) { Text(tr("Create muted copy")) }
        } },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(tr("Close")) } },
    )
}

@Composable
internal fun ShareDialog(onDismiss: () -> Unit, onShare: (String?) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Share media")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(password, { password = it }, label = { Text(tr("Optional password")) }, singleLine = true)
            Text(tr("Leave blank for normal sharing. A password creates an AES-encrypted ZIP."), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button({ if (password.isBlank() || password.length >= 6) onShare(password.ifBlank { null }) }) { Text(tr("Share")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
internal fun DeletePasswordDialog(
    hasPassword: Boolean,
    verify: (String) -> Boolean,
    setPassword: (String) -> Boolean,
    onDismiss: () -> Unit,
    onAuthorized: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val minimumMessage = tr("Use at least 6 characters")
    val incorrectMessage = tr("Incorrect password")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (hasPassword) "Enter deletion password" else "Create deletion password")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(password, { password = it; error = null }, label = { Text(tr("Password")) }, singleLine = true, isError = error != null)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = {
            Button({
                if (password.length < 6) error = minimumMessage
                else if (!hasPassword) {
                    if (setPassword(password)) onAuthorized() else error = minimumMessage
                } else if (verify(password)) onAuthorized() else error = incorrectMessage
            }) { Text(tr("Delete")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("Cancel")) } },
    )
}

@Composable
private fun MediaDetailsDialog(photo: PhotoEntity, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var rename by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(photo.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("File information")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            DetailRow(tr("Name"), photo.displayName)
            DetailRow(tr("Album"), photo.bucketName)
            DetailRow(tr("MIME type"), photo.mimeType)
            DetailRow(tr("Resolution"), "${photo.width} × ${photo.height}")
            DetailRow(tr("File size"), formatBytes(photo.sizeBytes))
            if (photo.durationMs > 0L) DetailRow(tr("Duration"), formatDuration(photo.durationMs))
            DetailRow(tr("Modified"), DateFormat.getDateTimeInstance().format(Date(photo.dateTaken)))
            TextButton({ rename = true }) { Icon(Icons.Default.DriveFileRenameOutline, null); Text(tr("Rename")) }
        } },
        confirmButton = { TextButton(onDismiss) { Text(tr("Close")) } },
    )
    if (rename) {
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(tr("Rename media")) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(tr("Name")) }, singleLine = true) },
            confirmButton = { TextButton({ onRename(name); rename = false; onDismiss() }) { Text(tr("Save")) } },
            dismissButton = { TextButton({ rename = false }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) { Text(label, Modifier.width(100.dp), fontWeight = FontWeight.Bold); Text(value, Modifier.weight(1f)) }
}

private fun fitNormalizedPoint(position: Offset, viewport: IntSize, imageWidth: Int, imageHeight: Int): Pair<Float, Float>? {
    if (imageWidth <= 0 || imageHeight <= 0 || viewport.width <= 0 || viewport.height <= 0) return null
    val imageAspect = imageWidth.toFloat() / imageHeight
    val viewportAspect = viewport.width.toFloat() / viewport.height
    val drawWidth: Float
    val drawHeight: Float
    val left: Float
    val top: Float
    if (imageAspect > viewportAspect) {
        drawWidth = viewport.width.toFloat(); drawHeight = drawWidth / imageAspect; left = 0f; top = (viewport.height - drawHeight) / 2f
    } else {
        drawHeight = viewport.height.toFloat(); drawWidth = drawHeight * imageAspect; left = (viewport.width - drawWidth) / 2f; top = 0f
    }
    if (position.x !in left..(left + drawWidth) || position.y !in top..(top + drawHeight)) return null
    return ((position.x - left) / drawWidth).coerceIn(0f, 1f) to ((position.y - top) / drawHeight).coerceIn(0f, 1f)
}

private fun cropRect(mode: String, width: Int, height: Int): FloatArray {
    if (mode == "Original" || width <= 0 || height <= 0) return floatArrayOf(0f, 0f, 1f, 1f)
    val target = when (mode) { "Square" -> 1f; "4:3" -> 4f / 3f; "16:9" -> 16f / 9f; else -> width.toFloat() / height }
    val current = width.toFloat() / height
    return if (current > target) {
        val fraction = target / current; val inset = (1f - fraction) / 2f; floatArrayOf(inset, 0f, 1f - inset, 1f)
    } else {
        val fraction = current / target; val inset = (1f - fraction) / 2f; floatArrayOf(0f, inset, 1f, 1f - inset)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.ROOT, "%.1f MB", mb)
    return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0)
}
