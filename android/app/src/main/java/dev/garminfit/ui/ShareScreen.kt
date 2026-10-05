package dev.garminfit.ui

import dev.garminfit.tr
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareScreen(content: ShareContent, onBack: () -> Unit, backLabel: String = tr("‹ Back to details", "‹ Kembali ke detail")) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fonts = remember { ShareFonts(ctx) }
    val available = content.stats
    // the big-visual layout only makes sense with a route, body map or heatmap to show
    val layouts = if (content.graphic != null) Layout.entries else Layout.entries - Layout.ROUTE
    val graphicLabel = when (content.graphic) {
        is ShareGraphic.Body -> tr("Muscles", "Otot")
        is ShareGraphic.Heat -> tr("Map", "Peta")
        else -> Layout.ROUTE.label
    }
    var spec by remember(content) {
        mutableStateOf(
            ShareSpec(
                stats = available.take(content.defaultStats).map { it.key }.toSet(),
                layout = if (content.graphic is ShareGraphic.Body || content.graphic == null) Layout.GRID else Layout.BOLD,
            ),
        )
    }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    // written on every draw, read by the gesture handler; not state, so drawing does not recompose
    val box = remember { object { var value: android.graphics.RectF? = null } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            photo = withContext(Dispatchers.IO) { runCatching { decodePhoto(ctx, uri) }.getOrNull() }
            if (photo == null) Toast.makeText(ctx, tr("Couldn't open the photo", "Foto tidak bisa dibuka"), Toast.LENGTH_SHORT).show()
            else spec = spec.copy(backdrop = Backdrop.PHOTO)
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    fun export(then: (Bitmap) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { exportShare(spec, content, photo, fonts) }
            then(bmp)
            busy = false
        }
    }

    // preview stays in view while the options scroll under it, so every change is visible as it is made
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            Text(backLabel, style = body(14.sp, FontWeight.SemiBold), color = K.good)
        }
        val ratio = SHARE_W.toFloat() / spec.ratio.h
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(16.dp))
                    .background(if (spec.backdrop == Backdrop.STICKER) K.track else K.surface)
                    .border(1.dp, K.border, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            spec = spec.copy(dx = spec.dx + pan.x / size.width, dy = spec.dy + pan.y / size.height, zoom = spec.zoom * zoom)
                                .keepInside(box.value)
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { spec = spec.copy(dx = 0f, dy = 0f, zoom = 1f) }) }
                    .semantics { contentDescription = tr("Preview of the image to share. Drag and pinch to position it, double tap to reset.", "Pratinjau gambar untuk dibagikan. Geser dan cubit untuk mengatur posisi, ketuk dua kali untuk reset.") },
            ) {
                drawIntoCanvas { box.value = renderShare(it.nativeCanvas, size.width, size.height, spec, content, photo, fonts) }
            }
        }
        Text(tr("Drag and pinch to position, double tap to reset.", "Geser dan cubit untuk mengatur posisi, ketuk dua kali untuk reset."), style = T.caption, color = K.muted)
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Option(tr("Background", "Latar")) { Segmented(Backdrop.entries, spec.backdrop, { it.label }) { b -> if (b == Backdrop.PHOTO && photo == null) pick() else spec = spec.copy(backdrop = b) } }
            if (spec.backdrop == Backdrop.PHOTO) {
                Box(Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClick = pick).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    Text(if (photo == null) tr("Pick photo", "Pilih foto") else tr("Change photo", "Ganti foto"), style = body(14.sp, FontWeight.SemiBold), color = K.good)
                }
            }
            Option(tr("Style", "Gaya")) { Segmented(layouts, spec.layout, { if (it == Layout.ROUTE) graphicLabel else it.label }) { spec = spec.copy(layout = it, dx = 0f, dy = 0f, zoom = 1f) } }
            Option(tr("Size", "Ukuran")) { Segmented(Ratio.entries, spec.ratio, { it.label }) { spec = spec.copy(ratio = it, dx = 0f, dy = 0f, zoom = 1f) } }
            Option(tr("Color", "Warna")) { Segmented(Ink.entries, spec.ink, { it.label }) { spec = spec.copy(ink = it) } }
            Option("Data") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { s ->
                        Toggle(s.label, s.key in spec.stats) { on -> spec = spec.copy(stats = if (on) spec.stats + s.key else spec.stats - s.key) }
                    }
                    Toggle(tr("KINETIK logo", "Logo KINETIK"), spec.wordmark) { spec = spec.copy(wordmark = it) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton(tr("Save", "Simpan"), Modifier.weight(1f), enabled = !busy) { export { saveToGallery(ctx, it, spec.backdrop) } }
            AccentButton(tr("Share", "Bagikan"), { export { shareImage(ctx, it, spec.backdrop) } }, Modifier.weight(1f), enabled = !busy)
        }
    }
}

@Composable
private fun Option(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(title)
        content()
    }
}

@Composable
private fun Toggle(text: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.sizeIn(minHeight = 40.dp).clip(CircleShape)
            .background(if (on) K.good else K.surface)
            .toggleable(on, role = Role.Checkbox, onValueChange = onChange)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = body(14.sp, FontWeight.Bold, 1.2f), color = if (on) K.onAccent else K.muted) }
}

@Composable
private fun OutlineButton(text: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier.sizeIn(minHeight = 48.dp).clip(CircleShape).border(1.5.dp, K.good, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = body(15.sp, FontWeight.Bold), color = K.good) }
}

/** Decoded to at most 2160 px on the long side, in software memory so it can be drawn on a bitmap canvas. */
private fun decodePhoto(ctx: Context, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val long = maxOf(info.size.width, info.size.height)
        if (long > 2160) {
            val s = 2160f / long
            d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
        }
    }

private fun Backdrop.png() = this == Backdrop.STICKER

private fun shareImage(ctx: Context, bmp: Bitmap, b: Backdrop) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, if (b.png()) "kinetik.png" else "kinetik.jpg")
    file.outputStream().use { bmp.compress(if (b.png()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (b.png()) "image/png" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, tr("Share activity", "Bagikan aktivitas")))
}

private fun saveToGallery(ctx: Context, bmp: Bitmap, b: Backdrop) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "kinetik_${System.currentTimeMillis()}.${if (b.png()) "png" else "jpg"}")
        put(MediaStore.Images.Media.MIME_TYPE, if (b.png()) "image/png" else "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kinetik")
    }
    val ok = runCatching {
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        ctx.contentResolver.openOutputStream(uri)!!.use {
            bmp.compress(if (b.png()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it)
        }
    }.isSuccess
    Toast.makeText(ctx, if (ok) tr("Saved to Gallery, Kinetik folder", "Tersimpan di Galeri, folder Kinetik") else tr("Couldn't save the image", "Gagal menyimpan gambar"), Toast.LENGTH_SHORT).show()
}
