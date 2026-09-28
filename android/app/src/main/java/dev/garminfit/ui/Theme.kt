package dev.garminfit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Warm paper neutrals keep morning reading calm; teal is the single interactive accent.
// Status colors are reserved for recovery meaning only. All text pairs checked >= 4.5:1.
private val Light = lightColorScheme(
    primary = Color(0xFF1F5E57),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFD7E8E4),
    onSecondaryContainer = Color(0xFF123B36),
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF1C1F1E),
    surface = Color(0xFFF6F4EF),
    onSurface = Color(0xFF1C1F1E),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEAE3),
    onSurfaceVariant = Color(0xFF5B605D),
    outlineVariant = Color(0xFFDAD6CD),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7CC4B8),
    onPrimary = Color(0xFF0B2E2A),
    secondaryContainer = Color(0xFF214640),
    onSecondaryContainer = Color(0xFFCDE8E2),
    background = Color(0xFF121413),
    onBackground = Color(0xFFECEDEA),
    surface = Color(0xFF121413),
    onSurface = Color(0xFFECEDEA),
    surfaceContainer = Color(0xFF1C1F1E),
    surfaceContainerHigh = Color(0xFF262A28),
    onSurfaceVariant = Color(0xFFA5AAA6),
    outlineVariant = Color(0xFF363B38),
)

@Immutable
data class StatusColors(val good: Color, val fair: Color, val low: Color, val band: Color)

private val LightStatus = StatusColors(Color(0xFF2E7D4F), Color(0xFF946000), Color(0xFFB3261E), Color(0x332E7D4F))
private val DarkStatus = StatusColors(Color(0xFF7BC89A), Color(0xFFE0B25B), Color(0xFFF2A29A), Color(0x407BC89A))

val LocalStatus = staticCompositionLocalOf { LightStatus }

enum class Status { GOOD, FAIR, LOW }

@Composable
fun Status.color(): Color = LocalStatus.current.let {
    when (this) {
        Status.GOOD -> it.good
        Status.FAIR -> it.fair
        Status.LOW -> it.low
    }
}

// System font (Roboto): native on every family phone, clear tabular numerals.
private val base = Typography()
private val Type = base.copy(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalStatus provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = AppShapes, content = content)
    }
}
