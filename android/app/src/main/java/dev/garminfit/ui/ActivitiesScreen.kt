package dev.garminfit.ui

import dev.garminfit.R
import dev.garminfit.tr
import dev.garminfit.isId
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.garminfit.Strength
import dev.garminfit.Extra
import dev.garminfit.Similar
import dev.garminfit.raceTime
import dev.garminfit.splitShort
import dev.garminfit.Series
import dev.garminfit.DynamicsBand
import dev.garminfit.cadenceBand
import dev.garminfit.gctBand
import dev.garminfit.oscillationBand
import dev.garminfit.ratioBand
import dev.garminfit.splitLabel
import dev.garminfit.teFocus
import dev.garminfit.one
import dev.garminfit.two
import dev.garminfit.exerciseLabel
import dev.garminfit.muscleLabel
import dev.garminfit.Profile
import dev.garminfit.Repo
import dev.garminfit.Split
import dev.garminfit.activityType
import dev.garminfit.activityIcon
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.roundToInt

@Composable
fun ActivitiesScreen(initialId: Long?, onClosed: () -> Unit) {
    val ctx = LocalContext.current
    val loader = rememberCachedLoad("activities") {
        coroutineScope {
            val profile = async { runCatching { Repo.me(ctx).profile }.getOrNull() }
            Repo.api(ctx).activities(LocalDate.now().minusDays(365).toString()) to profile.await()
        }
    }
    var selected by rememberSaveable(initialId) { mutableStateOf(initialId) }
    var sharing by remember { mutableStateOf<Activity?>(null) }
    var map by remember { mutableStateOf<List<List<Double>>?>(null) }
    LoadView(
        loader,
        loadingText = tr("Loading the past year of activities", "Memuat aktivitas setahun terakhir"),
        isEmpty = { it.first.isEmpty() },
        emptyText = tr("No activities in the past year. Activities from your Garmin watch show up here after a sync.", "Belum ada aktivitas dalam setahun terakhir. Aktivitas dari jam Garmin muncul di sini setelah sync."),
    ) { (list, profile) ->
        val open = list.firstOrNull { it.id == selected }?.id
        // list scroll lives out here, so coming back from a detail lands where you left the list
        val listState = rememberLazyListState()
        val reduce = LocalReduceMotion.current
        AnimatedContent(
            open,
            transitionSpec = {
                if (reduce) EnterTransition.None togetherWith ExitTransition.None
                else if (targetState != null) (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 }) togetherWith fadeOut(tween(120))
                else (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 8 }) togetherWith fadeOut(tween(120))
            },
            label = "detail",
        ) { id ->
        val current = list.firstOrNull { it.id == id }
        val share = sharing
        val route = map
        if (share != null && current != null && share.id == current.id) {
            ShareScreen(share.shareContent(), onBack = { sharing = null })
        } else if (route != null && current != null) {
            MapScreen(MapContent.Route(route), tr("Back to details", "Kembali ke detail"), onBack = { map = null }) { PaceLegend() }
        } else if (current != null) {
            // clearing the caller's id too lets the same activity be opened again from another tab
            val close = { selected = null; onClosed() }
            BackHandler(onBack = close)
            ActivityDetail(current, list, profile, onBack = close, onShare = { sharing = it }, onMap = { map = it })
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                item { Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) { ScreenTitle(tr("Activities", "Aktivitas")) } }
                items(list, key = { it.id }) { a ->
                    ActivityRow(a) { selected = a.id }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = K.track)
                }
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // neutral fill on purpose: color in this app means status, and the type is not a status
        Box(Modifier.size(40.dp).clip(CircleShape).background(K.track), contentAlignment = Alignment.Center) {
            Glyph(activityIcon(a.type), K.ink, 22.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(a.name ?: activityType(a.type), Modifier.weight(1f, fill = false), style = num(20.sp, FontWeight.Bold), color = K.ink)
                if (a.prs.isNotEmpty()) PrChip(a.prs)
            }
            Text(
                "${dayLabel(a.date)}, ${time(a.startTime)}" + if (a.name != null && a.name != activityType(a.type)) " · ${activityType(a.type)}" else "",
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
private fun ActivityDetail(a: Activity, all: List<Activity>, profile: Profile?, onBack: () -> Unit, onShare: (Activity) -> Unit, onMap: (List<List<Double>>) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val ctx = LocalContext.current
        val detail = rememberCachedLoad("activity", a.id) { Repo.api(ctx).activity(a.id) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { BackLink(tr("All activities", "Semua aktivitas"), onBack) }
            // waits for the detail so the share image can include the route or body map
            AccentButton(tr("Share", "Bagikan"), icon = R.drawable.ic_share, onClick = { onShare((detail.state as? Load.Ok)?.data ?: a) }, enabled = detail.state !is Load.Loading)
        }
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Label("${activityType(a.type)} · ${weekday(a.date)} ${dayMonth(a.date)}, ${time(a.startTime)}")
            Text((a.name ?: activityType(a.type)).uppercase(), style = num(32.sp), color = K.ink)
        }
        val strength = a.type == "strength_training"
        DetailCard(tr("Route", "Rute"), R.drawable.ic_route, detail, expected = a.hasDistance(), pick = { it.route }) { RouteView(it) { onMap(it) } }
        DetailCard(tr("Muscles worked", "Otot yang dilatih"), R.drawable.ic_fitness_center, detail, expected = strength, pick = { it.strength }) { BodyView(it) }
        val full = (detail.state as? Load.Ok)?.data
        val extra = full?.extra
        StatsCard(a, full?.strength, extra)
        full?.strength?.takeIf { it.exercises.isNotEmpty() }?.let {
            Panel(Modifier.fillMaxWidth()) { CardTitle(tr("Exercises", "Latihan"), icon = R.drawable.ic_fitness_center); ExercisesView(it) }
        }
        full?.series?.let { SeriesCard(it, a.hasDistance(), a.distanceM) }
        extra?.let { DynamicsCard(it) }
        extra?.bestSplits?.takeIf { it.isNotEmpty() }?.let { BestSplitsCard(it, full.prs) }
        full?.similar?.let { SimilarCard(a, it) }
        EffectCard(a, all, teFocus(extra?.teLabel))
        extra?.let { ImpactCard(it) }
        a.hrZonesS?.let { ZonesCard(it, profile?.hrZoneFloors) }
        a.splits?.let { SplitsCard(it) }
    }
}

