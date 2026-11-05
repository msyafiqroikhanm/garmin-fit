package dev.garminfit.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garminfit.ExerciseDef
import dev.garminfit.LogExercise
import dev.garminfit.LogSummary
import dev.garminfit.Me
import dev.garminfit.MuscleLoad
import dev.garminfit.R
import dev.garminfit.Repo
import dev.garminfit.WorkoutDef
import dev.garminfit.WorkoutDraft
import dev.garminfit.WorkoutItem
import dev.garminfit.WorkoutPrefs
import dev.garminfit.WorkoutsHome
import dev.garminfit.dayMonth
import dev.garminfit.duration
import dev.garminfit.friendly
import dev.garminfit.groupLabel
import dev.garminfit.muscleLabel
import dev.garminfit.time
import dev.garminfit.tr
import dev.garminfit.weekday
import dev.garminfit.weekdayShort
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// Same numbers as the server's workout_minutes, so an edited workout shows an honest length.
private const val REP_SECONDS = 3
const val ROUND_REST_S = 60

fun workoutMinutes(items: List<WorkoutItem>, restS: Int): Int {
    val work = items.sumOf { (it.reps ?: 0) * REP_SECONDS + (it.seconds ?: 0) }
    val rounds = items.zipWithNext().count { (a, b) -> a.block == b.block && b.round == a.round + 1 }
    return ceil((work + restS * (items.size - 1).coerceAtLeast(0) + ROUND_REST_S * rounds) / 60.0).toInt()
}

/** Same rule as the server's workout_muscles: each main or finisher set adds one to its muscles. */
fun itemMuscles(items: List<WorkoutItem>, ex: Map<String, ExerciseDef>): Map<String, MuscleLoad> {
    val out = mutableMapOf<String, MuscleLoad>()
    items.filter { it.block == "main" || it.block == "finisher" }.forEach { i ->
        val e = ex[i.ex] ?: return@forEach
        e.p.forEach { out[it] = (out[it] ?: MuscleLoad()).let { m -> m.copy(primary = m.primary + 1) } }
        e.s.forEach { out[it] = (out[it] ?: MuscleLoad()).let { m -> m.copy(secondary = m.secondary + 1) } }
    }
    return out
}

fun exMuscles(e: ExerciseDef): Map<String, MuscleLoad> =
    e.s.associateWith { MuscleLoad(secondary = 1) } + e.p.associateWith { MuscleLoad(primary = 1) }

/** "x12" for reps, "0:30" for a hold. */
fun workTarget(reps: Int?, seconds: Int?): String =
    reps?.let { "x$it" } ?: seconds?.let { "%d:%02d".format(it / 60, it % 60) } ?: "–"

fun kgText(kg: Double): String = if (kg % 1.0 == 0.0) "${kg.toInt()} kg" else String.format(Locale.getDefault(), "%.1f kg", kg)

fun WorkoutDef.heading(): String = day?.let { "Day $it" } ?: title.text

@Composable
fun BodyFigure(muscles: Map<String, MuscleLoad>, height: Dp, description: String) {
    Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        drawIntoCanvas { drawBody(it.nativeCanvas, 0f, 0f, size.width, size.height, muscles, K.good, K.track) }
    }
}

private fun musclesText(m: Map<String, MuscleLoad>) =
    m.entries.filter { it.value.primary > 0 }.sortedByDescending { it.value.primary }.joinToString(", ") { muscleLabel(it.key) }

