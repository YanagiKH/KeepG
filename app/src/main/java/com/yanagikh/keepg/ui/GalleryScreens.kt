package com.yanagikh.keepg.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yanagikh.keepg.data.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotoGrid(
    photos: List<PhotoEntity>,
    locks: List<LockEntity>,
    unlocked: Set<String>,
    onPhoto: (PhotoEntity) -> Unit,
    onLongPress: (PhotoEntity) -> Unit = {},
) {
    if (photos.isEmpty()) {
        EmptyState(Icons.Default.PhotoLibrary, "No media yet", "Grant photo and video access, then refresh your library.")
        return
    }
    LazyVerticalGrid(
        GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        gridItems(photos, key = { it.mediaId }) { photo ->
            val lock = findLock(photo, locks)
            val visible = lock == null || isUnlocked(lock, unlocked)
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(
                        onClick = { onPhoto(photo) },
                        onLongClick = { if (visible && photo.mimeType.startsWith("image/")) onLongPress(photo) else onPhoto(photo) },
                    )
            ) {
                if (visible) {
                    AsyncImage(Uri.parse(photo.uri), photo.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (photo.mimeType.startsWith("video/")) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        ) {
                            Icon(Icons.Default.PlayArrow, "Video", Modifier.padding(3.dp).size(18.dp))
                        }
                    }
                    if (photo.mimeType == "image/gif") {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                        ) { Text("GIF", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall) }
                    }
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, "Locked", tint = MaterialTheme.colorScheme.primary)
                        Text("Protected", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AlbumsScreen(
    photos: List<PhotoEntity>,
    locks: List<LockEntity>,
    unlocked: Set<String>,
    collections: List<CollectionEntity>,
    collectionItems: List<CollectionItemEntity>,
    onPhoto: (PhotoEntity) -> Unit,
    onLongPress: (PhotoEntity) -> Unit,
    onCreateCollection: (String) -> Unit,
    onLockAlbum: (Long, String) -> Unit,
    onUnlock: (LockEntity) -> Unit,
    allowProtection: Boolean,
) {
    var bucketId by remember { mutableStateOf<Long?>(null) }
    var collectionId by remember { mutableStateOf<Long?>(null) }
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    if (bucketId != null || collectionId != null) {
        val filtered = if (bucketId != null) photos.filter { it.bucketId == bucketId } else {
            val ids = collectionItems.filter { it.collectionId == collectionId }.map { it.mediaId }.toSet()
            photos.filter { it.mediaId in ids }
        }
        Column {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ bucketId = null; collectionId = null }) { Icon(Icons.Default.ArrowBack, "Back") }
                Text(
                    if (bucketId != null) photos.firstOrNull { it.bucketId == bucketId }?.bucketName ?: "Album"
                    else collections.firstOrNull { it.id == collectionId }?.name ?: "Collection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            PhotoGrid(filtered, locks, unlocked, onPhoto, onLongPress)
        }
    } else {
        val albums = photos.groupBy { it.bucketId }.values.filter { it.isNotEmpty() }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Collections", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton({ create = true }) { Icon(Icons.Default.Add, "New collection") }
                }
            }
            if (collections.isEmpty()) item { Text("Create logical albums without moving originals.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(collections, key = { "c-${it.id}" }) { collection ->
                ListItem(
                    { Text(collection.name) },
                    supportingContent = { Text("${collectionItems.count { it.collectionId == collection.id }} items") },
                    leadingContent = { Icon(Icons.Default.Collections, null) },
                    modifier = Modifier.clickable { collectionId = collection.id },
                )
            }
            item {
                HorizontalDivider()
                Text("Device albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            items(albums, key = { "a-${it.first().bucketId}" }) { group ->
                val id = group.first().bucketId
                val lock = locks.firstOrNull { it.targetType == "ALBUM" && it.targetId == id.toString() }
                val locked = allowProtection && lock != null && !isUnlocked(lock, unlocked)
                ListItem(
                    headlineContent = { Text(group.first().bucketName) },
                    supportingContent = { Text("${group.size} items${if (locked) " · Protected" else ""}") },
                    leadingContent = {
                        if (locked) Icon(Icons.Default.Lock, null)
                        else AsyncImage(Uri.parse(group.first().uri), null, Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    },
                    trailingContent = if (allowProtection) {
                        { IconButton({ if (locked) onUnlock(requireNotNull(lock)) else onLockAlbum(id, group.first().bucketName) }) { Icon(if (locked) Icons.Default.LockOpen else Icons.Default.Lock, null) } }
                    } else null,
                    modifier = Modifier.clickable { if (locked) onUnlock(requireNotNull(lock)) else bucketId = id },
                )
            }
        }
    }

    if (create) {
        AlertDialog(
            onDismissRequest = { create = false },
            title = { Text("New collection") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }) },
            confirmButton = { TextButton({ onCreateCollection(name); name = ""; create = false }) { Text("Create") } },
            dismissButton = { TextButton({ create = false }) { Text("Cancel") } },
        )
    }
}
