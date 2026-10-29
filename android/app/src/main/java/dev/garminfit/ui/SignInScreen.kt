package dev.garminfit.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.garminfit.AccessIn
import dev.garminfit.BuildConfig
import dev.garminfit.GoogleSignIn
import dev.garminfit.R
import dev.garminfit.Repo
import dev.garminfit.friendly
import dev.garminfit.greeting
import dev.garminfit.dayLabel
import dev.garminfit.tr
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate

/**
 * Google's account picker, then a server session saved where the access token used to live.
 * Returns false when the person closed the picker. A token from the old setup screen is linked
 * to the account right away, so updating the app keeps everyone's access.
 */
private suspend fun signInWithGoogle(ctx: Context): Boolean {
    val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = try {
        CredentialManager.create(ctx).getCredential(ctx, request).credential
    } catch (_: GetCredentialCancellationException) {
        return false
    }
    check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        tr("Google didn't return an account. Try again.", "Google tidak mengembalikan akun. Coba lagi.")
    }
    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
    val session = Repo.api("").signIn(GoogleSignIn(idToken, Build.MODEL)).session
    Repo.saveToken(ctx, session)
    clearLoadMemo(ctx)
    Repo.legacyToken(ctx)?.let { old ->
        // a rejected old token (revoked) is dropped too; a network failure keeps it for the next sign-in
        runCatching { Repo.api(session).addAccess(AccessIn(old)) }
            .onSuccess { Repo.saveLegacyToken(ctx, null) }
            .onFailure { if (it is HttpException) Repo.saveLegacyToken(ctx, null) }
    }
    return true
}

private fun Throwable.signInMessage(): String = when (this) {
    is NoCredentialException -> tr("No Google account on this phone. Add one in Settings, then try again.",
        "Belum ada akun Google di HP ini. Tambahkan di Setelan, lalu coba lagi.")
    is GetCredentialException -> tr("Couldn't reach Google. Check your connection and try again.",
        "Tidak bisa terhubung ke Google. Cek koneksi lalu coba lagi.")
    else -> friendly()
}

@Composable
fun SignInScreen(onSignedIn: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun signIn() {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            runCatching { signInWithGoogle(ctx) }
                .onSuccess { if (it) onSignedIn() }
                .onFailure { error = it.signInMessage() }
            busy = false
        }
    }
    Column(
        Modifier.fillMaxSize().background(K.base).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
    ) {
        Label("garmin-fit")
        Spacer(Modifier.height(10.dp))
        Text(tr("Sign in", "Masuk").uppercase(), style = T.statusWord.copy(fontSize = 48.sp, lineHeight = 44.sp), color = K.ink)
        Spacer(Modifier.height(10.dp))
        Text(
            tr("Readiness, runs and your plan, on any phone you sign in to.", "Readiness, lari, dan plan kamu, di HP mana pun kamu masuk."),
            style = T.body,
            color = K.body2,
        )
        Spacer(Modifier.height(20.dp))
        // the baseline band, the app's motif, as a quiet mark of what's behind the door
        FactorBar(56.0, 0.0, 100.0, 30.0, 66.0, Status.GOOD, "")
        Spacer(Modifier.height(36.dp))
        GoogleButton(busy, ::signIn)
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = T.bodyS, color = K.fair)
        }
    }
}

/** Google's dark sign-in button: #131314 fill, #8E918F outline, the four-color G. */
@Composable
private fun GoogleButton(busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color(0xFF8E918F)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF131314), contentColor = Color(0xFFE3E3E3),
            disabledContainerColor = Color(0xFF131314), disabledContentColor = Color(0xFFE3E3E3),
        ),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFFE3E3E3), strokeWidth = 2.dp)
        else Glyph(R.drawable.ic_google_g, Color.Unspecified, 20.dp)
        Spacer(Modifier.size(12.dp))
        Text(if (busy) tr("Signing in", "Sedang masuk") else tr("Continue with Google", "Lanjut dengan Google"), style = body(15.sp, FontWeight.SemiBold))
    }
}

/** Round initial that opens Account; volt for the owner, neutral for everyone else. */
@Composable
fun Avatar(name: String?, owner: Boolean, onClick: (() -> Unit)?, size: Int = 44) {
    val initial = name?.trim()?.firstOrNull()?.uppercase() ?: "?"
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = tr("Account", "Akun"), onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(if (owner) K.good else K.track),
            contentAlignment = Alignment.Center,
        ) {
            Text(initial, style = num((size * 0.45f).sp, FontWeight.Bold), color = if (owner) K.onAccent else K.ink)
        }
    }
}

/** Signed in, but the account has no access token yet: the one thing to do is add one. */
@Composable
fun NoAccessScreen(name: String?, onOpenAccount: () -> Unit, onAdded: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(K.base).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(dayLabel(LocalDate.now().toString()))
                Text(listOfNotNull(greeting(), name).joinToString(", "), style = T.greeting, color = K.ink)
            }
            Avatar(name, owner = false, onClick = onOpenAccount)
        }
        Panel(Modifier.fillMaxWidth(), padding = 20.dp, gap = 14.dp) {
            Text(tr("No data linked yet", "Belum ada data").uppercase(), style = T.screenTitle, color = K.ink)
            Text(
                tr("Your account is ready, but it can't see any Garmin data yet. Ask the owner for an access token and add it here. You only do this once per account.",
                    "Akun kamu sudah siap, tapi belum bisa melihat data Garmin. Minta token akses ke pemilik lalu tambahkan di sini. Cukup sekali per akun."),
                style = T.body,
                color = K.body3,
            )
            AccentButton(tr("Add access token", "Tambah token akses"), { adding = true }, Modifier.fillMaxWidth())
        }
    }
    if (adding) AddTokenSheet(onDismiss = { adding = false }, onAdded = { adding = false; onAdded() })
}
