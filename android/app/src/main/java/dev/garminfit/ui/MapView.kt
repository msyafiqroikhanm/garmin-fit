package dev.garminfit.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.view.ViewOutlineProvider
import androidx.compose.ui.draw.clipToBounds
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.garminfit.BuildConfig
import dev.garminfit.R
import dev.garminfit.tr
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.io.File

// OpenStreetMap's own tiles: free, no key, as long as the app identifies itself, caches tiles and credits OSM.
// They are drawn light, so a color matrix turns them into a dark map in the Kinetik greens:
// grayscale, inverted, then squeezed between #101511 and #969E97 so roads read as dark lines on dim land.
private val OSM = TileSourceFactory.MAPNIK

private fun darkMatrix(): ColorMatrixColorFilter {
    val lo = floatArrayOf(16f, 21f, 17f)
    val hi = floatArrayOf(150f, 158f, 151f)
    val rows = (0..2).flatMap { c ->
        val k = (hi[c] - lo[c]) / 150f
        listOf(-k * 0.299f, -k * 0.587f, -k * 0.114f, 0f, lo[c] + k * 255f)
    }
    return ColorMatrixColorFilter(ColorMatrix((rows + listOf(0f, 0f, 0f, 1f, 0f)).toFloatArray()))
}

private var configured = false

private fun configure(ctx: Context) {
    if (configured) return
    Configuration.getInstance().apply {
        // the OSM tile policy asks every app to identify itself
        userAgentValue = BuildConfig.APPLICATION_ID
        osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
        osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
    }
    configured = true
}

/** One route, pace-colored (lat, lon, speed), or several plain routes drawn at low alpha as a heatmap. */
sealed interface MapContent {
    data class Route(val points: List<RoutePoint>) : MapContent
    data class Heat(val routes: List<List<RoutePoint>>) : MapContent
}

private fun MapContent.allPoints() = when (this) {
    is MapContent.Route -> points
    is MapContent.Heat -> routes.flatten()
}

/** Start and finish dots, drawn above the lines. */
private class EndsOverlay(val start: GeoPoint, val end: GeoPoint, val radius: Float) : Overlay() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val p = Point()
    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        fun dot(g: GeoPoint, color: Color) {
            map.projection.toPixels(g, p)
            paint.color = K.base.toArgb()
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), radius * 1.5f, paint)
            paint.color = color.toArgb()
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), radius, paint)
        }
        dot(start, K.ink)
        dot(end, K.good)
    }
}

