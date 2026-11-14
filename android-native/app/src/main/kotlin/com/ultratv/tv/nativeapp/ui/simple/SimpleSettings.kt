package com.ultratv.tv.nativeapp.ui.simple

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
    fun leave() { app.setSimpleMode(false); onLeftSimple() }
    val first = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    RequestInitialFocus(first, hasFocus = { hasFocus })
    SimpleFrame(title = S.settings, onBack = onBack) {
        Column(Modifier.widthIn(max = 1100.design).padding(top = 24.design), verticalArrangement = Arrangement.spacedBy(28.design)) {
            SimpleButton(D.simpleBackFull, Icons.Home, primary = true, onClick = { if (SimpleHome.exitNeedsPin(pinSet)) askPin = true else leave() },
                modifier = Modifier.fillMaxWidth().focusRequester(first).onFocusChanged { hasFocus = it.isFocused })
            Text(D.simpleFullHint, color = Ux.Text2, fontFamily = Manrope, fontSize = 30.spx, lineHeight = 40.spx, maxLines = 3)
            SimpleButton(D.simpleAllSettings, Icons.Settings, primary = false, onClick = onAllSettings, modifier = Modifier.fillMaxWidth())
        }
    }
    if (askPin) ExitSimpleModePin(onUnlocked = { askPin = false; leave() }, onCancel = { askPin = false })
}
