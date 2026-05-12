package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val providers by vm.providers.collectAsState()
    val message by vm.message.collectAsState()
    val syncing by vm.syncing.collectAsState()

    // Default points at the project-hosted Cloudflare Worker — users can
    // override it if they self-host their own.
    var workerBase by remember { mutableStateOf("https://ultratv-config.khalilbenaz.workers.dev") }
    var configUrl by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var m3uName by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var stalkerName by remember { mutableStateOf("") }
    var stalkerUrl by remember { mutableStateOf("") }
    var stalkerMac by remember { mutableStateOf("") }
    var localM3uName by remember { mutableStateOf("") }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Storage Access Framework picker. Mime types are permissive because some
    // file managers report M3U as text/plain or application/octet-stream.
    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val (label, text) = withContext(Dispatchers.IO) {
                    val display = runCatching {
                        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else uri.lastPathSegment
                        }
                    }.getOrNull() ?: uri.toString()
                    val body = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?.toString(Charsets.UTF_8).orEmpty()
                    display to body
                }
                vm.addM3uLocal(localM3uName, label ?: "Local", text)
            }
        },
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        // ---- MAC-based auto-import (preferred flow) -----------------------
        Text("📡 Auto-import via device MAC", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your device MAC:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text(
                vm.deviceMacAddress,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Text(
            "Open your dashboard, paste this MAC, fill in your providers (Xtream / M3U / Stalker), save. Then come back here and press 'Sync from cloud'.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
        )
        TvTextField(label = "Cloudflare Worker URL (e.g. https://ultratv-config.acct.workers.dev)", value = workerBase, onValueChange = { workerBase = it })
        Button(
            onClick = { vm.importByMac(workerBase.trim()) },
            enabled = !syncing && workerBase.isNotBlank(),
        ) { Text(if (syncing) "Working…" else "Sync from cloud", fontSize = 16.sp) }

        Spacer(Modifier.height(20.dp))

        // ---- Auto-import: one URL → many providers ------------------------
        Text("⚡ One-shot import from a remote config URL", color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(
            "Host a JSON file (gist, pastebin, your own server) listing every provider — Xtream, M3U, Stalker — and paste the URL here to import them all at once.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
        )
        TvTextField(label = "Config URL (raw JSON)", value = configUrl, onValueChange = { configUrl = it })
        Button(
            onClick = { vm.importFromRemoteConfig(configUrl.trim()) },
            enabled = !syncing && configUrl.isNotBlank(),
        ) { Text(if (syncing) "Importing…" else "Import all providers", fontSize = 16.sp) }

        Spacer(Modifier.height(20.dp))
        Text("Or add manually:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        Text("Add an Xtream Codes provider", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)

        TvTextField(label = "Name (optional)", value = name, onValueChange = { name = it })
        TvTextField(label = "Server URL  (http://host:port)", value = url, onValueChange = { url = it })
        TvTextField(label = "Username", value = user, onValueChange = { user = it })
        TvTextField(label = "Password", value = pass, onValueChange = { pass = it }, password = true)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { vm.addAndSync(name, url, user, pass) },
                enabled = !syncing && url.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
            ) { Text(if (syncing) "Working…" else "Add + sync Live", fontSize = 16.sp) }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp) }

        Spacer(Modifier.height(16.dp))
        Text("…or add an M3U playlist (URL)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        TvTextField(label = "Name (optional)", value = m3uName, onValueChange = { m3uName = it })
        TvTextField(label = "Playlist URL (.m3u / .m3u8)", value = m3uUrl, onValueChange = { m3uUrl = it })
        Button(
            onClick = { vm.addM3uAndSync(m3uName, m3uUrl) },
            enabled = !syncing && m3uUrl.isNotBlank(),
        ) { Text("Add M3U from URL", fontSize = 16.sp) }

        Spacer(Modifier.height(16.dp))
        Text("…or pick an M3U file from local storage", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        TvTextField(label = "Name (optional)", value = localM3uName, onValueChange = { localM3uName = it })
        Button(
            onClick = {
                // ".m3u" / ".m3u8" / ".txt" — SAF lets the user override mime anyway.
                pickFile.launch(arrayOf(
                    "audio/x-mpegurl",
                    "application/vnd.apple.mpegurl",
                    "application/x-mpegurl",
                    "text/plain",
                    "application/octet-stream",
                    "*/*",
                ))
            },
            enabled = !syncing,
        ) { Text("Pick M3U file…", fontSize = 16.sp) }

        Spacer(Modifier.height(16.dp))
        Text("…or add a Stalker portal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        TvTextField(label = "Name (optional)", value = stalkerName, onValueChange = { stalkerName = it })
        TvTextField(label = "Portal URL (e.g. http://host:port)", value = stalkerUrl, onValueChange = { stalkerUrl = it })
        TvTextField(label = "MAC address (XX:XX:XX:XX:XX:XX)", value = stalkerMac, onValueChange = { stalkerMac = it })
        Button(
            onClick = { vm.addStalkerAndSync(stalkerName, stalkerUrl, stalkerMac) },
            enabled = !syncing && stalkerUrl.isNotBlank() && stalkerMac.isNotBlank(),
        ) { Text("Add Stalker portal", fontSize = 16.sp) }

        Spacer(Modifier.height(16.dp))
        Text("Display & playback", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        com.ultratv.tv.nativeapp.ui.settings.PreferencesSection()

        Spacer(Modifier.height(16.dp))
        Text("Parental controls", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        com.ultratv.tv.nativeapp.ui.parental.ParentalSection()
        Text(
            "When a PIN is set, adult categories (xxx / adult / 18+ / etc.) are auto-locked on each sync.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
        )

        Spacer(Modifier.height(16.dp))
        Text("Providers", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        if (providers.isEmpty()) {
            Text("(none yet — add one above)", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            providers.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${p.name}  ·  ${p.kind}", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                        Text(p.baseUrl, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { vm.resync(p.id) }, enabled = !syncing) { Text("Re-sync") }
                    Button(onClick = { vm.delete(p.id) }, enabled = !syncing) { Text("Delete") }
                }
            }
        }
    }
}

@androidx.tv.material3.ExperimentalTvMaterial3Api
@Composable
private fun TvTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp),
            decorationBox = { inner ->
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) { inner() }
            },
        )
    }
}
