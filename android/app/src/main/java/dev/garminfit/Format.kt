package dev.garminfit

import dev.garminfit.ui.Status
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ID = Locale("id", "ID")
private val dayMonth = DateTimeFormatter.ofPattern("EEEE, d MMM", ID)
private val shortDay = DateTimeFormatter.ofPattern("EEE d", ID)
private val fullDate = DateTimeFormatter.ofPattern("d MMM yyyy", ID)
private val weekdayFmt = DateTimeFormatter.ofPattern("EEEE", ID)
private val weekdayShortFmt = DateTimeFormatter.ofPattern("EEE", ID)
private val dayMonthFmt = DateTimeFormatter.ofPattern("d MMM", ID)
private val hhmm = DateTimeFormatter.ofPattern("HH:mm", ID)

fun Double?.int(): String = this?.let { String.format(ID, "%,d", it.roundToInt()) } ?: "–"

fun Double?.one(): String = this?.let { String.format(ID, "%.1f", it) } ?: "–"

fun Double?.two(): String = this?.let { String.format(ID, "%.2f", it) } ?: "–"

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
    return if (m < 60) "${m}m" else "%dj %02dm".format(m / 60, m % 60)
}

fun minSec(s: Double): String = s.roundToInt().let { "%dm %02dd".format(it / 60, it % 60) }

fun paceSec(secPerKm: Double): String = secPerKm.roundToInt().let { "%d:%02d".format(it / 60, it % 60) }

fun pace(speedMs: Double?): String {
    if (speedMs == null || speedMs <= 0) return "–"
    val secPerKm = (1000 / speedMs).roundToInt()
    return "%d:%02d".format(secPerKm / 60, secPerKm % 60)
}

fun dayLabel(iso: String): String = runCatching { LocalDate.parse(iso.take(10)).format(dayMonth) }.getOrDefault(iso)
fun shortLabel(iso: String): String = runCatching { LocalDate.parse(iso.take(10)).format(shortDay) }.getOrDefault(iso)
fun dateLabel(iso: String): String = runCatching { LocalDate.parse(iso.take(10)).format(fullDate) }.getOrDefault(iso)
fun time(start: String): String = start.drop(11).take(5)
fun weekday(iso: String): String = runCatching { LocalDate.parse(iso.take(10)).format(weekdayFmt) }.getOrDefault(iso)
fun weekdayShort(iso: String): String = runCatching { LocalDate.parse(iso.take(10)).format(weekdayShortFmt) }.getOrDefault(iso)
fun dayMonth(iso: String): String = runCatching { LocalDate.parse(iso.take(10)).format(dayMonthFmt) }.getOrDefault(iso)

fun greeting(hour: Int = LocalTime.now().hour): String = when {
    hour < 11 -> "Pagi"
    hour < 15 -> "Siang"
    hour < 18 -> "Sore"
    else -> "Malam"
}

/** Backend stores synced_at as naive UTC. */
fun syncTime(syncedAt: String?): String? = syncedAt?.let {
    runCatching {
        LocalDateTime.parse(it.take(19)).atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault()).format(hhmm)
    }.getOrNull()
}

data class Verdict(val status: Status, val word: String, val tagline: String, val advice: String, val chips: List<String>)

fun readinessVerdict(level: String?, score: Double?, recoveryMin: Double?): Verdict? {
    val status = when (level) {
        "PRIME", "HIGH" -> Status.GOOD
        "MODERATE" -> Status.FAIR
        "LOW", "POOR" -> Status.LOW
        else -> when {
            score == null -> return null
            score >= 70 -> Status.GOOD
            score >= 40 -> Status.FAIR
            else -> Status.LOW
        }
    }
    val recovery = recoveryMin?.let { "Pulih ${(it / 60).roundToInt()} jam" }
    return when (status) {
        Status.GOOD -> Verdict(status, "Gas!", "Siap latihan berat",
            "Tubuh lagi fit. Pas buat sesi kualitas: tempo 40 menit atau interval 6×800 m.",
            listOfNotNull("40–60 mnt", "Zona 3–4", recovery))
        Status.FAIR -> Verdict(status, "Santai", "Latihan ringan–sedang",
            "Tubuh belum pulih penuh. Easy run 30–45 menit di zona 2, simpan interval buat besok.",
            listOfNotNull("30–45 mnt", "Zona 2", recovery))
        Status.LOW -> Verdict(status, "Pulih", "Prioritaskan istirahat",
            "Tubuh butuh recovery. Jalan santai 20 menit atau stretching, dan tidur lebih awal malam ini.",
            listOfNotNull("≤ 20 mnt", "Zona 1", recovery))
    }
}

