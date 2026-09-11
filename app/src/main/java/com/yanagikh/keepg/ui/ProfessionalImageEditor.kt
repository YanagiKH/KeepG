package com.yanagikh.keepg.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yanagikh.keepg.KeepGApplication
import com.yanagikh.keepg.advanced.*
import com.yanagikh.keepg.agent.AgentEntryButton
import com.yanagikh.keepg.data.ImageExportFormat
import com.yanagikh.keepg.data.PhotoEntity
import com.yanagikh.keepg.editor.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

private enum class ImageTool(val label: String) { MOVE("Move image"), CROP("Crop"), LAYERS("Layers") }

/** Explicit modes prevent a layer/crop gesture from also moving the underlying image. */
@Composable
internal fun ProfessionalImageEditor(photo: PhotoEntity, onDismiss: () -> Unit, onApply: (AdvancedEditRequest) -> Unit, sourceOverride: Bitmap? = null, initialRequest: AdvancedEditRequest? = null) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as KeepGApplication).container }
    val settings by container.preferences.settings.collectAsState()
    val initial = remember(photo.mediaId) {
        initialRequest ?: AdvancedEditRequest(outputName = photo.displayName.substringBeforeLast('.', photo.displayName) + "_edit",
            viewportAspectRatio = (photo.width.toFloat() / photo.height.coerceAtLeast(1)).coerceIn(.2f, 5f),
            exportFormat = settings.exportFormat, exportQuality = settings.exportQuality)
    }
    var history by remember(photo.mediaId) { mutableStateOf(EditHistory(initial)) }
    val request = history.value
    var outputName by remember(photo.mediaId) { mutableStateOf(initial.outputName) }
    var tool by remember { mutableStateOf(ImageTool.MOVE) }
    var selectedLayer by remember { mutableIntStateOf(-1) }
    var grid by remember { mutableStateOf(settings.editorGrid) }
    var snap by remember { mutableStateOf(false) }
    var cropAspect by remember { mutableStateOf<Float?>(null) }
    var source by remember(photo.mediaId) { mutableStateOf<Bitmap?>(null) }
    var sourceIssue by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var aspectInitialized by remember(photo.mediaId) { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val latestLayer by rememberUpdatedState(selectedLayer)
    val latestSnap by rememberUpdatedState(snap)
    val latestAspect by rememberUpdatedState(cropAspect)
    fun change(value: AdvancedEditRequest) { history = history.change(value) }
    fun begin() { history = history.begin() }
    fun preview(value: AdvancedEditRequest) { history = history.preview(value) }
    fun finish() { history = history.finish() }
    fun coordinate(value: Float) = if (latestSnap) round(value * 20) / 20 else value

    LaunchedEffect(photo.uri, request.backgroundRemoval, request.backgroundStrength, sourceOverride) {
        loading = true; sourceIssue = false
        delay(180)
        try {
            val bitmap = sourceOverride ?: container.advanced.previewSource(photo, request.backgroundRemoval, request.backgroundStrength)
            if (bitmap == null) { sourceIssue = true; return@LaunchedEffect }
            source = bitmap
            if (!aspectInitialized) {
                if (history.past.isEmpty() && history.beforeGesture == null) {
                    history = EditHistory(history.value.copy(viewportAspectRatio = (bitmap.width.toFloat() / bitmap.height).coerceIn(.2f, 5f)))
                }
                aspectInitialized = true
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { sourceIssue = true }
        finally { loading = false }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onDismiss) { Icon(Icons.Default.Close, tr("Close")) }
                    Text(tr("Image editor"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    AgentEntryButton()
                    IconButton({ history = history.undo(); cropAspect = null }, enabled = history.past.isNotEmpty()) { Icon(Icons.Default.Undo, tr("Undo")) }
                    IconButton({ history = history.redo(); cropAspect = null }, enabled = history.future.isNotEmpty()) { Icon(Icons.Default.Redo, tr("Redo")) }
                    TextButton({ onApply(request.copy(outputName = outputName.trim()).validate()) }, enabled = source != null && !sourceIssue && !loading && outputName.isNotBlank()) { Text(tr(if (sourceOverride != null) "Save changes" else "Save copy")) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageTool.entries.forEach { mode -> FilterChip(tool == mode, { tool = mode }, { Text(tr(mode.label)) }, modifier = Modifier.testTag("editor-tool-${mode.name}")) }
                }
                if (photo.mimeType == "image/gif" && sourceOverride == null) Text(tr("This editor saves one still frame. Use GIF animation to preserve motion."), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
                if (sourceIssue) Text(tr("Image preview could not be prepared"), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val landscape = maxWidth > 650.dp
                    val previewPanel: @Composable (Modifier) -> Unit = { panel ->
                        BoxWithConstraints(panel.padding(8.dp), contentAlignment = Alignment.Center) {
                            val aspect = request.viewportAspectRatio
                            val fitted = if (maxHeight.value <= 0f || maxWidth.value / maxHeight.value < aspect)
                                Modifier.fillMaxWidth().aspectRatio(aspect) else Modifier.fillMaxHeight().aspectRatio(aspect)
                            val description = tr("Editing canvas")
                            val accent = MaterialTheme.colorScheme.primary
                            Canvas(fitted.clipToBounds().testTag("editing-canvas").semantics { contentDescription = description }
                                .pointerInput(tool) {
                                    if (tool == ImageTool.CROP) {
                                        var handle = CropHandle.MOVE
                                        detectDragGestures(onDragStart = { point ->
                                            begin()
                                            handle = EditGeometry.hit(history.value.crop, point.x / size.width, point.y / size.height, 30.dp.toPx() / size.width, 30.dp.toPx() / size.height)
                                        }, onDragEnd = { finish() }, onDragCancel = { finish() }) { change, delta ->
                                            change.consume()
                                            val current = history.value
                                            val bounds = EditGeometry.drag(current.crop, handle, delta.x / size.width, delta.y / size.height, latestAspect?.div(current.viewportAspectRatio))
                                            preview(current.withCrop(bounds))
                                        }
                                    } else {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false)
                                            begin()
                                            do {
                                                val event = awaitPointerEvent()
                                                val pan = event.calculatePan()
                                                val zoom = event.calculateZoom()
                                                val rotation = event.calculateRotation()
                                                val current = history.value
                                                if (tool == ImageTool.MOVE) {
                                                    preview(current.copy(offsetX = coordinate((current.offsetX + pan.x / size.width).coerceIn(-2f, 2f)),
                                                        offsetY = coordinate((current.offsetY + pan.y / size.height).coerceIn(-2f, 2f)),
                                                        scale = (current.scale * zoom).coerceIn(.05f, 20f), rotation = (current.rotation + rotation) % 360f))
                                                } else if (latestLayer in current.textLayers.indices) {
                                                    val layer = current.textLayers[latestLayer]
                                                    preview(current.copy(textLayers = current.textLayers.toMutableList().also {
                                                        it[latestLayer] = layer.copy(x = coordinate((layer.x + pan.x / size.width).coerceIn(-1f, 2f)),
                                                            y = coordinate((layer.y + pan.y / size.height).coerceIn(-1f, 2f)), scale = (layer.scale * zoom).coerceIn(.2f, 5f), rotation = (layer.rotation + rotation) % 360f)
                                                    }))
                                                }
                                                event.changes.filter { it.positionChanged() }.forEach { it.consume() }
                                            } while (event.changes.any { it.pressed })
                                            finish()
                                        }
                                    }
                                }) {
                                val tile = 16.dp.toPx()
                                for (y in 0..(size.height / tile).toInt()) for (x in 0..(size.width / tile).toInt()) {
                                    drawRect(if ((x + y) % 2 == 0) Color(0xFFE0E0E0) else Color(0xFFB8B8B8), Offset(x * tile, y * tile), Size(tile, tile))
                                }
                                source?.let { bitmap -> drawIntoCanvas { ImageRenderer.draw(it.nativeCanvas, bitmap, request, size.width, size.height) } }
                                val crop = request.crop
                                val left = crop.left * size.width; val top = crop.top * size.height
                                val right = crop.right * size.width; val bottom = crop.bottom * size.height
                                drawRect(Color.Black.copy(alpha = .48f), Offset.Zero, Size(size.width, top))
                                drawRect(Color.Black.copy(alpha = .48f), Offset(0f, bottom), Size(size.width, size.height - bottom))
                                drawRect(Color.Black.copy(alpha = .48f), Offset(0f, top), Size(left, bottom - top))
                                drawRect(Color.Black.copy(alpha = .48f), Offset(right, top), Size(size.width - right, bottom - top))
                                if (grid) for (i in 1..2) {
                                    drawLine(Color.White.copy(alpha = .5f), Offset(left + (right - left) * i / 3, top), Offset(left + (right - left) * i / 3, bottom))
                                    drawLine(Color.White.copy(alpha = .5f), Offset(left, top + (bottom - top) * i / 3), Offset(right, top + (bottom - top) * i / 3))
                                }
                                drawRect(accent, Offset(left, top), Size(right - left, bottom - top), style = Stroke(2.dp.toPx()))
                                if (tool == ImageTool.CROP) listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom)).forEach {
                                    drawCircle(accent, 7.dp.toPx(), it)
                                }
                                if (tool == ImageTool.LAYERS) request.textLayers.getOrNull(selectedLayer)?.let {
                                    drawCircle(accent, 9.dp.toPx(), Offset(it.x * size.width, it.y * size.height), style = Stroke(3.dp.toPx()))
                                }
                            }
                        }
                    }
                    val controls: @Composable (Modifier) -> Unit = { panel ->
                        LazyColumn(panel.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                            item { Text(tr(when (tool) { ImageTool.MOVE -> "Drag to move; pinch to zoom and rotate."; ImageTool.CROP -> "Drag a corner to resize; drag inside to move the crop."; ImageTool.LAYERS -> "Select a layer below, then drag or pinch on the canvas." }), style = MaterialTheme.typography.bodySmall) }
                            item { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(grid, { grid = it }); Text(tr("Composition grid")); Checkbox(snap, { snap = it }); Text(tr("Snap to grid")) } }
                            if (tool == ImageTool.MOVE) {
                                item {
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton({ change(request.copy(rotation = (request.rotation + 90) % 360)) }) { Text(tr("Rotate")) }
                                        FilterChip(request.flipX, { change(request.copy(flipX = !request.flipX)) }, { Text(tr("Flip horizontal")) })
                                        FilterChip(request.flipY, { change(request.copy(flipY = !request.flipY)) }, { Text(tr("Flip vertical")) })
                                        TextButton({ change(request.copy(scale = 1f, offsetX = 0f, offsetY = 0f, rotation = 0f, flipX = false, flipY = false)) }) { Text(tr("Reset view")) }
                                    }
                                }
                                item { EditorSlider("Scale", request.scale.coerceIn(.05f, 5f), .05f..5f, { begin(); preview(request.copy(scale = it)) }, ::finish) }
                                item { EditorSlider("Rotation", request.rotation, -360f..360f, { begin(); preview(request.copy(rotation = it)) }, ::finish) }
                            }
                            if (tool == ImageTool.CROP) {
                                item {
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("Free" to null, "Original" to request.viewportAspectRatio, "Square" to 1f, "4:3" to 4f / 3, "3:4" to 3f / 4, "16:9" to 16f / 9, "9:16" to 9f / 16).forEach { (label, ratio) ->
                                            FilterChip(cropAspect == ratio, { cropAspect = ratio; if (ratio != null) change(request.withCrop(EditGeometry.preset(ratio, request.viewportAspectRatio))) }, { Text(tr(label)) })
                                        }
                                    }
                                }
                                item { EditorSlider("Crop left", request.cropLeft, 0f..request.cropRight - .02f, { begin(); cropAspect = null; preview(request.copy(cropLeft = it)) }, ::finish) }
                                item { EditorSlider("Crop top", request.cropTop, 0f..request.cropBottom - .02f, { begin(); cropAspect = null; preview(request.copy(cropTop = it)) }, ::finish) }
                                item { EditorSlider("Crop right", request.cropRight, request.cropLeft + .02f..1f, { begin(); cropAspect = null; preview(request.copy(cropRight = it)) }, ::finish) }
                                item { EditorSlider("Crop bottom", request.cropBottom, request.cropTop + .02f..1f, { begin(); cropAspect = null; preview(request.copy(cropBottom = it)) }, ::finish) }
                                item { TextButton({ cropAspect = null; change(request.withCrop(CropBounds())) }) { Text(tr("Reset crop")) } }
                            }
                            if (tool == ImageTool.LAYERS) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(text, { text = it.take(120) }, Modifier.weight(1f), label = { Text(tr("Text layer")) }, singleLine = true)
                                        IconButton({ if (text.isNotBlank() && request.textLayers.size < 30) { change(request.copy(textLayers = request.textLayers + TextLayerSpec(text.trim(), .15f, .3f))); selectedLayer = request.textLayers.size; text = "" } }, enabled = request.textLayers.size < 30) { Icon(Icons.Default.Add, tr("Add text layer")) }
                                    }
                                }
                                request.textLayers.forEachIndexed { index, layer ->
                                    item(key = "layer-$index") {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selectedLayer == index, { selectedLayer = index })
                                            Text(layer.text, Modifier.weight(1f), maxLines = 1)
                                            IconButton({ change(request.copy(textLayers = request.textLayers.filterIndexed { i, _ -> i != index })); selectedLayer = -1 }) { Icon(Icons.Default.Delete, tr("Delete")) }
                                        }
                                    }
                                }
                                request.textLayers.getOrNull(selectedLayer)?.let { layer ->
                                    fun updateLayer(next: TextLayerSpec, gesture: Boolean = false) {
                                        val nextRequest = request.copy(textLayers = request.textLayers.toMutableList().also { it[selectedLayer] = next })
                                        if (gesture) { begin(); preview(nextRequest) } else change(nextRequest)
                                    }
                                    item { OutlinedTextField(layer.text, { updateLayer(layer.copy(text = it.take(120))) }, Modifier.fillMaxWidth(), label = { Text(tr("Edit layer text")) }, singleLine = true) }
                                    item {
                                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(layer.bold, { updateLayer(layer.copy(bold = !layer.bold)) }, { Text(tr("Bold")) })
                                            FilterChip(layer.shadow, { updateLayer(layer.copy(shadow = !layer.shadow)) }, { Text(tr("Shadow")) })
                                            listOf("White" to -1, "Black" to 0xFF000000.toInt(), "Yellow" to 0xFFFFD54F.toInt(), "Red" to 0xFFEF5350.toInt(), "Blue" to 0xFF42A5F5.toInt()).forEach { (label, argb) -> FilterChip(layer.colorArgb == argb, { updateLayer(layer.copy(colorArgb = argb)) }, { Text(tr(label)) }) }
                                        }
                                    }
                                    item { EditorSlider("Text size", layer.scale, .2f..5f, { updateLayer(layer.copy(scale = it), true) }, ::finish) }
                                    item { EditorSlider("Rotation", layer.rotation, -360f..360f, { updateLayer(layer.copy(rotation = it), true) }, ::finish) }
                                    item {
                                        Row {
                                            TextButton({ if (selectedLayer > 0) { val list = request.textLayers.toMutableList(); list.add(selectedLayer - 1, list.removeAt(selectedLayer)); change(request.copy(textLayers = list)); selectedLayer-- } }) { Text(tr("Move layer backward")) }
                                            TextButton({ if (selectedLayer < request.textLayers.lastIndex) { val list = request.textLayers.toMutableList(); list.add(selectedLayer + 1, list.removeAt(selectedLayer)); change(request.copy(textLayers = list)); selectedLayer++ } }) { Text(tr("Move layer forward")) }
                                        }
                                    }
                                }
                            }
                            item { HorizontalDivider(); Text(tr("Color adjustments"), style = MaterialTheme.typography.titleSmall) }
                            item { EditorSlider("Brightness", request.brightness, -1f..1f, { begin(); preview(request.copy(brightness = it)) }, ::finish) }
                            item { EditorSlider("Contrast", request.contrast, .25f..2.5f, { begin(); preview(request.copy(contrast = it)) }, ::finish) }
                            item { EditorSlider("Saturation", request.saturation, 0f..2f, { begin(); preview(request.copy(saturation = it)) }, ::finish) }
                            if (sourceOverride == null) item {
                                Text(tr("Background"), style = MaterialTheme.typography.titleSmall)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(BackgroundRemovalMode.NONE to "Keep background", BackgroundRemovalMode.AUTO to "Auto remove", BackgroundRemovalMode.MANUAL to "Manual remove").forEach { (mode, label) -> FilterChip(request.backgroundRemoval == mode, { change(request.copy(backgroundRemoval = mode)) }, { Text(tr(label)) }) }
                            }
                            }
                            if (sourceOverride == null && request.backgroundRemoval == BackgroundRemovalMode.MANUAL) item { EditorSlider("Background threshold", request.backgroundStrength, .05f.. .95f, { begin(); preview(request.copy(backgroundStrength = it)) }, ::finish) }
                            item { HorizontalDivider(); OutlinedTextField(outputName, { outputName = it.take(100) }, Modifier.fillMaxWidth(), label = { Text(tr("Output name")) }, singleLine = true) }
                            if (sourceOverride == null) item {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ImageExportFormat.entries.forEach { format -> FilterChip(request.exportFormat == format, { change(request.copy(exportFormat = format)) }, { Text(format.name) }) }
                                }
                                Text(tr("PNG preserves transparency. JPEG uses a white background; its quality slider affects file size."), style = MaterialTheme.typography.bodySmall)
                            }
                            if (sourceOverride == null && request.exportFormat != ImageExportFormat.PNG) item { EditorSlider("Export quality", request.exportQuality.toFloat(), 40f..100f, { begin(); preview(request.copy(exportQuality = it.toInt())) }, ::finish) }
                            if (sourceOverride == null) item {
                                Text(tr("Maximum output edge"))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1024, 2048, 4096).forEach { edge -> FilterChip(request.maxEdge == edge, { change(request.copy(maxEdge = edge)) }, { Text("$edge px") }) } }
                            }
                            item { OutlinedButton({ selectedLayer = -1; cropAspect = null; change(initial.copy(viewportAspectRatio = request.viewportAspectRatio)) }, Modifier.fillMaxWidth()) { Text(tr("Reset all edits")) } }
                        }
                    }
                    if (landscape) Row(Modifier.fillMaxSize()) { previewPanel(Modifier.weight(.6f).fillMaxHeight()); controls(Modifier.weight(.4f).fillMaxHeight()) }
                    else Column(Modifier.fillMaxSize()) { previewPanel(Modifier.weight(.52f).fillMaxWidth()); controls(Modifier.weight(.48f).fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
internal fun EditorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit, onFinish: () -> Unit = {}) {
    val translated = tr(label)
    Row(Modifier.fillMaxWidth()) { Text(translated, Modifier.weight(1f)); Text(String.format(Locale.ROOT, "%.2f", value), style = MaterialTheme.typography.labelMedium) }
    if (range.endInclusive - range.start > .0001f) Slider(value.coerceIn(range.start, range.endInclusive), onValue, valueRange = range, onValueChangeFinished = onFinish, modifier = Modifier.semantics { contentDescription = translated })
}
