package dev.garminfit.ui

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
fun TodayScreen(onOpenActivity: (Long) -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoad { Repo.fetchToday(ctx).also { Widgets.updateAll(ctx) } }
    var refreshing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }

    LoadView(loader, loadingText = "Memuat data hari ini") { today ->
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
                Header(today, onSync = ::refresh)
                syncError?.let { Text(it, Modifier.padding(horizontal = 4.dp), style = T.bodyS, color = K.low) }
                if (latest == null) {
                    Text(
                        if (today.role == "owner") "Belum ada data. Tarik layar ke bawah untuk sync dari Garmin."
                        else "Belum ada data. Data muncul setelah pemilik melakukan sync.",
                        Modifier.padding(horizontal = 4.dp, vertical = 48.dp),
                        style = T.body,
                        color = K.muted,
                    )
                } else {
                    val verdict = readinessVerdict(latest.readinessLevel, latest.readiness, latest.recoveryMin)
                    if (verdict != null && latest.readiness != null) Hero(latest, verdict)
                    Factors(latest, today)
                    today.flags.forEach { FlagBox(it.text, flagStatus(it.key, it.level)) }
                    Tiles(latest, verdict?.status ?: Status.GOOD)
                    WeekPanel(today)
                    today.lastActivity?.let { LastActivity(it, onOpenActivity) }
                    today.summary?.let { SummaryCard(it) }
                }
                TextButton(onClick = onLogout, Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Ganti token", style = T.bodyS, color = K.muted)
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
private fun Header(t: Today, onSync: () -> Unit) {
    val now = LocalDate.now()
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(dayLabel(now.toString()))
                val name = t.name?.replaceFirstChar { it.uppercase() }
                Text(listOfNotNull(greeting(), name).joinToString(", "), style = T.greeting, color = K.ink)
            }
            val label = if (t.role == "owner") "Sync" else "Muat ulang"
            Box(
                Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onSync),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    listOfNotNull("Sync", syncTime(t.latest?.syncedAt), "↻").joinToString(" "),
                    Modifier.clip(CircleShape).background(K.surface).padding(horizontal = 10.dp, vertical = 6.dp),
                    style = T.caption,
                    color = K.muted,
                )
            }
        }
        val latest = t.latest
        if (latest != null && latest.date != now.toString()) {
            Text(
                if (t.role == "owner") "Data terakhir dari ${dayLabel(latest.date)}. Tarik ke bawah untuk sync."
                else "Data terakhir dari ${dayLabel(latest.date)}.",
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
                readinessStatus(d.readinessLevel)?.let { Text("Level Garmin: ${it.first}", style = T.bodyS, color = K.muted) }
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(K.base).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Label("Saran latihan hari ini", color = c)
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
        add(Factor("HRV semalam", hrv.int(), "ms", hrv, lo - 13, hi + 13, lo, hi, hrvFactor(hrv, lo, hi), "normal ${lo.int()}–${hi.int()}"))
    }
    d.sleepScore?.let { add(Factor("Tidur", it.int(), "skor", it, 30.0, 100.0, 70.0, 100.0, sleepFactor(it), sleep(d.sleepS))) }
    val mean = t.baseline.rhr?.mean
    if (d.rhr != null && mean != null) {
        add(Factor("Resting HR", d.rhr.int(), "bpm", d.rhr, mean - 10, mean + 12, mean - 4, mean + 4, rhrFactor(d.rhr, mean), "rata-rata ${mean.int()}"))
    }
    t.baseline.load?.ratio?.let {
        add(Factor("Beban akut : kronis", it.two(), "rasio", it, 0.5, 1.8, 0.8, 1.3, loadFactor(it), "optimal 0,8–1,3"))
    }
    // Today's stress is still accumulating, so the last complete day is the fair comparison.
    val today = LocalDate.now().toString()
    t.spark.date.indices.lastOrNull { i -> t.spark.date[i].let { it != null && it < today } && t.spark.stress.getOrNull(i) != null }?.let { i ->
        val date = t.spark.date[i]!!
        val s = t.spark.stress[i]!!
        val note = if (date == LocalDate.now().minusDays(1).toString()) "kemarin" else weekday(date).lowercase()
        add(Factor("Stres", s.int(), "/100", s, 0.0, 100.0, 0.0, 35.0, stressFactor(s), note))
    }
}

