package dev.garminfit.ui

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One route point from the backend: lat, lon, smoothed speed in cm/s. */
typealias RoutePoint = List<Double>

fun haversineM(a: RoutePoint, b: RoutePoint): Double {
    val r = 6_371_000.0
    val dLat = (b[0] - a[0]) * PI / 180
    val dLon = (b[1] - a[1]) * PI / 180
    val h = sin(dLat / 2).let { it * it } + cos(a[0] * PI / 180) * cos(b[0] * PI / 180) * sin(dLon / 2).let { it * it }
    return 2 * r * asin(sqrt(h))
}

/** Drops the first and last [meters] so a shared route does not point at home. */
fun trimEnds(pts: List<RoutePoint>, meters: Double = 200.0): List<RoutePoint> {
    fun cut(list: List<RoutePoint>): Int {
        var d = 0.0
        for (i in 1 until list.size) {
            d += haversineM(list[i - 1], list[i])
            if (d >= meters) return i
        }
        return list.size
    }
    val start = cut(pts)
    val end = pts.size - cut(pts.asReversed())
    return if (start < end - 1) pts.subList(start, end) else emptyList()
}

/** Equirectangular projection fitted into w×h, aspect kept, centered. Good enough at city scale. */
fun project(pts: List<RoutePoint>, w: Float, h: Float): List<Pair<Float, Float>> {
    if (pts.isEmpty()) return emptyList()
    val k = cos(pts.sumOf { it[0] } / pts.size * PI / 180)
    val xs = pts.map { it[1] * k }
    val ys = pts.map { -it[0] }
    val spanX = (xs.max() - xs.min()).coerceAtLeast(1e-9)
    val spanY = (ys.max() - ys.min()).coerceAtLeast(1e-9)
    val s = min(w / spanX, h / spanY)
    val ox = (w - spanX * s) / 2
    val oy = (h - spanY * s) / 2
    return xs.indices.map { i -> ((ox + (xs[i] - xs.min()) * s).toFloat()) to ((oy + (ys[i] - ys.min()) * s).toFloat()) }
}

private val slow = K.low
private val mid = K.fair
private val fast = K.good

/** Slow → fast relative to this run's own spread (10th..90th percentile), so every run shows contrast. */
fun paceColor(speed: Double, lo: Double, hi: Double): Color {
    val t = ((speed - lo) / (hi - lo).coerceAtLeast(1.0)).coerceIn(0.0, 1.0).toFloat()
    return if (t < 0.5f) lerp(slow, mid, t * 2) else lerp(mid, fast, (t - 0.5f) * 2)
}

/**
 * Draws the route into the rect on a native canvas, used by the detail card and the share image.
 * [color] null means pace gradient; otherwise a single color.
 */
fun drawRoute(c: Canvas, left: Float, top: Float, w: Float, h: Float, pts: List<RoutePoint>, stroke: Float, color: Color? = null) {
    if (pts.size < 2) return
    val pad = stroke * 2
    val xy = project(pts, w - pad * 2, h - pad * 2).map { (x, y) -> left + pad + x to top + pad + y }
    val sorted = pts.map { it.getOrElse(2) { 0.0 } }.sorted()
    val lo = sorted[sorted.size / 10]
    val hi = sorted[sorted.size * 9 / 10]
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    if (color != null) {
        p.color = color.toArgb()
        val path = android.graphics.Path().apply {
            moveTo(xy[0].first, xy[0].second)
            for (i in 1 until xy.size) lineTo(xy[i].first, xy[i].second)
        }
        c.drawPath(path, p)
    } else {
        for (i in 1 until xy.size) {
            p.color = paceColor(pts[i].getOrElse(2) { 0.0 }, lo, hi).toArgb()
            c.drawLine(xy[i - 1].first, xy[i - 1].second, xy[i].first, xy[i].second, p)
        }
    }
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    dot.color = (color ?: K.ink).toArgb()
    c.drawCircle(xy.first().first, xy.first().second, stroke * 1.3f, dot)
    dot.color = (color ?: K.good).toArgb()
    c.drawCircle(xy.last().first, xy.last().second, stroke * 1.3f, dot)
}

/**
 * Many routes on one shared projection, each at low alpha, so streets that are run often build up
 * brighter than one-off detours.
 */
fun drawHeatmap(c: Canvas, left: Float, top: Float, w: Float, h: Float, routes: List<List<RoutePoint>>, stroke: Float, color: Color) {
    val all = routes.flatten()
    if (all.size < 2) return
    val pad = stroke * 2
    val xy = project(all, w - pad * 2, h - pad * 2)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color.copy(alpha = (1.4f / kotlin.math.sqrt(routes.size.toFloat())).coerceIn(0.3f, 1f)).toArgb()
    }
    var i = 0
    for (r in routes) {
        val path = android.graphics.Path()
        r.indices.forEach { j ->
            val (x, y) = xy[i + j]
            if (j == 0) path.moveTo(left + pad + x, top + pad + y) else path.lineTo(left + pad + x, top + pad + y)
        }
        c.drawPath(path, p)
        i += r.size
    }
}
