package com.yanagikh.keepg.agent

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class AgentPolicyTest {
    @Test fun repositoryAndPathsAreNotCommandsOrTraversal() {
        assertTrue(AgentPolicy.validRepository("litert-community/gemma-4-E2B-it-litert-lm"))
        listOf("../model", "user/../model", "https://host/model", "user/model?token=x", "user/model;curl").forEach { assertFalse(it, AgentPolicy.validRepository(it)) }
        listOf("../model.litertlm", "/model.litertlm", "x\\model.litertlm", "x/../../model.litertlm", "x/model.gguf").forEach { assertFalse(it, AgentPolicy.validModelPath(it)) }
        assertTrue(AgentPolicy.validModelPath("models/model.litertlm"))
    }
    @Test fun contentRangesMustMatchPinnedLengthAndOffset() {
        assertTrue(AgentPolicy.validContentRange("bytes 100-199/200", 100, 200))
        listOf(null, "bytes 0-99/200", "bytes 100-200/200", "bytes 100-199/201", "bytes */200").forEach { assertFalse(AgentPolicy.validContentRange(it, 100, 200)) }
    }
    @Test fun boundedCopiesDoNotWritePastTheLimit() {
        val output = ByteArrayOutputStream()
        assertEquals(10L, AgentPolicy.copyLimited(ByteArray(10).inputStream(), output, 10))
        assertEquals(10, output.size())
        assertThrows(IllegalArgumentException::class.java) { AgentPolicy.copyLimited(ByteArray(11).inputStream(), ByteArrayOutputStream(), 10) }
    }
    @Test fun skillNamesArePortableAndCannotEscapeStorage() {
        assertTrue(AgentPolicy.validSkillName("album-organizer"))
        listOf("../escape", "-name", "name-", "a--b", "Name", "").forEach { assertFalse(AgentPolicy.validSkillName(it)) }
    }
}
