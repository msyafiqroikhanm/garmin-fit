package dev.garminfit.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
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

private fun statusLabel(s: PlanSession) = when (s.status) {
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

private fun statusColor(status: String) = when (status) {
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

/** Today: what the plan says for today, and how much of it is already done. */
@Composable
fun PlanTodayCard(p: PlanToday, onOpen: () -> Unit) {
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
                    Text(s.title.text.uppercase(), style = num(24.sp), color = K.ink)
                    planTarget(s).takeIf { it.isNotEmpty() }?.let { Text(it, style = T.bodyS, color = K.muted) }
                }
                if (s.pct != null) RollingText(pctText(s.pct), num(28.sp), statusColor(s.status))
            }
            s.pct?.let { SpanBar(0f, it.toFloat(), statusColor(s.status)) }
            s.notes?.let { Text(it.text, style = T.bodyS, color = K.body3) }
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
        if (plan == null) {
            Text(
                tr("Plan your weeks with Claude: run /training-plan in Claude Code. The plan shows up here and each session is checked against your Garmin data.",
                    "Susun plan bareng Claude: jalankan /training-plan di Claude Code. Plan muncul di sini dan tiap sesi dicek ke data Garmin."),
                style = T.bodyS, color = K.body2,
            )
            return@Column
        }
        val monday = LocalDate.now().let { it.minusDays(it.dayOfWeek.value - 1L) }
        val days = (0..6).map { monday.plusDays(it.toLong()).toString() }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                val s = v.sessions.firstOrNull { it.date == d }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(weekdayShort(d), style = body(11.sp, FontWeight.SemiBold, 1.2f), color = if (d == LocalDate.now().toString()) K.ink else K.muted)
                    if (s != null) PlanStatusMark(s) else Box(Modifier.width(24.dp).height(24.dp))
                }
            }
        }
        v.weeks.firstOrNull { it.start == monday.toString() }?.let { WeekLine(it) }
    }
}

/** The whole plan: overall score, then each week as a list of sessions you can expand. */
@Composable
fun PlanScreen(onBack: () -> Unit, onOpenActivity: (Long) -> Unit) {
    val ctx = LocalContext.current
    val loader = rememberCachedLoad("plan") { Repo.api(ctx).plan() }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
        BackLink(tr("Training", "Latihan"), onBack)
        LoadView(
            loader,
            loadingText = tr("Loading your plan", "Memuat plan"),
            isEmpty = { it.plan == null },
            emptyText = tr("No training plan yet. Run /training-plan in Claude Code to make one.", "Belum ada training plan. Jalankan /training-plan di Claude Code untuk bikin."),
        ) { v ->
            val plan = v.plan!!
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.fillMaxWidth(), padding = 20.dp, gap = 10.dp) {
                    Label(plan.name)
                    Text(plan.goal.text, style = num(26.sp, lh = 1.05f), color = K.ink)
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
                v.weeks.forEach { w -> WeekCard(w, v.sessions.filter { weekOf(it.date) == w.start }, onOpenActivity) }
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

private fun weekOf(date: String) = LocalDate.parse(date).let { it.minusDays(it.dayOfWeek.value - 1L) }.toString()

@Composable
private fun WeekCard(w: PlanWeek, sessions: List<PlanSession>, onOpenActivity: (Long) -> Unit) {
    Panel(Modifier.fillMaxWidth(), gap = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(tr("Week of ${dayMonth(w.start)}", "Minggu ${dayMonth(w.start)}"), Modifier.weight(1f))
            Text("${w.done}/${w.planned} · ${pctText(w.pct)}", style = num(18.sp, FontWeight.Bold), color = if (w.pct == null) K.muted else K.ink)
        }
        sessions.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = K.track)
            SessionRow(s, onOpenActivity)
        }
    }
}

/** Status mark · title and day · status chip · expand for target vs done. */
@Composable
private fun SessionRow(s: PlanSession, onOpenActivity: (Long) -> Unit) {
    var open by rememberSaveable(s.date) { mutableStateOf(false) }
    val expandable = s.kind != "rest"
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().sizeIn(minHeight = 56.dp)
                .then(if (expandable) Modifier.clickable(role = Role.Button) { open = !open } else Modifier)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlanStatusMark(s)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.title.text, style = body(15.sp, FontWeight.SemiBold), color = if (s.status == "rest") K.muted else K.ink)
                Text(listOf("${weekday(s.date)} ${dayMonth(s.date)}", planTarget(s)).filter { it.isNotEmpty() }.joinToString(" · "), style = T.caption, color = K.muted)
            }
            if (s.status != "rest" && s.status != "upcoming") StatusPill(statusLabel(s), statusColor(s.status))
            if (expandable) Glyph(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more, K.muted, 20.dp)
        }
        if (open) {
            Column(Modifier.padding(start = 36.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat(planTarget(s).ifEmpty { "–" }, tr("target", "target"), num(18.sp, FontWeight.Bold), Modifier.weight(1f))
                    val d = s.done
                    Stat(
                        when {
                            d == null -> "–"
                            (s.target?.distanceM ?: 0.0) > 0 -> "${km(d.distanceM)} km"
                            else -> duration(d.durationS)
                        },
                        tr("done", "tercapai"), num(18.sp, FontWeight.Bold), Modifier.weight(1f),
                    )
                }
                s.pct?.let { SpanBar(0f, it.toFloat(), statusColor(s.status)) }
                s.notes?.let { Text(it.text, style = T.bodyS, color = K.body3) }
                s.activityIds.firstOrNull()?.let { id ->
                    Text(
                        tr("Open activity", "Buka aktivitas"),
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onOpenActivity(id) }.padding(vertical = 6.dp),
                        style = body(14.sp, FontWeight.SemiBold), color = K.good,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, c: Color) {
    Text(
        text,
        Modifier.clip(CircleShape).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        style = body(12.sp, FontWeight.Bold, 1.2f), color = c, textAlign = TextAlign.Center,
    )
}
