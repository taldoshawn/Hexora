/* SPDX-License-Identifier: GPL-3.0-or-later */
package dev.hexora.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8FF75),
    onPrimary = Color(0xFF222700),
    primaryContainer = Color(0xFF353D05),
    onPrimaryContainer = Color(0xFFF2FFAD),
    secondary = Color(0xFFB7C5D0),
    background = Color(0xFF0D0F10),
    onBackground = Color(0xFFE4E7E9),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE4E7E9),
    surfaceVariant = Color(0xFF1C2023),
    onSurfaceVariant = Color(0xFFADB5BB),
    outline = Color(0xFF42494E),
    outlineVariant = Color(0xFF2A2F32),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF566500),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8FF75),
    onPrimaryContainer = Color(0xFF191E00),
    secondary = Color(0xFF4D5D67),
    background = Color(0xFFF8FAFA),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFEDF0F1),
    onSurfaceVariant = Color(0xFF535B60),
    outline = Color(0xFFB8BFC3),
    outlineVariant = Color(0xFFDDE1E3),
    error = Color(0xFFBA1A1A),
)

@Composable
fun HexoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = HexoraTypography,
        content = content,
    )
}
