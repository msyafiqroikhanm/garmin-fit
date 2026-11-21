package dev.garminfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import dev.garminfit.share
import dev.garminfit.PlanSession
import dev.garminfit.PlanView
import dev.garminfit.PlanExtra
import dev.garminfit.targetShare
import dev.garminfit.kindOf
import dev.garminfit.activityType
import dev.garminfit.targetText
import dev.garminfit.R
import dev.garminfit.dayMonth
import dev.garminfit.duration
import dev.garminfit.km
import dev.garminfit.paceSec
import dev.garminfit.tr
import dev.garminfit.weekday
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private sealed interface Item {
    data class Para(val text: String) : Item
    data class Step(val n: String, val text: String) : Item
    data class Bullet(val text: String) : Item
}

private class Section(val heading: String?, val items: List<Item>)

/** The plan's note markup (see SessionNotes): aim lines, then "## " sections of steps, bullets and paragraphs. */
private fun parseNotes(text: String): List<Section> {
    val out = mutableListOf<Section>()
    var heading: String? = null
    var items = mutableListOf<Item>()
    fun flush() { if (heading != null || items.isNotEmpty()) out += Section(heading, items); items = mutableListOf() }
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { l ->
        val step = NUMBERED.find(l)
        when {
            l.startsWith("## ") -> { flush(); heading = l.removePrefix("## ") }
            step != null -> items += Item.Step(step.value.trim().removeSuffix("."), l.substring(step.value.length))
            l.startsWith("- ") -> items += Item.Bullet(l.substring(2))
            else -> items += Item.Para(l)
        }
    }
    flush()
    return out
}

private val RESULT = setOf("result", "hasil")
private val NEXT_TIME = Regex("""^(next time|lain kali):\s*""", RegexOption.IGNORE_CASE)

private fun daysFromToday(date: String) = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(date)).toInt()

private val TARGETS = Regex(
    """\d{1,2}:\d{2}(?:\s?[–-]\s?\d{1,2}:\d{2})?\s?/km|\b\d+(?:[.,]\d+)?\s?(?:km|m)\b|(?<=\bHR\b[^.\d]{0,24})\d{2,3}\b""",
    RegexOption.IGNORE_CASE,
)

/** Bold ink on the numbers a session is judged by: pace, distance and HR. */
private fun targets(text: String) = buildAnnotatedString {
    append(text)
    TARGETS.findAll(text).forEach { addStyle(SpanStyle(color = K.ink, fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1) }
}

@OptIn(ExperimentalLayoutApi::class)
/** One planned session: target vs done, the result written after the week, and the full notes. */
@Composable
fun PlanSessionScreen(
    s: PlanSession, weekNo: Int, v: PlanView, actions: PlanActions?,
    onBack: () -> Unit, onOpenActivity: (Long) -> Unit, onOpenLog: (String) -> Unit,
    onStartWorkout: ((String) -> Unit)? = null, onSynced: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    val sections = parseNotes(s.notes?.text.orEmpty())
    val result = sections.firstOrNull { it.heading?.lowercase() in RESULT }
    val body = sections.filter { it !== result }
    val bullets = result?.items?.filterIsInstance<Item.Bullet>().orEmpty()
    val next = bullets.firstOrNull { NEXT_TIME.containsMatchIn(it.text) }?.text?.replace(NEXT_TIME, "")
    val facts = bullets.filter { !NEXT_TIME.containsMatchIn(it.text) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { BackLink(tr("Training plan", "Training plan"), onBack) }
            if (onSynced != null && s.status in setOf("today", "missed", "partial")) SyncButton(onSynced)
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s.title.text, style = T.screenTitle, color = K.ink)
                    if (s.status != "rest" && s.status != "upcoming") StatusPill(statusLabel(s), statusColor(s.status))
                }
                val left = daysFromToday(s.date)
                val when_ = when {
                    s.status in setOf("done", "partial", "missed", "rest") || left <= 0 -> null
                    left == 1 -> tr("tomorrow", "besok")
                    else -> tr("in $left days", "$left hari lagi")
                }
                Label(listOfNotNull("${weekday(s.date)} ${dayMonth(s.date)}", tr("Week $weekNo", "Minggu $weekNo"), when_).joinToString(" · "))
            }

            if (s.kind != "rest") TargetCard(s, owner = actions != null, onOpenActivity, onOpenLog)
            Box(Modifier.padding(horizontal = 4.dp)) { StartWorkoutButton(s, onStartWorkout) }
            if (s.kind != "rest" && actions != null) ChangeCard(s, v, actions)

            if (facts.isNotEmpty() || next != null) {
                Panel(Modifier.fillMaxWidth(), gap = 14.dp) {
                    CardTitle(tr("Result", "Hasil"), icon = R.drawable.ic_flag)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { facts.forEach { BulletRow(it.text) } }
                    next?.let {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(K.base)
                                .border(1.dp, K.good.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Label(tr("Next time", "Lain kali"), K.good)
                            Text(targets(it), style = body(15.sp, lh = 1.5f), color = K.ink)
                        }
                    }
                }
            }

            if (body.isNotEmpty()) {
                Panel(Modifier.fillMaxWidth(), gap = 0.dp) {
                    body.forEachIndexed { i, sec ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 18.dp), color = K.line)
                        SectionView(sec, aim = i == 0 && sec.heading == null, key = s.date + s.kind)
                    }
                }
            }
        }
    }
}

