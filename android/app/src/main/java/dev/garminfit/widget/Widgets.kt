package dev.garminfit.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.DashPathEffect
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GColor
import dev.garminfit.Daily
import dev.garminfit.EXTRA_ACTIVITY
import dev.garminfit.EXTRA_PLAN
import dev.garminfit.EXTRA_TAB
import dev.garminfit.MainActivity
import dev.garminfit.Repo
import dev.garminfit.Tab
import dev.garminfit.Today
import dev.garminfit.PlanSession
import dev.garminfit.PlanToday
import dev.garminfit.PlanView
import dev.garminfit.R
import kotlin.math.roundToInt
import dev.garminfit.ui.planTarget
import dev.garminfit.ui.statusColor
import dev.garminfit.ui.statusIcon
import dev.garminfit.targetText
import kotlinx.coroutines.flow.first
import dev.garminfit.activityIcon
import dev.garminfit.activityType
import dev.garminfit.dayLabel
import dev.garminfit.dayMonth
import dev.garminfit.duration
import dev.garminfit.flagText
import dev.garminfit.hrvStatus
import dev.garminfit.int
import dev.garminfit.km
import dev.garminfit.pace
import dev.garminfit.readinessVerdict
import dev.garminfit.todayAdvice
import dev.garminfit.sleep
import dev.garminfit.syncTime
import dev.garminfit.tr
import dev.garminfit.ui.K
import dev.garminfit.ui.Status
import dev.garminfit.ui.color
import dev.garminfit.weekdayShort
import java.time.LocalDate

// Kinetik palette from the app theme (dark only), so a widget reads like a card from the app.
private fun c(color: Color) = ColorProvider(color, color)
private val Hairline = ImageProvider(R.drawable.bg_widget)
private val Ink = c(K.ink)
private val Muted = c(K.muted)
private val Accent = c(K.good)
private val AccentDim = c(K.good.copy(alpha = 0.45f))
private val Track = c(K.track)

private fun Status.provider(): GColor = c(color())

// Glance cannot load bundled fonts; the system condensed face is the nearest to Barlow Condensed.
private val Condensed = FontFamily("sans-serif-condensed")

private fun label(c: GColor = Muted) = TextStyle(color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium)
private val caps = TextStyle(color = Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = Condensed)

/** The app's Label: condensed caps in muted. Glance has no letter spacing, so the tracking is lost. */
@Composable
private fun Caps(text: String, modifier: GlanceModifier = GlanceModifier) = Text(text.uppercase(), style = caps, maxLines = 1, modifier = modifier)
private fun value(size: Int = 22, c: GColor = Ink) = TextStyle(color = c, fontSize = size.sp, fontWeight = FontWeight.Bold, fontFamily = Condensed)
private val body = TextStyle(color = Muted, fontSize = 12.sp)

/**
 * Responsive breakpoints shared by every widget. Glance renders the closest size that fits,
 * so content is added as the widget grows instead of being squeezed into the smallest layout.
 */
private val SIZES = setOf(
    DpSize(110.dp, 110.dp), DpSize(110.dp, 200.dp),
    DpSize(250.dp, 50.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 170.dp), DpSize(250.dp, 200.dp), DpSize(250.dp, 230.dp),
)

object Widgets {
    suspend fun updateAll(ctx: Context) {
        RecoveryWidget().updateAll(ctx)
        MorningWidget().updateAll(ctx)
        WeekWidget().updateAll(ctx)
        TrendWidget().updateAll(ctx)
        LastActivityWidget().updateAll(ctx)
        PlanWidget().updateAll(ctx)
    }
}

private fun open(ctx: Context, tab: Tab, activityId: Long? = null, plan: Boolean = false) = actionStartActivity(
    Intent(ctx, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_TAB, tab.name)
        activityId?.let { putExtra(EXTRA_ACTIVITY, it) }
        if (plan) putExtra(EXTRA_PLAN, true)
        // distinct per target, or Android hands every widget the same PendingIntent
        data = android.net.Uri.parse("garminfit://${tab.name}/${activityId ?: ""}${if (plan) "plan" else ""}")
    },
)

