package dev.garminfit.ui

import dev.garminfit.weekday
import androidx.compose.runtime.remember
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import dev.garminfit.R

import dev.garminfit.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garminfit.Daily
import dev.garminfit.Profile
import dev.garminfit.Repo
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.Canvas
import dev.garminfit.ratioBand
import dev.garminfit.gctBand
import dev.garminfit.cadenceBand
import dev.garminfit.DynamicsWeek
import dev.garminfit.WeekMuscles
import dev.garminfit.Prediction
import androidx.compose.runtime.mutableStateOf
import dev.garminfit.Record
import dev.garminfit.raceTime
import dev.garminfit.splitLabel
import dev.garminfit.paceSec
import dev.garminfit.dateLabel
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import dev.garminfit.VO2_CATEGORIES
import dev.garminfit.groupLabel
import dev.garminfit.isId
import dev.garminfit.dayMonth
import dev.garminfit.int
import dev.garminfit.loadAdvice
import dev.garminfit.loadStatus
import dev.garminfit.one
import dev.garminfit.trainingStatus
import dev.garminfit.trainingTone
import dev.garminfit.two
import dev.garminfit.vo2Category
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private fun from(days: Int) = LocalDate.now().minusDays(days - 1L).toString()

private fun List<Daily>.hasAny(pick: (Daily) -> Double?) = any { pick(it) != null }

@Composable
fun TrendsScreen() {
    val ctx = LocalContext.current
    var range by rememberSaveable { mutableIntStateOf(30) }
    var picked by rememberSaveable { mutableIntStateOf(0) }
    val loader = rememberCachedLoad("daily", range) { Repo.api(ctx).daily(from(range)) }
    // weekly technique needs a few weeks to say anything, so 7/30/90 days map to 4/8/13 weeks
    val dynamics = rememberCachedLoad("dynamics", range) { Repo.api(ctx).runningTrends(mapOf(7 to 4, 30 to 8, 90 to 13)[range] ?: 8) }
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle(tr("Trends", "Tren"))
        Segmented(listOf(7, 30, 90), range, { tr("$it days", "$it hari") }) { range = it }
        LoadView(
            loader,
            loadingText = tr("Loading $range day trends", "Memuat tren $range hari"),
            isEmpty = { it.isEmpty() },
            emptyText = tr("No data in this range yet. Pick a longer range or wait for the next sync.", "Belum ada data di rentang ini. Pilih rentang lebih panjang atau tunggu sync berikutnya."),
        ) { days ->
            val start = dayMonth(days.first().date)
            val hrvBase = days.lastOrNull { it.hrvLow != null && it.hrvHigh != null }
            val rhr = days.map { it.rhr }
            val rhrMean = rhr.filterNotNull().takeIf { it.isNotEmpty() }?.average()
            val metrics = listOf(
                TrendMetric(
                    tr("Overnight HRV", "HRV semalam"), "ms", K.good, true,
                    tr("The green band is your normal range. Staying inside it means steady recovery.", "Pita hijau = rentang normal kamu. Tetap di dalam pita artinya pemulihan stabil."),
                    days.map { it.hrv }, hrvBase?.let { it.hrvLow!!..it.hrvHigh!! }, bandIsNormal = true,
                ),
                TrendMetric(
                    "Resting HR", "bpm", K.ink, false,
                    tr("Lower usually means fitter. A slow decline means a more efficient heart. The band is ±4 around the range average.", "Makin rendah biasanya makin bugar. Turun pelan-pelan = jantung makin efisien. Pita = ±4 dari rata-rata rentang."),
                    rhr, rhrMean?.let { (it - 4)..(it + 4) },
                ),
                TrendMetric(
                    tr("Sleep score", "Skor tidur"), "/100", K.blue, true,
                    tr("A score above 70 lifts your readiness the next day. The band marks 70 to 100.", "Skor di atas 70 bikin readiness naik keesokan harinya. Pita menandai 70 sampai 100."),
                    days.map { it.sleepScore }, 70.0..100.0,
                ),
                TrendMetric(
                    tr("Peak Body Battery", "Body Battery tertinggi"), "", K.fair, true,
                    tr("Daily energy peak after sleep. Below 60 means sleep didn't fully recharge you.", "Puncak energi harian setelah tidur. Di bawah 60 berarti tidur belum cukup mengisi ulang."),
                    days.map { it.bbHigh },
                ),
            ).filter { m -> m.values.any { it != null } }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (metrics.isNotEmpty()) {
                    val current = metrics[picked.coerceIn(0, metrics.lastIndex)]
                    TrendMain(current, days, start, range)
                    Column(Modifier.selectableGroup()) {
                        metrics.forEachIndexed { i, m -> TrendRow(m, m === current) { picked = i } }
                    }
                }
                CardLoad(dynamics, isEmpty = { w -> w.none { it.cadence != null } }) { DynamicsTrendCard(it) }
            }
        }
    }
}