/** Average pace of what was done, seconds per km; null without distance. */
internal fun donePace(s: PlanSession): Double? {
    val d = s.done ?: return null
    val m = d.distanceM?.takeIf { it > 0 } ?: return null
    return (d.durationS ?: return null) * 1000 / m
}

@Composable
private fun TargetCard(s: PlanSession, owner: Boolean, onOpenActivity: (Long) -> Unit, onOpenLog: (String) -> Unit) {
    val tgt = s.adjust?.target ?: s.target
    val dist = tgt?.distanceM?.takeIf { it > 0 }
    val main = when {
        dist != null -> "${km(dist).trimEnd('0').trimEnd('.', ',')} km"
        tgt?.durationS != null -> duration(tgt.durationS)
        else -> "–"
    }
    val sub = listOfNotNull(s.hrZone?.let { "Z$it" }, s.pace?.let { "${paceSec(it.minSKm)}–${paceSec(it.maxSKm)} /km" }).joinToString(" · ")
    val finished = s.status == "done" || s.status == "partial"
    Panel(Modifier.fillMaxWidth(), gap = 12.dp) {
        adjustedText(s)?.let { Text(it, style = body(14.sp, FontWeight.SemiBold), color = K.fair) }
        if (finished) {
            val d = s.done
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(tr("Target", "Target"))
                    Text(main, style = num(34.sp), color = K.ink)
                    if (sub.isNotEmpty()) Text(sub, style = T.caption, color = K.muted)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(if (s.status == "partial") tr("You did", "Yang dikerjakan") else tr("Done", "Tercapai"))
                    Text(
                        when {
                            d == null -> "–"
                            d.sets != null -> tr("${d.sets} of ${d.of}", "${d.sets} dari ${d.of}")
                            dist != null -> "${km(d.distanceM)} km"
                            else -> duration(d.durationS)
                        },
                        style = num(34.sp), color = statusColor(s.status),
                    )
                    if (d?.sets != null) Text(tr("sets from your workout log", "set dari catatan workout"), style = T.caption, color = K.muted)
                }
            }
            s.share?.let {
                SpanBar(0f, it.toFloat(), statusColor(s.status), height = 6.dp)
                Text(targetShare(s, it), style = T.caption, color = K.muted)
            }
            s.doneOn?.let { on ->
                Text(tr("Done ${weekday(on)} ${dayMonth(on)} (planned ${weekday(s.date)})", "Dikerjakan ${weekday(on)} ${dayMonth(on)} (rencana ${weekday(s.date)})"), style = T.bodyS, color = K.body2)
            }
            if (s.kind == "run" && s.type in QUALITY) RepPaces(s) else if (s.kind == "run") PaceBand(s)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.activityIds.firstOrNull()?.let { id -> PillAction(tr("Open activity", "Buka aktivitas")) { onOpenActivity(id) } }
                s.logId?.let { id -> PillAction(tr("Open log", "Buka catatan")) { onOpenLog(id) } }
            }
        } else {
            Label(tr("Target", "Target"))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(main, style = num(44.sp, lh = 0.95f), color = K.ink)
                if (sub.isNotEmpty()) Text(sub, Modifier.weight(1f), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
            }
            Text(
                when {
                    s.status == "skipped" -> listOfNotNull(tr("Skipped", "Dilewati"), s.skipped?.let { skipReason(it) }).joinToString(" · ") +
                        tr(". It doesn't count against your scores.", ". Tidak mengurangi skormu.")
                    s.status == "missed" -> tr("No ${kindName(s.kind)} activity on the watch that day or the day either side.", "Tidak ada aktivitas ${kindName(s.kind)} di jam tangan hari itu atau sehari sebelum dan sesudahnya.")
                    owner && s.status == "today" -> tr("Garmin syncs at 10:00. Tap Sync to pull it now.", "Garmin sinkron jam 10:00. Ketuk Sync untuk menarik sekarang.")
                    else -> tr("What was done shows here after the watch syncs.", "Hasilnya muncul di sini setelah jam tangan sinkron.")
                },
                style = T.caption, color = K.muted,
            )
        }
    }
}