private fun MapView.draw(content: MapContent, stroke: Float) {
    overlays.clear()
    fun line(pts: List<RoutePoint>, color: Color, width: Float) = Polyline(this).apply {
        setPoints(pts.map { GeoPoint(it[0], it[1]) })
        outlinePaint.apply {
            this.color = color.toArgb()
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        // the line is part of the picture, not a tappable object with a popup
        infoWindow = null
    }
    when (content) {
        is MapContent.Route -> {
            val pts = content.points
            val sorted = pts.map { it.getOrElse(2) { 0.0 } }.sorted()
            val lo = sorted[sorted.size / 10]
            val hi = sorted[sorted.size * 9 / 10]
            // ponytail: one Polyline per segment (~300), merge runs of similar colour if the map ever stutters
            for (i in 1 until pts.size) overlays += line(listOf(pts[i - 1], pts[i]), paceColor(pts[i].getOrElse(2) { 0.0 }, lo, hi), stroke)
            overlays += EndsOverlay(GeoPoint(pts.first()[0], pts.first()[1]), GeoPoint(pts.last()[0], pts.last()[1]), stroke * 1.2f)
        }
        is MapContent.Heat -> {
            val alpha = (1.4f / kotlin.math.sqrt(content.routes.size.toFloat())).coerceIn(0.3f, 1f)
            content.routes.filter { it.size > 1 }.forEach { overlays += line(it, K.good.copy(alpha = alpha), stroke * 0.7f) }
        }
    }
    invalidate()
}

private fun MapView.fit(content: MapContent, paddingPx: Int) {
    val pts = content.allPoints()
    if (pts.size < 2) return
    val box = BoundingBox.fromGeoPoints(pts.map { GeoPoint(it[0], it[1]) })
    // a single straight street would otherwise zoom to street-sign level
    val padded = box.increaseByScale(1.15f)
    if (width > 0 && height > 0) zoomToBoundingBox(padded, false, paddingPx)
    else addOnFirstLayoutListener { _, _, _, _, _ -> zoomToBoundingBox(padded, false, paddingPx) }
}

/**
 * Map tiles under a route. [interactive] false keeps the page scroll working (the card version);
 * true lets one finger pan and two fingers zoom (the full-screen version).
 */
@Composable
fun RouteMap(content: MapContent, interactive: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val stroke = with(density) { 4.dp.toPx() }
    val pad = with(density) { 24.dp.roundToPx() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val map = remember {
        configure(ctx)
        MapView(ctx).apply {
            setTileSource(OSM)
            isTilesScaledToDpi = true
            overlayManager.tilesOverlay.setColorFilter(darkMatrix())
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setMultiTouchControls(interactive)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            minZoomLevel = 3.0
            overlayManager.tilesOverlay.loadingBackgroundColor = K.base.toArgb()
            overlayManager.tilesOverlay.loadingLineColor = K.base.toArgb()
            setBackgroundColor(K.base.toArgb())
            // osmdroid pans by scrolling its own canvas; without an outline clip it paints past the card
            outlineProvider = ViewOutlineProvider.BOUNDS
            clipToOutline = true
            // the card map swallows touches so it never pans by accident; the overlay above handles the tap
            if (!interactive) setOnTouchListener { _, _ -> true }
        }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(obs)
            map.onPause()
            map.onDetach()
        }
    }
    Box(modifier) {
        AndroidView(
            factory = { map.apply { draw(content, stroke); fit(content, pad) } },
            modifier = Modifier.fillMaxSize().clipToBounds(),
        )
        Text(
            "© OpenStreetMap contributors",
            Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(4.dp))
                .background(K.base.copy(alpha = 0.7f)).padding(horizontal = 5.dp, vertical = 1.dp),
            style = body(9.sp, lh = 1.2f),
            color = K.muted,
        )
    }
}

/** Card-sized static map; tapping it (or the expand button) opens the full-screen map. */
@Composable
fun MapPreview(content: MapContent, description: String, onExpand: () -> Unit, height: Dp = 240.dp) {
    Box(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = description },
    ) {
        RouteMap(content, interactive = false, modifier = Modifier.fillMaxSize())
        // a transparent layer on top takes the tap; the map view itself ignores touches here
        Box(Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = tr("Open map", "Buka peta"), onClick = onExpand))
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp).size(40.dp).clip(CircleShape).background(K.base.copy(alpha = 0.8f))
                .clickable(role = Role.Button, onClickLabel = tr("Open map", "Buka peta"), onClick = onExpand),
            contentAlignment = Alignment.Center,
        ) { Glyph(R.drawable.ic_open_in_full, K.ink, 20.dp) }
    }
}

/** Full-screen map with pan and pinch zoom. */
@Composable
fun MapScreen(content: MapContent, title: String, onBack: () -> Unit, legend: (@Composable () -> Unit)? = null) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(K.base)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { BackLink(title, onBack) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            RouteMap(content, interactive = true, modifier = Modifier.fillMaxSize())
            // same corner as the expand button on the card, so the way out sits where the way in was
            Box(
                Modifier.align(Alignment.TopEnd).padding(12.dp).size(48.dp).clip(CircleShape).background(K.base.copy(alpha = 0.85f))
                    .clickable(role = Role.Button, onClickLabel = tr("Close map", "Tutup peta"), onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Glyph(R.drawable.ic_close_fullscreen, K.ink, 22.dp) }
        }
        legend?.let {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { it() }
        }
    }
}