@Composable
private fun DynamicsTrendCard(weeks: List<DynamicsWeek>) {
    val rows = listOfNotNull(
        Triple(tr("Cadence", "Kadens"), "spm", weeks.map { it.cadence }).takeIf { r -> r.third.any { it != null } }?.let { it to { v: Double -> cadenceBand(v) } },
        Triple(tr("Ground contact", "Kontak tanah"), "ms", weeks.map { it.gctMs }).takeIf { r -> r.third.any { it != null } }?.let { it to { v: Double -> gctBand(v) } },
        Triple(tr("Vertical ratio", "Rasio vertikal"), "%", weeks.map { it.vrPct }).takeIf { r -> r.third.any { it != null } }?.let { it to { v: Double -> ratioBand(v) } },
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(tr("Weekly running form", "Teknik lari per minggu"))
        rows.forEach { (r, band) ->
            val (name, unit, values) = r
            val latest = values.lastOrNull { it != null } ?: return@forEach
            var sel by remember { mutableStateOf<Int?>(null) }
            // while scrubbing, the header shows the touched week instead of the latest one
            val shown = sel?.let { values[it] } ?: latest
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, Modifier.weight(1f), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                    band(shown).let { b -> if (b.status != null) StatusChip(b.label, b.status) else NeutralChip(b.label) }
                    Value(if (unit == "%") shown.one() else shown.int(), unit, num(22.sp, FontWeight.Bold))
                }
                LineChart(
                    values, tr("Weekly $name chart, latest ${latest.int()} $unit", "Grafik $name per minggu, terakhir ${latest.int()} $unit"),
                    height = 56.dp, color = K.blue, width = 1.8.dp,
                    selected = sel, onSelect = { sel = it },
                    label = { i -> tr("Week of ${dayMonth(weeks[i].week)}", "Minggu ${dayMonth(weeks[i].week)}") + if (values[i] == null) tr(" · no runs", " · tanpa lari") else "" },
                )
            }
        }
        Text(tr("Last ${weeks.size} weeks, each week averaged by run duration. Labels follow Garmin's scale.", "${weeks.size} minggu terakhir, rata-rata per minggu ditimbang dari durasi tiap lari. Label mengikuti skala Garmin."), style = T.caption, color = K.muted)
    }
}

private class TrendMetric(
    val title: String,
    val unit: String,
    val color: Color,
    val higherIsBetter: Boolean,
    val help: String,
    val values: List<Double?>,
    val band: ClosedFloatingPointRange<Double>? = null,
    /** true when [band] is Garmin's recorded normal range, not a drawn reference. */
    val bandIsNormal: Boolean = false,
)

/** One sentence from the numbers on screen: latest against the recorded normal range, or against the range average. */
private fun trendInsight(m: TrendMetric, days: Int): String {
    val present = m.values.filterNotNull()
    val last = present.last()
    val u = if (m.unit.isEmpty()) "" else " ${m.unit.trim()}"
    val band = m.band?.takeIf { m.bandIsNormal }
    if (band != null) {
        val lo = band.start.roundToInt()
        val hi = band.endInclusive.roundToInt()
        return when {
            last < band.start -> tr("${last.roundToInt()}$u, below your normal $lo–$hi.", "${last.roundToInt()}$u, di bawah normal kamu $lo–$hi.")
            last > band.endInclusive -> tr("${last.roundToInt()}$u, above your normal $lo–$hi.", "${last.roundToInt()}$u, di atas normal kamu $lo–$hi.")
            else -> tr("${last.roundToInt()}$u, inside your normal $lo–$hi.", "${last.roundToInt()}$u, di dalam normal kamu $lo–$hi.")
        }
    }
    val avg = present.average().roundToInt()
    val d = last.roundToInt() - avg
    val n = present.size
    return when {
        d > 0 -> tr("${last.roundToInt()}$u, $d above the $days-day average of $avg.", "${last.roundToInt()}$u, $d di atas rata-rata $days hari ($avg).")
        d < 0 -> tr("${last.roundToInt()}$u, ${-d} below the $days-day average of $avg.", "${last.roundToInt()}$u, ${-d} di bawah rata-rata $days hari ($avg).")
        else -> tr("${last.roundToInt()}$u, right at the $days-day average (from $n days).", "${last.roundToInt()}$u, pas di rata-rata $days hari (dari $n hari).")
    }
}

