package com.yanagikh.keepg.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yanagikh.keepg.advanced.AdvancedEditRequest
import com.yanagikh.keepg.agent.AgentEntryButton
import com.yanagikh.keepg.data.PhotoEntity
import com.yanagikh.keepg.editor.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GifAnimationEditor(photo: PhotoEntity, onDismiss: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GifEditRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<GifEditRepository.Source?>(null) }
    var edit by remember { mutableStateOf(AdvancedEditRequest(photo.displayName.substringBeforeLast('.') + "_animation")) }
    var timing by remember { mutableStateOf(GifTiming(0, 1000)) }
    var edge by remember { mutableIntStateOf(480) }
    var scrub by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var appearance by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var working by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var issue by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(photo.uri) {
        try { source = repo.open(photo); source?.let { timing = GifTiming(0, minOf(it.duration, 30_000)); edit = edit.copy(viewportAspectRatio = it.aspect) } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { issue = "GIF could not be opened or exceeds safety limits" }
        catch (_: OutOfMemoryError) { issue = "Not enough memory to edit this GIF" }
    }
    LaunchedEffect(source, scrub) {
        delay(50)
        try { source?.let { frame = withContext(Dispatchers.Default) { it.frame(scrub.toInt(), 640) } } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { issue = "GIF could not be opened or exceeds safety limits" }
        catch (_: OutOfMemoryError) { issue = "Not enough memory to edit this GIF" }
    }
    fun dismiss() { job?.cancel(); onDismiss() }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(12.dp)) {
                Row { Text(tr("GIF animation"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); AgentEntryButton(); TextButton(::dismiss) { Text(tr("Close")) } }
                frame?.let { bitmap ->
                    Canvas(Modifier.fillMaxWidth().heightIn(max = 260.dp).aspectRatio(edit.viewportAspectRatio.coerceIn(.6f, 2f))) {
                        drawIntoCanvas { ImageRenderer.draw(it.nativeCanvas, bitmap, edit, size.width, size.height, edit.crop) }
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("Drag the timeline handles to trim. Appearance edits apply to every frame."))
                    source?.let { clip ->
                        RangeSlider(timing.startMs.toFloat()..timing.endMs.toFloat(), { range ->
                            if (range.endInclusive - range.start >= 50) { timing = timing.copy(startMs = range.start.toInt(), endMs = range.endInclusive.toInt()); scrub = range.start }
                        }, enabled = !working, valueRange = 0f..clip.duration.toFloat())
                        Text("${timing.startMs} — ${timing.endMs} ms")
                        EditorSlider("Preview time", scrub, 0f..(clip.duration - 1).toFloat(), { scrub = it })
                    }
                    Button({ appearance = true }, enabled = !working && frame != null) { Text(tr("Edit crop, color and layers")) }
                    EditorSlider("Playback speed", timing.speed, .25f..4f, { timing = timing.copy(speed = it) })
                    EditorSlider("Frames per second", timing.fps.toFloat(), 1f..24f, { timing = timing.copy(fps = it.toInt()) })
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(240, 480, 640).forEach { size -> FilterChip(edge == size, { edge = size }, { Text("$size px") }, Modifier.padding(end = 8.dp)) }
                    }
                    Row { Checkbox(timing.reverse, { timing = timing.copy(reverse = it) }, enabled = !working); Text(tr("Reverse frames")) }
                    Row { Checkbox(timing.loops == 0, { timing = timing.copy(loops = if (it) 0 else 1) }, enabled = !working); Text(tr("Loop forever")) }
                    if (timing.loops != 0) EditorSlider("Loop count", timing.loops.toFloat(), 1f..10f, { timing = timing.copy(loops = it.toInt()) })
                    OutlinedTextField(edit.outputName, { edit = edit.copy(outputName = it.take(100)) }, Modifier.fillMaxWidth(), label = { Text(tr("Output name")) }, enabled = !working, singleLine = true)
                    Text(tr("GIF export is limited to 30 seconds and 360 frames. Palette quantization reduces colors; transparency is flattened onto white."), style = MaterialTheme.typography.bodySmall)
                    issue?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
                }
                if (working) { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()); TextButton({ job?.cancel() }) { Text(tr("Cancel export")) } }
                else Button(enabled = source != null && edit.outputName.isNotBlank() && runCatching { timing.validate(source!!.duration) }.isSuccess,
                    onClick = {
                        val clip = source ?: return@Button
                        val chosenTiming = timing; val chosenEdit = edit; val chosenEdge = edge
                        working = true; issue = null; progress = 0f
                        job = scope.launch {
                            try { repo.export(photo, clip, chosenTiming, chosenEdit, chosenEdge) { progress = it }; onRefresh(); issue = "Created edited copy" }
                            catch (cancel: CancellationException) { issue = "Export cancelled"; throw cancel }
                            catch (_: Exception) { issue = "GIF export failed" }
                            catch (_: OutOfMemoryError) { issue = "Not enough memory to edit this GIF" }
                            finally { working = false }
                        }
                    }) { Text(tr("Save animated copy")) }
            }
        }
    }
    if (appearance && frame != null) ProfessionalImageEditor(photo, { appearance = false }, { edit = it; appearance = false }, sourceOverride = frame, initialRequest = edit)
}