/** Card fed by the per-activity detail request: shows its own loading and error state, hides when the field is absent. */
@Composable
private fun <T : Any> DetailCard(
    title: String,
    @androidx.annotation.DrawableRes icon: Int,
    detail: Loader<Activity>,
    expected: Boolean,
    pick: (Activity) -> T?,
    content: @Composable (T) -> Unit,
) {
    val state = detail.state
    val value = (state as? Load.Ok)?.data?.let(pick)
    if (value == null && (state is Load.Ok || !expected)) return
    Panel(Modifier.fillMaxWidth()) {
        CardTitle(title, icon = icon)
        when {
            value != null -> content(value)
            state is Load.Err -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Couldn't load", "Gagal dimuat"), style = T.body, color = K.muted, modifier = Modifier.weight(1f))
                Box(Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClick = detail.reload).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(tr("Try again", "Coba lagi"), style = body(14.sp, FontWeight.SemiBold), color = K.good)
                }
            }
            else -> Shimmer(Modifier.fillMaxWidth().height(240.dp), 16.dp)
        }
    }
}

@Composable
private fun RouteView(route: List<List<Double>>, onExpand: () -> Unit) {
    MapPreview(MapContent.Route(route), tr("Route map, colored by pace", "Peta rute, diwarnai sesuai pace"), onExpand)
    PaceLegend()
}

@Composable
private fun PaceLegend() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Slow", "Lambat"), style = T.caption, color = K.muted)
        Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(K.low, K.fair, K.good))))
        Text(tr("Fast", "Cepat"), style = T.caption, color = K.muted)
    }
}

@Composable
private fun BodyView(s: Strength) {
    Canvas(Modifier.fillMaxWidth().height(280.dp)) {
        drawIntoCanvas { drawBody(it.nativeCanvas, 0f, 0f, size.width, size.height, s.muscles, K.good, K.track) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(K.good, tr("Primary", "Utama"))
        LegendDot(K.good.copy(alpha = 0.28f), tr("Secondary", "Pendukung"))
    }
    val main = s.muscles.entries.filter { it.value.primary > 0 }.sortedByDescending { it.value.primary }.map { muscleLabel(it.key) }
    if (main.isNotEmpty()) Text(main.joinToString(", "), style = T.body, color = K.body2)
    else Text(tr("The watch didn't recognize these exercises, so no muscles can be marked yet.", "Jam belum mengenali latihannya, jadi otot yang dilatih belum bisa ditandai."), style = T.body, color = K.muted)
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Text(text, style = T.caption, color = K.muted)
    }
}

