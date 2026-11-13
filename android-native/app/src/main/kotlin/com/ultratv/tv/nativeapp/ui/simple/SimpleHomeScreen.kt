package com.ultratv.tv.nativeapp.ui.simple

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.repo.TitleCleaner
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.SimpleStrings
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp
import com.ultratv.tv.nativeapp.ui.common.EpgClock
import com.ultratv.tv.nativeapp.i18n.greetMorning
import com.ultratv.tv.nativeapp.i18n.greetAfternoon
import com.ultratv.tv.nativeapp.i18n.greetEvening
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
 * Accueil du mode simple (maquette « Mode simple ») : date et salutation au prénom du profil, grande horloge, une
 * GRANDE carte « Reprendre » (titre, progression, temps restant), puis cinq grandes tuiles (Télé, Films, Séries,
 * Favoris, Rechercher) et, en bas, les seules touches utiles. Aucune rangée défilante ; une seule image (l'affiche de
 * la reprise). Remplace l'accueil habituel ; Retour des autres écrans y ramène toujours.
 *
 * Focus : la carte « Reprendre » d'abord ; la forme de l'écran ne change jamais (avec ou sans reprise) pour ne pas
 * perdre le focus quand la reprise arrive.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SimpleHomeScreen(
    onNavigate: (String) -> Unit,
    onPlay: (url: String, title: String) -> Unit,
    onGoSettings: () -> Unit,
    vm: SimpleHomeViewModel = hiltViewModel(),
) {
    val D = LocalDs.current
    val S = SimpleStrings(D.lang)
    val hasSource by vm.hasSource.collectAsStateWithLifecycle()
    val resume by vm.resume.collectAsStateWithLifecycle()
    val lastLive by vm.lastLive.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val multiProfiles by vm.multipleProfiles.collectAsStateWithLifecycle()
    val profileName by vm.profileName.collectAsStateWithLifecycle()
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
    // Horloge : relue toutes les 20 s (seuls l'heure et la date se recomposent).
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(20_000) } }
    val hour = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)
    val greet = when { hour < 12 -> D.greetMorning; hour < 18 -> D.greetAfternoon; else -> D.greetEvening }
    val dateText = remember(now / 60_000, D.lang) {
        val loc = java.util.Locale.forLanguageTag(D.lang.code)
        java.text.SimpleDateFormat("EEEE d MMMM", loc).format(java.util.Date(now)).replaceFirstChar { it.titlecase(loc) }
    }

    Column(
        Modifier.fillMaxSize().background(Ux.Bg).then(if (touch) Modifier.padding(16.design) else Modifier.padding(start = 96.design, end = 96.design, top = 64.design, bottom = 48.design)),
        verticalArrangement = Arrangement.spacedBy(if (touch) 24.design else 40.design),
    ) {
        // En-tête : date, « Bonsoir Mamie », grande horloge.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.design)) {
                Text(dateText, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 32.spx, maxLines = 1)
                // Prénom seulement s'il y a plusieurs profils (le profil unique s'appelle « Principal »).
                Text(listOfNotNull(greet, profileName.takeIf { multiProfiles }).joinToString(" "), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = if (touch) 56.spx else 76.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(EpgClock.hm(now), color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = if (touch) 56.spx else 92.spx, maxLines = 1)
        }
        if (hasSource == false) Text(S.noSource, color = Ux.Text2, fontFamily = Manrope, fontSize = 30.spx, maxLines = 3)

        // Carte « Reprendre » + tuiles : un groupe de focus qui se souvient du dernier élément (retour d'un écran).
        // Focus explicite : venu du menu latéral, l'écran hôte ne pose pas le focus (et le menu est masqué ici).
        val gridFocus = remember { FocusRequester() }
        var gridHasFocus by remember { mutableStateOf(false) }
        RequestInitialFocus(gridFocus, hasFocus = { gridHasFocus })
        val tiles = SimpleHome.mainTiles()
        Column(
            (if (touch) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier.weight(1f))
                .onFocusChanged { gridHasFocus = it.hasFocus }.focusRestorer().focusRequester(gridFocus).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(if (touch) 20.design else 36.design),
        ) {
            ResumeCard(S, D, resume, onClick = { onTile(SimpleTile.RESUME) }, modifier = Modifier.fillMaxWidth().height(if (touch) 220.designT else 280.designT))
            val columns = SimpleHome.mainColumnsFor(touch, LocalUiWidthDp.current)
            SimpleHome.rows(tiles, columns).forEach { line ->
                Row(
                    (if (touch) Modifier.height(240.designT) else Modifier.weight(1f)).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (touch) 20.design else 32.design),
                ) {
                    line.forEach { tile ->
                        key(tile) {
                            val (label, hint) = tileTexts(S, tile)
                            BigTile(label, hint, tilePath(tile), onClick = { onTile(tile) }, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // Pied : les seules touches utiles (TV), puis Réglages (sortie du mode simple) et changement de profil.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(40.design)) {
            if (!touch) {
                KeyHintBig("OK", S.keyOpen)
                KeyHintBig("↩", S.keyBack)
            }
            Spacer(Modifier.weight(1f))
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

private fun tileTexts(S: SimpleStrings, t: SimpleTile): Pair<String, String> = when (t) {
    SimpleTile.LIVE -> S.tileLive to S.tileLiveHint
    SimpleTile.RESUME -> S.tileResume to S.tileResumeHint
    SimpleTile.MOVIES -> S.tileMovies to S.tileMoviesHint
    SimpleTile.SERIES -> S.tileSeries to S.tileSeriesHint
    SimpleTile.FAVORITES -> S.tileFavorites to S.tileFavoritesHint
    SimpleTile.SEARCH -> S.tileSearch to S.tileSearchHint
}

/**
 * Carte « Reprendre » : affiche (ou icône), pastille accent, titre en grand, barre de progression et temps restant, gros
 * bouton lecture. Rien à reprendre : même carte, avec l'invitation à choisir un film (la forme de l'écran ne change pas).
 */
@Composable
private fun ResumeCard(S: SimpleStrings, D: com.ultratv.tv.nativeapp.i18n.DesignStrings, resume: WatchHistoryEntity?, onClick: () -> Unit, modifier: Modifier) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(40.design), bg = Ux.Surface2, ringWidth = 10.design, focusedScale = 1.02f, modifier = modifier) { f ->
        Row(Modifier.fillMaxSize().padding(28.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(44.design)) {
            Box(Modifier.fillMaxHeight().width(400.design).clip(RoundedCornerShape(26.design)).background(Ux.Surface), contentAlignment = Alignment.Center) {
                if (resume?.poster != null) com.ultratv.tv.nativeapp.ui.design.PosterImage(resume.poster, resume.title, Modifier.fillMaxSize(), radius = 26)
                else DIcon(Icons.Play, 96.design, Ux.Text3, fill = true)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.design)) {
                Text(
                    S.tileResume.uppercase(), color = Ux.OnAccent, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 24.spx, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(999.design)).background(Ux.Accent).padding(horizontal = 20.design, vertical = 6.design),
                )
                Text(
                    resume?.let { TitleCleaner.historyTitle(it.title) } ?: S.tileResumeNothing,
                    color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold,
                    fontSize = if (resume != null) 56.spx else 40.spx, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val frac = SimpleHome.progressOf(resume)
                if (resume != null && frac != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.design)) {
                    Box(Modifier.weight(1f).height(14.design).clip(RoundedCornerShape(7.design)).background(if (f) Ux.OnFocus2.copy(alpha = 0.25f) else Ux.Line)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(7.design)).background(Ux.Accent))
                    }
                    SimpleHome.minutesLeft(resume)?.let { m -> Text(D.remainingMin(m), color = if (f) Ux.OnFocus2 else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 30.spx, maxLines = 1) }
                }
            }
            Box(Modifier.size(132.design).clip(CircleShape).background(if (f) Ux.Accent else Ux.Text), contentAlignment = Alignment.Center) {
                DIcon(Icons.Play, 56.design, if (f) Ux.OnAccent else Ux.Bg, fill = true)
            }
        }
    }
}

/**
 * Grande tuile : icône en haut à gauche, libellé 48 px et aide 28 px en bas. Au focus : fond clair / texte foncé, anneau
 * accent de 10 px (FocusSurface) — lisible à 3 mètres.
 */
@Composable
private fun BigTile(label: String, hint: String, iconPath: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(36.design), bg = Ux.Surface2, ringWidth = 10.design, focusedScale = 1.03f, modifier = modifier) { f ->
        Column(Modifier.fillMaxSize().padding(horizontal = 32.design, vertical = 30.design), verticalArrangement = Arrangement.SpaceBetween) {
            DIcon(iconPath, 72.design, if (f) Ux.TextOnLight else Ux.Text, strokeWidth = 2.0f)
            Column(verticalArrangement = Arrangement.spacedBy(6.design)) {
                Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(hint, color = if (f) Ux.OnFocus2 else Ux.Text2, fontFamily = Manrope, fontSize = 25.spx, lineHeight = 31.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Rappel de touche en grand : « [OK] Ouvrir ». */
@Composable
internal fun KeyHintBig(key: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.design)) {
        Text(key, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 28.spx, maxLines = 1,
            modifier = Modifier.border(3.design, Ux.Text3, RoundedCornerShape(12.design)).padding(horizontal = 14.design, vertical = 2.design))
        Text(label, color = Ux.Text2, fontFamily = Manrope, fontSize = 30.spx, maxLines = 1)
    }
}
