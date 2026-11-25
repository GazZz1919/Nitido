package com.nitido.optimizador.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** Colores de acento por tipo de recurso (almacenamiento, RAM, caché…). */
@Immutable
data class AccentColors(
    val storage: Color,
    val memory: Color,
    val cache: Color,
    val battery: Color,
    val good: Color,
    val danger: Color,
    val info: Color,
)

private val DarkAccents = AccentColors(
    storage = Color(0xFF2EE6A8),
    memory = Color(0xFF9D8CFF),
    cache = Color(0xFFFFB547),
    battery = Color(0xFF4FB3FF),
    good = Color(0xFF3DDC97),
    danger = Color(0xFFFF6B6B),
    info = Color(0xFF6CC6FF),
)

private val LightAccents = AccentColors(
    storage = Color(0xFF00A676),
    memory = Color(0xFF6A55E8),
    cache = Color(0xFFE08A00),
    battery = Color(0xFF1E88E5),
    good = Color(0xFF14A06B),
    danger = Color(0xFFD93A3A),
    info = Color(0xFF1E88E5),
)

val LocalAccents = staticCompositionLocalOf { DarkAccents }

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2EE6A8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0E4D3B),
    onPrimaryContainer = Color(0xFFB4FFE3),
    secondary = Color(0xFF9D8CFF),
    onSecondary = Color(0xFF1A1060),
    secondaryContainer = Color(0xFF2B2563),
    onSecondaryContainer = Color(0xFFE2DDFF),
    tertiary = Color(0xFFFFB547),
    onTertiary = Color(0xFF452B00),
    background = Color(0xFF0A0F1C),
    onBackground = Color(0xFFE7EBF3),
    surface = Color(0xFF0A0F1C),
    onSurface = Color(0xFFE7EBF3),
    surfaceVariant = Color(0xFF1C2438),
    onSurfaceVariant = Color(0xFFA4AEC4),
    surfaceContainerLowest = Color(0xFF070B15),
    surfaceContainerLow = Color(0xFF0F1524),
    surfaceContainer = Color(0xFF121A2B),
    surfaceContainerHigh = Color(0xFF192134),
    surfaceContainerHighest = Color(0xFF222B42),
    outline = Color(0xFF3B4661),
    outlineVariant = Color(0xFF243049),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00A676),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F5DF),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF5B4BDB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E0FF),
    onSecondaryContainer = Color(0xFF1A1060),
    tertiary = Color(0xFFC77800),
    onTertiary = Color.White,
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF0E1525),
    surface = Color(0xFFF4F7FB),
    onSurface = Color(0xFF0E1525),
    surfaceVariant = Color(0xFFE3E8F1),
    onSurfaceVariant = Color(0xFF4A5468),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9FBFE),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF2F8),
    surfaceContainerHighest = Color(0xFFE5EAF2),
    outline = Color(0xFFB5BFCF),
    outlineVariant = Color(0xFFDCE2EC),
    error = Color(0xFFD93A3A),
    onError = Color.White,
)

private val AppTypography: Typography = Typography().let { t ->
    t.copy(
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun NitidoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAccents provides if (darkTheme) DarkAccents else LightAccents) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
