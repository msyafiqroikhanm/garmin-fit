package dev.garminfit.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import dev.garminfit.PlanMeta
import dev.garminfit.ApiService
import dev.garminfit.OverrideIn
import dev.garminfit.PlanExtra
import dev.garminfit.PlanListItem
import dev.garminfit.PlanCounts
import dev.garminfit.planCounts
import dev.garminfit.progressLine
import dev.garminfit.targetShare
import dev.garminfit.kindOf
import dev.garminfit.dayMonthYear
import dev.garminfit.friendly
import dev.garminfit.widget.Widgets
import android.widget.Toast
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garminfit.PlanSession
import dev.garminfit.PlanToday
import dev.garminfit.PlanView
import dev.garminfit.PlanWeek
import dev.garminfit.Verdict
import dev.garminfit.planStandsNote
import dev.garminfit.todayAdvice
import dev.garminfit.daySessions
import dev.garminfit.pending
import dev.garminfit.targetText
import dev.garminfit.R
import dev.garminfit.Repo
import dev.garminfit.activityType
import dev.garminfit.dayMonth
import dev.garminfit.duration
import dev.garminfit.km
import dev.garminfit.paceSec
import dev.garminfit.tr
import dev.garminfit.weekday
import dev.garminfit.weekdayShort
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private fun pctText(p: Double?) = p?.let { "${(it * 100).roundToInt()}%" } ?: "–"

/** "6 km · Z2 · 5:20–5:40 /km", whatever the session specifies. */
fun planTarget(s: PlanSession): String = listOfNotNull(
    s.target?.distanceM?.let { "${km(it).trimEnd('0').trimEnd('.', ',')} km" },
    s.target?.durationS?.takeIf { s.target.distanceM == null }?.let { duration(it) },
    s.hrZone?.let { "Z$it" },
    s.pace?.let { "${paceSec(it.minSKm)}–${paceSec(it.maxSKm)} /km" },
).joinToString(" · ")

internal fun statusLabel(s: PlanSession) = when (s.status) {
    "done" -> tr("Done", "Selesai")
    "partial" -> pctText(s.pct)
    "missed" -> tr("Missed", "Terlewat")
    "skipped" -> tr("Skipped", "Dilewati")
    "today" -> tr("Today", "Hari ini")
    "rest" -> tr("Rest", "Istirahat")
    else -> tr("Planned", "Rencana")
}

@DrawableRes
internal fun statusIcon(status: String) = when (status) {
    "done" -> R.drawable.ic_check_circle
    "partial" -> R.drawable.ic_partial
    "missed" -> R.drawable.ic_cancel
    "skipped" -> R.drawable.ic_skipped
    "rest" -> R.drawable.ic_bedtime
    else -> R.drawable.ic_radio_button_unchecked
}

internal fun statusColor(status: String) = when (status) {
    "done" -> K.good
    "partial" -> K.fair
    "missed" -> K.low
    "today" -> K.ink
    else -> K.muted
}

/** Status mark; a finished session's check pops in once, like ticking it off a list. */
@Composable
fun PlanStatusMark(s: PlanSession, size: androidx.compose.ui.unit.Dp = 24.dp, tint: Color? = null) {
    val pop = rememberReveal(s.date + s.status, durationMs = 450)
    val finished = s.status == "done" || s.status == "partial"
    Glyph(
        statusIcon(s.status), tint ?: statusColor(s.status), size,
        Modifier.graphicsLayer {
            val k = if (finished) 0.6f + 0.4f * pop.value else 1f
            scaleX = k; scaleY = k
        },
        description = statusLabel(s),
    )
}

/** "45m · 7:00–7:30 /km · 100% of time target": the target, then where the session stands. */
internal fun sessionProgress(s: PlanSession): String = listOfNotNull(
    planTarget(s).ifEmpty { null },
    when (s.status) {
        "done", "partial" -> s.pct?.let { targetShare(s, it) }
        "missed" -> tr("missed", "terlewat")
        "skipped" -> tr("skipped", "dilewati")
        "today" -> tr("still to do", "belum dikerjakan")
        else -> null
    },
).joinToString(" · ")

/**
 * Today before the session: the session is the headline, set on the background. The target follows, then
 * only what changes it (a recovery adjustment, or why low readiness does not), the aim with its steps, and
 * the way into the plan. [onStartWorkout] is null for viewers.
 */