@Composable
private fun Factors(d: Daily, t: Today) {
    val list = factors(d, t)
    if (list.isEmpty()) return
    Panel(Modifier.fillMaxWidth(), gap = 16.dp) {
        CardTitle("Kenapa skornya segini?")
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
                Value(bb.int(), if (d.bbNow != null) "sekarang" else "tertinggi", num(36.sp))
                val low = d.bbLow
                val high = d.bbHigh
                if (low != null && high != null) {
                    SpanBar((low / 100).toFloat(), (high / 100).toFloat(), readiness.color())
                    Text("Terendah ${low.int()} · tertinggi ${high.int()}", style = T.caption, color = K.muted)
                }
            }
            if (hasSteps) Tile(Modifier.weight(1f).fillMaxHeight()) {
                val goal = d.stepGoal?.takeIf { it > 0 } ?: 8000.0
                Label("Langkah")
                Value(d.steps.int(), "/ ${goal.int()}", num(36.sp))
                SpanBar(0f, (d.steps!! / goal).toFloat(), K.ink)
                Text("${(d.steps / goal * 100).roundToInt()}% target harian", style = T.caption, color = K.muted)
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
        Triple("Ringan", d.lightS, K.light),
        Triple("Bangun", d.awakeS, K.low),
    )
    Panel(Modifier.fillMaxWidth(), padding = 14.dp, radius = 20.dp, gap = 10.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Label("Tidur semalam", modifier = Modifier.weight(1f).alignByBaseline())
            Value(sleep(d.sleepS), d.sleepScore?.let { "skor ${it.int()}" } ?: "", num(28.sp), gap = 6.dp, modifier = Modifier.alignByBaseline())
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
    Panel(Modifier.fillMaxWidth(), gap = 14.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            CardTitle("7 hari terakhir", Modifier.weight(1f).alignByBaseline())
            Text("${w.activeDays} hari aktif", Modifier.alignByBaseline(), style = body(13.sp, FontWeight.SemiBold), color = K.good)
        }
        if (w.count == 0) {
            Text("Belum ada aktivitas 7 hari terakhir.", style = T.body, color = K.muted)
            return@Panel
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(w.count.toString(), "sesi", num(30.sp), Modifier.weight(1f))
            Stat((w.distanceM / 1000).one(), "km", num(30.sp), Modifier.weight(1f))
            Stat(duration(w.durationS), "durasi", num(30.sp), Modifier.weight(1f))
        }
        val days = w.dailyLoad
        if (days.isNotEmpty()) {
            val maxLoad = max(1.0, days.maxOf { it.load })
            val today = LocalDate.now().toString()
            // 64dp tallest bar + load number + day label; the handoff's 92dp clipped the labels under tall bars.
            Row(Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEach { d ->
                    val isToday = d.date == today
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (d.load > 0) Text(d.load.int(), style = T.mono.copy(fontSize = 10.sp), color = K.muted)
                        Box(
                            Modifier.fillMaxWidth()
                                .height(if (d.load > 0) max(8.0, d.load / maxLoad * 64).dp else 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        d.load <= 0 -> K.track
                                        isToday -> K.good
                                        else -> K.good.copy(alpha = 0.45f)
                                    },
                                ),
                        )
                        Text(weekdayShort(d.date), style = body(11.sp, FontWeight.SemiBold, 1.2f), color = if (isToday) K.ink else K.muted)
                    }
                }
            }
            Text("Tinggi batang = training load per hari · total ${days.sumOf { it.load }.int()}", style = T.caption, color = K.muted)
        }
    }
}

@Composable
private fun LastActivity(a: Activity, onOpen: (Long) -> Unit) {
    val hasDistance = (a.distanceM ?: 0.0) > 0
    val stats = listOfNotNull(
        if (hasDistance) km(a.distanceM) to "km" else duration(a.durationS) to "durasi",
        a.avgSpeed?.takeIf { hasDistance }?.let { pace(it) to "/km" },
        a.avgHr?.let { it.int() to "bpm" },
        a.aerobicTe?.let { it.one() to "TE aerobik" },
    )
    Panel(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = "Buka detail aktivitas") { onOpen(a.id) },
        gap = 10.dp,
    ) {
        Label("Aktivitas terakhir · ${weekday(a.date)}")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(a.name ?: activityType(a.type), Modifier.weight(1f), style = num(24.sp, FontWeight.Bold), color = K.ink)
            Text("›", style = body(18.sp), color = K.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stats.forEach { (v, l) -> Stat(v, l, num(20.sp, FontWeight.Bold), Modifier.weight(1f), captionStyle = body(11.sp, lh = 1.2f)) }
            repeat(4 - stats.size) { Spacer(Modifier.weight(1f)) }
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
    Panel(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).animateContentSize()
            .clickable(role = Role.Button, onClickLabel = if (open) "Tutup ringkasan" else "Buka ringkasan") { open = !open },
        gap = 8.dp,
        color = K.good,
    ) {
        Text(
            "Ringkasan minggu · ${rangeLabel(s.range.from, s.range.to)}".uppercase(),
            style = T.label.copy(fontWeight = FontWeight.Bold),
            color = K.onAccent,
        )
        Text(s.headline.uppercase(), style = num(24.sp, lh = 1.05f), color = K.onAccent)
        if (open) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                s.sections.forEach { sec ->
                    Column {
                        Text(sec.title.uppercase(), style = body(12.sp, FontWeight.Bold).copy(letterSpacing = 0.08.em), color = K.onAccent)
                        Text(sec.body, style = body(14.sp, lh = 1.45f), color = K.onAccent)
                    }
                }
            }
        }
        Text(if (open) "Tutup ↑" else "Ketuk untuk baca lengkap ↓", style = body(13.sp, FontWeight.SemiBold), color = K.onAccent)
    }
}
