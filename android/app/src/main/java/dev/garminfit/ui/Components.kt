package dev.garminfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.garminfit.friendly
import retrofit2.HttpException
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt

/** Clears the saved token and returns to the setup screen; provided by Home. */
val LocalLogout = staticCompositionLocalOf<(() -> Unit)?> { null }

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    /** [auth] marks a rejected token, which retrying cannot fix. */
    data class Err(val message: String, val auth: Boolean = false) : Load<Nothing>
}

class Loader<T>(val state: Load<T>, val reload: () -> Unit)

@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    var attempt by remember(*keys) { mutableIntStateOf(0) }
    LaunchedEffect(attempt, *keys) {
        if (state !is Load.Ok) state = Load.Loading
        state = runCatching { block() }.fold({ Load.Ok(it) }, { Load.Err(it.friendly(), it is HttpException && it.code() == 401) })
    }
    return Loader(state) { attempt++ }
}

@Composable
fun <T> LoadView(
    loader: Loader<T>,
    loadingText: String,
    isEmpty: (T) -> Boolean = { false },
    emptyText: String = "",
    content: @Composable (T) -> Unit,
) {
    when (val s = loader.state) {
        Load.Loading -> Centered {
            CircularProgressIndicator(color = K.good, trackColor = K.track)
            Spacer(Modifier.height(16.dp))
            Text(loadingText, style = T.body, color = K.muted)
        }
        is Load.Err -> Centered {
            Text(s.message, style = T.body, color = K.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            val logout = LocalLogout.current
            if (s.auth && logout != null) AccentButton("Masukkan token baru", logout)
            else AccentButton("Coba lagi", loader.reload)
        }
        is Load.Ok -> if (isEmpty(s.data)) Centered {
            Text(emptyText, style = T.body, textAlign = TextAlign.Center, color = K.muted)
        } else content(s.data)
    }
}

/** Loader for one card inside a screen: a short placeholder while loading, nothing on error or empty. */
@Composable
fun <T> CardLoad(loader: Loader<T>, isEmpty: (T) -> Boolean = { false }, content: @Composable (T) -> Unit) {
    when (val s = loader.state) {
        Load.Loading -> Panel(Modifier.fillMaxWidth().height(96.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = K.good, trackColor = K.track) }
        }
        is Load.Err -> Unit // the screen-level loader already reports connection and token problems
        is Load.Ok -> if (!isEmpty(s.data)) content(s.data)
    }
}

@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = K.good, contentColor = K.onAccent),
    ) { Text(text, style = body(15.sp, FontWeight.Bold)) }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** Card surface. Radius 24 for cards, 20 for tiles; the padding steps 20/18/14 mark hero, card, tile. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    radius: Dp = 24.dp,
    gap: Dp = 12.dp,
    color: Color = K.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.clip(RoundedCornerShape(radius)).background(color).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content,
    )
}

@Composable
fun Label(text: String, color: Color = K.muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = T.label, color = color)
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = T.cardTitle, color = K.ink)
}

@Composable
fun ScreenTitle(text: String) {
    Text(text.uppercase(), Modifier.padding(horizontal = 4.dp), style = T.screenTitle, color = K.ink)
}

/** Number plus unit sitting on the same baseline. */
@Composable
fun Value(
    value: String,
    unit: String,
    style: TextStyle,
    unitStyle: TextStyle = T.caption,
    color: Color = K.ink,
    unitColor: Color = K.muted,
    gap: Dp = 4.dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        Text(value, Modifier.alignByBaseline(), style = style, color = color)
        if (unit.isNotEmpty()) Text(unit, Modifier.alignByBaseline(), style = unitStyle, color = unitColor)
    }
}

/** Value over a small caption, used in the stat grids. */
@Composable
fun Stat(value: String, caption: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = K.ink, captionStyle: TextStyle = T.caption) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = style, color = color, maxLines = 1)
        Text(caption, style = captionStyle, color = K.muted)
    }
}

@Composable
fun StatusChip(text: String, status: Status) {
    val c = status.color()
    Row(
        Modifier.clip(CircleShape).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).background(c, CircleShape))
        Text(text, style = body(13.sp, FontWeight.SemiBold, 1.2f), color = c)
    }
}

