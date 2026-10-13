package dev.garminfit.ui

import dev.garminfit.tr
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
import dev.garminfit.R
import androidx.compose.runtime.currentCompositeKeyHash
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
import kotlin.math.abs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.key
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import dev.garminfit.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import java.io.File

/** Clears the saved token and returns to the setup screen; provided by Home. */
val LocalLogout = staticCompositionLocalOf<(() -> Unit)?> { null }

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val data: T) : Load<T>
    /** [auth] marks a rejected token, which retrying cannot fix. */
    data class Err(val message: String, val auth: Boolean = false) : Load<Nothing>
}

class Loader<T>(val state: Load<T>, val reload: () -> Unit)

private val loadMemo = mutableMapOf<String, Any?>()

/** Last good result per cached loader, so a cold start paints real data before the network answers. */
private object DiskCache {
    // not cacheDir: Android empties that under storage pressure, which is exactly when a cold start needs it
    private fun dir(ctx: Context) = File(ctx.noBackupFilesDir, "load")
    private fun file(ctx: Context, key: String) = File(dir(ctx), key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")
    private const val MAX_FILES = 80
    fun <T> read(ctx: Context, key: String, ser: KSerializer<T>): T? =
        runCatching { json.decodeFromString(ser, file(ctx, key).readText()) }.getOrNull()
    fun <T> write(ctx: Context, key: String, ser: KSerializer<T>, value: T) {
        runCatching {
            dir(ctx).mkdirs()
            file(ctx, key).writeText(json.encodeToString(ser, value))
            // every opened activity adds a file; keep the most recently written ones
            dir(ctx).listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_FILES)?.forEach { it.delete() }
        }
    }
    fun clear(ctx: Context) = dir(ctx).deleteRecursively()
}

/** Drops every remembered result, so a new token never shows the previous account's data. */
fun clearLoadMemo(ctx: Context) {
    loadMemo.clear()
    DiskCache.clear(ctx)
}

/**
 * Shows the last result for this call site right away and refreshes behind it, so switching tabs
 * (which drops the screen's composition) does not flash a placeholder. With [cache] the last result
 * also survives a restart; a failed refresh then keeps the stale data instead of an error screen.
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, cache: Pair<String, KSerializer<T>>? = null, block: suspend () -> T): Loader<T> {
    val ctx = LocalContext.current.applicationContext
    val memoKey = "${currentCompositeKeyHash}:${keys.joinToString()}"
    val diskKey = cache?.let { listOf(it.first, *keys).joinToString("_") }
    @Suppress("UNCHECKED_CAST")
    var state by remember(*keys) { mutableStateOf<Load<T>>(if (memoKey in loadMemo) Load.Ok(loadMemo[memoKey] as T) else Load.Loading) }
    var attempt by remember(*keys) { mutableIntStateOf(0) }
    LaunchedEffect(attempt, *keys) {
        if (state !is Load.Ok) {
            val disk = cache?.let { (_, ser) -> withContext(Dispatchers.IO) { DiskCache.read(ctx, diskKey!!, ser) } }
            state = if (disk != null) Load.Ok(disk) else Load.Loading
        }
        state = runCatching { block() }.fold(
            { value ->
                loadMemo[memoKey] = value
                cache?.let { (_, ser) -> withContext(Dispatchers.IO) { DiskCache.write(ctx, diskKey!!, ser, value) } }
                Load.Ok(value)
            },
            {
                val auth = it is HttpException && it.code() == 401
                if (cache != null && state is Load.Ok && !auth) state else Load.Err(it.friendly(), auth)
            },
        )
    }
    return Loader(state) { attempt++ }
}

/** [rememberLoad] with a disk copy under [name]; T must be @Serializable. */
@Composable
inline fun <reified T> rememberCachedLoad(name: String, vararg keys: Any?, noinline block: suspend () -> T): Loader<T> =
    rememberLoad(*keys, cache = name to serializer<T>(), block = block)

@Composable
fun <T> LoadView(
    loader: Loader<T>,
    loadingText: String,
    isEmpty: (T) -> Boolean = { false },
    emptyText: String = "",
    content: @Composable (T) -> Unit,
) {
    // fades from the skeleton to the content; a refresh of data already on screen stays put (same state class)
    Crossfade(loader.state is Load.Loading, animationSpec = tween(if (LocalReduceMotion.current) 0 else 280), label = "load") { loading ->
        if (loading) LoadingSkeleton(loadingText) else LoadBody(loader, isEmpty, emptyText, content)
    }
}

