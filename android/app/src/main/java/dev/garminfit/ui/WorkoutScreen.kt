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
import androidx.compose.foundation.layout.wrapContentHeight
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
import dev.garminfit.PlanSession
import dev.garminfit.ExerciseVisit
import dev.garminfit.LinkIn
import androidx.compose.ui.text.withStyle
import dev.garminfit.Routine
import dev.garminfit.RoutineItem
import androidx.compose.runtime.collectAsState
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

fun WorkoutDef.heading(): String = day?.let { tr("Day $it", "Hari $it") } ?: title.text

/** What leads Workout from the plan: strength sessions finished today, and the next one to do within a week. */
data class StrengthLead(val doneToday: List<PlanSession>, val next: PlanSession?)

internal fun strengthLead(sessions: List<PlanSession>, today: LocalDate): StrengthLead {
    val s = sessions.filter { it.kind == "strength" }
    val t = today.toString()
    val week = today.plusDays(7).toString()
    return StrengthLead(
        s.filter { it.date == t && (it.status == "done" || it.status == "partial") },
        s.firstOrNull { it.date == t && it.status == "today" } ?: s.firstOrNull { it.date > t && it.date <= week && it.status == "upcoming" },
    )
}

@Composable
fun BodyFigure(muscles: Map<String, MuscleLoad>, height: Dp, description: String) {
    Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        drawIntoCanvas { drawBody(it.nativeCanvas, 0f, 0f, size.width, size.height, muscles, K.good, K.track) }
    }
}

private fun musclesText(m: Map<String, MuscleLoad>) =
    m.entries.filter { it.value.primary > 0 }.sortedByDescending { it.value.primary }.joinToString(", ") { muscleLabel(it.key) }

