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

/** Android 13 per-app language sets the process default locale; Java still reports Indonesian as "in". */
fun isId(): Boolean = Locale.getDefault().language.let { it == "in" || it == "id" }

/** UI copy lives at the call site in both languages: English is the default, Indonesian when the app language is set to it. */
fun tr(en: String, id: String): String = if (isId()) id else en

// Getters, not vals: a language change recreates the activity but keeps the process, so cached formatters would keep the old locale.
private val loc: Locale get() = Locale.getDefault()
private val dayMonth get() = DateTimeFormatter.ofPattern("EEEE, d MMM", loc)
private val shortDay get() = DateTimeFormatter.ofPattern("EEE d", loc)
private val fullDate get() = DateTimeFormatter.ofPattern("d MMM yyyy", loc)
private val weekdayFmt get() = DateTimeFormatter.ofPattern("EEEE", loc)
private val weekdayShortFmt get() = DateTimeFormatter.ofPattern("EEE", loc)
private val dayMonthFmt get() = DateTimeFormatter.ofPattern("d MMM", loc)
private val hhmm get() = DateTimeFormatter.ofPattern("HH:mm", loc)

fun Double?.int(): String = this?.let { String.format(loc, "%,d", it.roundToInt()) } ?: "–"

fun Double?.one(): String = this?.let { String.format(loc, "%.1f", it) } ?: "–"

fun Double?.two(): String = this?.let { String.format(loc, "%.2f", it) } ?: "–"

fun km(m: Double?): String = m?.let { String.format(loc, "%.2f", it / 1000) } ?: "–"

/** Hour and second suffixes: h/s in English, j(am)/d(etik) in Indonesian. Minutes are "m" in both. */
private val H get() = tr("h", "j")
private val S get() = tr("s", "d")

fun duration(s: Double?): String {
    if (s == null) return "–"
    val total = s.roundToInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return when {
        h > 0 -> "${h}$H ${m}m"
        sec == 0 -> "${m}m"
        else -> "${m}m ${sec}$S"
    }
}

fun sleep(s: Double?): String {
    if (s == null) return "–"
    val m = (s / 60).roundToInt()
    return if (m < 60) "${m}m" else "%d$H %02dm".format(m / 60, m % 60)
}

fun minSec(s: Double): String = s.roundToInt().let { "%dm %02d$S".format(it / 60, it % 60) }

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
    hour < 11 -> tr("Morning", "Pagi")
    hour < 15 -> tr("Afternoon", "Siang")
    hour < 18 -> tr("Afternoon", "Sore")
    else -> tr("Evening", "Malam")
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
    val recovery = recoveryMin?.let { (it / 60).roundToInt() }?.let { tr("Recovery $it h", "Pulih $it jam") }
    return when (status) {
        Status.GOOD -> Verdict(status, tr("Go!", "Gas!"), tr("Ready for a hard session", "Siap latihan berat"),
            tr("Your body is fresh. Good day for quality work: a 40 minute tempo or 6×800 m intervals.",
                "Tubuh lagi fit. Pas buat sesi kualitas: tempo 40 menit atau interval 6×800 m."),
            listOfNotNull(tr("40–60 min", "40–60 mnt"), tr("Zone 3–4", "Zona 3–4"), recovery))
        Status.FAIR -> Verdict(status, tr("Easy", "Santai"), tr("Light to moderate training", "Latihan ringan–sedang"),
            tr("Not fully recovered yet. Easy run for 30–45 minutes in zone 2, save the intervals for tomorrow.",
                "Tubuh belum pulih penuh. Easy run 30–45 menit di zona 2, simpan interval buat besok."),
            listOfNotNull(tr("30–45 min", "30–45 mnt"), tr("Zone 2", "Zona 2"), recovery))
        Status.LOW -> Verdict(status, tr("Recover", "Pulih"), tr("Rest comes first", "Prioritaskan istirahat"),
            tr("Your body needs recovery. A 20 minute easy walk or stretching, and an early night.",
                "Tubuh butuh recovery. Jalan santai 20 menit atau stretching, dan tidur lebih awal malam ini."),
            listOfNotNull(tr("≤ 20 min", "≤ 20 mnt"), tr("Zone 1", "Zona 1"), recovery))
    }
}