@Composable
private fun <T> LoadBody(loader: Loader<T>, isEmpty: (T) -> Boolean, emptyText: String, content: @Composable (T) -> Unit) {
    when (val s = loader.state) {
        Load.Loading -> Unit
        is Load.Err -> Centered {
            Text(s.message, style = T.body, color = K.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            val logout = LocalLogout.current
            if (s.auth && logout != null) AccentButton(tr("Enter a new token", "Masukkan token baru"), logout)
            else AccentButton(tr("Try again", "Coba lagi"), loader.reload)
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
        Load.Loading -> Shimmer(Modifier.fillMaxWidth().height(96.dp), 24.dp)
        is Load.Err -> Unit // the screen-level loader already reports connection and token problems
        is Load.Ok -> if (!isEmpty(s.data)) content(s.data)
    }
}

/** Card-shaped placeholder that sweeps a soft highlight; a plain track fill when motion is reduced. */
@Composable
fun Shimmer(modifier: Modifier, radius: Dp = 24.dp) {
    val shape = RoundedCornerShape(radius)
    if (LocalReduceMotion.current) {
        Box(modifier.clip(shape).background(K.surface))
        return
    }
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "x",
    )
    Box(
        modifier.clip(shape).background(K.surface).background(
            Brush.linearGradient(
                listOf(Color.Transparent, K.track.copy(alpha = 0.7f), Color.Transparent),
                start = Offset(x * 1000f - 400f, 0f), end = Offset(x * 1000f, 400f),
            ),
        ),
    )
}

/** Placeholder cards in the rough shape of a screen. A cold server can take seconds, so the wait is counted. */
@Composable
private fun LoadingSkeleton(text: String) {
    var waited by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); waited++ } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text, style = T.body, color = K.muted)
            if (waited >= 3) Text(tr("Waking the server · ${waited}s", "Membangunkan server · ${waited} dtk"), style = T.caption, color = K.muted)
        }
        Shimmer(Modifier.fillMaxWidth().height(220.dp))
        Shimmer(Modifier.fillMaxWidth().height(150.dp))
        Shimmer(Modifier.fillMaxWidth().height(110.dp))
    }
}

/** A Material Symbols vector from res/drawable; decorative unless [description] is given. */
@Composable
fun Glyph(@DrawableRes res: Int, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier, description: String? = null) =
    Icon(painterResource(res), description, modifier.size(size), tint = tint)

/** Volt back link at the top of a sub-screen. */
@Composable
fun BackLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Glyph(R.drawable.ic_chevron_left, K.good, 20.dp)
        Text(text, style = body(14.sp, FontWeight.SemiBold), color = K.good)
    }
}

@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, @DrawableRes icon: Int? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = K.good, contentColor = K.onAccent),
    ) {
        icon?.let { Icon(painterResource(it), null, Modifier.padding(end = 6.dp).size(18.dp)) }
        Text(text, style = body(15.sp, FontWeight.Bold))
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

/** Card heading; [icon] is a muted Material Symbol that tells cards apart at a glance while scrolling. */
@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    if (icon == null) return Text(text.uppercase(), modifier, style = T.cardTitle, color = K.ink)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Glyph(icon, K.muted, 20.dp)
        Text(text.uppercase(), style = T.cardTitle, color = K.ink)
    }
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
    roll: Boolean = false,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        if (roll) RollingText(value, style, color, Modifier.alignByBaseline())
        else Text(value, Modifier.alignByBaseline(), style = style, color = color)
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

