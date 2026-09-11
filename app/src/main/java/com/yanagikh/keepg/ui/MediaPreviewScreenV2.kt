package com.yanagikh.keepg.ui

import com.yanagikh.keepg.agent.AgentEntryButton
import android.net.Uri
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.yanagikh.keepg.advanced.AdvancedEditRequest
import com.yanagikh.keepg.advanced.BackgroundRemovalMode
import com.yanagikh.keepg.advanced.MediaEditOperation
import com.yanagikh.keepg.advanced.TextLayerSpec
import com.yanagikh.keepg.data.CollectionEntity
import com.yanagikh.keepg.data.LockEntity
import com.yanagikh.keepg.data.PhotoEntity
import com.yanagikh.keepg.data.PreviewScaleMode
import com.yanagikh.keepg.data.VideoEditRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaPreviewDialogV2(
    photo: PhotoEntity,
    lock: LockEntity?,
    collections: List<CollectionEntity>,
    isFavorite: Boolean,
    fullFeatures: Boolean,
    swipeNavigationEnabled: Boolean,
    previewScaleMode: PreviewScaleMode = PreviewScaleMode.FIT,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
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
    onRefresh: () -> Unit,
) {
    val isVideo = photo.mimeType.startsWith("video/")
    var showShare by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var pickCollection by remember { mutableStateOf(false) }
    var repair by remember { mutableStateOf(false) }
    var videoChromeVisible by rememberSaveable(photo.mediaId) { mutableStateOf(true) }
    var previewScale by remember(photo.mediaId) { mutableFloatStateOf(1f) }
    val chromeVisible = !isVideo || videoChromeVisible

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                    TopAppBar(
                        title = { Text(photo.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { IconButton(onDismiss) { Icon(Icons.Default.ArrowBack, tr("Back")) } },
                        actions = {
                            AgentEntryButton()
                            IconButton(onFavorite) { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, tr(if (isFavorite) "Unfavorite" else "Favorite")) }
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
                                        DropdownMenuItem({ Text(tr("Vault")) }, { showMore = false; onVault() }, leadingIcon = { Icon(Icons.Default.EnhancedEncryption, null) })
                                        if (photo.mimeType.startsWith("image/")) {
                                            DropdownMenuItem({ Text(tr("Analyze")) }, { showMore = false; onAnalyze() }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) })
                                            DropdownMenuItem({ Text(tr("Links")) }, { showMore = false; onDetectLinks(null, null) }, leadingIcon = { Icon(Icons.Default.QrCodeScanner, null) })
                                        }
                                        DropdownMenuItem({ Text(tr("Repair")) }, { showMore = false; repair = true }, leadingIcon = { Icon(Icons.Default.Build, null) })
                                    }
                                    DropdownMenuItem({ Text(tr("Collection")) }, { showMore = false; pickCollection = true }, leadingIcon = { Icon(Icons.Default.Collections, null) }, enabled = collections.isNotEmpty())
                                }
                            }
                        },
                    )
                }

                Box(
                    Modifier.weight(1f).fillMaxWidth().clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (isVideo) {
                        ZoomableVideoPlayerV2(
                            photo = photo,
                            controlsVisible = videoChromeVisible,
                            onControlsVisibleChange = { videoChromeVisible = it },
                            swipeNavigationEnabled = swipeNavigationEnabled,
                            previewScaleMode = previewScaleMode,
                            canNavigatePrevious = canNavigatePrevious,
                            canNavigateNext = canNavigateNext,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onScaleChanged = { previewScale = it },
                        )
                    } else {
                        ZoomableImagePreviewV2(
                            photo = photo,
                            linkDetection = fullFeatures,
                            onDetectLinks = onDetectLinks,
                            swipeNavigationEnabled = swipeNavigationEnabled,
                            previewScaleMode = previewScaleMode,
                            canNavigatePrevious = canNavigatePrevious,
                            canNavigateNext = canNavigateNext,
                            onPrevious = onPrevious,
                            onNext = onNext,
                            onScaleChanged = { previewScale = it },
                        )
                    }

                    if (swipeNavigationEnabled && previewScale <= 1.02f && chromeVisible) {
                        if (canNavigatePrevious) {
                            Surface(
                                modifier = Modifier.align(Alignment.CenterStart).padding(8.dp),
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
                            ) { IconButton(onPrevious) { Icon(Icons.Default.ChevronLeft, tr("Previous media")) } }
                        }
                        if (canNavigateNext) {
                            Surface(
                                modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp),
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
                            ) { IconButton(onNext) { Icon(Icons.Default.ChevronRight, tr("Next media")) } }
                        }
                    }
                }

                AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                    PreviewActionBarV2(
                        isFavorite = isFavorite,
                        fullFeatures = fullFeatures,
                        onFavorite = onFavorite,
                        onShare = { showShare = true },
                        onEdit = { showEditor = true },
                        onDelete = { showDelete = true },
                        onDetails = { showDetails = true },
                    )
                }
            }
        }
    }

    if (showShare) ShareDialog({ showShare = false }) { password -> showShare = false; onShare(password) }
    if (showDelete) {
        DeletePasswordDialog(hasDeletionPassword, verifyDeletionPassword, setDeletionPassword, { showDelete = false }) {
            showDelete = false
            onDelete()
        }
    }
    if (showDetails) MediaDetailsDialogV2(photo, { showDetails = false }, onRename)
    if (showEditor) {
        if (photo.mimeType.startsWith("image/")) {
            AdvancedImageEditorDialogV2(
                photo = photo,
                onDismiss = { showEditor = false },
                onQuickEdit = { operation -> showEditor = false; onEdit(operation, .35f) },
                onApply = { request -> showEditor = false; onAdvancedEdit(request) },
            )
        } else {
            AdvancedVideoEditorDialogV2(photo, { showEditor = false }, onRefresh)
        }
    }
    if (pickCollection) {
        AlertDialog(
            onDismissRequest = { pickCollection = false },
            title = { Text(tr("Collection")) },
            text = { LazyColumn { items(collections, key = { it.id }) { collection -> ListItem(headlineContent = { Text(collection.name) }, modifier = Modifier.pointerInput(collection.id) { detectTapGestures(onTap = { onCollection(collection.id); pickCollection = false }) }) } } },
            confirmButton = {},
            dismissButton = { TextButton({ pickCollection = false }) { Text(tr("Cancel")) } },
        )
    }
    if (repair) {
        AlertDialog(
            onDismissRequest = { repair = false },
            title = { Text(tr("Repair")) },
            text = { Text(tr("KeepG creates a recovered copy instead of destructively rewriting the original.")) },
            confirmButton = { TextButton({ onRepair(false); repair = false }) { Text(tr("Preserve date")) } },
            dismissButton = { Row { TextButton({ onRepair(true); repair = false }) { Text(tr("Use current date")) }; TextButton({ repair = false }) { Text(tr("Cancel")) } } },
        )
    }
}

