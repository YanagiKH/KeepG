package com.yanagikh.keepg.agent

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Untrusted model names, repository responses and skill files never become executable paths. */
internal object AgentPolicy {
    const val MAX_MODEL_BYTES = 16L * 1024 * 1024 * 1024
    const val MAX_ATTACHMENT_BYTES = 16L * 1024 * 1024
    const val MAX_SKILL_BYTES = 64 * 1024
    const val MAX_SKILL_ARCHIVE_BYTES = 2 * 1024 * 1024
    const val MAX_METADATA_BYTES = 8 * 1024 * 1024
    private val repositoryPattern = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,95}/[A-Za-z0-9][A-Za-z0-9_.-]{0,95}")
    private val shaPattern = Regex("[a-fA-F0-9]{64}")
    private val revisionPattern = Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")

    fun validRepository(value: String) = repositoryPattern.matches(value) && value.split('/').none { it.contains("..") }
    fun safeRelativePath(value: String): Boolean = value.length in 1..240 && !value.startsWith('/') &&
        !value.contains('\\') && !value.contains(':') && !value.any { it.code < 32 } &&
        value.split('/').all { it.isNotBlank() && it != "." && it != ".." }
    fun validModelPath(value: String) = safeRelativePath(value) && value.endsWith(".litertlm", ignoreCase = true)
    fun validHash(value: String) = shaPattern.matches(value)
    fun validRevision(value: String) = revisionPattern.matches(value)
    fun validSkillName(value: String) = value.length in 1..64 &&
        Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(value)
    fun id(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).hex()
    fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

    fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        require(maxBytes >= 0)
        val buffer = ByteArray(64 * 1024)
        var count = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            require(count <= maxBytes - read) { "File exceeds the size limit" }
            output.write(buffer, 0, read)
            count += read
        }
        return count
    }

    /** A resume response must refer to exactly the pinned object, not a different revision. */
    fun validContentRange(header: String?, offset: Long, total: Long): Boolean {
        val match = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(header.orEmpty()) ?: return false
        val start = match.groupValues[1].toLongOrNull() ?: return false
        val end = match.groupValues[2].toLongOrNull() ?: return false
        val length = match.groupValues[3].toLongOrNull() ?: return false
        return start == offset && end >= start && end < total && length == total
    }
}

/** The model can propose only these operations. None execute without a fresh UI confirmation. */
internal enum class AgentActionType(val label: String, val mediaRequired: Boolean = false) {
    NAVIGATE("Open page"), SEARCH("Search"), SELECT("Select", true), FAVORITE("Favorite", true),
    SHARE("Share", true), DELETE("Delete", true), PROTECT("Protect", true), VAULT("Vault", true),
    ANALYZE("Analyze", true), OPEN("Open media", true), EDIT("Edit", true), RENAME("Rename", true),
    REPAIR("Repair", true), CREATE_COLLECTION("Create collection"), ADD_TO_COLLECTION("Add to collection", true),
    REMOVE_FROM_COLLECTION("Remove from collection", true), RENAME_COLLECTION("Rename collection"),
    CLEAR_COLLECTION("Clear collection"), DELETE_COLLECTION("Delete collection"),
    REFRESH("Refresh"), CAMERA("Camera"), SETTINGS("Settings"), INDEX_TEXT("Image text"), CLEAR_INDEX("Clear index");
}
