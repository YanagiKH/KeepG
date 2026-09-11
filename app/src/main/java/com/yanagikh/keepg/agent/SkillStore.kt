package com.yanagikh.keepg.agent

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

internal data class AgentSkill(val name: String, val description: String, val body: String, val enabled: Boolean = false)

internal object SkillParser {
    fun parse(markdown: String): AgentSkill {
        require(markdown.toByteArray(Charsets.UTF_8).size <= AgentPolicy.MAX_SKILL_BYTES)
        val normalized = markdown.removePrefix("\uFEFF").replace("\r\n", "\n")
        require(normalized.startsWith("---\n")) { "Invalid skill file" }
        val end = normalized.indexOf("\n---\n", 4)
        require(end in 4..8192) { "Invalid skill file" }
        val loader = LoaderOptions().apply {
            maxAliasesForCollections = 0; codePointLimit = AgentPolicy.MAX_SKILL_BYTES
            nestingDepthLimit = 8; isAllowDuplicateKeys = false
        }
        val fields = Yaml(SafeConstructor(loader)).load<Any>(normalized.substring(4, end)) as? Map<*, *> ?: error("Invalid skill file")
        val name = fields["name"] as? String ?: error("Invalid skill file")
        val description = fields["description"] as? String ?: error("Invalid skill file")
        require(AgentPolicy.validSkillName(name) && description.isNotBlank() && description.length <= 1024)
        val body = normalized.substring(end + 5).trim()
        require(body.isNotBlank())
        return AgentSkill(name, description, body)
    }

    fun read(input: InputStream, zipped: Boolean): AgentSkill {
        if (!zipped) return parse(String(bounded(input, AgentPolicy.MAX_SKILL_BYTES), Charsets.UTF_8))
        val archive = bounded(input, AgentPolicy.MAX_SKILL_ARCHIVE_BYTES)
        var skill: AgentSkill? = null
        var expanded = 0
        var count = 0
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++count <= 100 && AgentPolicy.safeRelativePath(entry.name.trimEnd('/'))) { "Unsafe skill archive" }
                if (!entry.isDirectory) {
                    val bytes = bounded(zip, AgentPolicy.MAX_SKILL_ARCHIVE_BYTES - expanded)
                    expanded += bytes.size
                    if (entry.name.substringAfterLast('/') == "SKILL.md") {
                        require(skill == null) { "Choose an archive with one skill" }
                        skill = parse(String(bytes, Charsets.UTF_8))
                        val parent = entry.name.substringBeforeLast('/', "")
                        if (parent.isNotEmpty()) require(parent.substringAfterLast('/') == skill!!.name)
                    }
                }
                zip.closeEntry()
            }
        }
        return skill ?: error("Invalid skill file")
    }
    private fun bounded(input: InputStream, limit: Int): ByteArray = ByteArrayOutputStream().also {
        AgentPolicy.copyLimited(input, it, limit.toLong())
    }.toByteArray()
}

internal class SkillStore(context: Context) {
    private val context = context.applicationContext
    private val root = File(this.context.filesDir, "agent/skills").apply { mkdirs() }
    fun installed(): List<AgentSkill> = root.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull { file ->
        runCatching {
            val json = JSONObject(file.readText())
            val name = json.getString("name")
            require(AgentPolicy.validSkillName(name) && file.nameWithoutExtension == name)
            AgentSkill(name, json.getString("description"), json.getString("body"), json.optBoolean("enabled"))
        }.getOrNull()
    }.sortedBy { it.name }
    fun read(uri: Uri, zipped: Boolean): AgentSkill = context.contentResolver.openInputStream(uri)?.use { SkillParser.read(it, zipped) }
        ?: throw AgentFailure("Unable to read attachment")
    fun save(skill: AgentSkill) {
        require(AgentPolicy.validSkillName(skill.name))
        require(skill.body.toByteArray().size <= AgentPolicy.MAX_SKILL_BYTES)
        ModelRepository.writeAtomic(File(root, "${skill.name}.json"), JSONObject().put("name", skill.name)
            .put("description", skill.description).put("body", skill.body).put("enabled", skill.enabled).toString())
    }
    fun delete(skill: AgentSkill) { require(AgentPolicy.validSkillName(skill.name)); File(root, "${skill.name}.json").delete() }
}