/** Flag copy is built here from the key so it follows the app language; the backend's Indonesian text is only a fallback. */
fun flagText(f: Flag, t: Today): String = when (f.key) {
    "hrv_low" -> tr("7 day HRV is below your normal range. Your body needs more recovery.",
        "HRV 7 hari terakhir di bawah rentang normal. Tubuh butuh pemulihan lebih.")
    "hrv_high" -> tr("7 day HRV is above your normal range. Your training is paying off.",
        "HRV 7 hari terakhir di atas rentang normal. Adaptasi latihanmu jalan.")
    "rhr_high" -> {
        val rhr = t.latest?.rhr
        val mean = t.baseline.rhr?.mean
        if (rhr == null || mean == null) f.text
        else {
            val d = (rhr - mean).roundToInt()
            tr("RHR ${rhr.int()} bpm, $d above your 30 day average. Could be fatigue or something coming on.",
                "RHR ${rhr.int()} bpm, $d di atas rata-rata 30 hari. Bisa tanda kelelahan atau mau sakit.")
        }
    }
    "load_spike" -> tr("Training load jumped compared with the last 4 weeks.", "Beban latihan melonjak dibanding 4 minggu terakhir.")
    else -> f.text
}

fun hrvFactor(v: Double, lo: Double, hi: Double): Pair<String, Status> = when {
    v > hi -> tr("Above normal", "Di atas normal") to Status.GOOD
    v >= lo -> tr("Balanced", "Seimbang") to Status.GOOD
    lo - v <= 3 -> tr("Low end of normal", "Batas bawah normal") to Status.FAIR
    else -> tr("Below normal", "Di bawah normal") to Status.LOW
}

fun sleepFactor(score: Double): Pair<String, Status> = when {
    score >= 80 -> tr("Deep", "Nyenyak") to Status.GOOD
    score >= 70 -> tr("Enough", "Cukup") to Status.GOOD
    score >= 50 -> tr("Short", "Kurang") to Status.FAIR
    else -> tr("Poor", "Buruk") to Status.LOW
}

fun rhrFactor(v: Double, mean: Double): Pair<String, Status> = when {
    v < mean - 1 -> tr("Lower than usual", "Lebih rendah dari biasa") to Status.GOOD
    v <= mean + 4 -> tr("Normal", "Normal") to Status.GOOD
    v <= mean + 5 -> tr("Slightly high", "Sedikit tinggi") to Status.FAIR
    else -> (v - mean).roundToInt().let { tr("+$it above average", "+$it dari rata-rata") } to Status.LOW
}

fun loadFactor(ratio: Double): Pair<String, Status> = when {
    ratio > 1.3 -> tr("Spiking", "Melonjak") to Status.LOW
    ratio >= 1.2 -> tr("Near the limit", "Mendekati batas") to Status.FAIR
    ratio >= 0.8 -> tr("Balanced", "Seimbang") to Status.GOOD
    else -> tr("Below usual", "Di bawah biasanya") to Status.FAIR
}

fun loadStatus(ratio: Double): Status = when {
    ratio > 1.3 -> Status.LOW
    ratio < 0.8 -> Status.FAIR
    else -> Status.GOOD
}

fun stressFactor(v: Double): Pair<String, Status> = when {
    v <= 25 -> tr("Low", "Rendah") to Status.GOOD
    v <= 50 -> tr("Medium", "Sedang") to Status.FAIR
    else -> tr("High", "Tinggi") to Status.LOW
}

fun loadAdvice(ratio: Double): String = when {
    ratio < 0.8 -> tr("Load is down. You can build volume gradually.", "Beban turun. Boleh naikkan volume bertahap.")
    ratio <= 1.3 -> tr("Safe to add up to ±10% volume next week.", "Aman menambah volume sampai ±10% minggu depan.")
    ratio <= 1.5 -> tr("Hold volume steady this week.", "Tahan dulu volume minggu ini.")
    else -> tr("Back off the intensity, injury risk is up.", "Kurangi intensitas, risiko cedera naik.")
}