@Composable
private fun ExercisesView(s: Strength) {
    Column {
        s.exercises.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = K.track)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(exerciseLabel(e.category, e.sub), style = body(15.sp, FontWeight.SemiBold), color = if (e.category == "UNKNOWN") K.muted else K.ink, modifier = Modifier.weight(1f))
                Text(
                    if (e.category == "UNKNOWN") tr("${e.sets} sets", "${e.sets} set") else tr("${e.sets} sets · ${e.reps} reps", "${e.sets} set · ${e.reps} rep"),
                    style = num(18.sp, FontWeight.Bold),
                    color = K.ink,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(a: Activity, strength: Strength?, e: Extra?) {
    val paused = (e?.elapsedS ?: 0.0) - (a.durationS ?: 0.0) > 60
    val stats = listOfNotNull(
        strength?.totalSets?.let { "$it" to tr("sets", "set") },
        strength?.totalReps?.let { "$it" to tr("reps", "rep") },
        duration(a.durationS) to tr("duration", "durasi"),
        e?.elapsedS?.takeIf { paused }?.let { duration(it) to tr("total with pauses", "total dengan jeda") },
        a.avgSpeed?.takeIf { a.hasDistance() }?.let { pace(it) to "pace /km" },
        e?.maxSpeed?.takeIf { a.hasDistance() }?.let { pace(it) to tr("fastest pace", "pace tercepat") },
        // grade-adjusted pace only says something new on a hilly route
        e?.gapSpeed?.takeIf { a.hasDistance() && (a.elevationGain ?: 0.0) >= 30 }?.let { pace(it) to tr("grade adjusted pace", "pace setara datar") },
        a.avgHr?.let { it.int() to tr("avg HR", "HR rata-rata") },
        a.maxHr?.let { it.int() to tr("max HR", "HR maks") },
        a.calories?.let { it.int() to tr("kcal", "kkal") },
        a.elevationGain?.takeIf { it > 0 }?.let { "${it.int()} m" to tr("elevation gain", "elevasi naik") },
        e?.elevationLoss?.takeIf { it > 0 }?.let { "${it.int()} m" to tr("elevation loss", "elevasi turun") },
        e?.steps?.takeIf { a.hasDistance() }?.let { it.int() to tr("steps", "langkah") },
    )
    Panel(Modifier.fillMaxWidth(), gap = 16.dp) {
        if (a.hasDistance()) Value(km(a.distanceM), "km", T.hero, unitStyle = body(18.sp), gap = 6.dp, roll = true)
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
private fun EffectCard(a: Activity, all: List<Activity>, focus: String?) {
    val rows = listOfNotNull(
        a.aerobicTe?.let { Triple(tr("Aerobic", "Aerobik"), it, K.good) to true },
        a.anaerobicTe?.let { Triple(tr("Anaerobic", "Anaerobik"), it, K.blue) to false },
    )
    if (rows.isEmpty() && a.trainingLoad == null) return
    Panel(Modifier.fillMaxWidth(), gap = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(tr("Training effect", "Efek ke tubuh"), Modifier.weight(1f), icon = R.drawable.ic_trending_up)
            focus?.let { NeutralChip(tr("Focus: $it", "Fokus: $it")) }
        }
        rows.forEach { (r, aerobic) ->
            val (name, v, c) = r
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(name, Modifier.weight(1f).alignByBaseline(), style = body(14.sp), color = K.body2)
                    Value(v.one(), "/ 5", num(24.sp), gap = 6.dp, modifier = Modifier.alignByBaseline())
                }
                SegmentBar(v, c)
                Text(teLabel(v, aerobic), style = body(13.sp, FontWeight.SemiBold), color = if (v >= 2) c else K.muted)
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
                        if (heaviest) append(tr(" · hardest session of the last 7 days.", " · sesi terberat 7 hari terakhir."))
                    },
                    Modifier.padding(top = 12.dp),
                    style = T.bodyS,
                    color = K.body2,
                )
            }
        }
    }
}

private class Track(val name: String, val values: List<Double?>, val color: Color, val summary: String, val description: String, val at: (Double) -> String)