@Composable
private fun Card(ctx: Context, tab: Tab, activityId: Long? = null, plan: Boolean = false, content: @Composable () -> Unit) {
    // the app's card: surface fill with a 1dp hairline edge, since a fill step alone reads as one slab on a dark wallpaper
    Box(
        GlanceModifier.fillMaxSize().background(Hairline).cornerRadius(24.dp).padding(14.dp).clickable(open(ctx, tab, activityId, plan)),
    ) { content() }
}

@Composable
private fun Placeholder(t: Today?) {
    Text(
        when {
            t == null -> tr("Open garmin-fit once to load widget data.", "Buka garmin-fit sekali untuk memuat data widget.")
            else -> tr("No data from Garmin yet.", "Belum ada data dari Garmin.")
        },
        style = body,
    )
}

/** Data from an earlier day says so; today's data shows when it was last synced. */
private fun freshness(d: Daily?): String? {
    d ?: return null
    if (d.date != LocalDate.now().toString()) return tr("Data from ${dayLabel(d.date)}", "Data ${dayLabel(d.date)}")
    return syncTime(d.syncedAt)?.let { "Sync $it" }
}

/** Title on the left, freshness on the right, so stale numbers are never read as today's. */
@Composable
private fun Header(title: String, d: Daily?, right: String? = freshness(d)) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Caps(title, GlanceModifier.defaultWeight().padding(end = 8.dp))
        right?.let { Text(it, style = body, maxLines = 1) }
    }
}

@Composable
private fun Steps(d: Daily) {
    val steps = d.steps ?: return
    val goal = d.stepGoal?.takeIf { it > 0 } ?: return
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Caps(tr("Steps", "Langkah"))
            Spacer(GlanceModifier.defaultWeight())
            Text(steps.int(), style = value(14))
            Text(" / ${goal.int()}", style = body)
        }
        Spacer(GlanceModifier.height(4.dp))
        LinearProgressIndicator(
            progress = (steps / goal).toFloat().coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = if (steps >= goal) Accent else Ink,
            backgroundColor = Track,
        )
    }
}

/** Today's planned session in one line, with its score once something is done; tapping it opens the plan. */
@Composable
private fun PlanLine(ctx: Context, p: PlanSession) {
    Row(GlanceModifier.fillMaxWidth().clickable(open(ctx, Tab.TRAINING, plan = true)), verticalAlignment = Alignment.Bottom) {
        Caps("Plan ")
        Text(listOf(p.title.text, planTarget(p)).filter { it.isNotEmpty() }.joinToString(" · "), style = value(13), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        p.pct?.let { Text(" ${(it * 100).roundToInt()}%", style = value(13, c(statusColor(p.status)))) }
    }
}

abstract class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = Repo.cachedToday(context)
        provideContent {
            // collected rather than read once: Glance keeps a session alive after an update and only recomposes it
            val t by remember { Repo.todayFlow(context) }.collectAsState(first)
            Content(context, t)
        }
    }

    @Composable
    abstract fun Content(ctx: Context, t: Today?)
}

