package dev.garminfit.ui

import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garminfit.Bi
import dev.garminfit.LogIn
import dev.garminfit.R
import dev.garminfit.Repo
import dev.garminfit.SetLog
import dev.garminfit.SetTarget
import dev.garminfit.WorkoutDef
import dev.garminfit.WorkoutDraft
import dev.garminfit.WorkoutItem
import dev.garminfit.WorkoutsHome
import dev.garminfit.duration
import dev.garminfit.isId
import dev.garminfit.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

private const val READY_MS = 10_000L
private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private enum class Phase { READY, WORK, REST, FINISH }

/** Everything the player needs to resume after a kill lives here and in the saved draft; timers run on elapsedRealtime. */
private class PlayerState(val def: WorkoutDef, draft: WorkoutDraft?) {
    var index by mutableIntStateOf(draft?.index ?: 0)
    var phase by mutableStateOf(Phase.READY)
    val sets = mutableStateListOf<SetLog?>().apply { addAll(draft?.sets ?: List(def.items.size) { null }) }
    var endsAt by mutableLongStateOf(SystemClock.elapsedRealtime() + READY_MS)
    var now by mutableLongStateOf(SystemClock.elapsedRealtime())
    var paused by mutableStateOf(false)
    var pausedLeft = 0L
    val startTime: String = draft?.startTime ?: LocalDateTime.now().format(STAMP)
    var activeMs by mutableLongStateOf(draft?.activeMs ?: 0L)
    val logId: String = draft?.logId ?: UUID.randomUUID().toString()
    var workStart = 0L
    var reps by mutableIntStateOf(0)
    var kg by mutableDoubleStateOf(0.0)
    var restTotal = 0L

    val item: WorkoutItem get() = def.items[index]
    val last: Boolean get() = index == def.items.size - 1
    val leftMs: Long get() = (if (paused) pausedLeft else endsAt - now).coerceAtLeast(0)

    fun draft() = WorkoutDraft(def, index, sets.toList(), startTime, activeMs, logId)

    fun pause() { if (!paused) { pausedLeft = endsAt - SystemClock.elapsedRealtime(); paused = true } }
    fun resume() { if (paused) { endsAt = SystemClock.elapsedRealtime() + pausedLeft; paused = false } }

    fun log(completed: Boolean): LogIn = LogIn(
        id = logId, key = def.key, program = def.program, day = def.day,
        title = def.day?.let { d -> Bi("Day $d · ${def.title.id}", "Day $d · ${def.title.en ?: def.title.id}") } ?: def.title,
        startTime = startTime, endTime = LocalDateTime.now().format(STAMP), durationS = (activeMs / 1000).toInt(), completed = completed,
        sets = def.items.mapIndexed { i, it -> sets[i] ?: SetLog(it.ex, it.block, it.round, SetTarget(it.reps, it.seconds), skipped = true) },
    )
}

