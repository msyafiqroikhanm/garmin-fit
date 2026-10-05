package dev.garminfit.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GColor
import dev.garminfit.EXTRA_ACTIVITY
import dev.garminfit.EXTRA_TAB
import dev.garminfit.MainActivity
import dev.garminfit.Repo
import dev.garminfit.Tab
import dev.garminfit.Today
import dev.garminfit.activityType
import dev.garminfit.dayLabel
import dev.garminfit.duration
import dev.garminfit.hrvStatus
import dev.garminfit.int
import dev.garminfit.km
import dev.garminfit.pace
import dev.garminfit.readinessStatus
import dev.garminfit.sleep
import dev.garminfit.ui.K
import dev.garminfit.ui.Status
import dev.garminfit.ui.color
import java.time.LocalDate
import java.util.Locale

// Kinetik palette from the app theme (dark only), so a widget reads like a card from the app.
private fun c(color: Color) = ColorProvider(color, color)
private val Bg = c(K.surface)
private val Ink = c(K.ink)
private val Muted = c(K.muted)
private val Accent = c(K.good)
private val Track = c(K.track)

private fun Status.provider(): GColor = c(color())

// Glance cannot load bundled fonts; the system condensed face is the nearest to Barlow Condensed.
private val Condensed = FontFamily("sans-serif-condensed")

private fun label(c: GColor = Muted) = TextStyle(color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium)
private fun value(size: Int = 22) = TextStyle(color = Ink, fontSize = size.sp, fontWeight = FontWeight.Bold, fontFamily = Condensed)
private val body = TextStyle(color = Muted, fontSize = 12.sp)

object Widgets {
    suspend fun updateAll(ctx: Context) {
        RecoveryWidget().updateAll(ctx)
        MorningWidget().updateAll(ctx)
        WeekWidget().updateAll(ctx)
        TrendWidget().updateAll(ctx)
        LastActivityWidget().updateAll(ctx)
    }
}

private fun open(ctx: Context, tab: Tab, activityId: Long? = null) = actionStartActivity(
    Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_TAB, tab.name)
        activityId?.let { putExtra(EXTRA_ACTIVITY, it) }
        data = android.net.Uri.parse("garminfit://${tab.name}/${activityId ?: ""}")
    },
)

@Composable
private fun Card(ctx: Context, tab: Tab, activityId: Long? = null, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().background(Bg).cornerRadius(16.dp).padding(14.dp).clickable(open(ctx, tab, activityId)),
    ) { content() }
}

@Composable
private fun Placeholder(t: Today?) {
    Text(
        when {
            t == null -> "Buka garmin-fit sekali untuk memuat data widget."
            else -> "Belum ada data dari Garmin."
        },
        style = body,
    )
}

private fun stale(t: Today): String? =
    t.latest?.date?.takeIf { it != LocalDate.now().toString() }?.let { "Data ${dayLabel(it)}" }

abstract class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val t = Repo.cachedToday(context)
        provideContent { Content(context, t) }
    }

    @Composable
    abstract fun Content(ctx: Context, t: Today?)
}

class RecoveryWidget : TodayWidget() {
    @Composable
    override fun Content(ctx: Context, t: Today?) = Card(ctx, Tab.TODAY) {
        val d = t?.latest
        if (d?.hrv == null) return@Card Placeholder(t)
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text("HRV semalam", style = label())
            Row(verticalAlignment = Alignment.Bottom) {
                Text(d.hrv.int(), style = value(34))
                Text(" ms", style = body)
            }
            hrvStatus(d.hrvStatus)?.let { (l, s) -> Text(l, style = label(s.provider())) }
            Spacer(GlanceModifier.height(8.dp))
            if (d.readiness != null) {
                Text("Readiness", style = label())
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(d.readiness.int(), style = value(18))
                    readinessStatus(d.readinessLevel)?.let { (l, s) -> Text("  $l", style = label(s.provider())) }
                }
            }
        }
    }
}

