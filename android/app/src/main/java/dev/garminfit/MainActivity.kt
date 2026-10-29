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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import dev.garminfit.ui.ActivitiesScreen
import dev.garminfit.ui.AppTheme
import dev.garminfit.ui.Centered
import dev.garminfit.ui.clearLoadMemo
import dev.garminfit.ui.LocalReduceMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import dev.garminfit.ui.TodayScreen
import dev.garminfit.ui.TrainingScreen
import dev.garminfit.ui.TrendsScreen
import dev.garminfit.widget.RefreshWorker
import dev.garminfit.widget.Widgets
import dev.garminfit.ui.AccountScreen
import dev.garminfit.ui.NoAccessScreen
import dev.garminfit.ui.SignInScreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import retrofit2.HttpException
import kotlinx.coroutines.launch

enum class Tab(private val en: String, private val id: String, @DrawableRes val icon: Int) {
    TODAY("Today", "Hari Ini", R.drawable.ic_monitor_heart),
    TRENDS("Trends", "Tren", R.drawable.ic_show_chart),
    ACTIVITIES("Activities", "Aktivitas", R.drawable.ic_directions_run),
    TRAINING("Training", "Latihan", R.drawable.ic_speed);

    val label: String get() = tr(en, id)
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
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    // null until /me answers; offline it stays null and Home paints from the caches
    var me by remember { mutableStateOf<Me?>(null) }
    var meVersion by remember { mutableIntStateOf(0) }
    // right after sign-in, wait for /me so a new account doesn't flash Home before "No data linked yet"
    var waitForMe by remember { mutableStateOf(false) }
    var accountOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        token = Repo.token(ctx)
        checked = true
    }
    LaunchedEffect(token, meVersion) {
        if (token == null) return@LaunchedEffect
        runCatching { Repo.me(ctx, fresh = true) }
            .onSuccess { m ->
                if (m.kind == "token") {
                    // an access token from the old setup screen: keep it to link once this phone signs in
                    Repo.saveLegacyToken(ctx, token)
                    Repo.saveToken(ctx, null)
                    token = null
                } else me = m
            }
            .onFailure { if (it is HttpException && it.code() == 401) { Repo.signOut(ctx); clearLoadMemo(ctx); token = null } }
        waitForMe = false
    }
    fun signOut() {
        scope.launch {
            Repo.signOut(ctx)
            clearLoadMemo(ctx)
            Widgets.updateAll(ctx)
            me = null
            accountOpen = false
            token = null
        }
    }
    BackHandler(enabled = accountOpen) { accountOpen = false }
    when {
        !checked -> Box(Modifier.fillMaxSize())
        token == null -> SignInScreen {
            scope.launch {
                waitForMe = true
                token = Repo.token(ctx)
            }
        }
        waitForMe -> Centered { CircularProgressIndicator(color = K.good) }
        accountOpen -> AccountScreen(onBack = { accountOpen = false }, onAccessChanged = { meVersion++ }, onSignOut = ::signOut)
        me?.role == null && me != null -> NoAccessScreen(me?.name, onOpenAccount = { accountOpen = true }, onAdded = {
            scope.launch { Widgets.updateAll(ctx) }
            meVersion++
        })
        else -> Home(launch, onOpenAccount = { accountOpen = true }, onLogout = ::signOut)
    }
}

@Composable
private fun Home(launch: Intent?, onOpenAccount: () -> Unit, onLogout: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var activityId by rememberSaveable { mutableStateOf<Long?>(null) }
    var planOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(launch) {
        launch?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name }?.let { tab = it } }
        launch?.getLongExtra(EXTRA_ACTIVITY, -1L)?.takeIf { it >= 0 }?.let { activityId = it }
    }
    BackHandler(enabled = tab != Tab.TODAY) { tab = Tab.TODAY }
    Scaffold(
        containerColor = K.base,
        bottomBar = { BottomNav(tab) { t -> tab = t; planOpen = false; if (t != Tab.ACTIVITIES) activityId = null } },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val reduce = LocalReduceMotion.current
            CompositionLocalProvider(LocalLogout provides onLogout) {
                // a short fade plus a small slide toward the tab's side of the nav bar
                AnimatedContent(
                    tab,
                    transitionSpec = {
                        if (reduce) EnterTransition.None togetherWith ExitTransition.None
                        else {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (fadeIn(tween(220, 60)) + slideInHorizontally(tween(260)) { it / 12 * dir }) togetherWith fadeOut(tween(120))
                        }
                    },
                    label = "tab",
                ) { t ->
                    when (t) {
                        Tab.TODAY -> TodayScreen(onOpenActivity = { activityId = it; tab = Tab.ACTIVITIES }, onOpenPlan = { planOpen = true; tab = Tab.TRAINING }, onOpenAccount = onOpenAccount)
                        Tab.TRENDS -> TrendsScreen()
                        Tab.ACTIVITIES -> ActivitiesScreen(activityId, onClosed = { activityId = null })
                        Tab.TRAINING -> TrainingScreen(onOpenActivity = { activityId = it; tab = Tab.ACTIVITIES }, planOpen = planOpen, onPlanOpen = { planOpen = it })
                    }
                }
            }
        }
    }
}

/** Icon over label, M3 style: the icon gives each tab a shape to find at a glance, the label removes guessing. */
@Composable
private fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(K.navBg)) {
        HorizontalDivider(color = K.track)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp).selectableGroup(),
        ) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier.weight(1f).sizeIn(minHeight = 48.dp).clip(RoundedCornerShape(16.dp))
                        .selectable(on, role = Role.Tab) { onSelect(t) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.size(width = 56.dp, height = 32.dp).clip(CircleShape).background(if (on) K.good else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(t.icon), null, Modifier.size(24.dp), tint = if (on) K.onAccent else K.muted)
                    }
                    Text(
                        t.label,
                        style = body(12.sp, if (on) FontWeight.Bold else FontWeight.SemiBold, 1.2f),
                        color = if (on) K.ink else K.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
