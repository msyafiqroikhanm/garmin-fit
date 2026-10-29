package dev.garminfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.garminfit.AccessIn
import dev.garminfit.Account
import dev.garminfit.Invite
import dev.garminfit.InviteIn
import dev.garminfit.NewInvite
import dev.garminfit.R
import dev.garminfit.Repo
import dev.garminfit.dayLabel
import dev.garminfit.friendly
import dev.garminfit.syncTime
import dev.garminfit.tr
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Who you are, whose data you see, and (for the owner) who else sees it. [onAccessChanged] runs after
 * a token is added or removed so Root can re-check the role; [onSignOut] leaves for the sign-in screen.
 */
@Composable
fun AccountScreen(onBack: () -> Unit, onAccessChanged: () -> Unit, onSignOut: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = rememberLoad { Repo.api(ctx).account() }
    var adding by remember { mutableStateOf(false) }
    var inviting by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            actionError = null
            runCatching { block() }.onFailure { actionError = it.friendly() }
            loader.reload()
        }
    }

    Column(Modifier.fillMaxSize().background(K.base).statusBarsPadding()) {
        LoadView(loader, loadingText = tr("Loading your account", "Memuat akun kamu")) { a ->
            val owner = a.access?.role == "owner"
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BackLink(tr("Today", "Hari Ini"), onBack)
                ScreenTitle(tr("Account", "Akun"))
                Profile(a, owner)
                DataAccess(
                    a,
                    onAdd = { adding = true },
                    onRemove = {
                        confirm = tr("Stop seeing this data? You'll need the token again to get it back.",
                            "Berhenti melihat data ini? Kamu butuh token lagi untuk mengembalikannya.") to {
                            Repo.api(ctx).removeAccess()
                            clearLoadMemo(ctx)
                            onAccessChanged()
                        }
                    },
                )
                a.garmin?.let { GarminCard(it.connected, it.lastSync) }
                a.invites?.let { invites ->
                    SharedAccess(
                        invites,
                        onCreate = { inviting = true },
                        onRevoke = { inv ->
                            confirm = tr("Revoke ${inv.name}? Everyone who added this token loses access right away.",
                                "Cabut ${inv.name}? Semua yang memakai token ini langsung kehilangan akses.") to {
                                Repo.api(ctx).revokeInvite(inv.id)
                            }
                        },
                    )
                }
                actionError?.let { Text(it, Modifier.padding(horizontal = 4.dp), style = T.bodyS, color = K.low) }
                Panel(Modifier.fillMaxWidth(), gap = 4.dp) {
                    Text(tr("Language", "Bahasa"), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                    Text(
                        tr("Change it in Android Settings › Apps › garmin-fit › Language.", "Ubah di Setelan Android › Aplikasi › garmin-fit › Bahasa."),
                        style = T.caption,
                        color = K.muted,
                    )
                }
                SecondaryButton(tr("Sign out", "Keluar"), onSignOut, Modifier.fillMaxWidth(), color = K.low, icon = R.drawable.ic_logout)
                Text(
                    tr("Signing out clears the cached data on this phone. Your access stays on the account.",
                        "Keluar menghapus data tersimpan di HP ini. Akses kamu tetap tersimpan di akun."),
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    style = T.caption,
                    color = K.muted,
                )
            }
        }
    }

    if (adding) AddTokenSheet(onDismiss = { adding = false }, onAdded = { adding = false; loader.reload(); onAccessChanged() })
    if (inviting) InviteSheet(onDismiss = { inviting = false; loader.reload() })
    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = K.surface,
            text = { Text(text, style = T.body, color = K.ink) },
            confirmButton = { TextButton({ confirm = null; act(action) }) { Text(tr("Yes, remove", "Ya, hapus"), color = K.low) } },
            dismissButton = { TextButton({ confirm = null }) { Text(tr("Cancel", "Batal"), color = K.ink) } },
        )
    }
}

@Composable
private fun Profile(a: Account, owner: Boolean) {
    Panel(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(a.name, owner, onClick = null, size = 60)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(a.name ?: "", style = num(26.sp, FontWeight.Bold), color = K.ink)
                a.email?.let { Text(it, style = T.bodyS, color = K.muted) }
                Box(Modifier.padding(top = 4.dp)) { NeutralChip(tr("Signed in with Google", "Masuk dengan Google")) }
            }
        }
    }
}

