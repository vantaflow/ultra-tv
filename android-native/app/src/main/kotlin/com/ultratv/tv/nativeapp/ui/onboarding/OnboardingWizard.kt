package com.ultratv.tv.nativeapp.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.config.DeviceMac
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: UserPreferencesStore,
    private val provider: ProviderRepository,
    private val deviceMac: DeviceMac,
) : ViewModel() {

    val mac: String = deviceMac.mac

    /**
     * `true` while the wizard should be shown: pref flag not flipped AND there
     * is genuinely no provider yet. Existing installs that picked up the
     * upgrade with providers already configured never see the wizard.
     */
    val show: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        prefs.flow, provider.observeProviders(),
    ) { p, ps -> !p.hasSeenOnboarding && ps.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismiss() {
        viewModelScope.launch { prefs.markOnboardingSeen() }
    }
}

/** Full-screen modal wizard. Shows the MAC, the worker URL hint and the
 *  manual entry path. Three steps, navigated with on-screen buttons. */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun OnboardingWizard(
    onOpenSettings: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val show by vm.show.collectAsState()
    if (!show) return

    var step by remember { mutableIntStateOf(0) }
    val total = 3

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 520.dp, max = 760.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                when (step) {
                    0 -> "👋 Welcome to Ultra TV"
                    1 -> "📡 Add a provider"
                    else -> "🎉 You're set"
                },
                fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text("Step ${step + 1} / $total", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

            when (step) {
                0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ultra TV is a native Android-TV IPTV client. It speaks Xtream Codes, M3U / M3U8, M3U files from local storage, and Stalker Portal.",
                        color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp,
                    )
                    Text(
                        "It uses Compose-TV for the UI, Media3 / ExoPlayer for playback, Room for the catalog. D-pad navigation works out of the box.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    )
                }

                1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Two paths:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    )
                    Text(
                        "• Settings → +Xtream / +M3U URL / +M3U file / +Stalker. Fill in the form.",
                        color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp,
                    )
                    Text(
                        "• Or open your Cloudflare Worker dashboard, paste the MAC below, add your providers there, then Settings → Sync from cloud.",
                        color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Your device MAC:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        vm.mac,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }

                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Tips you can come back to anytime:",
                        color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp,
                    )
                    Text("• ★ Default provider switching is in Settings.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• 💤 Sleep timer + 📊 Stream stats live in the player overlay.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• 🔒 Lock individual channels via Settings → Parental.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• 💾 Backup & restore exports providers + favorites + history as JSON.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• 🗓 Guide → Refresh xmltv pulls a 12 h EPG grid.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (step > 0) {
                    Button(
                        onClick = { step-- },
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) { Text("Back") }
                }
                if (step < total - 1) {
                    Button(onClick = { step++ }) { Text("Next") }
                } else {
                    Button(onClick = {
                        vm.dismiss(); onOpenSettings()
                    }) { Text("Add a provider →") }
                    Button(
                        onClick = { vm.dismiss() },
                        colors = ButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) { Text("Skip for now") }
                }
            }
        }
    }
}
