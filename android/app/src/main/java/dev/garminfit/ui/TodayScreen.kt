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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import dev.garminfit.restUp
import dev.garminfit.todayAdvice
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

/**
 * Today leads with one message that depends on the moment: after a session its result and what comes next,
 * before it the session itself, with no session Garmin's readiness verdict. Everything else (readiness
 * detail, recovery, the week) follows as quieter sections, so each number shouts only once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onOpenActivity: (Long) -> Unit, onOpenPlan: () -> Unit, onOpenAccount: () -> Unit, onStartWorkout: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // widgets redraw on their own coroutine so the screen does not wait for five Glance renders; the plan
    // is fetched there too because the Plan widget's week strip reads it
    val loader = rememberCachedLoad("today") {
        Repo.fetchToday(ctx).also { scope.launch { runCatching { Repo.fetchPlan(ctx) }; Widgets.updateAll(ctx) } }
    }
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
                val activityToday = today.lastActivity?.takeIf { it.date.take(10) == LocalDate.now().toString() }
                val verdict = latest?.takeIf { it.readiness != null }?.let { readinessVerdict(it.readinessLevel, it.readiness, it.recoveryMin) }
                val session = today.plan?.session?.takeIf { it.kind != "rest" }
                Header(today, onSync = ::refresh, onOpenAccount = onOpenAccount)
                syncError?.let { Text(it, Modifier.padding(horizontal = 4.dp), style = T.bodyS, color = K.low) }
                // the focus: one block on the background, chosen by where the day stands
                val readinessLeads = activityToday == null && session == null
                Column(Modifier.padding(top = 12.dp)) {
                    when {
                        activityToday != null -> DoneFocus(activityToday, today, verdict, onOpenActivity, onOpenPlan)
                        session != null -> PlanTodayFocus(today.plan!!, verdict, onOpenPlan, onStartWorkout.takeIf { today.role == "owner" })
                        verdict != null && latest != null -> ReadinessFocus(latest, verdict, restDay = today.plan?.session != null, onOpenPlan)
                    }
                }
                if (latest == null) {
                    Text(
                        if (today.role == "owner") tr("No data yet. Pull down to sync from Garmin.", "Belum ada data. Tarik layar ke bawah untuk sync dari Garmin.")
                        else tr("No data yet. It shows up after the owner syncs.", "Belum ada data. Data muncul setelah pemilik melakukan sync."),
                        Modifier.padding(horizontal = 4.dp, vertical = 48.dp),
                        style = T.body,
                        color = K.muted,
                    )
                } else {
                    BodySection(latest, today, verdict.takeUnless { readinessLeads })
                    Recovery(latest, verdict?.status ?: Status.GOOD)
                    WeekSection(today, lastActivity = today.lastActivity.takeIf { activityToday == null }, onOpenActivity)
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Modifier.clip(CircleShape).background(K.surface).border(1.dp, K.line, CircleShape)
                        .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Glyph(R.drawable.ic_refresh, K.ink, 16.dp)
                    Text(listOfNotNull("Sync", syncTime(t.latest?.syncedAt)).joinToString(" "), style = body(13.sp, FontWeight.SemiBold, 1.2f), color = K.ink)
                }
            }
            Avatar(t.name, onOpenAccount, size = 40)
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

/**
 * After a session: the result is the message. The route (mapcn's lesson: the line is the only bright thing
 * on a quiet ground) sits under a big distance, then one "what's next" line ties it to the plan and recovery.
 */
@Composable
private fun DoneFocus(a: Activity, t: Today, verdict: Verdict?, onOpen: (Long) -> Unit, onOpenPlan: () -> Unit) {
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
    val open = Modifier.clickable(role = Role.Button, onClickLabel = tr("Open activity details", "Buka detail aktivitas")) { onOpen(a.id) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).then(open).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label(tr("Done today · ${time(a.startTime)}", "Selesai hari ini · ${time(a.startTime)}"), K.good)
            Text(a.name ?: activityType(a.type), style = head(18.sp, -0.02f), color = K.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (hasDistance) Value(km(a.distanceM), "km", T.hero, unitStyle = body(18.sp, FontWeight.SemiBold), gap = 6.dp, roll = true)
            else RollingText(duration(a.durationS), num(64.sp, italic = true, lh = 0.9f), K.ink)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.forEach { (v, l) -> Stat(v, l, num(24.sp, FontWeight.Bold), Modifier.weight(1f), captionStyle = body(12.sp, lh = 1.2f)) }
                repeat(3 - stats.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
        ActivityPicture(a, full, Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(20.dp)).then(open))
        WhatsNext(t, verdict, onOpenPlan)
    }
}

