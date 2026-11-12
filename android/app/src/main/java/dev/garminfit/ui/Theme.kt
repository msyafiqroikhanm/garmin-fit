package dev.garminfit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.garminfit.R

// Kinetik: dark only. The app is read first thing in the morning and right after a run, next to a
// dark watch face; big condensed numbers on near-black read at a glance. Volt doubles as "good" on purpose.
object K {
    val base = Color(0xFF0A0F0B)
    val surface = Color(0xFF141A15)
    val track = Color(0xFF222823)
    val border = Color(0xFF2A2F2B)
    /** Hairline card edge: on near-black a fill step alone reads as one flat slab. */
    val line = Color(0x12F4F6F2)
    val ink = Color(0xFFF4F6F2)
    val muted = Color(0xFFA0A6A1)
    val body2 = Color(0xFFB9C0BA)
    val body3 = Color(0xFFC9D0CA)
    val good = Color(0xFFB1EF4A)
    val fair = Color(0xFFFFB330)
    val low = Color(0xFFFF645F)
    val onAccent = Color(0xFF0A0F0B)
    val blue = Color(0xFF67ADDD)
    val zones = listOf(Color(0xFF67ADDD), Color(0xFF37D8C9), good, fair, low)
    val deep = Color(0xFF4671B7)
    val light = Color(0xFF93C7D9)
}

enum class Status { GOOD, FAIR, LOW }

fun Status.color(): Color = when (this) {
    Status.GOOD -> K.good
    Status.FAIR -> K.fair
    Status.LOW -> K.low
}

// Barlow Condensed carries numbers and the one status word (sporty, narrow enough for 80sp distances on a
// phone); Plus Jakarta Sans, sentence case with tight tracking, for every heading, label and paragraph, so
// screens stop shouting in caps; JetBrains Mono only for axis labels and small load numbers.
val Condensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
    Font(R.font.barlow_condensed_extrabolditalic, FontWeight.ExtraBold, FontStyle.Italic),
)
@OptIn(ExperimentalTextApi::class)
private fun jakarta(w: Int) = Font(R.font.plus_jakarta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))
val Mono = FontFamily(Font(R.font.jetbrains_mono_medium, FontWeight.Medium))

fun num(size: TextUnit, weight: FontWeight = FontWeight.ExtraBold, italic: Boolean = false, lh: Float = 1f) = TextStyle(
    fontFamily = Condensed,
    fontSize = size,
    lineHeight = size * lh,
    fontWeight = weight,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    fontFeatureSettings = "tnum",
)

fun body(size: TextUnit, weight: FontWeight = FontWeight.Normal, lh: Float = 1.4f) =
    TextStyle(fontFamily = Jakarta, fontSize = size, lineHeight = size * lh, fontWeight = weight, fontFeatureSettings = "tnum")

/** Heading in Jakarta; bigger sizes get tighter tracking so they hold together like one shape. */
fun head(size: TextUnit, tracking: Float, weight: FontWeight = FontWeight.Bold, lh: Float = 1.15f) =
    body(size, weight, lh).copy(letterSpacing = tracking.em)

object T {
    val hero = num(80.sp, italic = true, lh = 0.85f)
    val statusWord = num(44.sp, italic = true, lh = 0.9f)
    val screenTitle = head(32.sp, -0.035f, lh = 1.05f)
    /** The one message a screen leads with, set straight on the background (Today's session, Trends' insight). */
    val headline = head(28.sp, -0.03f, lh = 1.12f)
    val sectionTitle = head(20.sp, -0.025f, lh = 1.2f)
    val sheetTitle = head(24.sp, -0.03f)
    val cardTitle = head(16.sp, -0.015f, lh = 1.25f)
    val greeting = head(26.sp, -0.03f)
    val label = body(12.5.sp, FontWeight.SemiBold, 1.3f)
    val body = body(15.sp, lh = 1.5f)
    val bodyS = body(13.sp)
    val caption = body(12.sp)
    val mono = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
}

// primary is ink, not volt: Material's own interaction marks (pull-to-refresh, selection, progress) are
// interaction, and volt is kept for the body's "good" and for work that is done.
private val Scheme = darkColorScheme(
    primary = K.ink,
    onPrimary = K.onAccent,
    background = K.base,
    onBackground = K.ink,
    surface = K.base,
    onSurface = K.ink,
    surfaceContainer = K.surface,
    surfaceContainerHigh = K.track,
    surfaceContainerHighest = K.track,
    onSurfaceVariant = K.muted,
    outline = K.border,
    outlineVariant = K.track,
    error = K.low,
)

/** True when the phone's animator scale is off; every entrance animation then snaps to its end state. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val reduce = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val base = MaterialTheme.typography
    val type = base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = Jakarta),
        bodyMedium = base.bodyMedium.copy(fontFamily = Jakarta),
        bodySmall = base.bodySmall.copy(fontFamily = Jakarta),
        labelLarge = base.labelLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    )
    CompositionLocalProvider(LocalReduceMotion provides reduce) {
        MaterialTheme(colorScheme = Scheme, typography = type, content = content)
    }
}
