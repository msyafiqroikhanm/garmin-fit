package dev.garminfit

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class Daily(
    val date: String,
    val rhr: Double? = null,
    @SerialName("max_hr") val maxHr: Double? = null,
    @SerialName("stress_avg") val stressAvg: Double? = null,
    @SerialName("bb_high") val bbHigh: Double? = null,
    @SerialName("bb_low") val bbLow: Double? = null,
    @SerialName("bb_now") val bbNow: Double? = null,
    val steps: Double? = null,
    @SerialName("hrv_last_night") val hrv: Double? = null,
    @SerialName("hrv_weekly_avg") val hrvWeekly: Double? = null,
    @SerialName("hrv_status") val hrvStatus: String? = null,
    @SerialName("hrv_balanced_low") val hrvLow: Double? = null,
    @SerialName("hrv_balanced_high") val hrvHigh: Double? = null,
    @SerialName("sleep_score") val sleepScore: Double? = null,
    @SerialName("sleep_s") val sleepS: Double? = null,
    @SerialName("deep_s") val deepS: Double? = null,
    @SerialName("rem_s") val remS: Double? = null,
    @SerialName("light_s") val lightS: Double? = null,
    @SerialName("awake_s") val awakeS: Double? = null,
    val readiness: Double? = null,
    @SerialName("readiness_level") val readinessLevel: String? = null,
    @SerialName("training_status") val trainingStatus: String? = null,
    @SerialName("acute_load") val acuteLoad: Double? = null,
    @SerialName("chronic_load") val chronicLoad: Double? = null,
    val vo2max: Double? = null,
    @SerialName("recovery_time_min") val recoveryMin: Double? = null,
    @SerialName("step_goal") val stepGoal: Double? = null,
    @SerialName("synced_at") val syncedAt: String? = null,
)

@Serializable
data class Activity(
    @SerialName("_id") val id: Long,
    val date: String,
    @SerialName("start_time") val startTime: String = "",
    val type: String? = null,
    val name: String? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
    @SerialName("duration_s") val durationS: Double? = null,
    @SerialName("avg_hr") val avgHr: Double? = null,
    @SerialName("max_hr") val maxHr: Double? = null,
    @SerialName("avg_speed") val avgSpeed: Double? = null,
    val calories: Double? = null,
    @SerialName("elevation_gain") val elevationGain: Double? = null,
    @SerialName("aerobic_te") val aerobicTe: Double? = null,
    @SerialName("anaerobic_te") val anaerobicTe: Double? = null,
    @SerialName("training_load") val trainingLoad: Double? = null,
    @SerialName("hr_zones_s") val hrZonesS: List<Double?>? = null,
    val splits: List<Split>? = null,
    val route: List<List<Double>>? = null,
    val strength: Strength? = null,
    val extra: Extra? = null,
    val series: Series? = null,
    val prs: List<Int> = emptyList(),
    val similar: Similar? = null,
)

@Serializable
data class Similar(
    val count: Int,
    @SerialName("avg_speed") val avgSpeed: Double,
    @SerialName("avg_hr") val avgHr: Double? = null,
    val efficiency: Double? = null,
    @SerialName("this_efficiency") val thisEfficiency: Double? = null,
)

@Serializable
data class Totals(
    val count: Int = 0,
    @SerialName("distance_m") val distanceM: Double = 0.0,
    @SerialName("duration_s") val durationS: Double = 0.0,
    @SerialName("active_days") val activeDays: Int = 0,
    @SerialName("by_kind") val byKind: Map<String, Int> = emptyMap(),
)

@Serializable
data class Recap(
    val period: String,
    val start: String,
    val end: String,
    val count: Int = 0,
    @SerialName("distance_m") val distanceM: Double = 0.0,
    @SerialName("duration_s") val durationS: Double = 0.0,
    @SerialName("active_days") val activeDays: Int = 0,
    @SerialName("by_kind") val byKind: Map<String, Int> = emptyMap(),
    val previous: Totals = Totals(),
    @SerialName("longest_run") val longestRun: LongestRun? = null,
    @SerialName("streak_weeks") val streakWeeks: Int = 0,
    val buckets: List<Bucket> = emptyList(),
    val prs: List<RecapPr> = emptyList(),
)

