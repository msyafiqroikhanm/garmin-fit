package dev.garminfit.ui

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
import dev.garminfit.VO2_CATEGORIES
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
    val loader = rememberLoad(range) { Repo.api(ctx).daily(from(range)) }
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenTitle("Tren tubuh")
        Segmented(listOf(7, 30, 90), range, { "$it hari" }) { range = it }
        LoadView(
            loader,
            loadingText = "Memuat tren $range hari",
            isEmpty = { it.isEmpty() },
            emptyText = "Belum ada data di rentang ini. Pilih rentang lebih panjang atau tunggu sync berikutnya.",
        ) { days ->
            val start = dayMonth(days.first().date)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val hrvBase = days.lastOrNull { it.hrvLow != null && it.hrvHigh != null }
                TrendCard(
                    days, start, "HRV semalam", "ms", { it.hrv }, K.good, higherIsBetter = true,
                    band = hrvBase?.let { it.hrvLow!!..it.hrvHigh!! },
                    insight = "Pita hijau = rentang normal kamu. Tetap di dalam pita artinya pemulihan stabil.",
                )
                val rhrMean = days.mapNotNull { it.rhr }.takeIf { it.isNotEmpty() }?.average()
                TrendCard(
                    days, start, "Resting HR", "bpm", { it.rhr }, K.ink, higherIsBetter = false,
                    band = rhrMean?.let { (it - 4)..(it + 4) },
                    insight = "Makin rendah biasanya makin bugar. Turun pelan-pelan = jantung makin efisien.",
                )
                TrendCard(
                    days, start, "Skor tidur", "/100", { it.sleepScore }, K.blue, higherIsBetter = true,
                    band = 70.0..100.0,
                    insight = "Skor di atas 70 bikin readiness naik keesokan harinya.",
                )
                TrendCard(
                    days, start, "Body Battery tertinggi", "", { it.bbHigh }, K.fair, higherIsBetter = true,
                    insight = "Puncak energi harian setelah tidur. Di bawah 60 berarti tidur belum cukup mengisi ulang.",
                )
            }
        }
    }
}

@Composable
private fun TrendCard(
    days: List<Daily>,
    start: String,
    title: String,
    unit: String,
    pick: (Daily) -> Double?,
    color: Color,
    higherIsBetter: Boolean,
    insight: String,
    band: ClosedFloatingPointRange<Double>? = null,
) {
    val values = days.map(pick)
    val present = values.filterNotNull()
    if (present.isEmpty()) return
    val last = present.last().roundToInt()
    val diff = last - present.first().roundToInt()
    val deltaText = when {
        diff > 0 -> "▲ +$diff"
        diff < 0 -> "▼ $diff"
        else -> "= 0"
    }
    val deltaStatus = when {
        diff == 0 -> null
        (diff > 0) == higherIsBetter -> Status.GOOD
        else -> Status.FAIR
    }
    Panel(Modifier.fillMaxWidth(), gap = 10.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(title)
                Value(last.toString(), unit, num(36.sp), unitStyle = T.bodyS)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DeltaPill(deltaText, deltaStatus)
                Text("rata-rata ${present.average().int()}", style = T.caption, color = K.muted)
            }
        }
        LineChart(values, "Grafik $title ${days.size} hari, terakhir $last $unit, berubah $diff", color = color, band = band)
        ChartDates(start)
        Text(insight, style = T.bodyS, color = K.body3)
    }
}

@Composable
fun TrainingScreen() {
    val ctx = LocalContext.current
    val loader = rememberLoad {
        Repo.api(ctx).daily(from(90)) to runCatching { Repo.me(ctx).profile }.getOrNull()
    }
    LoadView(
        loader,
        loadingText = "Memuat data latihan 90 hari",
        isEmpty = { (days, _) -> !days.hasAny { it.vo2max } && !days.hasAny { it.acuteLoad } && days.none { it.trainingStatus != null } },
        emptyText = "Belum ada data latihan. Data VO2max dan beban latihan muncul setelah beberapa sesi lari atau sepeda.",
    ) { (days, profile) ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenTitle("Kebugaran")
            days.lastOrNull { it.trainingStatus != null }?.let { StatusCard(it.trainingStatus) }
            if (days.hasAny { it.vo2max }) Vo2Card(days, profile)
            if (days.hasAny { it.acuteLoad }) LoadCard(days)
        }
    }
}

