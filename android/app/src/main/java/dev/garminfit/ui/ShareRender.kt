package dev.garminfit.ui

import dev.garminfit.tr
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
import dev.garminfit.MuscleLoad
import dev.garminfit.R
import dev.garminfit.activityType
import dev.garminfit.dayMonth
import dev.garminfit.duration
import dev.garminfit.int
import dev.garminfit.km
import dev.garminfit.PlanView
import dev.garminfit.pace
import dev.garminfit.speedText
import dev.garminfit.paceSec
import dev.garminfit.splitShort
import kotlin.math.ceil
import kotlin.math.max

enum class Ratio(val label: String, val h: Int) { STORY("9:16", 1920), FEED("4:5", 1350) }
enum class Backdrop(private val en: String, private val id: String) {
    PHOTO("Photo", "Foto"), CARD("Card", "Kartu"), STICKER("Sticker", "Stiker");

    val label: String get() = tr(en, id)
}
enum class Layout(private val en: String, private val id: String) {
    BOLD("Bold", "Bold"), MINIMAL("Minimal", "Minimal"), GRID("Grid", "Grid"), ROUTE("Route", "Rute");

    val label: String get() = tr(en, id)
}
enum class Ink(private val en: String, private val id: String, val color: Color) {
    WHITE("White", "Putih", Color.White), BLACK("Black", "Hitam", K.base), VOLT("Volt", "Volt", K.good);

    val label: String get() = tr(en, id)
}

const val SHARE_W = 1080

data class ShareStat(val key: String, val value: String, val unit: String, val label: String)

data class ShareSpec(
    val ratio: Ratio = Ratio.STORY,
    val backdrop: Backdrop = Backdrop.CARD,
    val layout: Layout = Layout.BOLD,
    val ink: Ink = Ink.WHITE,
    val stats: Set<String> = emptySet(),
    val wordmark: Boolean = true,
    /** The plan pace band, when the activity has a plan pace to compare with. */
    val pace: Boolean = true,
    /** The stat that leads (the hero in Bold); null keeps the first selected one. */
    val main: String? = null,
    /** Title from the activity's own name ("Jakarta Marathon") instead of its type and date. */
    val useName: Boolean = false,
    // overlay position as a fraction of the canvas, and its zoom; set by drag and pinch
    val dx: Float = 0f,
    val dy: Float = 0f,
    val zoom: Float = 1f,
)

sealed interface ShareGraphic {
    data class Route(val points: List<RoutePoint>) : ShareGraphic
    data class Body(val muscles: Map<String, MuscleLoad>) : ShareGraphic
    data class Heat(val routes: List<List<RoutePoint>>) : ShareGraphic
}

/** The plan's pace range and the run's average pace, all in seconds per km. */
data class SharePace(val fast: Double, val slow: Double, val actual: Double)

/** Everything a share image shows; built from an activity or a recap so both share one renderer. */
data class ShareContent(
    val title: String,
    val stats: List<ShareStat>,
    val graphic: ShareGraphic?,
    val defaultStats: Int = 4,
    val pace: SharePace? = null,
    /** The activity's own name, offered as the title. */
    val name: String? = null,
)

/** [plan] is the cached plan view; a run that fulfilled a plan session with a pace target gets the pace band. */
fun Activity.shareContent(plan: PlanView? = null): ShareContent {
    val pr = prs.takeIf { it.isNotEmpty() }?.let { " · PR " + it.joinToString(" ") { d -> splitShort(d) } }.orEmpty()
    // the app's workout log knows the real exercises; the watch's guesses are the fallback, as on the detail screen
    val muscles = workout?.muscles?.takeIf { it.isNotEmpty() } ?: strength?.muscles
    val graphic = route?.let { trimEnds(it) }?.takeIf { it.size > 1 }?.let { ShareGraphic.Route(it) }
        ?: muscles?.takeIf { m -> m.values.any { it.primary > 0 } }?.let { ShareGraphic.Body(it) }
    val target = plan?.sessions?.firstOrNull { it.kind == "run" && id in it.activityIds }?.pace
    val pace = target?.let { t -> avgSpeed?.takeIf { it > 0 }?.let { SharePace(t.minSKm, t.maxSKm, 1000 / it) } }
    return ShareContent("${activityType(type)} · ${dayMonth(date)}$pr", shareStats(this), graphic, pace = pace, name = name)
}