@Composable
fun WorkoutScreen(
    openKey: String?, onKeyOpened: () -> Unit, openLog: String?, onLogClosed: () -> Unit,
    onPlaying: (Boolean) -> Unit, onOpenActivity: (Long) -> Unit, onOpenPlanSession: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // the lead reads the cached plan, so refresh it alongside
    val loader = rememberCachedLoad("workouts") { Repo.api(ctx).workouts().also { scope.launch { runCatching { Repo.fetchPlan(ctx) } } } }
    // the role rides along with /workouts (disk cached); older servers fall back to the saved /me or /today role
    val savedRole by produceState<String?>(null) { value = Repo.role(ctx) }
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
    var startAfterDraft by remember { mutableStateOf<WorkoutDef?>(null) }
    LaunchedEffect(openKey) {
        if (openKey != null) { preview = openKey; onKeyOpened() }
    }
    LaunchedEffect(openLog) { if (openLog != null) logId = openLog }
    // after the process was killed mid-workout, playing comes back from saved state; the draft has the rest
    LaunchedEffect(playing) {
        if (playing && playDef == null) Repo.draft(ctx)?.let { resume = it; playDef = it.workout } ?: run { playing = false }
        onPlaying(playing)
    }
    startAfterDraft?.let { next ->
        val d = draft
        val go = { startAfterDraft = null; draftChecked++; resume = null; playDef = next; playing = true }
        if (d == null) go()
        else AlertDialog(
            onDismissRequest = { startAfterDraft = null },
            containerColor = K.surface,
            title = { Text(tr("You have an unfinished ${d.workout.heading()}", "Masih ada ${d.workout.heading()} yang belum selesai"), style = T.cardTitle, color = K.ink) },
            text = { Text(tr("Starting a new workout replaces it. Save what you did as partial, or discard it.", "Mulai workout baru akan menggantikannya. Simpan yang sudah dikerjakan sebagai sebagian, atau buang."), style = T.bodyS, color = K.body2) },
            confirmButton = {
                TextButton({
                    scope.launch {
                        Repo.queueLog(ctx, draftLog(d, completed = false))
                        Repo.saveDraft(ctx, null)
                        runCatching { Repo.flushWorkoutLogs(ctx) }
                        go()
                    }
                }) { Text(tr("Save as partial", "Simpan sebagian"), color = K.ink) }
            },
            dismissButton = {
                Row {
                    TextButton({ scope.launch { Repo.saveDraft(ctx, null); go() } }) { Text(tr("Discard", "Buang"), color = K.low) }
                    TextButton({ startAfterDraft = null }) { Text(tr("Cancel", "Batal"), color = K.muted) }
                }
            },
        )
    }
    LoadView(loader, loadingText = tr("Loading workouts", "Memuat workout")) { home ->
        val role = home.role ?: savedRole
        val owner = role == "owner"
        val def = playDef
        when {
            playing && def != null -> WorkoutPlayer(def, home, resume) {
                playing = false; playDef = null; resume = null; preview = null
                draftChecked++
                loader.reload()
            }
            logId != null -> LogDetail(
                logId!!, owner, home,
                onDoAgain = { key -> logId = null; preview = key },
                onChanged = loader.reload,
                back = if (openLog != null) tr("Training plan", "Training plan") else if (history) tr("History", "Riwayat") else "Workout",
                onBack = { logId = null; if (openLog != null) onLogClosed() }, onOpenActivity = onOpenActivity,
            )
            history -> HistoryScreen(onBack = { history = false }, onOpen = { logId = it })
            library -> LibraryScreen(home, onBack = { library = false })
            preview != null -> {
                val w = home.workouts.firstOrNull { it.key == preview }
                if (w == null) LaunchedEffect(Unit) { preview = null }
                else WorkoutPreview(w, home, owner, onBack = { preview = null }, onSaved = loader.reload) { edited ->
                    // an unfinished workout would be overwritten by the new one's draft: ask what to do with it first
                    if (draft != null) startAfterDraft = edited else { resume = null; playDef = edited; playing = true }
                }
            }
            else -> WorkoutHome(
                home, owner, viewer = role != null && !owner, stale = loader, draft = draft, reload = loader.reload,
                onOpenActivity = onOpenActivity, onOpenPlanSession = onOpenPlanSession,
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
    viewer: Boolean,
    stale: Loader<*>,
    draft: WorkoutDraft?,
    reload: () -> Unit,
    onPreview: (String) -> Unit,
    onResume: (WorkoutDraft) -> Unit,
    onDraftCleared: () -> Unit,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onLog: (String) -> Unit,
    onOpenActivity: (Long) -> Unit,
    onOpenPlanSession: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = home.program
    // the plan from its disk cache: the lead follows the plan's strength sessions, offline too
    val plan by remember { Repo.planFlow(ctx) }.collectAsState(null)
    val sessions = plan?.sessions.orEmpty()
    val lead = strengthLead(sessions, LocalDate.now())
    val usesProgram = plan?.plan == null || sessions.any { it.workout?.program == "strength28" }
    // finished workouts still on the phone; flushed on every visit so a gym with no signal catches up later
    var waiting by remember { mutableIntStateOf(0) }
    var refused by remember { mutableStateOf<List<String>>(emptyList()) }
    var flushing by remember { mutableStateOf(false) }
    suspend fun flush() {
        flushing = true
        waiting = runCatching { Repo.flushWorkoutLogs(ctx) }.getOrElse { Repo.pending(ctx).size }
        refused = Repo.rejected.also { Repo.rejected = emptyList() }
        flushing = false
        if (waiting == 0) reload()
    }
    LaunchedEffect(Unit) { waiting = Repo.pending(ctx).size; if (owner && waiting > 0) flush() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Workout")
        StaleNote(stale)
        if (viewer) Text(tr("Only the owner logs workouts.", "Hanya pemilik yang mencatat workout."), Modifier.padding(horizontal = 4.dp), style = T.bodyS, color = K.muted)
        if (owner && draft != null) ResumeCard(draft, onResume = { onResume(draft) }) {
            scope.launch { Repo.saveDraft(ctx, null); onDraftCleared() }
        }
        lead.doneToday.forEach { DoneStrength(it, onOpenActivity, onLog) }
        val next = lead.next
        if (next != null) {
            val def = next.workoutKey?.let { k -> home.workouts.firstOrNull { it.key == k } }
            val whenText = if (next.date == LocalDate.now().toString()) tr("Today's strength", "Strength hari ini")
                else tr("Next strength · ${weekdayShort(next.date)} ${dayMonth(next.date)}", "Strength berikutnya · ${weekdayShort(next.date)} ${dayMonth(next.date)}")
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (def != null) NextHero(def, home, owner, title = next.title.text, context = listOfNotNull(whenText, planTarget(next).ifEmpty { null }).joinToString(" · ")) { onPreview(def.key) }
                else Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(whenText)
                    Text(next.title.text, style = T.headline, color = K.ink)
                    planTarget(next).takeIf { it.isNotEmpty() }?.let { Text(it, style = body(16.sp, FontWeight.SemiBold, 1.35f), color = K.body2) }
                }
                NotesWithSteps(next)
                if (def == null) SecondaryButton(tr("Open session", "Buka sesi"), { onOpenPlanSession(next.key) }, Modifier.fillMaxWidth())
            }
        } else {
            home.workouts.firstOrNull { it.key == "strength28:${p.nextDay}" }?.let { NextHero(it, home, owner) { onPreview(it.key) } }
        }
        if (usesProgram) ProgramCard(home, owner, onPreview)
        SectionHeader(tr("Body focus", "Fokus otot"))
        val focus = home.workouts.filter { it.program == "focus" }
        Column(Modifier.fillMaxWidth()) {
            focus.forEachIndexed { i, w ->
                if (i > 0) HorizontalDivider(color = K.line)
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onPreview(w.key) }.sizeIn(minHeight = 60.dp).padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(w.title.text, style = T.cardTitle, color = K.ink)
                        Text(tr("${w.minutes} min · ${w.items.distinctBy { it.ex }.size} exercises", "${w.minutes} mnt · ${w.items.distinctBy { it.ex }.size} latihan"), style = T.caption, color = K.muted)
                    }
                    Glyph(R.drawable.ic_chevron_right, K.muted)
                }
            }
        }
        if (home.routines.isNotEmpty()) {
            SectionHeader(tr("My routines", "Rutinitas saya"))
            val mine = home.workouts.filter { it.program == "routine" }
            Column(Modifier.fillMaxWidth()) {
                mine.forEachIndexed { i, w ->
                    if (i > 0) HorizontalDivider(color = K.line)
                    WorkoutRow(w) { onPreview(w.key) }
                }
            }
        }
        if (!usesProgram) {
            HorizontalDivider(color = K.line)
            ProgramRow(home) { onPreview("strength28:${p.nextDay}") }
        }
        SectionHeader(tr("This week", "Minggu ini"), tr("Mon to Sun", "Sen sampai Min"))
        WeekCard(home)
        if (waiting > 0 || refused.isNotEmpty()) PendingRow(waiting, refused, flushing) { scope.launch { flush() } }
        SectionHeader(tr("History", "Riwayat"), trailing = tr("All", "Semua"), onTrailing = onHistory)
        if (home.recent.isEmpty()) Text(tr("No workouts logged yet. Finish one and it shows up here.", "Belum ada workout. Selesaikan satu dan riwayatnya muncul di sini."), Modifier.padding(horizontal = 4.dp), style = T.bodyS, color = K.muted)
        else Panel(Modifier.fillMaxWidth(), gap = 0.dp, padding = 6.dp) {
            home.recent.take(5).forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = K.track, modifier = Modifier.padding(horizontal = 12.dp))
                LogRow(l) { if (l.source == "watch") l.activityId?.let(onOpenActivity) else onLog(l.id) }
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