/** The line after a result: the plan session's state if there is one, then the advice. */
@Composable
private fun WhatsNext(t: Today, verdict: Verdict?, onOpenPlan: () -> Unit) {
    val s = t.plan?.session?.takeIf { it.kind != "rest" }
    val advice = when {
        s?.status == "done" -> restUp()
        verdict != null -> todayAdvice(verdict, t.plan).first
        else -> null
    }
    if (s == null && advice == null) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(K.surface).border(1.dp, K.line, RoundedCornerShape(20.dp)),
    ) {
        if (s != null) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = tr("Open plan", "Buka plan"), onClick = onOpenPlan)
                    .sizeIn(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlanStatusMark(s)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tr("Plan · ${s.title.text}", "Plan · ${s.title.text}"), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                    Text(sessionProgress(s), style = T.caption, color = K.muted)
                }
                Glyph(R.drawable.ic_chevron_right, K.muted, 20.dp)
            }
        }
        if (advice != null) {
            if (s != null) HorizontalDivider(color = K.line)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(tr("What's next", "Selanjutnya"))
                Text(advice, style = T.body, color = K.ink)
            }
        }
    }
}

/** No session today (or a rest day): Garmin's readiness verdict is the message. */
@Composable
private fun ReadinessFocus(d: Daily, v: Verdict, restDay: Boolean, onOpenPlan: () -> Unit) {
    val c = v.status.color()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Label(if (restDay) tr("Rest day in your plan", "Hari istirahat di plan") else tr("No session planned today", "Tidak ada sesi hari ini"), modifier = Modifier.padding(horizontal = 4.dp))
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ReadinessRing(d.readiness!!.roundToInt(), c, 112.dp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(v.word.uppercase(), style = T.statusWord, color = c)
                Text(v.tagline, style = body(15.sp, FontWeight.SemiBold, 1.3f), color = K.ink)
                readinessStatus(d.readinessLevel)?.let { Text(tr("Garmin level: ${it.first}", "Level Garmin: ${it.first}"), style = T.bodyS, color = K.muted) }
            }
        }
        Text(v.advice, Modifier.padding(horizontal = 4.dp), style = T.body, color = K.ink)
        Box(Modifier.padding(horizontal = 4.dp)) { ChipRow(v.chips) }
        if (restDay) Text(
            tr("Open plan", "Buka plan"),
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onOpenPlan).sizeIn(minHeight = 48.dp).padding(horizontal = 4.dp, vertical = 14.dp),
            style = body(14.sp, FontWeight.SemiBold), color = K.ink,
        )
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

/**
 * Readiness and why: the compact verdict (when it is not already the focus), the baseline bands straight on the
 * background, and the flags. [verdict] is null when the focus above already shows it.
 */
@Composable
private fun BodySection(d: Daily, t: Today, verdict: Verdict?) {
    val list = factors(d, t)
    if (list.isEmpty() && verdict == null && t.flags.isEmpty()) return
    SectionHeader(tr("Readiness", "Readiness"), tr("green = your normal", "hijau = normalmu").takeIf { list.isNotEmpty() })
    if (verdict != null) {
        val c = verdict.status.color()
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ReadinessRing(d.readiness!!.roundToInt(), c, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(verdict.word.uppercase(), style = num(26.sp, italic = true, lh = 1f), color = c)
                Text(
                    listOfNotNull(verdict.tagline, readinessStatus(d.readinessLevel)?.let { tr("Garmin level ${it.first.lowercase()}", "level Garmin ${it.first.lowercase()}") }).joinToString(" · "),
                    style = T.bodyS, color = K.body2,
                )
            }
        }
    }
    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
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
    t.flags.forEach { FlagBox(flagText(it, t), flagStatus(it.key, it.level)) }
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