@Serializable
data class LongestRun(
    @SerialName("_id") val id: Long,
    val date: String,
    val name: String? = null,
    @SerialName("distance_m") val distanceM: Double = 0.0,
    @SerialName("duration_s") val durationS: Double = 0.0,
)

@Serializable
data class Bucket(val key: String, @SerialName("distance_m") val distanceM: Double = 0.0, @SerialName("duration_s") val durationS: Double = 0.0, val count: Int = 0)

@Serializable
data class RecapPr(@SerialName("activity_id") val activityId: Long, val date: String, val name: String? = null, val distances: List<Int> = emptyList())

@Serializable
data class Prediction(
    val date: String,
    @SerialName("5k") val k5: Double? = null,
    @SerialName("10k") val k10: Double? = null,
    val half: Double? = null,
    val marathon: Double? = null,
)

@Serializable
data class WeekMuscles(
    val sessions: Int = 0,
    val sets: Int = 0,
    val muscles: Map<String, MuscleLoad> = emptyMap(),
    val untrained: List<String> = emptyList(),
)

@Serializable
data class DynamicsWeek(
    val week: String,
    val runs: Int = 0,
    val cadence: Double? = null,
    @SerialName("gct_ms") val gctMs: Double? = null,
    @SerialName("vr_pct") val vrPct: Double? = null,
    @SerialName("vo_cm") val voCm: Double? = null,
    val power: Double? = null,
)

@Serializable
data class Record(
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("duration_s") val durationS: Double,
    @SerialName("activity_id") val activityId: Long,
    val date: String,
    val name: String? = null,
    @SerialName("times_broken") val timesBroken: Int = 0,
)

/** ~200 bucket averages over the session; speed in cm/s, elev in dm, nulls where the watch stopped. */
@Serializable
data class Series(
    val t: List<Int> = emptyList(),
    val hr: List<Int?>? = null,
    val speed: List<Int?>? = null,
    val cadence: List<Int?>? = null,
    val power: List<Int?>? = null,
    val elev: List<Int?>? = null,
)

@Serializable
data class Extra(
    @SerialName("elapsed_s") val elapsedS: Double? = null,
    @SerialName("moving_s") val movingS: Double? = null,
    @SerialName("max_speed") val maxSpeed: Double? = null,
    @SerialName("gap_speed") val gapSpeed: Double? = null,
    val cadence: Double? = null,
    @SerialName("cadence_max") val cadenceMax: Double? = null,
    @SerialName("stride_cm") val strideCm: Double? = null,
    @SerialName("gct_ms") val gctMs: Double? = null,
    @SerialName("vo_cm") val voCm: Double? = null,
    @SerialName("vr_pct") val vrPct: Double? = null,
    val power: Double? = null,
    @SerialName("power_max") val powerMax: Double? = null,
    @SerialName("elevation_loss") val elevationLoss: Double? = null,
    val steps: Double? = null,
    @SerialName("bb_delta") val bbDelta: Double? = null,
    @SerialName("water_ml") val waterMl: Double? = null,
    @SerialName("moderate_min") val moderateMin: Double? = null,
    @SerialName("vigorous_min") val vigorousMin: Double? = null,
    @SerialName("te_label") val teLabel: String? = null,
    val vo2max: Double? = null,
    @SerialName("best_splits") val bestSplits: List<Split> = emptyList(),
)

@Serializable
data class Strength(
    val exercises: List<ExerciseRow> = emptyList(),
    val muscles: Map<String, MuscleLoad> = emptyMap(),
    @SerialName("total_sets") val totalSets: Int? = null,
    @SerialName("total_reps") val totalReps: Int? = null,
)