/** A strength session finished today: the done mark, how it was done, and the way into its activity or log. */
@Composable
private fun DoneStrength(s: PlanSession, onOpenActivity: (Long) -> Unit, onLog: (String) -> Unit) {
    val act = s.activityIds.firstOrNull()
    val open = act?.let { { onOpenActivity(it) } } ?: s.logId?.let { { onLog(it) } }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(K.surface).border(1.dp, K.line, RoundedCornerShape(20.dp))
            .then(if (open != null) Modifier.clickable(role = Role.Button, onClick = open) else Modifier)
            .sizeIn(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlanStatusMark(s)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(s.title.text, style = body(15.sp, FontWeight.SemiBold), color = K.ink)
            Text(resultText(s)?.replaceFirstChar { it.uppercase() } ?: tr("Done today", "Selesai hari ini"), style = T.caption, color = K.muted)
        }
        if (open != null) Glyph(R.drawable.ic_chevron_right, K.muted, 20.dp)
    }
}

@Composable
private fun WorkoutRow(w: WorkoutDef, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).sizeIn(minHeight = 60.dp).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(w.title.text, style = T.cardTitle, color = K.ink)
            Text(tr("${w.minutes} min · ${w.items.distinctBy { it.ex }.size} exercises", "${w.minutes} mnt · ${w.items.distinctBy { it.ex }.size} latihan"), style = T.caption, color = K.muted)
        }
        Glyph(R.drawable.ic_chevron_right, K.muted)
    }
}

/** Strength 28 when the plan does not use it: one quiet row, so it does not compete with the plan's sessions. */
@Composable
private fun ProgramRow(home: WorkoutsHome, onOpen: () -> Unit) {
    val p = home.program
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).sizeIn(minHeight = 60.dp).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Glyph(R.drawable.ic_calendar_month, K.muted, 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.name.text, style = T.cardTitle, color = K.ink)
            Text(tr("Next: Day ${p.nextDay} · ${p.doneDays.size} of ${p.days} done", "Berikutnya: Hari ${p.nextDay} · ${p.doneDays.size} dari ${p.days} selesai"), style = T.caption, color = K.muted)
        }
        Glyph(R.drawable.ic_chevron_right, K.muted)
    }
}

/** Finished workouts not on the server yet, with a retry; and any the server refused, said once. */
@Composable
private fun PendingRow(waiting: Int, refused: List<String>, busy: Boolean, onRetry: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), padding = 14.dp, radius = 20.dp, gap = 8.dp) {
        if (waiting > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr(if (waiting == 1) "1 workout waiting to upload" else "$waiting workouts waiting to upload", "$waiting workout menunggu diunggah"),
                Modifier.weight(1f), style = body(15.sp, FontWeight.SemiBold), color = K.ink,
            )
            if (busy) Text(tr("Uploading", "Mengunggah"), style = T.caption, color = K.muted)
            else PillAction(tr("Retry", "Coba lagi"), onRetry)
        }
        refused.forEach {
            Text(tr("The server refused \"$it\", so it wasn't saved.", "Server menolak \"$it\", jadi tidak tersimpan."), style = T.bodyS, color = K.fair)
        }
    }
}