@Composable
fun WorkoutScreen(openKey: String?, onKeyOpened: () -> Unit, onPlaying: (Boolean) -> Unit, onOpenActivity: (Long) -> Unit) {
    val ctx = LocalContext.current
    val loader = rememberCachedLoad("workouts") { Repo.api(ctx).workouts() }
    val me by produceState<Me?>(null) { value = runCatching { Repo.me(ctx) }.getOrNull() }
    val owner = me?.role == "owner"
    var draft by remember { mutableStateOf<WorkoutDraft?>(null) }
    var draftChecked by remember { mutableStateOf(0) }
    LaunchedEffect(draftChecked) { draft = Repo.draft(ctx) }
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    var library by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    var logId by rememberSaveable { mutableStateOf<String?>(null) }
    var playing by rememberSaveable { mutableStateOf(false) }
    var playDef by remember { mutableStateOf<WorkoutDef?>(null) }
    var resume by remember { mutableStateOf<WorkoutDraft?>(null) }
    LaunchedEffect(openKey) {
        if (openKey != null) { preview = openKey; onKeyOpened() }
    }
    // after the process was killed mid-workout, playing comes back from saved state; the draft has the rest
    LaunchedEffect(playing) {
        if (playing && playDef == null) Repo.draft(ctx)?.let { resume = it; playDef = it.workout } ?: run { playing = false }
        onPlaying(playing)
    }
    LoadView(loader, loadingText = tr("Loading workouts", "Memuat workout")) { home ->
        val def = playDef
        when {
            playing && def != null -> WorkoutPlayer(def, home, resume) {
                playing = false; playDef = null; resume = null; preview = null
                draftChecked++
                loader.reload()
            }
            logId != null -> LogDetail(logId!!, owner, back = if (history) tr("History", "Riwayat") else "Workout", onBack = { logId = null }, onOpenActivity = onOpenActivity)
            history -> HistoryScreen(onBack = { history = false }, onOpen = { logId = it })
            library -> LibraryScreen(home, onBack = { library = false })
            preview != null -> {
                val w = home.workouts.firstOrNull { it.key == preview }
                if (w == null) LaunchedEffect(Unit) { preview = null }
                else WorkoutPreview(w, home, owner, onBack = { preview = null }) { edited -> resume = null; playDef = edited; playing = true }
            }
            else -> WorkoutHome(
                home, owner, draft, reload = loader.reload,
                onPreview = { preview = it },
                onResume = { d -> resume = d; playDef = d.workout; playing = true },
                onDraftCleared = { draftChecked++ },
                onHistory = { history = true },
                onLibrary = { library = true },
                onLog = { logId = it },
            )
        }
    }
}

@Composable
private fun WorkoutHome(
    home: WorkoutsHome,
    owner: Boolean,
    draft: WorkoutDraft?,
    reload: () -> Unit,
    onPreview: (String) -> Unit,
    onResume: (WorkoutDraft) -> Unit,
    onDraftCleared: () -> Unit,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onLog: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = home.program
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Workout")
        if (!owner) Text(tr("Only the owner logs workouts.", "Hanya pemilik yang mencatat workout."), Modifier.padding(horizontal = 4.dp), style = T.bodyS, color = K.muted)
        if (owner && draft != null) ResumeCard(draft, onResume = { onResume(draft) }) {
            scope.launch { Repo.saveDraft(ctx, null); onDraftCleared() }
        }
        val next = home.workouts.firstOrNull { it.key == "strength28:${p.nextDay}" }
        next?.let { NextHero(it, home, owner) { onPreview(it.key) } }
        ProgramCard(home, owner, reload, onPreview)
        SectionHeader(tr("Body focus", "Fokus otot"))
        home.workouts.filter { it.program == "focus" }.forEach { w ->
            Panel(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button) { onPreview(w.key) }, padding = 14.dp, radius = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(w.title.text, style = T.cardTitle, color = K.ink)
                        Text(tr("${w.minutes} min · ${w.items.distinctBy { it.ex }.size} exercises", "${w.minutes} mnt · ${w.items.distinctBy { it.ex }.size} latihan"), style = T.caption, color = K.muted)
                    }
                    Glyph(R.drawable.ic_chevron_right, K.muted)
                }
            }
        }
        SectionHeader(tr("This week", "Minggu ini"))
        WeekCard(home)
        SectionHeader(tr("History", "Riwayat"), trailing = tr("All", "Semua"), onTrailing = onHistory)
        if (home.recent.isEmpty()) Text(tr("No workouts logged yet. Finish one and it shows up here.", "Belum ada workout. Selesaikan satu dan riwayatnya muncul di sini."), Modifier.padding(horizontal = 4.dp), style = T.bodyS, color = K.muted)
        else Panel(Modifier.fillMaxWidth(), gap = 0.dp, padding = 6.dp) {
            home.recent.take(5).forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = K.track, modifier = Modifier.padding(horizontal = 12.dp))
                LogRow(l) { onLog(l.id) }
            }
        }
        Panel(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onLibrary), padding = 14.dp, radius = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardTitle(tr("Exercise library", "Pustaka latihan"), Modifier.weight(1f), icon = R.drawable.ic_fitness_center)
                Text("${home.exercises.size}", style = num(20.sp, FontWeight.Bold), color = K.muted)
                Glyph(R.drawable.ic_chevron_right, K.muted)
            }
        }
        if (owner) SettingsCard(home, reload)
    }
}