@Composable
fun PlanTodayFocus(p: PlanToday, verdict: Verdict?, onOpen: () -> Unit, onStartWorkout: ((String) -> Unit)? = null) {
    val day = p.daySessions
    val s = p.pending() ?: day.firstOrNull() ?: return
    val finished = s.status == "done" || s.status == "partial"
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(tr("Today's session", "Sesi hari ini"), modifier = Modifier.weight(1f))
                if (finished) PlanStatusMark(s, 22.dp)
            }
            Text(s.title.text, style = T.headline, color = K.ink)
            planTarget(s).takeIf { it.isNotEmpty() }?.let { Text(it, style = body(16.sp, FontWeight.SemiBold, 1.35f), color = K.body2) }
            p.adjust?.takeIf { s == p.session }?.let { a -> targetText(a.target)?.let { Text(tr("Adjusted for recovery: $it", "Disesuaikan karena belum pulih: $it"), style = body(14.sp, FontWeight.SemiBold), color = K.fair) } }
            s.pct?.let {
                Text(targetShare(s, it), Modifier.padding(top = 4.dp), style = body(13.sp, FontWeight.SemiBold), color = statusColor(s.status))
                SpanBar(0f, it.toFloat(), statusColor(s.status), height = 6.dp)
            }
        }
        // only what changes the session: the adjustment's reason, or why low readiness leaves it as is
        val why = if (p.adjust != null && s == p.session && verdict != null) todayAdvice(verdict, p)?.first else verdict?.let { planStandsNote(it) }
        why?.let { Text(it, Modifier.padding(horizontal = 4.dp), style = T.body, color = K.body3) }
        s.notes?.let { n ->
            var steps by rememberSaveable(s.date, s.kind) { mutableStateOf(false) }
            Column(Modifier.padding(horizontal = 4.dp)) {
                SessionNotes(n.text, full = steps)
                if (n.text.lines().any { isNoteMarkup(it) }) {
                    Text(
                        if (steps) tr("Hide steps", "Tutup langkah") else tr("Show steps", "Lihat langkah"),
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { steps = !steps }
                            .sizeIn(minHeight = 44.dp).wrapContentHeight(),
                        style = body(14.sp, FontWeight.SemiBold), color = K.ink,
                    )
                }
            }
        }
        StartWorkoutButton(s, onStartWorkout)
        // the rest of the day stays one compact line each, so the session above remains the only headline
        day.filter { it != s }.forEach { o ->
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlanStatusMark(o, 20.dp)
                    Text(
                        listOfNotNull(tr("Also today: ${o.title.text}", "Juga hari ini: ${o.title.text}"), planTarget(o).ifEmpty { null }).joinToString(" · "),
                        style = body(14.sp, FontWeight.SemiBold, 1.35f), color = K.body2,
                    )
                }
                StartWorkoutButton(o, onStartWorkout)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(K.surface).border(1.dp, K.line, RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClickLabel = tr("Open plan", "Buka plan"), onClick = onOpen)
                .sizeIn(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Glyph(R.drawable.ic_event_available, K.muted, 20.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name, style = body(15.sp, FontWeight.SemiBold), color = K.ink, maxLines = 1)
                p.week?.let { Text(weekProgress(it), style = T.caption, color = K.muted) }
            }
            Glyph(R.drawable.ic_chevron_right, K.muted, 20.dp)
        }
    }
}

/** Ink "Start workout" for a strength session with a Strength 28 day that is not done yet; nothing for viewers. */
@Composable
fun StartWorkoutButton(s: PlanSession, onStartWorkout: ((String) -> Unit)?) {
    val w = s.workout ?: return
    if (s.kind != "strength" || s.status !in setOf("today", "upcoming", "missed") || onStartWorkout == null) return
    AccentButton(tr("Start workout", "Mulai workout"), { onStartWorkout("strength28:${w.day}") }, Modifier.fillMaxWidth(), icon = R.drawable.ic_play_arrow)
}

/**
 * The two plan numbers mean different things, so each says what it counts: done is sessions finished out of
 * the week's sessions; the % is the average share of target reached on the sessions already due.
 */
internal fun weekProgress(w: PlanWeek): String {
    // older servers count only done of planned
    if (w.partial == null || w.missed == null) return tr("This week ${w.done} of ${w.planned} done", "Minggu ini ${w.done} dari ${w.planned} selesai")
    val toGo = (w.planned - w.done - w.partial - w.missed).coerceAtLeast(0)
    return tr("This week · ", "Minggu ini · ") + progressLine(PlanCounts(w.done, w.partial, w.missed, w.skipped ?: 0, toGo))
}

