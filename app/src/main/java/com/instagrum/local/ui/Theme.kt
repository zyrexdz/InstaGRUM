package com.instagrum.local.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ActionBlue = Color(0xFF3897F0)
private val DarkScheme = darkColorScheme(
    primary = ActionBlue, onPrimary = Color.White,
    secondary = Color(0xFFF5F5F5), onSecondary = Color.Black,
    tertiary = Color(0xFFF05B77), background = Color.Black, surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF262626), onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFFA8A8A8), outline = Color(0xFF555555), outlineVariant = Color(0xFF262626),
)
private val LightScheme = lightColorScheme(
    primary = ActionBlue, onPrimary = Color.White, secondary = Color(0xFF262626),
    background = Color.White, surface = Color.White, surfaceVariant = Color(0xFFEFEFEF),
    onSurface = Color(0xFF151515), onSurfaceVariant = Color(0xFF737373), outlineVariant = Color(0xFFDBDBDB),
)

@Composable
fun InstaTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
