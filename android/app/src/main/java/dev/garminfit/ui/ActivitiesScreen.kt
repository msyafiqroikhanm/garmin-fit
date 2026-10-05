package dev.garminfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garminfit.Activity
import dev.garminfit.Profile
import dev.garminfit.Repo
import dev.garminfit.Split
import dev.garminfit.activityType
import dev.garminfit.dayLabel
import dev.garminfit.dayMonth
import dev.garminfit.duration
import dev.garminfit.int
import dev.garminfit.km
import dev.garminfit.minSec
import dev.garminfit.one
import dev.garminfit.pace
import dev.garminfit.paceSec
import dev.garminfit.teLabel
import dev.garminfit.time
import dev.garminfit.weekday
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ActivitiesScreen(initialId: Long?) {
    val ctx = LocalContext.current
    val loader = rememberLoad {
        Repo.api(ctx).activities(LocalDate.now().minusDays(365).toString()) to runCatching { Repo.me(ctx).profile }.getOrNull()
    }
    var selected by rememberSaveable(initialId) { mutableStateOf(initialId) }
    LoadView(
        loader,
        loadingText = "Memuat aktivitas setahun terakhir",
        isEmpty = { it.first.isEmpty() },
        emptyText = "Belum ada aktivitas dalam setahun terakhir. Aktivitas dari jam Garmin muncul di sini setelah sync.",
    ) { (list, profile) ->
        val current = list.firstOrNull { it.id == selected }
        if (current != null) {
            BackHandler { selected = null }
            ActivityDetail(current, list, profile) { selected = null }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item { Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) { ScreenTitle("Aktivitas") } }
                items(list, key = { it.id }) { a ->
                    ActivityRow(a) { selected = a.id }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = K.track)
                }
            }
        }
    }
}

private fun Activity.hasDistance() = (distanceM ?: 0.0) > 0

@Composable
private fun ActivityRow(a: Activity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(a.name ?: activityType(a.type), style = num(20.sp, FontWeight.Bold), color = K.ink)
            Text(
                "${dayLabel(a.date)}, ${time(a.startTime)}" + if (a.name != null) " · ${activityType(a.type)}" else "",
                style = T.caption,
                color = K.muted,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (a.hasDistance()) Value(km(a.distanceM), "km", num(20.sp, FontWeight.Bold))
            else Text(duration(a.durationS), style = num(20.sp, FontWeight.Bold), color = K.ink)
            Text(
                if (a.hasDistance()) duration(a.durationS) else a.avgHr?.let { "HR ${it.int()}" } ?: "",
                style = T.caption,
                color = K.muted,
            )
        }
    }
}

@Composable
private fun ActivityDetail(a: Activity, all: List<Activity>, profile: Profile?, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            Text("‹ Semua aktivitas", style = body(14.sp, FontWeight.SemiBold), color = K.good)
        }
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("${activityType(a.type)} · ${weekday(a.date)} ${dayMonth(a.date)}, ${time(a.startTime)}")
            Text((a.name ?: activityType(a.type)).uppercase(), style = num(32.sp), color = K.ink)
        }
        val ctx = LocalContext.current
        val detail = rememberLoad(a.id) { Repo.api(ctx).activity(a.id) }
        RouteCard(detail, expected = a.hasDistance(), onRetry = detail.reload)
        StatsCard(a)
        EffectCard(a, all)
        a.hrZonesS?.let { ZonesCard(it, profile?.hrZoneFloors) }
        a.splits?.let { SplitsCard(it) }
    }
}

@Composable
private fun RouteCard(detail: Loader<Activity>, expected: Boolean, onRetry: () -> Unit) {
    val state = detail.state
    val route = (state as? Load.Ok)?.data?.route
    if (route == null && (state is Load.Ok || !expected)) return
    Panel(Modifier.fillMaxWidth()) {
        CardTitle("Rute")
        when {
            route != null -> {
                val density = LocalDensity.current
                Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                    drawIntoCanvas { drawRoute(it.nativeCanvas, 0f, 0f, size.width, size.height, route, with(density) { 4.dp.toPx() }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lambat", style = T.caption, color = K.muted)
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(K.low, K.fair, K.good))))
                    Text("Cepat", style = T.caption, color = K.muted)
                }
            }
            state is Load.Err -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Rute gagal dimuat", style = T.body, color = K.muted, modifier = Modifier.weight(1f))
                Box(Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClick = onRetry).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text("Coba lagi", style = body(14.sp, FontWeight.SemiBold), color = K.good)
                }
            }
            else -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = K.good, trackColor = K.track)
            }
        }
    }
}

