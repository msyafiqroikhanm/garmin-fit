package dev.garminfit.ui

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
    "today" -> tr("Today", "Hari ini")
    "rest" -> tr("Rest", "Istirahat")
    else -> tr("Planned", "Rencana")
}

@DrawableRes
private fun statusIcon(status: String) = when (status) {
    "done" -> R.drawable.ic_check_circle
    "partial" -> R.drawable.ic_check_circle
    "missed" -> R.drawable.ic_cancel
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
fun PlanStatusMark(s: PlanSession, size: androidx.compose.ui.unit.Dp = 24.dp) {
    val pop = rememberReveal(s.date + s.status, durationMs = 450)
    val finished = s.status == "done" || s.status == "partial"
    Glyph(
        statusIcon(s.status), statusColor(s.status), size,
        Modifier.graphicsLayer {
            val k = if (finished) 0.6f + 0.4f * pop.value else 1f
            scaleX = k; scaleY = k
        },
        description = statusLabel(s),
    )
}

/** Today: what the plan says for today, and how much of it is already done. [onStartWorkout] is null for viewers. */
@Composable
fun PlanTodayCard(p: PlanToday, onOpen: () -> Unit, onStartWorkout: ((String) -> Unit)? = null) {
    val s = p.session
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(K.surface)
            .clickable(role = Role.Button, onClickLabel = tr("Open plan", "Buka plan"), onClick = onOpen).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(tr("Today's plan", "Plan hari ini"), Modifier.weight(1f), icon = R.drawable.ic_event_available)
            Glyph(R.drawable.ic_chevron_right, K.muted)
        }
        if (s == null) {
            Text(tr("Nothing planned today. Rest or move easy.", "Tidak ada sesi hari ini. Istirahat atau gerak santai."), style = T.body, color = K.body2)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanStatusMark(s, 28.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.title.text, style = head(20.sp, -0.025f), color = K.ink)
                    planTarget(s).takeIf { it.isNotEmpty() }?.let { Text(it, style = T.bodyS, color = K.muted) }
                    p.adjust?.let { a -> targetText(a.target)?.let { Text(tr("Adjusted for recovery: $it", "Disesuaikan karena belum pulih: $it"), style = T.bodyS, color = K.fair) } }
                }
                if (s.pct != null) RollingText(pctText(s.pct), num(28.sp), statusColor(s.status))
            }
            s.pct?.let { SpanBar(0f, it.toFloat(), statusColor(s.status)) }
            s.notes?.let { n ->
                var steps by rememberSaveable(s.date, s.kind) { mutableStateOf(false) }
                SessionNotes(n.text, full = steps)
                if (n.text.lines().any { isNoteMarkup(it) }) {
                    Text(
                        if (steps) tr("Hide steps", "Tutup langkah") else tr("Show steps", "Lihat langkah"),
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { steps = !steps }
                            .sizeIn(minHeight = 44.dp).wrapContentHeight(),
                        style = body(14.sp, FontWeight.SemiBold), color = K.good,
                    )
                }
            }
            val w = s.workout
            if (w != null && s.kind == "strength" && s.status in setOf("today", "upcoming", "missed") && onStartWorkout != null) {
                AccentButton(tr("Start Day ${w.day}", "Mulai Day ${w.day}"), { onStartWorkout("strength28:${w.day}") }, icon = R.drawable.ic_play_arrow)
            }
        }
        p.week?.let { WeekLine(it) }
    }
}

@Composable
private fun WeekLine(w: PlanWeek) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(tr("This week ${w.done}/${w.planned} sessions", "Minggu ini ${w.done}/${w.planned} sesi"), Modifier.weight(1f), style = T.caption, color = K.muted)
            Text(pctText(w.pct), style = body(12.sp, FontWeight.Bold), color = K.ink)
        }
        if (w.planned > 0) SegmentBar(w.done.toDouble(), K.good, of = w.planned)
    }
}

/** Training tab: this week's sessions at a glance; tap for the whole plan. */
@Composable
fun PlanWeekCard(v: PlanView, onOpen: () -> Unit) {
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
                tr("Plan your weeks with Claude: run /training-plan in Claude Code. The plan shows up here and each session is checked against your Garmin data.",
                    "Susun plan bareng Claude: jalankan /training-plan di Claude Code. Plan muncul di sini dan tiap sesi dicek ke data Garmin."),
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
        week?.let { WeekLine(it) }
    }
}

