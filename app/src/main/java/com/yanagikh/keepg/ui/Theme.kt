package com.yanagikh.keepg.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.yanagikh.keepg.data.ThemeMode

private val LightColors = lightColorScheme(primary = Color(0xFF6D28D9), secondary = Color(0xFF4F46E5), tertiary = Color(0xFF0F766E), surface = Color(0xFFFAFAFA), surfaceVariant = Color(0xFFF3F4F6))
private val DarkColors = darkColorScheme(primary = Color(0xFFC4B5FD), secondary = Color(0xFFA5B4FC), tertiary = Color(0xFF5EEAD4))

@Composable
fun KeepGTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, dynamicColors: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = if (dynamicColors && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
