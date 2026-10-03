package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Affiche le code d'appairage à saisir dans le tableau de bord. Textes en
 * anglais comme les messages d'état du ViewModel (non localisés pour l'instant).
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun CloudPairingDialog(state: PairingUi, onCancel: () -> Unit, onRetry: () -> Unit) {
    if (state is PairingUi.Idle) return
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 480.dp, max = 720.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Pair with your dashboard", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            when (state) {
                PairingUi.Requesting -> Text("Requesting a code…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                is PairingUi.ShowCode -> {
                    Text(
                        "1. On a phone or computer, open ${state.workerBase} and sign in.\n2. Enter this code in the \"Appairer un appareil\" panel:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    )
                    Text(
                        state.code,
                        fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("Waiting for confirmation… the code expires in 10 minutes.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                is PairingUi.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                PairingUi.Idle -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (state is PairingUi.Failed) Button(onClick = onRetry) { Text("Try again", fontSize = 15.sp) }
                Button(onClick = onCancel) { Text(if (state is PairingUi.Failed) "Close" else "Cancel", fontSize = 15.sp) }
            }
        }
    }
}
