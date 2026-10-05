package dev.garminfit.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.garminfit.MuscleLoad
import kotlin.math.min

/** Garmin muscle key → polygon regions in BodyData. */
private val REGIONS = mapOf(
    "CHEST" to setOf("CHEST"),
    "ABS" to setOf("ABS"),
    "OBLIQUES" to setOf("OBLIQUES"),
    "BICEPS" to setOf("BICEPS"),
    "TRICEPS" to setOf("TRICEPS"),
    "FOREARM" to setOf("FOREARM"),
    "SHOULDERS" to setOf("FRONT_DELTOIDS", "BACK_DELTOIDS"),
    "TRAPS" to setOf("TRAPEZIUS"),
    "LATS" to setOf("UPPER_BACK"),
    "LOWER_BACK" to setOf("LOWER_BACK"),
    "GLUTES" to setOf("GLUTEAL"),
    "HAMSTRINGS" to setOf("HAMSTRING"),
    "QUADS" to setOf("QUADRICEPS"),
    "CALVES" to setOf("CALVES", "LEFT_SOLEUS", "RIGHT_SOLEUS"),
    // the source art has no hip flexor or outer-hip shape, so these borrow the nearest region
    "ADDUCTORS" to setOf("ABDUCTORS", "ABDUCTOR"),
    "HIPS" to setOf("ABDUCTORS"),
    "ABDUCTORS" to setOf("GLUTEAL"),
)

/**
 * Fill color per polygon region: primary work scales the tint from 55% to full by set count,
 * secondary-only work gets a faint tint, untouched regions are left out.
 */
fun regionColors(muscles: Map<String, MuscleLoad>, tint: Color): Map<String, Color> {
    val maxPrimary = muscles.values.maxOfOrNull { it.primary }?.takeIf { it > 0 } ?: 1
    val out = mutableMapOf<String, Color>()
    for ((key, load) in muscles) {
        val c = when {
            load.primary > 0 -> tint.copy(alpha = 0.55f + 0.45f * load.primary / maxPrimary)
            load.secondary > 0 -> tint.copy(alpha = 0.28f)
            else -> continue
        }
        for (r in REGIONS[key].orEmpty()) {
            if ((out[r]?.alpha ?: 0f) < c.alpha) out[r] = c
        }
    }
    return out
}

/** Front and back figures side by side, fitted into the rect. Same function for the detail card and the share image. */
fun drawBody(c: Canvas, left: Float, top: Float, w: Float, h: Float, muscles: Map<String, MuscleLoad>, tint: Color, base: Color) {
    val colors = regionColors(muscles, tint)
    val gap = w * 0.06f
    // the back figure's feet reach y=220, past the source's nominal 200 viewBox
    val s = min((w - gap) / 200f, h / 220f)
    val ox = left + (w - (200f * s + gap)) / 2
    val oy = top + (h - 220f * s) / 2
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val path = Path()
    listOf(BODY_FRONT to ox, BODY_BACK to ox + 100f * s + gap).forEach { (polys, x0) ->
        for (p in polys) {
            path.reset()
            path.moveTo(x0 + p.points[0] * s, oy + p.points[1] * s)
            for (i in 2 until p.points.size step 2) path.lineTo(x0 + p.points[i] * s, oy + p.points[i + 1] * s)
            path.close()
            val fill = colors[p.region]
            // a worked region is laid over the base so faint tints still read as "this muscle"
            paint.color = base.toArgb()
            c.drawPath(path, paint)
            if (fill != null) {
                paint.color = fill.toArgb()
                c.drawPath(path, paint)
            }
        }
    }
}