@Composable
private fun ResumeCard(d: WorkoutDraft, onResume: () -> Unit, onDiscard: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val n = d.workout.items.size
    val started = runCatching { java.time.LocalDateTime.parse(d.startTime.replace(' ', 'T')) }.getOrNull()
    // an unfinished workout from hours ago is more likely done with than paused
    val stale = started?.isBefore(java.time.LocalDateTime.now().minusHours(12)) == true
    Panel(Modifier.fillMaxWidth()) {
        Text(tr("Resume ${d.workout.title.text}", "Lanjutkan ${d.workout.title.text}"), style = T.cardTitle, color = K.ink)
        Text(
            listOfNotNull(tr("Set ${d.index + 1} of $n", "Set ${d.index + 1} dari $n"), started?.let { tr("started ${weekdayShort(it.toString())} ${dayMonth(it.toString())}", "mulai ${weekdayShort(it.toString())} ${dayMonth(it.toString())}") }).joinToString(" · "),
            style = T.bodyS, color = K.body2,
        )
        SegmentBar(d.index.toDouble(), K.good, of = n)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentButton(tr("Resume", "Lanjutkan"), onResume, icon = R.drawable.ic_play_arrow)
            Spacer(Modifier.weight(1f))
            if (stale) TextButton({
                scope.launch {
                    Repo.queueLog(ctx, draftLog(d, completed = false))
                    Repo.saveDraft(ctx, null)
                    runCatching { Repo.flushWorkoutLogs(ctx) }
                    onDiscard()
                }
            }) { Text(tr("Save as partial", "Simpan sebagian"), style = body(14.sp, FontWeight.SemiBold), color = K.ink) }
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
private fun NextHero(w: WorkoutDef, home: WorkoutsHome, owner: Boolean, title: String = w.title.text, context: String? = null, onOpen: () -> Unit) {
    val muscles = itemMuscles(w.items, home.exercises)
    val names = w.items.filter { it.block == "main" }.mapNotNull { home.exercises[it.ex]?.name?.text }.distinct()
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            context?.let { Label(it) }
            Text(title, style = T.headline, color = K.ink)
            if (context == null) Text(
                listOfNotNull(w.day?.let { tr("Day $it", "Hari $it") }, w.week?.let { tr("week $it", "minggu $it") }, home.program.name.text).joinToString(" · "),
                style = T.bodyS, color = K.muted,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { BodyFigure(muscles, 200.dp, tr("Muscles this day works", "Otot yang dilatih hari ini")) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Value("${w.minutes}", tr("min", "mnt"), num(28.sp, FontWeight.Bold), unitStyle = T.bodyS)
                Value("${w.items.distinctBy { it.ex }.size}", tr("exercises", "latihan"), num(28.sp, FontWeight.Bold), unitStyle = T.bodyS)
                musclesText(muscles).takeIf { it.isNotEmpty() }?.let {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Label(tr("Works", "Melatih"))
                        Text(it, style = T.bodyS, color = K.body2, maxLines = 4)
                    }
                }
            }
        }
        if (names.isNotEmpty()) Text(names.take(5).joinToString(" · ") + if (names.size > 5) " · +${names.size - 5}" else "", style = T.bodyS, color = K.body2, maxLines = 3)
        // it opens the preview, where Start begins the player
        if (owner) AccentButton(tr("See workout", "Lihat workout"), onOpen, Modifier.fillMaxWidth(), icon = R.drawable.ic_play_arrow)
        else Text(tr("See exercises", "Lihat latihan"), Modifier.clickable(role = Role.Button, onClick = onOpen).sizeIn(minHeight = 44.dp).padding(vertical = 12.dp), style = body(14.sp, FontWeight.SemiBold), color = K.ink)
    }
}