/** Average share of target, shown only when it says something the counts do not (below 90%). */
internal fun averageLine(pct: Double?, scored: Int): String? = pct?.takeIf { it < 0.9 }?.let {
    tr("Average ${pctText(it)} of target across $scored scored sessions", "Rata-rata ${pctText(it)} dari target pada $scored sesi yang dinilai")
}

/** Done, short, missed and still to go as one bar, in the order the counts line reads. */
@Composable
internal fun CountsBar(c: PlanCounts) {
    val total = c.done + c.short + c.missed + c.toGo
    if (total == 0) return
    StackedBar(
        listOf(c.done.toDouble() to K.good, c.short.toDouble() to K.fair, c.missed.toDouble() to K.low, c.toGo.toDouble() to K.track),
        description = progressLine(c), height = 6.dp, gap = 2.dp, segmentRadius = 3.dp,
    )
}

@Composable
private fun WeekLine(sessions: List<PlanSession>) {
    val c = planCounts(sessions)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr("This week · ", "Minggu ini · ") + progressLine(c), style = T.caption, color = K.muted)
        CountsBar(c)
    }
}

/** Training tab: this week's sessions at a glance; tap for the whole plan. */
@Composable
fun PlanWeekCard(v: PlanView, owner: Boolean = true, onOpen: () -> Unit) {
    val plan = v.plan
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(K.surface)
            .clickable(role = Role.Button, onClickLabel = tr("Open plan", "Buka plan"), onClick = onOpen).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                CardTitle(tr("Training plan", "Training plan"), icon = R.drawable.ic_event_available)
                Text(plan?.name ?: tr("No plan yet", "Belum ada plan"), style = T.bodyS, color = K.muted, maxLines = 1)
            }
            Glyph(R.drawable.ic_chevron_right, K.muted)
        }
        val monday = LocalDate.now().let { it.minusDays(it.dayOfWeek.value - 1L) }
        val week = v.weeks.firstOrNull { it.start == monday.toString() }
        week?.goal?.let { Text(it.text, style = T.bodyS, color = K.body3) }
        if (plan == null) {
            Text(
                if (owner) tr("Plan your weeks with Claude: run /training-plan in Claude Code. The plan shows up here and each session is checked against your Garmin data.",
                    "Susun plan bareng Claude: jalankan /training-plan di Claude Code. Plan muncul di sini dan tiap sesi dicek ke data Garmin.")
                else tr("No training plan yet. When the owner saves one, its weeks show up here.", "Belum ada training plan. Kalau pemilik sudah menyimpannya, minggunya muncul di sini."),
                style = T.bodyS, color = K.body2,
            )
            return@Column
        }
        val days = (0..6).map { monday.plusDays(it.toLong()).toString() }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                // ponytail: one mark per day; on a run + strength day the run's mark shows
                val s = v.sessions.filter { it.date == d }.let { l -> l.firstOrNull { it.kind == "run" } ?: l.firstOrNull() }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(weekdayShort(d), style = body(11.sp, FontWeight.SemiBold, 1.2f), color = if (d == LocalDate.now().toString()) K.ink else K.muted)
                    if (s != null) PlanStatusMark(s) else Box(Modifier.width(24.dp).height(24.dp))
                }
            }
        }
        if (week != null) WeekLine(v.sessions.filter { weekOf(it.date) == week.start })
    }
}

/** What the owner can change on a session; null for viewers and for earlier blocks, which are read-only. */
class PlanActions(
    val link: (PlanSession, List<Long>) -> Unit,
    val skip: (PlanSession, String) -> Unit,
    val undo: (PlanSession) -> Unit,
)