fun shareStats(a: Activity): List<ShareStat> = listOfNotNull(
    a.distanceM?.takeIf { it > 0 }?.let { ShareStat("dist", km(it), "km", tr("distance", "jarak")) },
    a.avgSpeed?.takeIf { (a.distanceM ?: 0.0) > 0 }?.let { speedText(a.type, it).let { (v, u) -> ShareStat("pace", v, u, if (u == "km/h") tr("speed", "kecepatan") else "pace") } },
    ShareStat("time", duration(a.durationS), "", tr("time", "waktu")),
    (a.workout?.totalSets?.takeIf { it > 0 } ?: a.strength?.totalSets)?.let { ShareStat("sets", "$it", "set", tr("sets", "set")) },
    (a.workout?.reps?.takeIf { it > 0 } ?: a.strength?.totalReps)?.let { ShareStat("reps", "$it", "rep", tr("reps", "rep")) },
    a.avgHr?.let { ShareStat("hr", it.int(), "bpm", tr("avg HR", "HR rata-rata")) },
    a.calories?.let { ShareStat("kcal", it.int(), tr("kcal", "kkal"), tr("calories", "kalori")) },
    a.elevationGain?.takeIf { it > 0 }?.let { ShareStat("elev", it.int(), "m", tr("elevation", "elevasi")) },
)

class ShareFonts(ctx: Context) {
    val hero: Typeface = ResourcesCompat.getFont(ctx, R.font.barlow_condensed_extrabolditalic)!!
    val num: Typeface = ResourcesCompat.getFont(ctx, R.font.barlow_condensed_bold)!!
    /** Plus Jakarta Sans, the app's text face; its weight goes on the paint through the wght axis. */
    val text: Typeface = ResourcesCompat.getFont(ctx, R.font.plus_jakarta_sans)!!
}

private fun Paint.jakarta(f: ShareFonts, weight: Int, size: Float) {
    typeface = f.text
    // cleared first: the paint skips a settings string equal to the last one, even after the typeface changed
    fontVariationSettings = null
    fontVariationSettings = "'wght' $weight"
    textSize = size
}

private fun Paint.barlow(face: Typeface, size: Float) {
    typeface = face
    textSize = size
}

/**
 * Draws the share image at any size; every measure is in units of a 1080 px wide canvas so the
 * preview (drawn straight into a Compose canvas) and the exported bitmap come out identical.
 * Each layout stacks its block from the bottom up and ends with the wordmark on top, so nothing floats.
 * Returns the overlay's bounds as fractions of the canvas, before drag and zoom.
 */