/** Readiness first: the number, Garmin's call as one word, and what that means for today. */
class RecoveryWidget : TodayWidget() {
    @Composable
    override fun Content(ctx: Context, t: Today?) = Card(ctx, Tab.TODAY) {
        val d = t?.latest
        val verdict = d?.let { readinessVerdict(it.readinessLevel, it.readiness, it.recoveryMin) }
        if (d == null) return@Card Placeholder(t)
        if (verdict == null) return@Card Text(tr("No readiness score yet today.", "Belum ada skor readiness hari ini."), style = body)
        val tall = LocalSize.current.height >= 190.dp
        Column(GlanceModifier.fillMaxSize()) {
            // a 2-wide header fits the time but not the word "Sync"
            val narrow = LocalSize.current.width < 200.dp
            Header("Readiness", d, right = freshness(d)?.let { if (narrow) it.removePrefix("Sync ") else it })
            Spacer(GlanceModifier.defaultWeight())
            // stacked, since a 2-wide widget has no room for the number and the word side by side
            Text(d.readiness.int(), style = value(44))
            Text(verdict.word.uppercase(), style = value(20, verdict.status.provider()), maxLines = 1)
            Text(verdict.tagline, style = body, maxLines = 1)
            // one child for the tall extras: Glance caps a Column at 10 and HRV plus the plan line passed it
            if (tall) Column(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
                d.hrv?.let { hrv ->
                    val status = hrvStatus(d.hrvStatus)
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Caps("HRV ")
                        Text(hrv.int(), style = value(16))
                        Spacer(GlanceModifier.defaultWeight())
                        status?.let { (l, s) -> Text(l, style = label(s.provider()), maxLines = 1) }
                    }
                    HrvBand(d, hrv, status?.second)
                }
                t.plan?.session?.let { Spacer(GlanceModifier.height(8.dp)); PlanLine(ctx, it) }
            }
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

class MorningWidget : TodayWidget() {
    @Composable
    override fun Content(ctx: Context, t: Today?) = Card(ctx, Tab.TODAY) {
        val d = t?.latest ?: return@Card Placeholder(t)
        val h = LocalSize.current.height
        val verdict = readinessVerdict(d.readinessLevel, d.readiness, d.recoveryMin)
        Column(GlanceModifier.fillMaxSize()) {
            Header(tr("Today", "Hari ini"), d)
            Spacer(GlanceModifier.height(6.dp))
            if (verdict != null) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(verdict.word.uppercase(), style = value(26, verdict.status.provider()))
                    Text("  ${verdict.tagline}", style = body, maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    d.readiness?.let { Text(it.int(), style = value(26)) }
                }
            }
            if (h >= 160.dp || verdict == null) {
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Stat("HRV", d.hrv.int(), hrvStatus(d.hrvStatus)?.let { it.first to it.second.provider() }, GlanceModifier.defaultWeight())
                    Stat("RHR", d.rhr.int(), null, GlanceModifier.defaultWeight())
                    Stat(tr("Sleep", "Tidur"), d.sleepScore.int(), sleep(d.sleepS) to Muted, GlanceModifier.defaultWeight())
                    Stat("Battery", (d.bbNow ?: d.bbHigh).int(), (if (d.bbNow != null) tr("now", "sekarang") else tr("peak", "tertinggi")) to Muted, GlanceModifier.defaultWeight())
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            // grouped: Glance caps a Column at 10 children and the tallest size reaches that
            t.plan?.session?.takeIf { h >= 150.dp }?.let { Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) { PlanLine(ctx, it) } }
            Steps(d)
            if (h >= 220.dp) {
                val flag = t.flags.firstOrNull()
                Spacer(GlanceModifier.height(8.dp))
                if (flag != null) {
                    val s = if (flag.level == "good") Status.GOOD else Status.FAIR
                    Text(flagText(flag, t), style = label(s.provider()), maxLines = 2)
                } else if (verdict != null) {
                    Text(todayAdvice(verdict, t.plan).first, style = body, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun Stat(title: String, v: String, sub: Pair<String, GColor>?, modifier: GlanceModifier, color: GColor = Ink) {
    Column(modifier) {
        Caps(title)
        Text(v, style = value(22, color))
        sub?.let { Text(it.first, style = label(it.second), maxLines = 1) }
    }
}

class WeekWidget : TodayWidget() {
    @Composable
    override fun Content(ctx: Context, t: Today?) = Card(ctx, Tab.ACTIVITIES) {
        if (t == null) return@Card Placeholder(null)
        val w = t.week
        val h = LocalSize.current.height
        Column(GlanceModifier.fillMaxSize()) {
            Header(tr("This week", "Minggu ini"), null, right = tr(if (w.activeDays == 1) "1 active day" else "${w.activeDays} active days", "${w.activeDays} hari aktif"))
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Stat(tr("Sessions", "Sesi"), w.count.toString(), null, GlanceModifier.defaultWeight())
                if (w.distanceM > 0) Stat(tr("Distance", "Jarak"), "${km(w.distanceM)} km", null, GlanceModifier.defaultWeight())
                else Stat("Load", w.load.int(), null, GlanceModifier.defaultWeight())
                Stat(tr("Time", "Durasi"), duration(w.durationS), null, GlanceModifier.defaultWeight())
                t.plan?.week?.takeIf { it.planned > 0 }?.let {
                    Stat("Plan", "${it.done}/${it.planned}", null, GlanceModifier.defaultWeight(), if (it.done >= it.planned) Accent else Ink)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            if (h >= 140.dp) LoadBars(t, (h - 150.dp).coerceIn(20.dp, 72.dp))
        }
    }
}

/** Same bars as the Today screen: height is that day's training load, today in full volt, rest days a flat track. */
@Composable
private fun LoadBars(t: Today, maxBar: Dp) {
    val days = t.week.dailyLoad.takeIf { it.size == 7 } ?: return
    val top = days.maxOf { it.load }.coerceAtLeast(1.0)
    val today = LocalDate.now().toString()
    // Glance caps a Row at 10 children: 7 day columns, spacing via padding.
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        days.forEach { d ->
            Column(GlanceModifier.defaultWeight().padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val bar = if (d.load > 0) (maxBar * (d.load / top).toFloat()).coerceAtLeast(6.dp) else 4.dp
                Box(
                    GlanceModifier.fillMaxWidth().height(bar).cornerRadius(4.dp)
                        .background(if (d.load <= 0) Track else if (d.date == today) Accent else AccentDim),
                ) {}
                Spacer(GlanceModifier.height(4.dp))
                Text(weekdayShort(d.date).take(2), style = if (d.date == today) label(Ink) else body, maxLines = 1)
            }
        }
    }
}

class TrendWidget : TodayWidget() {
    @Composable
    override fun Content(ctx: Context, t: Today?) = Card(ctx, Tab.TRENDS) {
        if (t == null || t.spark.hrv.all { it == null } && t.spark.rhr.all { it == null }) return@Card Placeholder(t)
        val d = t.latest
        val both = LocalSize.current.height >= 140.dp
        Column(GlanceModifier.fillMaxSize()) {
            Header(tr("7 day trend", "Tren 7 hari"), d)
            Spacer(GlanceModifier.defaultWeight())
            val hrvColor = hrvStatus(d?.hrvStatus)?.second?.color() ?: K.good
            val normal = d?.hrvLow?.let { lo -> d.hrvHigh?.let { hi -> tr("normal ${lo.int()}–${hi.int()}", "normal ${lo.int()}–${hi.int()}") } }
            val band = d?.hrvLow?.let { lo -> d.hrvHigh?.let { hi -> lo to hi } }
            SparkRow("HRV", "ms", t.spark.hrv, hrvColor, normal, band = band)
            if (both) {
                Spacer(GlanceModifier.height(8.dp))
                val mean = t.baseline.rhr?.mean
                val avg = mean?.let { tr("30d avg ${it.int()}", "rata-rata 30h ${it.int()}") }
                SparkRow("RHR", "bpm", t.spark.rhr, K.ink, avg, ref = mean)
            }
            Spacer(GlanceModifier.defaultWeight())
        }
    }
}

/** Today's HRV on the personal normal range, drawn like the app's FactorBar. */
@Composable
private fun HrvBand(d: Daily, hrv: Double, status: Status?) {
    val lo = d.hrvLow ?: return
    val hi = d.hrvHigh ?: return
    Spacer(GlanceModifier.height(4.dp))
    Image(
        ImageProvider(bandBar(hrv, lo - 13, hi + 13, lo, hi, (status ?: Status.GOOD).color())),
        contentDescription = tr("HRV ${hrv.int()}, normal ${lo.int()} to ${hi.int()}", "HRV ${hrv.int()}, normal ${lo.int()} sampai ${hi.int()}"),
        modifier = GlanceModifier.fillMaxWidth().height(14.dp),
    )
}

private fun bandBar(value: Double, min: Double, max: Double, lo: Double, hi: Double, dot: Color, w: Int = 600, h: Int = 42): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val r = h / 2f
    fun x(v: Double) = (r + ((v - min) / (max - min)).coerceIn(0.0, 1.0) * (w - 2 * r)).toFloat()
    val bar = h * 0.43f
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    Canvas(bmp).apply {
        p.color = K.track.toArgb()
        drawRoundRect(0f, r - bar / 2, w.toFloat(), r + bar / 2, bar / 2, bar / 2, p)
        p.color = K.good.copy(alpha = 0.3f).toArgb()
        drawRoundRect(x(lo), r - bar / 2, x(hi), r + bar / 2, bar / 2, bar / 2, p)
        p.color = K.surface.toArgb()
        drawCircle(x(value), r, r, p)
        p.color = dot.toArgb()
        drawCircle(x(value), r, r * 0.58f, p)
    }
    return bmp
}

@Composable
private fun SparkRow(title: String, unit: String, values: List<Double?>, color: Color, note: String?, band: Pair<Double, Double>? = null, ref: Double? = null) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.width(96.dp)) {
            Caps(title)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(values.lastOrNull { it != null }.int(), style = value(20))
                Text(" $unit", style = body)
            }
            note?.let { Text(it, style = body, maxLines = 1) }
        }
        Image(
            ImageProvider(sparkline(values, color, band, ref)),
            contentDescription = tr("$title chart, 7 days", "Grafik $title 7 hari"),
            modifier = GlanceModifier.defaultWeight().height(44.dp),
        )
    }
}

/** [band] is the normal range (volt at 12%, as in Tren), [ref] a dashed reference line such as the 30 day average. */
private fun sparkline(values: List<Double?>, color: Color, band: Pair<Double, Double>? = null, ref: Double? = null, w: Int = 480, h: Int = 132): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val present = values.filterNotNull()
    if (present.size < 2) return bmp
    val scale = present + listOfNotNull(band?.first, band?.second, ref)
    val lo = scale.min()
    val span = (scale.max() - lo).coerceAtLeast(1.0)
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
        band?.let { (a, b) ->
            drawRect(0f, y(b), w.toFloat(), y(a), Paint().apply { this.color = K.good.copy(alpha = 0.12f).toArgb() })
        }
        ref?.let {
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = K.muted.toArgb(); style = Paint.Style.STROKE; strokeWidth = 3f; pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            drawLine(0f, y(it), w.toFloat(), y(it), dash)
        }
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
            val hasDistance = (a.distanceM ?: 0.0) > 0
            // date first, so a long race name is what gets cut
            val title = "${dayMonth(a.date)} · ${a.name ?: activityType(a.type)}"
            if (LocalSize.current.height < 100.dp) {
                Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    TypeIcon(a.type, 40.dp)
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Caps(title)
                        Text(
                            listOfNotNull(
                                a.distanceM?.takeIf { hasDistance }?.let { "${km(it)} km" },
                                duration(a.durationS),
                                a.avgSpeed?.takeIf { hasDistance }?.let { "${pace(it)}/km" },
                                a.avgHr?.takeIf { !hasDistance }?.let { "HR ${it.int()}" },
                            ).joinToString(" · "),
                            style = value(18),
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Column(GlanceModifier.fillMaxSize()) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TypeIcon(a.type, 28.dp)
                        Spacer(GlanceModifier.width(8.dp))
                        Caps(title, GlanceModifier.defaultWeight())
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    // the hero is distance when there is one, else time (strength, yoga)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(if (hasDistance) km(a.distanceM) else duration(a.durationS), style = value(36))
                        if (hasDistance) Text(" km", style = body)
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        if (hasDistance) {
                            Stat(tr("Time", "Durasi"), duration(a.durationS), null, GlanceModifier.defaultWeight())
                            Stat("Pace", pace(a.avgSpeed), "/km" to Muted, GlanceModifier.defaultWeight())
                        } else {
                            Stat(tr("Calories", "Kalori"), a.calories.int(), tr("kcal", "kkal") to Muted, GlanceModifier.defaultWeight())
                        }
                        Stat("HR", a.avgHr.int(), "bpm" to Muted, GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeIcon(type: String?, size: Dp) {
    Box(GlanceModifier.size(size).background(ImageProvider(R.drawable.bg_icon_circle)), contentAlignment = Alignment.Center) {
        Image(
            ImageProvider(activityIcon(type)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Ink),
            modifier = GlanceModifier.size(size * 0.55f),
        )
    }
}

/**
 * What the plan says for today and how much of it is done, then the week. A tap opens the plan; the sync control
 * pulls Garmin right away instead of waiting for the 2 hour refresh.
 */
class PlanWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = Repo.cachedToday(context)
        val plan = Repo.planFlow(context).first()
        val syncingNow = Repo.widgetSyncingFlow(context).first()
        provideContent {
            val t by remember { Repo.todayFlow(context) }.collectAsState(today)
            val v by remember { Repo.planFlow(context) }.collectAsState(plan)
            val syncing by remember { Repo.widgetSyncingFlow(context) }.collectAsState(syncingNow)
            Card(context, Tab.TRAINING, plan = true) {
                if (t == null) Placeholder(null) else PlanBody(context, t!!, v, syncing)
            }
        }
    }
}

@Composable
private fun PlanBody(ctx: Context, t: Today, v: PlanView?, syncing: Boolean) {
    // Responsive mode lays out for the size bucket, not the real height, so each tier must fit its bucket:
    // <100 one row; <170 row and week count; <200 header, row and week strip; 200+ adds the bars and count.
    val h = LocalSize.current.height
    val compact = h < 100.dp
    val header = h >= 170.dp
    val full = h >= 200.dp
    val p = t.plan
    val s = p?.session
    val color = s?.let { c(statusColor(it.status)) } ?: Muted
    val (title, sub) = when {
        p == null -> tr("No plan yet", "Belum ada plan") to tr("Tap to open Training", "Ketuk untuk buka Latihan")
        s == null -> tr("Nothing planned", "Tidak ada sesi") to tr("Rest or move easy", "Istirahat atau gerak santai")
        else -> s.title.text to planTarget(s).ifEmpty { null }
    }
    val days = p?.let { weekDays(it, v) }
    Column(GlanceModifier.fillMaxSize()) {
        if (header) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Caps(tr("Today's plan", "Plan hari ini"), GlanceModifier.defaultWeight().padding(end = 8.dp))
                SyncControl(t.latest, syncing, showTime = true)
            }
            Spacer(GlanceModifier.defaultWeight())
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(s?.let { statusIcon(it.status) } ?: if (p == null) R.drawable.ic_event_available else R.drawable.ic_bedtime),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color),
                modifier = GlanceModifier.size(if (compact) 24.dp else 30.dp),
            )
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(title, style = value(if (compact) 18 else 24), maxLines = 1)
                sub?.let { Text(it, style = body, maxLines = 1) }
            }
            s?.pct?.let { Text(" ${(it * 100).roundToInt()}%", style = value(if (compact) 22 else 32, color)) }
            if (!header) SyncControl(t.latest, syncing, showTime = false)
        }
        if (compact) return@Column
        // adjust line and session bar each sit in one child, so the column stays under Glance's 10
        p?.adjust?.let { a -> targetText(a.target) }?.let {
            Text(tr("Adjusted for recovery: $it", "Disesuaikan karena belum pulih: $it"), style = label(Status.FAIR.provider()), maxLines = 1, modifier = GlanceModifier.padding(top = 4.dp))
        }
        if (full) s?.pct?.let {
            Column(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) { Bar(it.toFloat(), color) }
        }
        Spacer(GlanceModifier.defaultWeight())
        if (header && days != null) {
            WeekStrip(days)
            if (!full) return@Column
            Spacer(GlanceModifier.height(8.dp))
        }
        val w = p?.week?.takeIf { it.planned > 0 } ?: return@Column
        // done of planned, not the week's score: "2 of 5" next to "100%" read as a contradiction
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(tr("This week", "Minggu ini"), style = body, maxLines = 1, modifier = GlanceModifier.defaultWeight())
            Text("${w.done}/${w.planned}", style = value(16, if (w.done >= w.planned) Accent else Ink))
            Text(tr(" done", " selesai"), style = body, maxLines = 1)
        }
        if (!header) {
            Spacer(GlanceModifier.height(4.dp))
            Bar(w.done.toFloat() / w.planned, Accent)
        }
    }
}