@Composable
fun NeutralChip(text: String) {
    Text(
        text,
        Modifier.clip(CircleShape).background(K.track).padding(horizontal = 10.dp, vertical = 5.dp),
        style = body(13.sp, FontWeight.SemiBold, 1.2f),
        color = K.ink,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(chips: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { NeutralChip(it) }
    }
}

/** Pill showing a signed change; [status] null means no change. */
@Composable
fun DeltaPill(text: String, status: Status?) {
    val c = status?.color() ?: K.muted
    Text(
        text,
        Modifier.clip(CircleShape).background(if (status == null) K.track else c.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = body(13.sp, FontWeight.Bold, 1.2f),
        color = c,
    )
}

@Composable
fun ReadinessRing(score: Int, color: Color) {
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Training readiness $score dari 100" }) {
            val w = 12.dp.toPx()
            val inset = w / 2 + 4.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(K.track, 0f, 360f, false, tl, arc, style = Stroke(w))
            drawArc(color, -90f, 360f * score.coerceIn(0, 100) / 100f, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(score.toString(), style = num(52.sp, lh = 0.9f), color = K.ink)
            Text("READINESS", style = TextStyle(fontFamily = Condensed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em), color = K.muted)
        }
    }
}

/** Personal normal range as a band, today's value as a dot. The app's identity motif. */
@Composable
fun FactorBar(value: Double, min: Double, max: Double, bandLow: Double, bandHigh: Double, status: Status, description: String) {
    val dot = status.color()
    Canvas(Modifier.fillMaxWidth().height(14.dp).semantics { contentDescription = description }) {
        fun x(v: Double) = ((v - min) / (max - min)).coerceIn(0.0, 1.0).toFloat() * size.width
        val cy = size.height / 2
        val h = 6.dp.toPx()
        val r = CornerRadius(3.dp.toPx())
        drawRoundRect(K.track, Offset(0f, cy - h / 2), Size(size.width, h), r)
        drawRoundRect(K.good.copy(alpha = 0.3f), Offset(x(bandLow), cy - h / 2), Size(x(bandHigh) - x(bandLow), h), r)
        val cx = x(value).coerceIn(7.dp.toPx(), size.width - 7.dp.toPx())
        drawCircle(K.surface, 7.dp.toPx(), Offset(cx, cy))
        drawCircle(dot, 4.dp.toPx(), Offset(cx, cy))
    }
}

/** Horizontal track with a filled span from [start] to [end] (fractions 0..1). */
@Composable
fun SpanBar(start: Float, end: Float, color: Color, height: Dp = 8.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(K.track, cornerRadius = r)
        val s = start.coerceIn(0f, 1f) * size.width
        val e = end.coerceIn(0f, 1f) * size.width
        if (e > s) drawRoundRect(color, Offset(s, 0f), Size(e - s, size.height), r)
    }
}

@Composable
fun SegmentBar(value: Double, color: Color, of: Int = 5) {
    val lit = value.roundToInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (1..of).forEach { k ->
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(3.dp)).background(if (k <= lit) color else K.track))
        }
    }
}

/** Proportional segments. Sleep stages round each segment; HR zones clip the whole bar instead. */
@Composable
fun StackedBar(
    parts: List<Pair<Double, Color>>,
    description: String,
    height: Dp = 14.dp,
    gap: Dp = 3.dp,
    segmentRadius: Dp = 4.dp,
    outerRadius: Dp = 0.dp,
) {
    Row(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(outerRadius)).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        parts.filter { it.first > 0 }.forEach { (w, c) ->
            Box(Modifier.weight(w.toFloat()).fillMaxHeight().clip(RoundedCornerShape(segmentRadius)).background(c))
        }
    }
}

@Composable
fun LineChart(
    values: List<Double?>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 100.dp,
    color: Color = K.good,
    width: Dp = 2.2.dp,
    band: ClosedFloatingPointRange<Double>? = null,
    secondDashed: List<Double?>? = null,
    baseline: Boolean = true,
) {
    val all = (values + (secondDashed ?: emptyList()) + listOfNotNull(band?.start, band?.endInclusive)).filterNotNull()
    if (all.isEmpty()) return
    val lo = all.min()
    val span = (all.max() - lo).coerceAtLeast(0.1)
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        val inset = 6.dp.toPx()
        fun y(v: Double) = size.height - inset - ((v - lo) / span).toFloat() * (size.height - 2 * inset)
        band?.let {
            drawRect(K.good.copy(alpha = 0.12f), Offset(0f, y(it.endInclusive)), Size(size.width, y(it.start) - y(it.endInclusive)))
        }
        if (baseline) drawLine(K.border, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
        fun series(vs: List<Double?>, c: Color, stroke: Stroke) {
            val step = if (vs.size > 1) size.width / (vs.size - 1) else 0f
            val path = Path()
            var started = false
            vs.forEachIndexed { i, v ->
                if (v == null) { started = false; return@forEachIndexed }
                val p = Offset(i * step, y(v))
                if (started) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
                started = true
            }
            drawPath(path, c, style = stroke)
        }
        secondDashed?.let {
            val d = 4.dp.toPx()
            series(it, K.muted, Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(d, d))))
        }
        series(values, color, Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun ChartDates(start: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(start, style = T.mono, color = K.muted)
        Spacer(Modifier.weight(1f))
        Text("Hari ini", style = T.mono, color = K.muted)
    }
}

/** Pill-shaped single choice, used for the Tren range picker. */
@Composable
fun <V> Segmented(options: List<V>, selected: V, label: (V) -> String, onSelect: (V) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(K.surface).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.weight(1f).height(40.dp).clip(CircleShape).background(if (on) K.good else Color.Transparent)
                    .selectable(on, role = Role.Tab) { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) { Text(label(o), style = body(14.sp, FontWeight.Bold, 1.2f), color = if (on) K.onAccent else K.muted) }
        }
    }
}

/** Small colored square plus name, used by chart and sleep legends. */
@Composable
fun RowScope.LegendKey(name: String, color: Color, swatch: Modifier = Modifier.size(7.dp)) {
    Box(swatch.clip(RoundedCornerShape(2.dp)).background(color).align(Alignment.CenterVertically))
    Text(name, style = body(11.sp, lh = 1.2f), color = K.muted)
}
