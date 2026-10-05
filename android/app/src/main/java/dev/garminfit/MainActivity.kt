package dev.garminfit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.garminfit.ui.AccentButton
import dev.garminfit.ui.K
import dev.garminfit.ui.LocalLogout
import dev.garminfit.ui.T
import dev.garminfit.ui.body
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.garminfit.ui.ActivitiesScreen
import dev.garminfit.ui.AppTheme
import dev.garminfit.ui.Centered
import dev.garminfit.ui.TodayScreen
import dev.garminfit.ui.TrainingScreen
import dev.garminfit.ui.TrendsScreen
import dev.garminfit.widget.RefreshWorker
import kotlinx.coroutines.launch

enum class Tab(val label: String) {
    TODAY("Hari Ini"),
    TRENDS("Tren"),
    ACTIVITIES("Aktivitas"),
    TRAINING("Latihan"),
}

const val EXTRA_TAB = "tab"
const val EXTRA_ACTIVITY = "activity_id"

class MainActivity : ComponentActivity() {
    private var launch by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark-only app: force light system bar icons regardless of the phone's theme.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        RefreshWorker.schedule(this)
        launch = intent
        setContent { AppTheme { Root(launch) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launch = intent
    }
}

@Composable
private fun Root(launch: Intent?) {
    val ctx = LocalContext.current
    var token by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        token = Repo.token(ctx)
        checked = true
    }
    val scope = rememberCoroutineScope()
    when {
        !checked -> Box(Modifier.fillMaxSize())
        token == null -> SetupScreen { t -> token = t }
        else -> Home(launch) {
            scope.launch { Repo.saveToken(ctx, null); token = null }
        }
    }
}

@Composable
private fun Home(launch: Intent?, onLogout: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var activityId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(launch) {
        launch?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name }?.let { tab = it } }
        launch?.getLongExtra(EXTRA_ACTIVITY, -1L)?.takeIf { it >= 0 }?.let { activityId = it }
    }
    BackHandler(enabled = tab != Tab.TODAY) { tab = Tab.TODAY }
    Scaffold(
        containerColor = K.base,
        bottomBar = { BottomNav(tab) { t -> tab = t; if (t != Tab.ACTIVITIES) activityId = null } },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            CompositionLocalProvider(LocalLogout provides onLogout) { when (tab) {
                Tab.TODAY -> TodayScreen(onOpenActivity = { activityId = it; tab = Tab.ACTIVITIES }, onLogout = onLogout)
                Tab.TRENDS -> TrendsScreen()
                Tab.ACTIVITIES -> ActivitiesScreen(activityId, onClosed = { activityId = null })
                Tab.TRAINING -> TrainingScreen(onOpenActivity = { activityId = it; tab = Tab.ACTIVITIES })
            } }
        }
    }
}

/** Text-only pills: four short Indonesian labels read faster than icons we'd have to invent. */
@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(K.navBg)) {
        HorizontalDivider(color = K.track)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 14.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Box(
                    Modifier.sizeIn(minHeight = 48.dp).clip(CircleShape).selectable(on, role = Role.Tab) { onSelect(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t.label,
                        Modifier.clip(CircleShape).background(if (on) K.good else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        style = body(13.sp, if (on) FontWeight.Bold else FontWeight.SemiBold, 1.2f),
                        color = if (on) K.onAccent else K.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(onDone: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        val t = input.trim()
        if (t.isEmpty()) { error = "Masukkan token dulu."; return }
        scope.launch {
            busy = true
            error = null
            runCatching { Repo.api(t).me() }
                .onSuccess { Repo.saveToken(ctx, t); onDone(t) }
                .onFailure { error = it.friendly() }
            busy = false
        }
    }
    Scaffold(containerColor = K.base) { pad ->
        Centered {
            Column(Modifier.padding(pad).fillMaxWidth().imePadding()) {
                Text("GARMIN-FIT", style = T.screenTitle, color = K.ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Masukkan token akses dari pemilik akun. Token cukup dimasukkan sekali.",
                    style = T.body,
                    color = K.muted,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    label = { Text("Token akses") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = K.low) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                AccentButton(if (busy) "Menghubungkan…" else "Hubungkan", ::submit, Modifier.fillMaxWidth(), enabled = !busy)
            }
        }
    }
}
