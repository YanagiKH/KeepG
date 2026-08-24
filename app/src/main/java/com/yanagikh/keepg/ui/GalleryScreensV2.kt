package com.yanagikh.keepg.ui

import android.net.Uri
import android.view.TextureView
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
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.video.videoFramePercent
import com.yanagikh.keepg.data.*
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun LibraryScreenV2(
    photos: List<PhotoEntity>,
    locks: List<LockEntity>,
    unlocked: Set<String>,
    favorites: Set<Long>,
    selectedIds: Set<Long>,
    settings: GallerySettings,
    query: String,
    typeFilter: MediaTypeFilter,
    sizeFilter: MediaSizeFilter,
    extensionFilter: String,
    onQuery: (String) -> Unit,
    onTypeFilter: (MediaTypeFilter) -> Unit,
    onSizeFilter: (MediaSizeFilter) -> Unit,
    onExtensionFilter: (String) -> Unit,
    onSort: (MediaSortMode) -> Unit,
    onSortDescending: (Boolean) -> Unit,
    onGridColumns: (Int) -> Unit,
    onPhoto: (PhotoEntity) -> Unit,
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
            onPhoto = { photo -> if (selectedIds.isNotEmpty()) onToggleSelection(photo.mediaId) else onPhoto(photo) },
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
            TextButton(onSelectAll) { Icon(Icons.Default.SelectAll, null); Spacer(Modifier.width(4.dp)); Text("All") }
            IconButton(onFavorite) { Icon(Icons.Default.Favorite, tr("Favorite selected")) }
            IconButton(onShare) { Icon(Icons.Default.Share, tr("Share selected")) }
            IconButton(onCollection) { Icon(Icons.Default.Collections, tr("Collection")) }
            if (fullFeatures) {
                IconButton(onProtect) { Icon(Icons.Default.Lock, tr("Protect")) }
                IconButton(onVault) { Icon(Icons.Default.EnhancedEncryption, "Vault") }
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
    onPhoto: (PhotoEntity) -> Unit,
    onLongPress: (PhotoEntity) -> Unit = {},
    onGridColumns: (Int) -> Unit = {},
) {
    if (photos.isEmpty()) {
        EmptyState(Icons.Default.PhotoLibrary, tr("No media yet"), tr("Grant photo and video access, then refresh your library."))
        return
    }
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val photoById = remember(photos) { photos.associateBy { it.mediaId } }
    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    var activeVideoId by remember { mutableStateOf<Long?>(null) }
    var gridTransforming by remember { mutableStateOf(false) }
    var lastColumnChange by remember { mutableLongStateOf(0L) }

    DisposableEffect(player) { onDispose { player.release() } }

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
        modifier = Modifier.fillMaxSize().clipToBounds().animateContentSize().pointerInput(columns) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var accumulated = 1f
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
                        val now = System.currentTimeMillis()
                        if (now - lastColumnChange > 150L && abs(accumulated - 1f) > .16f) {
                            onGridColumns((if (accumulated > 1f) columns - 1 else columns + 1).coerceIn(2, 8))
                            lastColumnChange = now
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
            Box(
                Modifier
                    .animateItem()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(
                        onClick = { onPhoto(photo) },
                        onLongClick = { if (visible) onLongPress(photo) else onPhoto(photo) },
                    )
            ) {
                if (visible) {
                    if (activeVideo) GridVideoSurface(player, Modifier.fillMaxSize())
                    else MediaThumbnail(photo, Modifier.fillMaxSize())

                    if (photo.mimeType.startsWith("video/")) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .80f),
                        ) {
                            Row(Modifier.padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (activeVideo) Icons.Default.Pause else Icons.Default.PlayArrow, "Video", Modifier.size(16.dp))
                                if (photo.durationMs > 0L) Text(formatDuration(photo.durationMs), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (photo.mimeType == "image/gif" || photo.displayName.endsWith(".gif", true)) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .80f),
                        ) { Text("GIF", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) }
                    }
                    if (photo.mediaId in favorites) Icon(Icons.Default.Favorite, null, Modifier.align(Alignment.TopEnd).padding(5.dp), tint = MaterialTheme.colorScheme.primary)
                    if (selected) {
                        Surface(Modifier.align(Alignment.TopStart).padding(5.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary) {
                            Icon(Icons.Default.Check, null, Modifier.padding(3.dp).size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, "Locked", tint = MaterialTheme.colorScheme.primary)
                        Text(tr("Protected"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun GridVideoSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { context -> TextureView(context).also(player::setVideoTextureView) },
        update = player::setVideoTextureView,
        onRelease = player::clearVideoTextureView,
    )
}

@Composable
internal fun MediaThumbnail(photo: PhotoEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(context, photo.uri, photo.mimeType) {
        ImageRequest.Builder(context)
            .data(Uri.parse(photo.uri))
            .apply {
                if (photo.mimeType.startsWith("video/")) {
                    decoderFactory(VideoFrameDecoder.Factory())
                    videoFramePercent(0.35)
                }
            }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = photo.displayName,
        modifier = modifier.clipToBounds(),
        contentScale = ContentScale.Crop,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumsScreenV2(
    photos: List<PhotoEntity>,
    locks: List<LockEntity>,
    unlocked: Set<String>,
    collections: List<CollectionEntity>,
    collectionItems: List<CollectionItemEntity>,
    favorites: Set<Long>,
    selectedIds: Set<Long>,
    settings: GallerySettings,
    onPhoto: (PhotoEntity) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onGridColumns: (Int) -> Unit,
    onCreateCollection: (String) -> Unit,
    onLockAlbum: (Long, String) -> Unit,
    onUnlock: (LockEntity) -> Unit,
    onSelectMedia: (List<PhotoEntity>) -> Unit,
    onFavoriteMedia: (List<PhotoEntity>) -> Unit,
    onShareMedia: (List<PhotoEntity>) -> Unit,
    onDeleteMedia: (List<PhotoEntity>) -> Unit,
    onVaultMedia: (List<PhotoEntity>) -> Unit,
    allowProtection: Boolean,
) {
    var bucketId by remember { mutableStateOf<Long?>(null) }
    var collectionId by remember { mutableStateOf<Long?>(null) }
    var favoritesOpen by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var albumQuery by remember { mutableStateOf("") }
    var manageMedia by remember { mutableStateOf<List<PhotoEntity>?>(null) }
    var manageTitle by remember { mutableStateOf("") }
    var manageBucket by remember { mutableStateOf<Long?>(null) }

    if (bucketId != null || collectionId != null || favoritesOpen) {
        val filtered = when {
            favoritesOpen -> photos.filter { it.mediaId in favorites }
            bucketId != null -> photos.filter { it.bucketId == bucketId }
            else -> {
                val ids = collectionItems.filter { it.collectionId == collectionId }.map { it.mediaId }.toSet()
                photos.filter { it.mediaId in ids }
            }
        }
        Column {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ bucketId = null; collectionId = null; favoritesOpen = false }) { Icon(Icons.Default.ArrowBack, tr("Back")) }
                Text(
                    when {
                        favoritesOpen -> tr("Favorites")
                        bucketId != null -> photos.firstOrNull { it.bucketId == bucketId }?.bucketName ?: tr("Album")
                        else -> collections.firstOrNull { it.id == collectionId }?.name ?: tr("Collections")
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton({
                    manageMedia = filtered
                    manageTitle = when {
                        favoritesOpen -> tr("Favorites")
                        bucketId != null -> photos.firstOrNull { it.bucketId == bucketId }?.bucketName ?: tr("Album")
                        else -> collections.firstOrNull { it.id == collectionId }?.name ?: tr("Collections")
                    }
                    manageBucket = bucketId
                }) { Icon(Icons.Default.MoreVert, "Manage album") }
            }
            PhotoGridV2(
                filtered,
                locks,
                unlocked,
                selectedIds,
                favorites,
                settings.gridColumns,
                settings.videoPreviewAutoPlay,
                onPhoto = { photo -> if (selectedIds.isNotEmpty()) onToggleSelection(photo.mediaId) else onPhoto(photo) },
                onLongPress = { onToggleSelection(it.mediaId) },
                onGridColumns = onGridColumns,
            )
        }
    } else {
        val normalizedQuery = albumQuery.trim().lowercase(Locale.ROOT)
        val albums = photos.groupBy { it.bucketId }.values.filter {
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
                    headlineContent = { Text(tr("Favorites"), fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(favorites.size.toString()) },
                    leadingContent = { Icon(Icons.Default.Favorite, null) },
                    trailingContent = { IconButton({ manageMedia = favoriteMedia; manageTitle = tr("Favorites"); manageBucket = null }) { Icon(Icons.Default.MoreVert, "Manage") } },
                    modifier = Modifier.combinedClickable(onClick = { favoritesOpen = true }, onLongClick = { manageMedia = favoriteMedia; manageTitle = tr("Favorites"); manageBucket = null }),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Collections"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton({ create = true }) { Icon(Icons.Default.Add, tr("New collection")) }
                }
            }
            items(visibleCollections, key = { "c-${it.id}" }) { collection ->
                val ids = collectionItems.filter { it.collectionId == collection.id }.map { it.mediaId }.toSet()
                val media = photos.filter { it.mediaId in ids }
                ListItem(
                    headlineContent = { Text(collection.name) },
                    supportingContent = { Text("${media.size} items") },
                    leadingContent = { if (media.isNotEmpty()) MediaThumbnail(media.first(), Modifier.size(50.dp).clip(RoundedCornerShape(10.dp))) else Icon(Icons.Default.Collections, null) },
                    trailingContent = { IconButton({ manageMedia = media; manageTitle = collection.name; manageBucket = null }) { Icon(Icons.Default.MoreVert, "Manage") } },
                    modifier = Modifier.combinedClickable(onClick = { collectionId = collection.id }, onLongClick = { manageMedia = media; manageTitle = collection.name; manageBucket = null }),
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
                    supportingContent = { Text("${group.size} items${if (locked) " · ${tr("Protected")}" else ""}") },
                    leadingContent = { if (locked) Icon(Icons.Default.Lock, null) else MediaThumbnail(group.first(), Modifier.size(50.dp).clip(RoundedCornerShape(10.dp))) },
                    trailingContent = {
                        IconButton({ manageMedia = group; manageTitle = group.first().bucketName; manageBucket = id }) { Icon(Icons.Default.MoreVert, "Manage album") }
                    },
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
        val albumLock = bucket?.let { id -> locks.firstOrNull { it.targetType == "ALBUM" && it.targetId == id.toString() } }
        AlertDialog(
            onDismissRequest = { manageMedia = null; manageBucket = null },
            title = { Text(manageTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${media.size} items", style = MaterialTheme.typography.bodySmall)
                    AlbumActionButton(Icons.Default.SelectAll, "Select all") { onSelectMedia(media); manageMedia = null }
                    AlbumActionButton(Icons.Default.Favorite, "Favorite all") { onFavoriteMedia(media); manageMedia = null }
                    AlbumActionButton(Icons.Default.Share, tr("Share")) { onShareMedia(media); manageMedia = null }
                    if (allowProtection && bucket != null) {
                        AlbumActionButton(if (albumLock == null) Icons.Default.Lock else Icons.Default.LockOpen, if (albumLock == null) tr("Protect") else tr("Unlock")) {
                            if (albumLock == null) onLockAlbum(bucket, manageTitle) else onUnlock(albumLock)
                            manageMedia = null
                        }
                        AlbumActionButton(Icons.Default.EnhancedEncryption, "Copy album to Vault") { onVaultMedia(media); manageMedia = null }
                    }
                    AlbumActionButton(Icons.Default.Delete, tr("Delete"), destructive = true) { onDeleteMedia(media); manageMedia = null }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ manageMedia = null; manageBucket = null }) { Text(tr("Close")) } },
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
