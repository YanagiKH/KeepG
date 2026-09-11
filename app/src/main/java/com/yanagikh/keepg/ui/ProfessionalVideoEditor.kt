@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.yanagikh.keepg.ui

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yanagikh.keepg.agent.AgentEntryButton
import com.yanagikh.keepg.data.PhotoEntity
import com.yanagikh.keepg.editor.*
import kotlinx.coroutines.*
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfessionalVideoEditor(photo: PhotoEntity, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    fun localized(key: String) = UiLocalizer.text(language, key)
    val scope = rememberCoroutineScope()
    var duration by remember(photo.mediaId) { mutableLongStateOf(photo.durationMs.coerceAtLeast(1L)) }
    var history by remember(photo.mediaId) { mutableStateOf(EditHistory(VideoEditSpec(endMs = duration, outputName = photo.displayName.substringBeforeLast('.') + "_edit"))) }
    val spec = history.value
    var loaded by remember { mutableStateOf(false) }
    var scrub by remember { mutableLongStateOf(0) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var playing by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var issue by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var exportJob by remember { mutableStateOf<Job?>(null) }
    fun change(next: VideoEditSpec) { history = history.change(next) }
    fun slide(next: VideoEditSpec) { history = history.begin().preview(next) }
    fun finish() { history = history.finish() }
    fun close() { exportJob?.cancel(); onDismiss() }
    LaunchedEffect(photo.uri, scrub) {
        delay(120)
        try {
            val result = withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, Uri.parse(photo.uri))
                    val length = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: duration
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= 27) r.getScaledFrameAtTime(scrub * 1000, MediaMetadataRetriever.OPTION_CLOSEST, 640, 360)
                    else {
                        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLongOrNull() ?: 0
                        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLongOrNull() ?: 0
                        require(w * h in 1..16_000_000L)
                        r.getFrameAtTime(scrub * 1000, MediaMetadataRetriever.OPTION_CLOSEST)?.let { raw ->
                            val scale = min(1f, 640f / maxOf(raw.width, raw.height))
                            Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true).also { if (it !== raw) raw.recycle() }
                        }
                    }
                    length to requireNotNull(bitmap)
                } finally { r.release() }
            }
            frame = result.second
            if (!loaded) { duration = result.first.coerceAtLeast(1L); history = EditHistory(spec.copy(endMs = duration)); loaded = true }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { issue = "Video preview unavailable" }
        catch (_: OutOfMemoryError) { issue = "Video preview unavailable" }
    }
    Dialog(::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("video-editor")) {
            Column(Modifier.safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(::close) { Icon(Icons.Default.Close, tr("Close")) }
                    Text(tr("Video editor"), Modifier.weight(1f))
                    AgentEntryButton()
                    IconButton({ history = history.undo() }, enabled = !working && history.past.isNotEmpty()) { Icon(Icons.Default.Undo, tr("Undo")) }
                    IconButton({ history = history.redo() }, enabled = !working && history.future.isNotEmpty()) { Icon(Icons.Default.Redo, tr("Redo")) }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (playing && !working) VideoExportPreview(photo, spec, Modifier.fillMaxWidth().height(220.dp))
                    else frame?.let { bitmap ->
                        Canvas(Modifier.fillMaxWidth().height(220.dp).testTag("video-crop").pointerInput(bitmap, working) {
                            if (working) return@pointerInput
                            val scale = min(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
                            val w = bitmap.width * scale; val h = bitmap.height * scale
                            val x = (size.width - w) / 2; val y = (size.height - h) / 2
                            var handle = CropHandle.MOVE
                            detectDragGestures(onDragStart = { p -> history = history.begin(); handle = EditGeometry.hit(history.value.crop, (p.x - x) / w, (p.y - y) / h, 32 / w, 32 / h) },
                                onDragEnd = ::finish, onDragCancel = ::finish) { event, delta ->
                                event.consume(); history = history.preview(history.value.copy(crop = EditGeometry.drag(history.value.crop, handle, delta.x / w, delta.y / h)))
                            }
                        }) {
                            val scale = min(size.width / bitmap.width, size.height / bitmap.height)
                            val w = bitmap.width * scale; val h = bitmap.height * scale
                            val x = (size.width - w) / 2; val y = (size.height - h) / 2
                            drawIntoCanvas { c -> c.nativeCanvas.drawBitmap(bitmap, null, android.graphics.RectF(x, y, x + w, y + h), null) }
                            val crop = spec.crop
                            drawRect(Color.White, Offset(x + crop.left * w, y + crop.top * h), Size(crop.width * w, crop.height * h), style = Stroke(3.dp.toPx()))
                            listOf(crop.left to crop.top, crop.right to crop.top, crop.left to crop.bottom, crop.right to crop.bottom).forEach { (cx, cy) -> drawCircle(Color.White, 6.dp.toPx(), Offset(x + cx * w, y + cy * h)) }
                        }
                    }
                    TextButton({ playing = !playing }, enabled = loaded && !working) { Text(tr(if (playing) "Drag crop" else "Play edited preview")) }
                    Text(tr("Drag corners to resize the crop; drag inside to move it. Preview playback applies crop, rotation and speed."), style = MaterialTheme.typography.bodySmall)
                    Text(tr("Trim range"), style = MaterialTheme.typography.titleSmall)
                    RangeSlider(spec.startMs.toFloat()..spec.endMs.toFloat(), { range -> if (range.endInclusive - range.start >= 1f) slide(spec.copy(startMs = range.start.toLong(), endMs = range.endInclusive.toLong())) },
                        enabled = loaded && !working, valueRange = 0f..duration.toFloat(), onValueChangeFinished = ::finish)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(spec.startMs.toString(), { it.toLongOrNull()?.takeIf { v -> v in 0 until spec.endMs }?.let { v -> change(spec.copy(startMs = v)) } }, Modifier.weight(1f), enabled = !working, label = { Text(tr("Start (ms)")) }, singleLine = true)
                        OutlinedTextField(spec.endMs.toString(), { it.toLongOrNull()?.takeIf { v -> v in (spec.startMs + 1)..duration }?.let { v -> change(spec.copy(endMs = v)) } }, Modifier.weight(1f), enabled = !working, label = { Text(tr("End (ms)")) }, singleLine = true)
                    }
                    Text(tr("Preview position")); Slider(scrub.toFloat().coerceIn(0f, duration.toFloat()), { scrub = it.toLong() }, enabled = !working, valueRange = 0f..duration.toFloat())
                    if (!working) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(spec.crop == CropBounds(), { change(spec.copy(crop = CropBounds())) }, { Text(tr("Original")) })
                            listOf("1:1" to 1f, "4:3" to 4f / 3, "16:9" to 16f / 9).forEach { (label, aspect) ->
                                AssistChip({ change(spec.copy(crop = EditGeometry.preset(aspect, frame?.let { it.width.toFloat() / it.height } ?: 1f))) }, { Text(label) })
                            }
                        }
                        val c = spec.crop
                        EditorSlider("Crop left", c.left, 0f..(c.right - .02f), { slide(spec.copy(crop = c.copy(left = it))) }, ::finish)
                        EditorSlider("Crop top", c.top, 0f..(c.bottom - .02f), { slide(spec.copy(crop = c.copy(top = it))) }, ::finish)
                        EditorSlider("Crop right", c.right, (c.left + .02f)..1f, { slide(spec.copy(crop = c.copy(right = it))) }, ::finish)
                        EditorSlider("Crop bottom", c.bottom, (c.top + .02f)..1f, { slide(spec.copy(crop = c.copy(bottom = it))) }, ::finish)
                        EditorSlider("Rotation", spec.rotation, -180f..180f, { slide(spec.copy(rotation = it)) }, ::finish)
                        EditorSlider("Playback speed", spec.speed, .25f..4f, { slide(spec.copy(speed = it)) }, ::finish)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(spec.flipX, { change(spec.copy(flipX = !spec.flipX)) }, { Text(tr("Flip horizontal")) })
                            FilterChip(spec.flipY, { change(spec.copy(flipY = !spec.flipY)) }, { Text(tr("Flip vertical")) })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(spec.mute, { change(spec.copy(mute = it)) }); Text(tr("Remove audio")) }
                        Text(tr("Output height"))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(0, 480, 720, 1080).forEach { height -> FilterChip(spec.height == height, { change(spec.copy(height = height)) }, { Text(if (height == 0) tr("Original") else "$height px") }) } }
                        OutlinedTextField(spec.outputName, { change(spec.copy(outputName = it.take(100))) }, Modifier.fillMaxWidth(), label = { Text(tr("Output name")) }, singleLine = true)
                    }
                    Text(tr("KeepG writes a new H.264/AAC MP4. Export requires a compatible device encoder and may take longer than playback."), style = MaterialTheme.typography.bodySmall)
                    issue?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
                    if (working) { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); OutlinedButton({ exportJob?.cancel() }) { Text(tr("Cancel export")) } }
                    else Button(enabled = loaded && runCatching { spec.validate(duration) }.isSuccess, modifier = Modifier.fillMaxWidth(), onClick = {
                        working = true; playing = false; issue = null; progress = 0f
                        val captured = spec
                        exportJob = scope.launch {
                            try { VideoExporter(context).export(photo, captured) { progress = it }; onRefresh(); onDismiss() }
                            catch (_: CancellationException) { issue = "Export cancelled" }
                            catch (_: Exception) { issue = "Video edit failed" }
                            catch (_: OutOfMemoryError) { issue = "Video edit failed" }
                            finally { working = false }
                        }
                    }) { Text(tr("Save edited copy")) }
                }
            }
        }
    }
}

@Composable
private fun VideoExportPreview(photo: PhotoEntity, spec: VideoEditSpec, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val player = remember(photo.uri) { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE } }
    var failed by remember { mutableStateOf(false) }
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener { override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed = true } }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        player.addListener(listener); owner.lifecycle.addObserver(observer)
        onDispose { player.removeListener(listener); owner.lifecycle.removeObserver(observer); player.release() }
    }
    LaunchedEffect(player, spec) {
        try {
            player.setVideoEffects(VideoExporter.spatialEffects(spec)); player.setPlaybackSpeed(spec.speed); player.volume = if (spec.mute) 0f else 1f
            player.setMediaItem(MediaItem.Builder().setUri(photo.uri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(spec.startMs).setEndPositionMs(spec.endMs).build()).build())
            player.prepare(); player.play()
        } catch (_: Exception) { failed = true }
    }
    if (failed) Text(tr("Video preview unavailable"), modifier)
    else AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = modifier)
}
