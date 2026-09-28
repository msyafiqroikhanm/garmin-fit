package dev.garminfit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.garminfit.Daily
import dev.garminfit.Repo
import dev.garminfit.int
import dev.garminfit.one
import dev.garminfit.shortLabel
import dev.garminfit.trainingStatus
import java.time.LocalDate

@Composable
private fun rememberDaily(days: Int) = LocalContext.current.let { ctx ->
    rememberLoad(days) { Repo.api(ctx).daily(LocalDate.now().minusDays(days - 1L).toString()) }
}

private fun List<Daily>.hasAny(pick: (Daily) -> Double?) = any { pick(it) != null }

@Composable
fun TrendsScreen() {
    var range by rememberSaveable { mutableIntStateOf(30) }
    val options = listOf(7, 30, 90)
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
            options.forEachIndexed { i, d ->
                SegmentedButton(
                    selected = range == d,
                    onClick = { range = d },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text("$d hari") }
            }
        }
        val loader = rememberDaily(range)
        LoadView(
            loader,
            loadingText = "Memuat tren $range hari",
            isEmpty = { it.isEmpty() },
            emptyText = "Belum ada data di rentang ini. Pilih rentang lebih panjang atau tunggu sync berikutnya.",
        ) { days ->
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val last = days.lastOrNull { it.hrvLow != null }
                ChartPanel(days, "HRV semalam", "ms", { it.hrv },
                    band = last?.let { l -> l.hrvLow!!..(l.hrvHigh ?: l.hrvLow) }, note = "Area hijau: rentang normal kamu")
                ChartPanel(days, "Resting HR", "bpm", { it.rhr })
                ChartPanel(days, "Skor tidur", "", { it.sleepScore })
                ChartPanel(days, "Durasi tidur", "jam", { d -> d.sleepS?.let { it / 3600 } }, decimals = true)
                ChartPanel(days, "Stres rata-rata", "", { it.stressAvg })
                ChartPanel(days, "Body Battery tertinggi", "", { it.bbHigh })
            }
        }
    }
}

@Composable
fun TrainingScreen() {
    val loader = rememberDaily(90)
    LoadView(
        loader,
        loadingText = "Memuat data latihan 90 hari",
        isEmpty = { days -> !days.hasAny { it.vo2max } && !days.hasAny { it.acuteLoad } && days.none { it.trainingStatus != null } },
        emptyText = "Belum ada data latihan. Data VO2max dan beban latihan muncul setelah beberapa sesi lari atau sepeda.",
    ) { days ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            days.lastOrNull { it.trainingStatus != null }?.let { d ->
                trainingStatus(d.trainingStatus)?.let { (label, desc) ->
                    Panel(Modifier.fillMaxWidth()) {
                        Label("Training status")
                        Spacer(Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.headlineMedium)
                        if (desc.isNotEmpty()) Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            ChartPanel(days, "VO2max", "", { it.vo2max }, decimals = true)
            if (days.hasAny { it.acuteLoad }) {
                val latest = days.last { it.acuteLoad != null }
                Panel(Modifier.fillMaxWidth()) {
                    Label("Beban latihan akut vs kronis")
                    Spacer(Modifier.height(4.dp))
                    Row {
                        BigValue(latest.acuteLoad.int(), "akut")
                        Spacer(Modifier.width(24.dp))
                        BigValue(latest.chronicLoad.int(), "kronis")
                    }
                    val ratio = latest.acuteLoad?.let { a -> latest.chronicLoad?.takeIf { it > 0 }?.let { a / it } }
                    ratio?.let {
                        val s = when {
                            it > 1.5 -> "Rasio ${it.one()}, terlalu tinggi" to Status.LOW
                            it > 1.3 -> "Rasio ${it.one()}, naik tajam" to Status.FAIR
                            it < 0.8 -> "Rasio ${it.one()}, di bawah biasanya" to Status.FAIR
                            else -> "Rasio ${it.one()}, seimbang" to Status.GOOD
                        }
                        StatusText(s.first, s.second)
                    }
                    Spacer(Modifier.height(12.dp))
                    LineChart(
                        days.map { it.acuteLoad },
                        second = days.map { it.chronicLoad },
                        description = "Grafik beban akut dan kronis 90 hari",
                    )
                    Legend(listOf("Akut" to MaterialTheme.colorScheme.primary, "Kronis" to MaterialTheme.colorScheme.onSurfaceVariant))
                }
            }
            ChartPanel(days, "Training Readiness", "", { it.readiness })
        }
    }
}

@Composable
private fun ChartPanel(
    days: List<Daily>,
    title: String,
    unit: String,
    pick: (Daily) -> Double?,
    band: ClosedFloatingPointRange<Double>? = null,
    note: String? = null,
    decimals: Boolean = false,
) {
    val values = days.map(pick)
    val present = values.filterNotNull()
    if (present.isEmpty()) return
    val fmt: (Double?) -> String = { if (decimals) it.one() else it.int() }
    Panel(Modifier.fillMaxWidth()) {
        Label(title)
        Spacer(Modifier.height(4.dp))
        Row {
            BigValue(fmt(present.last()), unit)
            Spacer(Modifier.width(16.dp))
            Text(
                "rata-rata ${fmt(present.average())}",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        LineChart(values, band = band, description = "Grafik $title ${days.size} hari, terakhir ${fmt(present.last())}")
        Row {
            Text(shortLabel(days.first().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(shortLabel(days.last().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun Legend(items: List<Pair<String, Color>>) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { (label, color) ->
            Row {
                Text("● ", color = color, style = MaterialTheme.typography.labelSmall)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