class MorningWidget : TodayWidget() {
    @Composable
    override fun Content(ctx: Context, t: Today?) = Card(ctx, Tab.TODAY) {
        val d = t?.latest ?: return@Card Placeholder(t)
        Column(GlanceModifier.fillMaxSize()) {
            Text(stale(t) ?: "Pagi ini", style = label())
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Stat("HRV", d.hrv.int(), hrvStatus(d.hrvStatus)?.let { it.first to it.second.provider() }, GlanceModifier.defaultWeight())
                Stat("RHR", d.rhr.int(), null, GlanceModifier.defaultWeight())
                Stat("Tidur", d.sleepScore.int(), sleep(d.sleepS) to Muted, GlanceModifier.defaultWeight())
                Stat("Battery", d.bbHigh.int(), d.bbLow?.let { "min ${it.int()}" to Muted }, GlanceModifier.defaultWeight())
            }
            Spacer(GlanceModifier.defaultWeight())
            val flag = t.flags.firstOrNull()
            if (flag != null) {
                val s = if (flag.level == "good") Status.GOOD else Status.FAIR
                Text(flag.text, style = label(s.provider()), maxLines = 2)
            } else if (t.baseline.hrv != null) {
                Text("HRV dan RHR dalam rentang normal", style = body, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Stat(title: String, v: String, sub: Pair<String, GColor>?, modifier: GlanceModifier) {
    Column(modifier) {
        Text(title, style = label())
        Text(v, style = value(22))
        sub?.let { Text(it.first, style = label(it.second), maxLines = 2) }
    }
}

class WeekWidget : TodayWidget() {
    @Composable
    override fun Content(ctx: Context, t: Today?) = Card(ctx, Tab.ACTIVITIES) {
        if (t == null) return@Card Placeholder(null)
        val w = t.week
        Column(GlanceModifier.fillMaxSize()) {
            Text("7 hari terakhir", style = label())
            Spacer(GlanceModifier.height(6.dp))
            if (w.count == 0) {
                Text("Belum ada aktivitas minggu ini.", style = body)
            } else {
                Row(GlanceModifier.fillMaxWidth()) {
                    Stat("Sesi", w.count.toString(), null, GlanceModifier.defaultWeight())
                    Stat("Jarak", "${km(w.distanceM)} km", null, GlanceModifier.defaultWeight())
                    Stat("Durasi", duration(w.durationS), null, GlanceModifier.defaultWeight())
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            val active = w.dates.toSet()
            // Glance caps a Row at 10 children: 7 day columns + label, spacing via padding.
            Row(verticalAlignment = Alignment.CenterVertically) {
                (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }.forEach { day ->
                    Column(GlanceModifier.padding(end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(GlanceModifier.size(10.dp).cornerRadius(5.dp).background(if (day.toString() in active) Accent else Track)) {}
                        Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("id")), style = body)
                    }
                }
                Text("${w.activeDays} hari aktif", style = body)
            }
        }
    }
}

class TrendWidget : TodayWidget() {
    @Composable
    override fun Content(ctx: Context, t: Today?) = Card(ctx, Tab.TRENDS) {
        if (t == null || t.spark.hrv.all { it == null } && t.spark.rhr.all { it == null }) return@Card Placeholder(t)
        val line = K.good
        val muted = K.muted
        Column(GlanceModifier.fillMaxSize()) {
            Text("Tren 7 hari", style = label())
            Spacer(GlanceModifier.height(6.dp))
            SparkRow("HRV", "ms", t.spark.hrv, line)
            Spacer(GlanceModifier.height(8.dp))
            SparkRow("RHR", "bpm", t.spark.rhr, muted)
        }
    }
}

@Composable
private fun SparkRow(title: String, unit: String, values: List<Double?>, color: Color) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.width(72.dp)) {
            Text(title, style = label())
            Row(verticalAlignment = Alignment.Bottom) {
                Text(values.lastOrNull { it != null }.int(), style = value(20))
                Text(" $unit", style = body)
            }
        }
        Image(
            ImageProvider(sparkline(values, color)),
            contentDescription = "Grafik $title 7 hari",
            modifier = GlanceModifier.defaultWeight().height(40.dp),
        )
    }
}

private fun sparkline(values: List<Double?>, color: Color, w: Int = 480, h: Int = 120): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val present = values.filterNotNull()
    if (present.size < 2) return bmp
    val lo = present.min()
    val span = (present.max() - lo).coerceAtLeast(1.0)
    val pad = 12f
    val step = (w - 2 * pad) / (values.size - 1).coerceAtLeast(1)
    fun y(v: Double) = (h - pad - ((v - lo) / span) * (h - 2 * pad)).toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb(); style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    val path = Path()
    var started = false
    var lastX = 0f
    var lastY = 0f
    values.forEachIndexed { i, v ->
        if (v == null) { started = false; return@forEachIndexed }
        lastX = pad + i * step
        lastY = y(v)
        if (started) path.lineTo(lastX, lastY) else path.moveTo(lastX, lastY)
        started = true
    }
    Canvas(bmp).apply {
        drawPath(path, paint)
        drawCircle(lastX, lastY, 9f, paint.apply { style = Paint.Style.FILL })
    }
    return bmp
}

class LastActivityWidget : TodayWidget() {
    @Composable
    override fun Content(ctx: Context, t: Today?) {
        val a = t?.lastActivity
        Card(ctx, Tab.ACTIVITIES, a?.id) {
            if (a == null) return@Card Placeholder(t)
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Text("${activityType(a.type)}, ${dayLabel(a.date)}", style = label(), maxLines = 1)
                Text(
                    listOfNotNull(
                        a.distanceM?.takeIf { it > 0 }?.let { "${km(it)} km" },
                        duration(a.durationS),
                        a.avgSpeed?.takeIf { (a.distanceM ?: 0.0) > 0 }?.let { "${pace(it)}/km" },
                        a.avgHr?.let { "HR ${it.int()}" },
                    ).joinToString("  ·  "),
                    style = value(16),
                    maxLines = 1,
                )
            }
        }
    }
}

class RecoveryReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RecoveryWidget() }
class MorningReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MorningWidget() }
class WeekReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WeekWidget() }
class TrendReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TrendWidget() }
class LastActivityReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LastActivityWidget() }
