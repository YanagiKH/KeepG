package com.yanagikh.keepg

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yanagikh.keepg.data.GalleryPreferences
import com.yanagikh.keepg.data.KeepGDatabase
import com.yanagikh.keepg.data.LockEntity
import com.yanagikh.keepg.data.PhotoEntity
import com.yanagikh.keepg.security.VaultCipher
import com.yanagikh.keepg.widget.KeepGWidgetProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class KeepGInstrumentedTest {
    private lateinit var database: KeepGDatabase
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, KeepGDatabase::class.java).build()
    }

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

    @Test fun previewSwipeSettingPersists() {
        context.getSharedPreferences("keepg_gallery", Context.MODE_PRIVATE).edit().clear().commit()
        val first = GalleryPreferences(context)
        first.setPreviewSwipeNavigation(false)
        assertEquals(false, first.settings.value.previewSwipeNavigation)
        val reloaded = GalleryPreferences(context)
        assertEquals(false, reloaded.settings.value.previewSwipeNavigation)
    }

    @Test fun cameraPermissionAndWidgetProviderAreRegistered() {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(packageInfo.requestedPermissions.orEmpty().contains(Manifest.permission.CAMERA))
        val receiver = context.packageManager.getReceiverInfo(ComponentName(context, KeepGWidgetProvider::class.java), 0)
        assertEquals(KeepGWidgetProvider::class.java.name, receiver.name)
    }
}
