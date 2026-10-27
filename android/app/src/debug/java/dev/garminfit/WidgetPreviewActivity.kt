package dev.garminfit

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.util.SizeF
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.garminfit.widget.Widgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Debug only: hosts every widget at its picker size and saves each as a transparent PNG,
 * so `res/drawable-nodpi/preview_*.png` can be retaken with the real data.
 *
 *   adb shell appwidget grantbind --package dev.garminfit
 *   adb shell am start -n dev.garminfit/.WidgetPreviewActivity
 *   adb pull /sdcard/Android/data/dev.garminfit/files/previews
 *
 * Sizes are the old previews at 440 dpi: 4×2 is 344×223 dp, 2×2 164×223, 4×1 344×104.
 */
class WidgetPreviewActivity : ComponentActivity() {
    private val host by lazy { AppWidgetHost(this, 4711) }

    private val widgets = listOf(
        Triple("recovery", "RecoveryReceiver", SizeF(164f, 223f)),
        Triple("morning", "MorningReceiver", SizeF(344f, 223f)),
        Triple("week", "WeekReceiver", SizeF(344f, 223f)),
        Triple("trend", "TrendReceiver", SizeF(344f, 223f)),
        Triple("last", "LastActivityReceiver", SizeF(344f, 104f)),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host.deleteHost()
        val mgr = AppWidgetManager.getInstance(this)
        val dp = resources.displayMetrics.density
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val views = widgets.map { (name, receiver, size) ->
            val opts = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.width.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.width.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.height.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.height.toInt())
                putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(size))
            }
            val id = host.allocateAppWidgetId()
            check(mgr.bindAppWidgetIdIfAllowed(id, ComponentName(this, "dev.garminfit.widget.$receiver"), opts)) {
                "run: adb shell appwidget grantbind --package dev.garminfit"
            }
            val view = host.createView(this, id, mgr.getAppWidgetInfo(id)).apply { setPadding(0, 0, 0, 0) }
            column.addView(view, LinearLayout.LayoutParams((size.width * dp).toInt(), (size.height * dp).toInt()).apply { bottomMargin = (12 * dp).toInt() })
            name to view
        }
        setContentView(ScrollView(this).apply { addView(column) })
        lifecycleScope.launch {
            Widgets.updateAll(this@WidgetPreviewActivity)
            delay(8000)
            val dir = File(getExternalFilesDir(null), "previews").apply { mkdirs() }
            views.forEach { (name, view) -> save(view, File(dir, "preview_$name.png")) }
        }
    }

    private fun save(view: AppWidgetHostView, file: File) {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        // a software draw skips Glance's outline clip, so round the card here (Glance cornerRadius is 16dp)
        val r = 16 * resources.displayMetrics.density
        Canvas(bmp).apply {
            clipPath(Path().apply { addRoundRect(RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()), r, r, Path.Direction.CW) })
            view.draw(this)
        }
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    override fun onStart() { super.onStart(); host.startListening() }
    override fun onStop() { super.onStop(); host.stopListening() }
}
