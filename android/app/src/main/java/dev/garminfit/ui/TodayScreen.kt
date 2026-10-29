package dev.garminfit.ui

import dev.garminfit.R

import dev.garminfit.tr
import dev.garminfit.isId
import dev.garminfit.flagText
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextOverflow
import dev.garminfit.activityIcon
import dev.garminfit.time
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.garminfit.Activity
import dev.garminfit.Daily
import dev.garminfit.Repo
import dev.garminfit.Summary
import dev.garminfit.Today
import dev.garminfit.Verdict
import dev.garminfit.activityType
import dev.garminfit.dayLabel
import dev.garminfit.dayMonth
import dev.garminfit.duration
import dev.garminfit.friendly
import dev.garminfit.greeting
import dev.garminfit.hrvFactor
import dev.garminfit.int
import dev.garminfit.km
import dev.garminfit.loadFactor
import dev.garminfit.one
import dev.garminfit.pace
import dev.garminfit.readinessStatus
import dev.garminfit.readinessVerdict
import dev.garminfit.rhrFactor
import dev.garminfit.sleep
import dev.garminfit.sleepFactor
import dev.garminfit.stressFactor
import dev.garminfit.syncTime
import dev.garminfit.two
import dev.garminfit.weekday
import dev.garminfit.weekdayShort
import dev.garminfit.widget.Widgets
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onOpenActivity: (Long) -> Unit, onOpenPlan: () -> Unit, onOpenAccount: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // widgets redraw on their own coroutine so the screen does not wait for five Glance renders
    val loader = rememberCachedLoad("today") { Repo.fetchToday(ctx).also { scope.launch { Widgets.updateAll(ctx) } } }
    var refreshing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }

    LoadView(loader, loadingText = tr("Loading today's data", "Memuat data hari ini")) { today ->
        fun refresh() {
            if (refreshing) return
            scope.launch {
                refreshing = true
                syncError = null
                if (today.role == "owner") {
                    runCatching { Repo.api(ctx).sync() }.onFailure { syncError = it.friendly() }
                }
                loader.reload()
                refreshing = false
            }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = ::refresh, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val latest = today.latest
                // a run done today is the first thing to check after it, so it jumps above the readiness hero
                val activityToday = today.lastActivity?.takeIf { it.date.take(10) == LocalDate.now().toString() }
                Header(today, onSync = ::refresh, onOpenAccount = onOpenAccount)
                syncError?.let { Text(it, Modifier.padding(horizontal = 4.dp), style = T.bodyS, color = K.low) }
                activityToday?.let { TodayActivity(it, onOpenActivity) }
                if (latest == null) {
                    Text(
                        if (today.role == "owner") tr("No data yet. Pull down to sync from Garmin.", "Belum ada data. Tarik layar ke bawah untuk sync dari Garmin.")
                        else tr("No data yet. It shows up after the owner syncs.", "Belum ada data. Data muncul setelah pemilik melakukan sync."),
                        Modifier.padding(horizontal = 4.dp, vertical = 48.dp),
                        style = T.body,
                        color = K.muted,
                    )
                } else {
                    val verdict = readinessVerdict(latest.readinessLevel, latest.readiness, latest.recoveryMin)
                    if (verdict != null && latest.readiness != null) Hero(latest, verdict)
                    today.plan?.let { PlanTodayCard(it, onOpenPlan) }
                    Factors(latest, today)
                    today.flags.forEach { FlagBox(flagText(it, today), flagStatus(it.key, it.level)) }
                    Tiles(latest, verdict?.status ?: Status.GOOD)
                    WeekPanel(today)
                    if (activityToday == null) today.lastActivity?.let { LastActivity(it, onOpenActivity) }
                    today.summary?.let { SummaryCard(it) }
                }
            }
        }
    }
}

private fun flagStatus(key: String, level: String) = when {
    key == "rhr_high" || key == "load_spike" -> Status.LOW
    level == "good" -> Status.GOOD
    else -> Status.FAIR
}