@Composable
private fun ProgramCard(home: WorkoutsHome, owner: Boolean, onPreview: (String) -> Unit) {
    val p = home.program
    var open by rememberSaveable { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth(), gap = 14.dp) {
        Row(
            Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = if (open) tr("Hide calendar", "Tutup kalender") else tr("Show calendar", "Tampilkan kalender")) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                CardTitle(p.name.text, icon = R.drawable.ic_calendar_month)
                Text(tr("${p.doneDays.size} of ${p.days} days done", "${p.doneDays.size} dari ${p.days} hari selesai"), style = T.caption, color = K.muted)
            }
            if (p.runsCompleted > 0) NeutralChip(tr("Finished ${p.runsCompleted}×", "Tamat ${p.runsCompleted}×"))
            Glyph(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more, K.muted)
        }
        SegmentBar(p.doneDays.size.toDouble(), K.ink, of = p.days)
        if (open) (0 until 4).forEach { w ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(tr("Week ${w + 1}", "Minggu ${w + 1}"))
                Row(Modifier.fillMaxWidth()) {
                    (1..7).forEach { i ->
                        val d = w * 7 + i
                        val done = d in p.doneDays
                        val partial = d in p.partialDays
                        val isNext = d == p.nextDay
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape)
                                    .background(if (done) K.ink else K.track)
                                    .then(if (isNext) Modifier.border(2.dp, K.ink, CircleShape) else if (partial) Modifier.border(2.dp, K.fair, CircleShape) else Modifier)
                                    .clickable(role = Role.Button, onClickLabel = tr("Day $d", "Hari $d")) { onPreview("strength28:$d") },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (done) Glyph(R.drawable.ic_check_circle, K.onAccent, 20.dp, description = tr("Day $d done", "Hari $d selesai"))
                                else Text("$d", style = num(16.sp, FontWeight.Bold), color = if (isNext) K.ink else if (partial) K.fair else K.muted)
                            }
                        }
                    }
                }
            }
        }
        if (p.partialDays.isNotEmpty()) Text(tr("Orange ring: started but not finished.", "Lingkaran oranye: dimulai tapi belum selesai."), style = T.caption, color = K.muted)
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
            Stat("${w.sets}", tr("sets", "set"), num(28.sp), Modifier.weight(1f))
        }
        if (w.watch > 0) Text(tr(if (w.watch == 1) "Includes 1 session recorded only on the watch." else "Includes ${w.watch} sessions recorded only on the watch.",
            "Termasuk ${w.watch} sesi yang tercatat di jam saja."), style = T.caption, color = K.muted)
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
    var diff by remember(home.prefs.difficulty) { mutableIntStateOf(home.prefs.difficulty) }
    Panel(Modifier.fillMaxWidth(), gap = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Difficulty", "Tingkat"), style = T.cardTitle, color = K.ink)
            Text(tr("Bodyweight reps and holds ×0.8 or ×1.2 in Strength 28, body focus and your routines. Dumbbell sets and warm-ups stay as written.",
                "Repetisi dan tahanan tanpa alat ×0,8 atau ×1,2 di Strength 28, fokus otot dan rutinitasmu. Set dumbbell dan pemanasan tetap."), style = T.caption, color = K.muted)
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
        HorizontalDivider(color = K.line)
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
                colors = SwitchDefaults.colors(checkedThumbColor = K.onAccent, checkedTrackColor = K.ink, uncheckedTrackColor = K.track, uncheckedBorderColor = K.border),
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
            Glyph(icon, if (enabled) K.ink else K.muted.copy(alpha = 0.4f), 20.dp, description = description)
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
                Text(
                    if (l.source == "watch") tr("${duration(l.durationS.toDouble())} · on the watch", "${duration(l.durationS.toDouble())} · di jam tangan")
                    else tr("${duration(l.durationS.toDouble())} · ${l.reps} reps", "${duration(l.durationS.toDouble())} · ${l.reps} repetisi"),
                    style = T.bodyS, color = K.body2,
                )
                if (l.source == "homeworkout") NeutralChip(tr("Imported", "Diimpor"))
                if (!l.completed) StatusChip(tr("Partial", "Sebagian"), Status.FAIR)
            }
        }
        if (l.activityId != null) Glyph(R.drawable.ic_watch, K.muted, 20.dp, description = if (l.source == "watch") tr("Recorded on the watch", "Tercatat di jam") else tr("Linked to a watch activity", "Tersambung ke aktivitas jam"))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutPreview(def: WorkoutDef, home: WorkoutsHome, owner: Boolean, onBack: () -> Unit, onSaved: () -> Unit, onStart: (WorkoutDef) -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val origRounds = def.items.filter { it.block == "main" }.maxOfOrNull { it.rounds } ?: 1
    val initial = remember(def.key, def.items) { def.items.filter { it.block == "main" && it.round == 1 } }
    // edits work on one round of the main block and apply to every round of this session only; a plan's routine
    // (each exercise its own sets) plays exactly as written
    val editable = owner && def.program != "plan"
    var main by remember(def.key, def.items) { mutableStateOf(initial) }
    var rounds by remember(def.key, def.items) { mutableIntStateOf(origRounds) }
    var editing by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var swap by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val dirty = main != initial || rounds != origRounds
    val items = remember(main, rounds, def) {
        if (!dirty) def.items
        else {
            val head = def.items.takeWhile { it.block == "warmup" }
            val tail = def.items.filter { it.block == "finisher" || it.block == "cooldown" }
            head + (1..rounds).flatMap { r -> main.map { it.copy(round = r, rounds = rounds) } } + tail
        }
    }
    val edited = def.copy(items = items, minutes = workoutMinutes(items, home.prefs.restS))
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BackLink("Workout", onBack) }
                if (editable) TextButton({ editing = !editing }) {
                    Text(if (editing) tr("Done editing", "Selesai edit") else "Edit", style = body(14.sp, FontWeight.SemiBold), color = K.ink)
                }
            }
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(def.title.text, style = T.screenTitle, color = K.ink)
                Text(
                    when (def.program) {
                        "strength28" -> listOfNotNull(home.program.name.text, def.day?.let { tr("Day $it", "Hari $it") }, def.week?.let { tr("Week $it", "Minggu $it") }).joinToString(" · ")
                        "plan" -> tr("From your training plan", "Dari training plan")
                        "routine" -> tr("Your routine", "Rutinitasmu")
                        else -> tr("Body focus", "Fokus otot")
                    },
                    style = T.body, color = K.body2,
                )
            }
            Panel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("${edited.minutes}", tr("min", "mnt"), num(28.sp), Modifier.weight(1f))
                    Stat("${items.distinctBy { it.ex }.size}", tr("exercises", "latihan"), num(28.sp), Modifier.weight(1f))
                    if (def.program == "plan") Stat("${items.count { it.block == "main" }}", tr("sets", "set"), num(28.sp), Modifier.weight(1f))
                    else Stat("$rounds", tr("rounds", "ronde"), num(28.sp), Modifier.weight(1f))
                }
                val m = itemMuscles(items, home.exercises)
                BodyFigure(m, 200.dp, tr("Muscles this workout works", "Otot yang dilatih workout ini"))
                Text(musclesText(m), style = T.bodyS, color = K.body2)
            }
            if (editing) Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Rounds", "Ronde"), Modifier.weight(1f), style = T.cardTitle, color = K.ink)
                Stepper("$rounds", onMinus = { rounds = (rounds - 1).coerceAtLeast(1) }, onPlus = { rounds = (rounds + 1).coerceAtMost(6) }, minusEnabled = rounds > 1, plusEnabled = rounds < 6)
            }
            val groups = if (def.program == "plan") {
                // one row per exercise with its sets, in the plan's order
                listOf("plan" to items.filter { it.round == 1 })
            } else listOf(
                "warmup" to items.filter { it.block == "warmup" },
                "main" to main,
                "finisher" to items.filter { it.block == "finisher" },
                "cooldown" to items.filter { it.block == "cooldown" },
            ).filter { it.second.isNotEmpty() }
            groups.forEach { (block, list) ->
                Label(if (block == "plan") tr("Exercises · sets in a row, 60 s between sets", "Latihan · set berturut-turut, 60 dtk antar set") else blockLabel(block, rounds), modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                Panel(Modifier.fillMaxWidth(), gap = 0.dp, padding = 6.dp) {
                    list.forEachIndexed { i, it ->
                        if (i > 0) HorizontalDivider(color = K.track, modifier = Modifier.padding(horizontal = 12.dp))
                        val e = home.exercises[it.ex]
                        val kg = (it.kg ?: home.weights[it.ex]?.suggest)?.takeIf { e?.equipment == "dumbbell" && it > 0 }
                        val ids = list.map { x -> x.ex }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { sheet = ids to i }
                                .padding(horizontal = 12.dp, vertical = if (editing && block == "main") 4.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e?.name?.text ?: it.ex, style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                                Text(
                                    (if (block == "plan") "${it.rounds} × " + (it.reps?.toString() ?: workTarget(null, it.seconds)) else workTarget(it.reps, it.seconds)) + (kg?.let { k -> " · ${kgText(k)}" } ?: ""),
                                    style = num(16.sp, FontWeight.Bold), color = K.muted,
                                )
                            }
                            if (editing && block == "main") {
                                val step = if (it.reps != null) 1 else 5
                                Stepper(
                                    "", valueStyle = T.caption,
                                    onMinus = { main = main.toMutableList().also { l -> l[i] = it.copy(reps = it.reps?.minus(step)?.coerceAtLeast(1), seconds = it.seconds?.minus(step)?.coerceAtLeast(10)) } },
                                    onPlus = { main = main.toMutableList().also { l -> l[i] = it.copy(reps = it.reps?.plus(step)?.coerceAtMost(100), seconds = it.seconds?.plus(step)?.coerceAtMost(600)) } },
                                )
                                IconAction(R.drawable.ic_swap_horiz, tr("Swap exercise", "Ganti latihan")) { swap = i }
                                IconAction(R.drawable.ic_delete, tr("Remove", "Hapus"), enabled = main.size > 1) { main = main.filterIndexed { k, _ -> k != i } }
                            }
                        }
                    }
                    if (editing && block == "main") Text(
                        tr("Add exercise", "Tambah latihan"),
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { adding = true }
                            .sizeIn(minHeight = 48.dp).padding(horizontal = 12.dp).wrapContentHeight(),
                        style = body(14.sp, FontWeight.SemiBold), color = K.ink,
                    )
                }
            }
            if (owner && def.program == "routine") TextButton({ deleting = true }) {
                Text(tr("Delete routine", "Hapus rutinitas"), style = body(14.sp, FontWeight.SemiBold), color = K.low)
            }
        }
        if (owner) Row(
            Modifier.fillMaxWidth().background(K.base).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (dirty) SecondaryButton(tr("Save as routine", "Simpan rutinitas"), { naming = true }, Modifier.weight(1f))
            AccentButton(tr("Start", "Mulai"), { onStart(edited) }, Modifier.weight(1f), icon = R.drawable.ic_play_arrow)
        }
    }
    sheet?.let { (ids, i) -> ExerciseSheet(ids, i, home.exercises, home.history) { sheet = null } }
    if (adding) LibraryPicker(home) { ex ->
        adding = false
        if (ex != null) home.exercises[ex]?.let { e ->
            main = main + WorkoutItem(ex, reps = if (e.kind == "reps") 10 else null, seconds = if (e.kind == "time") 30 else null, block = "main", round = 1, rounds = rounds)
        }
    }
    if (naming) {
        var title by remember { mutableStateOf(if (def.program == "routine") def.title.text else "") }
        AlertDialog(
            onDismissRequest = { naming = false },
            containerColor = K.surface,
            title = { Text(tr("Save as routine", "Simpan rutinitas"), style = T.cardTitle, color = K.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("It shows under My routines with these exercises and $rounds rounds.", "Muncul di Rutinitas saya dengan latihan ini dan $rounds ronde."), style = T.bodyS, color = K.body2)
                    androidx.compose.material3.OutlinedTextField(
                        title, { title = it.take(40) }, singleLine = true,
                        placeholder = { Text(tr("Name, like Push day", "Nama, misal Hari dorong"), color = K.muted) },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = K.ink, unfocusedBorderColor = K.border, focusedTextColor = K.ink, unfocusedTextColor = K.ink, cursorColor = K.ink),
                    )
                }
            },
            confirmButton = {
                TextButton({
                    naming = false
                    val r = Routine(
                        id = def.key.removePrefix("routine:").takeIf { def.program == "routine" }, title = title.trim(), rounds = rounds,
                        items = main.map { RoutineItem(it.ex, it.reps, it.seconds) },
                    )
                    scope.launch {
                        runCatching { Repo.api(ctx).saveRoutine(r) }
                            .onSuccess { Toast.makeText(ctx, tr("Saved to My routines", "Tersimpan di Rutinitas saya"), Toast.LENGTH_SHORT).show(); onSaved() }
                            .onFailure { Toast.makeText(ctx, it.friendly(), Toast.LENGTH_LONG).show() }
                    }
                }, enabled = title.isNotBlank()) { Text(tr("Save", "Simpan"), color = K.ink) }
            },
            dismissButton = { TextButton({ naming = false }) { Text(tr("Cancel", "Batal"), color = K.muted) } },
        )
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        containerColor = K.surface,
        title = { Text(tr("Delete this routine?", "Hapus rutinitas ini?"), style = T.cardTitle, color = K.ink) },
        text = { Text(tr("Workouts you already logged with it stay in your history.", "Workout yang sudah tercatat dengannya tetap di riwayat."), style = T.bodyS, color = K.body2) },
        confirmButton = {
            TextButton({
                deleting = false
                scope.launch {
                    runCatching { Repo.api(ctx).deleteRoutine(def.key.removePrefix("routine:")) }
                        .onSuccess { onSaved(); onBack() }
                        .onFailure { Toast.makeText(ctx, it.friendly(), Toast.LENGTH_LONG).show() }
                }
            }) { Text(tr("Delete", "Hapus"), color = K.low) }
        },
        dismissButton = { TextButton({ deleting = false }) { Text(tr("Cancel", "Batal"), color = K.ink) } },
    )
    swap?.let { i ->
        val cur = main[i]
        val first = home.exercises[cur.ex]?.p?.firstOrNull()
        val curEx = home.exercises[cur.ex]
        // same main muscle and the same kind (reps or holds), and no stretches or warm-up moves in a main block
        val options = home.exercises.filter { (k, v) -> k != cur.ex && v.p.firstOrNull() == first && v.kind == curEx?.kind && k !in STRETCHES }.entries.sortedBy { it.value.name.text }
        Sheet({ swap = null }) {
            Text(tr("Swap exercise", "Ganti latihan"), style = T.sheetTitle, color = K.ink)
            Text(tr("Same main muscle: ${first?.let(::muscleLabel) ?: "–"}", "Otot utama sama: ${first?.let(::muscleLabel) ?: "–"}"), style = T.bodyS, color = K.muted)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (options.isEmpty()) Text(tr("Nothing else works this muscle the same way.", "Tidak ada latihan lain yang serupa untuk otot ini."), style = T.body, color = K.muted)
                options.forEach { (k, v) ->
                    Text(
                        v.name.text + if (v.equipment == "dumbbell") " · Dumbbell" else "",
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) {
                            main = main.toMutableList().also { l -> l[i] = cur.copy(ex = k) }
                            swap = null
                        }.padding(vertical = 14.dp, horizontal = 4.dp),
                        style = body(15.sp, FontWeight.SemiBold), color = K.ink,
                    )
                }
            }
        }
    }
}