/** The plan: this week first with a strip to move between weeks; an empty and a finished screen around it. */
@Composable
fun PlanScreen(
    picked: String?, onPick: (String?) -> Unit, onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit, onOpenLog: (String) -> Unit, onOpenToday: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberCachedLoad("plan") { Repo.api(ctx).plan() }
    val earlier = rememberCachedLoad("plans") { Repo.api(ctx).plans() }
    val role by produceState<String?>(null) { value = Repo.role(ctx) }
    var lookBack by rememberSaveable { mutableStateOf(false) }
    var archive by rememberSaveable { mutableStateOf<String?>(null) }
    fun change(call: suspend ApiService.() -> PlanView) {
        scope.launch {
            runCatching { Repo.api(ctx).call() }
                .onSuccess {
                    loader.reload()
                    runCatching { Repo.fetchPlan(ctx); Repo.fetchToday(ctx) }
                    Widgets.updateAll(ctx)
                }
                .onFailure { Toast.makeText(ctx, it.friendly(), Toast.LENGTH_LONG).show() }
        }
    }
    val actions = PlanActions(
        link = { s, ids -> change { planOverride(OverrideIn(s.date, s.kind, s.n, activityIds = ids)) } },
        skip = { s, why -> change { planOverride(OverrideIn(s.date, s.kind, s.n, skipped = why)) } },
        undo = { s -> change { deletePlanOverride(s.date, s.kind, s.n) } },
    ).takeIf { role == "owner" }
    archive?.let { id -> return ArchivePlan(id, onBack = { archive = null }, onOpenActivity, onOpenLog) }
    val back = { if (lookBack) lookBack = false else onBack() }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        if (picked == null) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { BackLink(tr("Training", "Latihan"), back) }
            SyncButton { loader.reload() }
        }
        StaleNote(loader)
        LoadView(loader, loadingText = tr("Loading your plan", "Memuat plan")) { v ->
            val plan = v.plan
            val body = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
            val blocks = (earlier.state as? Load.Ok)?.data.orEmpty().filter { it.status != "active" && it.id != plan?.id }
            if (plan == null) {
                NoPlan(body, role == "owner", blocks, { archive = it }, onOpenToday)
                return@LoadView
            }
            val sel = picked?.let { k -> v.sessions.firstOrNull { it.key == k } }
            if (sel != null) {
                PlanSessionScreen(sel, weekNo(plan, weekOf(sel.date)), v, actions, { onPick(null) }, onOpenActivity, onOpenLog) { loader.reload() }
                return@LoadView
            }
            if (LocalDate.now() > LocalDate.parse(plan.end) && !lookBack) FinishedPlan(body, v, plan, { lookBack = true }, onOpenActivity, blocks) { archive = it }
            else WeekView(body, v, plan, actions, { onPick(it.key) }, onOpenActivity, blocks) { archive = it }
        }
    }
}

/** An earlier block, read-only: the same week view and session screens, no changes. */
@Composable
private fun ArchivePlan(id: String, onBack: () -> Unit, onOpenActivity: (Long) -> Unit, onOpenLog: (String) -> Unit) {
    val ctx = LocalContext.current
    val loader = rememberCachedLoad("plan_archive", id) { Repo.api(ctx).planById(id) }
    var picked by rememberSaveable(id) { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        if (picked == null) BackLink(tr("Current plan", "Plan sekarang"), onBack)
        LoadView(loader, loadingText = tr("Loading the block", "Memuat blok")) { v ->
            val plan = v.plan ?: return@LoadView
            val sel = picked?.let { k -> v.sessions.firstOrNull { it.key == k } }
            if (sel != null) PlanSessionScreen(sel, weekNo(plan, weekOf(sel.date)), v, null, { picked = null }, onOpenActivity, onOpenLog)
            else WeekView(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), v, plan, null, { picked = it.key }, onOpenActivity, emptyList()) {}
        }
    }
}

/** Stable id of a session inside its plan; Home holds it so other tabs can open one. */
val PlanSession.key get() = "$date/$kind/$n"

internal fun weekOf(date: String) = LocalDate.parse(date).let { it.minusDays(it.dayOfWeek.value - 1L) }.toString()

internal fun weekNo(plan: PlanMeta, start: String) =
    ChronoUnit.WEEKS.between(LocalDate.parse(weekOf(plan.start)), LocalDate.parse(start)).toInt() + 1

@Composable
private fun StripRow(scroll: ScrollState, width: Dp, userScroll: Boolean = true, content: @Composable RowScope.() -> Unit) {
    // requiredWidth undoes the screen's 16dp side padding so chips run to the edges and the next one peeks
    Row(
        Modifier.requiredWidth(width + 32.dp).horizontalScroll(scroll, enabled = userScroll).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

@Composable
private fun WeekChip(n: Int, start: String, ratio: Float, current: Boolean, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(64.dp).sizeIn(minHeight = 64.dp).clip(shape).background(if (selected) K.ink else K.surface)
            .border(1.dp, if (selected) K.ink else if (current) K.ink.copy(alpha = 0.6f) else K.line, shape)
            .selectable(selected, role = Role.Tab, onClick = onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("W$n", style = head(15.sp, -0.02f), color = if (selected) K.onAccent else K.ink)
        Text(dayMonth(start), style = body(11.5.sp), color = if (selected) Color(0xFF3B423C) else K.muted, maxLines = 1, softWrap = false)
        SpanBar(0f, ratio, if (selected) K.onAccent else K.good, height = 4.dp)
    }
}

/**
 * Plan header: the short plan name as the title, the stored goal in full as its own paragraph, the race, then
 * where the sessions stand as counts that add up (done, short, missed, to go) on one bar. The average share of
 * target only shows when it is low enough to say something the counts do not.
 */
@Composable
private fun PlanHeader(v: PlanView, plan: PlanMeta, raceLine: String) {
    val c = planCounts(v.sessions)
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(plan.name, style = T.screenTitle, color = K.ink)
        Label(raceLine)
        Text(plan.goal.text, style = T.body, color = K.body3)
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RollingText("${c.done}", num(36.sp, lh = 0.95f), K.ink, Modifier.alignByBaseline())
                Text(tr(if (c.done == 1) "session done" else "sessions done", "sesi selesai"), Modifier.alignByBaseline(), style = body(15.sp, FontWeight.SemiBold), color = K.body2)
            }
            CountsBar(c)
            Text(progressLine(c), style = T.caption, color = K.muted)
            averageLine(v.overall, v.sessions.count { it.pct != null })?.let { Text(it, style = T.caption, color = K.muted) }
        }
    }
}

