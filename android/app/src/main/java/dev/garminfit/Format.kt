package dev.garminfit

import dev.garminfit.ui.Status
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ID = Locale("id", "ID")
private val dayMonth = DateTimeFormatter.ofPattern("EEEE, d MMM", ID)
private val shortDay = DateTimeFormatter.ofPattern("EEE d", ID)
private val fullDate = DateTimeFormatter.ofPattern("d MMM yyyy", ID)

fun Double?.int(): String = this?.let { String.format(ID, "%,d", it.roundToInt()) } ?: "–"

fun Double?.one(): String = this?.let { String.format(ID, "%.1f", it) } ?: "–"

fun km(m: Double?): String = m?.let { String.format(ID, "%.2f", it / 1000) } ?: "–"

fun duration(s: Double?): String {
    if (s == null) return "–"
    val total = s.roundToInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return when {
        h > 0 -> "${h}j ${m}m"
        sec == 0 -> "${m}m"
        else -> "${m}m ${sec}d"
    }
}

fun sleep(s: Double?): String {
    if (s == null) return "–"
    val m = (s / 60).roundToInt()
    return "${m / 60}j ${m % 60}m"
}

fun pace(speedMs: Double?): String {
    if (speedMs == null || speedMs <= 0) return "–"
    val secPerKm = (1000 / speedMs).roundToInt()
    return "%d:%02d".format(secPerKm / 60, secPerKm % 60)
}

fun dayLabel(iso: String): String = runCatching { LocalDate.parse(iso.take(10)).format(dayMonth) }.getOrDefault(iso)
fun shortLabel(iso: String): String = runCatching { LocalDate.parse(iso.take(10)).format(shortDay) }.getOrDefault(iso)
fun dateLabel(iso: String): String = runCatching { LocalDate.parse(iso.take(10)).format(fullDate) }.getOrDefault(iso)
fun time(start: String): String = start.drop(11).take(5)

fun hrvStatus(s: String?): Pair<String, Status>? = when (s) {
    "BALANCED" -> "Seimbang" to Status.GOOD
    "UNBALANCED" -> "Tidak seimbang" to Status.FAIR
    "LOW" -> "Rendah" to Status.LOW
    "POOR" -> "Buruk" to Status.LOW
    null -> null
    else -> s.lowercase().replaceFirstChar { it.uppercase() } to Status.FAIR
}

fun readinessStatus(level: String?): Pair<String, Status>? = when (level) {
    "PRIME" -> "Prima" to Status.GOOD
    "HIGH" -> "Tinggi" to Status.GOOD
    "MODERATE" -> "Sedang" to Status.FAIR
    "LOW" -> "Rendah" to Status.LOW
    "POOR" -> "Buruk" to Status.LOW
    null -> null
    else -> level to Status.FAIR
}

fun trainingStatus(s: String?): Pair<String, String>? = when (s) {
    "PRODUCTIVE" -> "Produktif" to "Kebugaran naik, beban latihan pas."
    "MAINTAINING" -> "Menjaga" to "Kebugaran stabil. Tambah beban kalau mau naik."
    "PEAKING" -> "Puncak" to "Kondisi ideal untuk lomba."
    "RECOVERY" -> "Pemulihan" to "Beban ringan, tubuh sedang pulih."
    "UNPRODUCTIVE" -> "Tidak produktif" to "Beban cukup tapi kebugaran turun. Cek tidur dan stres."
    "DETRAINING" -> "Detraining" to "Beban latihan jauh lebih rendah dari biasanya."
    "OVERREACHING" -> "Overreaching" to "Beban terlalu tinggi. Beri waktu pulih."
    "STRAINED" -> "Terbebani" to "Pemulihan terhambat. Kurangi intensitas."
    null -> null
    else -> s.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() } to ""
}

fun activityType(t: String?): String = when (t) {
    "running" -> "Lari"
    "treadmill_running" -> "Lari treadmill"
    "trail_running" -> "Trail run"
    "track_running" -> "Lari lintasan"
    "cycling", "road_biking" -> "Sepeda"
    "indoor_cycling" -> "Sepeda indoor"
    "mountain_biking" -> "Sepeda gunung"
    "walking" -> "Jalan"
    "hiking" -> "Hiking"
    "lap_swimming" -> "Renang kolam"
    "open_water_swimming" -> "Renang perairan terbuka"
    "strength_training" -> "Latihan beban"
    "yoga" -> "Yoga"
    "hiit" -> "HIIT"
    null -> "Aktivitas"
    else -> t.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

fun Throwable.friendly(): String = when (this) {
    is HttpException -> when (code()) {
        401 -> "Token tidak valid atau sudah dicabut. Minta token baru ke pemilik."
        403 -> "Hanya pemilik yang bisa sync data."
        502 -> "Server gagal mengambil data dari Garmin. Coba lagi beberapa menit lagi."
        else -> "Server error (${code()}). Coba lagi."
    }
    is IOException -> "Tidak bisa terhubung ke server. Cek koneksi internet."
    else -> message ?: "Terjadi kesalahan."
}
