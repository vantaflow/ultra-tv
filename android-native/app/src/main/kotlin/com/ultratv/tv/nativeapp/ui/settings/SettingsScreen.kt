package com.ultratv.tv.nativeapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

private enum class OpenDialog { NONE, XTREAM, M3U_URL, STALKER, WORKER, CONFIG_PASSWORD }

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val providers by vm.providers.collectAsState()
    val message by vm.message.collectAsState()
    val syncing by vm.syncing.collectAsState()

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var openDialog by remember { mutableStateOf(OpenDialog.NONE) }
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current
    val savedMsg = S.toastBackupSaved
    val saveFailedMsg = S.toastSaveFailed
    val emptyFileMsg = S.toastEmptyFile
    val configPwdSavedMsg = S.toastConfigPasswordSaved
    val backupReadyMsg = S.toastBackupReady
    val restoredTemplate = S.toastRestoredTemplate
    val restoreFailedPrefix = S.toastRestoreFailed

    // SAF picker for local M3U files. Kept here at the top so the contract is
    // remembered across recompositions; the trigger is a Button further down.
    // Backup export: SAF CreateDocument with a JSON mime hint. The VM has
    // already serialised the bundle to text via prepareBackup() before we
    // get here, so we just stream it to the picked URI.
    // Backup encryption password — shared between export and restore so the
    // user can also use it as the decryption hint when re-importing.
    var backupPwd by remember { mutableStateOf("") }

    val saveBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            val text = vm.consumeBackup()
            if (uri == null || text == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }.onSuccess {
                    com.ultratv.tv.nativeapp.ui.common.Toaster.ok(savedMsg)
                }.onFailure {
                    com.ultratv.tv.nativeapp.ui.common.Toaster.err(saveFailedMsg + (it.message ?: ""))
                }
            }
        },
    )
    val loadBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                val txt = runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?.toString(Charsets.UTF_8).orEmpty()
                }.getOrNull()
                if (txt.isNullOrBlank()) {
                    com.ultratv.tv.nativeapp.ui.common.Toaster.err(emptyFileMsg)
                } else {
                    vm.restoreBackup(
                        text = txt,
                        restoredTemplate = restoredTemplate,
                        failedPrefix = restoreFailedPrefix,
                        password = backupPwd.takeIf { it.isNotEmpty() },
                    )
                }
            }
        },
    )

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

    // Worker URL is now stored in DataStore (per-device), never hard-coded.
    // Each user provisions their own worker and pastes its URL here once.
    val workerBase by vm.workerBaseUrl.collectAsState()
    val configPwd by vm.configPassword.collectAsState()

    val T = com.ultratv.tv.nativeapp.ui.theme.UltraTokens
    val F = com.ultratv.tv.nativeapp.ui.theme.UltraFonts
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = T.EdgeGutter, end = T.EdgeGutter, top = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "RÉGLAGES",
            color = T.Fg3,
            fontSize = 11.sp,
            letterSpacing = 2.3.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            S.settingsTitle,
            fontFamily = F.Serif,
            fontSize = 56.sp,
            lineHeight = 56.sp,
            letterSpacing = (-1.5).sp,
            color = T.Fg,
        )
        Spacer(Modifier.height(8.dp))

        // Manual update check — useful when the launch-time auto-check
        // missed (no network at start, dialog dismissed too early, etc.).
        val updateInfo by com.ultratv.tv.nativeapp.update.UpdateChecker.state.collectAsState()
        var checking by remember { mutableStateOf(false) }
        var checkMsg by remember { mutableStateOf<String?>(null) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    if (checking) return@Button
                    checking = true
                    checkMsg = null
                    scope.launch {
                        val info = com.ultratv.tv.nativeapp.update.UpdateChecker.checkForUpdate()
                        checking = false
                        checkMsg = if (info != null) "Mise à jour ${info.versionName} disponible"
                        else "Vous êtes à jour (v${com.ultratv.tv.nativeapp.BuildConfig.VERSION_NAME})"
                    }
                },
            ) {
                Text(if (checking) "Vérification…" else "Vérifier les mises à jour", fontSize = 14.sp)
            }
            checkMsg?.let { Text(it, color = T.Fg3, fontSize = 13.sp) }
            if (updateInfo != null) {
                Text(
                    "v${updateInfo!!.versionName} prête à installer",
                    color = T.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // ---- 1. MAC + cloud sync ----
        SectionCard {
            Text(S.settingsAutoImportTitle, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(S.settingsYourMac, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
                S.settingsMacHint,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    workerBase,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { openDialog = OpenDialog.WORKER }) { Text(S.change) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    S.settingsConfigPasswordLabel + if (configPwd.isBlank()) S.settingsConfigPasswordNone
                    else "•".repeat(configPwd.length.coerceAtMost(20)),
                    color = if (configPwd.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { openDialog = OpenDialog.CONFIG_PASSWORD }) {
                    Text(if (configPwd.isBlank()) S.settingsSet else S.change)
                }
            }
            Button(
                onClick = { vm.importByMac(workerBase.trim()) },
                enabled = !syncing && workerBase.isNotBlank(),
            ) { Text(if (syncing) S.settingsSyncing else S.settingsSyncFromCloud, fontSize = 15.sp) }
        }

        // ---- 2. Add a provider manually ----
        SectionCard {
            Text(S.settingsAddProviderTitle, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                S.settingsAddProviderHint,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openDialog = OpenDialog.XTREAM }) { Text(S.settingsAddXtream) }
                Button(onClick = { openDialog = OpenDialog.M3U_URL }) { Text(S.settingsAddM3uUrl) }
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
                ) { Text(S.settingsAddM3uFile) }
                Button(onClick = { openDialog = OpenDialog.STALKER }) { Text(S.settingsAddStalker) }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
        }

        // ---- 3. Configured providers ----
        SectionCard {
            Text("${S.settingsConfiguredHeader} (${providers.size})", color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (providers.isEmpty()) {
                Text(S.settingsNoneYet, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                providers.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (p.active) {
                                    Text(
                                        S.settingsDefaultBadge,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Text("${p.name}  ·  ${p.kind}", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                            }
                            Text(p.baseUrl, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!p.active) {
                            Button(onClick = { vm.setDefault(p.id) }, enabled = !syncing) { Text(S.settingsSetDefault) }
                        }
                        Button(onClick = { vm.resync(p.id) }, enabled = !syncing) { Text(S.settingsResync) }
                        Button(
                            onClick = { vm.delete(p.id) },
                            enabled = !syncing,
                            colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) { Text(S.delete) }
                    }
                }
            }
        }

        // ---- 4. Display & playback ----
        SectionCard {
            Text(S.settingsDisplay, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            PreferencesSection()
        }

        // ---- 4b. Backup / restore ----
        SectionCard {
            Text(S.settingsBackupTitle, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                S.settingsBackupHint,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            )
            Text(
                "Le fichier exporté contient tes credentials Xtream/Stalker en clair. Saisis un mot de passe pour chiffrer le backup en AES-GCM (recommandé).",
                color = T.Fg3,
                fontSize = 12.sp,
            )
            com.ultratv.tv.nativeapp.ui.settings.FormField(
                label = "Mot de passe de chiffrement (optionnel)",
                value = backupPwd,
                onChange = { backupPwd = it },
                password = true,
                placeholder = "Laisser vide pour un export en clair",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.prepareBackup(backupReadyMsg, password = backupPwd.takeIf { it.isNotEmpty() })
                    val suffix = if (backupPwd.isNotEmpty()) "encrypted" else "plain"
                    saveBackup.launch("ultra-tv-backup-${System.currentTimeMillis()}-$suffix.json")
                }) { Text(S.settingsBackupExport) }
                Button(onClick = {
                    loadBackup.launch(arrayOf("application/json", "*/*"))
                }) { Text(S.settingsBackupImport) }
            }
        }

        // ---- 5. Parental ----
        SectionCard {
            Text(S.settingsParental, color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            com.ultratv.tv.nativeapp.ui.parental.ParentalSection(
                onManageLockedChannels = { onNavigate("locked-channels") },
            )
            Text(
                S.settingsParentalHint,
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
        OpenDialog.WORKER -> WorkerUrlDialog(
            initial = workerBase,
            onDismiss = { openDialog = OpenDialog.NONE },
            onSubmit = { url ->
                vm.saveWorkerBase(url); openDialog = OpenDialog.NONE
            },
        )
        OpenDialog.CONFIG_PASSWORD -> ConfigPasswordDialog(
            initial = configPwd,
            onDismiss = { openDialog = OpenDialog.NONE },
            onSubmit = { pwd ->
                vm.saveConfigPassword(pwd); openDialog = OpenDialog.NONE
                com.ultratv.tv.nativeapp.ui.common.Toaster.ok(configPwdSavedMsg)
            },
        )
        OpenDialog.NONE -> Unit
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun ConfigPasswordDialog(initial: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var pwd by remember { mutableStateOf(initial) }
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current
    AddProviderDialog(
        title = S.settingsConfigPwdDialogTitle,
        onDismiss = onDismiss,
        onSubmit = { onSubmit(pwd) },
        canSubmit = true,
    ) {
        Text(
            S.settingsConfigPwdDialogHint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        FormField(
            label = S.settingsConfigPwdFieldLabel,
            value = pwd,
            onChange = { pwd = it },
            placeholder = S.settingsConfigPwdFieldPlaceholder,
            password = true,
            autoFocus = true,
        )
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun WorkerUrlDialog(initial: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var url by remember { mutableStateOf(initial) }
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current
    AddProviderDialog(
        title = S.settingsWorkerDialogTitle,
        onDismiss = onDismiss,
        onSubmit = { onSubmit(url) },
        canSubmit = url.isNotBlank(),
    ) {
        Text(
            S.settingsWorkerDialogHint,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        FormField(
            label = S.settingsWorkerFieldLabel,
            value = url,
            onChange = { url = it },
            placeholder = "https://your-config.your-acct.workers.dev",
        )
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(com.ultratv.tv.nativeapp.ui.theme.UltraTokens.Surface1)
            .androidx_border()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Modifier.androidx_border(): Modifier =
    this.border(
        1.dp,
        com.ultratv.tv.nativeapp.ui.theme.UltraTokens.Line,
        RoundedCornerShape(16.dp),
    )

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope
