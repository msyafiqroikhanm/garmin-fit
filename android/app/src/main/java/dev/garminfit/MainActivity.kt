package dev.garminfit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.garminfit.ui.ActivitiesScreen
import dev.garminfit.ui.AppTheme
import dev.garminfit.ui.Centered
import dev.garminfit.ui.Status
import dev.garminfit.ui.TodayScreen
import dev.garminfit.ui.TrainingScreen
import dev.garminfit.ui.TrendsScreen
import dev.garminfit.ui.color
import dev.garminfit.widget.RefreshWorker
import kotlinx.coroutines.launch

enum class Tab(val label: String, val icon: Int) {
    TODAY("Hari Ini", R.drawable.ic_today),
    TRENDS("Tren", R.drawable.ic_trends),
    ACTIVITIES("Aktivitas", R.drawable.ic_activities),
    TRAINING("Latihan", R.drawable.ic_training),
}

const val EXTRA_TAB = "tab"
const val EXTRA_ACTIVITY = "activity_id"

class MainActivity : ComponentActivity() {
    private var launch by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; if (t != Tab.ACTIVITIES) activityId = null },
                        icon = { Icon(painterResource(t.icon), contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.TODAY -> TodayScreen(onOpenActivity = { activityId = it; tab = Tab.ACTIVITIES }, onLogout = onLogout)
                Tab.TRENDS -> TrendsScreen()
                Tab.ACTIVITIES -> ActivitiesScreen(activityId)
                Tab.TRAINING -> TrainingScreen()
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
    Scaffold { pad ->
        Centered {
            Column(Modifier.padding(pad).fillMaxWidth().imePadding()) {
                Text("garmin-fit", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Masukkan token akses dari pemilik akun. Token cukup dimasukkan sekali.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; error = null },
                    label = { Text("Token akses") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = Status.LOW.color()) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.height(20.dp), strokeWidth = 2.dp)
                    else Text("Hubungkan")
                }
            }
        }
    }
}
