package com.ydl.gui.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF63E6BE)

private val Dark = darkColorScheme(
    primary = Green, onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF1F3B33), onPrimaryContainer = Green,
    secondary = Color(0xFF8FD6C0), tertiary = Color(0xFFFFB4A8),
    background = Color(0xFF0F0F13), onBackground = Color(0xFFE6E6EA),
    surface = Color(0xFF16161C), onSurface = Color(0xFFE6E6EA),
    surfaceVariant = Color(0xFF26262E), onSurfaceVariant = Color(0xFFA9A9B3),
    surfaceContainer = Color(0xFF1C1C23), surfaceContainerHigh = Color(0xFF22222A),
    outline = Color(0xFF4A4A55), error = Color(0xFFFF8A80),
)

private val Light = lightColorScheme(
    primary = Color(0xFF00806A), onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F2E1), onPrimaryContainer = Color(0xFF00201A),
    background = Color(0xFFF7F8F8), surface = Color.White,
    surfaceVariant = Color(0xFFE8ECEB), surfaceContainer = Color(0xFFF0F3F2), surfaceContainerHigh = Color(0xFFE9EEEC),
)

@Composable
fun YdlTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
