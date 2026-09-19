package com.instagrum.local.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ActionBlue = Color(0xFF0095F6)
val IgShell = Color(0xFF0C1014)
val IgElevated = Color(0xFF1A1E23)
val IgButton = Color(0xFF262A2E)
val IgSecondaryText = Color(0xFFA8A8A8)
val IgDivider = Color(0xFF262626)
val IgSeenRing = Color(0xFF4A4A4A)

private val DarkScheme = darkColorScheme(
    primary = ActionBlue, onPrimary = Color.White,
    secondary = Color(0xFFF5F5F5), onSecondary = Color.Black,
    tertiary = Color(0xFFF05B77), background = IgShell, surface = IgElevated,
    surfaceVariant = IgButton, onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = IgSecondaryText, outline = Color(0xFF555555), outlineVariant = IgDivider,
)
private val LightScheme = lightColorScheme(
    primary = ActionBlue, onPrimary = Color.White, secondary = Color(0xFF262626),
    background = Color.White, surface = Color.White, surfaceVariant = Color(0xFFEFEFEF),
    onSurface = Color(0xFF151515), onSurfaceVariant = Color(0xFF737373), outlineVariant = Color(0xFFDBDBDB),
)

private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerate = CubicBezierEasing(0.1f, 0.7f, 0.1f, 1f)

object Motion {
    fun <T> screen() = tween<T>(300, easing = Standard)
    fun <T> tab() = tween<T>(200, easing = Standard)
    fun <T> enter() = tween<T>(350, easing = EmphasizedDecelerate)
    fun <T> quick() = tween<T>(150, easing = Standard)
    fun <T> pop() = spring<T>(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> press() = spring<T>(dampingRatio = 0.75f, stiffness = 1400f)
    fun <T> settle() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 380f)
}

@Composable
fun InstaTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}
