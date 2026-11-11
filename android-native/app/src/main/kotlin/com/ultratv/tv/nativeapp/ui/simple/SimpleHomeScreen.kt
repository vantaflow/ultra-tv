package com.ultratv.tv.nativeapp.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.repo.TitleCleaner
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.SimpleStrings
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.parental.PinPromptDialog
import kotlinx.coroutines.launch

/**
 * Accueil du mode simple : six GRANDES tuiles (icône 68 px, texte 52 px, anneau de focus de 10 px), sans rangée
 * défilante, sans image à charger (icônes vectorielles) et sans animation autre que l'échelle du focus déjà utilisée
 * partout. Remplace l'accueil habituel ; Retour des autres écrans y ramène toujours (destination de départ).
 *
 * Focus : [ScreenFocusHost] pose le focus sur le premier élément focalisable, la tuile « Télé en direct » ; la grille
 * garde les six tuiles en permanence (même forme avant et après l'arrivée de la reprise) pour ne jamais perdre le focus.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimpleHomeScreen(
    onNavigate: (String) -> Unit,
    onPlay: (url: String, title: String) -> Unit,
    onGoSettings: () -> Unit,
    vm: SimpleHomeViewModel = hiltViewModel(),
) {
    val S = SimpleStrings(LocalDs.current.lang)
    val hasSource by vm.hasSource.collectAsStateWithLifecycle()
    val resume by vm.resume.collectAsStateWithLifecycle()
    val lastLive by vm.lastLive.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val multiProfiles by vm.multipleProfiles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pinFor by remember { mutableStateOf<WatchHistoryEntity?>(null) }

    val playLive: (WatchHistoryEntity) -> Unit = { h -> scope.launch { vm.prepareLastLive(h); onPlay(h.streamUrl, h.title) } }
    val onTile: (SimpleTile) -> Unit = { tile ->
        when (tile) {
            SimpleTile.LIVE -> when (val a = SimpleHome.liveAction(lastLive, locked)) {
                is SimpleHome.LiveAction.PlayLast -> if (a.needsPin) pinFor = a.item else playLive(a.item)
                SimpleHome.LiveAction.ShowList -> onNavigate(Routes.SIMPLE_CHANNELS)
            }
            SimpleTile.RESUME -> when (val a = SimpleHome.resumeAction(resume)) {
                is SimpleHome.ResumeAction.Play -> { vm.prepareResume(a.item); onPlay(a.item.streamUrl, a.item.title) }
                is SimpleHome.ResumeAction.Browse -> onNavigate(a.route)
            }
            else -> tile.route?.let(onNavigate)
        }
    }

    val touch = LocalTouch.current
    val columns = SimpleHome.columnsFor(touch, LocalUiWidthDp.current)
    val rows = SimpleHome.rows(SimpleHome.tiles(), columns)

    Column(
        Modifier.fillMaxSize().background(Ux.Bg).padding(horizontal = 72.design, vertical = 48.design),
        verticalArrangement = Arrangement.spacedBy(28.design),
    ) {
        Text(S.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 56.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (hasSource == false) Text(S.noSource, color = Ux.Text2, fontFamily = Manrope, fontSize = 30.spx, maxLines = 3)

        // Grille : groupe de focus qui se souvient de la dernière tuile (retour d'un écran, d'une fenêtre).
        // Focus explicite : venu du menu latéral (navigation « Accueil »), l'écran hôte ne pose pas le focus — et ce menu
        // n'existe plus ici (rail masqué), donc sans cette demande le focus serait perdu.
        val gridFocus = remember { FocusRequester() }
        var gridHasFocus by remember { mutableStateOf(false) }
        RequestInitialFocus(gridFocus, hasFocus = { gridHasFocus })
        val gridModifier = if (touch) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier.weight(1f)
        Column(gridModifier.onFocusChanged { gridHasFocus = it.hasFocus }.focusRestorer().focusRequester(gridFocus).focusGroup(), verticalArrangement = Arrangement.spacedBy(28.design)) {
            rows.forEach { line ->
                Row(
                    // Tactile : hauteur fixe (la grille défile) ; TV : les lignes se partagent la hauteur.
                    (if (touch) Modifier.height(220.design) else Modifier.weight(1f)).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(28.design),
                ) {
                    line.forEach { tile ->
                        key(tile) {
                            val (label, hint) = tileTexts(S, tile, resume)
                            BigTile(label, hint, tilePath(tile), dim = tile == SimpleTile.RESUME && resume == null, onClick = { onTile(tile) }, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    // Dernière ligne plus courte : cases vides pour garder des tuiles de même taille.
                    repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // Accès discrets en bas : les Réglages (sortie du mode simple comprise) et, s'il y en a plusieurs, le changement de profil.
        Row(horizontalArrangement = Arrangement.spacedBy(20.design)) {
            PillButton(S.settings, onGoSettings, heightPx = 72, hPadPx = 36, fontPx = 28, bg = Ux.Surface2, iconPath = Icons.Settings)
            if (multiProfiles) PillButton(S.switchProfile, { vm.requestProfileSwitch() }, heightPx = 72, hPadPx = 36, fontPx = 28, bg = Ux.Surface2, iconPath = Icons.Account)
        }
    }

    pinFor?.let { h ->
        PinPromptDialog(title = h.title, onUnlocked = { pinFor = null; playLive(h) }, onCancel = { pinFor = null })
    }
}

private fun tilePath(t: SimpleTile): String = when (t) {
    SimpleTile.LIVE -> Icons.Live
    SimpleTile.RESUME -> Icons.Play
    SimpleTile.MOVIES -> Icons.Movies
    SimpleTile.SERIES -> Icons.Series
    SimpleTile.FAVORITES -> Icons.Heart
    SimpleTile.SEARCH -> Icons.Search
}

private fun tileTexts(S: SimpleStrings, t: SimpleTile, resume: WatchHistoryEntity?): Pair<String, String> = when (t) {
    SimpleTile.LIVE -> S.tileLive to S.tileLiveHint
    SimpleTile.RESUME -> S.tileResume to (resume?.let { TitleCleaner.historyTitle(it.title) } ?: S.tileResumeNothing)
    SimpleTile.MOVIES -> S.tileMovies to S.tileMoviesHint
    SimpleTile.SERIES -> S.tileSeries to S.tileSeriesHint
    SimpleTile.FAVORITES -> S.tileFavorites to S.tileFavoritesHint
    SimpleTile.SEARCH -> S.tileSearch to S.tileSearchHint
}

/**
 * Grande tuile : pastille d'accent (icône dessus, toujours très contrastée), libellé 52 px gras, aide 30 px.
 * Au focus : fond blanc / texte noir, anneau accent de 10 px (FocusSurface) — lisible à 3 mètres.
 */
@Composable
private fun BigTile(label: String, hint: String, iconPath: String, dim: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(40.design),
        bg = Ux.Surface2,
        ringWidth = 10.design,
        focusedScale = 1.03f,
        modifier = modifier,
    ) { f ->
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.design, vertical = 12.design),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(120.design).clip(CircleShape).background(if (dim) Ux.Muted2 else Ux.Accent), contentAlignment = Alignment.Center) {
                DIcon(iconPath, 68.design, if (dim) Ux.TextOnLight else Ux.OnAccent, strokeWidth = 2.4f, fill = iconPath == Icons.Play)
            }
            Spacer(Modifier.height(12.design))
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 52.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.design))
            Text(hint, color = if (f) Ux.OnFocus2 else Ux.Text2, fontFamily = Manrope, fontSize = 30.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().height(76.designT))
        }
    }
}