@Composable
private fun DataAccess(a: Account, onAdd: () -> Unit, onRemove: () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        CardTitle(tr("Data you can see", "Data yang bisa kamu lihat"), icon = R.drawable.ic_monitor_heart)
        val access = a.access
        if (access == null) {
            Text(tr("Nothing yet. Add the access token the owner sent you.", "Belum ada. Tambahkan token akses dari pemilik."), style = T.bodyS, color = K.body2)
            AccentButton(tr("Add access token", "Tambah token akses"), onAdd, Modifier.fillMaxWidth())
            return@Panel
        }
        val owner = access.role == "owner"
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(access.ownerName, owner, onClick = null, size = 40)
            Column(Modifier.weight(1f)) {
                val who = access.ownerName?.let { tr("$it's Garmin", "Garmin $it") } ?: tr("Garmin data", "Data Garmin")
                Text(who, style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                Text(if (owner) tr("You can sync and invite", "Kamu bisa sync dan mengundang") else tr("Read-only", "Hanya lihat"), style = T.caption, color = K.muted)
            }
            if (owner) StatusChip(tr("Owner", "Pemilik"), Status.GOOD) else NeutralChip("Viewer")
        }
        SecondaryButton(tr("Add access token", "Tambah token akses"), onAdd, Modifier.fillMaxWidth(), icon = R.drawable.ic_key)
        if (!owner) TextButton(onRemove, Modifier.align(Alignment.CenterHorizontally)) {
            Text(tr("Stop seeing this data", "Berhenti melihat data ini"), style = T.bodyS, color = K.low)
        }
    }
}

@Composable
private fun GarminCard(connected: Boolean, lastSync: String?) {
    Panel(Modifier.fillMaxWidth()) {
        CardTitle(tr("Garmin connection", "Koneksi Garmin"), icon = R.drawable.ic_refresh)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).background(if (connected) K.good else K.low, CircleShape))
            Text(if (connected) tr("Connected", "Terhubung") else tr("Not connected", "Belum terhubung"), Modifier.weight(1f), style = body(15.sp, FontWeight.SemiBold), color = K.ink)
            syncTime(lastSync)?.let { Text("Sync $it", style = T.caption, color = K.muted) }
        }
        Text(
            tr("Daily sync runs at 10:00. Signing in to Garmin still happens from the laptop, so your Garmin password never reaches the server.",
                "Sync harian jalan jam 10:00. Login Garmin tetap dari laptop, jadi password Garmin kamu tidak pernah sampai ke server."),
            style = T.bodyS,
            color = K.body2,
        )
    }
}

@Composable
private fun SharedAccess(invites: List<Invite>, onCreate: () -> Unit, onRevoke: (Invite) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        CardTitle(tr("Shared access", "Akses bersama"), icon = R.drawable.ic_group)
        Text(
            tr("Partner, family, anyone you send a token to sees your data, read-only. They sign in with their own Google account, then add the token.",
                "Pasangan, keluarga, siapa pun yang kamu kirimi token bisa melihat datamu, hanya lihat. Mereka masuk dengan akun Google sendiri, lalu menambahkan token."),
            style = T.bodyS,
            color = K.body2,
        )
        Column {
            invites.forEachIndexed { i, inv ->
                if (i > 0) HorizontalDivider(color = K.track)
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(inv.name, inv.role == "owner", onClick = null, size = 40)
                    Column(Modifier.weight(1f)) {
                        Text(inv.name, style = body(15.sp, FontWeight.SemiBold), color = K.ink)
                        Text(inviteLine(inv), style = T.caption, color = K.muted)
                    }
                    if (inv.self) NeutralChip(tr("You", "Kamu"))
                    else TextButton({ onRevoke(inv) }) { Text(tr("Revoke", "Cabut"), style = body(14.sp, FontWeight.SemiBold), color = K.low) }
                }
            }
        }
        AccentButton(tr("Create invite token", "Buat token undangan"), onCreate, Modifier.fillMaxWidth())
    }
}

private fun inviteLine(inv: Invite): String {
    val role = if (inv.role == "owner") tr("Owner", "Pemilik") else "Viewer"
    val who = if (inv.users.isEmpty()) tr("token not used yet", "token belum dipakai") else inv.users.joinToString(", ")
    val seen = inv.lastUsed?.let { tr("last used ${dayLabel(it)}", "terakhir dipakai ${dayLabel(it)}") }
    return listOfNotNull(role, who, seen).joinToString(" · ")
}

/** Track-colored pill for the second action next to a volt one. */
@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = K.ink, icon: Int? = null) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = K.track, contentColor = color),
    ) {
        icon?.let { Glyph(it, color, 18.dp, Modifier.padding(end = 6.dp)) }
        Text(text, style = body(15.sp, FontWeight.Bold))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = K.good, unfocusedBorderColor = K.border, errorBorderColor = K.low,
    focusedTextColor = K.ink, unfocusedTextColor = K.ink, cursorColor = K.good,
    focusedLabelColor = K.good, unfocusedLabelColor = K.muted, focusedContainerColor = K.base, unfocusedContainerColor = K.base,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = K.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}

