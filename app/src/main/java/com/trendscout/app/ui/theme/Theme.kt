package com.trendscout.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF0B0B0E)
    val Surface = Color(0xFF16171C)
    val SurfaceHigh = Color(0xFF1F2128)
    val Outline = Color(0xFF2C2F38)
    val Text = Color(0xFFF2F3F5)
    val TextMuted = Color(0xFF9A9FAB)
    val Cyan = Color(0xFF25F4EE)
    val Pink = Color(0xFFFE2C55)
    val Green = Color(0xFF3DDC84)
    val Amber = Color(0xFFFFB547)
    val Red = Color(0xFFFF5C5C)
}

private val scheme = darkColorScheme(
    primary = Palette.Cyan,
    onPrimary = Color(0xFF00201F),
    primaryContainer = Color(0xFF0E3B3A),
    onPrimaryContainer = Palette.Cyan,
    secondary = Palette.Pink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3D1220),
    onSecondaryContainer = Color(0xFFFFD9DF),
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Bg,
    onSurface = Palette.Text,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextMuted,
    surfaceContainerLowest = Palette.Bg,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF282B33),
    outline = Palette.Outline,
    outlineVariant = Color(0xFF23252C),
    error = Palette.Red,
)

private val typography = Typography().let { t ->
    t.copy(
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

@Composable
fun TrendScoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
