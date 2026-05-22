package com.ultratv.tv.nativeapp.update

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.ui.theme.UltraFonts
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens
import com.ultratv.tv.nativeapp.ui.theme.ultraButtonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Self-update prompt — listens to [UpdateChecker.state] and shows a small
 * dialog when a newer GitHub release is detected. Tapping "Mettre à jour"
 * downloads the APK from the release and triggers PackageInstaller; the
 * system asks the user to confirm the first time.
 */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun UpdateDialog() {
    val info by UpdateChecker.state.collectAsState()
    val update = info ?: return

    var dismissed by remember(update.tag) { mutableStateOf(false) }
    if (dismissed) return

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember(update.tag) { mutableStateOf(false) }
    var progress by remember(update.tag) { mutableFloatStateOf(0f) }
    var error by remember(update.tag) { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF0000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(min = 520.dp, max = 640.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF15151B))      // opaque, sits on top of the scrim
                .border(1.dp, UltraTokens.Accent, RoundedCornerShape(20.dp))
                .padding(28.dp),
        ) {
            Text(
                "MISE À JOUR DISPONIBLE",
                color = UltraTokens.Accent,
                fontSize = 11.sp,
                letterSpacing = 2.3.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Ultra TV ${update.versionName}",
                color = UltraTokens.Fg,
                fontSize = 32.sp,
                fontFamily = UltraFonts.Serif,
                lineHeight = 32.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                update.notes.ifBlank { "Une nouvelle version est disponible." },
                color = UltraTokens.Fg2,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )

            if (downloading) {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(UltraTokens.Surface2),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(UltraTokens.Accent),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Téléchargement ${(progress * 100).toInt()}%",
                    color = UltraTokens.Fg3,
                    fontSize = 12.sp,
                    fontFamily = UltraFonts.Mono,
                )
            }

            if (error != null) {
                Spacer(Modifier.height(14.dp))
                Text(error!!, color = UltraTokens.Live, fontSize = 13.sp)
            }

            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        downloading = true
                        error = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    UpdateChecker.downloadAndInstall(ctx, update) { p -> progress = p }
                                }
                            }.onFailure {
                                error = it.message ?: it.javaClass.simpleName
                                downloading = false
                            }
                        }
                    },
                    enabled = !downloading,
                    colors = ultraButtonColors(
                        containerColor = UltraTokens.Accent,
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = UltraTokens.Accent,
                    ),
                ) {
                    Text(
                        if (downloading) "Téléchargement…" else "Mettre à jour",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Button(
                    onClick = { dismissed = true },
                    enabled = !downloading,
                    colors = ultraButtonColors(
                        containerColor = UltraTokens.Surface2,
                        contentColor = UltraTokens.Fg2,
                    ),
                ) {
                    Text("Plus tard", fontSize = 14.sp)
                }
            }
        }
    }
}