@Composable
private fun ResumeCard(d: WorkoutDraft, onResume: () -> Unit, onDiscard: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val n = d.workout.items.size
    Panel(Modifier.fillMaxWidth()) {
        Text(tr("Resume ${d.workout.heading()}", "Lanjutkan ${d.workout.heading()}"), style = T.cardTitle, color = K.ink)
        Text(tr("${d.index + 1} of $n exercises", "${d.index + 1} dari $n latihan"), style = T.bodyS, color = K.body2)
        SegmentBar(d.index.toDouble(), K.good, of = n)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentButton(tr("Resume", "Lanjutkan"), onResume, icon = R.drawable.ic_play_arrow)
            Spacer(Modifier.weight(1f))
            TextButton({ confirm = true }) { Text(tr("Discard", "Buang"), style = body(14.sp, FontWeight.SemiBold), color = K.muted) }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = K.surface,
        title = { Text(tr("Discard this workout?", "Buang workout ini?"), style = T.cardTitle, color = K.ink) },
        text = { Text(tr("What you did so far won't be saved.", "Yang sudah dikerjakan tidak akan disimpan."), style = T.bodyS, color = K.body2) },
        confirmButton = { TextButton({ confirm = false; onDiscard() }) { Text(tr("Discard", "Buang"), color = K.low) } },
        dismissButton = { TextButton({ confirm = false }) { Text(tr("Keep", "Simpan dulu"), color = K.ink) } },
    )
}

@Composable
private fun NextHero(w: WorkoutDef, home: WorkoutsHome, owner: Boolean, onOpen: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), padding = 20.dp) {
        Label("${home.program.name.text} · ${tr("week", "minggu")} ${w.week}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RollingText("Day ${w.day}", num(54.sp), K.ink)
                Text(w.title.text, style = T.cardTitle, color = K.body2)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Stat("${w.minutes}", tr("min", "mnt"), num(24.sp, FontWeight.Bold))
                    Stat("${w.items.distinctBy { it.ex }.size}", tr("exercises", "latihan"), num(24.sp, FontWeight.Bold))
                }
            }
            Box(Modifier.width(120.dp)) {
                BodyFigure(itemMuscles(w.items, home.exercises), 116.dp, tr("Muscles this day works", "Otot yang dilatih hari ini"))
            }
        }
        if (owner) AccentButton(tr("Start Day ${w.day}", "Mulai Day ${w.day}"), onOpen, Modifier.fillMaxWidth(), icon = R.drawable.ic_play_arrow)
        else Text(tr("See exercises", "Lihat latihan"), Modifier.clickable(role = Role.Button, onClick = onOpen).sizeIn(minHeight = 44.dp).padding(vertical = 12.dp), style = body(14.sp, FontWeight.SemiBold), color = K.good)
    }
}