fun renderShare(c: Canvas, w: Float, h: Float, spec: ShareSpec, content: ShareContent, photo: Bitmap?, fonts: ShareFonts): RectF {
    val u = w / SHARE_W
    val box = RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    when (spec.backdrop) {
        Backdrop.PHOTO -> if (photo != null) drawCover(c, photo, w, h) else c.drawColor(K.surface.toArgb())
        // black ink on the dark card would vanish, so the card flips to volt for it
        Backdrop.CARD -> c.drawColor((if (spec.ink == Ink.BLACK) K.good else K.base).toArgb())
        Backdrop.STICKER -> Unit
    }
    val ink = spec.ink.color
    // a soft shadow keeps the ink readable on whatever photo sits behind it: dark under light ink, light under black
    val shadow = when (spec.backdrop) {
        Backdrop.CARD -> 0
        Backdrop.PHOTO -> if (spec.ink == Ink.BLACK) 0 else 0x8C000000.toInt()
        Backdrop.STICKER -> if (spec.ink == Ink.BLACK) 0x99FFFFFF.toInt() else 0x80000000.toInt()
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink.toArgb()
        if (shadow != 0) setShadowLayer(16 * u, 0f, 3 * u, shadow)
    }
    val all = content.stats
    val picked = all.filter { it.key in spec.stats }.ifEmpty { all.take(1) }
    // the chosen main number leads; the rest keep their order
    val stats = picked.sortedBy { if (it.key == spec.main) 0 else 1 }
    val heading = content.name?.takeIf { spec.useName && it.isNotBlank() } ?: content.title
    val pace = content.pace?.takeIf { spec.pace }

    c.save()
    c.translate(spec.dx * w, spec.dy * h)
    c.scale(spec.zoom, spec.zoom, w / 2, h / 2)
    val m = 72 * u
    val full = w - 2 * m
    val g = content.graphic?.takeIf { spec.layout == Layout.BOLD || spec.layout == Layout.ROUTE }
    val mark = if (spec.wordmark) 72 * u + 40 * u else 0f
    fun graphic(left: Float, top: Float, gw: Float, gh: Float, stroke: Float) {
        g ?: return
        box.union(left, top, left + gw, top + gh)
        when (g) {
            is ShareGraphic.Route -> drawRoute(c, left, top, gw, gh, g.points, stroke, ink, shadow)
            is ShareGraphic.Body -> drawBody(c, left, top, gw, gh, g.muscles, ink, ink.copy(alpha = 0.16f))
            is ShareGraphic.Heat -> drawHeatmap(c, left, top, gw, gh, g.routes, stroke * 0.5f, ink)
        }
    }
    var y = h - m
    // the band repeats the pace as a number only when the stats above do not already show it
    val paceShown = stats.any { it.key == "pace" }
    if (pace != null) y = paceBand(c, text, fonts, u, m, y, full, pace, !paceShown, ink, shadow, box) - 64 * u
    when (spec.layout) {
        Layout.BOLD -> {
            val rest = stats.drop(1).take(3)
            if (rest.isNotEmpty()) y = statRow(c, text, fonts, u, m, y, full, rest, 84f, box) - 72 * u
            val hero = stats.first()
            // a long hero such as "51m 42s" steps down until it and its unit fit the frame
            text.barlow(fonts.hero, 300 * u)
            val heroW = text.measureText(hero.value) + if (hero.unit.isEmpty()) 0f else 120 * u
            val heroSize = 300 * u * minOf(1f, full / heroW)
            text.textSize = heroSize
            c.drawText(hero.value, m, y, text)
            var right = m + text.measureText(hero.value)
            if (hero.unit.isNotEmpty()) {
                val x = right + 18 * u
                text.barlow(fonts.num, 80 * u)
                c.drawText(hero.unit, x, y, text)
                right = x + text.measureText(hero.unit)
            }
            box.union(m, y - heroSize * 0.75f, right, y + heroSize * 0.08f)
            y = title(c, text, fonts, u, heading, m, y - heroSize * 0.75f - 48 * u, box)
            if (g != null) {
                // the picture sits right on the title, about 70% of the frame wide; a story keeps its top fifth clear
                val ceiling = (if (spec.ratio == Ratio.STORY) h * 0.2f else m) + mark
                val bottom = y - 48 * u
                val (gw, gh) = fit(g, 0.7f * w, bottom - ceiling)
                if (gh >= 200 * u) {
                    graphic(m, bottom - gh, gw, gh, 16 * u)
                    y = bottom - gh
                }
            }
        }
        Layout.MINIMAL -> {
            y = statRow(c, text, fonts, u, m, y, full, stats.take(3), 96f, box) - 56 * u
            y = title(c, text, fonts, u, heading, m, y, box)
        }
        Layout.GRID -> {
            val cells = stats.take(4)
            val cw = full / 2
            val rows = (cells.size + 1) / 2
            var top = y
            cells.forEachIndexed { i, s ->
                val by = y - (rows - 1 - i / 2) * 240 * u
                top = minOf(top, statCell(c, text, fonts, u, m + (i % 2) * cw, by, s, 120f, box))
            }
            y = title(c, text, fonts, u, heading, m, top - 56 * u, box)
        }
        Layout.ROUTE -> {
            y = statRow(c, text, fonts, u, m, y, full, stats.take(3), 84f, box) - 56 * u
            y = title(c, text, fonts, u, heading, m, y, box)
            if (g != null) {
                val ceiling = (if (spec.ratio == Ratio.STORY) h * 0.12f else m) + mark
                val bottom = y - 64 * u
                val (gw, gh) = fit(g, full, bottom - ceiling)
                graphic(m + (full - gw) / 2, bottom - gh, gw, gh, 14 * u)
                y = bottom - gh
            }
        }
    }
    if (spec.wordmark) {
        // part of the block, sized to read next to the hero instead of hiding in a corner
        text.barlow(fonts.hero, 72 * u)
        text.letterSpacing = 0.03f
        val by = y - 40 * u
        c.drawText("KINETIK", m, by, text)
        box.union(m, by - 72 * u * 0.72f, m + text.measureText("KINETIK"), by)
        text.letterSpacing = 0f
    }
    c.restore()
    return RectF(box.left / w, box.top / h, box.right / w, box.bottom / h)
}