/** Warm-up and cool-down moves that a swap in a main block should not offer. */
private val STRETCHES = setOf(
    "cat-cow-pose", "chest-stretch", "childs-pose", "cobra-stretch", "shoulder-stretch", "arm-circles", "jumping-jacks",
    "side-lying-floor-stretch-left", "side-lying-floor-stretch-right", "spine-lumbar-twist-stretch-left", "spine-lumbar-twist-stretch-right",
    "standing-quad-stretch-left", "standing-quad-stretch-right",
)

/** The library as a picker for "Add exercise": search, tap one to add it; dismiss adds nothing. */
@Composable
private fun LibraryPicker(home: WorkoutsHome, onPick: (String?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = home.exercises.entries.filter { it.key !in STRETCHES }
        .filter { (_, e) -> query.isBlank() || e.name.text.contains(query.trim(), true) || (e.name.en ?: "").contains(query.trim(), true) }
        .sortedBy { it.value.name.text }
    Sheet({ onPick(null) }) {
        Text(tr("Add exercise", "Tambah latihan"), style = T.sheetTitle, color = K.ink)
        Box(Modifier.padding(horizontal = 0.dp)) { SearchField(query, tr("Search exercises", "Cari latihan")) { query = it } }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            list.forEach { (k, e) ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onPick(k) }.padding(vertical = 12.dp, horizontal = 4.dp),
                ) {
                    Text(e.name.text, style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                    Text(listOfNotNull(if (e.equipment == "dumbbell") "Dumbbell" else tr("No equipment", "Tanpa alat"), e.p.firstOrNull()?.let(::muscleLabel)).joinToString(" · "), style = T.caption, color = K.muted)
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
fun ExerciseSheet(ids: List<String>, start: Int, catalog: Map<String, ExerciseDef>, history: Map<String, List<ExerciseVisit>> = emptyMap(), onDismiss: () -> Unit) {
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
        history[ids[i]]?.takeIf { it.isNotEmpty() }?.let { visits ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Last time: ${visitText(visits[0])} · ${dayMonth(visits[0].date)}", "Terakhir: ${visitText(visits[0])} · ${dayMonth(visits[0].date)}"), style = T.cardTitle, color = K.ink)
                visits.drop(1).forEach { v ->
                    Row {
                        Text(dayMonth(v.date), Modifier.width(72.dp), style = T.caption, color = K.muted)
                        Text(visitText(v), style = T.bodyS, color = K.body2)
                    }
                }
            }
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

/** "3 × 10 · 5 kg", "10 · 10 · 8 · 5 kg", "2 × 0:30": sets as done, the same reps folded into one count. */
internal fun visitText(v: ExerciseVisit): String {
    val parts = v.reps.takeIf { r -> r.any { it > 0 } }?.let { r -> if (r.distinct().size == 1) "${r.size} × ${r[0]}" else r.joinToString(" · ") }
        ?: v.seconds.takeIf { it.isNotEmpty() }?.let { s -> if (s.distinct().size == 1) "${s.size} × ${workTarget(null, s[0])}" else s.joinToString(" · ") { workTarget(null, it) } }
        ?: "–"
    return parts + (v.kg?.let { " · ${kgText(it)}" } ?: "")
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
    sheet?.let { (ids, i) -> ExerciseSheet(ids, i, home.exercises, home.history) { sheet = null } }
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
private fun LogDetail(
    id: String, owner: Boolean, home: WorkoutsHome, onDoAgain: (String) -> Unit, onChanged: () -> Unit,
    back: String, onBack: () -> Unit, onOpenActivity: (Long) -> Unit,
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberCachedLoad("workout_log", id) { Repo.api(ctx).workoutLog(id) }
    var confirm by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf<String?>(null) }
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
                    if (l.sets.isNotEmpty()) LogSets(l.sets, home) else LogExercises(l.exercises)
                }
                if (owner && home.workouts.any { it.key == l.key }) SecondaryButton(tr("Do it again", "Ulangi workout ini"), { onDoAgain(l.key) }, Modifier.fillMaxWidth())
                if (owner && l.activityId == null) Panel(Modifier.fillMaxWidth(), padding = 14.dp, radius = 20.dp, gap = 8.dp) {
                    Text(tr("Not linked to a watch activity", "Belum tersambung ke aktivitas jam"), style = T.cardTitle, color = K.ink)
                    Text(tr("Recorded strength on the watch too? Link it so the day counts once and HR shows here.", "Merekam strength di jam juga? Sambungkan supaya harinya dihitung sekali dan HR-nya ikut."), style = T.bodyS, color = K.muted)
                    PillAction(tr("Link watch activity", "Sambungkan aktivitas jam")) { linking = l.date }
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
                if (owner && l.activityId != null) TextButton({
                    scope.launch {
                        runCatching { Repo.api(ctx).linkWorkoutLog(id, LinkIn(null)) }.onSuccess { loader.reload(); onChanged() }
                            .onFailure { Toast.makeText(ctx, it.friendly(), Toast.LENGTH_SHORT).show() }
                    }
                }) { Text(tr("Unlink watch activity", "Lepas aktivitas jam"), style = body(14.sp, FontWeight.SemiBold), color = K.muted) }
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
                        .onSuccess { onChanged(); onBack() }
                        .onFailure { Toast.makeText(ctx, it.friendly(), Toast.LENGTH_SHORT).show() }
                }
            }) { Text(tr("Delete", "Hapus"), color = K.low) }
        },
        dismissButton = { TextButton({ confirm = false }) { Text(tr("Cancel", "Batal"), color = K.ink) } },
    )
    linking?.let { day ->
        val acts = rememberLoad(day) { Repo.api(ctx).activities(day, day).filter { it.type == "strength_training" } }
        Sheet({ linking = null }) {
            Text(tr("Link watch activity", "Sambungkan aktivitas jam"), style = T.sheetTitle, color = K.ink)
            Text(tr("Strength sessions on the watch that day.", "Sesi strength di jam pada hari itu."), style = T.bodyS, color = K.muted)
            when (val st = acts.state) {
                Load.Loading -> Shimmer(Modifier.fillMaxWidth().height(64.dp), 16.dp)
                is Load.Err -> Text(st.message, style = T.body, color = K.muted)
                is Load.Ok -> if (st.data.isEmpty()) Text(tr("No strength activity on the watch that day.", "Tidak ada aktivitas strength di jam hari itu."), style = T.body, color = K.muted)
                else Column {
                    st.data.forEachIndexed { i, a ->
                        if (i > 0) HorizontalDivider(color = K.track)
                        ActivityRow(a, hPad = 0.dp) {
                            linking = null
                            scope.launch {
                                runCatching { Repo.api(ctx).linkWorkoutLog(id, LinkIn(a.id)) }.onSuccess { loader.reload(); onChanged() }
                                    .onFailure { Toast.makeText(ctx, it.friendly(), Toast.LENGTH_LONG).show() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Each exercise with its sets as done: "10 · 10 · 8 @ 5 kg", skipped sets shown muted as "–". */
@Composable
private fun LogSets(sets: List<dev.garminfit.SetLog>, home: WorkoutsHome) {
    val byEx = sets.filter { it.block == "main" || it.block == "finisher" }.groupBy { it.ex }
    Column {
        byEx.entries.forEachIndexed { i, (ex, xs) ->
            if (i > 0) HorizontalDivider(color = K.track)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(home.exercises[ex]?.name?.text ?: ex, style = body(15.sp, FontWeight.SemiBold), color = K.ink, modifier = Modifier.weight(1f))
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        xs.forEachIndexed { k, x ->
                            if (k > 0) append(" · ")
                            if (x.skipped) withStyle(androidx.compose.ui.text.SpanStyle(color = K.muted)) { append("–") }
                            else append(x.reps?.toString() ?: x.seconds?.let { workTarget(null, it) } ?: "–")
                        }
                        xs.mapNotNull { it.kg?.takeIf { k -> k > 0 } }.maxOrNull()?.let { append(" @ ${kgText(it)}") }
                    },
                    style = num(17.sp, FontWeight.Bold), color = K.ink,
                )
            }
        }
    }
}
