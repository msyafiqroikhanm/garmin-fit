package dev.garminfit.ui

import dev.garminfit.R
import androidx.annotation.DrawableRes

import dev.garminfit.tr
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garminfit.Bucket
import dev.garminfit.Recap
import dev.garminfit.Repo
import dev.garminfit.dateLabel
import dev.garminfit.duration
import dev.garminfit.km
import dev.garminfit.splitShort
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.coroutineScope


private fun periodLabel(period: String, key: String): String =
    if (period == "year") key
    else YearMonth.parse(key).let { "${it.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() }} ${it.year}" }

private fun shift(period: String, key: String, by: Long): String =
    if (period == "year") (key.toInt() + by).toString() else YearMonth.parse(key).plusMonths(by).toString()

/** First and last day of a recap period, matching the backend's period_range. */
private fun periodRange(period: String, key: String): Pair<String, String> =
    if (period == "year") "$key-01-01" to "$key-12-31"
    else YearMonth.parse(key).let { it.atDay(1).toString() to it.atEndOfMonth().toString() }

private fun currentKey(period: String) = if (period == "year") LocalDate.now().year.toString() else YearMonth.now().toString()

@Composable
fun RecapScreen(onBack: () -> Unit, onOpenActivity: (Long) -> Unit) {
    val ctx = LocalContext.current
    var period by rememberSaveable { mutableStateOf("month") }
    var key by rememberSaveable { mutableStateOf(currentKey("month")) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    val loader = rememberCachedLoad("recap", period, key) {
        coroutineScope {
            val (start, end) = periodRange(period, key)
            val routes = async { runCatching { Repo.api(ctx).routes(start, end) }.getOrDefault(emptyList()) }
            Repo.api(ctx).recap(period, key) to routes.await()
        }
    }
    val ok = (loader.state as? Load.Ok)?.data
    if (sharing && ok != null) {
        ShareScreen(recapContent(ok.first, ok.second, periodLabel(period, key)), onBack = { sharing = false }, backLabel = tr("Back to recap", "Kembali ke rekap"))
        return
    }
    if (mapOpen && ok != null && ok.second.isNotEmpty()) {
        MapScreen(MapContent.Heat(ok.second), tr("Back to recap", "Kembali ke rekap"), onBack = { mapOpen = false })
        return
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { BackLink(tr("Training", "Latihan"), onBack) }
            AccentButton(tr("Share", "Bagikan"), icon = R.drawable.ic_share, onClick = { sharing = true }, enabled = ok != null && ok.first.count > 0)
        }
        Segmented(listOf("month", "year"), period, { if (it == "month") tr("Month", "Bulan") else tr("Year", "Tahun") }) {
            period = it
            key = currentKey(it)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper(R.drawable.ic_chevron_left, tr("Previous period", "Periode sebelumnya"), enabled = true) { key = shift(period, key, -1) }
            Text(periodLabel(period, key).uppercase(), Modifier.weight(1f), style = T.screenTitle, color = K.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Stepper(R.drawable.ic_chevron_right, tr("Next period", "Periode berikutnya"), enabled = key < currentKey(period)) { key = shift(period, key, 1) }
        }
        LoadView(loader, loadingText = tr("Loading recap", "Memuat rekap"), isEmpty = { it.first.count == 0 }, emptyText = tr("No activities in ${periodLabel(period, key)}.", "Belum ada aktivitas di ${periodLabel(period, key)}.")) { (r, routes) ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecapHero(r, period)
                BucketsCard(r.buckets, period)
                if (routes.isNotEmpty()) {
                    Panel(Modifier.fillMaxWidth()) {
                        CardTitle(tr("Run map", "Peta lari"), icon = R.drawable.ic_map)
                        MapPreview(MapContent.Heat(routes), tr("Combined map of ${routes.size} routes", "Peta gabungan ${routes.size} rute"), { mapOpen = true }, height = 300.dp)
                        Text(tr("${routes.size} routes around where you start most runs. Brighter paths are run more often.", "${routes.size} rute di area kamu paling sering mulai lari. Jalur yang makin terang makin sering dilewati."), style = T.caption, color = K.muted)
                    }
                }
                HighlightsCard(r, onOpenActivity)
            }
        }
    }
}

@Composable
private fun Stepper(@DrawableRes icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, if (enabled) K.good else K.track, 32.dp) }
}

@Composable
private fun RecapHero(r: Recap, period: String) {
    Panel(Modifier.fillMaxWidth(), padding = 20.dp, gap = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Value(km(r.distanceM), "km", T.hero, unitStyle = body(18.sp), gap = 6.dp, roll = true)
            val diff = r.distanceM - r.previous.distanceM
            if (r.previous.count > 0) {
                DeltaPill(
                    "${if (diff >= 0) "+" else "−"}${km(kotlin.math.abs(diff))} km " + if (period == "year") tr("vs last year", "dari tahun lalu") else tr("vs last month", "dari bulan lalu"),
                    if (diff >= 0) Status.GOOD else null,
                    up = diff >= 0,
                )
            }
        }
        val stats = listOf(
            "${r.count}" to tr("sessions", "sesi"),
            duration(r.durationS) to tr("total time", "total waktu"),
            "${r.activeDays}" to tr("active days", "hari aktif"),
            "${r.byKind["run"] ?: 0}" to tr("runs", "lari"),
            "${r.byKind["strength"] ?: 0}" to "strength",
            tr("${r.streakWeeks} wk", "${r.streakWeeks} mgg") to tr("active streak", "streak aktif"),
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            stats.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (v, l) -> Stat(v, l, num(24.sp, FontWeight.Bold), Modifier.weight(1f)) }
                }
            }
        }
        Text(tr("Streak counts consecutive weeks with at least one activity.", "Streak dihitung dari minggu berturut-turut yang punya minimal satu aktivitas."), style = T.caption, color = K.muted)
    }
}