@Composable
private fun ProgramCard(home: WorkoutsHome, owner: Boolean, reload: () -> Unit, onPreview: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = home.program
    Panel(Modifier.fillMaxWidth(), gap = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(p.name.text, Modifier.weight(1f), icon = R.drawable.ic_calendar_month)
            RollingText("${(p.doneDays.size * 100f / p.days).roundToInt()}%", num(28.sp), K.ink)
        }
        (0 until 4).forEach { w ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(tr("Week ${w + 1}", "Minggu ${w + 1}"))
                Row(Modifier.fillMaxWidth()) {
                    (1..7).forEach { i ->
                        val d = w * 7 + i
                        val done = d in p.doneDays
                        val isNext = d == p.nextDay
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (done) K.good else if (isNext) K.surface else K.track)
                                    .then(if (isNext) Modifier.border(2.dp, K.good, CircleShape) else Modifier)
                                    .clickable(role = Role.Button, onClickLabel = "Day $d") { onPreview("strength28:$d") },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (done) Glyph(R.drawable.ic_check_circle, K.onAccent, 20.dp, description = tr("Day $d done", "Day $d selesai"))
                                else Text("$d", style = num(16.sp, FontWeight.Bold), color = if (isNext) K.ink else K.muted)
                            }
                        }
                    }
                }
            }
        }
        if (p.runsCompleted > 0) NeutralChip(tr("Completed ${p.runsCompleted}×", "Selesai ${p.runsCompleted}×"))
        if (owner) {
            var diff by remember(p.difficulty) { mutableIntStateOf(p.difficulty) }
            Label(tr("Difficulty", "Tingkat"))
            Segmented(listOf(-1, 0, 1), diff, { tr(listOf("Easier", "Normal", "Harder")[it + 1], listOf("Lebih ringan", "Normal", "Lebih berat")[it + 1]) }) { pick ->
                val old = diff
                diff = pick
                scope.launch {
                    runCatching { Repo.api(ctx).saveWorkoutPrefs(WorkoutPrefs(pick, home.prefs.restS)) }
                        .onSuccess { reload() }
                        .onFailure { diff = old; Toast.makeText(ctx, it.friendly(), Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(home: WorkoutsHome) {
    val w = home.week
    val monday = LocalDate.now().let { it.minusDays(it.dayOfWeek.value - 1L) }
    Panel(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("${w.sessions}", tr("sessions", "sesi"), num(28.sp), Modifier.weight(1f))
            Stat("${w.minutes}", tr("minutes", "menit"), num(28.sp), Modifier.weight(1f))
            Stat("${w.reps}", tr("reps", "repetisi"), num(28.sp), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth()) {
            (0..6).map { monday.plusDays(it.toLong()).toString() }.forEach { d ->
                val on = d in w.dates
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(weekdayShort(d), style = body(11.sp, FontWeight.SemiBold, 1.2f), color = if (d == LocalDate.now().toString()) K.ink else K.muted)
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (on) K.good else K.track))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(home: WorkoutsHome, reload: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rest by remember(home.prefs.restS) { mutableIntStateOf(home.prefs.restS) }
    // saves once the stepper has been still for 600 ms
    LaunchedEffect(rest) {
        if (rest == home.prefs.restS) return@LaunchedEffect
        delay(600)
        runCatching { Repo.api(ctx).saveWorkoutPrefs(WorkoutPrefs(home.prefs.difficulty, rest)) }
            .onSuccess { reload() }
            .onFailure { rest = home.prefs.restS; Toast.makeText(ctx, it.friendly(), Toast.LENGTH_SHORT).show() }
    }
    var voice by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { voice = Repo.voice(ctx) }
    Panel(Modifier.fillMaxWidth(), gap = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Rest between exercises", "Istirahat antar latihan"), style = T.cardTitle, color = K.ink)
                Text(tr("60 s between rounds", "60 dtk antar ronde"), style = T.caption, color = K.muted)
            }
            Stepper("${rest} s", onMinus = { rest = (rest - 5).coerceAtLeast(10) }, onPlus = { rest = (rest + 5).coerceAtMost(60) }, minusEnabled = rest > 10, plusEnabled = rest < 60)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Glyph(R.drawable.ic_volume_up, K.muted, 20.dp)
            Text(tr("Voice coach", "Suara pelatih"), Modifier.weight(1f), style = T.cardTitle, color = K.ink)
            Switch(
                voice,
                { on -> voice = on; scope.launch { Repo.setVoice(ctx, on) } },
                colors = SwitchDefaults.colors(checkedThumbColor = K.onAccent, checkedTrackColor = K.good, uncheckedTrackColor = K.track, uncheckedBorderColor = K.border),
            )
        }
    }
}

/** − value + with 48dp targets; volt glyphs as DESIGN.md allows on steppers. */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean = true, plusEnabled: Boolean = true, valueStyle: androidx.compose.ui.text.TextStyle = num(22.sp, FontWeight.Bold)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton(R.drawable.ic_remove, tr("Less", "Kurangi"), minusEnabled, onMinus)
        Text(value, Modifier.padding(horizontal = 6.dp), style = valueStyle, color = K.ink)
        StepButton(R.drawable.ic_add, tr("More", "Tambah"), plusEnabled, onPlus)
    }
}

@Composable
private fun StepButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(K.track), contentAlignment = Alignment.Center) {
            Glyph(icon, if (enabled) K.good else K.muted.copy(alpha = 0.4f), 20.dp, description = description)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogRow(l: LogSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${weekday(l.date)} ${dayMonth(l.date)} · ${time(l.startTime)}", style = T.caption, color = K.muted)
            Text(l.title.text, style = body(15.sp, FontWeight.SemiBold), color = K.ink, maxLines = 1)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tr("${duration(l.durationS.toDouble())} · ${l.reps} reps", "${duration(l.durationS.toDouble())} · ${l.reps} repetisi"), style = T.bodyS, color = K.body2)
                if (l.source == "homeworkout") NeutralChip(tr("Imported", "Diimpor"))
                if (!l.completed) StatusChip(tr("Partial", "Sebagian"), Status.FAIR)
            }
        }
        if (l.activityId != null) Glyph(R.drawable.ic_watch, K.muted, 20.dp, description = tr("Linked to a watch activity", "Tersambung ke aktivitas jam"))
        Glyph(R.drawable.ic_chevron_right, K.muted, 20.dp)
    }
}