/** Body Battery, steps and sleep as one strip: three readings of the same night and day, not three cards. */
@Composable
private fun Recovery(d: Daily, readiness: Status) {
    val bb = d.bbNow ?: d.bbHigh
    val hasSteps = d.steps != null
    if (bb == null && !hasSteps && d.sleepS == null) return
    SectionHeader(tr("Recovery", "Pemulihan"))
    Panel(Modifier.fillMaxWidth(), padding = 16.dp, radius = 20.dp, gap = 14.dp) {
        if (bb != null || hasSteps) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (bb != null) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("Body Battery")
                    Value(bb.int(), if (d.bbNow != null) tr("now", "sekarang") else tr("peak", "tertinggi"), num(32.sp))
                    val low = d.bbLow
                    val high = d.bbHigh
                    if (low != null && high != null) {
                        SpanBar((low / 100).toFloat(), (high / 100).toFloat(), readiness.color(), height = 6.dp)
                        Text(tr("Low ${low.int()} · high ${high.int()}", "Terendah ${low.int()} · tertinggi ${high.int()}"), style = T.caption, color = K.muted)
                    }
                }
                if (bb != null && hasSteps) Box(Modifier.width(1.dp).fillMaxHeight().background(K.line))
                if (hasSteps) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val goal = d.stepGoal?.takeIf { it > 0 } ?: 8000.0
                    Label(tr("Steps", "Langkah"))
                    Value(d.steps.int(), "/ ${goal.int()}", num(32.sp))
                    SpanBar(0f, (d.steps!! / goal).toFloat(), K.ink, height = 6.dp)
                    Text(tr("${(d.steps / goal * 100).roundToInt()}% of daily goal", "${(d.steps / goal * 100).roundToInt()}% target harian"), style = T.caption, color = K.muted)
                }
            }
        }
        if (d.sleepS != null) {
            if (bb != null || hasSteps) HorizontalDivider(color = K.line)
            Sleep(d)
        }
    }
}

