package com.yanagikh.keepg.agent

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import com.yanagikh.keepg.data.GallerySettings
import com.yanagikh.keepg.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal val LocalAgentLauncher = compositionLocalOf<(() -> Unit)?> { null }

@Composable
internal fun AgentEntryButton() {
    LocalAgentLauncher.current?.let { launch ->
        IconButton(launch, Modifier.testTag("agent-open")) { Icon(Icons.Default.AutoAwesome, tr("AI assistant")) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgentHost(
    agent: AgentViewModel,
    settings: GallerySettings,
    catalog: String,
    permittedIds: Set<Long>,
    mediaLabels: Map<Long, String>,
    onAction: (AgentAction) -> Unit,
) {
    val state by agent.state.collectAsStateWithLifecycle()
    if (!state.opened) return
    val language = LocalAppLanguage.current
    var input by rememberSaveable { mutableStateOf("") }
    var includeCatalog by rememberSaveable { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<AgentAttachment>>(emptyList()) }
    var review by remember { mutableStateOf<AgentAction?>(null) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            try {
                val additions = withContext(Dispatchers.IO) { uris.take((4 - files.size).coerceAtLeast(0)).map(agent.attachments::describe) }
                files = (files + additions).distinctBy { it.uri }.take(4)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { agent.issue("Unable to read attachment") }
        }
    }
    Dialog(onDismissRequest = agent::close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("agent-host"), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                TopAppBar(title = { Text(tr("AI assistant")) }, navigationIcon = { IconButton(agent::close) { Icon(Icons.Default.Close, tr("Close")) } },
                    actions = { TextButton(agent::clear, enabled = !state.running) { Text(tr("Clear chat")) } })
                TabRow(selectedTabIndex = state.page) {
                    listOf("Chat", "Models", "Skills").forEachIndexed { index, key ->
                        Tab(state.page == index, { agent.page(index) }, text = { Text(tr(key)) }, modifier = Modifier.testTag("agent-tab-$index"))
                    }
                }
                state.issue?.let { issue ->
                    Text(tr(issue), Modifier.fillMaxWidth().padding(12.dp).testTag("agent-issue"), color = MaterialTheme.colorScheme.error)
                }
                when (state.page) {
                    0 -> {
                        val listState = rememberLazyListState()
                        LaunchedEffect(state.messages.size, state.streaming.length) {
                            val count = listState.layoutInfo.totalItemsCount
                            if (count > 0) listState.animateScrollToItem(count - 1)
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("agent-chat"), state = listState,
                            contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item { Text(tr("Local chat. Your media is not uploaded. Model answers can be wrong."), style = MaterialTheme.typography.bodySmall) }
                            item { Text(state.installed.firstOrNull { it.id == state.selected }?.name ?: tr("Install and select a model first"), style = MaterialTheme.typography.labelMedium) }
                            items(state.messages) { message ->
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(tr(if (message.user) "You" else "AI assistant"), fontWeight = FontWeight.Bold)
                                        SelectionContainer { Text(message.issueKey?.let { tr(it) } ?: message.text) }
                                    }
                                }
                            }
                            if (state.running) item {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                SelectionContainer { Text(state.streaming.ifBlank { tr("Loading model and generating…") }) }
                            }
                            items(state.actions, key = { it.id }) { action ->
                                OutlinedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(tr(action.type.label), fontWeight = FontWeight.Bold)
                                        Text(action.argument.ifBlank { trf("%s items", action.mediaIds.size) })
                                        TextButton({ review = action }, enabled = settings.agentAllowTools && !state.running) { Text(tr("Review action")) }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(includeCatalog, { includeCatalog = it }, enabled = !state.running)
                            Text(tr("Share visible media metadata for this chat turn"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                        if (files.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                                files.forEach { file -> InputChip(true, { if (!state.running) files = files.filterNot { it.uri == file.uri } }, { Text(file.name.take(30)) }, trailingIcon = { Icon(Icons.Default.Close, tr("Remove attachment")) }) }
                            }
                            Text(tr("Text is excerpted. Vision reads reduced images, two PDF pages or two video frames. Other files are metadata-only."), Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall)
                        }
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                            IconButton({ picker.launch(arrayOf("*/*")) }, enabled = !state.running && files.size < 4) { Icon(Icons.Default.AttachFile, tr("Attach files")) }
                            OutlinedTextField(input, { input = it.take(4000) }, Modifier.weight(1f).testTag("agent-input"), label = { Text(tr("Message")) }, maxLines = 4, enabled = !state.running)
                            if (state.running) IconButton(agent::cancel) { Icon(Icons.Default.Stop, tr("Stop generation")) }
                            else IconButton({
                                agent.send(input, files, if (includeCatalog) catalog else "", if (includeCatalog) permittedIds else emptySet(), settings.agentAllowTools, language.name)
                                // Keep drafts when a missing model redirects to the model manager.
                                if (state.installed.any { it.id == state.selected }) { input = ""; files = emptyList() }
                            }, enabled = input.isNotBlank() || files.isNotEmpty()) { Icon(Icons.Default.Send, tr("Send")) }
                        }
                    }
                    1 -> ModelManager(agent, state, settings)
                    2 -> SkillManager(agent, state)
                }
            }
        }
    }
    review?.let { action ->
        AlertDialog(onDismissRequest = { review = null }, title = { Text(tr("Review action")) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr(action.type.label), fontWeight = FontWeight.Bold)
                    if (action.argument.isNotBlank()) Text(action.argument)
                    if (action.value.isNotBlank()) Text(action.value)
                    action.collectionId?.let { Text(trf("Collection ID: %s", it)) }
                    action.mediaIds.forEach { Text(mediaLabels[it] ?: tr("Unavailable media")) }
                    Text(tr("Review every target. Deletion, sharing and protection still require the existing confirmation steps."))
                }
            }, confirmButton = {
                Button({
                    review = null
                    if (settings.agentAllowTools && agent.consume(action)) { agent.close(); onAction(action) }
                }, enabled = settings.agentAllowTools) { Text(tr("Continue to action")) }
            }, dismissButton = { TextButton({ agent.consume(action); review = null }) { Text(tr("Reject")) } })
    }
}