/** Per-exercise rows of a log: sets × average reps (or total hold time) and the heaviest weight. */
@Composable
fun LogExercises(rows: List<LogExercise>) {
    Column {
        rows.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = K.track)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.name.text, style = body(15.sp, FontWeight.SemiBold), color = K.ink, modifier = Modifier.weight(1f))
                e.kgMax?.let { Text(tr("max ${kgText(it)}", "maks ${kgText(it)}"), style = T.caption, color = K.muted) }
                Text(
                    if (e.reps > 0) "${e.sets} × ${(e.reps.toDouble() / e.sets).roundToInt()}" else workTarget(null, e.seconds),
                    style = num(18.sp, FontWeight.Bold), color = K.ink,
                )
            }
        }
    }
}

private fun blockLabel(block: String, rounds: Int) = when (block) {
    "warmup" -> tr("Warm-up", "Pemanasan")
    "main" -> tr("Main · ×$rounds rounds", "Inti · ×$rounds ronde")
    "finisher" -> "Finisher"
    else -> tr("Cool-down", "Pendinginan")
}

@Composable
private fun WorkoutPreview(def: WorkoutDef, home: WorkoutsHome, owner: Boolean, onBack: () -> Unit, onStart: (WorkoutDef) -> Unit) {
    BackHandler(onBack = onBack)
    val rounds = def.items.filter { it.block == "main" }.maxOfOrNull { it.rounds } ?: 1
    // edits work on one round of the main block and apply to every round of this session only
    var main by remember(def.key, def.items) { mutableStateOf(def.items.filter { it.block == "main" && it.round == 1 }) }
    var editing by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var swap by remember { mutableStateOf<Int?>(null) }
    val items = remember(main, def) {
        val head = def.items.takeWhile { it.block == "warmup" }
        val tail = def.items.filter { it.block == "finisher" || it.block == "cooldown" }
        head + (1..rounds).flatMap { r -> main.map { it.copy(round = r, rounds = rounds) } } + tail
    }
    val edited = def.copy(items = items, minutes = workoutMinutes(items, home.prefs.restS))
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BackLink("Workout", onBack) }
                if (owner) TextButton({ editing = !editing }) {
                    Text(if (editing) tr("Done editing", "Selesai edit") else "Edit", style = body(14.sp, FontWeight.SemiBold), color = K.good)
                }
            }
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(def.heading(), style = T.screenTitle, color = K.ink)
                Text(listOfNotNull(def.title.text.takeIf { def.day != null }, def.week?.let { tr("Week $it", "Minggu $it") }).joinToString(" · ").ifEmpty { tr("Body focus", "Fokus otot") }, style = T.body, color = K.body2)
            }
            Panel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${edited.minutes}", tr("min", "mnt"), num(28.sp), Modifier.weight(1f))
                    Stat("${items.distinctBy { it.ex }.size}", tr("exercises", "latihan"), num(28.sp), Modifier.weight(1f))
                    Stat("$rounds", tr("rounds", "ronde"), num(28.sp), Modifier.weight(1f))
                }
                val m = itemMuscles(items, home.exercises)
                BodyFigure(m, 200.dp, tr("Muscles this workout works", "Otot yang dilatih workout ini"))
                Text(musclesText(m), style = T.bodyS, color = K.body2)
            }
            val groups = listOf(
                "warmup" to items.filter { it.block == "warmup" },
                "main" to main,
                "finisher" to items.filter { it.block == "finisher" },
                "cooldown" to items.filter { it.block == "cooldown" },
            ).filter { it.second.isNotEmpty() }
            groups.forEach { (block, list) ->
                Label(blockLabel(block, rounds), modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                Panel(Modifier.fillMaxWidth(), gap = 0.dp, padding = 6.dp) {
                    list.forEachIndexed { i, it ->
                        if (i > 0) HorizontalDivider(color = K.track, modifier = Modifier.padding(horizontal = 12.dp))
                        val e = home.exercises[it.ex]
                        val kg = home.weights[it.ex]?.suggest?.takeIf { e?.equipment == "dumbbell" && it > 0 }
                        val ids = list.map { x -> x.ex }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { sheet = ids to i }
                                .padding(horizontal = 12.dp, vertical = if (editing && block == "main") 4.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e?.name?.text ?: it.ex, style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                                Text(workTarget(it.reps, it.seconds) + (kg?.let { k -> " · ${kgText(k)}" } ?: ""), style = num(16.sp, FontWeight.Bold), color = K.muted)
                            }
                            if (editing && block == "main") {
                                val step = if (it.reps != null) 1 else 5
                                Stepper(
                                    "", valueStyle = T.caption,
                                    onMinus = { main = main.toMutableList().also { l -> l[i] = it.copy(reps = it.reps?.minus(step)?.coerceAtLeast(1), seconds = it.seconds?.minus(step)?.coerceAtLeast(10)) } },
                                    onPlus = { main = main.toMutableList().also { l -> l[i] = it.copy(reps = it.reps?.plus(step), seconds = it.seconds?.plus(step)) } },
                                )
                                IconAction(R.drawable.ic_swap_horiz, tr("Swap exercise", "Ganti latihan")) { swap = i }
                                IconAction(R.drawable.ic_delete, tr("Remove", "Hapus"), enabled = main.size > 1) { main = main.filterIndexed { k, _ -> k != i } }
                            }
                        }
                    }
                }
            }
        }
        if (owner) Box(Modifier.fillMaxWidth().background(K.base).padding(horizontal = 16.dp, vertical = 12.dp)) {
            AccentButton(tr("Start", "Mulai"), { onStart(edited) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_play_arrow)
        }
    }
    sheet?.let { (ids, i) -> ExerciseSheet(ids, i, home.exercises) { sheet = null } }
    swap?.let { i ->
        val cur = main[i]
        val first = home.exercises[cur.ex]?.p?.firstOrNull()
        val options = home.exercises.filter { (k, v) -> k != cur.ex && v.p.firstOrNull() == first }.entries.sortedBy { it.value.name.text }
        Sheet({ swap = null }) {
            Text(tr("Swap exercise", "Ganti latihan"), style = T.sheetTitle, color = K.ink)
            Text(tr("Same main muscle: ${first?.let(::muscleLabel) ?: "–"}", "Otot utama sama: ${first?.let(::muscleLabel) ?: "–"}"), style = T.bodyS, color = K.muted)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (options.isEmpty()) Text(tr("Nothing else works this muscle.", "Tidak ada latihan lain untuk otot ini."), style = T.body, color = K.muted)
                options.forEach { (k, v) ->
                    Text(
                        v.name.text,
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                            val keepKind = (cur.reps != null) == (v.kind == "reps")
                            main = main.toMutableList().also { l ->
                                l[i] = cur.copy(ex = k, reps = if (v.kind == "reps") (if (keepKind) cur.reps else 10) else null, seconds = if (v.kind == "time") (if (keepKind) cur.seconds else 30) else null)
                            }
                            swap = null
                        }.padding(vertical = 14.dp, horizontal = 4.dp),
                        style = body(15.sp, FontWeight.SemiBold), color = K.ink,
                    )
                }
            }
        }
    }
}

