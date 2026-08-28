package com.yanagikh.keepg

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yanagikh.keepg.data.GalleryPreferences
import com.yanagikh.keepg.data.CollectionEntity
import com.yanagikh.keepg.data.CollectionItemEntity
import com.yanagikh.keepg.data.FaceObservationEntity
import com.yanagikh.keepg.data.KeepGDatabase
import com.yanagikh.keepg.data.LockEntity
import com.yanagikh.keepg.data.MediaTextIndexEntity
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

    @Test fun completeScanPrunesOnlyOrphanedPhotoData() = runBlocking {
        val dao = database.dao()
        val old = PhotoEntity(2, "content://image/2", 7, "Camera", "old.jpg", "image/jpeg", 1, 100, 100, lastScannedAt = 1)
        dao.upsertPhotos(listOf(old))
        dao.upsertMediaTextIndex(MediaTextIndexEntity(2, "private searchable text"))
        dao.upsertLock(LockEntity("PHOTO", "2", "DEVICE"))
        dao.upsertLock(LockEntity("ALBUM", "7", "DEVICE"))
        dao.insertFaces(listOf(FaceObservationEntity(mediaId = 2, faceIndex = 0, embedding = byteArrayOf(1))))
        val collectionId = dao.insertCollection(CollectionEntity(name = "Test"))
        dao.addCollectionItem(CollectionItemEntity(collectionId, 2))

        dao.replaceScannedPhotos(emptyList(), scanStartedAt = 2, pruneMissing = true)

        assertTrue(dao.observePhotos().first().isEmpty())
        assertTrue(dao.observeMediaTextIndex().first().isEmpty())
        assertTrue(dao.observeFaces().first().isEmpty())
        assertTrue(dao.observeCollectionItems().first().isEmpty())
        assertEquals(listOf("ALBUM"), dao.observeLocks().first().map { it.targetType })
    }

    @Test fun incompleteScanNeverPrunesCachedLibrary() = runBlocking {
        val dao = database.dao()
        dao.upsertPhotos(listOf(PhotoEntity(3, "content://image/3", 8, "Saved", "cached.jpg", "image/jpeg", 1, 80, 80, lastScannedAt = 1)))
        dao.replaceScannedPhotos(emptyList(), scanStartedAt = 2, pruneMissing = false)
        assertEquals(1, dao.observePhotos().first().size)
    }

    @Test fun imageTextSearchHonorsAlbumScopeWithoutLoadingFullIndex() = runBlocking {
        val dao = database.dao()
        dao.upsertPhotos(
            listOf(
                PhotoEntity(10, "content://image/10", 1, "Receipts", "one.jpg", "image/jpeg", 1, 80, 80),
                PhotoEntity(11, "content://image/11", 2, "Notes", "two.jpg", "image/jpeg", 1, 80, 80),
            )
        )
        dao.upsertMediaTextIndex(MediaTextIndexEntity(10, "Invoice TOTAL 42"))
        dao.upsertMediaTextIndex(MediaTextIndexEntity(11, "Personal total reminder"))

        assertEquals(listOf(10L, 11L), dao.searchMediaText("total", null).sorted())
        assertEquals(listOf(10L), dao.searchMediaText("TOTAL", 1))
        assertEquals(2, dao.observeMediaTextIndexCount().first())
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
        assertTrue(packageInfo.requestedPermissions.orEmpty().contains(Manifest.permission.RECORD_AUDIO))
        val receiver = context.packageManager.getReceiverInfo(ComponentName(context, KeepGWidgetProvider::class.java), 0)
        assertEquals(KeepGWidgetProvider::class.java.name, receiver.name)
    }
}
