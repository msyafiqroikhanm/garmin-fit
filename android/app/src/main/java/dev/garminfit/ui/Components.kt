package dev.garminfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.garminfit.friendly
import dev.garminfit.one
import kotlin.math.max
import kotlin.math.min

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

class Loader<T>(val state: Load<T>, val reload: () -> Unit)

@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    var state by remember(*keys) { mutableStateOf<Load<T>>(Load.Loading) }
    var attempt by remember(*keys) { mutableIntStateOf(0) }
    LaunchedEffect(attempt, *keys) {
        if (state !is Load.Ok) state = Load.Loading
        state = runCatching { block() }.fold({ Load.Ok(it) }, { Load.Err(it.friendly()) })
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
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(loadingText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is Load.Err -> Centered {
            Text(s.message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = loader.reload) { Text("Coba lagi") }
        }
        is Load.Ok -> if (isEmpty(s.data)) Centered {
            Text(emptyText, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else content(s.data)
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun StatusText(label: String, status: Status) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(status.color(), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = status.color())
    }
}

@Composable
fun BigValue(value: String, unit: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = style)
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(unit, Modifier.padding(bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    status: Pair<String, Status>? = null,
    caption: String? = null,
) {
    Panel(modifier) {
        Label(label)
        Spacer(Modifier.height(6.dp))
        BigValue(value, unit)
        status?.let { Spacer(Modifier.height(4.dp)); StatusText(it.first, it.second) }
        caption?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Personal normal range drawn as a band, with the current value as a dot. */
@Composable
fun BaselineBar(value: Double, low: Double, high: Double, modifier: Modifier = Modifier) {
    val span = (high - low).coerceAtLeast(1.0)
    val min = min(value, low) - span * 0.5
    val max = max(value, high) + span * 0.5
    val inside = value in low..high
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val band = LocalStatus.current.band
    val dot = if (inside) Status.GOOD.color() else if (value < low) Status.LOW.color() else Status.FAIR.color()
    val ring = MaterialTheme.colorScheme.surfaceContainer
    Canvas(modifier.fillMaxWidth().height(14.dp).semantics {
        contentDescription = "Nilai ${value.toInt()}, rentang normal ${low.toInt()} sampai ${high.toInt()}"
    }) {
        fun x(v: Double) = ((v - min) / (max - min)).toFloat() * size.width
        val cy = size.height / 2
        val h = 6.dp.toPx()
        drawRoundRect(track, Offset(0f, cy - h / 2), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(band, Offset(x(low), cy - h / 2), Size(x(high) - x(low), h), CornerRadius(h / 2))
        drawCircle(ring, 7.dp.toPx(), Offset(x(value), cy))
        drawCircle(dot, 5.dp.toPx(), Offset(x(value), cy))
    }
}

@Composable
fun LineChart(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    second: List<Double?>? = null,
    secondColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    band: ClosedFloatingPointRange<Double>? = null,
    description: String,
) {
    val all = (values + (second ?: emptyList()) + listOfNotNull(band?.start, band?.endInclusive)).filterNotNull()
    if (all.isEmpty()) return
    val lo = all.min()
    val hi = all.max()
    val span = (hi - lo).coerceAtLeast(0.1)
    val axisFmt: (Double) -> String = { if (span < 10) it.one() else it.toInt().toString() }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bandColor = LocalStatus.current.band
    val axisText = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.fillMaxWidth()) {
        Row {
            // Labels sit at the same inset as the plotted min/max so they line up with the line.
            Column(Modifier.height(140.dp).padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(axisFmt(hi), style = MaterialTheme.typography.labelSmall, color = axisText)
                Text(axisFmt(lo), style = MaterialTheme.typography.labelSmall, color = axisText)
            }
            Canvas(Modifier.weight(1f).height(140.dp).semantics { contentDescription = description }) {
                val inset = 8.dp.toPx()
                fun y(v: Double) = size.height - inset - ((v - lo) / span).toFloat() * (size.height - 2 * inset)
                band?.let {
                    drawRect(bandColor, Offset(0f, y(it.endInclusive)), Size(size.width, y(it.start) - y(it.endInclusive)))
                }
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                fun series(vs: List<Double?>, c: Color, w: Float) {
                    val step = if (vs.size > 1) size.width / (vs.size - 1) else 0f
                    val path = Path()
                    var started = false
                    vs.forEachIndexed { i, v ->
                        if (v == null) { started = false; return@forEachIndexed }
                        val p = Offset(i * step, y(v))
                        if (started) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
                        started = true
                    }
                    drawPath(path, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    vs.withIndex().lastOrNull { it.value != null }?.let { (i, v) ->
                        drawCircle(c, w * 1.6f, Offset(i * step, y(v!!)))
                    }
                }
                second?.let { series(it, secondColor, 1.5.dp.toPx()) }
                series(values, color, 2.dp.toPx())
            }
        }
    }
}