/** Ink pill for a secondary action inside a card. */
@Composable
internal fun PillAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        Modifier.clip(CircleShape).background(K.track).clickable(role = Role.Button, onClick = onClick)
            .sizeIn(minHeight = 44.dp).padding(horizontal = 18.dp).wrapContentHeight(),
        style = body(14.sp, FontWeight.SemiBold), color = K.ink,
    )
}

/**
 * The plan's pace range as the baseline band, the run's average pace as the dot, faster to the right. The % above
 * only counts distance or time, so this is where a quality session's pace is judged.
 */
@Composable
private fun PaceBand(s: PlanSession) {
    val band = s.pace ?: return
    val p = donePace(s) ?: return
    val inside = p in band.minSKm..band.maxSKm
    val status = if (inside) Status.GOOD else Status.FAIR
    // negated so a faster pace sits further right, like every other baseline band in the app
    val lo = -(band.maxSKm + 60)
    val hi = -(band.minSKm - 60)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(tr("Average pace", "Pace rata-rata"), Modifier.weight(1f), style = body(14.sp), color = K.body2)
            Value(paceSec(p), "/km", num(22.sp, FontWeight.Bold))
        }
        FactorBar(-p, lo, hi, -band.maxSKm, -band.minSKm, status, tr("Average pace ${paceSec(p)} per km, plan ${paceSec(band.minSKm)} to ${paceSec(band.maxSKm)}", "Pace rata-rata ${paceSec(p)} per km, plan ${paceSec(band.minSKm)} sampai ${paceSec(band.maxSKm)}"))
        Row {
            Text(
                when {
                    inside -> tr("Inside the plan's pace", "Di dalam pace plan")
                    p > band.maxSKm -> tr("Slower than planned", "Lebih lambat dari plan")
                    else -> tr("Faster than planned", "Lebih cepat dari plan")
                },
                Modifier.weight(1f), style = body(12.sp, FontWeight.SemiBold, 1.3f), color = status.color(),
            )
            Text(tr("plan ${paceSec(band.minSKm)}–${paceSec(band.maxSKm)}", "plan ${paceSec(band.minSKm)}–${paceSec(band.maxSKm)}"), style = T.caption, color = K.muted)
        }
    }
}

private val QUALITY = setOf("tempo", "interval", "race")

/**
 * Work laps of the run (laps run within 30 s/km of the plan's slow end), each against the plan's range. A
 * quality session's average pace includes its warm-up and recovery jogs, so the reps are what is judged.
 */
internal fun workLaps(splits: List<dev.garminfit.Split>, band: dev.garminfit.PlanPace): List<Double> =
    splits.mapNotNull { l -> l.distanceM?.takeIf { it >= 200 }?.let { d -> l.durationS?.let { it * 1000 / d } } }
        .filter { it <= band.maxSKm + 30 }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepPaces(s: PlanSession) {
    val band = s.pace ?: return
    val id = s.activityIds.firstOrNull() ?: return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val detail = rememberCachedLoad("activity", id) { dev.garminfit.Repo.api(ctx).activity(id) }
    val splits = (detail.state as? Load.Ok)?.data?.splits ?: return
    val reps = workLaps(splits, band)
    if (reps.isEmpty()) return PaceBand(s)
    val inside = reps.count { it in band.minSKm - 5..band.maxSKm + 5 }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(tr("Work laps", "Lap kerja"), Modifier.weight(1f), style = body(14.sp), color = K.body2)
            Text(tr("$inside of ${reps.size} inside the plan", "$inside dari ${reps.size} sesuai plan"), style = body(13.sp, FontWeight.SemiBold), color = if (inside == reps.size) K.good else K.fair)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            reps.forEach { p -> Text(paceSec(p), style = num(20.sp, FontWeight.Bold), color = if (p in band.minSKm - 5..band.maxSKm + 5) K.ink else K.fair) }
        }
        Text(tr("plan ${paceSec(band.minSKm)}–${paceSec(band.maxSKm)} /km", "plan ${paceSec(band.minSKm)}–${paceSec(band.maxSKm)} /km"), style = T.caption, color = K.muted)
    }
}

/** Unplanned activities of the session's kind within 3 days, nearest first: candidates for "Count this run". */
internal fun linkCandidates(s: PlanSession, extras: List<PlanExtra>): List<PlanExtra> {
    val d = LocalDate.parse(s.date)
    return extras.filter { kindOf(it.type) == s.kind && kotlin.math.abs(ChronoUnit.DAYS.between(d, LocalDate.parse(it.date))) <= 3 }
        .sortedBy { kotlin.math.abs(ChronoUnit.DAYS.between(d, LocalDate.parse(it.date))) }
}

