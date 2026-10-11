package com.routinecheck.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 노션 시안의 민트 · 그린 · 블루 팔레트 */
object Brand {
    val Mint = Color(0xFF14A38B)
    val MintLight = Color(0xFFCFF1E9)
    val Green = Color(0xFF34C38F)
    val Blue = Color(0xFF2F80ED)
    val BlueLight = Color(0xFFDCE9FD)
    val Badge = Color(0xFFEF4444)
    val Scrim = Color(0xFF0B1015)
}

private val LightColors = lightColorScheme(
    primary = Brand.Mint,
    onPrimary = Color.White,
    primaryContainer = Brand.MintLight,
    onPrimaryContainer = Color(0xFF00382F),
    secondary = Brand.Blue,
    onSecondary = Color.White,
    secondaryContainer = Brand.BlueLight,
    onSecondaryContainer = Color(0xFF0B2A57),
    tertiary = Brand.Green,
    onTertiary = Color.White,
    background = Color(0xFFF4FAF8),
    onBackground = Color(0xFF16201D),
    surface = Color.White,
    onSurface = Color(0xFF16201D),
    surfaceVariant = Color(0xFFE6EFEC),
    onSurfaceVariant = Color(0xFF52615C),
    outline = Color(0xFFB7C6C1),
    error = Color(0xFFD93B3B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FD7BE),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0E5146),
    onPrimaryContainer = Color(0xFFCFF1E9),
    secondary = Color(0xFF8DB9FF),
    onSecondary = Color(0xFF0B2A57),
    secondaryContainer = Color(0xFF1E3C66),
    onSecondaryContainer = Color(0xFFDCE9FD),
    tertiary = Color(0xFF6EDDB0),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDDE5E2),
    surface = Color(0xFF161D1B),
    onSurface = Color(0xFFDDE5E2),
    surfaceVariant = Color(0xFF26302D),
    onSurfaceVariant = Color(0xFFA9B8B3),
    outline = Color(0xFF4B5955),
    error = Color(0xFFFF8A80)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun RoutineCheckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
