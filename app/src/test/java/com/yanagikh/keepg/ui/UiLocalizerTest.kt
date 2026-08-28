package com.yanagikh.keepg.ui

import com.yanagikh.keepg.data.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLocalizerTest {
    private val explicitLanguages = listOf(
        AppLanguage.ENGLISH,
        AppLanguage.CHINESE,
        AppLanguage.JAPANESE,
        AppLanguage.KOREAN,
    )

    @Test
    fun everyRegisteredKeyExistsInEverySupportedLanguage() {
        assertTrue(UiLocalizer.supportedKeys.isNotEmpty())
        explicitLanguages.forEach { language ->
            assertEquals(emptySet<String>(), UiLocalizer.missingKeys(language))
            assertEquals(UiLocalizer.supportedKeys, UiLocalizer.translationKeys(language))
        }
    }

    @Test
    fun everyRegisteredTranslationIsNonBlankAndPreservesFormatArguments() {
        explicitLanguages.forEach { language ->
            UiLocalizer.supportedKeys.forEach { key ->
                val translation = UiLocalizer.text(language, key)
                assertFalse("$language: $key", translation.isBlank())
                assertEquals("$language: $key", placeholderCount(key), placeholderCount(translation))
            }
        }
    }

    @Test
    fun unknownKeysRemainReadable() {
        val unknown = "Unregistered future label"
        explicitLanguages.forEach { language ->
            assertEquals(unknown, UiLocalizer.text(language, unknown))
        }
    }

    @Test
    fun runtimeOperationMessagesAreLocalized() {
        assertEquals("已索引 3／5 張圖片", UiLocalizer.message(AppLanguage.CHINESE, "Indexed 3/5 images"))
        assertEquals("スマート分析完了：7件の顔を処理しました", UiLocalizer.message(AppLanguage.JAPANESE, "Smart analysis complete: 7 faces processed"))
        assertEquals("대단히 특별한 오류", UiLocalizer.message(AppLanguage.KOREAN, "대단히 특별한 오류"))
    }

    private fun placeholderCount(value: String): Int = Regex("%s").findAll(value).count()
}