@Composable
private fun Header(t: Today, onSync: () -> Unit, onOpenAccount: () -> Unit) {
    val now = LocalDate.now()
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(dayLabel(now.toString()))
                val name = t.name?.replaceFirstChar { it.uppercase() }
                Text(listOfNotNull(greeting(), name).joinToString(", "), style = T.greeting, color = K.ink)
            }
            val label = if (t.role == "owner") "Sync" else tr("Reload", "Muat ulang")
            Box(
                Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onSync),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.clip(CircleShape).background(K.surface).padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(listOfNotNull("Sync", syncTime(t.latest?.syncedAt)).joinToString(" "), style = T.caption, color = K.muted)
                    Glyph(R.drawable.ic_refresh, K.muted, 16.dp)
                }
            }
            Avatar(t.name, t.role == "owner", onOpenAccount, size = 36)
        }
        val latest = t.latest
        if (latest != null && latest.date != now.toString()) {
            Text(
                if (t.role == "owner") tr("Latest data is from ${dayLabel(latest.date)}. Pull down to sync.", "Data terakhir dari ${dayLabel(latest.date)}. Tarik ke bawah untuk sync.")
                else tr("Latest data is from ${dayLabel(latest.date)}.", "Data terakhir dari ${dayLabel(latest.date)}."),
                style = T.caption,
                color = K.muted,
            )
        }
    }
}

@Composable
private fun Hero(d: Daily, v: Verdict) {
    val c = v.status.color()
    Panel(Modifier.fillMaxWidth(), padding = 20.dp, gap = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ReadinessRing(d.readiness!!.roundToInt(), c)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(v.word.uppercase(), style = T.statusWord, color = c)
                Text(v.tagline, style = body(15.sp, FontWeight.SemiBold, 1.3f), color = K.ink)
                readinessStatus(d.readinessLevel)?.let { Text(tr("Garmin level: ${it.first}", "Level Garmin: ${it.first}"), style = T.bodyS, color = K.muted) }
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(K.base).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Label(tr("Today's training advice", "Saran latihan hari ini"), color = c)
            Text(v.advice, style = T.body, color = K.ink)
            ChipRow(v.chips)
        }
    }
}

private class Factor(
    val name: String, val value: String, val unit: String,
    val v: Double, val min: Double, val max: Double, val lo: Double, val hi: Double,
    val status: Pair<String, Status>, val note: String,
)

private fun factors(d: Daily, t: Today): List<Factor> = buildList {
    val hrv = d.hrv
    val lo = d.hrvLow
    val hi = d.hrvHigh
    if (hrv != null && lo != null && hi != null) {
        add(Factor(tr("Overnight HRV", "HRV semalam"), hrv.int(), "ms", hrv, lo - 13, hi + 13, lo, hi, hrvFactor(hrv, lo, hi), "normal ${lo.int()}–${hi.int()}"))
    }
    d.sleepScore?.let { add(Factor(tr("Sleep", "Tidur"), it.int(), tr("score", "skor"), it, 30.0, 100.0, 70.0, 100.0, sleepFactor(it), sleep(d.sleepS))) }
    val mean = t.baseline.rhr?.mean
    if (d.rhr != null && mean != null) {
        add(Factor("Resting HR", d.rhr.int(), "bpm", d.rhr, mean - 10, mean + 12, mean - 4, mean + 4, rhrFactor(d.rhr, mean), tr("avg ${mean.int()}", "rata-rata ${mean.int()}")))
    }
    t.baseline.load?.ratio?.let {
        add(Factor(tr("Acute : chronic load", "Beban akut : kronis"), it.two(), tr("ratio", "rasio"), it, 0.5, 1.8, 0.8, 1.3, loadFactor(it), tr("optimal 0.8–1.3", "optimal 0,8–1,3")))
    }
    // Today's stress is still accumulating, so the last complete day is the fair comparison.
    val today = LocalDate.now().toString()
    t.spark.date.indices.lastOrNull { i -> t.spark.date[i].let { it != null && it < today } && t.spark.stress.getOrNull(i) != null }?.let { i ->
        val date = t.spark.date[i]!!
        val s = t.spark.stress[i]!!
        val note = if (date == LocalDate.now().minusDays(1).toString()) tr("yesterday", "kemarin") else weekday(date).lowercase()
        add(Factor(tr("Stress", "Stres"), s.int(), "/100", s, 0.0, 100.0, 0.0, 35.0, stressFactor(s), note))
    }
}