/**
 * Distance in km at the end of each series bucket: speed integrated over timer time, then scaled so the
 * last point equals the recorded distance (bucket averages drift a little from the watch's own total).
 */
private fun cumulativeKm(s: Series, totalM: Double?): List<Double>? {
    val speed = s.speed ?: return null
    var acc = 0.0
    val raw = s.t.mapIndexed { i, t ->
        val dt = (t - (s.t.getOrNull(i - 1) ?: 0)).coerceAtLeast(0)
        acc += (speed.getOrNull(i) ?: 0) / 100.0 * dt
        acc
    }
    val scale = if (acc > 0 && totalM != null && totalM > 0) totalM / acc else 1.0
    return raw.map { it * scale / 1000 }
}

@Composable
private fun SeriesCard(s: Series, distance: Boolean, totalM: Double?) {
    fun List<Int?>?.d(scale: Double = 1.0) = this?.map { it?.div(scale) }
    // values are bucket averages, so only averages are summarized here; true peaks live in the stat cards
    fun List<Double?>.stats() = filterNotNull().takeIf { it.isNotEmpty() }
    val tracks = listOfNotNull(
        s.speed.d(100.0)?.takeIf { distance }?.let { v ->
            // speed is plotted so faster sits higher; labels convert it back to pace
            v.stats()?.let { Track("Pace", v, K.good, tr("avg ${pace(it.average())} /km", "rata-rata ${pace(it.average())} /km"), tr("Pace over the session", "Grafik pace sepanjang sesi")) { x -> "${pace(x)} /km" } }
        },
        s.hr.d()?.let { v -> v.stats()?.let { Track(tr("Heart rate", "Detak jantung"), v, K.low, tr("avg ${it.average().int()} bpm", "rata-rata ${it.average().int()} bpm"), tr("Heart rate over the session", "Grafik detak jantung sepanjang sesi")) { x -> "${x.int()} bpm" } } },
        s.cadence.d()?.takeIf { distance }?.let { v -> v.stats()?.let { Track(tr("Cadence", "Kadens"), v, K.blue, tr("avg ${it.average().int()} spm", "rata-rata ${it.average().int()} spm"), tr("Cadence over the session", "Grafik kadens sepanjang sesi")) { x -> "${x.int()} spm" } } },
        s.power.d()?.let { v -> v.stats()?.let { Track("Power", v, K.fair, tr("avg ${it.average().int()} W", "rata-rata ${it.average().int()} W"), tr("Power over the session", "Grafik power sepanjang sesi")) { x -> "${x.int()} W" } } },
        s.elev.d(10.0)?.takeIf { distance }?.let { v ->
            v.stats()?.takeIf { it.max() - it.min() >= 3 }?.let { Track(tr("Elevation", "Elevasi"), v, K.muted, tr("${it.min().int()} to ${it.max().int()} m", "${it.min().int()} sampai ${it.max().int()} m"), tr("Elevation over the session", "Grafik elevasi sepanjang sesi")) { x -> "${x.int()} m" } }
        },
    )
    if (tracks.isEmpty()) return
    val kms = remember(s, totalM) { if (distance) cumulativeKm(s, totalM) else null }
    // one index shared by every chart, so dragging any of them reads the same moment across all
    var sel by remember { mutableStateOf<Int?>(null) }
    Panel(Modifier.fillMaxWidth(), gap = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(tr("Over the session", "Sepanjang sesi"), Modifier.weight(1f), icon = R.drawable.ic_show_chart)
            val i = sel
            if (i != null) {
                Text(
                    listOfNotNull(kms?.getOrNull(i)?.let { "km ${it.two()}" }, duration(s.t.getOrNull(i)?.toDouble())).joinToString(" · "),
                    style = num(18.sp, FontWeight.Bold), color = K.good,
                )
            } else {
                Text(tr("Drag a chart", "Geser chart"), style = T.caption, color = K.muted)
            }
        }
        tracks.forEach { track ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(track.name, Modifier.weight(1f), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                    val v = sel?.let { track.values.getOrNull(it) }
                    if (v != null) Text(track.at(v), style = num(16.sp, FontWeight.Bold), color = K.ink)
                    else Text(track.summary, style = T.caption, color = K.muted)
                }
                LineChart(
                    track.values, "${track.description}, ${track.summary}", height = 72.dp, color = track.color, width = 1.8.dp,
                    selected = sel, onSelect = { sel = it },
                )
            }
        }
        KmRuler(kms, s.t, sel)
    }
}