private fun raceLine(plan: PlanMeta, today: LocalDate): String? = plan.raceDate?.let { r ->
    when (val left = ChronoUnit.DAYS.between(today, LocalDate.parse(r))) {
        0L -> tr("Race day", "Hari lomba")
        1L -> tr("Race tomorrow", "Lomba besok")
        in 2..Long.MAX_VALUE -> tr("Race on ${weekdayShort(r)} ${dayMonth(r)}, in $left days", "Lomba ${weekdayShort(r)} ${dayMonth(r)}, $left hari lagi")
        else -> null
    }
}

@Composable
private fun WeekView(
    modifier: Modifier, v: PlanView, plan: PlanMeta, actions: PlanActions?,
    onOpen: (PlanSession) -> Unit, onOpenActivity: (Long) -> Unit,
    blocks: List<PlanListItem>, onOpenBlock: (String) -> Unit,
) {
    val today = LocalDate.now()
    val cur = v.weeks.indexOfFirst { it.start == weekOf(today.toString()) }.takeIf { it >= 0 }
        ?: if (today < LocalDate.parse(plan.start)) 0 else v.weeks.lastIndex
    var sel by rememberSaveable(plan.id) { mutableIntStateOf(-1) }
    val s = if (sel < 0) cur else sel.coerceAtMost(v.weeks.lastIndex)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PlanHeader(v, plan, raceLine(plan, today) ?: "${dayMonth(plan.start)} – ${dayMonth(plan.end)}")

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val scroll = rememberScrollState()
            val density = LocalDensity.current
            val reduce = LocalReduceMotion.current
            var placed by remember { mutableStateOf(false) }
            LaunchedEffect(s, maxWidth) {
                val max = snapshotFlow { scroll.maxValue }.first { it > 0 }
                val t = with(density) { (s * 72 + 32).dp.toPx() - maxWidth.toPx() / 2 }.roundToInt().coerceIn(0, max)
                if (!placed || reduce) scroll.scrollTo(t) else scroll.animateScrollTo(t)
                placed = true
            }
            StripRow(scroll, maxWidth) {
                v.weeks.forEachIndexed { i, w ->
                    WeekChip(weekNo(plan, w.start), w.start, if (w.planned > 0) w.done.toFloat() / w.planned else 0f, i == cur, i == s) { sel = i }
                }
            }
        }
        val w = v.weeks.getOrNull(s) ?: return@Column
        val rows = v.sessions.filter { weekOf(it.date) == w.start }
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val ago = ChronoUnit.WEEKS.between(LocalDate.parse(w.start), LocalDate.parse(weekOf(today.toString()))).toInt()
                    val tag = when {
                        ago == 0 -> tr("This week", "Minggu ini")
                        ago == 1 -> tr("Last week", "Minggu lalu")
                        ago > 1 -> tr("$ago weeks ago", "$ago minggu lalu")
                        ago == -1 -> tr("Next week", "Minggu depan")
                        else -> tr("In ${-ago} weeks", "${-ago} minggu lagi")
                    }
                    Text("$tag · ${dayMonth(w.start)}–${dayMonth(LocalDate.parse(w.start).plusDays(6).toString())}", style = T.sectionTitle, color = K.ink)
                    val c = planCounts(rows)
                    val progress = if (ago < 0) tr("${w.planned} planned", "${w.planned} direncanakan") else progressLine(c)
                    Text(tr("Week ${weekNo(plan, w.start)} · $progress", "Minggu ${weekNo(plan, w.start)} · $progress"), style = T.caption, color = K.muted)
                }
                if (s != cur) Text(
                    tr("This week", "Minggu ini"),
                    Modifier.clip(CircleShape).background(K.track).clickable(role = Role.Button) { sel = -1 }
                        .sizeIn(minHeight = 44.dp).padding(horizontal = 16.dp).wrapContentHeight(),
                    style = body(13.sp, FontWeight.SemiBold), color = K.ink,
                )
            }
            w.goal?.let { Text(it.text, Modifier.padding(horizontal = 4.dp), style = T.bodyS, color = K.body3) }
            // agenda: one row per day, the date in a column of its own so the week scans top to bottom
            Column(Modifier.padding(top = 6.dp)) {
                rows.groupBy { it.date }.entries.forEachIndexed { i, (date, list) ->
                    if (i > 0) HorizontalDivider(color = K.line)
                    AgendaDay(date, list, onOpen)
                }
            }
        }

        val extras = v.extras.filter { weekOf(it.date) == w.start }
        if (extras.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth().padding(top = 8.dp), gap = 4.dp) {
                CardTitle(tr("Outside the plan this week", "Di luar plan minggu ini"))
                extras.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = K.line)
                    ExtraRow(e, v.sessions, actions, onOpenActivity)
                }
            }
        }
        EarlierBlocks(blocks, onOpenBlock)
    }
}