/** The whole plan: overall score, then each week as a list of sessions you can expand. */
@Composable
fun PlanScreen(onBack: () -> Unit, onOpenActivity: (Long) -> Unit) {
    val ctx = LocalContext.current
    val loader = rememberCachedLoad("plan") { Repo.api(ctx).plan() }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        if (picked == null) BackLink(tr("Training", "Latihan"), onBack)
        LoadView(
            loader,
            loadingText = tr("Loading your plan", "Memuat plan"),
            isEmpty = { it.plan == null },
            emptyText = tr("No training plan yet. Run /training-plan in Claude Code to make one.", "Belum ada training plan. Jalankan /training-plan di Claude Code untuk bikin."),
        ) { v ->
            val plan = v.plan!!
            val sel = picked?.let { k -> v.sessions.firstOrNull { it.key == k } }
            if (sel != null) {
                val first = LocalDate.parse(weekOf(plan.start))
                PlanSessionScreen(sel, ChronoUnit.WEEKS.between(first, LocalDate.parse(weekOf(sel.date))).toInt() + 1, { picked = null }, onOpenActivity)
                return@LoadView
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.fillMaxWidth(), padding = 20.dp, gap = 10.dp) {
                    Label(plan.name)
                    Text(plan.goal.text, style = T.screenTitle.copy(fontSize = 28.sp), color = K.ink)
                    Text(
                        "${dayMonth(plan.start)} – ${dayMonth(plan.end)}" + (plan.raceDate?.let { r ->
                            val left = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(r))
                            if (left >= 0) tr(" · race in $left days", " · lomba $left hari lagi") else ""
                        } ?: ""),
                        style = T.bodyS, color = K.muted,
                    )
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RollingText(pctText(v.overall), T.hero, K.good)
                        Text(tr("achieved so far", "tercapai sejauh ini"), Modifier.padding(bottom = 10.dp), style = T.bodyS, color = K.muted)
                    }
                    val scored = v.sessions.count { it.kind != "rest" }
                    val done = v.sessions.count { it.status == "done" }
                    if (scored > 0) SegmentBar(done.toDouble(), K.good, of = scored.coerceAtMost(40))
                    Text(tr("$done of $scored sessions done. Each session scores distance (or time) done against its target.", "$done dari $scored sesi selesai. Tiap sesi dinilai dari jarak (atau waktu) yang dicapai dibanding target."), style = T.caption, color = K.muted)
                }
                v.weeks.forEach { w -> WeekCard(w, v.sessions.filter { weekOf(it.date) == w.start }) { picked = it.key } }
                if (v.extras.isNotEmpty()) {
                    Panel(Modifier.fillMaxWidth(), gap = 4.dp) {
                        CardTitle(tr("Outside the plan", "Di luar plan"))
                        v.extras.forEach { e ->
                            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenActivity(e.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.name ?: activityType(e.type), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                                    Text("${weekday(e.date)} ${dayMonth(e.date)}", style = T.caption, color = K.muted)
                                }
                                e.distanceM?.takeIf { it > 0 }?.let { Text("${km(it)} km", style = num(18.sp, FontWeight.Bold), color = K.ink) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val PlanSession.key get() = "$date/$kind"

private fun weekOf(date: String) = LocalDate.parse(date).let { it.minusDays(it.dayOfWeek.value - 1L) }.toString()

@Composable
private fun WeekCard(w: PlanWeek, sessions: List<PlanSession>, onOpen: (PlanSession) -> Unit) {
    Panel(Modifier.fillMaxWidth(), gap = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(tr("Week of ${dayMonth(w.start)}", "Minggu ${dayMonth(w.start)}"), Modifier.weight(1f))
            Text("${w.done}/${w.planned} · ${pctText(w.pct)}", style = num(18.sp, FontWeight.Bold), color = if (w.pct == null) K.muted else K.ink)
        }
        w.goal?.let { Text(it.text, Modifier.padding(bottom = 4.dp), style = T.bodyS, color = K.body3) }
        sessions.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = K.track)
            SessionRow(s) { onOpen(s) }
        }
    }
}

/** Status mark · title and day · status chip; tap opens the session. */
@Composable
private fun SessionRow(s: PlanSession, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlanStatusMark(s)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.title.text, style = body(15.sp, FontWeight.SemiBold), color = if (s.status == "rest") K.muted else K.ink)
            Text(listOf("${weekday(s.date)} ${dayMonth(s.date)}", planTarget(s)).filter { it.isNotEmpty() }.joinToString(" · "), style = T.caption, color = K.muted)
            s.workout?.let { Text("Strength 28 · Day ${it.day}", style = T.caption, color = K.muted) }
        }
        if (s.status != "rest" && s.status != "upcoming") StatusPill(statusLabel(s), statusColor(s.status))
        Glyph(R.drawable.ic_chevron_right, K.muted, 20.dp)
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
