package dev.garminfit.ui

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
import dev.garminfit.Activity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareScreen(a: Activity, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fonts = remember { ShareFonts(ctx) }
    val available = remember(a) { shareStats(a) }
    // the big-visual layout only makes sense with a route or a body map to show
    val layouts = if (a.route != null || a.strength?.muscles?.isNotEmpty() == true) Layout.entries else Layout.entries - Layout.ROUTE
    var spec by remember(a) {
        mutableStateOf(ShareSpec(stats = available.take(4).map { it.key }.toSet(), layout = if (a.route != null) Layout.BOLD else Layout.GRID))
    }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            photo = withContext(Dispatchers.IO) { runCatching { decodePhoto(ctx, uri) }.getOrNull() }
            if (photo == null) Toast.makeText(ctx, "Foto tidak bisa dibuka", Toast.LENGTH_SHORT).show()
            else spec = spec.copy(backdrop = Backdrop.PHOTO)
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    fun export(then: (Bitmap) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val bmp = withContext(Dispatchers.Default) { exportShare(spec, a, photo, fonts) }
            then(bmp)
            busy = false
        }
    }

    // preview stays in view while the options scroll under it, so every change is visible as it is made
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClick = onBack).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
            Text("‹ Kembali ke detail", style = body(14.sp, FontWeight.SemiBold), color = K.good)
        }
        val ratio = SHARE_W.toFloat() / spec.ratio.h
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(16.dp))
                    .background(if (spec.backdrop == Backdrop.STICKER) K.track else K.surface)
                    .border(1.dp, K.border, RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            spec = spec.copy(
                                dx = (spec.dx + pan.x / size.width).coerceIn(-0.8f, 0.8f),
                                dy = (spec.dy + pan.y / size.height).coerceIn(-0.8f, 0.8f),
                                zoom = (spec.zoom * zoom).coerceIn(0.5f, 2f),
                            )
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { spec = spec.copy(dx = 0f, dy = 0f, zoom = 1f) }) }
                    .semantics { contentDescription = "Pratinjau gambar untuk dibagikan. Geser dan cubit untuk mengatur posisi, ketuk dua kali untuk reset." },
            ) {
                drawIntoCanvas { renderShare(it.nativeCanvas, size.width, size.height, spec, a, photo, fonts) }
            }
        }
        Text("Geser dan cubit untuk mengatur posisi, ketuk dua kali untuk reset.", style = T.caption, color = K.muted)
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Option("Latar") { Segmented(Backdrop.entries, spec.backdrop, { it.label }) { b -> if (b == Backdrop.PHOTO && photo == null) pick() else spec = spec.copy(backdrop = b) } }
            if (spec.backdrop == Backdrop.PHOTO) {
                Box(Modifier.sizeIn(minHeight = 48.dp).clickable(role = Role.Button, onClick = pick).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    Text(if (photo == null) "Pilih foto" else "Ganti foto", style = body(14.sp, FontWeight.SemiBold), color = K.good)
                }
            }
            Option("Gaya") { Segmented(layouts, spec.layout, { if (it == Layout.ROUTE && a.route == null) "Otot" else it.label }) { spec = spec.copy(layout = it) } }
            Option("Ukuran") { Segmented(Ratio.entries, spec.ratio, { it.label }) { spec = spec.copy(ratio = it) } }
            Option("Warna") { Segmented(Ink.entries, spec.ink, { it.label }) { spec = spec.copy(ink = it) } }
            Option("Data") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { s ->
                        Toggle(s.label, s.key in spec.stats) { on -> spec = spec.copy(stats = if (on) spec.stats + s.key else spec.stats - s.key) }
                    }
                    Toggle("Logo KINETIK", spec.wordmark) { spec = spec.copy(wordmark = it) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("Simpan", Modifier.weight(1f), enabled = !busy) { export { saveToGallery(ctx, it, spec.backdrop) } }
            AccentButton("Bagikan", { export { shareImage(ctx, it, spec.backdrop) } }, Modifier.weight(1f), enabled = !busy)
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
    ctx.startActivity(Intent.createChooser(send, "Bagikan aktivitas"))
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
    Toast.makeText(ctx, if (ok) "Tersimpan di Galeri, folder Kinetik" else "Gagal menyimpan gambar", Toast.LENGTH_SHORT).show()
}