/** A session an unplanned activity could count for: same kind, within 3 days, not done and not in the future. */
internal fun countableFor(e: PlanExtra, sessions: List<PlanSession>): PlanSession? {
    val d = LocalDate.parse(e.date)
    return sessions.filter { it.kind == kindOf(e.type) && it.status in setOf("missed", "partial", "today") }
        .filter { kotlin.math.abs(ChronoUnit.DAYS.between(d, LocalDate.parse(it.date))) <= 3 }
        .minByOrNull { kotlin.math.abs(ChronoUnit.DAYS.between(d, LocalDate.parse(it.date))) }
}

@Composable
private fun ExtraRow(e: PlanExtra, sessions: List<PlanSession>, actions: PlanActions?, onOpenActivity: (Long) -> Unit) {
    val target = actions?.let { countableFor(e, sessions) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenActivity(e.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.name ?: activityType(e.type), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                Text("${weekday(e.date)} ${dayMonth(e.date)}", style = T.caption, color = K.muted)
            }
            e.distanceM?.takeIf { it > 0 }?.let { Text("${km(it)} km", style = num(18.sp, FontWeight.Bold), color = K.ink) }
        }
        if (target != null && actions != null) Text(
            tr("Count for ${weekdayShort(target.date)} · ${target.title.text}", "Hitung untuk ${weekdayShort(target.date)} · ${target.title.text}"),
            Modifier.clip(CircleShape).background(K.track).clickable(role = Role.Button) { actions.link(target, target.activityIds + e.id) }
                .sizeIn(minHeight = 44.dp).padding(horizontal = 16.dp).wrapContentHeight(),
            style = body(13.sp, FontWeight.SemiBold), color = K.ink, maxLines = 1,
        )
        if (target != null) androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
    }
}

/** Blocks saved before this one, newest first; each opens read-only. */
@Composable
private fun EarlierBlocks(blocks: List<PlanListItem>, onOpen: (String) -> Unit) {
    if (blocks.isEmpty()) return
    Column {
        SectionHeader(tr("Earlier blocks", "Blok sebelumnya"))
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        blocks.forEachIndexed { i, b ->
            if (i > 0) HorizontalDivider(color = K.line)
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(b.id) }.sizeIn(minHeight = 56.dp).padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(b.name, style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                    Text("${dayMonthYear(b.start)} – ${dayMonthYear(b.end)} · " + tr("${b.planned} sessions", "${b.planned} sesi"), style = T.caption, color = K.muted)
                }
                Glyph(R.drawable.ic_chevron_right, K.muted, 18.dp)
            }
        }
    }
}

