package com.yanagikh.keepg.agent

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SkillParserTest {
    private val markdown = "---\nname: organize-albums\ndescription: Help organize selected media.\n---\nPropose a collection. Never delete originals."
    @Test fun importsDisabledInstructionsNotExecutableCode() {
        val result = SkillParser.parse(markdown)
        assertEquals("organize-albums", result.name)
        assertFalse(result.enabled)
        assertTrue(result.body.contains("Never delete"))
    }
    @Test fun yamlObjectsAndDuplicateFieldsAreRejected() {
        assertThrows(Exception::class.java) { SkillParser.parse(markdown.replace("name: organize-albums", "name: !!java.net.URL [https://example.invalid]")) }
        assertThrows(Exception::class.java) { SkillParser.parse(markdown.replace("name: organize-albums", "name: organize-albums\nname: other-name")) }
    }
    @Test fun traversalAndMultipleSkillsAreRejected() {
        fun archive(vararg names: String): ByteArray = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip -> names.forEach { zip.putNextEntry(ZipEntry(it)); zip.write(markdown.toByteArray()); zip.closeEntry() } }
        }.toByteArray()
        assertThrows(Exception::class.java) { SkillParser.read(archive("../SKILL.md").inputStream(), true) }
        assertThrows(Exception::class.java) { SkillParser.read(archive("one/SKILL.md", "two/SKILL.md").inputStream(), true) }
        assertEquals("organize-albums", SkillParser.read(archive("organize-albums/SKILL.md").inputStream(), true).name)
    }
    @Test fun oversizedSkillsAreRejected() {
        assertThrows(Exception::class.java) { SkillParser.read(ByteArray(AgentPolicy.MAX_SKILL_BYTES + 1).inputStream(), false) }
    }
}
