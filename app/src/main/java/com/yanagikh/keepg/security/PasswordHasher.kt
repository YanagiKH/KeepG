package com.yanagikh.keepg.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ITERATIONS = 210_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16
    data class Encoded(val hash: String, val salt: String)

    fun create(password: CharArray): Encoded {
        require(password.size >= 6) { "Password must contain at least 6 characters" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val hash = derive(password, salt)
        return Encoded(Base64.getEncoder().encodeToString(hash), Base64.getEncoder().encodeToString(salt))
    }

    fun verify(password: CharArray, expectedHash: String, encodedSalt: String): Boolean {
        val salt = Base64.getDecoder().decode(encodedSalt)
        val expected = Base64.getDecoder().decode(expectedHash)
        val actual = derive(password, salt)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword(); password.fill('\u0000') }
    }
}