@Composable
private fun StatsCard(a: Activity) {
    val stats = listOfNotNull(
        duration(a.durationS) to "durasi",
        a.avgSpeed?.takeIf { a.hasDistance() }?.let { pace(it) to "pace /km" },
        a.avgHr?.let { it.int() to "HR rata-rata" },
        a.maxHr?.let { it.int() to "HR maks" },
        a.calories?.let { it.int() to "kkal" },
        a.elevationGain?.takeIf { it > 0 }?.let { "${it.int()} m" to "elevasi naik" },
    )
    Panel(Modifier.fillMaxWidth(), gap = 16.dp) {
        if (a.hasDistance()) Value(km(a.distanceM), "km", T.hero, unitStyle = body(18.sp), gap = 6.dp)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            stats.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (v, l) -> Stat(v, l, num(24.sp, FontWeight.Bold), Modifier.weight(1f)) }
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun EffectCard(a: Activity, all: List<Activity>) {
    val rows = listOfNotNull(
        a.aerobicTe?.let { Triple("Aerobik", it, K.good) to "daya tahan" },
        a.anaerobicTe?.let { Triple("Anaerobik", it, K.blue) to "kecepatan" },
    )
    if (rows.isEmpty() && a.trainingLoad == null) return
    Panel(Modifier.fillMaxWidth(), gap = 14.dp) {
        CardTitle("Efek ke tubuh")
        rows.forEach { (r, target) ->
            val (name, v, c) = r
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(name, Modifier.weight(1f).alignByBaseline(), style = body(14.sp), color = K.body2)
                    Value(v.one(), "/ 5", num(24.sp), gap = 6.dp, modifier = Modifier.alignByBaseline())
                }
                SegmentBar(v, c)
                Text(teLabel(v, target), style = body(13.sp, FontWeight.SemiBold), color = if (v >= 2) c else K.muted)
            }
        }
        a.trainingLoad?.let { load ->
            val weekStart = runCatching { LocalDate.parse(a.date).minusDays(6).toString() }.getOrDefault(a.date)
            val heaviest = all.filter { it.date in weekStart..a.date }.maxByOrNull { it.trainingLoad ?: 0.0 }?.id == a.id
            Column {
                HorizontalDivider(color = K.track)
                Text(
                    buildAnnotatedString {
                        append("Training load ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = K.ink)) { append(load.int()) }
                        if (heaviest) append(" · sesi terberat 7 hari terakhir.")
                    },
                    Modifier.padding(top = 12.dp),
                    style = T.bodyS,
                    color = K.body2,
                )
            }
        }
    }
}

private val ZONE_NAMES = listOf("Pemanasan", "Mudah", "Aerobik", "Ambang", "Maksimal")

@Composable
private fun ZonesCard(zones: List<Double?>, floors: List<Double>?) {
    val secs = zones.map { it ?: 0.0 }
    val total = secs.sum()
    if (total <= 0) return
    val bpm = floors?.takeIf { it.size == 5 }?.map { it.roundToInt() }?.let { f ->
        listOf("< ${f[1]} bpm", "${f[1]}–${f[2]}", "${f[2]}–${f[3]}", "${f[3]}–${f[4]}", "> ${f[4]}")
    }
    Panel(Modifier.fillMaxWidth(), gap = 12.dp) {
        CardTitle("Zona detak jantung")
        StackedBar(
            secs.mapIndexed { i, s -> s to K.zones[i] },
            description = secs.mapIndexed { i, s -> "Zona ${i + 1} ${(s / total * 100).roundToInt()} persen" }.joinToString(", "),
            height = 18.dp, gap = 2.dp, segmentRadius = 0.dp, outerRadius = 6.dp,
        )
        secs.forEachIndexed { i, s ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Z${i + 1}",
                    Modifier.width(28.dp).clip(RoundedCornerShape(6.dp)).background(K.zones[i]).padding(vertical = 2.dp),
                    style = num(15.sp), color = K.onAccent, textAlign = TextAlign.Center,
                )
                Column(Modifier.weight(1f)) {
                    Text(ZONE_NAMES[i], style = body(14.sp, FontWeight.SemiBold, 1.3f), color = K.ink)
                    bpm?.let { Text(it[i], style = body(11.sp, lh = 1.3f), color = K.muted) }
                }
                Text(minSec(s), Modifier.width(64.dp), style = T.bodyS, color = K.muted, textAlign = TextAlign.End)
                Text("${(s / total * 100).roundToInt()}%", Modifier.width(40.dp), style = num(18.sp, FontWeight.Bold), color = K.ink, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun SplitsCard(splits: List<Split>) {
    // ponytail: only full auto-laps count as km splits; structured-workout laps of other lengths hide the card
    val paces = splits.filter { abs((it.distanceM ?: 0.0) - 1000) <= 10 && (it.durationS ?: 0.0) > 0 }
        .map { it.durationS!! * 1000 / it.distanceM!! }
    if (paces.size < 2 || paces.size < splits.size - 1) return
    val fast = paces.min()
    val slow = paces.max()
    val avg = paces.average()
    val half = paces.size / 2
    val negative = paces.drop(paces.size - half).average() < paces.take(half).average()
    Panel(Modifier.fillMaxWidth(), gap = 8.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            CardTitle("Split per km", Modifier.weight(1f).alignByBaseline())
            if (negative) Text("negative split ✓", Modifier.alignByBaseline(), style = T.caption, color = K.muted)
        }
        paces.forEachIndexed { i, p ->
            val w = if (slow > fast) 0.4f + ((slow - p) / (slow - fast)).toFloat() * 0.6f else 1f
            Row(
                Modifier.semantics(mergeDescendants = true) { contentDescription = "Km ${i + 1}, ${paceSec(p)} per km" },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", Modifier.width(22.dp), style = T.mono, color = K.muted)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(K.track)) {
                    Box(
                        Modifier.fillMaxWidth(w).height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(if (p < avg) K.good else K.good.copy(alpha = 0.5f)),
                    )
                }
                Text(paceSec(p), Modifier.width(40.dp), style = num(16.sp, FontWeight.Bold), color = K.ink, textAlign = TextAlign.End)
            }
        }
    }
}