@Serializable
data class ExerciseRow(
    val category: String,
    val sub: String? = null,
    val sets: Int = 0,
    val reps: Int = 0,
    @SerialName("duration_s") val durationS: Double = 0.0,
)

@Serializable
data class MuscleLoad(val primary: Int = 0, val secondary: Int = 0)

@Serializable
data class Split(
    @SerialName("distance_m") val distanceM: Double? = null,
    @SerialName("duration_s") val durationS: Double? = null,
    @SerialName("avg_hr") val avgHr: Double? = null,
)

@Serializable
data class Flag(val key: String, val level: String, val text: String)

@Serializable
data class Baseline(
    val hrv: HrvBase? = null,
    val rhr: RhrBase? = null,
    val load: LoadBase? = null,
)

@Serializable
data class HrvBase(val mean: Double, val sd: Double, @SerialName("recent_avg") val recentAvg: Double)

@Serializable
data class RhrBase(val mean: Double)

@Serializable
data class LoadBase(val acute: Double, val chronic: Double, val ratio: Double)

@Serializable
data class Spark(
    val date: List<String?> = emptyList(),
    @SerialName("hrv_last_night") val hrv: List<Double?> = emptyList(),
    val rhr: List<Double?> = emptyList(),
    @SerialName("sleep_score") val sleepScore: List<Double?> = emptyList(),
    @SerialName("bb_high") val bbHigh: List<Double?> = emptyList(),
    @SerialName("stress_avg") val stress: List<Double?> = emptyList(),
)

@Serializable
data class Week(
    val count: Int = 0,
    @SerialName("distance_m") val distanceM: Double = 0.0,
    @SerialName("duration_s") val durationS: Double = 0.0,
    val load: Double = 0.0,
    @SerialName("active_days") val activeDays: Int = 0,
    val dates: List<String> = emptyList(),
    @SerialName("daily_load") val dailyLoad: List<DayLoad> = emptyList(),
)

@Serializable
data class DayLoad(val date: String, val load: Double = 0.0)

@Serializable
data class Section(val title: String, val body: String)

@Serializable
data class DateRange(val from: String, val to: String)

@Serializable
data class SummaryText(val headline: String, val sections: List<Section>)

@Serializable
data class Summary(
    val period: String,
    val range: DateRange,
    val headline: String,
    val sections: List<Section>,
    val en: SummaryText? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
)

@Serializable
data class Today(
    val role: String,
    val name: String? = null,
    val latest: Daily? = null,
    val baseline: Baseline = Baseline(),
    val flags: List<Flag> = emptyList(),
    val spark: Spark = Spark(),
    val week: Week = Week(),
    @SerialName("last_activity") val lastActivity: Activity? = null,
    val summary: Summary? = null,
    val plan: PlanToday? = null,
)

/** Text Claude writes in both languages: Indonesian in [id], English in [en] when given. */
@Serializable
data class Bi(val id: String, val en: String? = null) {
    val text: String get() = if (isId()) id else en ?: id
}

@Serializable
data class PlanTarget(@SerialName("distance_m") val distanceM: Double? = null, @SerialName("duration_s") val durationS: Double? = null)

@Serializable
data class PlanPace(@SerialName("min_s_km") val minSKm: Double, @SerialName("max_s_km") val maxSKm: Double)

@Serializable
data class PlanSession(
    val date: String,
    val kind: String,
    val type: String,
    val title: Bi,
    val target: PlanTarget? = null,
    val pace: PlanPace? = null,
    @SerialName("hr_zone") val hrZone: Int? = null,
    val notes: Bi? = null,
    /** done | partial | missed | today | upcoming | rest */
    val status: String = "upcoming",
    val pct: Double? = null,
    @SerialName("activity_ids") val activityIds: List<Long> = emptyList(),
    val done: PlanTarget? = null,
)

@Serializable
data class PlanWeek(val start: String, val planned: Int = 0, val done: Int = 0, val pct: Double? = null)