@Composable
private fun FinishedPlan(
    modifier: Modifier, v: PlanView, plan: PlanMeta,
    onLookBack: () -> Unit, onOpenActivity: (Long) -> Unit, blocks: List<PlanListItem>, onOpenBlock: (String) -> Unit,
) {
    val race = v.sessions.filter { it.type == "race" }.maxByOrNull { it.date }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PlanHeader(v, plan, tr("Plan ended ${weekdayShort(plan.end)} ${dayMonth(plan.end)} · ${v.weeks.size} weeks", "Plan selesai ${weekdayShort(plan.end)} ${dayMonth(plan.end)} · ${v.weeks.size} minggu"))
        if (race != null) Panel(Modifier.fillMaxWidth(), padding = 18.dp, gap = 14.dp) {
                val id = race.activityIds.firstOrNull()
                Row(
                    Modifier.fillMaxWidth().then(if (id != null) Modifier.clickable(role = Role.Button) { onOpenActivity(id) } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(tr("Race · ${weekdayShort(race.date)} ${dayMonth(race.date)}", "Lomba · ${weekdayShort(race.date)} ${dayMonth(race.date)}"), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                        Text(planTarget(race), style = T.caption, color = K.muted)
                    }
                    race.done?.durationS?.let { Text(duration(it), style = num(30.sp), color = K.good) }
                }
        }
        SecondaryButton(tr("Look back week by week", "Lihat lagi per minggu"), onLookBack, Modifier.fillMaxWidth())
        Text(
            tr("The next plan saved with /training-plan replaces this one.", "Plan berikutnya yang disimpan lewat /training-plan menggantikan yang ini."),
            Modifier.padding(horizontal = 4.dp), style = T.caption, color = K.muted,
        )
        EarlierBlocks(blocks, onOpenBlock)
    }
}

@Composable
private fun NoPlan(modifier: Modifier, owner: Boolean, blocks: List<PlanListItem>, onOpenBlock: (String) -> Unit, onOpenToday: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr("Training plan", "Training plan"), Modifier.padding(horizontal = 4.dp), style = T.screenTitle, color = K.ink)
        BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            StripRow(rememberScrollState(), maxWidth, userScroll = false) {
                repeat(6) {
                    Box(
                        Modifier.width(64.dp).height(64.dp).drawBehind {
                            val w = 1.5.dp.toPx()
                            drawRoundRect(
                                K.border, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(16.dp.toPx()),
                                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                            )
                        },
                    )
                }
            }
        }
        Panel(Modifier.fillMaxWidth(), padding = 22.dp, gap = 12.dp) {
            Text(tr("No plan yet", "Belum ada plan"), style = head(20.sp, -0.025f), color = K.ink)
            Text(
                if (owner) tr("Plans are written with Claude on the laptop using /training-plan. Once saved, the weeks show up here and each session is scored against your watch.",
                    "Plan disusun bareng Claude di laptop lewat /training-plan. Setelah disimpan, minggunya muncul di sini dan tiap sesi dinilai dari data jam tangan.")
                else tr("When the owner saves a plan, its weeks show up here and each session is scored against the watch.",
                    "Kalau pemilik sudah menyimpan plan, minggunya muncul di sini dan tiap sesi dinilai dari data jam tangan."),
                style = T.bodyS, color = K.body3,
            )
            Text(tr("Until then, Today's advice follows readiness.", "Sampai saat itu, saran di Today mengikuti readiness."), style = T.bodyS, color = K.body3)
            SecondaryButton(tr("Open Today", "Buka Today"), onOpenToday, Modifier.fillMaxWidth())
        }
        EarlierBlocks(blocks, onOpenBlock)
    }
}

private val PlanSession.isToday get() = date == LocalDate.now().toString()

/** One agenda day: weekday and date on the left (today in an ink pill), that day's sessions on the right. */
@Composable
private fun AgendaDay(date: String, list: List<PlanSession>, onOpen: (PlanSession) -> Unit) {
    val isToday = date == LocalDate.now().toString()
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.padding(top = 6.dp).width(44.dp).clip(RoundedCornerShape(12.dp)).background(if (isToday) K.ink else Color.Transparent).padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(weekdayShort(date), style = body(11.sp, FontWeight.SemiBold, 1.2f), color = if (isToday) K.onAccent else K.muted)
            Text("${LocalDate.parse(date).dayOfMonth}", style = num(22.sp, FontWeight.Bold, lh = 1f), color = if (isToday) K.onAccent else K.ink)
        }
        Column(Modifier.weight(1f)) { list.forEach { SessionRow(it) { onOpen(it) } } }
    }
}