@Composable
fun WorkoutPlayer(def: WorkoutDef, home: WorkoutsHome, draft: WorkoutDraft?, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val st = remember(def.key) { PlayerState(def, draft) }
    var info by remember { mutableStateOf(false) }
    var quit by remember { mutableStateOf(false) }
    var uploaded by remember { mutableStateOf<Boolean?>(null) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var voiceOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { voiceOn = Repo.voice(ctx) }
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = if (isId()) Locale("in", "ID") else Locale.US
                ttsReady = true
            }
        }
        engine
    }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    fun say(text: String) { if (voiceOn && ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "workout") }

    fun name(i: WorkoutItem) = home.exercises[i.ex]?.name?.text ?: i.ex
    fun spoken(i: WorkoutItem) = name(i) + ", " + (i.reps?.let { tr("$it reps", "$it kali") } ?: tr("${i.seconds} seconds", "${i.seconds} detik"))
    fun dumbbell(i: WorkoutItem) = home.exercises[i.ex]?.equipment == "dumbbell"

    fun startWork() {
        val i = st.item
        st.phase = Phase.WORK
        st.reps = i.reps ?: 0
        st.kg = st.sets.filterNotNull().lastOrNull { it.ex == i.ex && (it.kg ?: 0.0) > 0 }?.kg ?: home.weights[i.ex]?.suggest ?: 0.0
        st.workStart = SystemClock.elapsedRealtime()
        st.endsAt = st.workStart + (i.seconds ?: 0) * 1000L
        st.paused = false
    }
    fun startReady() {
        st.phase = Phase.READY
        st.endsAt = SystemClock.elapsedRealtime() + READY_MS
        st.paused = false
        say(spoken(st.item))
    }
    fun finish() {
        st.phase = Phase.FINISH
        val log = st.log(completed = true)
        scope.launch {
            // queued first, so a crash or no signal right now never loses a finished workout
            Repo.queueLog(ctx, log)
            Repo.saveDraft(ctx, null)
            uploaded = runCatching { Repo.flushWorkoutLogs(ctx) == 0 }.getOrDefault(false)
        }
        say(tr("Workout done. Nice work.", "Workout beres. Mantap."))
    }
    fun next() {
        if (st.last) return finish()
        val cur = st.item
        val nxt = def.items[st.index + 1]
        val newRound = nxt.block == cur.block && nxt.round == cur.round + 1
        st.restTotal = (if (newRound) ROUND_REST_S else home.prefs.restS) * 1000L
        st.phase = Phase.REST
        st.endsAt = SystemClock.elapsedRealtime() + st.restTotal
        st.paused = false
        say(tr("Rest. Next: ${spoken(nxt)}", "Istirahat. Berikutnya: ${spoken(nxt)}"))
    }
    fun record(skipped: Boolean = false) {
        val i = st.item
        val elapsed = ((SystemClock.elapsedRealtime() - st.workStart) / 1000).toInt().coerceIn(0, i.seconds ?: 0)
        st.sets[st.index] = SetLog(
            i.ex, i.block, i.round, SetTarget(i.reps, i.seconds),
            reps = i.reps?.let { if (skipped) null else st.reps },
            seconds = i.seconds?.let { if (skipped) elapsed else it },
            kg = st.kg.takeIf { dumbbell(i) && it > 0 && !skipped },
            skipped = skipped,
        )
        next()
    }
    fun afterRest() { st.index++; startWork() }

    LaunchedEffect(Unit) { if (draft != null) startReady() else say(spoken(st.item)) }
    // one loop drives every timer; active time leaves out pauses and the finish screen
    LaunchedEffect(Unit) {
        var lastTick = SystemClock.elapsedRealtime()
        var spokenSecond = -1L
        while (true) {
            delay(200)
            val t = SystemClock.elapsedRealtime()
            st.now = t
            if (!st.paused && st.phase != Phase.FINISH) st.activeMs += t - lastTick
            lastTick = t
            if (st.paused) continue
            val left = st.endsAt - t
            when (st.phase) {
                Phase.READY -> if (left <= 0) startWork()
                Phase.REST -> if (left <= 0) afterRest()
                Phase.WORK -> if (st.item.seconds != null) {
                    val s = ceil(left / 1000.0).toLong()
                    if (s in 1..3 && s != spokenSecond) { spokenSecond = s; say("$s") }
                    if (left <= 0) record()
                }
                Phase.FINISH -> Unit
            }
        }
    }
    LaunchedEffect(st.index, st.phase) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (st.phase != Phase.FINISH) Repo.saveDraft(ctx, st.draft())
    }
    BackHandler { if (st.phase == Phase.FINISH) onExit() else st.pause() }

    Box(Modifier.fillMaxSize().background(K.base)) {
        if (st.phase == Phase.FINISH) FinishView(st, home, uploaded, onExit)
        else Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Progress(st)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (st.phase) {
                    Phase.READY -> {
                        Label(tr("Get ready", "Siap-siap"))
                        Text(name(st.item), style = T.sheetTitle, color = K.ink)
                        Ring(st.leftMs / READY_MS.toFloat(), "${ceil(st.leftMs / 1000.0).toInt()}")
                        Text(workTarget(st.item.reps, st.item.seconds), style = num(36.sp), color = K.muted)
                    }
                    Phase.WORK -> WorkView(st, home, ::name, ::dumbbell) { record() }
                    Phase.REST -> {
                        val nxt = def.items[st.index + 1]
                        val newRound = nxt.block == st.item.block && nxt.round == st.item.round + 1
                        Label(if (newRound) tr("Round ${nxt.round} next", "Ronde ${nxt.round} berikutnya") else tr("Rest", "Istirahat"))
                        Ring(st.leftMs / st.restTotal.toFloat().coerceAtLeast(1f), "${ceil(st.leftMs / 1000.0).toInt()}")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton({ st.endsAt += 20_000; st.restTotal += 20_000 }) { Text("+20s", style = body(15.sp, FontWeight.Bold), color = K.ink) }
                            AccentButton(tr("Skip rest", "Lewati istirahat"), ::afterRest)
                        }
                        Panel(Modifier.fillMaxWidth(), padding = 14.dp, radius = 20.dp) {
                            Label(tr("Next · ${st.index + 2} / ${def.items.size}", "Berikutnya · ${st.index + 2} / ${def.items.size}"))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name(nxt), Modifier.weight(1f), style = T.cardTitle, color = K.ink)
                                Text(workTarget(nxt.reps, nxt.seconds), style = num(24.sp, FontWeight.Bold), color = K.ink)
                            }
                            home.exercises[nxt.ex]?.let { BodyFigure(exMuscles(it), 120.dp, tr("Muscles it works", "Otot yang dilatih")) }
                        }
                    }
                    Phase.FINISH -> Unit
                }
            }
            Controls(
                canBack = st.index > 0 || st.phase == Phase.REST,
                onBack = { if (st.phase != Phase.REST) st.index-- ; startWork() },
                onPause = { st.pause() },
                onSkip = { if (st.phase == Phase.WORK) record(skipped = true) else if (st.phase == Phase.REST) afterRest() else startWork() },
                onInfo = { st.pause(); info = true },
            )
        }
        if (st.paused && !info && !quit) PausedOverlay(
            onResume = { st.resume() },
            onRestart = { if (st.phase == Phase.REST) afterRest() else startWork() },
            onQuit = { quit = true },
        )
    }
    if (info) ExerciseSheet(listOf(st.item.ex), 0, home.exercises) { info = false; st.resume() }
    if (quit) AlertDialog(
        onDismissRequest = { quit = false },
        containerColor = K.surface,
        title = { Text(tr("Save what you did?", "Simpan yang sudah dikerjakan?"), style = T.cardTitle, color = K.ink) },
        text = { Text(tr("Exercises you didn't reach are saved as skipped.", "Latihan yang belum dikerjakan disimpan sebagai dilewati."), style = T.bodyS, color = K.body2) },
        confirmButton = {
            TextButton({
                quit = false
                val log = st.log(completed = false)
                scope.launch {
                    Repo.queueLog(ctx, log)
                    Repo.saveDraft(ctx, null)
                    val left = runCatching { Repo.flushWorkoutLogs(ctx) }.getOrDefault(1)
                    Toast.makeText(ctx, if (left == 0) tr("Saved", "Tersimpan") else tr("Saved on this phone, uploads when you're online", "Tersimpan di HP, diunggah saat online"), Toast.LENGTH_SHORT).show()
                    onExit()
                }
            }) { Text(tr("Save", "Simpan"), color = K.good) }
        },
        dismissButton = {
            Row {
                TextButton({ quit = false; scope.launch { Repo.saveDraft(ctx, null); onExit() } }) { Text(tr("Discard", "Buang"), color = K.low) }
                TextButton({ quit = false; st.resume() }) { Text(tr("Keep going", "Lanjut"), color = K.ink) }
            }
        },
    )
}