@Composable
private fun PreviewActionBarV2(
    isFavorite: Boolean,
    fullFeatures: Boolean,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(66.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreviewActionCellV2(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, tr("Favorite"), true, onFavorite, Modifier.weight(1f))
            PreviewActionCellV2(Icons.Default.Share, tr("Share"), true, onShare, Modifier.weight(1f))
            PreviewActionCellV2(Icons.Default.Edit, tr("Edit"), fullFeatures, onEdit, Modifier.weight(1f))
            PreviewActionCellV2(Icons.Default.Delete, tr("Delete"), true, onDelete, Modifier.weight(1f))
            PreviewActionCellV2(Icons.Default.Info, tr("Details"), true, onDetails, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewActionCellV2(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else .38f
    Column(
        modifier
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = LocalContentColor.current.copy(alpha = alpha))
        Spacer(Modifier.height(2.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = alpha))
    }
}

@Composable
private fun ZoomableImagePreviewV2(
    photo: PhotoEntity,
    linkDetection: Boolean,
    onDetectLinks: (Float?, Float?) -> Unit,
    swipeNavigationEnabled: Boolean,
    previewScaleMode: PreviewScaleMode,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onScaleChanged: (Float) -> Unit,
) {
    var scale by remember(photo.mediaId) { mutableFloatStateOf(1f) }
    var translation by remember(photo.mediaId) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var swipeDistance by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(viewport, photo.width, photo.height, scale, previewScaleMode) {
        translation = clampMediaTranslation(viewport.width, viewport.height, photo.width, photo.height, scale, translation, previewScaleMode == PreviewScaleMode.FILL)
        onScaleChanged(scale)
    }

    Box(
        Modifier.fillMaxSize().clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(photo.mediaId, viewport, previewScaleMode) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = normalizedZoom(scale, zoom, 16f)
                    scale = nextScale
                    translation = clampMediaTranslation(viewport.width, viewport.height, photo.width, photo.height, nextScale, translation + pan, previewScaleMode == PreviewScaleMode.FILL)
                }
            }
            .pointerInput(photo.mediaId, scale, swipeNavigationEnabled, canNavigatePrevious, canNavigateNext) {
                if (!swipeNavigationEnabled || scale > 1.02f) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { swipeDistance = 0f },
                    onHorizontalDrag = { change, amount -> swipeDistance += amount; change.consume() },
                    onDragEnd = {
                        when (previewSwipeDirection(swipeDistance, 0f, size.width.toFloat())) {
                            -1 -> if (canNavigatePrevious) onPrevious()
                            1 -> if (canNavigateNext) onNext()
                        }
                        swipeDistance = 0f
                    },
                    onDragCancel = { swipeDistance = 0f },
                )
            }
            .pointerInput(photo.mediaId, scale, translation, viewport, linkDetection) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1.01f) 1f else 3f
                        translation = Offset.Zero
                        onScaleChanged(scale)
                    },
                    onLongPress = { position ->
                        if (!linkDetection || viewport.width <= 0 || viewport.height <= 0) return@detectTapGestures
                        val center = Offset(viewport.width / 2f, viewport.height / 2f)
                        val unscaled = Offset(
                            (position.x - center.x - translation.x) / scale + center.x,
                            (position.y - center.y - translation.y) / scale + center.y,
                        )
                        fitNormalizedPointV2(unscaled, viewport, photo.width, photo.height, previewScaleMode == PreviewScaleMode.FILL)?.let { (x, y) -> onDetectLinks(x, y) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = Uri.parse(photo.uri),
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize().clipToBounds().graphicsLayer(scaleX = scale, scaleY = scale, translationX = translation.x, translationY = translation.y),
            contentScale = if (previewScaleMode == PreviewScaleMode.FILL) ContentScale.Crop else ContentScale.Fit,
        )
        if (linkDetection) {
            Surface(Modifier.align(Alignment.TopCenter).padding(8.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .78f)) {
                Text(tr("Long-press a URL or QR code in the image to open it."), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ZoomableVideoPlayerV2(
    photo: PhotoEntity,
    controlsVisible: Boolean,
    onControlsVisibleChange: (Boolean) -> Unit,
    swipeNavigationEnabled: Boolean,
    previewScaleMode: PreviewScaleMode,
    canNavigatePrevious: Boolean,
    canNavigateNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onScaleChanged: (Float) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var position by rememberSaveable(photo.mediaId) { mutableLongStateOf(0L) }
    var requestedPlay by rememberSaveable(photo.mediaId) { mutableStateOf(true) }
    var speed by rememberSaveable(photo.mediaId) { mutableFloatStateOf(1f) }
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val player = remember(photo.mediaId) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(Uri.parse(photo.uri)))
            playbackParameters = PlaybackParameters(speed)
            if (position > 0L) seekTo(position)
            prepare()
            playWhenReady = requestedPlay && lifecycleStarted
        }
    }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(photo.mediaId) { mutableFloatStateOf(1f) }
    var translation by remember(photo.mediaId) { mutableStateOf(Offset.Zero) }
    var duration by remember { mutableLongStateOf(photo.durationMs.coerceAtLeast(0L)) }
    var speedMenu by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(requestedPlay) }
    var swipeDistance by remember { mutableFloatStateOf(0f) }

    val latestRequestedPlay by rememberUpdatedState(requestedPlay)
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    lifecycleStarted = true
                    player.playWhenReady = latestRequestedPlay
                }
                Lifecycle.Event.ON_STOP -> {
                    position = player.currentPosition.coerceAtLeast(0L)
                    player.pause()
                    playing = false
                    lifecycleStarted = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            position = player.currentPosition.coerceAtLeast(0L)
            player.release()
        }
    }
    LaunchedEffect(player, lifecycleStarted) {
        if (!lifecycleStarted) return@LaunchedEffect
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            if (d > 0L) duration = d
            playing = player.isPlaying
            delay(250)
        }
    }
    LaunchedEffect(viewport, photo.width, photo.height, scale, previewScaleMode) {
        translation = clampMediaTranslation(viewport.width, viewport.height, photo.width, photo.height, scale, translation, previewScaleMode == PreviewScaleMode.FILL)
        onScaleChanged(scale)
    }
    LaunchedEffect(controlsVisible, speedMenu, photo.mediaId) {
        if (controlsVisible && !speedMenu) {
            delay(3_000)
            onControlsVisibleChange(false)
        }
    }

    Box(
        Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }
            .pointerInput(photo.mediaId, viewport, previewScaleMode) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = normalizedZoom(scale, zoom, 12f)
                    scale = nextScale
                    translation = clampMediaTranslation(viewport.width, viewport.height, photo.width, photo.height, nextScale, translation + pan, previewScaleMode == PreviewScaleMode.FILL)
                    onScaleChanged(nextScale)
                    onControlsVisibleChange(true)
                }
            }
            .pointerInput(photo.mediaId, scale, swipeNavigationEnabled, canNavigatePrevious, canNavigateNext) {
                if (!swipeNavigationEnabled || scale > 1.02f) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { swipeDistance = 0f },
                    onHorizontalDrag = { change, amount -> swipeDistance += amount; change.consume() },
                    onDragEnd = {
                        when (previewSwipeDirection(swipeDistance, 0f, size.width.toFloat())) {
                            -1 -> if (canNavigatePrevious) onPrevious()
                            1 -> if (canNavigateNext) onNext()
                        }
                        swipeDistance = 0f
                    },
                    onDragCancel = { swipeDistance = 0f },
                )
            }
            .pointerInput(photo.mediaId) {
                detectTapGestures(
                    onTap = { onControlsVisibleChange(!controlsVisible) },
                    onDoubleTap = {
                        scale = if (scale > 1.01f) 1f else 3f
                        translation = Offset.Zero
                        onScaleChanged(scale)
                        onControlsVisibleChange(true)
                    },
                )
            },
    ) {
        Box(
            Modifier.fillMaxSize().clipToBounds().graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = translation.x,
                translationY = translation.y,
            ),
        ) {
            FittedVideoTextureSurfaceV2(
                player = player,
                fallbackWidth = photo.width,
                fallbackHeight = photo.height,
                modifier = Modifier.fillMaxSize(),
                fillContainer = previewScaleMode == PreviewScaleMode.FILL,
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    if (duration > 0L) {
                        Slider(
                            value = position.coerceAtMost(duration).toFloat(),
                            onValueChange = {
                                onControlsVisibleChange(true)
                                position = it.toLong()
                                player.seekTo(position)
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({
                            onControlsVisibleChange(true)
                            requestedPlay = !requestedPlay
                            if (lifecycleStarted) player.playWhenReady = requestedPlay
                            playing = requestedPlay && lifecycleStarted
                        }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                        Text("${formatDuration(position)} / ${formatDuration(duration)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        Box {
                            TextButton({ speedMenu = true; onControlsVisibleChange(true) }) { Text("${speed}×") }
                            DropdownMenu(speedMenu, { speedMenu = false }) {
                                listOf(.25f, .5f, 1f, 1.5f, 2f, 3f).forEach { value ->
                                    DropdownMenuItem(
                                        { Text("${value}×") },
                                        {
                                            speed = value
                                            player.playbackParameters = PlaybackParameters(value)
                                            speedMenu = false
                                            onControlsVisibleChange(true)
                                        },
                                    )
                                }
                            }
                        }
                        IconButton({ scale = 1f; translation = Offset.Zero; onScaleChanged(1f); onControlsVisibleChange(true) }) { Icon(Icons.Default.CenterFocusStrong, tr("Reset zoom")) }
                    }
                }
            }
        }
    }
}

