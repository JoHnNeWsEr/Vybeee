package com.vybeee.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(primary = Color(0xFFB8A7FF), secondary = Color(0xFF9AD9FF))
private val LightColors = lightColorScheme(primary = Color(0xFF6750A4), secondary = Color(0xFF3F6374))

@Composable
fun VybeeeTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
