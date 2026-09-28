package dev.garminfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.garminfit.Activity
import dev.garminfit.Repo
import dev.garminfit.activityType
import dev.garminfit.dayLabel
import dev.garminfit.duration
import dev.garminfit.int
import dev.garminfit.km
import dev.garminfit.one
import dev.garminfit.pace
import dev.garminfit.time
import java.time.LocalDate

@Composable
fun ActivitiesScreen(initialId: Long?) {
    val ctx = LocalContext.current
    val loader = rememberLoad { Repo.api(ctx).activities(LocalDate.now().minusDays(365).toString()) }
    var selected by rememberSaveable(initialId) { mutableStateOf(initialId) }
    LoadView(
        loader,
        loadingText = "Memuat aktivitas setahun terakhir",
        isEmpty = { it.isEmpty() },
        emptyText = "Belum ada aktivitas dalam setahun terakhir. Aktivitas dari jam Garmin muncul di sini setelah sync.",
    ) { list ->
        val current = list.firstOrNull { it.id == selected }
        if (current != null) {
            BackHandler { selected = null }
            ActivityDetail(current) { selected = null }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { a ->
                    ActivityRow(a) { selected = a.id }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun Activity.hasDistance() = (distanceM ?: 0.0) > 0

@Composable
private fun ActivityRow(a: Activity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(a.name ?: activityType(a.type), style = MaterialTheme.typography.titleMedium)
            Text(
                "${dayLabel(a.date)}, ${time(a.startTime)}" + if (a.name != null) "  ·  ${activityType(a.type)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (a.hasDistance()) "${km(a.distanceM)} km" else duration(a.durationS), style = MaterialTheme.typography.titleMedium)
            Text(
                if (a.hasDistance()) duration(a.durationS) else a.avgHr?.let { "HR ${it.int()}" } ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActivityDetail(a: Activity, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Kembali ke daftar") }
        Column {
            Text(a.name ?: activityType(a.type), style = MaterialTheme.typography.headlineMedium)
            Text(
                (if (a.name != null) "${activityType(a.type)}  ·  " else "") + "${dayLabel(a.date)}, ${time(a.startTime)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (a.hasDistance()) BigValue(km(a.distanceM), "km", MaterialTheme.typography.displayMedium)

        val stats = listOfNotNull(
            "Durasi" to duration(a.durationS),
            if (a.hasDistance() && a.avgSpeed != null) "Pace rata-rata" to "${pace(a.avgSpeed)} /km" else null,
            a.avgHr?.let { "HR rata-rata" to "${it.int()} bpm" },
            a.maxHr?.let { "HR maks" to "${it.int()} bpm" },
            a.calories?.let { "Kalori" to "${it.int()} kkal" },
            a.elevationGain?.takeIf { it > 0 }?.let { "Elevasi naik" to "${it.int()} m" },
            a.aerobicTe?.let { "Training effect aerobik" to it.one() },
            a.anaerobicTe?.let { "Training effect anaerobik" to it.one() },
            a.trainingLoad?.let { "Training load" to it.int() },
        )
        Panel(Modifier.fillMaxWidth()) {
            stats.forEachIndexed { i, (label, value) ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row {
                    Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        a.hrZonesS?.let { HrZones(it) }
    }
}

@Composable
private fun HrZones(zones: List<Double?>) {
    val secs = zones.map { it ?: 0.0 }
    val total = secs.sum()
    if (total <= 0) return
    val primary = MaterialTheme.colorScheme.primary
    val shades = listOf(0.35f, 0.5f, 0.65f, 0.8f, 1f).map { primary.copy(alpha = it) }
    Panel(Modifier.fillMaxWidth()) {
        Label("Waktu per zona detak jantung")
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(16.dp).clip(MaterialTheme.shapes.small).semantics {
                contentDescription = secs.mapIndexed { i, s -> "Zona ${i + 1} ${(s / total * 100).toInt()} persen" }.joinToString(", ")
            },
        ) {
            secs.forEachIndexed { i, s ->
                if (s > 0) Box(Modifier.weight(s.toFloat()).fillMaxHeight().background(shades[i]))
            }
        }
        Spacer(Modifier.height(12.dp))
        secs.forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(12.dp).height(12.dp).clip(MaterialTheme.shapes.small).background(shades[i]))
                Spacer(Modifier.width(8.dp))
                Text("Zona ${i + 1}", Modifier.weight(1f))
                Text(duration(s), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  ${(s / total * 100).toInt()}%", Modifier.width(52.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
