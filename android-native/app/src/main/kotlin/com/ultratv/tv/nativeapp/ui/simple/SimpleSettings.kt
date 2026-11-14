package com.ultratv.tv.nativeapp.ui.simple

import androidx.compose.foundation.layout.heightIn
import com.ultratv.tv.nativeapp.i18n.textSizeLabel
import com.ultratv.tv.nativeapp.i18n.textSizeName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.hilt.navigation.compose.hiltViewModel
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.SimpleStrings
import com.ultratv.tv.nativeapp.i18n.simpleAllSettings
import com.ultratv.tv.nativeapp.i18n.simpleBackFull
import com.ultratv.tv.nativeapp.i18n.simpleFullHint
import com.ultratv.tv.nativeapp.ui.AppViewModel
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.profile.ProfileViewModel

/**
 * Réglages du mode simple : un grand bouton « Revenir au mode complet » (code parental demandé s'il existe) et
 * « Tous les réglages » (réglages complets, sans quitter le mode simple).
 */
@Composable
fun SimpleSettingsScreen(
    onAllSettings: () -> Unit,
    onLeftSimple: () -> Unit,
    onBack: () -> Unit,
    app: AppViewModel = hiltViewModel(),
    profiles: ProfileViewModel = hiltViewModel(),
) {
    val D = LocalDs.current
    val S = SimpleStrings(D.lang)
    val pinSet by profiles.parentalPinSet.collectAsState()
    var askPin by remember { mutableStateOf(false) }
    var choice by remember { mutableStateOf("") }
    val p by app.prefs.collectAsState()
    val profileList by profiles.profiles.collectAsState()
    val currentProfile by profiles.current.collectAsState()
    val multiProfiles = profileList.size > 1
    val profileName = currentProfile?.name
    fun leave() { app.setSimpleMode(false); onLeftSimple() }
    val first = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    RequestInitialFocus(first, hasFocus = { hasFocus })
    SimpleFrame(title = S.settings, onBack = onBack) {
        Column(Modifier.widthIn(max = 1100.design).padding(top = 24.design), verticalArrangement = Arrangement.spacedBy(28.design)) {
            SimpleButton(D.simpleBackFull, Icons.Home, primary = true, onClick = { if (SimpleHome.exitNeedsPin(pinSet)) askPin = true else leave() },
                modifier = Modifier.fillMaxWidth().focusRequester(first).onFocusChanged { hasFocus = it.isFocused })
            Text(D.simpleFullHint, color = Ux.Text2, fontFamily = Manrope, fontSize = 30.spx, lineHeight = 40.spx, maxLines = 3)
            // Quelques réglages essentiels, sans passer par les réglages complets.
            SimpleSettingRow(D.language, D.langName(com.ultratv.tv.nativeapp.i18n.AppLang.fromCode(p.language)), Icons.Globe) { choice = "lang" }
            SimpleSettingRow(D.textSizeLabel, D.textSizeName(com.ultratv.tv.nativeapp.ui.design.TextSize.parse(p.textSize)), Icons.Info) { choice = "text" }
            SimpleSettingRow(D.theme, com.ultratv.tv.nativeapp.ui.settings.themeLabel(D, p.theme), Icons.Monitor) { choice = "theme" }
            if (multiProfiles) SimpleSettingRow(S.switchProfile, profileName.orEmpty(), Icons.Account) { profiles.requestSwitch() }
            SimpleButton(D.simpleAllSettings, Icons.Settings, primary = false, onClick = onAllSettings, modifier = Modifier.fillMaxWidth())
        }
    }
    when (choice) {
        "lang" -> com.ultratv.tv.nativeapp.ui.design.ChoiceDialog(D.language, com.ultratv.tv.nativeapp.i18n.AppLang.entries.map { it.code to D.langName(it) }, p.language, { app.setLanguage(it); choice = "" }, { choice = "" })
        "text" -> com.ultratv.tv.nativeapp.ui.design.ChoiceDialog(D.textSizeLabel, com.ultratv.tv.nativeapp.ui.design.TextSize.entries.map { it to D.textSizeName(it) }, com.ultratv.tv.nativeapp.ui.design.TextSize.parse(p.textSize), { app.setTextSize(it.key); choice = "" }, { choice = "" })
        "theme" -> com.ultratv.tv.nativeapp.ui.design.ChoiceDialog(D.theme, listOf(com.ultratv.tv.nativeapp.data.prefs.AppTheme.AUTO, com.ultratv.tv.nativeapp.data.prefs.AppTheme.LIGHT, com.ultratv.tv.nativeapp.data.prefs.AppTheme.DARK).map { it to com.ultratv.tv.nativeapp.ui.settings.themeLabel(D, it) }, p.theme, { app.setTheme(it); choice = "" }, { choice = "" })
    }
    if (askPin) ExitSimpleModePin(onUnlocked = { askPin = false; leave() }, onCancel = { askPin = false })
}

/** Ligne de réglage du mode simple : grand libellé, valeur actuelle à droite, OK pour changer. */
@Composable
private fun SimpleSettingRow(label: String, value: String, iconPath: String, onClick: () -> Unit) {
    com.ultratv.tv.nativeapp.ui.design.FocusSurface(
        onClick = onClick, shape = androidx.compose.foundation.shape.RoundedCornerShape(28.design), bg = Ux.Surface2, ringWidth = 8.design, focusedScale = 1.02f,
        modifier = Modifier.fillMaxWidth().heightIn(min = 104.design),
    ) { f ->
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 44.design, vertical = 22.design), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.design)) {
            com.ultratv.tv.nativeapp.ui.design.DIcon(iconPath, 42.design, if (f) Ux.TextOnLight else Ux.Text, strokeWidth = 2.2f)
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = com.ultratv.tv.nativeapp.ui.design.Sora, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 36.spx, maxLines = 2, modifier = Modifier.weight(1f))
            Text(value, color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 30.spx, maxLines = 1)
        }
    }
}