/** Pill showing a signed change; [status] null means no change. [up] adds a direction arrow. */
@Composable
fun DeltaPill(text: String, status: Status?, up: Boolean? = null) {
    val c = status?.color() ?: K.muted
    Row(
        Modifier.clip(CircleShape).background(if (status == null) K.track else c.copy(alpha = 0.14f))
            .padding(start = if (up == null) 10.dp else 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        up?.let { Glyph(if (it) R.drawable.ic_arrow_drop_up else R.drawable.ic_arrow_drop_down, c, 20.dp) }
        Text(text, style = body(13.sp, FontWeight.Bold, 1.2f), color = c)
    }
}

/**
 * Text whose number counts up from 0 the first time it shows, then eases to any new value. It stays a
 * single Text on purpose: per-digit animated layouts lose the text baseline on some phones (a Samsung
 * A33 showed the unit floating above the number). Only the first number in [text] moves; the rest is kept.
 */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val m = remember(text) { Regex("""\d+(?:[.,]\d+)?""").find(text) }
    val target = m?.value?.replace(',', '.')?.toDoubleOrNull()
    val reduce = LocalReduceMotion.current
    val anim = remember { Animatable(if (reduce || target == null) (target ?: 0.0).toFloat() else 0f) }
    LaunchedEffect(target) {
        if (target == null) return@LaunchedEffect
        if (reduce) anim.snapTo(target.toFloat()) else anim.animateTo(target.toFloat(), tween(900, easing = FastOutSlowInEasing))
    }
    val shown = if (m == null || target == null) text else {
        val decimals = m.value.substringAfter('.', m.value.substringAfter(',', "")).let { if (it == m.value) 0 else it.length }
        val sep = if (m.value.contains(',')) "," else "."
        val v = String.format(java.util.Locale.US, "%.${decimals}f", anim.value).replace(".", sep)
        text.replaceRange(m.range, v)
    }
    Text(shown, modifier, style = style, color = color, maxLines = 1)
}

@Composable
fun ReadinessRing(score: Int, color: Color) {
    val sweep = rememberReveal(score, durationMs = 900)
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = tr("Training readiness $score out of 100", "Training readiness $score dari 100") }) {
            val w = 12.dp.toPx()
            val inset = w / 2 + 4.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(K.track, 0f, 360f, false, tl, arc, style = Stroke(w))
            drawArc(color, -90f, 360f * score.coerceIn(0, 100) / 100f * sweep.value, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RollingText(score.toString(), num(52.sp, lh = 0.9f), K.ink)
            Text("READINESS", style = TextStyle(fontFamily = Condensed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.em), color = K.muted)
        }
    }
}

