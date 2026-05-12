package com.ultratv.tv.nativeapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class OpenDialog { NONE, XTREAM, M3U_URL, STALKER }

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val providers by vm.providers.collectAsState()
    val message by vm.message.collectAsState()
    val syncing by vm.syncing.collectAsState()

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var openDialog by remember { mutableStateOf(OpenDialog.NONE) }

    // SAF picker for local M3U files. Kept here at the top so the contract is
    // remembered across recompositions; the trigger is a Button further down.
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
                vm.addM3uLocal("", label ?: "Local", text)
            }
        },
    )

    // Default worker URL pre-filled so users can just press "Sync from cloud"
    // once their MAC is provisioned in the dashboard.
    var workerBase by remember { mutableStateOf("https://ultratv-config.khalilbenaz.workers.dev") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

        // ---- 1. MAC + cloud sync ----
        SectionCard {
            Text("📡 Auto-import via device MAC", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your MAC:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text(
                    vm.deviceMacAddress,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Text(
                "Open the dashboard, paste this MAC, add your providers there, then press Sync.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            )
            Button(
                onClick = { vm.importByMac(workerBase.trim()) },
                enabled = !syncing,
            ) { Text(if (syncing) "Working…" else "Sync from cloud", fontSize = 15.sp) }
        }

        // ---- 2. Add a provider manually ----
        SectionCard {
            Text("➕ Add a provider", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Tap a button to open the form. The IME only shows up inside the dialog — you won't trip on it while scrolling Settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openDialog = OpenDialog.XTREAM }) { Text("+ Xtream Codes") }
                Button(onClick = { openDialog = OpenDialog.M3U_URL }) { Text("+ M3U URL") }
                Button(
                    onClick = {
                        pickFile.launch(arrayOf(
                            "audio/x-mpegurl",
                            "application/vnd.apple.mpegurl",
                            "application/x-mpegurl",
                            "text/plain",
                            "application/octet-stream",
                            "*/*",
                        ))
                    },
                ) { Text("+ M3U file…") }
                Button(onClick = { openDialog = OpenDialog.STALKER }) { Text("+ Stalker portal") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
        }

        // ---- 3. Configured providers ----
        SectionCard {
            Text("Configured providers (${providers.size})", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (providers.isEmpty()) {
                Text("(none yet)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                providers.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.name}  ·  ${p.kind}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text(p.baseUrl, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { vm.resync(p.id) }, enabled = !syncing) { Text("Re-sync") }
                        Button(
                            onClick = { vm.delete(p.id) },
                            enabled = !syncing,
                            colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) { Text("Delete") }
                    }
                }
            }
        }

        // ---- 4. Display & playback ----
        SectionCard {
            Text("Display & playback", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            PreferencesSection()
        }

        // ---- 5. Parental ----
        SectionCard {
            Text("Parental controls", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            com.ultratv.tv.nativeapp.ui.parental.ParentalSection()
            Text(
                "When a PIN is set, adult categories (xxx / adult / 18+ / etc.) auto-lock on each sync.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    // ---- Dialogs ----
    when (openDialog) {
        OpenDialog.XTREAM -> XtreamDialog(
            onDismiss = { openDialog = OpenDialog.NONE },
            onSubmit = { name, url, user, pass ->
                vm.addAndSync(name, url, user, pass); openDialog = OpenDialog.NONE
            },
        )
        OpenDialog.M3U_URL -> M3uDialog(
            onDismiss = { openDialog = OpenDialog.NONE },
            onSubmit = { name, url ->
                vm.addM3uAndSync(name, url); openDialog = OpenDialog.NONE
            },
        )
        OpenDialog.STALKER -> StalkerDialog(
            onDismiss = { openDialog = OpenDialog.NONE },
            onSubmit = { name, url, mac ->
                vm.addStalkerAndSync(name, url, mac); openDialog = OpenDialog.NONE
            },
        )
        OpenDialog.NONE -> Unit
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope
