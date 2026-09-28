package com.vybeee.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VybeeeBackground = Color(0xFF14101E)
private val VybeeeSurface = Color(0xFF1E1629)
private val VybeeePrimary = Color(0xFFFF6F86)
private val VybeeeSecondary = Color(0xFFF34FA3)
private val VybeeeOnSurface = Color(0xFFF8F5FC)
private val VybeeeMuted = Color(0xFFA29AB8)

private val DarkColors = darkColorScheme(
    primary = VybeeePrimary,
    secondary = VybeeeSecondary,
    background = VybeeeBackground,
    surface = VybeeeSurface,
    onBackground = VybeeeOnSurface,
    onSurface = VybeeeOnSurface,
    onSurfaceVariant = VybeeeMuted
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB83263),
    secondary = Color(0xFF9A2F69),
    background = Color(0xFFFFF9FF),
    surface = Color(0xFFFFF9FF),
    onSurfaceVariant = Color(0xFF665F6E)
)

@Composable
fun VybeeeTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