@Composable
private fun StatusCard(status: String?) {
    val (label, desc) = trainingStatus(status) ?: return
    Panel(Modifier.fillMaxWidth(), padding = 20.dp, gap = 6.dp, color = trainingTone(status).color()) {
        Label("Training status", color = K.onAccent)
        Text(label.uppercase(), style = num(48.sp, italic = true, lh = 0.9f), color = K.onAccent)
        if (desc.isNotEmpty()) Text(desc, style = body(15.sp, FontWeight.Medium), color = K.onAccent)
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
                Value(v.one(), "ml/kg/mnt", num(56.sp, lh = 0.9f), unitStyle = T.bodyS, gap = 6.dp)
            }
            DeltaPill((if (delta >= 0) "+" else "") + delta.one() + " / ${spanDays(present)} hari", if (delta >= 0) Status.GOOD else Status.FAIR)
        }
        val a = age(profile)
        val g = profile?.gender
        val cat = if (a != null && g != null) vo2Category(v, g == "MALE", a) else null
        if (cat != null) {
            Row(
                Modifier.semantics { contentDescription = "Kategori ${VO2_CATEGORIES[cat.index]}" },
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
            val next = cat.toNext?.let { " Kurang ${it.one()} poin lagi ke kategori ${VO2_CATEGORIES[cat.index + 1]}." } ?: ""
            Text("\"$current\" untuk ${cat.sexLabel} usia ${cat.ageRange}.$next", style = T.bodyS, color = K.body3)
        }
    }
}

// Gauge scale 0.5–1.8; segment weights are the widths of Kurang / Optimal / Tinggi / Rawan on that scale.
private val GAUGE = listOf(0.3f to K.blue, 0.5f to K.good, 0.2f to K.fair, 0.3f to K.low)

@Composable
private fun LoadCard(days: List<Daily>) {
    val latest = days.last { it.acuteLoad != null }
    val ratio = latest.acuteLoad?.let { a -> latest.chronicLoad?.takeIf { it > 0 }?.let { a / it } }
    Panel(Modifier.fillMaxWidth(), gap = 12.dp) {
        CardTitle("Beban latihan")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(latest.acuteLoad.int(), "akut (7 hari)", num(30.sp), Modifier.weight(1f))
            Stat(latest.chronicLoad.int(), "kronis (28 hari)", num(30.sp), Modifier.weight(1f))
            Stat(ratio.two(), "rasio", num(30.sp), Modifier.weight(1f), color = ratio?.let { loadStatus(it).color() } ?: K.ink)
        }
        if (ratio != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(20.dp).semantics { contentDescription = "Rasio ${ratio.two()}, optimal 0,8 sampai 1,3" },
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
                    listOf("Kurang", "Optimal 0,8–1,3", "Tinggi", "Rawan cedera").forEachIndexed { i, s ->
                        if (i > 0) Spacer(Modifier.weight(1f))
                        Text(s, style = body(11.sp, lh = 1.2f), color = K.muted)
                    }
                }
            }
        }
        LineChart(
            days.map { it.acuteLoad },
            "Grafik beban akut dan kronis 90 hari",
            height = 110.dp,
            width = 2.4.dp,
            secondDashed = days.map { it.chronicLoad },
            baseline = false,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LineKey("Akut", K.good)
            LineKey("Kronis", K.muted)
            Spacer(Modifier.weight(1f))
            Text("${spanDays(days)} hari", style = T.caption, color = K.muted)
        }
        ratio?.let { Text(loadAdvice(it), style = T.bodyS, color = K.body3) }
    }
}

@Composable
private fun LineKey(name: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(3.dp).background(color))
        Text(name, style = T.caption, color = K.muted)
    }
}
