package com.yanagikh.keepg.ui

import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.yanagikh.keepg.data.*
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun LibraryScreen(
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
    onFavoriteSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
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
            AssistChip({ showSort = true }, { Text("${tr("Sort")}: ${tr(sortLabel(settings.sortMode))}") }, leadingIcon = { Icon(Icons.Default.Sort, null) })
            if (extensionFilter.isNotBlank()) InputChip(true, { onExtensionFilter("") }, { Text(".$extensionFilter") }, trailingIcon = { Icon(Icons.Default.Close, null) })
            if (typeFilter != MediaTypeFilter.ALL) InputChip(true, { onTypeFilter(MediaTypeFilter.ALL) }, { Text(tr(typeLabel(typeFilter))) })
            if (sizeFilter != MediaSizeFilter.ANY) InputChip(true, { onSizeFilter(MediaSizeFilter.ANY) }, { Text(tr(sizeLabel(sizeFilter))) })
        }
        if (selectedIds.isNotEmpty()) {
            SelectionBar(selectedIds.size, onClearSelection, onFavoriteSelected, onShareSelected, onDeleteSelected)
        }
        PhotoGrid(
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
                        MediaTypeFilter.entries.forEach { type -> FilterChip(typeFilter == type, { onTypeFilter(type) }, { Text(tr(typeLabel(type))) }) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MediaSizeFilter.entries.forEach { size -> FilterChip(sizeFilter == size, { onSizeFilter(size) }, { Text(tr(sizeLabel(size))) }) }
                    }
                    OutlinedTextField(
                        extensionFilter,
                        onExtensionFilter,
                        label = { Text(tr("Extension")) },
                        supportingText = { Text("jpg · png · gif · mp4 · webm …") },
                        singleLine = true,
                    )
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
                    MediaSortMode.entries.forEach { mode -> FilterChip(settings.sortMode == mode, { onSort(mode) }, { Text(tr(sortLabel(mode))) }) }
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
private fun SelectionBar(count: Int, onClear: () -> Unit, onFavorite: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClear) { Icon(Icons.Default.Close, tr("Clear selection")) }
            Text(trf("Selected: %s", count), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            IconButton(onFavorite) { Icon(Icons.Default.Favorite, tr("Favorite selected")) }
            IconButton(onShare) { Icon(Icons.Default.Share, tr("Share selected")) }
            IconButton(onDelete) { Icon(Icons.Default.Delete, tr("Delete selected")) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoGrid(
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
    var lastColumnChange by remember { mutableLongStateOf(0L) }
    LazyVerticalGrid(
        GridCells.Fixed(columns.coerceIn(2, 8)),
        modifier = Modifier.fillMaxSize().pointerInput(columns) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var accumulated = 1f
                while (true) {
                    val pointerEvent = awaitPointerEvent()
                    if (pointerEvent.changes.size >= 2) {
                        accumulated *= pointerEvent.calculateZoom()
                        val now = System.currentTimeMillis()
                        if (now - lastColumnChange > 180L && abs(accumulated - 1f) > .14f) {
                            onGridColumns((if (accumulated > 1f) columns - 1 else columns + 1).coerceIn(2, 8))
                            lastColumnChange = now
                            accumulated = 1f
                        }
                        pointerEvent.changes.forEach { it.consume() }
                    }
                    if (pointerEvent.changes.none { it.pressed }) break
                }
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
            Box(
                Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(
                        onClick = { onPhoto(photo) },
                        onLongClick = { if (visible) onLongPress(photo) else onPhoto(photo) },
                    )
            ) {
                if (visible) {
                    if (photo.mimeType.startsWith("video/") && autoPlayVideos) InlineVideoPreview(photo, Modifier.fillMaxSize())
                    else AsyncImage(Uri.parse(photo.uri), photo.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

                    if (photo.mimeType.startsWith("video/")) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .78f),
                        ) {
                            Row(Modifier.padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, "Video", Modifier.size(16.dp))
                                if (photo.durationMs > 0L) Text(formatDuration(photo.durationMs), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (photo.mimeType == "image/gif") {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .78f),
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
private fun InlineVideoPreview(photo: PhotoEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(photo.uri) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(Uri.parse(photo.uri)))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        update = { it.player = player },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumsScreen(
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
    allowProtection: Boolean,
) {
    var bucketId by remember { mutableStateOf<Long?>(null) }
    var collectionId by remember { mutableStateOf<Long?>(null) }
    var favoritesOpen by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var albumQuery by remember { mutableStateOf("") }

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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            PhotoGrid(
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
            it.isNotEmpty() && (normalizedQuery.isBlank() || fuzzyContains(it.first().bucketName, normalizedQuery))
        }
        val visibleCollections = collections.filter { normalizedQuery.isBlank() || fuzzyContains(it.name, normalizedQuery) }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                OutlinedTextField(
                    albumQuery,
                    { albumQuery = it },
                    Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text(tr("Search media and albums")) },
                    singleLine = true,
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(tr("Favorites"), fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(favorites.size.toString()) },
                    leadingContent = { Icon(Icons.Default.Favorite, null) },
                    modifier = Modifier.combinedClickable(onClick = { favoritesOpen = true }, onLongClick = {}),
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Collections"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton({ create = true }) { Icon(Icons.Default.Add, tr("New collection")) }
                }
            }
            items(visibleCollections, key = { "c-${it.id}" }) { collection ->
                ListItem(
                    headlineContent = { Text(collection.name) },
                    supportingContent = { Text(trf("%s items", collectionItems.count { it.collectionId == collection.id })) },
                    leadingContent = { Icon(Icons.Default.Collections, null) },
                    modifier = Modifier.combinedClickable(onClick = { collectionId = collection.id }, onLongClick = {}),
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
                    supportingContent = { Text(trf("%s items", group.size) + if (locked) " · ${tr("Protected")}" else "") },
                    leadingContent = {
                        if (locked) Icon(Icons.Default.Lock, null)
                        else AsyncImage(Uri.parse(group.first().uri), null, Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    },
                    trailingContent = if (allowProtection) {
                        { IconButton({ if (locked) onUnlock(requireNotNull(lock)) else onLockAlbum(id, group.first().bucketName) }) { Icon(if (locked) Icons.Default.LockOpen else Icons.Default.Lock, null) } }
                    } else null,
                    modifier = Modifier.combinedClickable(onClick = { if (locked) onUnlock(requireNotNull(lock)) else bucketId = id }, onLongClick = {}),
                )
            }
        }
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

private fun fuzzyContains(text: String, query: String): Boolean {
    val normalized = text.lowercase(Locale.ROOT)
    if (normalized.contains(query)) return true
    var index = 0
    normalized.forEach { c -> if (index < query.length && c == query[index]) index++ }
    return index == query.length
}

private fun typeLabel(type: MediaTypeFilter) = when (type) {
    MediaTypeFilter.ALL -> "All"
    MediaTypeFilter.IMAGES -> "Images"
    MediaTypeFilter.VIDEOS -> "Videos"
    MediaTypeFilter.GIFS -> "GIFs"
}

private fun sizeLabel(size: MediaSizeFilter) = when (size) {
    MediaSizeFilter.ANY -> "Any size"
    MediaSizeFilter.SMALL -> "Small < 1 MB"
    MediaSizeFilter.MEDIUM -> "Medium 1–10 MB"
    MediaSizeFilter.LARGE -> "Large > 10 MB"
}

private fun sortLabel(sort: MediaSortMode) = when (sort) {
    MediaSortMode.DATE -> "Date"
    MediaSortMode.NAME -> "Name"
    MediaSortMode.SIZE -> "Size"
    MediaSortMode.EXTENSION -> "Extension"
}

internal fun formatDuration(durationMs: Long): String {
    val total = durationMs / 1000L
    val minutes = total / 60L
    val seconds = total % 60L
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}
