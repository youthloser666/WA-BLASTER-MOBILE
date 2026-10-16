package com.example.wablaster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimary,
    secondary = GreenAccent,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceElevatedDark,
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimaryDark,
    secondary = GreenPrimary,
    background = androidx.compose.ui.graphics.Color(0xFFF0F2F5),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEFEAE2),
    error = ErrorRed
)

private val ColorPrimaryTextOnGreen = androidx.compose.ui.graphics.Color(0xFFFFFFFF)

@Composable
fun WABlasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
