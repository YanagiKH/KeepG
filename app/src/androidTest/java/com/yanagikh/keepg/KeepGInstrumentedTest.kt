package com.yanagikh.keepg

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yanagikh.keepg.data.KeepGDatabase
import com.yanagikh.keepg.data.LockEntity
import com.yanagikh.keepg.data.PhotoEntity
import com.yanagikh.keepg.security.VaultCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class KeepGInstrumentedTest {
    private lateinit var database: KeepGDatabase
    @Before fun setUp() { database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KeepGDatabase::class.java).build() }
    @After fun tearDown() { database.close() }
    @Test fun roomPersistsLibraryAndLockState() = runBlocking {
        val dao = database.dao()
        dao.upsertPhotos(listOf(PhotoEntity(1, "content://image/1", 7, "Camera", "one.jpg", "image/jpeg", 1234, 100, 100)))
        dao.upsertLock(LockEntity("PHOTO", "1", "DEVICE"))
        assertEquals(1, dao.observePhotos().first().size)
        assertEquals("DEVICE", dao.observeLocks().first().single().authType)
    }
    @Test fun androidKeystoreVaultCipherRoundTripsBytes() {
        val source = ByteArray(8192) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream(); val cipher = VaultCipher()
        cipher.encrypt(ByteArrayInputStream(source), encrypted)
        val decrypted = ByteArrayOutputStream(); cipher.decrypt(ByteArrayInputStream(encrypted.toByteArray()), decrypted)
        assertArrayEquals(source, decrypted.toByteArray())
    }
}