/** Paste the owner's token; it is saved to the account, so every phone signed in to it sees the same data. */
@Composable
fun AddTokenSheet(onDismiss: () -> Unit, onAdded: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun submit() {
        val t = input.trim()
        if (t.isEmpty()) { error = tr("Paste the token first.", "Tempel tokennya dulu."); return }
        scope.launch {
            busy = true
            error = null
            runCatching { Repo.api(ctx).addAccess(AccessIn(t)) }
                .onSuccess { clearLoadMemo(ctx); onAdded() }
                .onFailure {
                    error = if (it is HttpException && it.code() == 400) tr("This token is wrong or was revoked. Ask the owner for a new one.",
                        "Token salah atau sudah dicabut. Minta token baru ke pemilik.") else it.friendly()
                }
            busy = false
        }
    }
    Sheet(onDismiss) {
        Text(tr("Add access token", "Tambah token akses").uppercase(), style = num(28.sp), color = K.ink)
        Text(
            tr("Ask the owner for it. It's saved to your account, so every phone you sign in to sees the same data.",
                "Minta ke pemilik. Token disimpan di akun kamu, jadi semua HP tempat kamu masuk melihat data yang sama."),
            style = T.bodyS,
            color = K.body3,
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = null },
            label = { Text(tr("Access token", "Token akses")) },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it, color = K.low) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            textStyle = T.mono.copy(fontSize = 15.sp, lineHeight = 20.sp, color = K.ink),
            colors = fieldColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        clipboard.getText()?.text?.takeIf { it.isNotBlank() && input.isEmpty() }?.let { clip ->
            SecondaryButton(tr("Paste from clipboard", "Tempel dari clipboard"), { input = clip.trim(); error = null }, icon = R.drawable.ic_content_paste)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(tr("Cancel", "Batal"), onDismiss, Modifier.weight(1f))
            AccentButton(if (busy) tr("Adding…", "Menambahkan…") else tr("Add", "Tambah"), ::submit, Modifier.weight(1f), enabled = !busy)
        }
    }
}

/** Owner names an invite, then copies the token, which is only ever shown here. */
@Composable
private fun InviteSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf<NewInvite?>(null) }
    var copied by remember { mutableStateOf(false) }
    fun submit() {
        val n = name.trim()
        if (n.isEmpty()) { error = tr("Give it a name, like who it's for.", "Kasih nama, misalnya untuk siapa."); return }
        scope.launch {
            busy = true
            error = null
            runCatching { Repo.api(ctx).createInvite(InviteIn(n)) }.onSuccess { created = it }.onFailure { error = it.friendly() }
            busy = false
        }
    }
    Sheet(onDismiss) {
        val inv = created
        if (inv == null) {
            Text(tr("Create invite token", "Buat token undangan").uppercase(), style = num(28.sp), color = K.ink)
            Text(tr("Name it after the person, like \"partner\" or \"mom\". They get read-only access.",
                "Namai sesuai orangnya, misalnya \"pasangan\" atau \"ibu\". Mereka dapat akses hanya lihat."), style = T.bodyS, color = K.body3)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40); error = null },
                label = { Text(tr("Name", "Nama")) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it, color = K.low) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = fieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            AccentButton(if (busy) tr("Creating…", "Membuat…") else tr("Create", "Buat"), ::submit, Modifier.fillMaxWidth(), enabled = !busy)
        } else {
            Text(tr("Invite created", "Undangan dibuat").uppercase(), style = num(28.sp), color = K.ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(inv.name)
                NeutralChip(tr("Viewer, read-only", "Viewer, hanya lihat"))
            }
            Row(
                Modifier.fillMaxWidth().background(K.base, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(inv.token, Modifier.weight(1f), style = T.mono.copy(fontSize = 14.sp, lineHeight = 20.sp), color = K.ink)
                AccentButton(if (copied) tr("Copied", "Tersalin") else tr("Copy", "Salin"), {
                    clipboard.setText(AnnotatedString(inv.token))
                    copied = true
                }, icon = R.drawable.ic_content_copy)
            }
            Text(
                tr("Shown once. Copy it now and send it to ${inv.name}. Lost it? Revoke it and make a new one.",
                    "Hanya tampil sekali. Salin sekarang dan kirim ke ${inv.name}. Hilang? Cabut lalu buat yang baru."),
                Modifier.fillMaxWidth().background(K.fair.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(12.dp),
                style = T.bodyS,
                color = K.fair,
            )
            AccentButton(tr("Done", "Selesai"), onDismiss, Modifier.fillMaxWidth())
        }
    }
}
