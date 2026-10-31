package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.announce.Announcement
import com.ultratv.tv.nativeapp.data.announce.AnnouncementPlan
import com.ultratv.tv.nativeapp.data.announce.AnnouncementRepository
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Alertes de l'administrateur : lues au lancement puis toutes les 15 minutes tant que l'appli est au premier plan,
 * montrées une à une (voir [AnnouncementPlan.next]).
 */
@HiltViewModel
class AnnouncementViewModel @Inject constructor(private val repo: AnnouncementRepository) : ViewModel() {
    private val _current = MutableStateFlow<Announcement?>(null)
    val current: StateFlow<Announcement?> = _current.asStateFlow()
    private var items: List<Announcement> = emptyList()
    private var audience = "tv"
    private var started = false

    fun start(touch: Boolean) {
        if (started) return
        started = true
        audience = if (touch) "mobile" else "tv"
        viewModelScope.launch {
            while (true) {
                if (AppForeground.visible) refresh()
                delay(POLL_MS)
            }
        }
    }

    private suspend fun refresh() {
        val got = repo.fetch(audience)
        if (got != null) {
            items = got
            repo.saveSeen(AnnouncementPlan.pruneSeen(repo.seen(), got, true))
        }
        if (_current.value == null) _current.value = AnnouncementPlan.next(items, repo.seen(), shownThisLaunch)
    }

    fun dismiss(a: Announcement) {
        shownThisLaunch += a.id
        if (a.level != AnnouncementPlan.URGENT) repo.saveSeen(repo.seen() + a.id)
        _current.value = AnnouncementPlan.next(items, repo.seen(), shownThisLaunch)
    }

    companion object {
        const val POLL_MS = 15 * 60_000L
        /** Alertes urgentes déjà montrées depuis le lancement du processus (réaffichées au lancement suivant). */
        private val shownThisLaunch = mutableSetOf<String>()
    }
}

/** Fenêtre d'alerte, par-dessus l'écran ; jamais pendant la lecture plein écran ([suppressed]) : elle attend sa fin. */
@Composable
fun AnnouncementHost(suppressed: Boolean, vm: AnnouncementViewModel = hiltViewModel()) {
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.start(touch) }
    val a by vm.current.collectAsState()
    val shown = a ?: return
    if (suppressed) return
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    val accent = when (shown.level) { AnnouncementPlan.URGENT -> Ux.Accent; "warning" -> Color(0xFFD4A72C); else -> Ux.Text2 }
    ModalFocusScope(onBack = { vm.dismiss(shown) }, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier.widthIn(min = 560.design, max = 900.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(44.design),
            verticalArrangement = Arrangement.spacedBy(20.design),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
                DIcon(Icons.Info, 40.design, accent, strokeWidth = 2.2f)
                Text(shown.title.ifBlank { D.announcementTitle }, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 2)
            }
            Text(shown.message, color = Ux.Text2, fontFamily = Manrope, fontSize = 26.spx, lineHeight = 36.spx)
            PillButton(D.announcementOk, onClick = { vm.dismiss(shown) }, bg = Ux.Cta, weight = FontWeight.Bold)
        }
    }
}
