package com.xjb.wenzhen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealPrimaryDark,
    secondary = TealSecondary,
    onSecondary = Color.White,
    secondaryContainer = BlueSoft,
    onSecondaryContainer = Color(0xFF004E59),
    tertiary = TealTertiary,
    onTertiary = Color(0xFF00382F),
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = TealSoft,
    onSurfaceVariant = InkMuted,
    outline = Line,
    outlineVariant = Color(0xFFE9F1EF),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFA7F2E7),
    secondary = DarkTealSecondary,
    onSecondary = Color(0xFF00363E),
    background = DarkCanvas,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = Color(0xFF243A36),
    onSurfaceVariant = Color(0xFFB9CBC7),
    outline = Color(0xFF526B67),
)

private val WenzhenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun WenzhenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WenzhenTypography,
        shapes = WenzhenShapes,
        content = content,
    )
}
