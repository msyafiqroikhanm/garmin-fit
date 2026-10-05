package dev.garminfit.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import dev.garminfit.Activity
import dev.garminfit.R
import dev.garminfit.activityType
import dev.garminfit.dayMonth
import dev.garminfit.duration
import dev.garminfit.int
import dev.garminfit.km
import dev.garminfit.pace
import kotlin.math.max

enum class Ratio(val label: String, val h: Int) { STORY("9:16", 1920), FEED("4:5", 1350) }
enum class Backdrop(val label: String) { PHOTO("Foto"), CARD("Kartu"), STICKER("Stiker") }
enum class Layout(val label: String) { BOLD("Bold"), MINIMAL("Minimal"), GRID("Grid"), ROUTE("Rute") }
enum class Ink(val label: String, val color: Color) { WHITE("Putih", Color.White), BLACK("Hitam", K.base), VOLT("Volt", K.good) }

const val SHARE_W = 1080

data class ShareStat(val key: String, val value: String, val unit: String, val label: String)

data class ShareSpec(
    val ratio: Ratio = Ratio.STORY,
    val backdrop: Backdrop = Backdrop.CARD,
    val layout: Layout = Layout.BOLD,
    val ink: Ink = Ink.WHITE,
    val stats: Set<String> = emptySet(),
    val wordmark: Boolean = true,
    // overlay position as a fraction of the canvas, and its zoom; set by drag and pinch
    val dx: Float = 0f,
    val dy: Float = 0f,
    val zoom: Float = 1f,
)

fun shareStats(a: Activity): List<ShareStat> = listOfNotNull(
    a.distanceM?.takeIf { it > 0 }?.let { ShareStat("dist", km(it), "km", "jarak") },
    a.avgSpeed?.takeIf { (a.distanceM ?: 0.0) > 0 }?.let { ShareStat("pace", pace(it), "/km", "pace") },
    ShareStat("time", duration(a.durationS), "", "waktu"),
    a.strength?.totalSets?.let { ShareStat("sets", "$it", "set", "set") },
    a.strength?.totalReps?.let { ShareStat("reps", "$it", "rep", "rep") },
    a.avgHr?.let { ShareStat("hr", it.int(), "bpm", "HR rata-rata") },
    a.calories?.let { ShareStat("kcal", it.int(), "kkal", "kalori") },
    a.elevationGain?.takeIf { it > 0 }?.let { ShareStat("elev", it.int(), "m", "elevasi") },
)

class ShareFonts(ctx: Context) {
    val hero: Typeface = ResourcesCompat.getFont(ctx, R.font.barlow_condensed_extrabolditalic)!!
    val num: Typeface = ResourcesCompat.getFont(ctx, R.font.barlow_condensed_bold)!!
    val label: Typeface = ResourcesCompat.getFont(ctx, R.font.barlow_semibold)!!
}

/**
 * Draws the share image at any size; every measure is in units of a 1080 px wide canvas so the
 * preview (drawn straight into a Compose canvas) and the exported bitmap come out identical.
 */