/** Axis under the session charts: a tick at every full km (or every 5 min without distance) and the touched point. */
@Composable
private fun KmRuler(kms: List<Double>?, t: List<Int>, sel: Int?) {
    val n = t.size
    if (n < 2) return
    val ticks = if (kms != null) {
        (1..kms.last().toInt()).mapNotNull { k -> kms.indexOfFirst { it >= k }.takeIf { it >= 0 } }
    } else {
        (1..(t.last() / 300)).mapNotNull { m -> t.indexOfFirst { it >= m * 300 }.takeIf { it >= 0 } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            fun x(i: Int) = size.width * i / (n - 1f)
            drawLine(K.border, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            ticks.forEach { drawLine(K.muted, Offset(x(it), 0f), Offset(x(it), 6.dp.toPx()), 1.dp.toPx()) }
            sel?.let { drawLine(K.good, Offset(x(it), 0f), Offset(x(it), size.height), 2.dp.toPx()) }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(tr("start", "mulai"), style = T.mono, color = K.muted)
            Spacer(Modifier.weight(1f))
            Text(kms?.let { "${it.last().two()} km · " }.orEmpty() + duration(t.last().toDouble()), style = T.mono, color = K.muted)
        }
    }
}

/** One labelled reading with its value on the right and a one-line explanation under it. */
@Composable
private fun InfoRow(name: String, value: String, unit: String, note: String, chip: (@Composable () -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, Modifier.weight(1f), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
            chip?.invoke()
            Value(value, unit, num(22.sp, FontWeight.Bold), gap = 4.dp)
        }
        Text(note, style = T.bodyS, color = K.muted)
    }
}

@Composable
private fun BandChip(b: DynamicsBand) = if (b.status != null) StatusChip(b.label, b.status) else NeutralChip(b.label)

@Composable
private fun DynamicsCard(e: Extra) {
    if (e.cadence == null && e.gctMs == null && e.power == null) return
    Panel(Modifier.fillMaxWidth(), gap = 16.dp) {
        CardTitle(tr("Running form", "Teknik lari"), icon = R.drawable.ic_directions_run)
        e.cadence?.let { InfoRow(tr("Cadence", "Kadens"), it.int(), "spm", tr("Steps per minute. A high cadence usually means short, light steps.", "Langkah per menit. Kadens tinggi biasanya berarti langkah pendek dan ringan.")) { BandChip(cadenceBand(it)) } }
        e.strideCm?.let { InfoRow(tr("Stride length", "Panjang langkah"), (it / 100).two(), "m", tr("Average distance of one step.", "Jarak rata-rata satu langkah.")) }
        e.gctMs?.let { InfoRow(tr("Ground contact", "Kontak tanah"), it.int(), "ms", tr("How long your foot stays on the ground each step. Shorter is more reactive.", "Lama kaki menempel di tanah tiap langkah. Makin singkat makin reaktif.")) { BandChip(gctBand(it)) } }
        e.voCm?.let { InfoRow(tr("Vertical oscillation", "Osilasi vertikal"), it.one(), "cm", tr("How high your body bounces each step.", "Seberapa tinggi badan memantul tiap langkah.")) { BandChip(oscillationBand(it)) } }
        e.vrPct?.let { InfoRow(tr("Vertical ratio", "Rasio vertikal"), it.one(), "%", tr("Bounce relative to stride length. Lower is more efficient.", "Pantulan dibanding panjang langkah. Makin kecil makin efisien.")) { BandChip(ratioBand(it)) } }
        e.power?.let { p ->
            InfoRow("Power", p.int(), "W", tr("Average power you produced", "Tenaga rata-rata yang kamu hasilkan") + (e.powerMax?.let { tr(", peaking at ${it.int()} W.", ", puncaknya ${it.int()} W.") } ?: "."))
        }
        if (e.cadence != null || e.gctMs != null) {
            Text(tr("Labels follow Garmin's running dynamics scale, compared with other runners.", "Label mengikuti skala dinamika lari Garmin, dibandingkan dengan pelari lain."), style = T.caption, color = K.muted)
        }
    }
}