fun hrvFactor(v: Double, lo: Double, hi: Double): Pair<String, Status> = when {
    v > hi -> "Di atas normal" to Status.GOOD
    v >= lo -> "Seimbang" to Status.GOOD
    lo - v <= 3 -> "Batas bawah normal" to Status.FAIR
    else -> "Di bawah normal" to Status.LOW
}

fun sleepFactor(score: Double): Pair<String, Status> = when {
    score >= 80 -> "Nyenyak" to Status.GOOD
    score >= 70 -> "Cukup" to Status.GOOD
    score >= 50 -> "Kurang" to Status.FAIR
    else -> "Buruk" to Status.LOW
}

fun rhrFactor(v: Double, mean: Double): Pair<String, Status> = when {
    v < mean - 1 -> "Lebih rendah dari biasa" to Status.GOOD
    v <= mean + 4 -> "Normal" to Status.GOOD
    v <= mean + 5 -> "Sedikit tinggi" to Status.FAIR
    else -> "+${(v - mean).roundToInt()} dari rata-rata" to Status.LOW
}

fun loadFactor(ratio: Double): Pair<String, Status> = when {
    ratio > 1.3 -> "Melonjak" to Status.LOW
    ratio >= 1.2 -> "Mendekati batas" to Status.FAIR
    ratio >= 0.8 -> "Seimbang" to Status.GOOD
    else -> "Di bawah biasanya" to Status.FAIR
}

fun loadStatus(ratio: Double): Status = when {
    ratio > 1.3 -> Status.LOW
    ratio < 0.8 -> Status.FAIR
    else -> Status.GOOD
}

fun stressFactor(v: Double): Pair<String, Status> = when {
    v <= 25 -> "Rendah" to Status.GOOD
    v <= 50 -> "Sedang" to Status.FAIR
    else -> "Tinggi" to Status.LOW
}

fun loadAdvice(ratio: Double): String = when {
    ratio < 0.8 -> "Beban turun. Boleh naikkan volume bertahap."
    ratio <= 1.3 -> "Aman menambah volume sampai ±10% minggu depan."
    ratio <= 1.5 -> "Tahan dulu volume minggu ini."
    else -> "Kurangi intensitas, risiko cedera naik."
}

/** Garmin training effect wording; [target] is "daya tahan" or "kecepatan". */
fun teLabel(v: Double, target: String): String = when {
    v < 1 -> "Tanpa efek ke $target"
    v < 2 -> "Efek minim ke $target"
    v < 3 -> "Menjaga $target"
    v < 4 -> "Meningkatkan $target"
    v < 5 -> "Sangat meningkatkan $target"
    else -> "Berlebihan untuk $target"
}

val VO2_CATEGORIES = listOf("Kurang", "Cukup", "Baik", "Sangat baik", "Unggul")

data class Vo2Category(val index: Int, val sexLabel: String, val ageRange: String, val toNext: Double?)

// ponytail: Cooper Institute norms for age 20–29 only (the owner's band); add other age rows when a family member needs them
fun vo2Category(v: Double, male: Boolean, age: Int): Vo2Category? {
    if (age !in 20..29) return null
    val floors = if (male) listOf(38.0, 44.0, 51.0, 56.0) else listOf(32.0, 37.0, 42.0, 47.0)
    val i = floors.count { v >= it }
    return Vo2Category(i, if (male) "pria" else "wanita", "20–29", floors.getOrNull(i)?.let { it - v })
}

fun trainingTone(s: String?): Status = when (s) {
    "RECOVERY", "DETRAINING", "UNPRODUCTIVE" -> Status.FAIR
    "OVERREACHING", "STRAINED" -> Status.LOW
    else -> Status.GOOD
}

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
