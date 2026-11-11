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

/** The plan: this week first with a strip to move between weeks; an empty and a finished screen around it. */
@Composable
fun PlanScreen(onBack: () -> Unit, onOpenActivity: (Long) -> Unit, onOpenToday: () -> Unit) {
    val ctx = LocalContext.current
    val loader = rememberCachedLoad("plan") { Repo.api(ctx).plan() }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var lookBack by rememberSaveable { mutableStateOf(false) }
    val back = { if (lookBack) lookBack = false else onBack() }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        if (picked == null) BackLink(tr("Training", "Latihan"), back)
        LoadView(loader, loadingText = tr("Loading your plan", "Memuat plan")) { v ->
            val plan = v.plan
            val body = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
            if (plan == null) {
                NoPlan(body, onOpenToday)
                return@LoadView
            }
            val sel = picked?.let { k -> v.sessions.firstOrNull { it.key == k } }
            if (sel != null) {
                PlanSessionScreen(sel, weekNo(plan, weekOf(sel.date)), { picked = null }, onOpenActivity)
                return@LoadView
            }
            val scored = v.sessions.count { it.kind != "rest" }
            val done = v.sessions.count { it.status == "done" }
            if (LocalDate.now() > LocalDate.parse(plan.end) && !lookBack) FinishedPlan(body, v, plan, done, scored, { lookBack = true }, onOpenActivity)
            else WeekView(body, v, plan, done, scored, { picked = it.key }, onOpenActivity)
        }
    }
}

private val PlanSession.key get() = "$date/$kind"

private fun weekOf(date: String) = LocalDate.parse(date).let { it.minusDays(it.dayOfWeek.value - 1L) }.toString()

private fun weekNo(plan: PlanMeta, start: String) =
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
            .border(1.dp, if (selected) K.ink else if (current) K.good else K.line, shape)
            .selectable(selected, role = Role.Tab, onClick = onClick).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("W$n", style = head(15.sp, -0.02f), color = if (selected) K.onAccent else K.ink)
        Text(dayMonth(start), style = body(11.5.sp), color = if (selected) Color(0xFF3B423C) else K.muted, maxLines = 1, softWrap = false)
        SpanBar(0f, ratio, if (selected) K.onAccent else K.good, height = 4.dp)
    }
}

@Composable
private fun WeekView(
    modifier: Modifier, v: PlanView, plan: PlanMeta, done: Int, scored: Int,
    onOpen: (PlanSession) -> Unit, onOpenActivity: (Long) -> Unit,
) {
    val today = LocalDate.now()
    val cur = v.weeks.indexOfFirst { it.start == weekOf(today.toString()) }.takeIf { it >= 0 }
        ?: if (today < LocalDate.parse(plan.start)) 0 else v.weeks.lastIndex
    var sel by rememberSaveable { mutableIntStateOf(-1) }
    val s = if (sel < 0) cur else sel.coerceAtMost(v.weeks.lastIndex)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(plan.goal.text, style = T.screenTitle, color = K.ink)
            Label(
                plan.raceDate?.let { r ->
                    val left = ChronoUnit.DAYS.between(today, LocalDate.parse(r))
                    if (left >= 0) tr("Race on ${weekdayShort(r)} ${dayMonth(r)}, in $left days", "Lomba ${weekdayShort(r)} ${dayMonth(r)}, $left hari lagi") else null
                } ?: "${dayMonth(plan.start)} – ${dayMonth(plan.end)}",
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RollingText(pctText(v.overall), num(30.sp), K.good)
                SpanBar(0f, (v.overall ?: 0.0).toFloat(), K.good, height = 6.dp, modifier = Modifier.weight(1f))
                Text(tr("$done of $scored done", "$done dari $scored selesai"), style = T.caption, color = K.muted)
            }
        }

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
        Panel(Modifier.fillMaxWidth(), gap = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val ago = ChronoUnit.WEEKS.between(LocalDate.parse(w.start), LocalDate.parse(weekOf(today.toString()))).toInt()
                    val tag = when {
                        ago == 0 -> tr("This week", "Minggu ini")
                        ago == 1 -> tr("Last week", "Minggu lalu")
                        ago > 1 -> tr("$ago weeks ago", "$ago minggu lalu")
                        ago == -1 -> tr("Next week", "Minggu depan")
                        else -> tr("In ${-ago} weeks", "${-ago} minggu lagi")
                    }
                    Text("$tag · ${dayMonth(w.start)}–${dayMonth(LocalDate.parse(w.start).plusDays(6).toString())}", style = head(20.sp, -0.025f), color = K.ink)
                    val progress = if (ago < 0) tr("${w.planned} planned", "${w.planned} direncanakan") else tr("${w.done} of ${w.planned} done", "${w.done} dari ${w.planned} selesai")
                    Text(tr("Week ${weekNo(plan, w.start)} · $progress", "Minggu ${weekNo(plan, w.start)} · $progress"), style = T.caption, color = K.muted)
                }
                if (s != cur) Text(
                    tr("This week", "Minggu ini"),
                    Modifier.clip(CircleShape).background(K.track).clickable(role = Role.Button) { sel = -1 }
                        .sizeIn(minHeight = 44.dp).padding(horizontal = 16.dp).wrapContentHeight(),
                    style = body(13.sp, FontWeight.SemiBold), color = K.ink,
                )
            }
            w.goal?.let { Text(it.text, style = T.bodyS, color = K.body3) }
            Column {
                rows.forEachIndexed { i, r ->
                    if (i > 0 && !r.isToday && !rows[i - 1].isToday) HorizontalDivider(color = K.line)
                    SessionRow(r) { onOpen(r) }
                }
            }
        }

        val extras = v.extras.filter { weekOf(it.date) == w.start }
        if (extras.isNotEmpty()) {
            Panel(Modifier.fillMaxWidth(), gap = 4.dp) {
                CardTitle(tr("Outside the plan this week", "Di luar plan minggu ini"))
                extras.forEach { e ->
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

@Composable
private fun FinishedPlan(
    modifier: Modifier, v: PlanView, plan: PlanMeta, done: Int, scored: Int,
    onLookBack: () -> Unit, onOpenActivity: (Long) -> Unit,
) {
    val race = v.sessions.filter { it.type == "race" }.maxByOrNull { it.date }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(plan.goal.text, style = T.screenTitle, color = K.ink)
            Label(tr("Plan ended ${weekdayShort(plan.end)} ${dayMonth(plan.end)} · ${v.weeks.size} weeks", "Plan selesai ${weekdayShort(plan.end)} ${dayMonth(plan.end)} · ${v.weeks.size} minggu"))
        }
        Panel(Modifier.fillMaxWidth(), padding = 22.dp, gap = 14.dp) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RollingText(pctText(v.overall), num(64.sp), K.good)
                Text(
                    tr("of the plan achieved · $done of $scored done", "plan tercapai · $done dari $scored selesai"),
                    Modifier.weight(1f).padding(bottom = 8.dp), style = T.caption, color = K.muted,
                )
            }
            SpanBar(0f, (v.overall ?: 0.0).toFloat(), K.good, height = 8.dp)
            if (race != null) {
                HorizontalDivider(color = K.line)
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
        }
        SecondaryButton(tr("Look back week by week", "Lihat lagi per minggu"), onLookBack, Modifier.fillMaxWidth())
        Text(
            tr("The next plan you save with /training-plan replaces this one.", "Plan berikutnya yang disimpan lewat /training-plan menggantikan yang ini."),
            Modifier.padding(horizontal = 4.dp), style = T.caption, color = K.muted,
        )
    }
}

@Composable
private fun NoPlan(modifier: Modifier, onOpenToday: () -> Unit) {
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
                tr("Plans are written with Claude on the laptop using /training-plan. Once saved, the weeks show up here and each session is scored against your watch.",
                    "Plan disusun bareng Claude di laptop lewat /training-plan. Setelah disimpan, minggunya muncul di sini dan tiap sesi dinilai dari data jam tangan."),
                style = T.bodyS, color = K.body3,
            )
            Text(tr("Until then, Today's advice follows your readiness.", "Sampai saat itu, saran di Today mengikuti readiness kamu."), style = T.bodyS, color = K.body3)
            SecondaryButton(tr("Open Today", "Buka Today"), onOpenToday, Modifier.fillMaxWidth())
        }
    }
}

private val PlanSession.isToday get() = date == LocalDate.now().toString()

/** Date · title, then target and result; today's row sits on the base fill. Rows with nothing to open are not clickable. */
@Composable
private fun SessionRow(s: PlanSession, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val rest = s.status == "rest"
    val finished = s.status == "done" || s.status == "partial"
    val suffix = when {
        s.status == "missed" -> tr("skipped", "terlewat")
        finished && s.target?.distanceM != null && s.done != null -> tr("done ${km(s.done.distanceM)} km", "tercapai ${km(s.done.distanceM)} km")
        finished -> tr("done", "tercapai")
        else -> null
    }
    val meta = if (rest) "" else listOfNotNull(planTarget(s).ifEmpty { null }, suffix).joinToString(" · ")
    val open = s.kind != "rest" || s.notes != null
    Row(
        Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp)
            .then(if (s.isToday) Modifier.clip(shape).background(K.base).border(1.dp, K.good.copy(alpha = 0.25f), shape) else Modifier)
            .then(if (open) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .padding(horizontal = if (s.isToday) 10.dp else 0.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlanStatusMark(s, tint = if (s.isToday) K.ink else null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${weekdayShort(s.date)} ${LocalDate.parse(s.date).dayOfMonth} · ${s.title.text}", style = body(15.sp, FontWeight.SemiBold), color = if (rest) K.muted else K.ink)
            if (meta.isNotEmpty()) Text(meta, style = T.caption, color = K.muted)
            s.workout?.let { Text("Strength 28 · Day ${it.day}", style = T.caption, color = K.muted) }
        }
        s.pct?.let { Text(pctText(it), style = num(20.sp, FontWeight.Bold), color = if (s.status == "partial") K.fair else K.ink) }
        if (open) Glyph(R.drawable.ic_chevron_right, K.muted, 20.dp)
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