@Composable
private fun TrendMain(m: TrendMetric, days: List<Daily>, start: String, range: Int) {
    val present = m.values.filterNotNull()
    var sel by remember(m.title, range) { mutableStateOf<Int?>(null) }
    var help by remember(m.title) { mutableStateOf(false) }
    val last = present.last().roundToInt()
    val diff = last - present.first().roundToInt()
    val deltaText = when {
        diff > 0 -> "+$diff"
        diff < 0 -> "−${-diff}"
        else -> "= 0"
    }
    val deltaStatus = when {
        diff == 0 -> null
        (diff > 0) == m.higherIsBetter -> Status.GOOD
        else -> Status.FAIR
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(trendInsight(m, range), style = T.headline, color = K.ink)
        Row(verticalAlignment = Alignment.Top) {
            val i = sel
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(m.title)
                Value(if (i == null) last.toString() else m.values[i]?.roundToInt()?.toString() ?: "–", m.unit, num(36.sp), unitStyle = T.bodyS)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (i == null) DeltaPill(deltaText, deltaStatus, up = if (diff == 0) null else diff > 0) else NeutralChip(weekday(days[i].date).take(3) + " " + dayMonth(days[i].date))
                Text(tr("avg ${present.average().int()}", "rata-rata ${present.average().int()}"), style = T.caption, color = K.muted)
            }
        }
        LineChart(
            m.values, tr("${m.title} chart over ${days.size} days, latest $last ${m.unit}, change $diff", "Grafik ${m.title} ${days.size} hari, terakhir $last ${m.unit}, berubah $diff"),
            color = m.color, band = m.band, selected = sel, onSelect = { sel = it },
        )
        ChartDates(start)
        Text(
            if (help) tr("Hide explanation", "Sembunyikan penjelasan") else tr("What does this show?", "Ini menunjukkan apa?"),
            Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)
                .clickable(role = Role.Button) { help = !help },
            style = body(13.sp, FontWeight.SemiBold), color = K.muted,
        )
        if (help) Text(m.help, style = T.bodyS, color = K.body3)
    }
}

@Composable
private fun TrendRow(m: TrendMetric, selected: Boolean, onClick: () -> Unit) {
    val last = m.values.filterNotNull().last()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) K.surface else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) m.color else K.track))
        Text(
            m.title, Modifier.weight(1f).padding(start = 12.dp),
            style = body(15.sp, if (selected) FontWeight.Bold else FontWeight.Medium), color = if (selected) K.ink else K.body2,
        )
        Value(last.roundToInt().toString(), m.unit, num(22.sp, FontWeight.Bold), color = if (selected) K.ink else K.body2, unitStyle = T.caption)
    }
}

