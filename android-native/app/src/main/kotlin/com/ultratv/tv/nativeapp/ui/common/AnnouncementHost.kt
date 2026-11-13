package com.ultratv.tv.nativeapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.announce.Announcement
import com.ultratv.tv.nativeapp.data.announce.AnnouncementPlan
import com.ultratv.tv.nativeapp.data.announce.AnnouncementRepository
import com.ultratv.tv.nativeapp.data.announce.ReceivedAnnouncement
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
 * montrées une à une (voir [AnnouncementPlan.next]). Chaque affichage, fermeture et usage du bouton d'action est
 * compté une fois par appareil (statistiques du tableau de bord).
 */
@HiltViewModel
class AnnouncementViewModel @Inject constructor(private val repo: AnnouncementRepository) : ViewModel() {
    private val _current = MutableStateFlow<Announcement?>(null)
    val current: StateFlow<Announcement?> = _current.asStateFlow()
    /** Recherche de mise à jour demandée par le bouton « Mettre à jour » : le dialogue de mise à jour s'affiche. */
    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()
    private var items: List<Announcement> = emptyList()
    private var audience = "tv"
    private var started = false
    private var shownId: String? = null

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

    /** L'alerte est réellement à l'écran : gardée dans « Messages » et comptée (une fois par affichage). */
    fun onShown(a: Announcement) {
        if (shownId == a.id) return
        shownId = a.id
        repo.remember(a)
        viewModelScope.launch { repo.stat(a.id, "shown") }
    }

    /** [byUser] : fermée par l'utilisateur (comptée), sinon bandeau disparu de lui-même. */
    fun dismiss(a: Announcement, byUser: Boolean = true) {
        shownThisLaunch += a.id
        if (a.level != AnnouncementPlan.URGENT) repo.saveSeen(repo.seen() + a.id)
        if (byUser) viewModelScope.launch { repo.stat(a.id, "dismissed") }
        _current.value = AnnouncementPlan.next(items, repo.seen(), shownThisLaunch)
    }

    /** Bouton d'action : seule action prévue, la mise à jour (dialogue habituel, ou « vous êtes à jour »). */
    fun act(a: Announcement, upToDate: (String) -> Unit) {
        viewModelScope.launch { repo.stat(a.id, "action") }
        dismiss(a, byUser = false)
        viewModelScope.launch {
            val info = com.ultratv.tv.nativeapp.update.UpdateChecker.checkForUpdate()
            if (info != null) _updating.value = true else upToDate(com.ultratv.tv.nativeapp.BuildConfig.VERSION_NAME)
        }
    }

    fun updateClosed() { _updating.value = false }

    fun history(): List<ReceivedAnnouncement> = repo.history()

    companion object {
        const val POLL_MS = 15 * 60_000L
        const val BANNER_MS = 15_000L
        /** Alertes urgentes déjà montrées depuis le lancement du processus (réaffichées au lancement suivant). */
        private val shownThisLaunch = mutableSetOf<String>()
    }
}

private fun accentOf(level: String): Color = when (level) {
    AnnouncementPlan.URGENT -> Ux.Accent
    "warning" -> Color(0xFFD4A72C)
    else -> Ux.Text2
}

/**
 * Affichage des alertes :
 * - « info » sans bouton : bandeau discret en haut à droite, sans focus, qui disparaît seul ;
 * - « warning », « urgent » ou alerte avec bouton : fenêtre à valider ;
 * - pendant la lecture plein écran ([suppressed]) : seules les urgentes passent, en bandeau ; les autres attendent la fin.
 */
@Composable
fun AnnouncementHost(suppressed: Boolean, vm: AnnouncementViewModel = hiltViewModel()) {
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    LaunchedEffect(Unit) { vm.start(touch) }
    val updating by vm.updating.collectAsState()
    // Fermée, l'invite libère le bouton : un nouvel appui sur « Mettre à jour » la rouvre.
    if (updating) com.ultratv.tv.nativeapp.update.UpdateDialog(onClose = vm::updateClosed)
    val a by vm.current.collectAsState()
    val shown = a ?: return
    if (suppressed && !AnnouncementPlan.showDuringPlayback(shown)) return
    LaunchedEffect(shown.id) { vm.onShown(shown) }
    // Urgente AVEC bouton pendant la lecture : le bandeau s'efface sans la clore, la fenêtre (et son bouton)
    // s'affiche à la fin de la lecture ; sinon le bouton n'était jamais proposé avant le lancement suivant.
    if (suppressed || AnnouncementPlan.isBanner(shown)) AnnouncementBanner(shown, vm, keepPending = suppressed && shown.action != null)
    else AnnouncementDialog(shown, vm)
}