@Composable
private fun Sleep(d: Daily) {
    val stages = listOf(
        Triple("Deep", d.deepS, K.deep),
        Triple("REM", d.remS, K.blue),
        Triple(tr("Light", "Ringan"), d.lightS, K.light),
        Triple(tr("Awake", "Bangun"), d.awakeS, K.low),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Label(tr("Last night's sleep", "Tidur semalam"), modifier = Modifier.weight(1f).alignByBaseline())
            Value(sleep(d.sleepS), d.sleepScore?.let { tr("score ${it.int()}", "skor ${it.int()}") } ?: "", num(26.sp), gap = 6.dp, modifier = Modifier.alignByBaseline())
        }
        if (stages.any { (it.second ?: 0.0) > 0 }) {
            StackedBar(
                stages.map { (it.second ?: 0.0) to it.third },
                description = stages.joinToString(", ") { "${it.first} ${sleep(it.second)}" },
                height = 10.dp,
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

/** The week straight on the background: a stat strip, the daily load bars, and the last activity as a list row. */
@Composable
private fun WeekSection(t: Today, lastActivity: Activity?, onOpenActivity: (Long) -> Unit) {
    val w = t.week
    var sel by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    SectionHeader(tr("This week", "Minggu ini"), tr(if (w.activeDays == 1) "1 active day" else "${w.activeDays} active days", "${w.activeDays} hari aktif"))
    if (w.count == 0) {
        Text(tr("No activities this week yet.", "Belum ada aktivitas minggu ini."), Modifier.padding(horizontal = 4.dp), style = T.body, color = K.muted)
    } else {
        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(w.count.toString(), tr("sessions", "sesi"), num(30.sp), Modifier.weight(1f))
            Stat((w.distanceM / 1000).one(), "km", num(30.sp), Modifier.weight(1f))
            Stat(duration(w.durationS), tr("duration", "durasi"), num(30.sp), Modifier.weight(1f))
        }
        val days = w.dailyLoad
        if (days.isNotEmpty()) {
            val maxLoad = max(1.0, days.maxOf { it.load })
            val today = LocalDate.now().toString()
            val reveal = rememberReveal(days, durationMs = 600)
            val picked = sel?.let { days.getOrNull(it) }
            // 64dp tallest bar + load number + day label; the handoff's 92dp clipped the labels under tall bars.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).height(104.dp).scrub(days.size, bars = true) { i ->
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
            Text(
                if (picked != null) "${weekday(picked.date)} · load ${picked.load.int()}"
                else tr("Bar height = training load per day · total ${days.sumOf { it.load }.int()}", "Tinggi batang = training load per hari · total ${days.sumOf { it.load }.int()}"),
                Modifier.padding(horizontal = 4.dp), style = T.caption, color = if (picked != null) K.ink else K.muted,
            )
        }
    }
    lastActivity?.let { a ->
        Column {
            Label(tr("Last activity", "Aktivitas terakhir"), modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            ActivityRow(a, hPad = 4.dp) { onOpenActivity(a.id) }
            HorizontalDivider(Modifier.padding(horizontal = 4.dp), color = K.track)
        }
    }
}

/** Route for GPS sessions, body map for strength, otherwise the type icon; the icon also stands in while the detail loads. */
@Composable
private fun ActivityPicture(a: Activity, full: Activity?, modifier: Modifier) {
    val route = full?.route?.takeIf { it.size > 1 }
    val muscles = full?.strength?.muscles?.takeIf { it.isNotEmpty() }
    Box(modifier.background(K.surface).border(1.dp, K.line, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
        when {
            route != null -> Canvas(Modifier.fillMaxSize().padding(20.dp)) {
                drawIntoCanvas { drawRoute(it.nativeCanvas, 0f, 0f, size.width, size.height, route, 4.dp.toPx()) }
            }
            muscles != null -> Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                drawIntoCanvas { drawBody(it.nativeCanvas, 0f, 0f, size.width, size.height, muscles, K.good, K.track) }
            }
            else -> Glyph(activityIcon(a.type), K.muted, 48.dp)
        }
        // the one control on the picture, in a corner like a map's own buttons
        Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp).clip(CircleShape).background(K.base.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
            Glyph(R.drawable.ic_chevron_right, K.ink, 20.dp)
        }
    }
}

private fun rangeLabel(from: String, to: String): String {
    val f = runCatching { LocalDate.parse(from) }.getOrNull() ?: return "$from–$to"
    val t = runCatching { LocalDate.parse(to) }.getOrNull() ?: return "$from–$to"
    return if (f.month == t.month) "${f.dayOfMonth}–${dayMonth(to)}" else "${dayMonth(from)}–${dayMonth(to)}"
}

/** Claude's weekly read: a headline to open, on a plain card so it no longer outshouts today's focus. */
@Composable
private fun SummaryCard(s: Summary) {
    var open by remember { mutableStateOf(false) }
    // English summary when the app is in English and the skill wrote one; older summaries only have Indonesian.
    val text = s.en?.takeUnless { isId() }
    val headline = text?.headline ?: s.headline
    val sections = text?.sections ?: s.sections
    SectionHeader(tr("Weekly summary", "Ringkasan minggu"), rangeLabel(s.range.from, s.range.to))
    Panel(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).animateContentSize()
            .clickable(role = Role.Button, onClickLabel = if (open) tr("Close summary", "Tutup ringkasan") else tr("Open summary", "Buka ringkasan")) { open = !open },
        gap = 10.dp,
    ) {
        Text(headline, style = head(21.sp, -0.03f), color = K.ink)
        if (open) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sections.forEach { sec ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(sec.title, style = body(13.sp, FontWeight.Bold), color = K.ink)
                        Text(sec.body, style = body(14.sp, lh = 1.5f), color = K.body3)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (open) tr("Close", "Tutup") else tr("Read it all", "Baca lengkap"), style = body(13.sp, FontWeight.SemiBold), color = K.ink)
            Glyph(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more, K.ink, 18.dp)
        }
    }
}