@Composable
private fun IconAction(icon: Int, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(icon, if (enabled) K.ink else K.muted.copy(alpha = 0.4f), 20.dp, description = description)
    }
}

/** One exercise: kind, equipment, muscles on the body map and the steps; chevrons walk through [ids]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseSheet(ids: List<String>, start: Int, catalog: Map<String, ExerciseDef>, onDismiss: () -> Unit) {
    var i by remember { mutableIntStateOf(start) }
    val e = catalog[ids[i]] ?: return
    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(e.name.text, Modifier.weight(1f), style = T.sheetTitle, color = K.ink)
            if (ids.size > 1) {
                IconAction(R.drawable.ic_chevron_left, tr("Previous", "Sebelumnya"), enabled = i > 0) { i-- }
                Text("${i + 1} / ${ids.size}", style = num(16.sp, FontWeight.Bold), color = K.muted)
                IconAction(R.drawable.ic_chevron_right, tr("Next", "Berikutnya"), enabled = i < ids.size - 1) { i++ }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NeutralChip(if (e.kind == "reps") tr("Reps", "Repetisi") else tr("Timed", "Waktu"))
            NeutralChip(if (e.equipment == "dumbbell") "Dumbbell" else tr("No equipment", "Tanpa alat"))
        }
        BodyFigure(exMuscles(e), 180.dp, tr("Muscles this exercise works", "Otot yang dilatih latihan ini"))
        ChipRow((e.p + e.s).map(::muscleLabel))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            e.steps.text.forEachIndexed { k, s ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${k + 1}.", Modifier.width(20.dp), style = num(17.sp, FontWeight.Bold), color = K.muted)
                    Text(s, style = T.body, color = K.body3)
                }
            }
        }
    }
}

private val LIBRARY_GROUPS = linkedMapOf(
    "CHEST" to setOf("CHEST"),
    "CORE" to setOf("ABS", "OBLIQUES"),
    "SHOULDERS" to setOf("SHOULDERS", "TRAPS"),
    "BACK" to setOf("LATS", "LOWER_BACK"),
    "ARMS" to setOf("BICEPS", "TRICEPS", "FOREARM"),
    "LEGS" to setOf("QUADS", "HAMSTRINGS", "GLUTES", "CALVES", "ADDUCTORS", "HIPS", "ABDUCTORS"),
)

@Composable
private fun LibraryScreen(home: WorkoutsHome, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val list = home.exercises.entries
        .filter { (_, e) -> group == null || e.p.firstOrNull() in LIBRARY_GROUPS.getValue(group!!) }
        .filter { (_, e) -> query.isBlank() || e.name.text.contains(query.trim(), true) || (e.name.en ?: "").contains(query.trim(), true) }
        .sortedBy { it.value.name.text }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BackLink("Workout", onBack)
            ScreenTitle(tr("Exercise library", "Pustaka latihan"))
        }
        SearchField(query, tr("Search exercises", "Cari latihan")) { query = it }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf<String?>(null) + LIBRARY_GROUPS.keys).forEach { g ->
                val on = g == group
                Text(
                    g?.let(::groupLabel) ?: tr("All", "Semua"),
                    Modifier.clip(CircleShape).background(if (on) K.ink else K.track).selectable(on, role = Role.Tab) { group = g }
                        .sizeIn(minHeight = 40.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                    style = body(13.sp, FontWeight.SemiBold, 1.2f), color = if (on) K.onAccent else K.ink,
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp)) {
            if (list.isEmpty()) Text(tr("No exercise matches that.", "Tidak ada latihan yang cocok."), Modifier.padding(20.dp), style = T.body, color = K.muted)
            val ids = list.map { it.key }
            list.forEachIndexed { i, (_, e) ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { sheet = ids to i }.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(K.track), contentAlignment = Alignment.Center) {
                        Glyph(R.drawable.ic_fitness_center, K.muted, 20.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(e.name.text, style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                        Text(
                            listOf(if (e.equipment == "dumbbell") "Dumbbell" else tr("No equipment", "Tanpa alat"), if (e.kind == "reps") tr("Reps", "Repetisi") else tr("Timed", "Waktu"), e.p.firstOrNull()?.let(::muscleLabel)).filterNotNull().joinToString(" · "),
                            style = T.caption, color = K.muted,
                        )
                    }
                }
            }
        }
    }
    sheet?.let { (ids, i) -> ExerciseSheet(ids, i, home.exercises) { sheet = null } }
}

@Composable
private fun HistoryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val loader = rememberCachedLoad("workout_logs") { Repo.api(ctx).workoutLogs(LocalDate.now().minusDays(365).toString()) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BackLink("Workout", onBack)
            ScreenTitle(tr("History", "Riwayat"))
        }
        LoadView(loader, tr("Loading workout history", "Memuat riwayat workout"), isEmpty = { it.isEmpty() }, emptyText = tr("No workouts in the last year yet.", "Belum ada workout setahun terakhir.")) { logs ->
            val weeks = logs.groupBy { LocalDate.parse(it.date).let { d -> d.minusDays(d.dayOfWeek.value - 1L) } }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                weeks.forEach { (monday, list) ->
                    val sunday = monday.plusDays(6)
                    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${dayMonth(monday.toString())}–${dayMonth(sunday.toString())}", Modifier.weight(1f), style = T.cardTitle, color = K.ink)
                        Text(
                            tr("${list.size} sessions · ${duration(list.sumOf { it.durationS }.toDouble())} · ${list.sumOf { it.reps }} reps",
                                "${list.size} sesi · ${duration(list.sumOf { it.durationS }.toDouble())} · ${list.sumOf { it.reps }} repetisi"),
                            style = T.caption, color = K.muted,
                        )
                    }
                    Panel(Modifier.fillMaxWidth(), gap = 0.dp, padding = 6.dp) {
                        list.forEachIndexed { i, l ->
                            if (i > 0) HorizontalDivider(color = K.track, modifier = Modifier.padding(horizontal = 12.dp))
                            LogRow(l) { onOpen(l.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogDetail(id: String, owner: Boolean, back: String, onBack: () -> Unit, onOpenActivity: (Long) -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberCachedLoad("workout_log", id) { Repo.api(ctx).workoutLog(id) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) { BackLink(back, onBack) }
        LoadView(loader, tr("Loading workout", "Memuat workout")) { l ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("${weekday(l.date)} ${dayMonth(l.date)}, ${time(l.startTime)} · ${duration(l.durationS.toDouble())}")
                    Text(l.title.text, style = T.screenTitle.copy(fontSize = 28.sp), color = K.ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (l.completed) StatusChip(tr("Completed", "Selesai"), Status.GOOD) else StatusChip(tr("Partial", "Sebagian"), Status.FAIR)
                        if (l.source == "homeworkout") NeutralChip(tr("Imported", "Diimpor"))
                    }
                }
                Panel(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("${l.totalSets}", tr("sets", "set"), num(28.sp), Modifier.weight(1f))
                        Stat("${l.reps}", tr("reps", "repetisi"), num(28.sp), Modifier.weight(1f))
                        if (l.volumeKg > 0) Stat(kgText(l.volumeKg), tr("volume", "volume"), num(28.sp), Modifier.weight(1f))
                    }
                    BodyFigure(l.muscles, 240.dp, tr("Muscles this workout worked", "Otot yang dilatih workout ini"))
                    Text(musclesText(l.muscles), style = T.bodyS, color = K.body2)
                }
                Panel(Modifier.fillMaxWidth()) {
                    CardTitle(tr("Exercises", "Latihan"), icon = R.drawable.ic_fitness_center)
                    LogExercises(l.exercises)
                }
                l.activityId?.let { a ->
                    Panel(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button) { onOpenActivity(a) }, padding = 14.dp, radius = 20.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Glyph(R.drawable.ic_watch, K.muted, 20.dp)
                            Text(tr("Open watch activity", "Buka aktivitas jam"), Modifier.weight(1f), style = T.cardTitle, color = K.ink)
                            Glyph(R.drawable.ic_chevron_right, K.muted)
                        }
                    }
                }
                if (owner) TextButton({ confirm = true }) {
                    Glyph(R.drawable.ic_delete, K.low, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Delete", "Hapus"), style = body(14.sp, FontWeight.SemiBold), color = K.low)
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = K.surface,
        title = { Text(tr("Delete this workout?", "Hapus workout ini?"), style = T.cardTitle, color = K.ink) },
        text = { Text(tr("It leaves your history, the program progress and the plan score.", "Workout hilang dari riwayat, progres program dan skor plan."), style = T.bodyS, color = K.body2) },
        confirmButton = {
            TextButton({
                confirm = false
                scope.launch {
                    runCatching { Repo.api(ctx).deleteWorkoutLog(id) }
                        .onSuccess { onBack() }
                        .onFailure { Toast.makeText(ctx, it.friendly(), Toast.LENGTH_SHORT).show() }
                }
            }) { Text(tr("Delete", "Hapus"), color = K.low) }
        },
        dismissButton = { TextButton({ confirm = false }) { Text(tr("Cancel", "Batal"), color = K.ink) } },
    )
}