/** "done 8.01 km", "12 of 14 sets", "skipped · sick", "done Wed": what happened, after the target. */
internal fun resultText(s: PlanSession): String? {
    val finished = s.status == "done" || s.status == "partial"
    return when {
        s.status == "missed" -> tr("missed", "terlewat")
        s.status == "skipped" -> listOfNotNull(tr("skipped", "dilewati"), s.skipped?.let { skipReason(it).lowercase() }).joinToString(" · ")
        finished && s.done?.sets != null -> tr("${s.done.sets} of ${s.done.of} sets", "${s.done.sets} dari ${s.done.of} set")
        finished && s.doneOn != null -> tr("done ${weekdayShort(s.doneOn)}", "dikerjakan ${weekdayShort(s.doneOn)}") +
            (s.done?.distanceM?.takeIf { (s.target?.distanceM ?: 0.0) > 0 }?.let { " · ${km(it)} km" } ?: "")
        finished && s.target?.distanceM != null && s.done != null -> tr("done ${km(s.done.distanceM)} km", "tercapai ${km(s.done.distanceM)} km")
        finished -> tr("done", "tercapai")
        else -> null
    }
}

fun skipReason(r: String) = when (r) {
    "sick" -> tr("Sick", "Sakit")
    "travel" -> tr("Travel", "Perjalanan")
    "rest" -> tr("Needed rest", "Butuh istirahat")
    else -> tr("Other", "Lainnya")
}

/** "Adjusted for recovery: 4.2 km (plan 6 km)" when a stored adjustment changed the target. */
internal fun adjustedText(s: PlanSession): String? {
    val now = targetText(s.adjust?.target) ?: return null
    val plan = targetText(s.target)
    return tr("Adjusted for recovery: $now", "Disesuaikan karena belum pulih: $now") + (plan?.let { tr(" (plan $it)", " (plan $it)") } ?: "")
}

/** Title, then target and result, the status mark and % at the right. Rows with nothing to open are not clickable. */
@Composable
private fun SessionRow(s: PlanSession, onOpen: () -> Unit) {
    val rest = s.status == "rest"
    val meta = if (rest) "" else listOfNotNull(planTarget(s).ifEmpty { null }, resultText(s)).joinToString(" · ")
    val open = s.kind != "rest" || s.notes != null
    Row(
        Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp).clip(RoundedCornerShape(12.dp))
            .then(if (open) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.title.text, style = body(15.sp, FontWeight.SemiBold), color = if (rest || s.status == "skipped") K.muted else K.ink)
            if (meta.isNotEmpty()) Text(meta, style = T.caption, color = K.muted)
            adjustedText(s)?.let { Text(it, style = T.caption, color = K.fair) }
            s.workout?.let { Text(tr("Strength 28 · Day ${it.day}", "Strength 28 · Hari ${it.day}"), style = T.caption, color = K.muted) }
        }
        s.pct?.let { Text(pctText(it), style = num(18.sp, FontWeight.Bold), color = if (s.status == "partial") K.fair else K.body2) }
        PlanStatusMark(s, 22.dp)
        if (open) Glyph(R.drawable.ic_chevron_right, K.muted, 18.dp)
    }
}

private fun isNoteMarkup(line: String) = line.startsWith("## ") || line.startsWith("- ") || NUMBERED.containsMatchIn(line)
internal val NUMBERED = Regex("""^\d+\. """)

/**
 * Session notes from the plan: text before the first "## " heading is the session's aim, then
 * "## " headings, "1. " steps and "- " bullets with a hanging indent so wrapped lines stay aligned.
 * [full] false shows only the aim.
 */
@Composable
private fun SessionNotes(text: String, full: Boolean) {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val aimEnd = lines.indexOfFirst { isNoteMarkup(it) }.let { if (it < 0) lines.size else it }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEachIndexed { i, l ->
            if (!full && i >= aimEnd) return@forEachIndexed
            val step = NUMBERED.find(l)
            when {
                i < aimEnd -> Text(l, style = T.bodyS, color = K.body2)
                l.startsWith("## ") -> Text(l.removePrefix("## "), Modifier.padding(top = 6.dp), style = body(13.5.sp, FontWeight.SemiBold), color = K.ink)
                step != null || l.startsWith("- ") -> Row {
                    Text(
                        step?.value?.trim()?.removeSuffix(".") ?: "•",
                        Modifier.width(20.dp).alignByBaseline(),
                        style = if (step != null) num(15.sp, FontWeight.Bold) else T.bodyS, color = K.muted,
                    )
                    Text(l.substring(step?.value?.length ?: 2), Modifier.weight(1f).alignByBaseline(), style = T.bodyS, color = K.body3)
                }
                else -> Text(l, style = T.bodyS, color = K.body3)
            }
        }
    }
}

@Composable
internal fun StatusPill(text: String, c: Color) {
    Text(
        text,
        Modifier.clip(CircleShape).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        style = body(12.sp, FontWeight.Bold, 1.2f), color = c, textAlign = TextAlign.Center,
    )
}
