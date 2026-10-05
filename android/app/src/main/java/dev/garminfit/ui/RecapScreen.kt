package dev.garminfit.ui

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

private val ID = Locale("id", "ID")

private fun periodLabel(period: String, key: String): String =
    if (period == "year") key
    else YearMonth.parse(key).let { "${it.month.getDisplayName(TextStyle.FULL, ID)} ${it.year}" }

private fun shift(period: String, key: String, by: Long): String =
    if (period == "year") (key.toInt() + by).toString() else YearMonth.parse(key).plusMonths(by).toString()

private fun currentKey(period: String) = if (period == "year") LocalDate.now().year.toString() else YearMonth.now().toString()

@Composable
fun RecapScreen(onBack: () -> Unit, onOpenActivity: (Long) -> Unit) {
    val ctx = LocalContext.current
    var period by rememberSaveable { mutableStateOf("month") }
    var key by rememberSaveable { mutableStateOf(currentKey("month")) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val loader = rememberLoad(period, key) {
        val r = Repo.api(ctx).recap(period, key)
        r to runCatching { Repo.api(ctx).routes(r.start, r.end) }.getOrDefault(emptyList())
    }
    val ok = (loader.state as? Load.Ok)?.data
    if (sharing && ok != null) {
        ShareScreen(recapContent(ok.first, ok.second, periodLabel(period, key)), onBack = { sharing = false }, backLabel = "‹ Kembali ke rekap")
        return
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                Text("‹ Kebugaran", style = body(14.sp, FontWeight.SemiBold), color = K.good)
            }
            AccentButton("Bagikan", { sharing = true }, enabled = ok != null && ok.first.count > 0)
        }
        Segmented(listOf("month", "year"), period, { if (it == "month") "Bulan" else "Tahun" }) {
            period = it
            key = currentKey(it)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper("‹", "Periode sebelumnya", enabled = true) { key = shift(period, key, -1) }
            Text(periodLabel(period, key).uppercase(), Modifier.weight(1f), style = T.screenTitle, color = K.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Stepper("›", "Periode berikutnya", enabled = key < currentKey(period)) { key = shift(period, key, 1) }
        }
        LoadView(loader, loadingText = "Memuat rekap", isEmpty = { it.first.count == 0 }, emptyText = "Belum ada aktivitas di ${periodLabel(period, key)}.") { (r, routes) ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecapHero(r, period)
                BucketsCard(r.buckets, period)
                if (routes.isNotEmpty()) {
                    Panel(Modifier.fillMaxWidth()) {
                        CardTitle("Peta lari")
                        val density = LocalDensity.current
                        Canvas(Modifier.fillMaxWidth().height(300.dp).semantics { contentDescription = "Peta gabungan ${routes.size} rute" }) {
                            drawIntoCanvas { drawHeatmap(it.nativeCanvas, 0f, 0f, size.width, size.height, routes, with(density) { 2.5.dp.toPx() }, K.good) }
                        }
                        Text("${routes.size} rute di area kamu paling sering mulai lari. Jalur yang makin terang makin sering dilewati.", style = T.caption, color = K.muted)
                    }
                }
                HighlightsCard(r, onOpenActivity)
            }
        }
    }
}

@Composable
private fun Stepper(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = num(32.sp), color = if (enabled) K.good else K.track) }
}

@Composable
private fun RecapHero(r: Recap, period: String) {
    Panel(Modifier.fillMaxWidth(), padding = 20.dp, gap = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Value(km(r.distanceM), "km", T.hero, unitStyle = body(18.sp), gap = 6.dp)
            val diff = r.distanceM - r.previous.distanceM
            if (r.previous.count > 0) {
                DeltaPill(
                    "${if (diff >= 0) "+" else "−"}${km(kotlin.math.abs(diff))} km dari ${if (period == "year") "tahun" else "bulan"} lalu",
                    if (diff >= 0) Status.GOOD else null,
                )
            }
        }
        val stats = listOf(
            "${r.count}" to "sesi",
            duration(r.durationS) to "total waktu",
            "${r.activeDays}" to "hari aktif",
            "${r.byKind["run"] ?: 0}" to "lari",
            "${r.byKind["strength"] ?: 0}" to "strength",
            "${r.streakWeeks} mgg" to "streak aktif",
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            stats.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (v, l) -> Stat(v, l, num(24.sp, FontWeight.Bold), Modifier.weight(1f)) }
                }
            }
        }
        Text("Streak dihitung dari minggu berturut-turut yang punya minimal satu aktivitas.", style = T.caption, color = K.muted)
    }
}

@Composable
private fun BucketsCard(buckets: List<Bucket>, period: String) {
    val max = buckets.maxOfOrNull { it.distanceM }?.takeIf { it > 0 } ?: return
    val labels = buckets.map {
        if (period == "year") YearMonth.parse(it.key).month.getDisplayName(TextStyle.SHORT, ID).take(3)
        else LocalDate.parse(it.key).let { d -> "${d.dayOfMonth}/${d.monthValue}" }
    }
    Panel(Modifier.fillMaxWidth(), gap = 8.dp) {
        CardTitle(if (period == "year") "Jarak per bulan" else "Jarak per minggu")
        Canvas(
            Modifier.fillMaxWidth().height(120.dp).semantics {
                contentDescription = buckets.zip(labels).joinToString(", ") { (b, l) -> "$l ${km(b.distanceM)} km" }
            },
        ) {
            val gap = 6.dp.toPx()
            val bw = (size.width - gap * (buckets.size - 1)) / buckets.size
            buckets.forEachIndexed { i, b ->
                val bh = (b.distanceM / max).toFloat() * size.height
                val x = i * (bw + gap)
                drawRoundRect(K.track, Offset(x, 0f), Size(bw, size.height), CornerRadius(6.dp.toPx()))
                if (bh > 0) drawRoundRect(K.good, Offset(x, size.height - bh), Size(bw, bh), CornerRadius(6.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { Text(it, Modifier.weight(1f), style = T.mono, color = K.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
private fun HighlightsCard(r: Recap, onOpen: (Long) -> Unit) {
    if (r.longestRun == null && r.prs.isEmpty()) return
    Panel(Modifier.fillMaxWidth(), gap = 4.dp) {
        CardTitle("Sorotan")
        r.longestRun?.let { l ->
            HighlightRow("Lari terjauh", "${dateLabel(l.date)} · ${l.name ?: "Lari"}", "${km(l.distanceM)} km") { onOpen(l.id) }
        }
        r.prs.forEach { p ->
            HorizontalDivider(color = K.track)
            HighlightRow("Rekor baru " + p.distances.joinToString(" · ") { splitShort(it) }, "${dateLabel(p.date)} · ${p.name ?: "Lari"}", "PR") { onOpen(p.activityId) }
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
    title = "REKAP ${label.uppercase()}",
    stats = listOf(
        ShareStat("dist", km(r.distanceM), "km", "jarak"),
        ShareStat("count", "${r.count}", "", "sesi"),
        ShareStat("time", "${(r.durationS / 3600).roundToInt()}", "jam", "total waktu"),
        ShareStat("days", "${r.activeDays}", "", "hari aktif"),
        ShareStat("runs", "${r.byKind["run"] ?: 0}", "", "lari"),
        ShareStat("streak", "${r.streakWeeks}", "mgg", "streak"),
    ),
    // the busiest start is usually home, so shared maps lose each route's first and last 200 m too
    graphic = routes.map { trimEnds(it) }.filter { it.size > 1 }.takeIf { it.isNotEmpty() }?.let { ShareGraphic.Heat(it) },
)