@Composable
private fun AnnouncementBanner(shown: Announcement, vm: AnnouncementViewModel, keepPending: Boolean = false) {
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    var hidden by remember(shown.id) { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(shown.id) {
        delay(AnnouncementViewModel.BANNER_MS)
        if (keepPending) hidden = true else vm.dismiss(shown, byUser = false)
    }
    if (hidden) return
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    // Fermeture par l'utilisateur : Retour (télécommande) ou toucher (mobile) ; sinon il disparaît seul.
    val close = { if (keepPending) hidden = true else vm.dismiss(shown) }
    androidx.activity.compose.BackHandler(onBack = close)
    // Temps restant avant disparition, visible : le bandeau ne paraît plus « bloqué ».
    val left = remember(shown.id) { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(shown.id) { left.animateTo(0f, androidx.compose.animation.core.tween(AnnouncementViewModel.BANNER_MS.toInt(), easing = androidx.compose.animation.core.LinearEasing)) }
    val accent = accentOf(shown.level)
    Box(Modifier.fillMaxSize().padding(end = 96.design, top = 54.design), contentAlignment = Alignment.TopEnd) {
        Column(
            Modifier.width(620.design).clip(RoundedCornerShape(22.design)).background(Ux.Surface)
                .border(2.design, accent, RoundedCornerShape(22.design))
                .then(if (touch) Modifier.clickable { close() } else Modifier),
        ) {
        Row(
            Modifier.padding(horizontal = 28.design, vertical = 24.design),
            horizontalArrangement = Arrangement.spacedBy(20.design),
        ) {
            DIcon(Icons.Info, 40.design, accent, strokeWidth = 2.2f)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.design)) {
                Text(shown.title.ifBlank { D.announcementTitle }, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(shown.message, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 30.spx, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (!touch) Text(D.announcementCloseHint, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.spx, modifier = Modifier.padding(top = 4.design))
            }
        }
        Box(Modifier.fillMaxWidth(left.value).height(4.design).background(accent))
        }
    }
}

@Composable
private fun AnnouncementDialog(shown: Announcement, vm: AnnouncementViewModel) {
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    val S = com.ultratv.tv.nativeapp.i18n.LocalStrings.current
    val accent = accentOf(shown.level)
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
            Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
                if (shown.action == AnnouncementPlan.ACTION_UPDATE) {
                    PillButton(D.announcementUpdate, onClick = { vm.act(shown) { v -> Toaster.ok(S.settingsUpToDateTemplate.format(v)) } }, bg = Ux.Cta, weight = FontWeight.Bold)
                    PillButton(D.announcementOk, onClick = { vm.dismiss(shown) })
                } else {
                    PillButton(D.announcementOk, onClick = { vm.dismiss(shown) }, bg = Ux.Cta, weight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Rubrique « Messages » des Réglages : les alertes déjà reçues sur cet appareil, la plus récente d'abord. */
@Composable
fun AnnouncementHistoryPane(vm: AnnouncementViewModel = hiltViewModel()) {
    val D = com.ultratv.tv.nativeapp.i18n.LocalDs.current
    val list = remember { vm.history() }
    val fmt = remember(D.lang) { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }
    com.ultratv.tv.nativeapp.ui.design.PaneTitle(D.rubMessages)
    if (list.isEmpty()) {
        Text(D.noMessages, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.design)) {
        list.forEach { m ->
            // Focalisable (sans action) : la télécommande fait défiler la liste ligne par ligne.
            com.ultratv.tv.nativeapp.ui.design.FocusSurface(onClick = {}, shape = RoundedCornerShape(18.design), bg = Ux.Surface, ringWidth = 5.design, focusedScale = 1.0f, modifier = Modifier.fillMaxWidth()) { f ->
                Column(Modifier.padding(horizontal = 28.design, vertical = 20.design), verticalArrangement = Arrangement.spacedBy(6.design)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.design), verticalAlignment = Alignment.CenterVertically) {
                        DIcon(Icons.Info, 28.design, if (f) Ux.TextOnLight else accentOf(m.level), strokeWidth = 2.2f)
                        Text(m.title.ifBlank { D.announcementTitle }, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(fmt.format(java.util.Date(m.at)) + ", " + com.ultratv.tv.nativeapp.ui.common.EpgClock.wall(m.at), color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1)
                    }
                    Text(m.message, color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 30.spx)
                }
            }
        }
    }
}