/** The largest size of [g]'s shape that fits in [maxW]×[maxH]. */
private fun fit(g: ShareGraphic, maxW: Float, maxH: Float): Pair<Float, Float> {
    val aspect = when (g) {
        is ShareGraphic.Route -> routeAspect(g.points)
        is ShareGraphic.Heat -> routeAspect(g.routes.flatten())
        // two figures side by side with a 6% gap, 200×220 units
        is ShareGraphic.Body -> 0.97f
    }
    val h = maxH.coerceAtLeast(0f)
    return if (maxW / aspect <= h) maxW to maxW / aspect else h * aspect to h
}

/** Sentence-case title line; [y] is its baseline. Returns the top of the line. */
private fun title(c: Canvas, p: Paint, f: ShareFonts, u: Float, s: String, x: Float, y: Float, box: RectF): Float {
    p.jakarta(f, 700, 40 * u)
    c.drawText(s, x, y, p)
    box.union(x, y - 40 * u * 0.75f, x + p.measureText(s), y + 10 * u)
    return y - 40 * u * 0.75f
}

/** Value with unit above a sentence-case caption; [y] is the caption baseline. Returns the top of the value. */
private fun statCell(c: Canvas, p: Paint, f: ShareFonts, u: Float, x: Float, y: Float, s: ShareStat, size: Float, box: RectF): Float {
    val caption = s.label.replaceFirstChar { it.uppercase() }
    p.jakarta(f, 600, 30 * u)
    c.drawText(caption, x, y, p)
    val vy = y - 52 * u
    val top = vy - size * 0.75f * u
    box.union(x, top, x + p.measureText(caption), y + 8 * u)
    p.barlow(f.num, size * u)
    c.drawText(s.value, x, vy, p)
    box.union(x + p.measureText(s.value), vy)
    if (s.unit.isNotEmpty() && s.unit != s.label) {
        val vx = x + p.measureText(s.value) + 10 * u
        p.textSize = size * 0.42f * u
        c.drawText(s.unit, vx, vy, p)
        box.union(vx + p.measureText(s.unit), vy)
    }
    return top
}

private fun statRow(c: Canvas, p: Paint, f: ShareFonts, u: Float, x: Float, y: Float, width: Float, row: List<ShareStat>, size: Float, box: RectF): Float {
    val cw = width / max(1, row.size)
    return row.mapIndexed { i, s -> statCell(c, p, f, u, x + i * cw, y, s, size, box) }.minOrNull() ?: y
}

/**
 * The baseline band from the app, for pace: a track, the plan's range as a band, the run's average as a dot.
 * Faster is to the right. [y] is the bottom of the bar; returns the top of the line above it.
 */
private fun paceBand(c: Canvas, p: Paint, f: ShareFonts, u: Float, x: Float, y: Float, width: Float, pace: SharePace, number: Boolean, ink: Color, shadow: Int, box: RectF): Float {
    val spread = (pace.slow - pace.fast).coerceAtLeast(20.0)
    val lo = minOf(pace.fast, pace.actual) - spread * 0.6
    val hi = maxOf(pace.slow, pace.actual) + spread * 0.6
    fun px(sec: Double) = x + ((hi - sec) / (hi - lo)).toFloat() * width
    val barH = 14 * u
    val cy = y - barH / 2
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.color = ink.copy(alpha = 0.22f).toArgb()
    c.drawRoundRect(x, y - barH, x + width, y, barH / 2, barH / 2, fill)
    fill.color = ink.copy(alpha = 0.55f).toArgb()
    c.drawRoundRect(px(pace.slow), y - barH, px(pace.fast), y, barH / 2, barH / 2, fill)
    fill.color = ink.toArgb()
    if (shadow != 0) fill.setShadowLayer(10 * u, 0f, 2 * u, shadow)
    c.drawCircle(px(pace.actual), cy, 22 * u, fill)
    // the actual pace as a number, then what the plan asked for
    val by = y - barH - 40 * u
    var nx = x
    var top = by - 30 * u * 0.75f
    if (number) {
        p.barlow(f.num, 56 * u)
        val v = paceSec(pace.actual)
        c.drawText(v, x, by, p)
        nx += p.measureText(v) + 10 * u
        top = by - 56 * u * 0.75f
    }
    p.jakarta(f, 600, 30 * u)
    val range = "${paceSec(pace.fast)}–${paceSec(pace.slow)}"
    val rest = if (number) "/km · plan $range" else tr("Plan pace $range /km", "Pace plan $range /km")
    c.drawText(rest, nx, by, p)
    nx += p.measureText(rest)
    box.union(x - 22 * u, top, maxOf(x + width + 22 * u, nx), y + 22 * u - barH / 2)
    return top
}

