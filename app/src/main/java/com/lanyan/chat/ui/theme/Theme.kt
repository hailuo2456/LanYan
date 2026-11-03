package com.lanyan.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.lanyan.chat.data.model.ThemeMode

val BrandBlue = Color(0xFF4D6BFE)
private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8ECFF),
    onPrimaryContainer = Color(0xFF15265F),
    secondary = Color(0xFF5D6272),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF0F1217),
    surface = Color.White,
    onSurface = Color(0xFF0F1217),
    surfaceVariant = Color(0xFFF0F2F6),
    onSurfaceVariant = Color(0xFF565D6B),
    outline = Color(0xFFD9DDE7),
    outlineVariant = Color(0xFFE9ECF2),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DAEFF),
    onPrimary = Color(0xFF10266F),
    primaryContainer = Color(0xFF2E438F),
    onPrimaryContainer = Color(0xFFE4E8FF),
    background = Color(0xFF101216),
    onBackground = Color(0xFFF7F8FC),
    surface = Color(0xFF17191F),
    onSurface = Color(0xFFF7F8FC),
    surfaceVariant = Color(0xFF24272E),
    onSurfaceVariant = Color(0xFFC6CBD6),
    outline = Color(0xFF484D59),
    outlineVariant = Color(0xFF2C3038),
)

@Composable
fun LanYanTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
