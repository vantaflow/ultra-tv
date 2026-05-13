package com.ultratv.tv.nativeapp.ui.parental

import androidx.compose.foundation.background
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
 * Reusable parental-PIN prompt. Invokes [onUnlocked] when the user enters the
 * correct PIN, [onCancel] otherwise. Falls through (calls onUnlocked) when no
 * PIN is configured — locking content without a PIN to unlock it would brick
 * playback.
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
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
    val resolvedTitle = title ?: S.parentalLockedTitle
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(resolvedTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(
                S.parentalEnterPin,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(12.dp),
            ) {
                BasicTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter { c -> c.isDigit() }.take(4)
                        error = false
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        if (pin.isEmpty()) Text("PIN", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
                        inner()
                    },
                )
            }
            if (error) Text(S.parentalWrongPin, color = androidx.compose.ui.graphics.Color(0xFFFF6B6B), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            if (vm.check(pin)) onUnlocked()
                            else { error = true; pin = "" }
                        }
                    },
                    enabled = pin.length == 4,
                ) { Text(S.parentalUnlock) }
                Button(onClick = onCancel) { Text(S.cancel) }
            }
        }
    }
}