@Composable
private fun Progress(st: PlayerState) {
    val n = st.def.items.size
    val i = st.item
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (0 until n).forEach { k ->
                val c = when {
                    k < st.index || (k == st.index && st.phase == Phase.REST) -> K.good
                    k == st.index -> K.ink
                    else -> K.track
                }
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c))
            }
        }
        Row {
            Text("${st.index + 1} / $n", Modifier.weight(1f), style = num(18.sp, FontWeight.Bold), color = K.ink)
            if (i.block == "main") Text(tr("Round ${i.round} of ${i.rounds}", "Ronde ${i.round} dari ${i.rounds}"), style = T.label, color = K.muted)
            else Text(st.def.heading(), style = T.label, color = K.muted)
        }
    }
}

@Composable
private fun WorkView(st: PlayerState, home: WorkoutsHome, name: (WorkoutItem) -> String, dumbbell: (WorkoutItem) -> Boolean, onDone: () -> Unit) {
    val i = st.item
    Text(name(i), style = T.sheetTitle, color = K.ink)
    home.exercises[i.ex]?.let { BodyFigure(exMuscles(it), 160.dp, tr("Muscles it works", "Otot yang dilatih")) }
    if (i.seconds != null) {
        Ring(st.leftMs / (i.seconds * 1000f), workTarget(null, ceil(st.leftMs / 1000.0).toInt()))
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stepper("x${st.reps}", onMinus = { st.reps = (st.reps - 1).coerceAtLeast(0) }, onPlus = { st.reps++ }, minusEnabled = st.reps > 0, valueStyle = T.hero)
        }
        if (i.reps != st.reps) Text(tr("Target x${i.reps}", "Target x${i.reps}"), style = T.caption, color = K.muted)
        if (dumbbell(i)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(tr("Per dumbbell", "Per dumbbell"))
                Stepper(kgText(st.kg), onMinus = { st.kg = (st.kg - 0.5).coerceAtLeast(0.0) }, onPlus = { st.kg += 0.5 }, minusEnabled = st.kg > 0)
            }
        }
        AccentButton(tr("Done", "Selesai"), onDone, Modifier.fillMaxWidth())
    }
}