/** Garmin training effect wording; [aerobic] picks endurance over speed. */
fun teLabel(v: Double, aerobic: Boolean): String {
    val en = if (aerobic) "endurance" else "speed"
    val id = if (aerobic) "daya tahan" else "kecepatan"
    return when {
        v < 1 -> tr("No effect on $en", "Tanpa efek ke $id")
        v < 2 -> tr("Minor effect on $en", "Efek minim ke $id")
        v < 3 -> tr("Maintaining $en", "Menjaga $id")
        v < 4 -> tr("Improving $en", "Meningkatkan $id")
        v < 5 -> tr("Highly improving $en", "Sangat meningkatkan $id")
        else -> tr("Overreaching for $en", "Berlebihan untuk $id")
    }
}

val VO2_CATEGORIES: List<String> get() =
    if (isId()) listOf("Kurang", "Cukup", "Baik", "Sangat baik", "Unggul")
    else listOf("Poor", "Fair", "Good", "Excellent", "Superior")

data class Vo2Category(val index: Int, val sexLabel: String, val ageRange: String, val toNext: Double?)

// ponytail: Cooper Institute norms for age 20–29 only (the owner's band); add other age rows when a family member needs them
fun vo2Category(v: Double, male: Boolean, age: Int): Vo2Category? {
    if (age !in 20..29) return null
    val floors = if (male) listOf(38.0, 44.0, 51.0, 56.0) else listOf(32.0, 37.0, 42.0, 47.0)
    val i = floors.count { v >= it }
    return Vo2Category(i, if (male) tr("men", "pria") else tr("women", "wanita"), "20–29", floors.getOrNull(i)?.let { it - v })
}

fun trainingTone(s: String?): Status = when (s) {
    "RECOVERY", "DETRAINING", "UNPRODUCTIVE" -> Status.FAIR
    "OVERREACHING", "STRAINED" -> Status.LOW
    else -> Status.GOOD
}

fun hrvStatus(s: String?): Pair<String, Status>? = when (s) {
    "BALANCED" -> tr("Balanced", "Seimbang") to Status.GOOD
    "UNBALANCED" -> tr("Unbalanced", "Tidak seimbang") to Status.FAIR
    "LOW" -> tr("Low", "Rendah") to Status.LOW
    "POOR" -> tr("Poor", "Buruk") to Status.LOW
    null -> null
    else -> s.lowercase().replaceFirstChar { it.uppercase() } to Status.FAIR
}

fun readinessStatus(level: String?): Pair<String, Status>? = when (level) {
    "PRIME" -> tr("Prime", "Prima") to Status.GOOD
    "HIGH" -> tr("High", "Tinggi") to Status.GOOD
    "MODERATE" -> tr("Moderate", "Sedang") to Status.FAIR
    "LOW" -> tr("Low", "Rendah") to Status.LOW
    "POOR" -> tr("Poor", "Buruk") to Status.LOW
    null -> null
    else -> level to Status.FAIR
}

fun trainingStatus(s: String?): Pair<String, String>? = when (s) {
    "PRODUCTIVE" -> tr("Productive", "Produktif") to tr("Fitness is rising and the load fits.", "Kebugaran naik, beban latihan pas.")
    "MAINTAINING" -> tr("Maintaining", "Menjaga") to tr("Fitness is steady. Add load if you want to improve.", "Kebugaran stabil. Tambah beban kalau mau naik.")
    "PEAKING" -> tr("Peaking", "Puncak") to tr("Ideal shape for a race.", "Kondisi ideal untuk lomba.")
    "RECOVERY" -> tr("Recovery", "Pemulihan") to tr("Light load while your body recovers.", "Beban ringan, tubuh sedang pulih.")
    "UNPRODUCTIVE" -> tr("Unproductive", "Tidak produktif") to tr("Load is fine but fitness is dropping. Check sleep and stress.", "Beban cukup tapi kebugaran turun. Cek tidur dan stres.")
    "DETRAINING" -> tr("Detraining", "Detraining") to tr("Training load is far below your usual.", "Beban latihan jauh lebih rendah dari biasanya.")
    "OVERREACHING" -> tr("Overreaching", "Overreaching") to tr("Load is too high. Give it time to recover.", "Beban terlalu tinggi. Beri waktu pulih.")
    "STRAINED" -> tr("Strained", "Terbebani") to tr("Recovery is held back. Cut the intensity.", "Pemulihan terhambat. Kurangi intensitas.")
    null -> null
    else -> s.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() } to ""
}