@Composable
private fun Factors(d: Daily, t: Today) {
    val list = factors(d, t)
    if (list.isEmpty()) return
    Panel(Modifier.fillMaxWidth(), gap = 16.dp) {
        CardTitle(tr("Why this score?", "Kenapa skornya segini?"), icon = R.drawable.ic_monitor_heart)
        list.forEach { f ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(f.name, Modifier.weight(1f).alignByBaseline(), style = body(14.sp), color = K.body2)
                    Value(f.value, f.unit, num(22.sp, FontWeight.Bold), modifier = Modifier.alignByBaseline())
                }
                FactorBar(f.v, f.min, f.max, f.lo, f.hi, f.status.second, "${f.name} ${f.value} ${f.unit}, ${f.status.first}, ${f.note}")
                Row {
                    Text(f.status.first, Modifier.weight(1f), style = body(12.sp, FontWeight.SemiBold, 1.3f), color = f.status.second.color())
                    Text(f.note, style = T.caption, color = K.muted)
                }
            }
        }
    }
}

@Composable
private fun FlagBox(text: String, status: Status) {
    val c = status.color()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(c.copy(alpha = if (status == Status.LOW) 0.14f else 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).background(c, CircleShape))
        Text(text, style = body(14.sp), color = K.ink)
    }
}

@Composable
private fun Tiles(d: Daily, readiness: Status) {
    val bb = d.bbNow ?: d.bbHigh
    val hasSteps = d.steps != null
    if (bb != null || hasSteps) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (bb != null) Tile(Modifier.weight(1f).fillMaxHeight()) {
                Label("Body Battery")
                Value(bb.int(), if (d.bbNow != null) tr("now", "sekarang") else tr("peak", "tertinggi"), num(36.sp))
                val low = d.bbLow
                val high = d.bbHigh
                if (low != null && high != null) {
                    SpanBar((low / 100).toFloat(), (high / 100).toFloat(), readiness.color())
                    Text(tr("Low ${low.int()} · high ${high.int()}", "Terendah ${low.int()} · tertinggi ${high.int()}"), style = T.caption, color = K.muted)
                }
            }
            if (hasSteps) Tile(Modifier.weight(1f).fillMaxHeight()) {
                val goal = d.stepGoal?.takeIf { it > 0 } ?: 8000.0
                Label(tr("Steps", "Langkah"))
                Value(d.steps.int(), "/ ${goal.int()}", num(36.sp))
                SpanBar(0f, (d.steps!! / goal).toFloat(), K.ink)
                Text(tr("${(d.steps / goal * 100).roundToInt()}% of daily goal", "${(d.steps / goal * 100).roundToInt()}% target harian"), style = T.caption, color = K.muted)
            }
        }
    }
    if (d.sleepS != null) SleepTile(d)
}

@Composable
private fun Tile(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) =
    Panel(modifier, padding = 14.dp, radius = 20.dp, gap = 8.dp, content = content)

