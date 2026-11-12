package com.ultratv.tv.nativeapp.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.memory.ActionResult
import com.ultratv.tv.nativeapp.data.memory.EconomyMode
import com.ultratv.tv.nativeapp.data.memory.ImageCacheLimit
import com.ultratv.tv.nativeapp.data.memory.MemoryManager
import com.ultratv.tv.nativeapp.data.memory.MemoryPrefs
import com.ultratv.tv.nativeapp.data.memory.RamState
import com.ultratv.tv.nativeapp.data.memory.StorageUsage
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.Toaster
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.responsiveWidth
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.GroupLabel
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PaneTitle
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.PrefRow
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.SwitchPrefRow
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Actions de la rubrique. */
enum class MemoryAction { IMAGES, TEMP, COMPACT, HISTORY, RESUME, TIMESHIFT }

data class MemoryUi(
    val usage: StorageUsage? = null,
    val ram: RamState? = null,
    val limited: Boolean = false,
    val autoCacheBytes: Long = 0L,
    /** Action en cours (une seule à la fois). */
    val busy: MemoryAction? = null,
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val manager: MemoryManager,
    private val prefs: MemoryPrefs,
) : ViewModel() {
    private val _state = MutableStateFlow(MemoryUi())
    val state: StateFlow<MemoryUi> = _state.asStateFlow()
    val values = prefs.state

    /** Recalcule RAM et tailles hors du fil principal ; l'affichage reste sur l'ancien état en attendant. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val ram = manager.ram()
            val limited = manager.limitedDevice()
            val auto = ImageCacheLimit.autoBytes(manager.freeBytes(), limited)
            _state.update { it.copy(ram = ram, limited = limited, autoCacheBytes = auto) }
            val usage = manager.usage()
            _state.update { it.copy(usage = usage) }
        }
    }

    fun run(action: MemoryAction, allProfiles: Boolean, onResult: (ActionResult) -> Unit) {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = action) }
        viewModelScope.launch {
            val r = when (action) {
                MemoryAction.IMAGES -> manager.clearImageCache()
                MemoryAction.TEMP -> manager.purgeTemp()
                MemoryAction.COMPACT -> manager.compactDatabase()
                MemoryAction.HISTORY -> manager.clearHistory(allProfiles)
                MemoryAction.RESUME -> manager.clearResumePositions(allProfiles)
                MemoryAction.TIMESHIFT -> manager.clearTimeshift()
            }
            _state.update { it.copy(busy = null) }
            onResult(r)
            refresh()
        }
    }

    fun setImageCache(v: String) {
        prefs.setImageCache(v)
        // Limite plus basse que la taille actuelle : on vide tout de suite (la fabrique d'images, elle, se règle au prochain démarrage).
        viewModelScope.launch {
            val limit = ImageCacheLimit.bytes(v, manager.freeBytes(), manager.limitedDevice())
            val size = _state.value.usage?.images ?: 0L
            if (ImageCacheLimit.overLimit(size, limit)) manager.clearImageCache()
            refresh()
        }
    }

    fun setAutoClean(v: Boolean) { prefs.setAutoClean(v); manager.applySchedule() }
    fun setOptimizeImages(v: Boolean) = prefs.setOptimizeImages(v)
    fun setEconomy(v: String) = prefs.setEconomy(v)
}

private data class Pending(val action: MemoryAction, val allProfiles: Boolean = false)

/** Réglages › Mémoire et stockage. */
@Composable
fun MemoryPane(vm: MemoryViewModel = hiltViewModel()) {
    val S = rememberMemoryStrings()
    val ui by vm.state.collectAsState()
    val v by vm.values.collectAsState()
    var scopeFor by remember { mutableStateOf<MemoryAction?>(null) }
    var confirm by remember { mutableStateOf<Pending?>(null) }
    var imageDialog by remember { mutableStateOf(false) }
    var economyDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh() }

    val u = ui.usage
    fun sz(pick: (StorageUsage) -> Long) = if (u == null) S.calculating else S.size(pick(u))

    PaneTitle(S.title, S.subtitle)

    Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
        GroupLabel(S.groupUsed)
        com.ultratv.tv.nativeapp.ui.design.SettingsCard {
            PrefRow(S.database, sz { it.database }, hint = S.databaseHint) { }
            PrefRow(S.images, sz { it.images }) { }
            PrefRow(S.downloads, sz { it.downloads }) { }
            PrefRow(S.timeshift, sz { it.timeshift }) { }
            PrefRow(S.temp, sz { it.temp }, hint = S.tempHint) { }
            PrefRow(S.logs, sz { it.logs }) { }
            PrefRow(S.totalUsed, sz { it.total }) { }
            PrefRow(S.freeSpace, sz { it.free }) { }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
        GroupLabel(S.groupRam)
        val ram = ui.ram
        com.ultratv.tv.nativeapp.ui.design.SettingsCard {
            PrefRow(S.ramAvailable, if (ram == null) S.calculating else S.ramValue(ram.available, ram.total)) { }
            PrefRow(S.device, if (ui.limited) S.deviceLimited else S.deviceComfortable) { }
        }
        if (ui.limited) Text(S.limitedNote, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 30.spx, modifier = Modifier.padding(top = 4.design))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
        GroupLabel(S.groupActions)
        com.ultratv.tv.nativeapp.ui.design.SettingsCard {
            ActionRow(S.clearImages, S.clearImagesHint, ui.busy == MemoryAction.IMAGES, S) { confirm = Pending(MemoryAction.IMAGES) }
            ActionRow(S.clearTemp, S.clearTempHint, ui.busy == MemoryAction.TEMP, S) { confirm = Pending(MemoryAction.TEMP) }
            ActionRow(S.compact, S.compactHint, ui.busy == MemoryAction.COMPACT, S) { confirm = Pending(MemoryAction.COMPACT) }
            ActionRow(S.clearHistory, S.clearHistoryHint, ui.busy == MemoryAction.HISTORY, S) { scopeFor = MemoryAction.HISTORY }
            ActionRow(S.clearResume, S.clearResumeHint, ui.busy == MemoryAction.RESUME, S) { scopeFor = MemoryAction.RESUME }
            ActionRow(S.clearTimeshift, S.clearTimeshiftHint, ui.busy == MemoryAction.TIMESHIFT, S) { confirm = Pending(MemoryAction.TIMESHIFT) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
        GroupLabel(S.groupSettings)
        val imageLabel = if (v.imageCache == ImageCacheLimit.AUTO) S.autoLimit(ui.autoCacheBytes) else S.size(v.imageCache.toLong() * ImageCacheLimit.MB)
        val economyLabel = when (v.economy) { EconomyMode.ON -> S.on; EconomyMode.OFF -> S.off; else -> S.economyAuto(ui.limited) }
        com.ultratv.tv.nativeapp.ui.design.SettingsCard {
            PrefRow(S.imageLimit, imageLabel, hint = S.imageLimitHint) { imageDialog = true }
            SwitchPrefRow(S.autoClean, v.autoClean, hint = S.autoCleanHint) { vm.setAutoClean(it) }
            PrefRow(S.economy, economyLabel, hint = S.economyHint) { economyDialog = true }
            SwitchPrefRow(S.optimizeImages, v.optimizeImages, hint = S.optimizeImagesHint) { vm.setOptimizeImages(it) }
        }
    }

    if (imageDialog) ChoiceDialog(
        S.imageLimit,
        listOf(ImageCacheLimit.AUTO to S.autoLimit(ui.autoCacheBytes)) + ImageCacheLimit.CHOICES_MB.map { it.toString() to S.size(it * ImageCacheLimit.MB) },
        v.imageCache, { vm.setImageCache(it); imageDialog = false }, { imageDialog = false },
    )
    if (economyDialog) ChoiceDialog(
        S.economy,
        listOf(EconomyMode.AUTO to S.economyAuto(ui.limited), EconomyMode.ON to S.on, EconomyMode.OFF to S.off),
        v.economy, { vm.setEconomy(it); economyDialog = false }, { economyDialog = false },
    )
    scopeFor?.let { a ->
        ChoiceDialog(
            S.scopeTitle, listOf(false to S.scopeCurrent, true to S.scopeAll), null,
            { all -> scopeFor = null; confirm = Pending(a, all) }, { scopeFor = null },
        )
    }
    confirm?.let { p ->
        val (title, body) = when (p.action) {
            MemoryAction.IMAGES -> S.confirmImagesTitle to S.confirmImagesBody
            MemoryAction.TEMP -> S.confirmTempTitle to S.confirmTempBody
            MemoryAction.COMPACT -> S.confirmCompactTitle to S.confirmCompactBody
            MemoryAction.HISTORY -> S.confirmHistoryTitle(p.allProfiles) to S.confirmHistoryBody
            MemoryAction.RESUME -> S.confirmResumeTitle(p.allProfiles) to S.confirmResumeBody
            MemoryAction.TIMESHIFT -> S.confirmTimeshiftTitle to S.confirmTimeshiftBody
        }
        ConfirmDialog(title, body, S.confirm, S.cancel, onConfirm = {
            confirm = null
            vm.run(p.action, p.allProfiles) { r -> report(S, p.action, r) }
        }, onDismiss = { confirm = null })
    }
}

private fun report(S: MemoryStrings, action: MemoryAction, r: ActionResult) {
    when (r) {
        is ActionResult.Done -> {
            val bytesAction = action == MemoryAction.IMAGES || action == MemoryAction.TEMP || action == MemoryAction.COMPACT || action == MemoryAction.TIMESHIFT
            when {
                bytesAction && r.freed > 0 -> Toaster.ok(S.freed(r.freed))
                bytesAction -> Toaster.show(S.nothingToFree)
                r.count > 0 -> Toaster.ok(S.cleared(r.count))
                else -> Toaster.show(S.nothingToFree)
            }
        }
        ActionResult.BlockedBySync -> Toaster.err(S.blockedSync)
        ActionResult.TimeshiftInUse -> Toaster.err(S.timeshiftBusy)
        ActionResult.NoSpace -> Toaster.err(S.noSpace)
        ActionResult.Failed -> Toaster.err(S.failed)
    }
}

@Composable
private fun ActionRow(label: String, hint: String, busy: Boolean, S: MemoryStrings, onClick: () -> Unit) {
    PrefRow(label, if (busy) S.working else "", hint = hint) { if (!busy) onClick() }
}

/** Confirmation modale : « Annuler » est le premier bouton (focus initial) — jamais de suppression d'un OK distrait. */
@Composable
private fun ConfirmDialog(title: String, body: String, confirmLabel: String, cancelLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier.responsiveWidth(760).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design),
            verticalArrangement = Arrangement.spacedBy(16.design),
        ) {
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.spx, maxLines = 3)
            Text(body, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx)
            Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
                PillButton(cancelLabel, onDismiss, heightPx = 64, hPadPx = 34, fontPx = 22, weight = FontWeight.Bold, bg = Ux.Cta)
                PillButton(confirmLabel, onConfirm, heightPx = 64, hPadPx = 30, fontPx = 22)
            }
        }
    }
}
