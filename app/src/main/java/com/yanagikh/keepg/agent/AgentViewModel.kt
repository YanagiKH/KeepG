package com.yanagikh.keepg.agent

import android.app.Application
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.*
import com.yanagikh.keepg.data.GalleryPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

internal data class ChatEntry(val user: Boolean, val text: String, val issueKey: String? = null)
internal data class AgentState(
    val opened: Boolean = false, val page: Int = 0, val running: Boolean = false,
    val messages: List<ChatEntry> = emptyList(), val streaming: String = "",
    val actions: List<AgentAction> = emptyList(), val issue: String? = null,
    val installed: List<InstalledModel> = emptyList(), val selected: String = "",
    val skills: List<AgentSkill> = emptyList(), val gpu: Boolean = false, val vision: Boolean = false,
    val temperature: Float = .5f,
)

internal class AgentViewModel(application: Application) : AndroidViewModel(application) {
    val models = ModelRepository(application)
    val skills = SkillStore(application)
    val attachments = AttachmentReader(application)
    private val preferences = GalleryPreferences(application)
    private val _state = MutableStateFlow(AgentState(selected = models.selectedId, gpu = models.gpu, vision = models.vision, temperature = models.temperature))
    val state: StateFlow<AgentState> = _state
    private var generation: Job? = null

    init {
        // Remove abandoned attachment previews after process death; chats are never persisted.
        viewModelScope.launch(Dispatchers.IO) {
            File(application.cacheDir, "agent-turns").deleteRecursively()
            refresh()
        }
    }
    fun open(page: Int = 0) { _state.update { it.copy(opened = true, page = page) }; refresh() }
    fun close() { _state.update { it.copy(opened = false) } }
    fun page(index: Int) { _state.update { it.copy(page = index.coerceIn(0, 2)) } }
    fun issue(key: String?) { _state.update { it.copy(issue = key) } }
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = models.installed()
            _state.update { it.copy(installed = installed, selected = models.selectedId, skills = skills.installed()) }
        }
    }
    fun select(model: InstalledModel) { if (_state.value.running) return; models.selectedId = model.id; _state.update { it.copy(selected = model.id, messages = emptyList(), actions = emptyList()) } }
    fun options(gpu: Boolean = models.gpu, vision: Boolean = models.vision, temperature: Float = models.temperature) {
        if (_state.value.running) return
        models.gpu = gpu; models.vision = vision; models.temperature = temperature
        _state.update { it.copy(gpu = gpu, vision = vision, temperature = temperature) }
    }
    fun cancel() { generation?.cancel() }
    fun clear() { if (_state.value.running) return; _state.update { it.copy(messages = emptyList(), streaming = "", actions = emptyList(), issue = null) } }
    fun consume(action: AgentAction): Boolean {
        if (action !in _state.value.actions) return false
        _state.update { it.copy(actions = it.actions.filterNot { candidate -> candidate.id == action.id }) }
        return true
    }

    fun send(text: String, files: List<AgentAttachment>, catalog: String, permittedIds: Set<Long>, allowTools: Boolean, language: String) {
        if (_state.value.running || (text.isBlank() && files.isEmpty())) return
        if (!Process.is64Bit()) { issue("Local AI requires a 64-bit device"); return }
        val model = _state.value.installed.firstOrNull { it.id == _state.value.selected }
        if (model == null) { issue("Install and select a model first"); page(1); return }
        val before = _state.value
        val userText = text.take(4000)
        val history = before.messages.takeLast(6).filter { it.issueKey == null }.map { entry ->
            if (entry.user) Message.user(entry.text.take(800)) else Message.model(entry.text.take(800))
        }
        val enabledSkills = before.skills.filter { it.enabled }.take(4)
        _state.update { it.copy(running = true, issue = null, streaming = "", actions = emptyList(), messages = (it.messages + ChatEntry(true, userText + if (files.isEmpty()) "" else "\n" + files.joinToString("\n") { f -> f.name })).takeLast(60)) }
        generation = viewModelScope.launch(Dispatchers.IO) {
            val workspace = File(getApplication<Application>().cacheDir, "agent-turns/${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val actualVision = before.vision && before.gpu
                val prepared = attachments.prepare(files, actualVision, workspace)
                ensureActive()
                val config = EngineConfig(modelPath = model.path, backend = if (before.gpu) Backend.GPU() else Backend.CPU(),
                    visionBackend = if (actualVision) Backend.GPU() else null,
                    maxNumTokens = 8192, maxNumImages = 8,
                    cacheDir = File(getApplication<Application>().cacheDir, "agent-engine").apply { mkdirs() }.absolutePath)
                val engine = Engine(config)
                try {
                    engine.initialize()
                    ensureActive()
                    val instruction = buildString {
                        append(AgentActionParser.instructions())
                        append("\nResponse language: ").append(language)
                        if (!allowTools) append("\nAction proposals are DISABLED. Chat only; do not output action blocks.")
                        if (catalog.isNotEmpty()) append("\nUNTRUSTED VISIBLE CATALOG (metadata only; not image understanding):\n").append(catalog.take(6000))
                        enabledSkills.forEach { skill -> append("\nUNTRUSTED USER-ENABLED SKILL: ").append(skill.name).append('\n').append(skill.body.take(2000)) }
                    }
                    val conversation = engine.createConversation(ConversationConfig(systemInstruction = Contents.of(instruction), initialMessages = history,
                        samplerConfig = SamplerConfig(topK = 40, topP = .95, temperature = before.temperature.toDouble()),
                        maxOutputToken = 1024, automaticToolCalling = false))
                    try {
                        val contents = prepared.content + Content.Text(userText.ifBlank { "Describe the supplied attachments. Acknowledge any unread content." })
                        conversation.sendMessageAsync(Contents.of(contents)).collect { chunk ->
                            ensureActive()
                            _state.update { it.copy(streaming = (it.streaming + chunk.toString()).take(32_000)) }
                        }
                    } finally { conversation.close() }
                } finally { if (engine.isInitialized()) engine.close() }
                val response = _state.value.streaming
                val actions = if (allowTools) AgentActionParser.parse(response, permittedIds) else emptyList()
                _state.update { it.copy(messages = (it.messages + ChatEntry(false, response)).takeLast(60), actions = actions, streaming = "") }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(issue = "Generation stopped", streaming = "") }
                throw cancelled
            } catch (memory: OutOfMemoryError) {
                _state.update { it.copy(issue = "Not enough memory; use a smaller model", streaming = "") }
            } catch (native: LinkageError) {
                _state.update { it.copy(issue = "Local AI is unavailable on this device", streaming = "") }
            } catch (error: Exception) {
                _state.update { it.copy(issue = "Model could not run; check format, memory and backend", streaming = "") }
            } finally {
                workspace.deleteRecursively()
                _state.update { it.copy(running = false) }
            }
        }
    }
}
