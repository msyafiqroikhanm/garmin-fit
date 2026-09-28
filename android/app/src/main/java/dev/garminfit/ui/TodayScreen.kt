package dev.garminfit.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.garminfit.Activity
import dev.garminfit.Daily
import dev.garminfit.Repo
import dev.garminfit.Summary
import dev.garminfit.Today
import dev.garminfit.activityType
import dev.garminfit.dateLabel
import dev.garminfit.dayLabel
import dev.garminfit.duration
import dev.garminfit.friendly
import dev.garminfit.hrvStatus
import dev.garminfit.int
import dev.garminfit.km
import dev.garminfit.pace
import dev.garminfit.readinessStatus
import dev.garminfit.sleep
import dev.garminfit.widget.Widgets
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onOpenActivity: (Long) -> Unit, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoad { Repo.fetchToday(ctx).also { Widgets.updateAll(ctx) } }
    var refreshing by remember { mutableStateOf(false) }
    var syncError by remember { mutableStateOf<String?>(null) }

    LoadView(loader, loadingText = "Memuat data hari ini") { today ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    syncError = null
                    if (today.role == "owner") {
                        runCatching { Repo.api(ctx).sync() }.onFailure { syncError = it.friendly() }
                    }
                    loader.reload()
                    refreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                syncError?.let { Text(it, color = Status.LOW.color(), style = MaterialTheme.typography.bodyMedium) }
                val latest = today.latest
                if (latest == null) {
                    Text(
                        if (today.role == "owner") "Belum ada data. Tarik layar ke bawah untuk sync dari Garmin."
                        else "Belum ada data. Data muncul setelah pemilik melakukan sync.",
                        Modifier.padding(vertical = 48.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Header(latest, today.role)
                    HrvCard(latest, today)
                    today.flags.forEach { f ->
                        StatusText(f.text, if (f.level == "good") Status.GOOD else Status.FAIR)
                    }
                    Tiles(latest, today)
                    WeekPanel(today)
                    today.summary?.let { SummaryPanel(it) }
                    today.lastActivity?.let { LastActivity(it, onOpenActivity) }
                }
                TextButton(onClick = onLogout, Modifier.align(Alignment.CenterHorizontally)) { Text("Ganti token") }
            }
        }
    }
}

@Composable
private fun Header(d: Daily, role: String) {
    Column {
        Text(dayLabel(d.date), style = MaterialTheme.typography.titleMedium)
        if (d.date != LocalDate.now().toString()) {
            Text(
                if (role == "owner") "Data hari ini belum ada. Tarik ke bawah untuk sync." else "Data terakhir dari tanggal ini.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HrvCard(d: Daily, t: Today) {
    Panel(Modifier.fillMaxWidth()) {
        Label("HRV semalam")
        Spacer(Modifier.height(4.dp))
        BigValue(d.hrv.int(), "ms", MaterialTheme.typography.displayMedium)
        hrvStatus(d.hrvStatus)?.let { StatusText(it.first, it.second) }
        val low = d.hrvLow
        val high = d.hrvHigh
        if (d.hrv != null && low != null && high != null) {
            Spacer(Modifier.height(12.dp))
            BaselineBar(d.hrv, low, high)
            Spacer(Modifier.height(4.dp))
            Text(
                "Rentang normal kamu ${low.int()} sampai ${high.int()} ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val weekly = d.hrvWeekly ?: t.baseline.hrv?.recentAvg
        weekly?.let {
            Text("Rata-rata 7 hari ${it.int()} ms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Tiles(d: Daily, t: Today) {
    val tiles = buildList<@Composable (Modifier) -> Unit> {
        if (d.rhr != null) add { m ->
            MetricTile("Resting HR", d.rhr.int(), "bpm", m, caption = t.baseline.rhr?.let { "Rata-rata 30 hari ${it.mean.int()}" })
        }
        if (d.sleepS != null || d.sleepScore != null) add { m ->
            MetricTile("Tidur", d.sleepScore.int(), "skor", m, caption = sleep(d.sleepS))
        }
        if (d.bbHigh != null) add { m ->
            MetricTile("Body Battery", d.bbHigh.int(), "tertinggi", m, caption = d.bbLow?.let { "Terendah ${it.int()}" })
        }
        if (d.readiness != null) add { m ->
            MetricTile("Training Readiness", d.readiness.int(), "", m, status = readinessStatus(d.readinessLevel))
        }
        if (d.stressAvg != null) add { m ->
            MetricTile("Stres rata-rata", d.stressAvg.int(), "/100", m)
        }
        if (d.steps != null) add { m -> MetricTile("Langkah", d.steps.int(), "", m) }
    }
    tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { it(Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeekPanel(t: Today) {
    val w = t.week
    Panel(Modifier.fillMaxWidth()) {
        Label("7 hari terakhir")
        Spacer(Modifier.height(8.dp))
        if (w.count == 0) {
            Text("Belum ada aktivitas minggu ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column { BigValue(w.count.toString(), "sesi") }
                Column { BigValue(km(w.distanceM), "km") }
                Column { BigValue(duration(w.durationS), "") }
            }
            Spacer(Modifier.height(12.dp))
            ActiveDays(t)
        }
    }
}

@Composable
private fun ActiveDays(t: Today) {
    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val active = t.week.dates.toSet()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        days.forEach { day ->
            val isActive = day.toString() in active
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(10.dp).background(
                        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        CircleShape,
                    ),
                )
                Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale("id")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(8.dp))
        Text("${t.week.activeDays} hari aktif", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryPanel(s: Summary) {
    var open by remember { mutableStateOf(false) }
    Panel(
        Modifier.fillMaxWidth().animateContentSize()
            .clickable(role = Role.Button, onClickLabel = if (open) "Tutup ringkasan" else "Buka ringkasan") { open = !open },
    ) {
        Label("Ringkasan minggu ${dateLabel(s.range.from)} sampai ${dateLabel(s.range.to)}")
        Spacer(Modifier.height(6.dp))
        Text(s.headline, style = MaterialTheme.typography.titleMedium)
        if (open) {
            s.sections.forEach { sec ->
                Spacer(Modifier.height(12.dp))
                Text(sec.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(sec.body, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text("Ketuk untuk baca lengkap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LastActivity(a: Activity, onOpen: (Long) -> Unit) {
    Panel(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Buka detail aktivitas") { onOpen(a.id) }) {
        Label("Aktivitas terakhir, ${dayLabel(a.date)}")
        Spacer(Modifier.height(6.dp))
        Text(a.name ?: activityType(a.type), style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(
                a.distanceM?.takeIf { it > 0 }?.let { "${km(it)} km" },
                duration(a.durationS),
                a.avgSpeed?.takeIf { (a.distanceM ?: 0.0) > 0 }?.let { "${pace(it)} /km" },
                a.avgHr?.let { "HR ${it.int()}" },
            ).joinToString("  ·  "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