/**
 * The owner's fixes for a session the watch did not match: count an unplanned activity for it, or mark it skipped
 * with a reason so it leaves the scores. A changed session offers Undo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangeCard(s: PlanSession, v: PlanView, a: PlanActions) {
    val canLink = s.status in setOf("missed", "partial", "today")
    val canSkip = s.status in setOf("missed", "today", "upcoming")
    val candidates = if (canLink) linkCandidates(s, v.extras) else emptyList()
    if (!s.override && candidates.isEmpty() && !canSkip) return
    var skipping by rememberSaveable(s.key) { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth(), gap = 12.dp) {
        if (candidates.isNotEmpty()) {
            Text(
                if (s.kind == "run") tr("Count this run", "Hitung lari ini") else tr("Count this activity", "Hitung aktivitas ini"),
                style = T.cardTitle, color = K.ink,
            )
            Text(tr("Done on another day or not matched by the watch? Pick it and it counts for this session.", "Dikerjakan di hari lain atau tidak dicocokkan jam? Pilih dan aktivitasnya dihitung untuk sesi ini."), style = T.bodyS, color = K.muted)
            Column {
                candidates.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = K.line)
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { a.link(s, s.activityIds + e.id) }.sizeIn(minHeight = 56.dp).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name ?: activityType(e.type), style = body(15.sp, FontWeight.SemiBold), color = K.ink, maxLines = 1)
                            Text("${weekday(e.date)} ${dayMonth(e.date)}" + (e.distanceM?.takeIf { it > 0 }?.let { " · ${km(it)} km" } ?: e.durationS?.let { " · ${duration(it)}" } ?: ""), style = T.caption, color = K.muted)
                        }
                        Text(tr("Count", "Hitung"), style = body(14.sp, FontWeight.SemiBold), color = K.ink)
                    }
                }
            }
        }
        if (canSkip && s.status != "skipped") {
            if (candidates.isNotEmpty()) HorizontalDivider(color = K.line)
            if (!skipping) PillAction(tr("Mark skipped", "Tandai dilewati")) { skipping = true }
            else {
                Text(tr("Why are you skipping it?", "Kenapa dilewati?"), style = T.cardTitle, color = K.ink)
                Text(tr("A skipped session leaves your scores instead of counting as missed.", "Sesi yang dilewati keluar dari skor, tidak dihitung terlewat."), style = T.bodyS, color = K.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SKIP_REASONS.forEach { r -> PillAction(skipReason(r)) { skipping = false; a.skip(s, r) } }
                }
            }
        }
        if (s.override) {
            if (candidates.isNotEmpty() || canSkip) HorizontalDivider(color = K.line)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (s.status == "skipped") tr("You marked this skipped.", "Kamu menandai ini dilewati.") else tr("You picked what counts for this session.", "Kamu memilih aktivitas untuk sesi ini."),
                    Modifier.weight(1f), style = T.bodyS, color = K.body2,
                )
                PillAction(tr("Undo", "Batalkan")) { a.undo(s) }
            }
        }
    }
}

private val SKIP_REASONS = listOf("sick", "travel", "rest", "other")

private fun kindName(kind: String) = when (kind) {
    "run" -> tr("running", "lari")
    "strength" -> tr("strength", "strength")
    else -> tr("training", "latihan")
}

/** A section of the notes. A heading ending in "?" ("Short on time?") folds shut. */
@Composable
private fun SectionView(sec: Section, aim: Boolean, key: String) {
    val fold = sec.heading?.endsWith("?") == true
    var open by rememberSaveable(key, sec.heading) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            aim -> Label(tr("Aim", "Tujuan"))
            fold -> Row(
                Modifier.fillMaxWidth().sizeIn(minHeight = 44.dp).clickable(role = Role.Button) { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sec.heading!!, Modifier.weight(1f), style = T.cardTitle, color = K.ink)
                Glyph(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more, K.muted)
            }
            else -> Text(sec.heading!!, style = T.cardTitle, color = K.ink)
        }
        if (fold && !open) return@Column
        val single = sec.items.count { it is Item.Step } == 1
        sec.items.map { if (single && it is Item.Step) Item.Para(it.text) else it }.forEach {
            when (it) {
                is Item.Para -> Text(targets(it.text), style = if (aim) body(17.sp, FontWeight.Medium, 1.5f) else body(15.sp, lh = 1.55f), color = if (aim) K.ink else K.body3)
                is Item.Step -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        it.n,
                        Modifier.size(26.dp).clip(CircleShape).background(K.track).wrapContentHeight(),
                        style = num(15.sp, FontWeight.Bold), color = K.ink, textAlign = TextAlign.Center,
                    )
                    Text(targets(it.text), Modifier.weight(1f), style = body(15.sp, lh = 1.55f), color = K.body3)
                }
                is Item.Bullet -> BulletRow(it.text)
            }
        }
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("•", Modifier.width(8.dp), style = body(15.sp, lh = 1.55f), color = K.muted)
        Text(targets(text), Modifier.weight(1f), style = body(15.sp, lh = 1.55f), color = K.body3)
    }
}
