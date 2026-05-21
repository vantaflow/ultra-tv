package com.ultratv.tv.nativeapp.ui.onboarding

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.ultratv.tv.nativeapp.ui.theme.UltraFonts
import com.ultratv.tv.nativeapp.ui.theme.UltraTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val show: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        prefs.flow, provider.observeProviders(),
    ) { p, ps -> !p.hasSeenOnboarding && ps.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismiss() {
        viewModelScope.launch { prefs.markOnboardingSeen() }
    }
}

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
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Aurora background
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(Color(0x553A0A26), Color.Transparent),
                    center = Offset(1632f, 324f),
                    radius = 900f,
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(Color(0x442A1A55), Color.Transparent),
                    center = Offset(288f, 864f),
                    radius = 800f,
                )
            )
        )

        // Logo top-left
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 80.dp, top = 60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(UltraTokens.Accent, UltraTokens.Accent2))),
                contentAlignment = Alignment.Center,
            ) {
                Text("▶", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("ULTRA", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = UltraTokens.Fg)
                Text("TV", fontSize = 10.sp, letterSpacing = 3.sp, color = UltraTokens.Fg3)
            }
        }

        // Stepper top-right
        Row(
            Modifier.align(Alignment.TopEnd).padding(end = 80.dp, top = 78.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (s in 0 until total) {
                val isPast = s < step
                val isCurrent = s == step
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isPast || isCurrent) UltraTokens.Accent else UltraTokens.Surface2)
                        .then(
                            if (isPast || isCurrent) Modifier
                            else Modifier.border(1.dp, UltraTokens.Line2, CircleShape)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isPast) "✓" else "${s + 1}",
                        color = if (isPast || isCurrent) Color.White else UltraTokens.Fg3,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = UltraFonts.Mono,
                    )
                }
                if (s < total - 1) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(1.dp)
                            .background(if (s < step) UltraTokens.Accent else UltraTokens.Line2)
                    )
                }
            }
        }

        // Step content — centered
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (step) {
                0 -> WelcomeStep(S, onNext = { step = 1 }, onSkip = { vm.dismiss() })
                1 -> ProviderStep(
                    S = S,
                    mac = vm.mac,
                    onNext = { step = 2 },
                    onBack = { step = 0 },
                )
                else -> DoneStep(
                    S = S,
                    onOpen = { vm.dismiss(); onOpenSettings() },
                    onSkip = { vm.dismiss() },
                    onBack = { step = 1 },
                )
            }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun WelcomeStep(
    S: com.ultratv.tv.nativeapp.i18n.Strings,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        Modifier.widthIn(max = 1100.dp).padding(horizontal = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ÉTAPE 1 SUR 3 · BIENVENUE",
            color = UltraTokens.Accent,
            fontSize = 13.sp,
            letterSpacing = 2.3.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            S.wizardWelcomeTitle,
            fontFamily = UltraFonts.Serif,
            fontSize = 88.sp,
            lineHeight = 84.sp,
            letterSpacing = (-2.5).sp,
            color = UltraTokens.Fg,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            S.wizardIntro1,
            color = UltraTokens.Fg2,
            fontSize = 20.sp,
            fontWeight = FontWeight.Light,
        )
        Spacer(Modifier.height(50.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.colors(
                    containerColor = UltraTokens.CtaBg,
                    contentColor = UltraTokens.CtaFgOnCta,
                ),
                modifier = Modifier.border(3.dp, UltraTokens.Accent, RoundedCornerShape(14.dp)),
            ) { Text(S.wizardNext + "  →", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onSkip,
                colors = ButtonDefaults.colors(containerColor = UltraTokens.Surface2),
            ) { Text(S.wizardSkip, color = UltraTokens.Fg2) }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun ProviderStep(
    S: com.ultratv.tv.nativeapp.i18n.Strings,
    mac: String,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.widthIn(max = 1280.dp).padding(horizontal = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ÉTAPE 2 SUR 3 · SOURCES",
            color = UltraTokens.Accent,
            fontSize = 13.sp,
            letterSpacing = 2.3.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            S.wizardAddProviderTitle,
            fontFamily = UltraFonts.Serif,
            fontSize = 56.sp,
            lineHeight = 56.sp,
            letterSpacing = (-1.5).sp,
            color = UltraTokens.Fg,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            S.wizardTwoPaths,
            color = UltraTokens.Fg3,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Option A: Cloud (recommended)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(UltraTokens.AccentTint, Color(0x05FF3A2F))
                        )
                    )
                    .border(1.dp, Color(0x40FF3A2F), RoundedCornerShape(20.dp))
                    .padding(28.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(UltraTokens.Accent)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "RECOMMANDÉ",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "OPTION A · CLOUD",
                    color = UltraTokens.Accent,
                    fontSize = 11.sp,
                    letterSpacing = 2.3.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    S.wizardPathCloud,
                    fontFamily = UltraFonts.Serif,
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
                    color = UltraTokens.Fg,
                )
                Spacer(Modifier.height(20.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x66000000))
                        .border(1.dp, UltraTokens.Line2, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                ) {
                    Text(
                        S.onboardingMacLabel.uppercase(),
                        color = UltraTokens.Fg3,
                        fontSize = 10.sp,
                        letterSpacing = 2.3.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        mac,
                        fontFamily = UltraFonts.Mono,
                        fontSize = 28.sp,
                        letterSpacing = 1.2.sp,
                        color = UltraTokens.Fg,
                    )
                }
            }
            // Option B: Manual
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(UltraTokens.Surface1)
                    .border(1.dp, UltraTokens.Line, RoundedCornerShape(20.dp))
                    .padding(28.dp),
            ) {
                Text(
                    "OPTION B · MANUEL",
                    color = UltraTokens.Fg3,
                    fontSize = 11.sp,
                    letterSpacing = 2.3.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    S.wizardPathManual,
                    fontFamily = UltraFonts.Serif,
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
                    color = UltraTokens.Fg,
                )
                Spacer(Modifier.height(16.dp))
                listOf(
                    "+ Xtream Codes" to "URL · user · password",
                    "+ M3U URL" to "Lien direct .m3u",
                    "+ M3U Fichier" to "Fichier local sur USB",
                    "+ Stalker Portal" to "MAC + portail",
                ).forEach { (label, desc) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(UltraTokens.Surface2)
                            .border(1.dp, UltraTokens.Line2, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    ) {
                        Text(label, color = UltraTokens.Fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(desc, color = UltraTokens.Fg4, fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.colors(containerColor = Color.Transparent),
            ) { Text("← " + S.wizardBack, color = UltraTokens.Fg3) }
            Button(
                onClick = onNext,
                colors = ButtonDefaults.colors(
                    containerColor = UltraTokens.CtaBg,
                    contentColor = UltraTokens.CtaFgOnCta,
                ),
                modifier = Modifier.border(3.dp, UltraTokens.Accent, RoundedCornerShape(12.dp)),
            ) { Text(S.wizardNext + "  →", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
private fun DoneStep(
    S: com.ultratv.tv.nativeapp.i18n.Strings,
    onOpen: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.widthIn(max = 1100.dp).padding(horizontal = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "ÉTAPE 3 SUR 3 · PRÊT",
            color = UltraTokens.Accent,
            fontSize = 13.sp,
            letterSpacing = 2.3.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            S.wizardDoneTitle,
            fontFamily = UltraFonts.Serif,
            fontSize = 64.sp,
            lineHeight = 60.sp,
            letterSpacing = (-1.8).sp,
            color = UltraTokens.Fg,
        )
        Spacer(Modifier.height(18.dp))
        Text(S.wizardTipsHead, color = UltraTokens.Fg2, fontSize = 17.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(28.dp))
        listOf(S.wizardTipDefault, S.wizardTipSleep, S.wizardTipLock, S.wizardTipBackup, S.wizardTipGuide).forEach { tip ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(UltraTokens.Accent, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(tip, color = UltraTokens.Fg2, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(40.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.colors(containerColor = Color.Transparent),
            ) { Text("← " + S.wizardBack, color = UltraTokens.Fg3) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.colors(containerColor = UltraTokens.Surface2),
                ) { Text(S.wizardSkip, color = UltraTokens.Fg2) }
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.colors(
                        containerColor = UltraTokens.Accent,
                        contentColor = Color.White,
                    ),
                ) { Text(S.wizardAddProviderCta + "  →", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