@Composable
private fun Bar(progress: Float, color: GColor) = LinearProgressIndicator(
    progress = progress.coerceIn(0f, 1f),
    modifier = GlanceModifier.fillMaxWidth().height(4.dp),
    color = color,
    backgroundColor = Track,
)

/** Monday to Sunday of this week, each with its session; today's comes from /today since it is the freshest. */
private fun weekDays(p: PlanToday, v: PlanView?): List<Pair<String, PlanSession?>>? {
    v ?: return null
    val now = LocalDate.now()
    val monday = now.minusDays(now.dayOfWeek.value - 1L)
    return (0..6).map { i ->
        val d = monday.plusDays(i.toLong()).toString()
        // one mark per day; on a run + strength day the run's mark shows, as in the app
        val s = p.session?.takeIf { it.date == d }
            ?: v.sessions.filter { it.date == d }.let { l -> l.firstOrNull { it.kind == "run" } ?: l.firstOrNull() }
        d to s
    }
}

/** The app's week strip: weekday over a status mark, today on the base fill. */
@Composable
private fun WeekStrip(days: List<Pair<String, PlanSession?>>) {
    val today = LocalDate.now().toString()
    Row(GlanceModifier.fillMaxWidth()) {
        days.forEach { (d, s) ->
            val isToday = d == today
            Column(
                GlanceModifier.defaultWeight().padding(vertical = 6.dp)
                    .let { if (isToday) it.background(ImageProvider(R.drawable.bg_widget_today)) else it },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(weekdayShort(d).take(2), style = if (isToday) label(Ink) else body, maxLines = 1)
                Spacer(GlanceModifier.height(4.dp))
                if (s == null) {
                    Box(GlanceModifier.size(18.dp), contentAlignment = Alignment.Center) {
                        Box(GlanceModifier.size(6.dp).background(ImageProvider(R.drawable.bg_icon_circle))) {}
                    }
                } else {
                    Image(
                        ImageProvider(statusIcon(s.status)),
                        contentDescription = "${weekdayShort(d)}: ${s.title.text}, ${dev.garminfit.ui.statusLabel(s)}",
                        colorFilter = ColorFilter.tint(c(statusColor(s.status))),
                        modifier = GlanceModifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Last sync time with a refresh icon; tapping it pulls Garmin now. Reads "Syncing" until the worker finishes. */
@Composable
private fun SyncControl(d: Daily?, syncing: Boolean, showTime: Boolean) {
    val text = if (syncing) tr("Syncing", "Menyinkron") else if (showTime) freshness(d) else null
    Row(
        GlanceModifier.height(40.dp).padding(start = 8.dp).cornerRadius(20.dp)
            .clickable(actionRunCallback<SyncAction>())
            .semantics { contentDescription = tr("Sync with Garmin now", "Sinkron dengan Garmin sekarang") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        text?.let { Text(it, style = body, maxLines = 1) }
        if (!syncing) {
            Spacer(GlanceModifier.width(6.dp))
            Image(ImageProvider(R.drawable.ic_refresh), contentDescription = null, colorFilter = ColorFilter.tint(Ink), modifier = GlanceModifier.size(18.dp))
        }
    }
}

class SyncAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.setWidgetSyncing(context, true)
        PlanWidget().update(context, glanceId)
        RefreshWorker.now(context)
    }
}

class RecoveryReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = RecoveryWidget() }
class MorningReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = MorningWidget() }
class WeekReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = WeekWidget() }
class TrendReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TrendWidget() }
class LastActivityReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LastActivityWidget() }
class PlanReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PlanWidget() }