@Composable
private fun BucketsCard(buckets: List<Bucket>, period: String) {
    val max = buckets.maxOfOrNull { it.distanceM }?.takeIf { it > 0 } ?: return
    val labels = buckets.map {
        if (period == "year") YearMonth.parse(it.key).month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3)
        else LocalDate.parse(it.key).let { d -> "${d.dayOfMonth}/${d.monthValue}" }
    }
    var sel by remember { mutableStateOf<Int?>(null) }
    val reveal = rememberReveal(buckets, durationMs = 650)
    val haptic = LocalHapticFeedback.current
    Panel(Modifier.fillMaxWidth(), gap = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(if (period == "year") tr("Distance per month", "Jarak per bulan") else tr("Distance per week", "Jarak per minggu"), Modifier.weight(1f), icon = R.drawable.ic_bar_chart)
            sel?.let { i ->
                val b = buckets[i]
                Text("${km(b.distanceM)} km · ${duration(b.durationS)}", style = num(16.sp, FontWeight.Bold), color = K.good)
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(120.dp).semantics {
                contentDescription = buckets.zip(labels).joinToString(", ") { (b, l) -> "$l ${km(b.distanceM)} km" }
            }.scrub(buckets.size, bars = true) { i ->
                if (i != null && i != sel) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                sel = i
            },
        ) {
            val gap = 6.dp.toPx()
            val bw = (size.width - gap * (buckets.size - 1)) / buckets.size
            buckets.forEachIndexed { i, b ->
                // each bar starts growing 30 ms after the one before it
                val grow = ((reveal.value * (buckets.size + 6) - i) / 6f).coerceIn(0f, 1f)
                val bh = (b.distanceM / max).toFloat() * size.height * grow
                val x = i * (bw + gap)
                drawRoundRect(K.track, Offset(x, 0f), Size(bw, size.height), CornerRadius(6.dp.toPx()))
                if (bh > 0) drawRoundRect(if (sel == null || sel == i) K.good else K.good.copy(alpha = 0.35f), Offset(x, size.height - bh), Size(bw, bh), CornerRadius(6.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l -> Text(l, Modifier.weight(1f), style = T.mono, color = if (sel == i) K.ink else K.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        val i = sel
        Text(
            if (i == null) tr("Touch a bar for its totals.", "Sentuh batang untuk lihat totalnya.")
            else tr("${buckets[i].count} sessions in this ${if (period == "year") "month" else "week"}", "${buckets[i].count} sesi di ${if (period == "year") "bulan" else "minggu"} ini"),
            style = T.caption, color = K.muted,
        )
    }
}

@Composable
private fun HighlightsCard(r: Recap, onOpen: (Long) -> Unit) {
    if (r.longestRun == null && r.prs.isEmpty()) return
    Panel(Modifier.fillMaxWidth(), gap = 4.dp) {
        CardTitle(tr("Highlights", "Sorotan"), icon = R.drawable.ic_emoji_events)
        r.longestRun?.let { l ->
            HighlightRow(tr("Longest run", "Lari terjauh"), "${dateLabel(l.date)} · ${l.name ?: tr("Run", "Lari")}", "${km(l.distanceM)} km") { onOpen(l.id) }
        }
        r.prs.forEach { p ->
            HorizontalDivider(color = K.track)
            HighlightRow(tr("New record ", "Rekor baru ") + p.distances.joinToString(" · ") { splitShort(it) }, "${dateLabel(p.date)} · ${p.name ?: tr("Run", "Lari")}", "PR") { onOpen(p.activityId) }
        }
    }
}

@Composable
private fun HighlightRow(title: String, sub: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = body(15.sp, FontWeight.SemiBold), color = K.ink)
            Text(sub, style = T.caption, color = K.muted, maxLines = 1)
        }
        Text(value, style = num(22.sp, FontWeight.Bold), color = K.ink)
    }
}

private fun recapContent(r: Recap, routes: List<List<List<Double>>>, label: String) = ShareContent(
    title = tr("RECAP ${label.uppercase()}", "REKAP ${label.uppercase()}"),
    stats = listOf(
        ShareStat("dist", km(r.distanceM), "km", tr("distance", "jarak")),
        ShareStat("count", "${r.count}", "", tr("sessions", "sesi")),
        ShareStat("time", "${(r.durationS / 3600).roundToInt()}", tr("hrs", "jam"), tr("total time", "total waktu")),
        ShareStat("days", "${r.activeDays}", "", tr("active days", "hari aktif")),
        ShareStat("runs", "${r.byKind["run"] ?: 0}", "", tr("runs", "lari")),
        ShareStat("streak", "${r.streakWeeks}", tr("wk", "mgg"), "streak"),
    ),
    // the busiest start is usually home, so shared maps lose each route's first and last 200 m too
    graphic = routes.map { trimEnds(it) }.filter { it.size > 1 }.takeIf { it.isNotEmpty() }?.let { ShareGraphic.Heat(it) },
)
