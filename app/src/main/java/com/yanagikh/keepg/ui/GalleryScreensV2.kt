package com.yanagikh.keepg.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.yanagikh.keepg.TextIndexProgress
import com.yanagikh.keepg.data.*
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

@Composable
internal fun LibraryScreenV2(
    photos: List<PhotoEntity>,
    availablePhotos: List<PhotoEntity>,
    locks: List<LockEntity>,
    unlocked: Set<String>,
    favorites: Set<Long>,
    selectedIds: Set<Long>,
    settings: GallerySettings,
    query: String,
    typeFilter: MediaTypeFilter,
    sizeFilter: MediaSizeFilter,
    extensionFilter: String,
    includeImageText: Boolean,
    searchBucketId: Long?,
    textIndexing: Boolean,
    textIndexProgress: TextIndexProgress?,
    indexedImageCount: Int,
    onQuery: (String) -> Unit,
    onTypeFilter: (MediaTypeFilter) -> Unit,
    onSizeFilter: (MediaSizeFilter) -> Unit,
    onExtensionFilter: (String) -> Unit,
    onIncludeImageText: (Boolean) -> Unit,
    onSearchBucketId: (Long?) -> Unit,
    onIndexImageText: (Boolean) -> Unit,
    onClearImageTextIndex: () -> Unit,
    onSort: (MediaSortMode) -> Unit,
    onSortDescending: (Boolean) -> Unit,
    onGridColumns: (Int) -> Unit,
    onPhoto: (PhotoEntity, List<Long>) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onFavoriteSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onCollectionSelected: () -> Unit,
    onProtectSelected: () -> Unit,
    onVaultSelected: () -> Unit,
    onAnalyzeSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    fullFeatures: Boolean,
) {
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    val previewScopeIds = remember(photos) { photos.map { it.mediaId } }
    val searchScopes = remember(availablePhotos) {
        availablePhotos.groupBy { it.bucketId }.mapNotNull { (bucketId, media) ->
            media.firstOrNull()?.let { bucketId to it.bucketName }
        }.sortedBy { it.second.lowercase(Locale.ROOT) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ onQuery("") }) { Icon(Icons.Default.Clear, null) } },
            placeholder = { Text(tr("Search media and albums")) },
            singleLine = true,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip({ showFilters = true }, { Text(tr("Filters")) }, leadingIcon = { Icon(Icons.Default.FilterList, null) })
            AssistChip({ showSort = true }, { Text("${tr("Sort")}: ${tr(sortLabelV2(settings.sortMode))}") }, leadingIcon = { Icon(Icons.Default.Sort, null) })
            if (extensionFilter.isNotBlank()) InputChip(true, { onExtensionFilter("") }, { Text(".$extensionFilter") }, trailingIcon = { Icon(Icons.Default.Close, null) })
            if (typeFilter != MediaTypeFilter.ALL) InputChip(true, { onTypeFilter(MediaTypeFilter.ALL) }, { Text(tr(typeLabelV2(typeFilter))) })
            if (sizeFilter != MediaSizeFilter.ANY) InputChip(true, { onSizeFilter(MediaSizeFilter.ANY) }, { Text(tr(sizeLabelV2(sizeFilter))) })
            searchBucketId?.let { bucketId ->
                val label = searchScopes.firstOrNull { it.first == bucketId }?.second ?: tr("Selected album")
                InputChip(true, { onSearchBucketId(null) }, { Text(label) }, trailingIcon = { Icon(Icons.Default.Close, null) })
            }
            if (includeImageText) InputChip(true, { onIncludeImageText(false) }, { Text(tr("Image text")) }, leadingIcon = { Icon(Icons.Default.DocumentScanner, null) })
        }
        if (selectedIds.isNotEmpty()) {
            SelectionBarV2(
                count = selectedIds.size,
                fullFeatures = fullFeatures,
                onClear = onClearSelection,
                onSelectAll = onSelectAll,
                onFavorite = onFavoriteSelected,
                onShare = onShareSelected,
                onCollection = onCollectionSelected,
                onProtect = onProtectSelected,
                onVault = onVaultSelected,
                onAnalyze = onAnalyzeSelected,
                onDelete = onDeleteSelected,
            )
        }
        PhotoGridV2(
            photos = photos,
            locks = locks,
            unlocked = unlocked,
            selectedIds = selectedIds,
            favorites = favorites,
            columns = settings.gridColumns,
            autoPlayVideos = settings.videoPreviewAutoPlay,
            layoutMode = settings.gridLayoutMode,
            thumbnailScaleMode = settings.thumbnailScaleMode,
            showMediaBadges = settings.showMediaBadges,
            animationsEnabled = settings.animationsEnabled,
            onPhoto = { photo -> if (selectedIds.isNotEmpty() && isGridMediaVisible(photo, locks, unlocked)) onToggleSelection(photo.mediaId) else onPhoto(photo, previewScopeIds) },
            onLongPress = { photo -> onToggleSelection(photo.mediaId) },
            onGridColumns = onGridColumns,
        )
    }

    if (showFilters) {
        AlertDialog(
            onDismissRequest = { showFilters = false },
            title = { Text(tr("Filters")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MediaTypeFilter.entries.forEach { type -> FilterChip(typeFilter == type, { onTypeFilter(type) }, { Text(tr(typeLabelV2(type))) }) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MediaSizeFilter.entries.forEach { size -> FilterChip(sizeFilter == size, { onSizeFilter(size) }, { Text(tr(sizeLabelV2(size))) }) }
                    }
                    OutlinedTextField(extensionFilter, onExtensionFilter, label = { Text(tr("Extension")) }, supportingText = { Text("jpg · png · gif · mp4 · webm …") }, singleLine = true)
                    Text(tr("Search range"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(searchBucketId == null, { onSearchBucketId(null) }, { Text(tr("Entire library")) })
                        searchScopes.forEach { (bucketId, label) ->
                            FilterChip(searchBucketId == bucketId, { onSearchBucketId(bucketId) }, { Text(label, maxLines = 1) })
                        }
                    }
                    if (fullFeatures) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(tr("Search text inside images"), fontWeight = FontWeight.Bold)
                                Text(trf("%s images indexed on this device", indexedImageCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(includeImageText, onIncludeImageText)
                        }
                        textIndexProgress?.let { progress ->
                            if (progress.total > 0) {
                                LinearProgressIndicator(
                                    progress = { progress.processed.toFloat() / progress.total.toFloat() },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Text(
                                trf("Indexed %s of %s images · %s failed", progress.processed, progress.total, progress.failures),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onIndexImageText(indexedImageCount > 0) },
                                enabled = !textIndexing,
                                modifier = Modifier.weight(1f),
                            ) {
                                if (textIndexing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.DocumentScanner, null)
                                Spacer(Modifier.width(6.dp))
                                Text(tr(if (indexedImageCount > 0) "Reindex image text" else "Index image text"))
                            }
                            if (indexedImageCount > 0) {
                                TextButton(onClearImageTextIndex, enabled = !textIndexing) { Text(tr("Clear index")) }
                            }
                        }
                        Text(
                            tr("Text recognition runs locally. Index only media you are comfortable storing as searchable text on this device."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = { TextButton({ showFilters = false }) { Text(tr("Close")) } },
        )
    }

    if (showSort) {
        AlertDialog(
            onDismissRequest = { showSort = false },
            title = { Text(tr("Sort")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaSortMode.entries.forEach { mode -> FilterChip(settings.sortMode == mode, { onSort(mode) }, { Text(tr(sortLabelV2(mode))) }) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (settings.sortDescending) tr("Descending") else tr("Ascending"), Modifier.weight(1f))
                        Switch(settings.sortDescending, onSortDescending)
                    }
                }
            },
            confirmButton = { TextButton({ showSort = false }) { Text(tr("Close")) } },
        )
    }
}

@Composable
private fun SelectionBarV2(
    count: Int,
    fullFeatures: Boolean,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onCollection: () -> Unit,
    onProtect: () -> Unit,
    onVault: () -> Unit,
    onAnalyze: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClear) { Icon(Icons.Default.Close, tr("Clear selection")) }
            Text(trf("Selected: %s", count), fontWeight = FontWeight.Bold)
            TextButton(onSelectAll) { Icon(Icons.Default.SelectAll, null); Spacer(Modifier.width(4.dp)); Text(tr("All")) }
            IconButton(onFavorite) { Icon(Icons.Default.Favorite, tr("Favorite selected")) }
            IconButton(onShare) { Icon(Icons.Default.Share, tr("Share selected")) }
            IconButton(onCollection) { Icon(Icons.Default.Collections, tr("Collection")) }
            if (fullFeatures) {
                IconButton(onProtect) { Icon(Icons.Default.Lock, tr("Protect")) }
                IconButton(onVault) { Icon(Icons.Default.EnhancedEncryption, tr("Vault")) }
                IconButton(onAnalyze) { Icon(Icons.Default.AutoAwesome, tr("Analyze")) }
            }
            IconButton(onDelete) { Icon(Icons.Default.Delete, tr("Delete selected")) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoGridV2(
    photos: List<PhotoEntity>,
    locks: List<LockEntity>,
    unlocked: Set<String>,
    selectedIds: Set<Long> = emptySet(),
    favorites: Set<Long> = emptySet(),
    columns: Int = 3,
    autoPlayVideos: Boolean = false,
    layoutMode: GridLayoutMode = GridLayoutMode.SQUARE,
    thumbnailScaleMode: ThumbnailScaleMode = ThumbnailScaleMode.CROP,
    showMediaBadges: Boolean = true,
    animationsEnabled: Boolean = true,
    onPhoto: (PhotoEntity) -> Unit,
    onLongPress: (PhotoEntity) -> Unit = {},
    onGridColumns: (Int) -> Unit = {},
) {
    if (photos.isEmpty()) {
        EmptyState(Icons.Default.PhotoLibrary, tr("No media yet"), tr("Grant photo and video access, then refresh your library."))
        return
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gridState = rememberLazyGridState()
    val photoById = remember(photos) { photos.associateBy { it.mediaId } }
    val latestColumns by rememberUpdatedState(columns.coerceIn(2, 8))
    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    var activeVideoId by remember { mutableStateOf<Long?>(null) }
    var gridTransforming by remember { mutableStateOf(false) }
    val latestAutoPlay by rememberUpdatedState(autoPlayVideos)
    val latestActiveVideoId by rememberUpdatedState(activeVideoId)

    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (latestAutoPlay && latestActiveVideoId != null) player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    LaunchedEffect(gridState, photos, locks, unlocked, autoPlayVideos, gridTransforming) {
        if (!autoPlayVideos || gridTransforming) {
            activeVideoId = null
            return@LaunchedEffect
        }
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long } }
            .collectLatest { visibleIds ->
                activeVideoId = visibleIds.asSequence()
                    .mapNotNull(photoById::get)
                    .firstOrNull { it.mimeType.startsWith("video/") && isGridMediaVisible(it, locks, unlocked) }
                    ?.mediaId
            }
    }

    LaunchedEffect(activeVideoId, autoPlayVideos, gridTransforming) {
        val active = activeVideoId?.let(photoById::get)
        if (!autoPlayVideos || gridTransforming || active == null) {
            player.pause()
            return@LaunchedEffect
        }
        player.setMediaItem(MediaItem.fromUri(Uri.parse(active.uri)))
        player.prepare()
        player.playWhenReady = true
    }

    LazyVerticalGrid(
        GridCells.Fixed(columns.coerceIn(2, 8)),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .then(if (animationsEnabled) Modifier.animateContentSize() else Modifier)
            .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var accumulated = 1f
                var gestureColumns = latestColumns
                var transformed = false
                while (true) {
                    val pointerEvent = awaitPointerEvent()
                    if (pointerEvent.changes.size >= 2) {
                        if (!transformed) {
                            gridTransforming = true
                            activeVideoId = null
                            transformed = true
                        }
                        accumulated *= pointerEvent.calculateZoom()
                        val nextColumns = steppedGridColumns(gestureColumns, accumulated)
                        if (nextColumns != gestureColumns) {
                            gestureColumns = nextColumns
                            onGridColumns(nextColumns)
                            accumulated = 1f
                        }
                        pointerEvent.changes.forEach { it.consume() }
                    }
                    if (pointerEvent.changes.none { it.pressed }) break
                }
                if (transformed) gridTransforming = false
            }
        },
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        gridItems(photos, key = { it.mediaId }) { photo ->
            val lock = findLock(photo, locks)
            val visible = lock == null || isUnlocked(lock, unlocked)
            val selected = photo.mediaId in selectedIds
            val activeVideo = visible && !gridTransforming && photo.mediaId == activeVideoId
            val aspectRatio = when (layoutMode) {
                GridLayoutMode.SQUARE -> 1f
                GridLayoutMode.PORTRAIT -> .75f
                GridLayoutMode.ADAPTIVE -> if (photo.width > 0 && photo.height > 0) {
                    (photo.width.toFloat() / photo.height.toFloat()).coerceIn(.65f, 1.8f)
                } else 1f
            }
            Box(
                (if (animationsEnabled) Modifier.animateItem() else Modifier)
                    .testTag("media-${photo.mediaId}")
                    .semantics { this.selected = selected }
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(8.dp))
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(
                        onClick = { onPhoto(photo) },
                        onLongClick = { if (visible) onLongPress(photo) else onPhoto(photo) },
                    )
            ) {
                if (visible) {
                    if (activeVideo) GridVideoSurface(player, photo, thumbnailScaleMode == ThumbnailScaleMode.CROP, Modifier.fillMaxSize())
                    else MediaThumbnail(photo, Modifier.fillMaxSize(), thumbnailScaleMode)

                    if (showMediaBadges && photo.mimeType.startsWith("video/")) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .80f),
                        ) {
                            Row(Modifier.padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (activeVideo) Icons.Default.Pause else Icons.Default.PlayArrow, tr("Video"), Modifier.size(16.dp))
                                if (photo.durationMs > 0L) Text(formatDuration(photo.durationMs), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (showMediaBadges && (photo.mimeType == "image/gif" || photo.displayName.endsWith(".gif", true))) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .80f),
                        ) { Text("GIF", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) }
                    }
                    if (showMediaBadges && photo.mediaId in favorites) Icon(Icons.Default.Favorite, null, Modifier.align(Alignment.TopEnd).padding(5.dp), tint = MaterialTheme.colorScheme.primary)
                    if (selected) {
                        Surface(Modifier.align(Alignment.TopStart).padding(5.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                            Icon(Icons.Default.Check, null, Modifier.padding(3.dp).size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, tr("Locked"), tint = MaterialTheme.colorScheme.primary)
                        Text(tr("Protected"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun GridVideoSurface(player: ExoPlayer, photo: PhotoEntity, fillContainer: Boolean, modifier: Modifier = Modifier) {
    FittedVideoTextureSurfaceV2(
        player = player,
        fallbackWidth = photo.width,
        fallbackHeight = photo.height,
        fillContainer = fillContainer,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
internal fun MediaThumbnail(
    photo: PhotoEntity,
    modifier: Modifier = Modifier,
    scaleMode: ThumbnailScaleMode = ThumbnailScaleMode.CROP,
) {
    val context = LocalContext.current
    val request = remember(context, photo.uri, photo.mimeType) {
        ImageRequest.Builder(context)
            .data(Uri.parse(photo.uri))
            .apply {
                if (photo.mimeType.startsWith("video/")) decoderFactory(VideoFrameDecoder.Factory())
            }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = photo.displayName,
        modifier = modifier.clipToBounds(),
        contentScale = if (scaleMode == ThumbnailScaleMode.CROP) ContentScale.Crop else ContentScale.Fit,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumsScreenV2(
    photos: List<PhotoEntity>,
    trash: List<PhotoEntity>,
    locks: List<LockEntity>,
    unlocked: Set<String>,
    collections: List<CollectionEntity>,
    collectionItems: List<CollectionItemEntity>,
    favorites: Set<Long>,
    selectedIds: Set<Long>,
    settings: GallerySettings,
    onPhoto: (PhotoEntity, List<Long>) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onGridColumns: (Int) -> Unit,
    onCreateCollection: (String) -> Unit,
    onRenameCollection: (Long, String) -> Unit,
    onClearCollection: (Long) -> Unit,
    onDeleteCollection: (Long) -> Unit,
    onRemoveFromCollection: (Long, Long) -> Unit,
    onLockAlbum: (Long, String) -> Unit,
    onUnlock: (LockEntity) -> Unit,
    onSelectMedia: (List<PhotoEntity>) -> Unit,
    onFavoriteMedia: (List<PhotoEntity>) -> Unit,
    onShareMedia: (List<PhotoEntity>) -> Unit,
    onDeleteMedia: (List<PhotoEntity>) -> Unit,
    onVaultMedia: (List<PhotoEntity>) -> Unit,
    onRestoreTrash: (List<PhotoEntity>) -> Unit,
    onDeleteTrash: (List<PhotoEntity>) -> Unit,
    allowProtection: Boolean,
    onClearSelection: () -> Unit,
    onCollectionSelected: () -> Unit,
    onProtectSelected: () -> Unit,
    onAnalyzeSelected: () -> Unit,
) {
    var bucketId by rememberSaveable { mutableStateOf<Long?>(null) }
    var collectionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var favoritesOpen by rememberSaveable { mutableStateOf(false) }
    var trashOpen by rememberSaveable { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var albumQuery by remember { mutableStateOf("") }
    var manageMedia by remember { mutableStateOf<List<PhotoEntity>?>(null) }
    var manageTitle by remember { mutableStateOf("") }
    var manageBucket by remember { mutableStateOf<Long?>(null) }
    var manageCollection by remember { mutableStateOf<Long?>(null) }
    var renameCollection by remember { mutableStateOf<CollectionEntity?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var pendingCollectionAction by remember { mutableStateOf<Pair<String, CollectionEntity>?>(null) }
    var removeFromCollection by remember { mutableStateOf<Pair<Long, PhotoEntity>?>(null) }
    var removeSelected by remember { mutableStateOf(false) }
    BackHandler(selectedIds.isNotEmpty() || bucketId != null || collectionId != null || favoritesOpen || trashOpen) {
        if (selectedIds.isNotEmpty()) onClearSelection()
        else { bucketId = null; collectionId = null; favoritesOpen = false; trashOpen = false }
    }
    val favoritesLabel = tr("Favorites")
    val albumLabel = tr("Album")
    val collectionsLabel = tr("Collections")
    val mediaById = remember(photos) { photos.associateBy { it.mediaId } }
    val collectionMediaIds = remember(collectionItems) {
        collectionItems.groupBy { it.collectionId }.mapValues { (_, items) -> items.map { it.mediaId } }
    }
    val albumsByBucket = remember(photos) { photos.groupBy { it.bucketId } }

    if (trashOpen) {
        TrashManagerV2(
            trash = trash,
            onBack = { trashOpen = false },
            onRestore = onRestoreTrash,
            onDeletePermanently = onDeleteTrash,
        )
        return
    }

    if (bucketId != null || collectionId != null || favoritesOpen) {
        val filtered = when {
            favoritesOpen -> photos.filter { it.mediaId in favorites }
            bucketId != null -> photos.filter { it.bucketId == bucketId }
            else -> {
                collectionMediaIds[collectionId].orEmpty().mapNotNull(mediaById::get)
            }
        }
        val previewScopeIds = remember(filtered) { filtered.map { it.mediaId } }
        val selectable = filtered.filter { isGridMediaVisible(it, locks, unlocked) }
        val scopedSelection = selectable.filter { it.mediaId in selectedIds }
        LaunchedEffect(selectable.map { it.mediaId }, selectedIds) {
            if (scopedSelection.size != selectedIds.size) onSelectMedia(scopedSelection)
        }
        val currentTitle = when {
            favoritesOpen -> favoritesLabel
            bucketId != null -> photos.firstOrNull { it.bucketId == bucketId }?.bucketName ?: albumLabel
            else -> collections.firstOrNull { it.id == collectionId }?.name ?: collectionsLabel
        }
        Column {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onClearSelection(); bucketId = null; collectionId = null; favoritesOpen = false }) { Icon(Icons.Default.ArrowBack, tr("Back")) }
                Text(
                    currentTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton({
                    manageMedia = filtered
                    manageTitle = currentTitle
                    manageBucket = bucketId
                    manageCollection = collectionId
                }) { Icon(Icons.Default.MoreVert, tr("Manage album")) }
            }
            if (scopedSelection.isNotEmpty()) {
                SelectionBarV2(
                    count = scopedSelection.size, fullFeatures = allowProtection,
                    onClear = onClearSelection, onSelectAll = { onSelectMedia(selectable) },
                    onFavorite = { onFavoriteMedia(scopedSelection) },
                    onShare = { onShareMedia(scopedSelection) }, onCollection = onCollectionSelected,
                    onProtect = onProtectSelected, onVault = { onVaultMedia(scopedSelection) },
                    onAnalyze = onAnalyzeSelected, onDelete = { onDeleteMedia(scopedSelection) },
                )
                if (collectionId != null) TextButton({ removeSelected = true }) { Text(tr("Remove from collection")) }
            }
            if (removeSelected) AlertDialog(
                onDismissRequest = { removeSelected = false },
                title = { Text(tr("Remove from collection")) },
                text = { Text(trf("Remove %s selected references? Original files stay on the device.", scopedSelection.size)) },
                confirmButton = { TextButton({
                    collectionId?.let { id -> scopedSelection.forEach { onRemoveFromCollection(id, it.mediaId) } }
                    onClearSelection(); removeSelected = false
                }) { Text(tr("Remove")) } },
                dismissButton = { TextButton({ removeSelected = false }) { Text(tr("Cancel")) } },
            )
            PhotoGridV2(
                filtered,
                locks,
                unlocked,
                selectedIds,
                favorites,
                settings.gridColumns,
                settings.videoPreviewAutoPlay,
                layoutMode = settings.gridLayoutMode,
                thumbnailScaleMode = settings.thumbnailScaleMode,
                showMediaBadges = settings.showMediaBadges,
                animationsEnabled = settings.animationsEnabled,
                onPhoto = { photo -> if (selectedIds.isNotEmpty() && isGridMediaVisible(photo, locks, unlocked)) onToggleSelection(photo.mediaId) else onPhoto(photo, previewScopeIds) },
                onLongPress = { photo -> if (isGridMediaVisible(photo, locks, unlocked)) onToggleSelection(photo.mediaId) },
                onGridColumns = onGridColumns,
            )
        }
    } else {
        val normalizedQuery = albumQuery.trim().lowercase(Locale.ROOT)
        val albums = albumsByBucket.values.filter {
            it.isNotEmpty() && (normalizedQuery.isBlank() || fuzzyContainsV2(it.first().bucketName, normalizedQuery))
        }
        val visibleCollections = collections.filter { normalizedQuery.isBlank() || fuzzyContainsV2(it.name, normalizedQuery) }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                OutlinedTextField(albumQuery, { albumQuery = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text(tr("Search media and albums")) }, singleLine = true)
            }
            item {
                val favoriteMedia = photos.filter { it.mediaId in favorites }
                ListItem(
                    headlineContent = { Text(favoritesLabel, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(favorites.size.toString()) },
                    leadingContent = { Icon(Icons.Default.Favorite, null) },
                    trailingContent = { IconButton({ manageMedia = favoriteMedia; manageTitle = favoritesLabel; manageBucket = null; manageCollection = null }) { Icon(Icons.Default.MoreVert, tr("Manage")) } },
                    modifier = Modifier.combinedClickable(onClick = { favoritesOpen = true }, onLongClick = { manageMedia = favoriteMedia; manageTitle = favoritesLabel; manageBucket = null; manageCollection = null }),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(tr("Trash"), fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(trf("%s items · restore or permanently delete", trash.size)) },
                    leadingContent = { Icon(Icons.Default.DeleteSweep, null) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                    modifier = Modifier.combinedClickable(onClick = { trashOpen = true }, onLongClick = { trashOpen = true }),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(collectionsLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton({ create = true }) { Icon(Icons.Default.Add, tr("New collection")) }
                }
            }
            items(visibleCollections, key = { "c-${it.id}" }) { collection ->
                val media = collectionMediaIds[collection.id].orEmpty().mapNotNull(mediaById::get)
                ListItem(
                    headlineContent = { Text(collection.name) },
                    supportingContent = { Text(trf("%s items", media.size)) },
                    leadingContent = { if (media.isNotEmpty()) MediaThumbnail(media.first(), Modifier.size(50.dp).clip(RoundedCornerShape(10.dp))) else Icon(Icons.Default.Collections, null) },
                    trailingContent = { IconButton({ manageMedia = media; manageTitle = collection.name; manageBucket = null; manageCollection = collection.id }) { Icon(Icons.Default.MoreVert, tr("Manage")) } },
                    modifier = Modifier.combinedClickable(onClick = { collectionId = collection.id }, onLongClick = { manageMedia = media; manageTitle = collection.name; manageBucket = null; manageCollection = collection.id }),
                )
            }
            item {
                HorizontalDivider()
                Text(tr("Device albums"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            items(albums, key = { "a-${it.first().bucketId}" }) { group ->
                val id = group.first().bucketId
                val lock = locks.firstOrNull { it.targetType == "ALBUM" && it.targetId == id.toString() }
                val locked = allowProtection && lock != null && !isUnlocked(lock, unlocked)
                ListItem(
                    headlineContent = { Text(group.first().bucketName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(if (locked) trf("%s items · Protected", group.size) else trf("%s items", group.size)) },
                    leadingContent = { if (locked) Icon(Icons.Default.Lock, null) else MediaThumbnail(group.first(), Modifier.size(50.dp).clip(RoundedCornerShape(10.dp))) },
                    trailingContent = { IconButton({ manageMedia = group; manageTitle = group.first().bucketName; manageBucket = id; manageCollection = null }) { Icon(Icons.Default.MoreVert, tr("Manage album")) } },
                    modifier = Modifier.combinedClickable(
                        onClick = { if (locked) onUnlock(requireNotNull(lock)) else bucketId = id },
                        onLongClick = { manageMedia = group; manageTitle = group.first().bucketName; manageBucket = id },
                    ),
                )
            }
        }
    }

    manageMedia?.let { media ->
        val bucket = manageBucket
        val collection = manageCollection?.let { id -> collections.firstOrNull { it.id == id } }
        val albumLock = bucket?.let { id -> locks.firstOrNull { it.targetType == "ALBUM" && it.targetId == id.toString() } }
        AlertDialog(
            onDismissRequest = { manageMedia = null; manageBucket = null; manageCollection = null },
            title = { Text(manageTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(trf("%s items", media.size), style = MaterialTheme.typography.bodySmall)
                    AlbumActionButton(Icons.Default.SelectAll, tr("Select all")) {
                        bucketId = bucket; collectionId = collection?.id
                        favoritesOpen = bucket == null && collection == null
                        onSelectMedia(media.filter { isGridMediaVisible(it, locks, unlocked) })
                        manageMedia = null; manageBucket = null; manageCollection = null
                    }
                    AlbumActionButton(Icons.Default.Favorite, tr("Favorite all")) { onFavoriteMedia(media); manageMedia = null }
                    AlbumActionButton(Icons.Default.Share, tr("Share")) { onShareMedia(media); manageMedia = null }
                    if (collection != null) {
                        AlbumActionButton(Icons.Default.Edit, tr("Rename collection")) {
                            renameCollection = collection
                            renameValue = collection.name
                            manageMedia = null
                            manageCollection = null
                        }
                        AlbumActionButton(Icons.Default.RemoveCircleOutline, tr("Clear collection")) {
                            pendingCollectionAction = "clear" to collection
                            manageMedia = null
                            manageCollection = null
                        }
                        AlbumActionButton(Icons.Default.DeleteOutline, tr("Delete collection"), destructive = true) {
                            pendingCollectionAction = "delete" to collection
                            manageMedia = null
                            manageCollection = null
                        }
                    }
                    if (allowProtection && bucket != null) {
                        AlbumActionButton(if (albumLock == null) Icons.Default.Lock else Icons.Default.LockOpen, if (albumLock == null) tr("Protect") else tr("Unlock")) {
                            if (albumLock == null) onLockAlbum(bucket, manageTitle) else onUnlock(albumLock)
                            manageMedia = null
                        }
                        AlbumActionButton(Icons.Default.EnhancedEncryption, tr("Copy album to Vault")) { onVaultMedia(media); manageMedia = null }
                    }
                    AlbumActionButton(Icons.Default.Delete, tr(if (collection == null) "Delete" else "Delete media from device"), destructive = true) { onDeleteMedia(media); manageMedia = null }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ manageMedia = null; manageBucket = null; manageCollection = null }) { Text(tr("Close")) } },
        )
    }

    renameCollection?.let { collection ->
        AlertDialog(
            onDismissRequest = { renameCollection = null },
            title = { Text(tr("Rename collection")) },
            text = { OutlinedTextField(renameValue, { renameValue = it }, label = { Text(tr("Name")) }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = { onRenameCollection(collection.id, renameValue); renameCollection = null },
                    enabled = renameValue.isNotBlank(),
                ) { Text(tr("Save")) }
            },
            dismissButton = { TextButton({ renameCollection = null }) { Text(tr("Cancel")) } },
        )
    }

    pendingCollectionAction?.let { (action, collection) ->
        val deleting = action == "delete"
        AlertDialog(
            onDismissRequest = { pendingCollectionAction = null },
            title = { Text(tr(if (deleting) "Delete collection" else "Clear collection")) },
            text = { Text(tr(if (deleting) "The collection will be deleted. Media files stay on your device." else "All items will be removed from this collection. Media files stay on your device.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleting) onDeleteCollection(collection.id) else onClearCollection(collection.id)
                        if (collectionId == collection.id) collectionId = null
                        pendingCollectionAction = null
                    }
                ) { Text(tr(if (deleting) "Delete" else "Clear"), color = if (deleting) MaterialTheme.colorScheme.error else LocalContentColor.current) }
            },
            dismissButton = { TextButton({ pendingCollectionAction = null }) { Text(tr("Cancel")) } },
        )
    }

    removeFromCollection?.let { (id, photo) ->
        AlertDialog(
            onDismissRequest = { removeFromCollection = null },
            title = { Text(tr("Remove from collection")) },
            text = { Text(trf("Remove %s from this collection? The media file stays on your device.", photo.displayName)) },
            confirmButton = {
                TextButton({ onRemoveFromCollection(id, photo.mediaId); removeFromCollection = null }) { Text(tr("Remove")) }
            },
            dismissButton = { TextButton({ removeFromCollection = null }) { Text(tr("Cancel")) } },
        )
    }

    if (create) {
        AlertDialog(
            onDismissRequest = { create = false },
            title = { Text(tr("New collection")) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(tr("Name")) }) },
            confirmButton = { TextButton({ onCreateCollection(name); name = ""; create = false }) { Text(tr("Create")) } },
            dismissButton = { TextButton({ create = false }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
private fun TrashManagerV2(
    trash: List<PhotoEntity>,
    onBack: () -> Unit,
    onRestore: (List<PhotoEntity>) -> Unit,
    onDeletePermanently: (List<PhotoEntity>) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Default.ArrowBack, tr("Back")) }
            Column(Modifier.weight(1f)) {
                Text(tr("Trash"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(trf("%s recoverable items", trash.size), style = MaterialTheme.typography.labelMedium)
            }
            if (trash.isNotEmpty()) {
                TextButton({ onRestore(trash) }) { Text(tr("Restore all")) }
            }
        }
        if (trash.isEmpty()) {
            EmptyState(Icons.Default.DeleteSweep, tr("Trash is empty"), tr("Items moved to Android MediaStore trash appear here until restored or expired."))
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(trash, key = { "trash-${it.uri}" }) { media ->
                    ElevatedCard {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            MediaThumbnail(media, Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(media.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(media.bucketName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton({ onRestore(listOf(media)) }) { Icon(Icons.Default.RestoreFromTrash, tr("Restore")) }
                            IconButton({ onDeletePermanently(listOf(media)) }) { Icon(Icons.Default.DeleteForever, tr("Delete permanently"), tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onDeletePermanently(trash) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(tr("Delete all permanently"), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, destructive: Boolean = false, action: () -> Unit) {
    TextButton(action, Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current)
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), color = if (destructive) MaterialTheme.colorScheme.error else LocalContentColor.current)
    }
}

private fun isGridMediaVisible(photo: PhotoEntity, locks: List<LockEntity>, unlocked: Set<String>): Boolean {
    val lock = findLock(photo, locks)
    return lock == null || isUnlocked(lock, unlocked)
}

private fun fuzzyContainsV2(text: String, query: String): Boolean {
    val normalized = text.lowercase(Locale.ROOT)
    if (normalized.contains(query)) return true
    var index = 0
    normalized.forEach { c -> if (index < query.length && c == query[index]) index++ }
    return index == query.length
}

private fun typeLabelV2(type: MediaTypeFilter) = when (type) {
    MediaTypeFilter.ALL -> "All"
    MediaTypeFilter.IMAGES -> "Images"
    MediaTypeFilter.VIDEOS -> "Videos"
    MediaTypeFilter.GIFS -> "GIFs"
}

private fun sizeLabelV2(size: MediaSizeFilter) = when (size) {
    MediaSizeFilter.ANY -> "Any size"
    MediaSizeFilter.SMALL -> "Small < 1 MB"
    MediaSizeFilter.MEDIUM -> "Medium 1–10 MB"
    MediaSizeFilter.LARGE -> "Large > 10 MB"
}

private fun sortLabelV2(sort: MediaSortMode) = when (sort) {
    MediaSortMode.DATE -> "Date"
    MediaSortMode.NAME -> "Name"
    MediaSortMode.SIZE -> "Size"
    MediaSortMode.EXTENSION -> "Extension"
}