@Composable
fun TrainingScreen(onOpenActivity: (Long) -> Unit, planOpen: Boolean, onPlanOpen: (Boolean) -> Unit, onOpenToday: () -> Unit) {
    val ctx = LocalContext.current
    val loader = rememberCachedLoad("training") {
        coroutineScope {
            val profile = async { runCatching { Repo.me(ctx).profile }.getOrNull() }
            Repo.api(ctx).daily(from(90)) to profile.await()
        }
    }
    val records = rememberCachedLoad("records") { Repo.api(ctx).records() }
    val predictions = rememberCachedLoad("predictions") { Repo.api(ctx).predictions(90) }
    val muscles = rememberCachedLoad("muscles") { Repo.api(ctx).weekMuscles() }
    val plan = rememberCachedLoad("plan") { Repo.api(ctx).plan() }
    var recap by rememberSaveable { mutableStateOf(false) }
    if (recap) return RecapScreen(onBack = { recap = false }, onOpenActivity = onOpenActivity)
    if (planOpen) return PlanScreen(onBack = { onPlanOpen(false) }, onOpenActivity = onOpenActivity, onOpenToday = onOpenToday)
    LoadView(loader, loadingText = tr("Loading 90 days of training", "Memuat data latihan 90 hari")) { (days, profile) ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenTitle(tr("Training", "Latihan"))
            CardLoad(plan) { PlanWeekCard(it) { onPlanOpen(true) } }
            days.lastOrNull { it.trainingStatus != null }?.let { StatusCard(it.trainingStatus) }
            RecapEntry { recap = true }
            CardLoad(predictions, isEmpty = { p -> p.none { it.half != null } }) { PredictionsCard(it) }
            CardLoad(records, isEmpty = { it.isEmpty() }) { RecordsCard(it, onOpenActivity) }
            CardLoad(muscles) { WeekMusclesCard(it) }
            if (days.hasAny { it.vo2max }) Vo2Card(days, profile)
            if (days.hasAny { it.acuteLoad }) LoadCard(days)
            if (!days.hasAny { it.vo2max } && !days.hasAny { it.acuteLoad }) {
                Text(tr("VO2max and training load show up after a few runs or rides.", "Data VO2max dan beban latihan muncul setelah beberapa sesi lari atau sepeda."), style = T.body, color = K.muted)
            }
        }
    }
}

@Composable
private fun PredictionsCard(rows: List<Prediction>) {
    val last = rows.last()
    val month = rows.lastOrNull { it.date <= LocalDate.parse(last.date).minusDays(30).toString() } ?: rows.first()
    val items = listOf(
        Triple("5K", last.k5, month.k5),
        Triple("10K", last.k10, month.k10),
        Triple("Half marathon", last.half, month.half),
        Triple("Marathon", last.marathon, month.marathon),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(tr("Race predictions", "Prediksi lomba"))
        Text(tr("Garmin's estimate if you raced today, based on VO2 max and training history.", "Perkiraan Garmin kalau kamu lomba hari ini, dari VO2 max dan riwayat latihan."), style = T.bodyS, color = K.muted)
        items.forEach { (name, now, before) ->
            now ?: return@forEach
            HorizontalDivider(Modifier.padding(top = 8.dp), color = K.track)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                before?.let { b ->
                    val d = (now - b).roundToInt()
                    // a lower predicted time is the improvement
                    if (d != 0) DeltaPill("${if (d < 0) "−" else "+"}${raceTime(kotlin.math.abs(d).toDouble())}", if (d < 0) Status.GOOD else Status.FAIR, up = d > 0)
                }
                Text(raceTime(now), Modifier.padding(start = 10.dp), style = num(22.sp, FontWeight.Bold), color = K.ink)
            }
        }
        val half = rows.map { it.half }
        if (half.count { it != null } > 2) {
            Text(tr("Half marathon, last ${rows.size} days", "Half marathon, ${rows.size} hari terakhir"), Modifier.padding(top = 14.dp), style = T.caption, color = K.muted)
            // negated so a faster prediction draws higher
            var sel by remember { mutableStateOf<Int?>(null) }
            LineChart(
                half.map { it?.let { v -> -v } }, tr("Half marathon prediction chart", "Grafik prediksi half marathon"), height = 64.dp, color = K.good, width = 1.8.dp,
                selected = sel, onSelect = { sel = it }, label = { i -> "${dayMonth(rows[i].date)} · ${half[i]?.let { raceTime(it) } ?: "–"}" },
            )
        }
        Text(tr("Change compared with 30 days ago.", "Perubahan dibanding 30 hari lalu."), Modifier.padding(top = 6.dp), style = T.caption, color = K.muted)
    }
}

@Composable
private fun WeekMusclesCard(w: WeekMuscles) {
    Panel(Modifier.fillMaxWidth(), gap = 12.dp) {
        CardTitle(tr("Muscles, last 7 days", "Otot 7 hari terakhir"), icon = R.drawable.ic_fitness_center)
        if (w.sessions == 0) {
            Text(tr("No strength sessions this week yet.", "Belum ada sesi strength minggu ini."), style = T.body, color = K.muted)
            return@Panel
        }
        Text(tr("${w.sessions} strength sessions · ${w.sets} sets", "${w.sessions} sesi strength · ${w.sets} set"), style = T.bodyS, color = K.muted)
        Canvas(Modifier.fillMaxWidth().height(240.dp).semantics { contentDescription = tr("Body map of muscles worked in the last 7 days", "Body map otot yang dilatih 7 hari terakhir") }) {
            drawIntoCanvas { drawBody(it.nativeCanvas, 0f, 0f, size.width, size.height, w.muscles, K.good, K.track) }
        }
        if (w.untrained.isNotEmpty()) {
            StatusChip(tr("Not trained: ${w.untrained.joinToString(", ") { groupLabel(it) }}", "Belum dilatih: ${w.untrained.joinToString(", ") { groupLabel(it) }}"), Status.FAIR)
        } else {
            StatusChip(tr("Every major muscle group got work this week", "Semua kelompok otot besar kena minggu ini"), Status.GOOD)
        }
    }
}

