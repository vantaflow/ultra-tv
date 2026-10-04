package com.ultratv.tv.nativeapp.ui.parental

import com.ultratv.tv.nativeapp.ui.common.leaveOnVerticalDpad
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Invite de code parental (maquette Parental.dc.html). Appelle [onUnlocked] si le code est correct, [onCancel] sinon.
 * Sans code configuré on déverrouille directement : verrouiller sans moyen de déverrouiller bloquerait la lecture.
 */
@Composable
fun PinPromptDialog(
    title: String? = null,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
    vm: ParentalViewModel = hiltViewModel(),
) {
    val pinSet by vm.pinSet.collectAsState()
    LaunchedEffect(pinSet) { if (!pinSet) onUnlocked() }
    if (!pinSet) return
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    var attempt by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    PinPad(
        title = D.lockedTitle, subtitle = D.lockedSubtitle.format(title.orEmpty()),
        onComplete = { pin -> scope.launch { if (vm.check(pin)) onUnlocked() else { error = S.parentalWrongPin; attempt++ } } },
        onCancel = onCancel, error = error, resetKey = attempt,
    )
}
