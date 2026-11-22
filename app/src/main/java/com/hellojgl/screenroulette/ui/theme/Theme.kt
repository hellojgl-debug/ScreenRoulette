package com.hellojgl.screenroulette.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD6B66A),
    onPrimary = Color(0xFF172719),
    primaryContainer = Color(0xFF4B3C20),
    onPrimaryContainer = Color(0xFFFFE6A3),
    secondary = Color(0xFF91C9A9),
    onSecondary = Color(0xFF10291F),
    background = Color(0xFF10291F),
    onBackground = Color(0xFFF4F1E8),
    surface = Color(0xFF193629),
    onSurface = Color(0xFFF4F1E8),
    surfaceVariant = Color(0xFF294737),
    onSurfaceVariant = Color(0xFFD8E5DA),
    outline = Color(0xFFA8BEAE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF176344),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EBDD),
    onPrimaryContainer = Color(0xFF103D2A),
    secondary = Color(0xFF365D48),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE9DE),
    onSecondaryContainer = Color(0xFF193C29),
    background = Color(0xFFF7F8F4),
    onBackground = Color(0xFF17291F),
    surface = Color.White,
    onSurface = Color(0xFF17291F),
    surfaceVariant = Color(0xFFE6ECE6),
    onSurfaceVariant = Color(0xFF354A3C),
    outline = Color(0xFF64776A),
    error = Color(0xFFB3261E),
    onError = Color.White
)

@Composable
fun ScreenRouletteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) {
            DarkColorScheme
        } else {
            LightColorScheme
        },
        typography = Typography,
        content = content
    )
}
