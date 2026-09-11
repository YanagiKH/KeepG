package com.yanagikh.keepg.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.yanagikh.keepg.agent.AgentHost
import com.yanagikh.keepg.agent.AgentViewModel
import com.yanagikh.keepg.data.*
import com.yanagikh.keepg.advanced.AdvancedEditRequest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual Compose screens with synthetic, app-private fixtures; no user's library is changed. */
class WorkspaceUiTest {
    @get:Rule val compose = createComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val settings = GallerySettings(videoPreviewAutoPlay = false, animationsEnabled = false, language = AppLanguage.ENGLISH)
    private fun bitmap(): Bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this); canvas.drawColor(Color.rgb(215, 235, 247))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(246, 185, 83); canvas.drawCircle(440f, 135f, 65f, paint)
        paint.color = Color.rgb(90, 149, 127)
        canvas.drawOval(-80f, 240f, 420f, 730f, paint)
        paint.color = Color.rgb(39, 101, 104); canvas.drawOval(180f, 310f, 760f, 850f, paint)
        paint.color = Color.WHITE; paint.textSize = 38f; canvas.drawText("KeepG · Demo", 40f, 535f, paint)
    }
    private fun fixture(id: Long, bucket: Long): PhotoEntity {
        val file = File(app.cacheDir, "keepg_qa_$id.png")
        bitmap().let { b -> file.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle() }
        return PhotoEntity(id, android.net.Uri.fromFile(file).toString(), bucket, if (bucket == 10L) "Demo album" else "Other album", "demo_$id.png", "image/png", 0, 600, 600)
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(app.getExternalFilesDir(null), "qa-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    @Test fun albumLongPressSelectAllStaysScopedAndClearKeepsAlbum() {
        val photos = listOf(fixture(1, 10), fixture(2, 10), fixture(3, 20))
        val selected = mutableStateOf(emptySet<Long>())
        var shared = emptyList<Long>()
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
            AlbumsScreenV2(photos, emptyList(), emptyList(), emptySet(), emptyList(), emptyList(), emptySet(), selected.value, settings,
                onPhoto = { _, _ -> error("Selection must not open preview") },
                onToggleSelection = { selected.value = selected.value.toMutableSet().apply { if (!add(it)) remove(it) } },
                onGridColumns = {}, onCreateCollection = {}, onRenameCollection = { _, _ -> }, onClearCollection = {}, onDeleteCollection = {},
                onRemoveFromCollection = { _, _ -> }, onLockAlbum = { _, _ -> }, onUnlock = {},
                onSelectMedia = { selected.value = it.map(PhotoEntity::mediaId).toSet() }, onFavoriteMedia = {},
                onShareMedia = { shared = it.map(PhotoEntity::mediaId) }, onDeleteMedia = {}, onVaultMedia = {}, onRestoreTrash = {}, onDeleteTrash = {},
                allowProtection = true, onClearSelection = { selected.value = emptySet() }, onCollectionSelected = {}, onProtectSelected = {}, onAnalyzeSelected = {})
        } } }
        compose.onNodeWithText("Demo album").performScrollTo().performClick()
        compose.onNodeWithTag("media-1").performTouchInput { longClick() }
        compose.onNodeWithText("Selected: 1").assertExists()
        compose.onNodeWithTag("media-2").performClick()
        compose.onNodeWithText("Selected: 2").assertExists()
        compose.onNodeWithText("All").performClick()
        compose.runOnIdle { assertEquals(setOf(1L, 2L), selected.value) }
        compose.onNodeWithContentDescription("Share selected").performClick()
        compose.runOnIdle { assertEquals(listOf(1L, 2L), shared) }
        screenshot("album-selection")
        compose.onNodeWithContentDescription("Clear selection").performClick()
        compose.onNodeWithText("Demo album").assertExists()
        compose.onNodeWithTag("media-1").assertIsNotSelected()
        compose.onNodeWithTag("media-3").assertDoesNotExist()
    }
    @Test fun settingsCategorySearchAndTranslations() {
        val prefs = GalleryPreferences(app)
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalAppLanguage provides AppLanguage.CHINESE) {
            GallerySettingsScreen(12, 0, 0, true, false, settings, false,
                onVideoPreviewAutoPlay = {}, onPreviewSwipeNavigation = {}, onGridColumns = {}, onGridLayoutMode = {},
                onThumbnailScaleMode = {}, onPreviewScaleMode = {}, onShowMediaBadges = {}, onAnimationsEnabled = {},
                onCameraGridEnabled = {}, onCameraAudioEnabled = {}, onDeleteToTrash = {}, onHideSensitiveContent = {}, onLanguage = {},
                onDeletionPassword = { true }, onDebugEnabled = {}, onPermissions = {}, onExportLog = {}, onClearLog = {}, onShowLog = { "" },
                preferences = prefs, onClearThumbnails = {}, onClearImageIndex = {}, onOpenAgent = {})
        } } }
        compose.onNodeWithTag("settings-tab-editing").performClick()
        compose.onNodeWithText("預設匯出格式").assertExists()
        screenshot("settings-editing")
        compose.onNodeWithTag("settings-search").performTextInput("模型")
        compose.onNodeWithText("模型與技能").assertExists()
    }
    @Test fun imageCropGestureHasUndoAndProducesValidRequest() {
        val photo = fixture(4, 10); val source = bitmap()
        var result: AdvancedEditRequest? = null
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
            ProfessionalImageEditor(photo, {}, { result = it }, sourceOverride = source)
        } } }
        compose.onNodeWithTag("editor-tool-CROP").performClick()
        compose.onNodeWithTag("editing-canvas").performTouchInput { swipe(topLeft + androidx.compose.ui.geometry.Offset(3f, 3f), center, 400) }
        compose.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
        screenshot("image-editor")
        compose.onNodeWithText("Save changes").performClick()
        compose.runOnIdle { requireNotNull(result).validate(); assertTrue(result!!.cropLeft > 0f) }
    }
    @Test fun modelPresetsAvailableWithoutNetworkRequests() {
        val agent = AgentViewModel(app)
        agent.open(1)
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
            AgentHost(agent, settings, "", emptySet(), emptyMap(), {})
        } } }
        compose.onNodeWithText("Gemma 4 E2B · Light").assertExists()
        compose.onNodeWithText("Gemma 4 E4B · Standard").assertExists()
        screenshot("ai-models")
        compose.onNodeWithTag("agent-tab-0").performClick()
        compose.onNodeWithText("Install and select a model first").assertExists()
        screenshot("ai-chat")
        compose.runOnIdle { agent.invalidate() }
    }
}