@Composable
private fun RecapEntry(onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(K.surface).clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            CardTitle(tr("Recap", "Rekap"), icon = R.drawable.ic_calendar_month)
            Text(tr("Monthly and yearly totals, run map, highlights", "Total bulanan dan tahunan, peta lari, sorotan"), style = T.bodyS, color = K.muted)
        }
        Glyph(R.drawable.ic_chevron_right, K.muted)
    }
}

@Composable
private fun RecordsCard(records: List<Record>, onOpen: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(tr("Personal records", "Rekor pribadi"))
        Spacer(Modifier.height(6.dp))
        records.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = K.track)
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(r.activityId) }.padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(splitLabel(r.distanceM), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                    Text("${dateLabel(r.date)} · ${r.name ?: tr("Run", "Lari")}", style = T.caption, color = K.muted, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(raceTime(r.durationS), style = num(22.sp, FontWeight.Bold), color = K.ink)
                    Text("${paceSec(r.durationS * 1000 / r.distanceM)} /km", style = T.caption, color = K.muted)
                }
            }
        }
        Text(tr("Counted from runs stored in the app since the first sync.", "Dihitung dari lari yang tersimpan di app sejak data pertama masuk."), Modifier.padding(start = 4.dp, top = 6.dp), style = T.caption, color = K.muted)
    }
}

@Composable
private fun StatusCard(status: String?) {
    val (label, desc) = trainingStatus(status) ?: return
    Panel(Modifier.fillMaxWidth(), padding = 16.dp, gap = 4.dp) {
        Label(tr("Training status", "Status latihan"))
        Text(label.uppercase(), style = num(24.sp, italic = true, lh = 1f), color = trainingTone(status).color())
        if (desc.isNotEmpty()) Text(desc, style = T.bodyS, color = K.body3)
    }
}

/** Days actually covered, so a young history does not claim the full 90-day window. */
private fun spanDays(days: List<Daily>): Long =
    runCatching { ChronoUnit.DAYS.between(LocalDate.parse(days.first().date), LocalDate.parse(days.last().date)) + 1 }.getOrDefault(90)

private fun age(profile: Profile?): Int? =
    profile?.birthDate?.let { runCatching { Period.between(LocalDate.parse(it), LocalDate.now()).years }.getOrNull() }

@Composable
private fun Vo2Card(days: List<Daily>, profile: Profile?) {
    val present = days.filter { it.vo2max != null }
    val v = present.last().vo2max!!
    val delta = v - present.first().vo2max!!
    Panel(Modifier.fillMaxWidth(), gap = 12.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("VO2max")
                Value(v.one(), tr("ml/kg/min", "ml/kg/mnt"), num(56.sp, lh = 0.9f), unitStyle = T.bodyS, gap = 6.dp, roll = true)
            }
            DeltaPill((if (delta >= 0) "+" else "") + delta.one() + tr(" / ${spanDays(present)} days", " / ${spanDays(present)} hari"), if (delta >= 0) Status.GOOD else Status.FAIR, up = delta >= 0)
        }
        val a = age(profile)
        val g = profile?.gender
        val cat = if (a != null && g != null) vo2Category(v, g == "MALE", a) else null
        if (cat != null) {
            Row(
                Modifier.semantics { contentDescription = tr("Category ${VO2_CATEGORIES[cat.index]}", "Kategori ${VO2_CATEGORIES[cat.index]}") },
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                VO2_CATEGORIES.forEachIndexed { i, name ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(3.dp)).background(
                                when {
                                    i == cat.index -> K.good
                                    i < cat.index -> K.good.copy(alpha = 0.3f)
                                    else -> K.track
                                },
                            ),
                        )
                        Text(
                            name, Modifier.fillMaxWidth(), style = body(10.sp, FontWeight.SemiBold, 1.2f),
                            color = if (i == cat.index) K.good else K.muted, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            val current = VO2_CATEGORIES[cat.index]
            val next = cat.toNext?.let { tr(" ${it.one()} points to go until ${VO2_CATEGORIES[cat.index + 1]}.", " Kurang ${it.one()} poin lagi ke kategori ${VO2_CATEGORIES[cat.index + 1]}.") } ?: ""
            Text(tr("\"$current\" for ${cat.sexLabel} aged ${cat.ageRange}.$next", "\"$current\" untuk ${cat.sexLabel} usia ${cat.ageRange}.$next"), style = T.bodyS, color = K.body3)
        }
    }
}

