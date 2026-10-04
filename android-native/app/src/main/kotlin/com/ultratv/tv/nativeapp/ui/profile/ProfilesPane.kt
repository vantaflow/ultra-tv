package com.ultratv.tv.nativeapp.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultratv.tv.nativeapp.data.profile.ProfileColors
import com.ultratv.tv.nativeapp.data.profile.ProfileEntity
import com.ultratv.tv.nativeapp.data.profile.ProfileRules
import com.ultratv.tv.nativeapp.data.profile.StartupMode
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.PaneTitle
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.PrefRow
import com.ultratv.tv.nativeapp.ui.design.SwitchPrefRow
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.parental.PinPad
import com.ultratv.tv.nativeapp.ui.settings.AddProviderDialog
import com.ultratv.tv.nativeapp.ui.settings.FormField
import kotlinx.coroutines.launch

private enum class ProfileAction { RENAME, COLOR, KIDS, PIN, PIN_REMOVE, DELETE }

/** Réglages › Profils : liste, création, renommage, couleur, profil Enfants, PIN, suppression, mode de démarrage. */
@Composable
fun ProfilesPane(vm: ProfileViewModel = hiltViewModel()) {
    val S = ProfileStrings(LocalDs.current.lang)
    val profiles by vm.profiles.collectAsState()
    val startup by vm.startupMode.collectAsState()
    var menuFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var action by remember { mutableStateOf<Pair<ProfileEntity, ProfileAction>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var startupDialog by remember { mutableStateOf(false) }

    PaneTitle(S.settingsTitle, S.settingsSub)
    Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
        profiles.forEach { p ->
            PrefRow(
                label = p.name,
                value = listOfNotNull(if (p.isKids) S.kidsHint else null, if (p.pinHash != null) S.protectedHint else null).joinToString(" · "),
            ) { menuFor = p }
        }
        if (ProfileRules.canAdd(profiles.size)) PillButton(S.newProfile, { creating = true }, heightPx = 64, hPadPx = 32, fontPx = 22, bg = Ux.Surface2, iconPath = "M12 5v14M5 12h14")
    }
    PrefRow(S.startup, if (startup == StartupMode.ALWAYS_ASK) S.alwaysAsk else S.lastProfile) { startupDialog = true }

    if (startupDialog) ChoiceDialog(S.startup, listOf(StartupMode.ALWAYS_ASK to S.alwaysAsk, StartupMode.LAST_PROFILE to S.lastProfile), startup, { vm.setStartupMode(it); startupDialog = false }, { startupDialog = false })
    if (creating) ProfileEditDialog(null, vm, onDismiss = { creating = false })

    menuFor?.let { p ->
        val opts = buildList {
            add(ProfileAction.RENAME to S.rename)
            add(ProfileAction.COLOR to S.color)
            add(ProfileAction.KIDS to "${S.kidsProfile} : ${if (p.isKids) S.pinSet else S.pinNone}")
            add(ProfileAction.PIN to "${S.pin} : ${if (p.pinHash != null) S.pinSet else S.pinNone}")
            if (p.pinHash != null) add(ProfileAction.PIN_REMOVE to S.pinRemove)
            add(ProfileAction.DELETE to if (ProfileRules.canDelete(profiles.size)) S.delete else S.deleteLast)
        }
        ChoiceDialog(p.name, opts, null, onPick = { a ->
            menuFor = null
            if (a == ProfileAction.KIDS) vm.setKids(p.id, !p.isKids)
            else if (a == ProfileAction.PIN_REMOVE) vm.setPin(p.id, null)
            else if (a != ProfileAction.DELETE || ProfileRules.canDelete(profiles.size)) action = p to a
        }, onDismiss = { menuFor = null })
    }

    action?.let { (p, a) ->
        val close = { action = null }
        when (a) {
            ProfileAction.RENAME -> ProfileEditDialog(p, vm, onDismiss = close)
            ProfileAction.COLOR -> ChoiceDialog(S.color, ProfileColors.palette.mapIndexed { i, c -> c to S.colorNames[i] }, p.color, { vm.setColor(p.id, it); close() }, close)
            ProfileAction.PIN -> {
                PinPad(title = S.pin, subtitle = p.name, onComplete = { vm.setPin(p.id, it); close() }, onCancel = close)
            }
            ProfileAction.DELETE -> ChoiceDialog(S.deleteConfirm(p.name), listOf(true to S.confirm, false to S.cancel), false, { if (it) vm.delete(p.id); close() }, close)
            else -> close()
        }
    }
}

/** Création (profil null) ou renommage : nom, et choix « Enfants » à la création. */
@Composable
fun ProfileEditDialog(profile: ProfileEntity?, vm: ProfileViewModel, onDismiss: () -> Unit) {
    val S = ProfileStrings(LocalDs.current.lang)
    val profiles by vm.profiles.collectAsState()
    var name by remember { mutableStateOf(profile?.name.orEmpty()) }
    var kids by remember { mutableStateOf(false) }
    AddProviderDialog(
        title = if (profile == null) S.newProfile else S.rename,
        icon = "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0",
        onDismiss = onDismiss,
        canSubmit = ProfileRules.cleanName(name).isNotEmpty(),
        submitLabel = S.save,
        onSubmit = {
            if (profile == null) vm.create(name, ProfileColors.forIndex(profiles.size), kids) else vm.rename(profile.id, name)
            onDismiss()
        },
    ) {
        FormField(S.nameLabel, name, { name = it.take(ProfileRules.MAX_NAME) }, autoFocus = true, last = profile != null, required = true, testTag = "field-profile-name")
        if (profile == null) SwitchPrefRow(S.kidsProfile, kids, hint = S.kidsHelp) { kids = it }
    }
}