@Composable
private fun BestSplitsCard(splits: List<Split>, prs: List<Int>) {
    Panel(Modifier.fillMaxWidth(), gap = 10.dp) {
        CardTitle(tr("Fastest times in this session", "Waktu tercepat di sesi ini"), icon = R.drawable.ic_timer)
        splits.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = K.track)
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(splitLabel(s.distanceM), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                    if (s.distanceM?.roundToInt() in prs) StatusChip(tr("New record", "Rekor baru"), Status.GOOD)
                }
                val secs = s.durationS ?: 0.0
                Text(paceSec(secs * 1000 / (s.distanceM ?: 1000.0)) + " /km", Modifier.padding(end = 16.dp), style = T.bodyS, color = K.muted)
                Text(raceTime(secs), style = num(20.sp, FontWeight.Bold), color = K.ink)
            }
        }
    }
}

@Composable
private fun PrChip(prs: List<Int>) {
    Text(
        "PR " + prs.joinToString(" · ") { splitShort(it) },
        Modifier.clip(CircleShape).background(K.good).padding(horizontal = 8.dp, vertical = 2.dp),
        style = body(12.sp, FontWeight.Bold, 1.2f),
        color = K.onAccent,
    )
}

@Composable
private fun SimilarCard(a: Activity, s: Similar) {
    val speed = a.avgSpeed ?: return
    // seconds per km: negative means this run was faster
    val paceDiff = (1000 / speed - 1000 / s.avgSpeed).roundToInt()
    val hrDiff = a.avgHr?.let { h -> s.avgHr?.let { (h - it).roundToInt() } }
    val pacePart = when {
        paceDiff < 0 -> tr("${-paceDiff} s/km faster", "${-paceDiff} detik/km lebih cepat")
        paceDiff > 0 -> tr("$paceDiff s/km slower", "$paceDiff detik/km lebih lambat")
        else -> tr("Same pace", "Pace sama")
    }
    val hrPart = hrDiff?.let {
        when {
            it < 0 -> tr(", HR ${-it} bpm lower", ", HR ${-it} bpm lebih rendah")
            it > 0 -> tr(", HR $it bpm higher", ", HR $it bpm lebih tinggi")
            else -> tr(", same HR", ", HR sama")
        }
    }.orEmpty()
    Panel(Modifier.fillMaxWidth(), gap = 16.dp) {
        CardTitle(tr("Compared with similar runs", "Dibanding run serupa"), icon = R.drawable.ic_bar_chart)
        Text(tr("$pacePart$hrPart than the average of your last ${s.count} runs of similar distance.", "$pacePart$hrPart dari rata-rata ${s.count} run terakhir dengan jarak mirip."), style = T.body, color = K.body2)
        InfoRow(tr("Avg pace", "Pace rata-rata"), pace(s.avgSpeed), "/km", tr("This run ${pace(speed)} /km.", "Run ini ${pace(speed)} /km."))
        s.avgHr?.let { InfoRow(tr("Avg HR", "HR rata-rata"), it.int(), "bpm", tr("This run ${a.avgHr.int()} bpm.", "Run ini ${a.avgHr.int()} bpm.")) }
        if (s.efficiency != null && s.thisEfficiency != null) {
            val better = s.thisEfficiency >= s.efficiency
            InfoRow(
                tr("Efficiency", "Efisiensi"), s.thisEfficiency.two(), tr("m/beat", "m/detak"),
                tr("Meters per heartbeat, similar runs average ${s.efficiency.two()}. Rising over time means you're getting fitter.", "Meter per detak jantung, rata-rata run serupa ${s.efficiency.two()}. Naik dari waktu ke waktu berarti makin bugar."),
            ) { StatusChip(if (better) tr("More efficient", "Lebih efisien") else tr("Less efficient", "Kurang efisien"), if (better) Status.GOOD else Status.FAIR) }
        }
    }
}

@Composable
private fun ImpactCard(e: Extra) {
    val intensity = (e.moderateMin ?: 0.0) + 2 * (e.vigorousMin ?: 0.0)
    if (e.bbDelta == null && e.waterMl == null && intensity <= 0) return
    Panel(Modifier.fillMaxWidth(), gap = 16.dp) {
        CardTitle(tr("Session impact", "Dampak sesi ini"), icon = R.drawable.ic_bolt)
        e.bbDelta?.let { InfoRow("Body Battery", if (it < 0) "−${(-it).int()}" else "+${it.int()}", "", tr("Energy used during the session.", "Energi yang terpakai selama sesi.")) }
        e.waterMl?.let { InfoRow(tr("Sweat", "Keringat"), (it / 1000).one(), "L", tr("Estimated fluid lost. Replace it gradually after the session.", "Perkiraan cairan yang keluar. Ganti pelan-pelan setelah sesi.")) }
        if (intensity > 0) {
            InfoRow(tr("Intensity minutes", "Menit intensitas"), intensity.int(), tr("min", "mnt"), tr("Vigorous minutes count double, as on Garmin. The WHO weekly target is 150 minutes.", "Menit berat dihitung dua kali, seperti di Garmin. Target mingguan WHO 150 menit."))
        }
        e.vo2max?.let { InfoRow("VO2 max", it.int(), "", tr("Garmin's estimate after this session.", "Perkiraan Garmin setelah sesi ini.")) }
    }
}