fun activityType(t: String?): String = when (t) {
    "running" -> tr("Run", "Lari")
    "treadmill_running" -> tr("Treadmill run", "Lari treadmill")
    "trail_running" -> tr("Trail run", "Trail run")
    "track_running" -> tr("Track run", "Lari lintasan")
    "cycling", "road_biking" -> tr("Ride", "Sepeda")
    "indoor_cycling" -> tr("Indoor ride", "Sepeda indoor")
    "mountain_biking" -> tr("Mountain bike", "Sepeda gunung")
    "walking" -> tr("Walk", "Jalan")
    "hiking" -> tr("Hike", "Hiking")
    "lap_swimming" -> tr("Pool swim", "Renang kolam")
    "open_water_swimming" -> tr("Open water swim", "Renang perairan terbuka")
    "strength_training" -> tr("Strength", "Latihan beban")
    "yoga" -> "Yoga"
    "hiit" -> "HIIT"
    null -> tr("Activity", "Aktivitas")
    else -> t.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

fun activityIcon(t: String?): Int = when (t) {
    "running", "treadmill_running", "trail_running", "track_running" -> R.drawable.ic_directions_run
    "cycling", "road_biking", "indoor_cycling", "mountain_biking" -> R.drawable.ic_directions_bike
    "walking" -> R.drawable.ic_directions_walk
    "hiking" -> R.drawable.ic_hiking
    "lap_swimming", "open_water_swimming" -> R.drawable.ic_pool
    "strength_training", "hiit" -> R.drawable.ic_fitness_center
    else -> R.drawable.ic_timer
}

/** One running-dynamics reading placed on Garmin's five color bands (purple, blue, green, orange, red). */
data class DynamicsBand(val label: String, val status: Status?)

private val BANDS: List<DynamicsBand> get() = listOf(
    DynamicsBand("Top 5%", Status.GOOD),
    DynamicsBand("Top 30%", Status.GOOD),
    DynamicsBand(tr("Average", "Rata-rata"), null),
    DynamicsBand(tr("Below average", "Di bawah rata-rata"), Status.FAIR),
    DynamicsBand(tr("Bottom 5%", "Bawah 5%"), Status.LOW),
)

/**
 * Garmin's running dynamics color scale, which ranks a reading against other runners
 * (purple > 95th percentile, blue 70–95, green 30–69, orange 5–29, red < 5).
 * [cuts] are the four band edges from best to worst; [higherIsBetter] flips the comparison.
 */
private fun band(v: Double, cuts: List<Double>, higherIsBetter: Boolean): DynamicsBand {
    val i = cuts.indexOfFirst { if (higherIsBetter) v > it else v < it }
    return BANDS[if (i < 0) 4 else i]
}

fun cadenceBand(spm: Double) = band(spm, listOf(183.0, 173.0, 163.0, 152.0), higherIsBetter = true)
fun gctBand(ms: Double) = band(ms, listOf(208.0, 241.0, 273.0, 306.0), higherIsBetter = false)
fun oscillationBand(cm: Double) = band(cm, listOf(6.4, 8.2, 9.8, 11.6), higherIsBetter = false)
fun ratioBand(pct: Double) = band(pct, listOf(6.1, 7.5, 8.7, 10.2), higherIsBetter = false)

/** m:ss under an hour, h:mm:ss above, for race-style times. */
fun raceTime(secs: Double): String = secs.roundToInt().let {
    if (it >= 3600) "%d:%02d:%02d".format(it / 3600, it / 60 % 60, it % 60) else "%d:%02d".format(it / 60, it % 60)
}

fun splitShort(m: Number): String = when (m.toInt()) {
    1000 -> "1K"
    1609 -> tr("1 mi", "1 mil")
    5000 -> "5K"
    10000 -> "10K"
    21098 -> "HM"
    42195 -> "FM"
    else -> "${m.toInt() / 1000}K"
}

fun splitLabel(m: Double?): String = when (m?.roundToInt()) {
    1000 -> "1 km"
    1609 -> tr("1 mile", "1 mil")
    5000 -> "5 km"
    10000 -> "10 km"
    21098 -> "Half marathon"
    42195 -> "Marathon"
    else -> "${km(m)} km"
}

fun teFocus(label: String?): String? = when (label) {
    "RECOVERY" -> tr("Recovery", "Pemulihan")
    "BASE" -> tr("Aerobic base", "Dasar aerobik")
    "TEMPO" -> "Tempo"
    "THRESHOLD" -> tr("Lactate threshold", "Ambang laktat")
    "VO2MAX" -> "VO2 max"
    "ANAEROBIC_CAPACITY" -> tr("Anaerobic capacity", "Kapasitas anaerobik")
    "SPEED" -> tr("Speed", "Kecepatan")
    null, "", "UNKNOWN", "NO_BENEFIT" -> null
    else -> label.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Garmin exercise key → readable name: the specific exercise when the watch named it, else its category. */
fun exerciseLabel(category: String, sub: String?): String =
    if (category == "UNKNOWN") tr("Other", "Lainnya")
    else (sub ?: category).lowercase().split('_').joinToString(" ").replaceFirstChar { it.uppercase() }

fun muscleLabel(key: String): String = when (key) {
    "QUADS" -> tr("Quads", "Paha depan")
    "HAMSTRINGS" -> tr("Hamstrings", "Paha belakang")
    "GLUTES" -> tr("Glutes", "Bokong")
    "CHEST" -> tr("Chest", "Dada")
    "LATS" -> tr("Lats", "Punggung atas")
    "LOWER_BACK" -> tr("Lower back", "Punggung bawah")
    "SHOULDERS" -> tr("Shoulders", "Bahu")
    "TRAPS" -> tr("Traps", "Trapezius")
    "BICEPS" -> tr("Biceps", "Bisep")
    "TRICEPS" -> tr("Triceps", "Trisep")
    "FOREARM" -> tr("Forearms", "Lengan bawah")
    "ABS" -> tr("Abs", "Perut")
    "OBLIQUES" -> tr("Obliques", "Perut samping")
    "CALVES" -> tr("Calves", "Betis")
    "ADDUCTORS" -> tr("Adductors", "Paha dalam")
    "ABDUCTORS" -> tr("Abductors", "Pinggul luar")
    "HIPS" -> tr("Hip flexors", "Fleksor pinggul")
    else -> key.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

fun Throwable.friendly(): String = when (this) {
    is HttpException -> when (code()) {
        401 -> tr("You've been signed out. Sign in again.", "Kamu sudah keluar. Masuk lagi ya.")
        // the server says "no access" when the account's token was revoked; anything else is an owner-only action
        403 -> if (response()?.errorBody()?.string()?.contains("no access") == true)
            tr("Your access was removed. Add a new access token in Account.", "Akses kamu dicabut. Tambahkan token akses baru di Akun.")
        else tr("Only the owner can do this.", "Hanya pemilik yang bisa melakukan ini.")
        502 -> tr("The server couldn't fetch data from Garmin. Try again in a few minutes.",
            "Server gagal mengambil data dari Garmin. Coba lagi beberapa menit lagi.")
        else -> tr("Server error (${code()}). Try again.", "Server error (${code()}). Coba lagi.")
    }
    is IOException -> tr("Can't reach the server. Check your internet connection.", "Tidak bisa terhubung ke server. Cek koneksi internet.")
    else -> message ?: tr("Something went wrong.", "Terjadi kesalahan.")
}

/** Big muscle group key from /muscles/week. */
fun groupLabel(key: String): String = when (key) {
    "CHEST" -> tr("Chest", "Dada")
    "BACK" -> tr("Back", "Punggung")
    "SHOULDERS" -> tr("Shoulders", "Bahu")
    "ARMS" -> tr("Arms", "Lengan")
    "CORE" -> tr("Core", "Perut")
    "LEGS" -> tr("Legs", "Kaki")
    else -> key
}
