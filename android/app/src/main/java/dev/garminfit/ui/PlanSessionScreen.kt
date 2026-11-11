package dev.garminfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import dev.garminfit.PlanSession
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
fun PlanSessionScreen(s: PlanSession, weekNo: Int, onBack: () -> Unit, onOpenActivity: (Long) -> Unit) {
    BackHandler(onBack = onBack)
    val sections = parseNotes(s.notes?.text.orEmpty())
    val result = sections.firstOrNull { it.heading?.lowercase() in RESULT }
    val body = sections.filter { it !== result }
    val bullets = result?.items?.filterIsInstance<Item.Bullet>().orEmpty()
    val next = bullets.firstOrNull { NEXT_TIME.containsMatchIn(it.text) }?.text?.replace(NEXT_TIME, "")
    val facts = bullets.filter { !NEXT_TIME.containsMatchIn(it.text) }

    Column(Modifier.fillMaxSize()) {
        BackLink(tr("Training plan", "Training plan"), onBack)
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

            if (s.kind != "rest") TargetCard(s, onOpenActivity)

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

@Composable
private fun TargetCard(s: PlanSession, onOpenActivity: (Long) -> Unit) {
    val dist = s.target?.distanceM?.takeIf { it > 0 }
    val main = when {
        dist != null -> "${km(dist).trimEnd('0').trimEnd('.', ',')} km"
        s.target?.durationS != null -> duration(s.target.durationS)
        else -> "–"
    }
    val sub = listOfNotNull(s.hrZone?.let { "Z$it" }, s.pace?.let { "${paceSec(it.minSKm)}–${paceSec(it.maxSKm)} /km" }).joinToString(" · ")
    val finished = s.status == "done" || s.status == "partial"
    Panel(Modifier.fillMaxWidth(), gap = 12.dp) {
        if (finished) {
            val d = s.done
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(tr("Target", "Target"))
                    Text(main, style = num(34.sp), color = K.ink)
                    if (sub.isNotEmpty()) Text(sub, style = T.caption, color = K.muted)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(tr("Done", "Tercapai"))
                    Text(
                        when {
                            d == null -> "–"
                            dist != null -> "${km(d.distanceM)} km"
                            else -> duration(d.durationS)
                        },
                        style = num(34.sp), color = statusColor(s.status),
                    )
                }
            }
            s.pct?.let { SpanBar(0f, it.toFloat(), statusColor(s.status), height = 6.dp) }
            s.activityIds.firstOrNull()?.let { id ->
                Text(
                    tr("Open activity", "Buka aktivitas"),
                    Modifier.clip(CircleShape).background(K.track).clickable(role = Role.Button) { onOpenActivity(id) }
                        .sizeIn(minHeight = 44.dp).padding(horizontal = 18.dp).wrapContentHeight(),
                    style = body(14.sp, FontWeight.SemiBold), color = K.ink,
                )
            }
        } else {
            Label(tr("Target", "Target"))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(main, style = num(44.sp, lh = 0.95f), color = K.ink)
                if (sub.isNotEmpty()) Text(sub, Modifier.weight(1f), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
            }
            Text(
                if (s.status == "missed") tr("No ${kindName(s.kind)} activity on the watch that day.", "Tidak ada aktivitas ${kindName(s.kind)} di jam tangan hari itu.")
                else tr("What you did shows here after the watch syncs.", "Hasilnya muncul di sini setelah jam tangan sinkron."),
                style = T.caption, color = K.muted,
            )
        }
    }
}

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