@Composable
private fun SleepTile(d: Daily) {
    val stages = listOf(
        Triple("Deep", d.deepS, K.deep),
        Triple("REM", d.remS, K.blue),
        Triple(tr("Light", "Ringan"), d.lightS, K.light),
        Triple(tr("Awake", "Bangun"), d.awakeS, K.low),
    )
    Panel(Modifier.fillMaxWidth(), padding = 14.dp, radius = 20.dp, gap = 10.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Label(tr("Last night's sleep", "Tidur semalam"), modifier = Modifier.weight(1f).alignByBaseline())
            Value(sleep(d.sleepS), d.sleepScore?.let { tr("score ${it.int()}", "skor ${it.int()}") } ?: "", num(28.sp), gap = 6.dp, modifier = Modifier.alignByBaseline())
        }
        if (stages.any { (it.second ?: 0.0) > 0 }) {
            StackedBar(
                stages.map { (it.second ?: 0.0) to it.third },
                description = stages.joinToString(", ") { "${it.first} ${sleep(it.second)}" },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stages.forEach { (name, s, c) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { LegendKey(name, c) }
                        Text(sleep(s), style = body(14.sp, FontWeight.SemiBold, 1.3f), color = K.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekPanel(t: Today) {
    val w = t.week
    var sel by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    Panel(Modifier.fillMaxWidth(), gap = 14.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            CardTitle(tr("This week", "Minggu ini"), Modifier.weight(1f).alignByBaseline(), icon = R.drawable.ic_calendar_month)
            val picked = sel?.let { w.dailyLoad.getOrNull(it) }
            Text(
                if (picked != null) "${weekday(picked.date)} · load ${picked.load.int()}"
                else tr(if (w.activeDays == 1) "1 active day" else "${w.activeDays} active days", "${w.activeDays} hari aktif"),
                Modifier.alignByBaseline(), style = body(13.sp, FontWeight.SemiBold), color = K.good,
            )
        }
        if (w.count == 0) {
            Text(tr("No activities this week yet.", "Belum ada aktivitas minggu ini."), style = T.body, color = K.muted)
            return@Panel
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(w.count.toString(), tr("sessions", "sesi"), num(30.sp), Modifier.weight(1f))
            Stat((w.distanceM / 1000).one(), "km", num(30.sp), Modifier.weight(1f))
            Stat(duration(w.durationS), tr("duration", "durasi"), num(30.sp), Modifier.weight(1f))
        }
        val days = w.dailyLoad
        if (days.isNotEmpty()) {
            val maxLoad = max(1.0, days.maxOf { it.load })
            val today = LocalDate.now().toString()
            val reveal = rememberReveal(days, durationMs = 600)
            // 64dp tallest bar + load number + day label; the handoff's 92dp clipped the labels under tall bars.
            Row(
                Modifier.fillMaxWidth().height(104.dp).scrub(days.size, bars = true) { i ->
                    if (i != null && i != sel) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    sel = i
                },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                days.forEachIndexed { i, d ->
                    val isToday = d.date == today
                    val grow = ((reveal.value * (days.size + 6) - i) / 6f).coerceIn(0f, 1f)
                    val dim = sel != null && sel != i
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (d.load > 0) Text(d.load.int(), style = T.mono.copy(fontSize = 10.sp), color = K.muted)
                        Box(
                            Modifier.fillMaxWidth()
                                .height(if (d.load > 0) (max(8.0, d.load / maxLoad * 64) * grow).coerceAtLeast(4.0).dp else 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        d.load <= 0 -> K.track
                                        dim -> K.good.copy(alpha = 0.2f)
                                        isToday || sel == i -> K.good
                                        else -> K.good.copy(alpha = 0.45f)
                                    },
                                ),
                        )
                        Text(weekdayShort(d.date), style = body(11.sp, FontWeight.SemiBold, 1.2f), color = if (isToday) K.ink else K.muted)
                    }
                }
            }
            Text(tr("Bar height = training load per day · total ${days.sumOf { it.load }.int()}", "Tinggi batang = training load per hari · total ${days.sumOf { it.load }.int()}"), style = T.caption, color = K.muted)
        }
    }
}

@Composable
private fun LastActivity(a: Activity, onOpen: (Long) -> Unit) {
    val hasDistance = (a.distanceM ?: 0.0) > 0
    val stats = listOfNotNull(
        if (hasDistance) km(a.distanceM) to "km" else duration(a.durationS) to tr("duration", "durasi"),
        a.avgSpeed?.takeIf { hasDistance }?.let { pace(it) to "/km" },
        a.avgHr?.let { it.int() to "bpm" },
        a.aerobicTe?.let { it.one() to tr("aerobic TE", "TE aerobik") },
    )
    Panel(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = tr("Open activity details", "Buka detail aktivitas")) { onOpen(a.id) },
        gap = 10.dp,
    ) {
        Label(tr("Last activity · ${weekday(a.date)}", "Aktivitas terakhir · ${weekday(a.date)}"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name ?: activityType(a.type), Modifier.weight(1f), style = num(24.sp, FontWeight.Bold), color = K.ink)
            Glyph(R.drawable.ic_chevron_right, K.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stats.forEach { (v, l) -> Stat(v, l, num(20.sp, FontWeight.Bold), Modifier.weight(1f), captionStyle = body(11.sp, lh = 1.2f)) }
            repeat(4 - stats.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * The session done today, right under the header: its route in pace colors (or the worked muscles for
 * strength) beside a big number, so it reads as today's result rather than one more card.
 */
@Composable
private fun TodayActivity(a: Activity, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    // same disk cache as the activity detail, so opening it after this is instant
    val detail = rememberCachedLoad("activity", a.id) { Repo.api(ctx).activity(a.id) }
    val full = (detail.state as? Load.Ok)?.data
    val hasDistance = (a.distanceM ?: 0.0) > 0
    val stats = listOfNotNull(
        if (hasDistance) duration(a.durationS) to tr("time", "waktu") else null,
        a.avgSpeed?.takeIf { hasDistance }?.let { pace(it) to "/km" },
        a.avgHr?.let { it.int() to "bpm" },
        full?.strength?.totalSets?.let { it.toString() to tr("sets", "set") },
        a.aerobicTe?.takeIf { !hasDistance }?.let { it.one() to tr("aerobic TE", "TE aerobik") },
    ).take(3)
    Panel(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = tr("Open activity details", "Buka detail aktivitas")) { onOpen(a.id) },
        padding = 20.dp,
        gap = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(tr("Done today · ${time(a.startTime)}", "Selesai hari ini · ${time(a.startTime)}"), K.good, Modifier.weight(1f))
            Glyph(R.drawable.ic_chevron_right, K.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(a.name ?: activityType(a.type), style = num(20.sp, FontWeight.Bold), color = K.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (hasDistance) Value(km(a.distanceM), "km", num(56.sp, italic = true, lh = 0.9f), unitStyle = body(16.sp), gap = 4.dp, roll = true)
                else RollingText(duration(a.durationS), num(48.sp, italic = true, lh = 0.9f), K.ink)
            }
            ActivityPicture(a, full)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stats.forEach { (v, l) -> Stat(v, l, num(20.sp, FontWeight.Bold), Modifier.weight(1f), captionStyle = body(11.sp, lh = 1.2f)) }
            repeat(3 - stats.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Route for GPS sessions, body map for strength, otherwise the type icon; the icon also stands in while the detail loads. */
@Composable
private fun ActivityPicture(a: Activity, full: Activity?) {
    val route = full?.route?.takeIf { it.size > 1 }
    val muscles = full?.strength?.muscles?.takeIf { it.isNotEmpty() }
    Box(Modifier.size(width = 116.dp, height = 116.dp).clip(RoundedCornerShape(16.dp)).background(K.base), contentAlignment = Alignment.Center) {
        when {
            route != null -> Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                drawIntoCanvas { drawRoute(it.nativeCanvas, 0f, 0f, size.width, size.height, route, 3.dp.toPx()) }
            }
            muscles != null -> Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                drawIntoCanvas { drawBody(it.nativeCanvas, 0f, 0f, size.width, size.height, muscles, K.good, K.track) }
            }
            else -> Glyph(activityIcon(a.type), K.muted, 40.dp)
        }
    }
}

private fun rangeLabel(from: String, to: String): String {
    val f = runCatching { LocalDate.parse(from) }.getOrNull() ?: return "$from–$to"
    val t = runCatching { LocalDate.parse(to) }.getOrNull() ?: return "$from–$to"
    return if (f.month == t.month) "${f.dayOfMonth}–${dayMonth(to)}" else "${dayMonth(from)}–${dayMonth(to)}"
}

@Composable
private fun SummaryCard(s: Summary) {
    var open by remember { mutableStateOf(false) }
    // English summary when the app is in English and the skill wrote one; older summaries only have Indonesian.
    val text = s.en?.takeUnless { isId() }
    val headline = text?.headline ?: s.headline
    val sections = text?.sections ?: s.sections
    Panel(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).animateContentSize()
            .clickable(role = Role.Button, onClickLabel = if (open) tr("Close summary", "Tutup ringkasan") else tr("Open summary", "Buka ringkasan")) { open = !open },
        gap = 8.dp,
        color = K.good,
    ) {
        Text(
            tr("Weekly summary · ${rangeLabel(s.range.from, s.range.to)}", "Ringkasan minggu · ${rangeLabel(s.range.from, s.range.to)}").uppercase(),
            style = T.label.copy(fontWeight = FontWeight.Bold),
            color = K.onAccent,
        )
        Text(headline.uppercase(), style = num(24.sp, lh = 1.05f), color = K.onAccent)
        if (open) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sections.forEach { sec ->
                    Column {
                        Text(sec.title.uppercase(), style = body(12.sp, FontWeight.Bold).copy(letterSpacing = 0.08.em), color = K.onAccent)
                        Text(sec.body, style = body(14.sp, lh = 1.45f), color = K.onAccent)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (open) tr("Close", "Tutup") else tr("Tap to read it all", "Ketuk untuk baca lengkap"), style = body(13.sp, FontWeight.SemiBold), color = K.onAccent)
            Glyph(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more, K.onAccent, 18.dp)
        }
    }
}