@Serializable
data class PlanMeta(val name: String, val goal: Bi, val start: String, val end: String, @SerialName("race_date") val raceDate: String? = null)

@Serializable
data class PlanExtra(@SerialName("_id") val id: Long, val date: String, val type: String? = null, val name: String? = null, @SerialName("distance_m") val distanceM: Double? = null)

@Serializable
data class PlanView(
    val plan: PlanMeta? = null,
    val sessions: List<PlanSession> = emptyList(),
    val weeks: List<PlanWeek> = emptyList(),
    val overall: Double? = null,
    val extras: List<PlanExtra> = emptyList(),
)

@Serializable
data class PlanToday(val name: String, val session: PlanSession? = null, val week: PlanWeek? = null, val overall: Double? = null)

@Serializable
data class Me(val name: String, val role: String, val profile: Profile? = null)

@Serializable
data class Profile(
    val gender: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("hr_zone_floors") val hrZoneFloors: List<Double>? = null,
    @SerialName("max_hr") val maxHr: Double? = null,
)

@Serializable
data class SyncResult(val ok: Boolean, val errors: List<String> = emptyList())

interface ApiService {
    @GET("me") suspend fun me(): Me
    @GET("today") suspend fun today(): Today
    @GET("daily") suspend fun daily(@Query("from") from: String, @Query("to") to: String? = null): List<Daily>
    @GET("activities") suspend fun activities(@Query("from") from: String): List<Activity>
    @GET("activities/{id}") suspend fun activity(@Path("id") id: Long): Activity
    @POST("sync") suspend fun sync(): SyncResult
    @GET("records") suspend fun records(): List<Record>
    @GET("race-predictions") suspend fun predictions(@Query("days") days: Int): List<Prediction>
    @GET("muscles/week") suspend fun weekMuscles(): WeekMuscles
    @GET("trends/running") suspend fun runningTrends(@Query("weeks") weeks: Int): List<DynamicsWeek>
    @GET("recap") suspend fun recap(@Query("period") period: String, @Query("key") key: String): Recap
    @GET("plan") suspend fun plan(): PlanView
    @GET("routes") suspend fun routes(@Query("from") from: String, @Query("to") to: String): List<List<List<Double>>>
}

val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

private val Context.store by preferencesDataStore("garmin_fit")
private val TOKEN = stringPreferencesKey("token")
private val TODAY_CACHE = stringPreferencesKey("today_cache")

object Repo {
    @Volatile private var cached: Pair<String, ApiService>? = null

    suspend fun token(ctx: Context): String? = ctx.store.data.first()[TOKEN]

    suspend fun saveToken(ctx: Context, token: String?) {
        ctx.store.edit { if (token == null) it.remove(TOKEN) else it[TOKEN] = token }
    }

    fun api(token: String): ApiService {
        cached?.let { (t, api) -> if (t == token) return api }
        val client = OkHttpClient.Builder()
            .readTimeout(70, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
            }
            .build()
        val api = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
        cached = token to api
        return api
    }

    suspend fun api(ctx: Context): ApiService = api(token(ctx) ?: error(tr("No token yet", "Belum ada token")))

    @Volatile private var me: Pair<String, Me>? = null

    // ponytail: in-memory only; profile (zones, sex, age) changes rarely and /me is cheap on a cold start
    suspend fun me(ctx: Context): Me {
        val t = token(ctx) ?: error(tr("No token yet", "Belum ada token"))
        me?.let { (k, m) -> if (k == t) return m }
        return api(t).me().also { me = t to it }
    }

    suspend fun fetchToday(ctx: Context): Today {
        val today = api(ctx).today()
        ctx.store.edit { it[TODAY_CACHE] = json.encodeToString(Today.serializer(), today) }
        return today
    }

    suspend fun cachedToday(ctx: Context): Today? =
        ctx.store.data.first()[TODAY_CACHE]?.let { runCatching { json.decodeFromString(Today.serializer(), it) }.getOrNull() }
}