private val ZONE_NAMES: List<String> get() =
    if (isId()) listOf("Pemanasan", "Mudah", "Aerobik", "Ambang", "Maksimal")
    else listOf("Warm up", "Easy", "Aerobic", "Threshold", "Maximum")

@Composable
private fun ZonesCard(zones: List<Double?>, floors: List<Double>?) {
    val secs = zones.map { it ?: 0.0 }
    val total = secs.sum()
    if (total <= 0) return
    val bpm = floors?.takeIf { it.size == 5 }?.map { it.roundToInt() }?.let { f ->
        listOf("< ${f[1]} bpm", "${f[1]}–${f[2]}", "${f[2]}–${f[3]}", "${f[3]}–${f[4]}", "> ${f[4]}")
    }
    Panel(Modifier.fillMaxWidth(), gap = 12.dp) {
        CardTitle(tr("Heart rate zones", "Zona detak jantung"), icon = R.drawable.ic_monitor_heart)
        StackedBar(
            secs.mapIndexed { i, s -> s to K.zones[i] },
            description = secs.mapIndexed { i, s -> tr("Zone ${i + 1} ${(s / total * 100).roundToInt()} percent", "Zona ${i + 1} ${(s / total * 100).roundToInt()} persen") }.joinToString(", "),
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
    val laps = splits.filter { abs((it.distanceM ?: 0.0) - 1000) <= 10 && (it.durationS ?: 0.0) > 0 }
    val paces = laps.map { it.durationS!! * 1000 / it.distanceM!! }
    if (paces.size < 2 || paces.size < splits.size - 1) return
    val fast = paces.min()
    val slow = paces.max()
    val avg = paces.average()
    val half = paces.size / 2
    val negative = paces.drop(paces.size - half).average() < paces.take(half).average()
    var sel by remember { mutableStateOf<Int?>(null) }
    val reveal = rememberReveal(paces, durationMs = 600)
    Panel(Modifier.fillMaxWidth(), gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(tr("Km splits", "Split per km"), Modifier.weight(1f), icon = R.drawable.ic_flag)
            val i = sel
            when {
                i != null -> Text(
                    "Km ${i + 1} · ${minSec(laps[i].durationS!!)}" + (laps[i].avgHr?.let { " · HR ${it.int()}" } ?: ""),
                    style = num(16.sp, FontWeight.Bold), color = K.good,
                )
                negative -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Glyph(R.drawable.ic_check_circle, K.muted, 16.dp)
                    Text("negative split", style = T.caption, color = K.muted)
                }
            }
        }
        paces.forEachIndexed { i, p ->
            val w = if (slow > fast) 0.4f + ((slow - p) / (slow - fast)).toFloat() * 0.6f else 1f
            val on = sel == null || sel == i
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button) { sel = if (sel == i) null else i }
                    .padding(vertical = 3.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "Km ${i + 1}, ${paceSec(p)} per km" },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", Modifier.width(22.dp), style = T.mono, color = if (sel == i) K.good else K.muted)
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(K.track)) {
                    // bars grow in one after another, 30 ms apart
                    val grow = ((reveal.value * (paces.size + 8) - i) / 8f).coerceIn(0f, 1f)
                    Box(
                        Modifier.fillMaxWidth(w * grow).height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background((if (p < avg) K.good else K.good.copy(alpha = 0.5f)).copy(alpha = if (on) (if (p < avg) 1f else 0.5f) else 0.18f)),
                    )
                }
                Text(paceSec(p), Modifier.width(40.dp), style = num(16.sp, FontWeight.Bold), color = if (on) K.ink else K.muted, textAlign = TextAlign.End)
            }
        }
    }
}