internal fun drawCover(c: Canvas, b: Bitmap, w: Float, h: Float) {
    val scale = max(w / b.width, h / b.height)
    val sw = (w / scale).toInt()
    val sh = (h / scale).toInt()
    val sx = (b.width - sw) / 2
    val sy = (b.height - sh) / 2
    c.drawBitmap(b, Rect(sx, sy, sx + sw, sy + sh), RectF(0f, 0f, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
}

fun exportShare(spec: ShareSpec, content: ShareContent, photo: Bitmap?, fonts: ShareFonts): Bitmap =
    if (spec.backdrop == Backdrop.STICKER) exportSticker(spec, content, fonts)
    else Bitmap.createBitmap(SHARE_W, spec.ratio.h, Bitmap.Config.ARGB_8888).also {
        renderShare(Canvas(it), SHARE_W.toFloat(), spec.ratio.h.toFloat(), spec, content, photo, fonts)
    }

/** A sticker is laid out on a story frame without drag or zoom, then cropped to what it draws. */
fun ShareSpec.asSticker() = copy(backdrop = Backdrop.STICKER, ratio = Ratio.STORY, dx = 0f, dy = 0f, zoom = 1f)

/** The sticker's crop in 1080 px frame units: everything it draws, wordmark included, plus a margin for the shadow. */
fun stickerBounds(spec: ShareSpec, content: ShareContent, fonts: ShareFonts): RectF {
    val w = SHARE_W.toFloat()
    val h = Ratio.STORY.h.toFloat()
    // measured by drawing once on a throwaway 1 px canvas; the box comes from layout, not from pixels
    val b = renderShare(Canvas(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)), w, h, spec.asSticker(), content, null, fonts)
    val pad = 40f
    return RectF(b.left * w - pad, b.top * h - pad, b.right * w + pad, b.bottom * h + pad)
}

/** Draws the sticker cropped to [bounds], scaled into a [w] wide area at the canvas origin. */
fun renderSticker(c: Canvas, w: Float, bounds: RectF, spec: ShareSpec, content: ShareContent, fonts: ShareFonts) {
    val k = w / bounds.width()
    c.save()
    c.scale(k, k)
    c.translate(-bounds.left, -bounds.top)
    renderShare(c, SHARE_W.toFloat(), Ratio.STORY.h.toFloat(), spec.asSticker(), content, null, fonts)
    c.restore()
}

/** Transparent PNG-ready bitmap of the cropped sticker, at least 1080 px on its long side. */
fun exportSticker(spec: ShareSpec, content: ShareContent, fonts: ShareFonts): Bitmap {
    val b = stickerBounds(spec, content, fonts)
    val k = max(1f, SHARE_W / max(b.width(), b.height()))
    val bmp = Bitmap.createBitmap(ceil(b.width() * k).toInt(), ceil(b.height() * k).toInt(), Bitmap.Config.ARGB_8888)
    renderSticker(Canvas(bmp), bmp.width.toFloat(), b, spec, content, fonts)
    return bmp
}

/**
 * Clamps drag and zoom so the overlay box (fractions of the canvas, before transform) stays inside
 * the frame with a small margin; an overlay larger than the frame is centered.
 */
fun ShareSpec.keepInside(box: RectF?, pad: Float = 0.03f): ShareSpec {
    if (box == null || box.isEmpty) return this
    val zMax = minOf((1 - 2 * pad) / box.width(), (1 - 2 * pad) / box.height(), 2f)
    val z = zoom.coerceIn(0.5f, maxOf(0.5f, zMax))
    fun axis(d: Float, lo: Float, hi: Float): Float {
        val min = pad - (0.5f + (lo - 0.5f) * z)
        val max = 1 - pad - (0.5f + (hi - 0.5f) * z)
        return if (min <= max) d.coerceIn(min, max) else (min + max) / 2
    }
    return copy(zoom = z, dx = axis(dx, box.left, box.right), dy = axis(dy, box.top, box.bottom))
}