fun renderShare(c: Canvas, w: Float, h: Float, spec: ShareSpec, a: Activity, photo: Bitmap?, fonts: ShareFonts) {
    val u = w / SHARE_W
    when (spec.backdrop) {
        Backdrop.PHOTO -> if (photo != null) drawCover(c, photo, w, h) else c.drawColor(K.surface.toArgb())
        // black ink on the dark card would vanish, so the card flips to volt for it
        Backdrop.CARD -> c.drawColor((if (spec.ink == Ink.BLACK) K.good else K.base).toArgb())
        Backdrop.STICKER -> Unit
    }
    val ink = spec.ink.color
    // a soft shadow keeps light ink readable on bright parts of a photo
    val shadow = spec.backdrop == Backdrop.PHOTO && spec.ink != Ink.BLACK
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink.toArgb()
        if (shadow) setShadowLayer(18 * u, 0f, 3 * u, 0x8C000000.toInt())
    }
    val all = shareStats(a)
    val stats = all.filter { it.key in spec.stats }.ifEmpty { all.take(1) }
    val route = a.route?.let { trimEnds(it) }?.takeIf { it.size > 1 }
    val muscles = a.strength?.muscles?.takeIf { m -> m.values.any { it.primary > 0 } }

    c.save()
    c.translate(spec.dx * w, spec.dy * h)
    c.scale(spec.zoom, spec.zoom, w / 2, h / 2)
    val m = 72 * u
    val title = "${activityType(a.type).uppercase()} · ${dayMonth(a.date).uppercase()}"
    fun graphic(left: Float, top: Float, gw: Float, gh: Float) {
        if (route != null) drawRoute(c, left, top, gw, gh, route, 9 * u, ink)
        else if (muscles != null) drawBody(c, left, top, gw, gh, muscles, ink, ink.copy(alpha = 0.16f))
    }
    val hasGraphic = route != null || muscles != null
    when (spec.layout) {
        Layout.BOLD -> {
            val hero = stats.first()
            var y = h - m
            val rest = stats.drop(1).take(3)
            if (rest.isNotEmpty()) {
                statRow(c, text, fonts, u, m, y, w - 2 * m, rest, 64f)
                y -= 150 * u
            }
            val heroSize = 300 * u
            text.typeface = fonts.hero; text.textSize = heroSize
            c.drawText(hero.value, m, y, text)
            if (hero.unit.isNotEmpty()) {
                val x = m + text.measureText(hero.value) + 18 * u
                text.typeface = fonts.num; text.textSize = 72 * u
                c.drawText(hero.unit, x, y, text)
            }
            y -= heroSize * 0.82f
            label(c, text, fonts, u, title, m, y)
            if (hasGraphic) graphic(m, y - 60 * u - 420 * u, 420 * u, 420 * u)
        }
        Layout.MINIMAL -> {
            val row = stats.take(3)
            statRow(c, text, fonts, u, m, h - m, w - 2 * m, row, 80f)
            label(c, text, fonts, u, title, m, h - m - 170 * u)
        }
        Layout.GRID -> {
            val cells = stats.take(4)
            val cw = (w - 2 * m) / 2
            val rows = (cells.size + 1) / 2
            val bottom = h - m
            cells.forEachIndexed { i, s ->
                val col = i % 2
                val r = i / 2
                val by = bottom - (rows - 1 - r) * 230 * u
                statCell(c, text, fonts, u, m + col * cw, by, s, 110f)
            }
            label(c, text, fonts, u, title, m, bottom - rows * 230 * u - 10 * u)
        }
        Layout.ROUTE -> {
            val row = stats.take(3)
            statRow(c, text, fonts, u, m, h - m, w - 2 * m, row, 72f)
            val labelY = h - m - 160 * u
            label(c, text, fonts, u, title, m, labelY)
            if (hasGraphic) {
                val top = if (spec.ratio == Ratio.STORY) h * 0.18f else m + 60 * u
                graphic(m, top, w - 2 * m, labelY - 80 * u - top)
            }
        }
    }
    c.restore()
    if (spec.wordmark) {
        text.typeface = fonts.hero; text.textSize = 44 * u
        text.letterSpacing = 0.04f
        c.drawText("KINETIK", m, m + 36 * u, text)
        text.letterSpacing = 0f
    }
}

private fun label(c: Canvas, p: Paint, f: ShareFonts, u: Float, s: String, x: Float, y: Float) {
    p.typeface = f.label; p.textSize = 34 * u
    p.letterSpacing = 0.08f
    c.drawText(s, x, y, p)
    p.letterSpacing = 0f
}

/** Value with unit above a small label; [y] is the label baseline. */
private fun statCell(c: Canvas, p: Paint, f: ShareFonts, u: Float, x: Float, y: Float, s: ShareStat, size: Float) {
    p.typeface = f.label; p.textSize = 30 * u
    c.drawText(s.label, x, y, p)
    p.typeface = f.num; p.textSize = size * u
    val vy = y - 46 * u
    c.drawText(s.value, x, vy, p)
    if (s.unit.isNotEmpty() && s.unit != s.label) {
        val vx = x + p.measureText(s.value) + 10 * u
        p.textSize = size * 0.42f * u
        c.drawText(s.unit, vx, vy, p)
    }
}

private fun statRow(c: Canvas, p: Paint, f: ShareFonts, u: Float, x: Float, y: Float, width: Float, row: List<ShareStat>, size: Float) {
    val cw = width / max(1, row.size)
    row.forEachIndexed { i, s -> statCell(c, p, f, u, x + i * cw, y, s, size) }
}

private fun drawCover(c: Canvas, b: Bitmap, w: Float, h: Float) {
    val scale = max(w / b.width, h / b.height)
    val sw = (w / scale).toInt()
    val sh = (h / scale).toInt()
    val sx = (b.width - sw) / 2
    val sy = (b.height - sh) / 2
    c.drawBitmap(b, Rect(sx, sy, sx + sw, sy + sh), RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
}

fun exportShare(spec: ShareSpec, a: Activity, photo: Bitmap?, fonts: ShareFonts): Bitmap =
    Bitmap.createBitmap(SHARE_W, spec.ratio.h, Bitmap.Config.ARGB_8888).also {
        renderShare(Canvas(it), SHARE_W.toFloat(), spec.ratio.h.toFloat(), spec, a, photo, fonts)
    }
