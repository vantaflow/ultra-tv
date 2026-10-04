package com.ultratv.tv.nativeapp.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.profile.ProfileColors
import com.ultratv.tv.nativeapp.data.profile.ProfileEntity
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.parental.PinPad
import kotlinx.coroutines.launch

/** Avatar carré arrondi de la maquette (200 px, initiale Sora 88). */
@Composable
fun ProfileAvatar(initial: String, color: Int, size: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(size.design).clip(RoundedCornerShape((size * 24 / 100).design)).background(Color(color)), contentAlignment = Alignment.Center) {
        Text(initial, color = Color.White, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = (size * 44 / 100).spx, maxLines = 1)
    }
}

/**
 * « Qui regarde ? » (maquette Profils.dc.html), sans état métier pour rester testable : le focus initial va sur
 * [focusId] (dernier profil). [onPick] est appelé pour un profil, [onAdd] pour « + Ajouter ».
 */
@Composable
fun WhoIsWatchingContent(
    profiles: List<ProfileEntity>,
    focusId: Long?,
    onPick: (ProfileEntity) -> Unit,
    onAdd: (() -> Unit)?,
    onManage: (() -> Unit)? = null,
) {
    val S = ProfileStrings(LocalDs.current.lang)
    val focus = remember { FocusRequester() }
    LaunchedEffect(profiles.size, focusId) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().background(Ux.Bg).padding(horizontal = 96.design, vertical = 54.design),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(64.design, Alignment.CenterVertically),
    ) {
        Text(S.whoWatching, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 72.spx, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(48.design)) {
            val target = profiles.firstOrNull { it.id == focusId } ?: profiles.firstOrNull()
            profiles.forEach { p ->
                ProfileTile(
                    initial = p.initial, color = p.color, label = p.name,
                    hint = when { p.pinHash != null -> S.protectedHint; p.isKids -> S.kidsHint; p.id == focusId -> S.lastHint; else -> "" },
                    modifier = Modifier.testTag("profile-${p.id}").then(if (p.id == target?.id) Modifier.focusRequester(focus) else Modifier),
                    onClick = { onPick(p) },
                )
            }
            if (onAdd != null) ProfileTile("+", 0xFF26262D.toInt(), S.add, "", Modifier.testTag("profile-add"), onAdd)
        }
        if (onManage != null) PillButton(S.manage, onManage, heightPx = 64, hPadPx = 32, fontPx = 22, bg = Ux.Surface)
    }
}

@Composable
private fun ProfileTile(initial: String, color: Int, label: String, hint: String, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(48.design), bg = Color.Transparent, focusedBg = Color.Transparent, focusedScale = 1.08f, ringWidth = 6.design, modifier = modifier.width(240.design)) { f ->
        Column(Modifier.fillMaxWidth().padding(bottom = 12.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.design)) {
            ProfileAvatar(initial, color, 200)
            Text(label, color = if (f) Color.White else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 28.spx, maxLines = 1, textAlign = TextAlign.Center)
            Text(hint, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
        }
    }
}

/** Écran complet : sélection, PIN du profil protégé (PinPad), création rapide. */
@Composable
fun WhoIsWatchingScreen(vm: ProfileViewModel = hiltViewModel(), onManage: () -> Unit = {}) {
    val profiles by vm.profiles.collectAsState()
    val current by vm.current.collectAsState()
    val S = ProfileStrings(LocalDs.current.lang)
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    WhoIsWatchingContent(
        profiles = profiles, focusId = current?.id,
        onPick = { p -> if (p.pinHash != null) { error = null; pinFor = p } else vm.select(p.id) },
        onAdd = if (profiles.size < com.ultratv.tv.nativeapp.data.profile.ProfileRules.MAX_PROFILES) ({ adding = true }) else null,
        onManage = null,
    )
    pinFor?.let { p ->
        PinPad(
            title = S.pinTitle, subtitle = S.pinSubtitle(p.name),
            onComplete = { pin -> scope.launch { if (vm.verifyPin(p.id, pin)) { pinFor = null; vm.select(p.id) } else { error = S.wrongPin; attempt++ } } },
            onCancel = { pinFor = null }, error = error, resetKey = attempt,
        )
    }
    if (adding) ProfileEditDialog(null, vm, onDismiss = { adding = false })
}
