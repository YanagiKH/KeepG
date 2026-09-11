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
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(150)
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(100) // Allow the Android window compositor to present the asserted frame.
        val image = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(app.getExternalFilesDir(null), "qa-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        // UiAutomation executes argv, not a shell expression: do not join with &&.
        // Copy evidence before UTP uninstalls the app and removes externalFilesDir.
        val destination = "/sdcard/Download/keepg-qa/${app.packageName}"
        shell("mkdir -p $destination")
        shell("cp ${directory.absolutePath}/$name.png $destination/$name.png")
        check(shell("ls $destination/$name.png").trim() == "$destination/$name.png") { "Screenshot evidence was not preserved" }
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
        compose.runOnIdle { requireNotNull(result).validate(); assertTrue(requireNotNull(result).cropLeft > 0f) }
    }
    @Test fun gifTimelineAndAppearanceEditorLoadWithRealFrames() {
        val file = File(app.cacheDir, "keepg_qa_animation.gif")
        file.outputStream().use { stream -> com.yanagikh.keepg.editor.GifEncoder(stream, 128, 96, 0).use { encoder ->
            repeat(12) { frame -> encoder.frame(IntArray(128 * 96) { pixel ->
                if (pixel % 128 < 20 + frame * 7) Color.rgb(39, 101, 104) else Color.rgb(215, 235, 247)
            }, 10) }
        } }
        val photo = PhotoEntity(90, android.net.Uri.fromFile(file).toString(), 10, "Demo album", "animation.gif", "image/gif", 0, 128, 96)
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
            GifAnimationEditor(photo, {}, {})
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Save animated copy").fetchSemanticsNodes().any {
            !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Edit crop, color and layers").fetchSemanticsNodes().any {
            !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        } }
        compose.onNodeWithText("Save animated copy").assertIsEnabled()
        screenshot("gif-editor")
        compose.onNodeWithText("Edit crop, color and layers").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("editing-canvas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("editing-canvas").assertExists()
        compose.onNodeWithText("Save changes").performClick()
        compose.onNodeWithText("GIF animation").assertExists()
    }
    @Test fun videoCropGestureSupportsUndoAndLoadedExport() {
        val file = File(app.cacheDir, "keepg_qa_video.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("editor-sample.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val photo = PhotoEntity(91, android.net.Uri.fromFile(file).toString(), 10, "Demo album", "video.mp4", "video/mp4", 0, 160, 120, durationMs = 2000)
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalAppLanguage provides AppLanguage.ENGLISH) {
            ProfessionalVideoEditor(photo, {}, {})
        } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("video-crop").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("video-crop").performTouchInput {
            // The 4:3 fixture is letterboxed in the wider canvas. Drag its visible
            // corner, not the empty left margin (moving a full crop is a no-op).
            val frameWidth = minOf(width.toFloat(), height * 4f / 3f)
            val frameHeight = frameWidth * 3f / 4f
            val corner = androidx.compose.ui.geometry.Offset((width - frameWidth) / 2f + 3f, (height - frameHeight) / 2f + 3f)
            swipe(corner, center, 400)
        }
        compose.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
        screenshot("video-editor")
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
        compose.onNodeWithTag("agent-input").assertIsDisplayed()
        compose.onNodeWithContentDescription("Attach files").assertIsDisplayed()
        screenshot("ai-chat")
        compose.runOnIdle { agent.invalidate() }
    }
}