@Composable
private fun ModelManager(agent: AgentViewModel, state: AgentState, settings: GallerySettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("gemma-4 litert") }
    var repositories by remember { mutableStateOf<List<String>>(emptyList()) }
    var available by remember { mutableStateOf<List<ModelFile>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ModelFile?>(null) }
    var imported by remember { mutableStateOf<AgentAttachment?>(null) }
    var remove by remember { mutableStateOf<InstalledModel?>(null) }
    var tokenDialog by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var tokenSaved by remember { mutableStateOf(agent.models.hasToken()) }
    val manager = remember(context) { WorkManager.getInstance(context) }
    val works by manager.getWorkInfosByTagFlow("keepg-model").collectAsStateWithLifecycle(initialValue = emptyList())
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { runCatching { withContext(Dispatchers.IO) { agent.attachments.describe(it) } }.onSuccess { imported = it }.onFailure { agent.issue("Unable to read attachment") } } }
    }
    LaunchedEffect(works.map { it.state }) { agent.refresh() }
    fun task(work: suspend () -> Unit) {
        if (busy) return
        busy = true; agent.issue(null)
        scope.launch {
            try { work() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: AgentFailure) { agent.issue(failure.key) }
            catch (error: Exception) { agent.issue("Unable to install model") }
            finally { busy = false }
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("agent-models"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(tr("Install trusted .litertlm files only. Safetensors, GGUF and hardware-specific models are not interchangeable. Downloads may be several GB.")) }
        items(agent.models.presets) { preset ->
            FilledTonalButton({ task {
                val choices = withContext(Dispatchers.IO) { agent.models.files(preset.repository) }
                pending = choices.firstOrNull { it.fileName == preset.file } ?: throw AgentFailure("Compatible model file not found")
            } }, Modifier.fillMaxWidth(), enabled = !busy && !state.running) { Text(tr(preset.label)) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ picker.launch(arrayOf("*/*")) }, Modifier.weight(1f), enabled = !busy && !state.running) { Text(tr("Import model")) }
                TextButton({ tokenDialog = true }) { Text(tr(if (tokenSaved) "Replace access token" else "Access token")) }
            }
        }
        item {
            Text(tr("Inference options"), fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(state.gpu, { agent.options(gpu = it, vision = if (it) state.vision else false) }, enabled = !state.running); Text(tr("Use GPU"), Modifier.padding(start = 8.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(state.vision, { agent.options(vision = it) }, enabled = !state.running && state.gpu); Text(tr("Enable vision for compatible models"), Modifier.padding(start = 8.dp)) }
            Text(tr("CPU text mode is the default. Vision needs a compatible model and GPU; disable it when loading fails."), style = MaterialTheme.typography.bodySmall)
            Text(trf("Temperature: %s", String.format(Locale.ROOT, "%.2f", state.temperature)))
            Slider(state.temperature, { agent.options(temperature = it) }, valueRange = 0f..1.5f, enabled = !state.running)
        }
        items(state.installed, key = { it.id }) { model ->
            ListItem(headlineContent = { Text(model.name) }, supportingContent = { Text(formatBytes(model.size)) },
                leadingContent = { RadioButton(state.selected == model.id, { agent.select(model) }, enabled = !state.running) },
                trailingContent = { IconButton({ remove = model }, enabled = !state.running) { Icon(Icons.Default.Delete, tr("Delete model")) } })
        }
        items(works.filter { !it.state.isFinished || it.outputData.getString("error") != null }, key = { it.id.toString() }) { info ->
            Column {
                Text(tr(if (info.progress.getBoolean("verifying", false)) "Verifying model checksum" else if (info.state.isFinished) "Download interrupted; retry to resume" else "Downloading model"))
                val total = info.progress.getLong("total", 0)
                val bytes = info.progress.getLong("bytes", 0)
                if (total > 0) { LinearProgressIndicator(progress = { (bytes.toDouble() / total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()); Text("${formatBytes(bytes)} / ${formatBytes(total)}") }
                else Text(tr("Waiting for an allowed network or worker slot"), style = MaterialTheme.typography.bodySmall)
                info.outputData.getString("error")?.let { Text(tr(it), color = MaterialTheme.colorScheme.error) }
                if (!info.state.isFinished) TextButton({ manager.cancelWorkById(info.id) }) { Text(tr("Cancel")) }
            }
        }
        item {
            OutlinedTextField(query, { query = it.take(160) }, Modifier.fillMaxWidth(), label = { Text(tr("Search Hugging Face")) }, singleLine = true)
            Button({ task { repositories = withContext(Dispatchers.IO) { agent.models.search(query) }; available = emptyList() } }, enabled = !busy && query.isNotBlank()) { Text(tr("Search")) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        items(repositories) { repository ->
            TextButton({ task { available = withContext(Dispatchers.IO) { agent.models.files(repository) }; if (available.isEmpty()) agent.issue("Compatible model file not found") } }, Modifier.fillMaxWidth(), enabled = !busy) { Text(repository) }
        }
        items(available, key = { it.id }) { model ->
            OutlinedButton({ pending = model }, Modifier.fillMaxWidth(), enabled = !state.running && !busy) {
                Column { Text(model.fileName); Text(formatBytes(model.size), style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
    pending?.let { model ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(tr("Install model")) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(model.repository); Text(model.fileName); Text(formatBytes(model.size))
                Text(tr("Review the repository license and trust the publisher before installing. This is a native model file, not an Agent Skill."))
                Text("${tr("Revision")}: ${model.revision}", style = MaterialTheme.typography.bodySmall)
                Text("SHA-256: ${model.sha256}", style = MaterialTheme.typography.bodySmall)
                Text(tr(if (settings.modelWifiOnly) "Download on unmetered networks only" else "Download may use mobile data"))
                TextButton({ runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://huggingface.co/${model.repository}"))) } }) { Text(tr("Open model card")) }
            }
        }, confirmButton = { Button({ pending = null; if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS); agent.models.download(model, settings.modelWifiOnly) }) { Text(tr("Download")) } }, dismissButton = { TextButton({ pending = null }) { Text(tr("Cancel")) } })
    }
    imported?.let { file ->
        AlertDialog(onDismissRequest = { imported = null }, title = { Text(tr("Import model")) }, text = { Text(file.name + "\n" + tr("Only import a trusted .litertlm model. The engine validates compatibility when it loads.")) },
            confirmButton = { Button({ imported = null; task { withContext(Dispatchers.IO) { agent.models.importModel(file.uri, file.name) }; agent.refresh() } }, enabled = file.name.endsWith(".litertlm", true)) { Text(tr("Import")) } },
            dismissButton = { TextButton({ imported = null }) { Text(tr("Cancel")) } })
    }
    remove?.let { model ->
        AlertDialog(onDismissRequest = { remove = null }, title = { Text(tr("Delete model")) }, text = { Text(model.name + "\n" + formatBytes(model.size)) },
            confirmButton = { Button({ remove = null; task { withContext(Dispatchers.IO) { agent.models.remove(model) }; agent.refresh() } }) { Text(tr("Delete")) } }, dismissButton = { TextButton({ remove = null }) { Text(tr("Cancel")) } })
    }
    if (tokenDialog) {
        AlertDialog(onDismissRequest = { tokenDialog = false; token = "" }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(tr("Hugging Face read token")) }, text = { Column { Text(tr("Optional for gated models. Stored with Android Keystore encryption. Never sent to download redirects.")); OutlinedTextField(token, { token = it.take(512) }, visualTransformation = PasswordVisualTransformation(), singleLine = true) } },
            confirmButton = { TextButton({ task { withContext(Dispatchers.IO) { agent.models.saveToken(token) }; tokenSaved = agent.models.hasToken(); token = ""; tokenDialog = false } }) { Text(tr("Save")) } },
            dismissButton = { TextButton({ task { withContext(Dispatchers.IO) { agent.models.saveToken("") }; tokenSaved = false; token = ""; tokenDialog = false } }) { Text(tr("Remove token")) } })
    }
}

@Composable
private fun SkillManager(agent: AgentViewModel, state: AgentState) {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<AgentSkill?>(null) }
    var remove by remember { mutableStateOf<AgentSkill?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch {
            try { pending = withContext(Dispatchers.IO) { val file = agent.attachments.describe(it); agent.skills.read(it, file.name.endsWith(".zip", true)) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { agent.issue("Invalid or unsafe skill file") }
        } }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("agent-skills"), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(tr("Import SKILL.md or a ZIP containing one skill. Scripts and bundled executables are never run. Review instructions before enabling.")) }
        item { Button({ picker.launch(arrayOf("*/*")) }, enabled = !state.running) { Text(tr("Import skill")) } }
        items(state.skills, key = { it.name }) { skill ->
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(skill.name, fontWeight = FontWeight.Bold); Text(skill.description)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(skill.enabled, { enabled -> scope.launch { withContext(Dispatchers.IO) { agent.skills.save(skill.copy(enabled = enabled)) }; agent.refresh() } }, enabled = !state.running)
                        Text(tr("Enabled"), Modifier.weight(1f).padding(8.dp))
                        TextButton({ pending = skill }) { Text(tr("Review")) }
                        IconButton({ remove = skill }, enabled = !state.running) { Icon(Icons.Default.Delete, tr("Delete")) }
                    }
                }
            }
        }
    }
    pending?.let { skill ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text(skill.name) }, text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) { Text(skill.description, fontWeight = FontWeight.Bold); SelectionContainer { Text(skill.body) }; Text(tr("Installing does not grant permissions. Enable this skill separately; every action still needs approval.")) }
        }, confirmButton = { Button({ scope.launch { withContext(Dispatchers.IO) { agent.skills.save(skill.copy(enabled = false)) }; agent.refresh(); pending = null } }) { Text(tr("Install disabled")) } }, dismissButton = { TextButton({ pending = null }) { Text(tr("Cancel")) } })
    }
    remove?.let { skill ->
        AlertDialog(onDismissRequest = { remove = null }, title = { Text(tr("Delete skill")) }, text = { Text(skill.name) },
            confirmButton = { Button({ scope.launch { withContext(Dispatchers.IO) { agent.skills.delete(skill) }; agent.refresh(); remove = null } }) { Text(tr("Delete")) } }, dismissButton = { TextButton({ remove = null }) { Text(tr("Cancel")) } })
    }
}

private fun formatBytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MiB", bytes / 1048576.0)