/** Countdown ring: volt arc on the track, the number in the middle. */
@Composable
private fun Ring(fraction: Float, text: String) {
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = text }) {
            val w = 12.dp.toPx()
            val inset = w / 2 + 4.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(K.track, 0f, 360f, false, tl, arc, style = Stroke(w))
            drawArc(K.good, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(text, style = num(64.sp, lh = 0.9f), color = K.ink)
    }
}

@Composable
private fun Controls(canBack: Boolean, onBack: () -> Unit, onPause: () -> Unit, onSkip: () -> Unit, onInfo: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        ControlButton(R.drawable.ic_skip_previous, tr("Previous", "Sebelumnya"), canBack, onBack)
        ControlButton(R.drawable.ic_pause, tr("Pause", "Jeda"), true, onPause, big = true)
        ControlButton(R.drawable.ic_skip_next, tr("Skip", "Lewati"), true, onSkip)
        ControlButton(R.drawable.ic_info, tr("How to do it", "Cara melakukannya"), true, onInfo)
    }
}

@Composable
private fun ControlButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit, big: Boolean = false) {
    Box(
        Modifier.size(if (big) 64.dp else 56.dp).clip(CircleShape).background(if (big) K.track else K.base)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, if (enabled) K.ink else K.muted.copy(alpha = 0.4f), if (big) 32.dp else 28.dp, description = description) }
}

@Composable
private fun PausedOverlay(onResume: () -> Unit, onRestart: () -> Unit, onQuit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(K.base.copy(alpha = 0.96f)).clickable(enabled = false) {}.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tr("Paused", "Dijeda"), style = T.screenTitle, color = K.ink)
        AccentButton(tr("Resume", "Lanjut"), onResume, Modifier.fillMaxWidth(), icon = R.drawable.ic_play_arrow)
        TextButton(onRestart) { Text(tr("Restart exercise", "Ulang latihan ini"), style = body(15.sp, FontWeight.SemiBold), color = K.ink) }
        TextButton(onQuit) { Text(tr("Quit", "Keluar"), style = body(15.sp, FontWeight.SemiBold), color = K.low) }
    }
}

@Composable
private fun FinishView(st: PlayerState, home: WorkoutsHome, uploaded: Boolean?, onExit: () -> Unit) {
    val done = st.sets.filterNotNull().filter { !it.skipped }
    val reps = done.sumOf { it.reps ?: 0 }
    val volume = done.sumOf { (it.reps ?: 0) * (it.kg ?: 0.0) }
    val worked = itemMuscles(st.def.items.filterIndexed { k, _ -> st.sets.getOrNull(k)?.skipped == false }, home.exercises)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(tr("DONE!", "BERES!"), style = T.statusWord, color = K.good)
        Text(st.def.heading() + " · " + st.def.title.text, style = T.cardTitle, color = K.body2)
        Panel(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(duration(st.activeMs / 1000.0), tr("time", "waktu"), num(26.sp), Modifier.weight(1f))
                Stat("${done.map { it.ex }.distinct().size}", tr("exercises", "latihan"), num(26.sp), Modifier.weight(1f))
                Stat("$reps", tr("reps", "repetisi"), num(26.sp), Modifier.weight(1f))
                if (volume > 0) Stat(kgText(volume), "volume", num(26.sp), Modifier.weight(1f))
            }
            BodyFigure(worked, 220.dp, tr("Muscles you worked", "Otot yang kamu latih"))
        }
        Text(
            when (uploaded) {
                null -> tr("Saving…", "Menyimpan…")
                true -> tr("Saved", "Tersimpan")
                false -> tr("Saved on this phone, uploads when you're online", "Tersimpan di HP, diunggah saat online")
            },
            style = T.bodyS, color = if (uploaded == false) K.fair else K.body2,
        )
        Text(tr("Your watch activity links after the next sync.", "Aktivitas jam tersambung setelah sync berikutnya."), style = T.bodyS, color = K.muted)
        Spacer(Modifier.height(8.dp))
        AccentButton(tr("Back to Workout", "Kembali ke Workout"), onExit, Modifier.fillMaxWidth())
    }
}