/** Personal normal range as a band, today's value as a dot. The app's identity motif. */
@Composable
fun FactorBar(value: Double, min: Double, max: Double, bandLow: Double, bandHigh: Double, status: Status, description: String) {
    val dot = status.color()
    val slide = rememberReveal(value, durationMs = 800)
    Canvas(Modifier.fillMaxWidth().height(14.dp).semantics { contentDescription = description }) {
        fun x(v: Double) = ((v - min) / (max - min)).coerceIn(0.0, 1.0).toFloat() * size.width
        val cy = size.height / 2
        val h = 6.dp.toPx()
        val r = CornerRadius(3.dp.toPx())
        drawRoundRect(K.track, Offset(0f, cy - h / 2), Size(size.width, h), r)
        drawRoundRect(K.good.copy(alpha = 0.3f), Offset(x(bandLow), cy - h / 2), Size(x(bandHigh) - x(bandLow), h), r)
        // the dot slides in from the band's middle to today's value
        val from = x((bandLow + bandHigh) / 2)
        val cx = (from + (x(value) - from) * slide.value).coerceIn(7.dp.toPx(), size.width - 7.dp.toPx())
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
    // ticks light up one after another
    val fill = rememberReveal(value, durationMs = 120 * of.coerceAtMost(10))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (1..of).forEach { k ->
            val on = k <= lit && fill.value * of >= k - 0.5f
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(3.dp)).background(if (on) color else K.track))
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

/**
 * Line chart, x = index. Pass [onSelect] to make it scrubbable: touch or drag sideways picks the nearest
 * point, release clears it. A vertical drag is left to the page scroll. [label] draws a pill above the guide.
 */
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
    selected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    label: ((Int) -> String)? = null,
) {
    val all = (values + (secondDashed ?: emptyList()) + listOfNotNull(band?.start, band?.endInclusive)).filterNotNull()
    if (all.isEmpty()) return
    val lo = all.min()
    val span = (all.max() - lo).coerceAtLeast(0.1)
    val reveal = rememberReveal(values)
    val haptic = LocalHapticFeedback.current
    val n = values.size
    val select by rememberUpdatedState(onSelect)
    var widthPx by remember { mutableIntStateOf(1) }
    Box(modifier.fillMaxWidth().height(height).onSizeChanged { widthPx = it.width }) {
        Canvas(
            Modifier.fillMaxSize().semantics { contentDescription = description }.then(
                if (onSelect == null) Modifier else Modifier.scrub(n) { i ->
                    if (i != null && i != selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    select?.invoke(i)
                },
            ),
        ) {
            val inset = 6.dp.toPx()
            fun y(v: Double) = size.height - inset - ((v - lo) / span).toFloat() * (size.height - 2 * inset)
            val step = if (n > 1) size.width / (n - 1) else 0f
            band?.let {
                drawRect(K.good.copy(alpha = 0.12f), Offset(0f, y(it.endInclusive)), Size(size.width, y(it.start) - y(it.endInclusive)))
            }
            if (baseline) drawLine(K.border, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
            fun series(vs: List<Double?>, c: Color, stroke: Stroke) {
                val st = if (vs.size > 1) size.width / (vs.size - 1) else 0f
                val path = Path()
                var started = false
                vs.forEachIndexed { i, v ->
                    if (v == null) { started = false; return@forEachIndexed }
                    val p = Offset(i * st, y(v))
                    if (started) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
                    started = true
                }
                drawPath(path, c, style = stroke)
            }
            // draw-in: the line is revealed left to right the first time this data shows up
            clipRect(right = size.width * reveal.value) {
                secondDashed?.let {
                    val d = 4.dp.toPx()
                    series(it, K.muted, Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(d, d))))
                }
                series(values, color, Stroke(width.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            selected?.takeIf { it in values.indices }?.let { i ->
                val x = i * step
                drawLine(K.muted.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                secondDashed?.getOrNull(i)?.let { drawCircle(K.surface, 5.dp.toPx(), Offset(x, y(it))); drawCircle(K.muted, 3.5.dp.toPx(), Offset(x, y(it))) }
                values[i]?.let { drawCircle(K.surface, 6.dp.toPx(), Offset(x, y(it))); drawCircle(color, 4.dp.toPx(), Offset(x, y(it))) }
            }
        }
        val i = selected
        if (label != null && i != null && i in values.indices) {
            val x = if (n > 1) widthPx * i / (n - 1f) else 0f
            Text(
                label(i),
                Modifier.layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0))
                    layout(p.width, p.height) { p.place((x - p.width / 2f).roundToInt().coerceIn(0, (widthPx - p.width).coerceAtLeast(0)), 0) }
                }.clip(CircleShape).background(K.track).padding(horizontal = 8.dp, vertical = 3.dp),
                style = body(12.sp, FontWeight.SemiBold, 1.2f),
                color = K.ink,
                maxLines = 1,
            )
        }
    }
}

/**
 * Touch-to-select along x for [n] evenly spaced points ([bars]: n equal slots). Shows on touch down,
 * follows a sideways drag, gives up as soon as the finger moves mostly vertically so the page can scroll.
 */
fun Modifier.scrub(n: Int, bars: Boolean = false, onSelect: (Int?) -> Unit): Modifier = pointerInput(n, bars) {
    fun index(x: Float) = when {
        bars -> (x / size.width * n).toInt().coerceIn(0, n - 1)
        n > 1 -> (x / size.width * (n - 1)).roundToInt().coerceIn(0, n - 1)
        else -> 0
    }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onSelect(index(down.position.x))
        var sideways = false
        var moved = Offset.Zero
        // finally: a gesture cut short (screen change, data refresh) must not leave a point selected
        try { while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if (!sideways && change.isConsumed) break // the page scroll took it
            moved += change.positionChange()
            if (!sideways) {
                if (abs(moved.y) > viewConfiguration.touchSlop && abs(moved.y) > abs(moved.x)) break
                if (abs(moved.x) > viewConfiguration.touchSlop) sideways = true
            }
            if (sideways) {
                change.consume()
                onSelect(index(change.position.x))
            }
        } } finally { onSelect(null) }
    }
}

/** 0 to 1 once per distinct [key]; instantly 1 when motion is reduced. Background refetches of equal data do not replay it. */
@Composable
fun rememberReveal(key: Any?, durationMs: Int = 700, delayMs: Int = 0): Animatable<Float, AnimationVector1D> {
    val reduce = LocalReduceMotion.current
    val a = remember(key) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key) { if (!reduce) a.animateTo(1f, tween(durationMs, delayMs, FastOutSlowInEasing)) }
    return a
}

@Composable
fun ChartDates(start: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(start, style = T.mono, color = K.muted)
        Spacer(Modifier.weight(1f))
        Text(tr("Today", "Hari ini"), style = T.mono, color = K.muted)
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
