package com.yanagikh.keepg.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test fun roundTripAcceptsCorrectPasswordAndRejectsWrongPassword() {
        val encoded = PasswordHasher.create("correct horse battery staple".toCharArray())
        assertTrue(PasswordHasher.verify("correct horse battery staple".toCharArray(), encoded.hash, encoded.salt))
        assertFalse(PasswordHasher.verify("incorrect password".toCharArray(), encoded.hash, encoded.salt))
    }
    @Test(expected = IllegalArgumentException::class) fun shortPasswordsAreRejected() { PasswordHasher.create("12345".toCharArray()) }
}