// Gauge scale 0.5–1.8; segment weights are the widths of Low / Optimal / High / Injury risk on that scale.
private val GAUGE = listOf(0.3f to K.blue, 0.5f to K.good, 0.2f to K.fair, 0.3f to K.low)

@Composable
private fun LoadCard(days: List<Daily>) {
    var sel by remember { mutableStateOf<Int?>(null) }
    // the stat row follows the scrubbed day, the gauge and advice stay on today
    val now = days.last { it.acuteLoad != null }
    val latest = sel?.let { days[it] } ?: now
    val ratio = latest.acuteLoad?.let { a -> latest.chronicLoad?.takeIf { it > 0 }?.let { a / it } }
    val nowRatio = now.acuteLoad?.let { a -> now.chronicLoad?.takeIf { it > 0 }?.let { a / it } }
    Panel(Modifier.fillMaxWidth(), gap = 12.dp) {
        CardTitle(tr("Training load", "Beban latihan"), icon = R.drawable.ic_speed)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(latest.acuteLoad.int(), tr("acute (7 days)", "akut (7 hari)"), num(30.sp), Modifier.weight(1f))
            Stat(latest.chronicLoad.int(), tr("chronic (28 days)", "kronis (28 hari)"), num(30.sp), Modifier.weight(1f))
            Stat(ratio.two(), tr("ratio", "rasio"), num(30.sp), Modifier.weight(1f), color = ratio?.let { loadStatus(it).color() } ?: K.ink)
        }
        if (nowRatio != null) {
            val ratio = nowRatio
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(20.dp).semantics { contentDescription = tr("Ratio ${ratio.two()}, optimal 0.8 to 1.3", "Rasio ${ratio.two()}, optimal 0,8 sampai 1,3") },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        GAUGE.forEach { (w, c) -> Box(Modifier.weight(w).height(12.dp).clip(RoundedCornerShape(3.dp)).background(c.copy(alpha = 0.6f))) }
                    }
                    val pos = ((ratio - 0.5) / 1.3).coerceIn(0.0, 1.0).toFloat()
                    Box(
                        Modifier.offset(x = (maxWidth - 4.dp) * pos).size(4.dp, 20.dp)
                            .clip(RoundedCornerShape(2.dp)).background(K.ink),
                    )
                }
                Row {
                    (if (isId()) listOf("Kurang", "Optimal 0,8–1,3", "Tinggi", "Rawan cedera") else listOf("Low", "Optimal 0.8–1.3", "High", "Injury risk")).forEachIndexed { i, s ->
                        if (i > 0) Spacer(Modifier.weight(1f))
                        Text(s, style = body(11.sp, lh = 1.2f), color = K.muted)
                    }
                }
            }
        }
        LineChart(
            days.map { it.acuteLoad },
            tr("Acute and chronic load chart, 90 days", "Grafik beban akut dan kronis 90 hari"),
            height = 110.dp,
            width = 2.4.dp,
            secondDashed = days.map { it.chronicLoad },
            baseline = false,
            selected = sel, onSelect = { sel = it }, label = { i -> dayMonth(days[i].date) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LineKey(tr("Acute", "Akut"), K.good)
            LineKey(tr("Chronic", "Kronis"), K.muted)
            Spacer(Modifier.weight(1f))
            Text(tr("${spanDays(days)} days", "${spanDays(days)} hari"), style = T.caption, color = K.muted)
        }
        nowRatio?.let { Text(loadAdvice(it), style = T.bodyS, color = K.body3) }
    }
}

@Composable
private fun LineKey(name: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(3.dp).background(color))
        Text(name, style = T.caption, color = K.muted)
    }
}