private enum class CropDragMode { NONE, MOVE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

@Composable
private fun AdvancedImageEditorDialogV2(
    photo: PhotoEntity,
    onDismiss: () -> Unit,
    onQuickEdit: (MediaEditOperation) -> Unit,
    onApply: (AdvancedEditRequest) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var sourceSize by remember(photo.mediaId) {
        mutableStateOf(IntSize(photo.width.coerceAtLeast(1), photo.height.coerceAtLeast(1)))
    }
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(1f) }
    var cropBottom by remember { mutableFloatStateOf(1f) }
    var cropMode by remember { mutableStateOf("Original") }
    var dragMode by remember { mutableStateOf(CropDragMode.NONE) }
    var background by remember { mutableStateOf(BackgroundRemovalMode.NONE) }
    var backgroundStrength by remember { mutableFloatStateOf(.28f) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var outputName by remember { mutableStateOf(photo.displayName.substringBeforeLast('.', photo.displayName) + "_edit") }
    var textInput by remember { mutableStateOf("") }
    var layers by remember { mutableStateOf<List<TextLayerSpec>>(emptyList()) }
    val latestLayers by rememberUpdatedState(layers)
    val borderColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    fun setPreset(mode: String) {
        cropMode = mode
        val rect = cropRectV2(mode, viewport.width, viewport.height, sourceSize.width, sourceSize.height)
        cropLeft = rect[0]; cropTop = rect[1]; cropRight = rect[2]; cropBottom = rect[3]
    }

    LaunchedEffect(viewport, cropMode, sourceSize) {
        if (viewport.width > 0 && viewport.height > 0 && cropMode != "Free") {
            val rect = cropRectV2(cropMode, viewport.width, viewport.height, sourceSize.width, sourceSize.height)
            cropLeft = rect[0]; cropTop = rect[1]; cropRight = rect[2]; cropBottom = rect[3]
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDismiss) { Icon(Icons.Default.Close, tr("Close")) }
                    AgentEntryButton()
                    Text(tr("Image editor"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Button({
                        onApply(
                            AdvancedEditRequest(
                                outputName = outputName,
                                offsetX = if (viewport.width > 0) offset.x / viewport.width else 0f,
                                offsetY = if (viewport.height > 0) offset.y / viewport.height else 0f,
                                scale = scale,
                                rotation = rotation,
                                viewportAspectRatio = if (viewport.width > 0 && viewport.height > 0) viewport.width.toFloat() / viewport.height else 1f,
                                cropLeft = cropLeft,
                                cropTop = cropTop,
                                cropRight = cropRight,
                                cropBottom = cropBottom,
                                backgroundRemoval = background,
                                backgroundStrength = backgroundStrength,
                                brightness = brightness,
                                contrast = contrast,
                                saturation = saturation,
                                textLayers = layers,
                            )
                        )
                    }) { Text(tr("Apply edit")) }
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f).clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant).onSizeChanged { viewport = it }
                        .pointerInput(photo.mediaId) {
                            detectTransformGestures { _, pan, zoom, gestureRotation ->
                                scale = (scale * zoom).coerceIn(.2f, 16f)
                                offset += pan
                                rotation = (rotation + gestureRotation) % 360f
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = Uri.parse(photo.uri),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clipToBounds().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y, rotationZ = rotation),
                        contentScale = ContentScale.Fit,
                        onSuccess = { state ->
                            val drawable = state.result.drawable
                            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                                sourceSize = IntSize(drawable.intrinsicWidth, drawable.intrinsicHeight)
                            }
                        },
                    )
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(viewport, cropLeft, cropTop, cropRight, cropBottom) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    if (viewport.width <= 0 || viewport.height <= 0) return@detectDragGestures
                                    val left = cropLeft * viewport.width
                                    val right = cropRight * viewport.width
                                    val top = cropTop * viewport.height
                                    val bottom = cropBottom * viewport.height
                                    val threshold = 40.dp.toPx()
                                    val nearLeft = abs(pos.x - left) <= threshold
                                    val nearRight = abs(pos.x - right) <= threshold
                                    val nearTop = abs(pos.y - top) <= threshold
                                    val nearBottom = abs(pos.y - bottom) <= threshold
                                    dragMode = when {
                                        nearLeft && nearTop -> CropDragMode.TOP_LEFT
                                        nearRight && nearTop -> CropDragMode.TOP_RIGHT
                                        nearLeft && nearBottom -> CropDragMode.BOTTOM_LEFT
                                        nearRight && nearBottom -> CropDragMode.BOTTOM_RIGHT
                                        nearLeft -> CropDragMode.LEFT
                                        nearRight -> CropDragMode.RIGHT
                                        nearTop -> CropDragMode.TOP
                                        nearBottom -> CropDragMode.BOTTOM
                                        pos.x in left..right && pos.y in top..bottom -> CropDragMode.MOVE
                                        else -> CropDragMode.NONE
                                    }
                                },
                                onDragEnd = { dragMode = CropDragMode.NONE },
                                onDragCancel = { dragMode = CropDragMode.NONE },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (viewport.width <= 0 || viewport.height <= 0) return@detectDragGestures
                                    cropMode = "Free"
                                    val dx = dragAmount.x / viewport.width
                                    val dy = dragAmount.y / viewport.height
                                    val minSize = .08f
                                    when (dragMode) {
                                        CropDragMode.MOVE -> {
                                            val width = cropRight - cropLeft
                                            val height = cropBottom - cropTop
                                            val newLeft = (cropLeft + dx).coerceIn(0f, 1f - width)
                                            val newTop = (cropTop + dy).coerceIn(0f, 1f - height)
                                            cropLeft = newLeft; cropRight = newLeft + width
                                            cropTop = newTop; cropBottom = newTop + height
                                        }
                                        CropDragMode.LEFT, CropDragMode.TOP_LEFT, CropDragMode.BOTTOM_LEFT -> cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - minSize)
                                        else -> Unit
                                    }
                                    when (dragMode) {
                                        CropDragMode.RIGHT, CropDragMode.TOP_RIGHT, CropDragMode.BOTTOM_RIGHT -> cropRight = (cropRight + dx).coerceIn(cropLeft + minSize, 1f)
                                        else -> Unit
                                    }
                                    when (dragMode) {
                                        CropDragMode.TOP, CropDragMode.TOP_LEFT, CropDragMode.TOP_RIGHT -> cropTop = (cropTop + dy).coerceIn(0f, cropBottom - minSize)
                                        else -> Unit
                                    }
                                    when (dragMode) {
                                        CropDragMode.BOTTOM, CropDragMode.BOTTOM_LEFT, CropDragMode.BOTTOM_RIGHT -> cropBottom = (cropBottom + dy).coerceIn(cropTop + minSize, 1f)
                                        else -> Unit
                                    }
                                },
                            )
                        }
                    ) {
                        val l = cropLeft * size.width
                        val r = cropRight * size.width
                        val t = cropTop * size.height
                        val b = cropBottom * size.height
                        val shade = Color.Black.copy(alpha = .42f)
                        drawRect(shade, Offset.Zero, Size(size.width, t))
                        drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
                        drawRect(shade, Offset(0f, t), Size(l, b - t))
                        drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
                        drawRect(borderColor, Offset(l, t), Size(r - l, b - t), style = Stroke(3.dp.toPx()))
                    }
                    val previewTextSize = with(density) {
                        (min(
                            (cropRight - cropLeft).coerceAtLeast(.02f) * viewport.width,
                            (cropBottom - cropTop).coerceAtLeast(.02f) * viewport.height,
                        ) * .075f).coerceAtLeast(16f).toSp()
                    }
                    layers.forEachIndexed { index, layer ->
                        Text(
                            text = layer.text.take(120),
                            color = Color.White,
                            fontSize = previewTextSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                            style = TextStyle(shadow = Shadow(Color.Black, Offset(0f, 2f), 4f)),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset {
                                    IntOffset(
                                        (layer.x.coerceIn(0f, 1f) * viewport.width).roundToInt(),
                                        (layer.y.coerceIn(0f, 1f) * viewport.height).roundToInt(),
                                    )
                                }
                                .graphicsLayer(
                                    scaleX = layer.scale,
                                    scaleY = layer.scale,
                                    rotationZ = layer.rotation,
                                    transformOrigin = TransformOrigin(0f, 0f),
                                )
                                .pointerInput(index, viewport) {
                                    detectTransformGestures { _, pan, zoom, gestureRotation ->
                                        if (viewport.width <= 0 || viewport.height <= 0) return@detectTransformGestures
                                        val current = latestLayers.getOrNull(index) ?: return@detectTransformGestures
                                        layers = latestLayers.toMutableList().also { mutable ->
                                            mutable[index] = current.copy(
                                                x = (current.x + pan.x / viewport.width).coerceIn(0f, .95f),
                                                y = (current.y + pan.y / viewport.height).coerceIn(0f, .95f),
                                                scale = (current.scale * zoom).coerceIn(.35f, 5f),
                                                rotation = (current.rotation + gestureRotation) % 360f,
                                            )
                                        }
                                    }
                                },
                        )
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text(tr("Drag, pinch, or rotate the image, crop box, and text layers directly on the preview."), style = MaterialTheme.typography.bodySmall) }
                    item { OutlinedTextField(outputName, { outputName = it }, Modifier.fillMaxWidth(), label = { Text(tr("Output name")) }, singleLine = true) }
                    item {
                        Text(tr("Crop"), fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Free", "Original", "Square", "4:3", "16:9").forEach { value -> FilterChip(cropMode == value, { setPreset(value) }, { Text(tr(value)) }) }
                        }
                    }
                    item {
                        Text(tr("Quick edits"), fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton({ onQuickEdit(MediaEditOperation.ROTATE_RIGHT) }) { Icon(Icons.Default.RotateRight, null); Text(tr("Rotate")) }
                            OutlinedButton({ onQuickEdit(MediaEditOperation.FLIP_HORIZONTAL) }) { Icon(Icons.Default.Flip, null); Text(tr("Flip")) }
                            OutlinedButton({ onQuickEdit(MediaEditOperation.GRAYSCALE) }) { Icon(Icons.Default.FilterBAndW, null); Text(tr("Grayscale")) }
                            TextButton({ scale = 1f; offset = Offset.Zero; rotation = 0f }) { Text(tr("Reset view")) }
                        }
                    }
                    item {
                        Text(tr("Color adjustments"), fontWeight = FontWeight.Bold)
                        Text(trf("Brightness: %s", String.format(Locale.ROOT, "%+.0f%%", brightness * 100f)), style = MaterialTheme.typography.labelMedium)
                        Slider(brightness, { brightness = it }, valueRange = -1f..1f)
                        Text(trf("Contrast: %s", String.format(Locale.ROOT, "%.0f%%", contrast * 100f)), style = MaterialTheme.typography.labelMedium)
                        Slider(contrast, { contrast = it }, valueRange = .25f..2.5f)
                        Text(trf("Saturation: %s", String.format(Locale.ROOT, "%.0f%%", saturation * 100f)), style = MaterialTheme.typography.labelMedium)
                        Slider(saturation, { saturation = it }, valueRange = 0f..2f)
                    }
                    item {
                        Text(tr("Background"), fontWeight = FontWeight.Bold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(BackgroundRemovalMode.NONE to "Keep background", BackgroundRemovalMode.AUTO to "Auto remove", BackgroundRemovalMode.MANUAL to "Manual remove").forEach { (mode, label) -> FilterChip(background == mode, { background = mode }, { Text(tr(label)) }) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedVideoEditorDialogV2(photo: PhotoEntity, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val appLanguage = LocalAppLanguage.current
    fun localized(key: String): String = UiLocalizer.text(appLanguage, key)
    val repository = remember(context) { VideoEditRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val durationSeconds = max(1f, photo.durationMs / 1000f)
    var range by remember(photo.mediaId) { mutableStateOf(0f..durationSeconds) }
    var mute by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(tr("Video editor")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Trim by dragging the range handles. KeepG writes a new MP4 and leaves the source untouched."))
                RangeSlider(value = range, onValueChange = { range = it }, valueRange = 0f..durationSeconds)
                Text(String.format(Locale.ROOT, "%.1fs — %.1fs", range.start, range.endInclusive))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(mute, { mute = it })
                    Text(tr("Remove audio"))
                }
                message?.let { Text(it, color = if (it == localized("Created edited copy")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = !working && range.endInclusive - range.start >= .1f,
                onClick = {
                    working = true
                    message = null
                    scope.launch {
                        try {
                            repository.createEditedCopy(photo, (range.start * 1000).toLong(), (range.endInclusive * 1000).toLong(), mute)
                            message = localized("Created edited copy")
                            onRefresh()
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (error: Throwable) {
                            Log.e("KeepGPreview", "Video edit failed", error)
                            message = localized("Video edit failed")
                        } finally {
                            working = false
                        }
                    }
                },
            ) { Text(tr("Apply edit")) }
        },
        dismissButton = { TextButton({ if (!working) onDismiss() }) { Text(tr("Close")) } },
    )
}

@Composable
private fun MediaDetailsDialogV2(photo: PhotoEntity, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var rename by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(photo.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("File information")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                DetailRowV2(tr("Name"), photo.displayName)
                DetailRowV2(tr("Album"), photo.bucketName)
                DetailRowV2(tr("MIME type"), photo.mimeType)
                DetailRowV2(tr("Resolution"), "${photo.width} × ${photo.height}")
                DetailRowV2(tr("File size"), formatBytesV2(photo.sizeBytes))
                if (photo.durationMs > 0L) DetailRowV2(tr("Duration"), formatDuration(photo.durationMs))
                DetailRowV2(tr("Modified"), DateFormat.getDateTimeInstance().format(Date(photo.dateTaken)))
                TextButton({ rename = true }) { Icon(Icons.Default.DriveFileRenameOutline, null); Text(tr("Rename")) }
            }
        },
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
private fun DetailRowV2(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) { Text(label, Modifier.width(100.dp), fontWeight = FontWeight.Bold); Text(value, Modifier.weight(1f)) }
}

private fun fitNormalizedPointV2(position: Offset, viewport: IntSize, imageWidth: Int, imageHeight: Int, fillViewport: Boolean = false): Pair<Float, Float>? {
    if (imageWidth <= 0 || imageHeight <= 0 || viewport.width <= 0 || viewport.height <= 0) return null
    val imageAspect = imageWidth.toFloat() / imageHeight
    val viewportAspect = viewport.width.toFloat() / viewport.height
    val scale = if (fillViewport) {
        max(viewport.width.toFloat() / imageWidth, viewport.height.toFloat() / imageHeight)
    } else {
        kotlin.math.min(viewport.width.toFloat() / imageWidth, viewport.height.toFloat() / imageHeight)
    }
    val drawWidth = imageWidth * scale
    val drawHeight = imageHeight * scale
    val left = (viewport.width - drawWidth) / 2f
    val top = (viewport.height - drawHeight) / 2f
    if (!fillViewport && (position.x !in left..(left + drawWidth) || position.y !in top..(top + drawHeight))) return null
    return ((position.x - left) / drawWidth).coerceIn(0f, 1f) to ((position.y - top) / drawHeight).coerceIn(0f, 1f)
}

private fun formatBytesV2(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.ROOT, "%.1f MB", mb)
    return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0)
}
