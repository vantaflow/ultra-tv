package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.data.prefs.DefaultPlayer
import com.ultratv.tv.nativeapp.data.prefs.SidebarPosition
import com.ultratv.tv.nativeapp.ui.AppViewModel

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun PreferencesSection(vm: AppViewModel = hiltViewModel()) {
    val p by vm.prefs.collectAsState()
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // Menu position
        PrefRow(label = S.settingsMenuPosition) {
            ChoiceChip(S.prefSidebar, on = p.sidebarPosition == SidebarPosition.LEFT) { vm.setSidebar(SidebarPosition.LEFT) }
            ChoiceChip(S.prefTopBar, on = p.sidebarPosition == SidebarPosition.TOP) { vm.setSidebar(SidebarPosition.TOP) }
        }

        // Theme
        PrefRow(label = S.settingsTheme) {
            ChoiceChip(S.prefThemeDark, on = p.theme == AppTheme.DARK) { vm.setTheme(AppTheme.DARK) }
            ChoiceChip(S.prefThemeAmoled, on = p.theme == AppTheme.AMOLED) { vm.setTheme(AppTheme.AMOLED) }
            ChoiceChip(S.prefThemeBlue, on = p.theme == AppTheme.BLUE) { vm.setTheme(AppTheme.BLUE) }
        }

        // External player option removed: everything plays through the
        // bundled Media3 / ExoPlayer to keep the experience seamless. The
        // pref is left in UserPreferences for backwards compat but no
        // longer exposed in the UI.

        SwitchRow(
            title = "Diagnostics distants",
            hint = "Envoie crashes + events au worker pour debug. Désactive pour stopper toute télémétrie sortante.",
            value = p.telemetryEnabled,
        ) { vm.setTelemetry(it) }
        SwitchRow(S.settingsAutoSync, S.prefAutoSyncHint, p.autoSyncOnLaunch) { vm.setAutoSync(it) }
        SwitchRow(S.prefShowChannelNumbers, S.prefShowChannelNumbersHint, p.showChannelNumbers) { vm.setShowChannelNumbers(it) }
        SwitchRow(S.prefHideAdult, S.prefHideAdultHint, p.hideAdultCategories) { vm.setHideAdult(it) }
        SwitchRow(S.prefResume, S.prefResumeHint, p.resumePlayback) { vm.setResumePlayback(it) }
        SwitchRow(S.prefAutoPlayNext, S.prefAutoPlayNextHint, p.autoPlayNextEpisode) { vm.setAutoPlayNext(it) }
        SwitchRow(S.prefLaunchAtBoot, S.prefLaunchAtBootHint, p.launchAtBoot) { vm.setLaunchAtBoot(it) }
        SwitchRow(S.prefAutoPlayLast, S.prefAutoPlayLastHint, p.autoPlayLastOnLaunch) { vm.setAutoPlayLast(it) }

        PrefRow(label = S.settingsRefreshPlaylists) {
            IntervalChip(S.prefIntervalLaunch, 0, p.syncIntervalHours, vm::setSyncInterval)
            IntervalChip(S.prefInterval6, 6, p.syncIntervalHours, vm::setSyncInterval)
            IntervalChip(S.prefInterval12, 12, p.syncIntervalHours, vm::setSyncInterval)
            IntervalChip(S.prefInterval24, 24, p.syncIntervalHours, vm::setSyncInterval)
        }

        PrefRow(label = S.settingsLanguage) {
            com.ultratv.tv.nativeapp.i18n.AppLang.entries.forEach { lang ->
                ChoiceChip(
                    label = lang.displayName,
                    on = p.language == lang.code,
                ) { vm.setLanguage(lang.code) }
            }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun IntervalChip(label: String, value: Int, current: Int, onSet: (Int) -> Unit) {
    ChoiceChip(label, on = value == current) { onSet(value) }
}

@Composable
private fun PrefRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun ChoiceChip(label: String, on: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = ButtonDefaults.shape(RoundedCornerShape(20.dp)),
        colors = if (on) ButtonDefaults.colors()
        else ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Text(label, fontSize = 14.sp) }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun SwitchRow(title: String, hint: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg = if (focused)
        com.ultratv.tv.nativeapp.ui.theme.UltraTokens.AccentSoft
    else
        androidx.compose.ui.graphics.Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { onChange(!value) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}
