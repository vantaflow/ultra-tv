package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design

/** Interrupteur de la maquette : piste 64×36, bouton 28 ; actif = accent (noir si la ligne est focalisée), inactif = #3F3F46 (#D4D4D8 si focalisée). */
@Composable
fun Switch(on: Boolean, inverted: Boolean = false) {
    val onDarkFocus = Ux.Cta == Color.White   // focus blanc (thème sombre) ; encre en thème clair
    val track = when {
        on -> if (inverted && onDarkFocus) Color(0xFF0A0A0C) else Ux.Accent
        inverted -> if (onDarkFocus) Color(0xFFD4D4D8) else Color(0xFF52525B)
        else -> Ux.Line
    }
    Box(Modifier.width(64.design).height(36.design).clip(RoundedCornerShape(18.design)).background(track)) {
        Box(Modifier.padding(start = if (on) 32.design else 4.design, top = 4.design).width(28.design).height(28.design).clip(RoundedCornerShape(14.design)).background(Color.White))
    }
}

private const val CHEVRON = "M9 6l6 6-6 6"

/** Vrai dans une [SettingsCard] : les lignes y sont à plat (sans coins ni anneau), séparées par un filet, le focus remplit la ligne. */
val LocalSettingsCard = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Carte arrondie des Réglages (maquettes Reglages) : un empilement de lignes (libellé + aide à gauche, valeur ou interrupteur
 * à droite). Le fond de la carte est la couleur du filet : 1 dp d'écart entre les lignes le laisse voir comme séparateur.
 */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val divider = Ux.Line.copy(alpha = if (Ux.palette.light) 0.8f else 0.5f)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.design)).background(divider),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalSettingsCard provides true) { content() }
    }
}

/** Forme d'une ligne de réglage : à plat dans une carte, arrondie sinon. */
@Composable
fun settingsRowShape(): androidx.compose.ui.graphics.Shape =
    if (LocalSettingsCard.current) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(18.design)

/** Anneau de focus d'une ligne : aucun dans une carte (le fond suffit), 5 px sinon. */
@Composable
fun settingsRowRing(): androidx.compose.ui.unit.Dp =
    if (LocalSettingsCard.current) androidx.compose.ui.unit.Dp(0f) else 5.design

/** Ligne de réglage « valeur + chevron » (maquette Réglages) : libellé + aide à gauche, valeur et chevron à droite ; focus = couleurs de focus. */
@Composable
fun PrefRow(label: String, value: String, modifier: Modifier = Modifier, hint: String? = null, onClick: () -> Unit) {
    // La fenêtre de choix qu'ouvre la ligne lui rend le focus en se fermant (FocusSurface se signale comme ouvreur).
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    FocusSurface(
        onClick = onClick,
        shape = settingsRowShape(), bg = Ux.SurfaceDeep, ringWidth = settingsRowRing(), focusedScale = 1.0f,
        modifier = modifier.fillMaxWidth().settingsRowHeight(touch, 84),
    ) { f ->
        Row(settingsRowInner(touch), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = if (touch) 2 else 1, overflow = TextOverflow.Ellipsis)
                if (hint != null) Text(hint, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = if (touch) 3 else 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.design))
            Text(value, color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = if (touch) 2 else 1, overflow = TextOverflow.Ellipsis, textAlign = if (touch) androidx.compose.ui.text.style.TextAlign.End else null, modifier = Modifier.widthIn(max = if (touch) 300.design else 420.design))
            Spacer(Modifier.width(12.design))
            DIcon(CHEVRON, 20.design, if (f) Ux.OnFocus2 else Ux.Text3, strokeWidth = 2.5f)
        }
    }
}

/** Ligne de réglage avec interrupteur (accent quand activé). */
@Composable
fun SwitchPrefRow(label: String, on: Boolean, modifier: Modifier = Modifier, hint: String? = null, onChange: (Boolean) -> Unit) {
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    FocusSurface(onClick = { onChange(!on) }, shape = settingsRowShape(), bg = Ux.SurfaceDeep, ringWidth = settingsRowRing(), focusedScale = 1.0f, modifier = modifier.fillMaxWidth().settingsRowHeight(touch, 84)) { f ->
        Row(settingsRowInner(touch), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = if (touch) 2 else 1, overflow = TextOverflow.Ellipsis)
                if (hint != null) Text(hint, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = if (touch) 3 else 1, overflow = TextOverflow.Ellipsis)
            }
            if (touch) Spacer(Modifier.width(12.design))
            Switch(on, inverted = f)
        }
    }
}

/**
 * Téléphone : la ligne grandit avec son texte (libellé sur 2 lignes, aide sur 3) au lieu d'une hauteur fixe qui coupait
 * les libellés longs ; TV : hauteur fixe inchangée.
 */
private fun Modifier.settingsRowHeight(touch: Boolean, px: Int): Modifier =
    if (touch) this.heightIn(min = px.designT) else this.height(px.designT)

private fun settingsRowInner(touch: Boolean): Modifier =
    if (touch) Modifier.fillMaxWidth().padding(horizontal = 32.design, vertical = 14.design) else Modifier.fillMaxSize().padding(horizontal = 32.design)

/** Titre de rubrique (Sora 36) avec description d'une ligne facultative. */
@Composable
fun PaneTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
        val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = if (touch) 2 else 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = if (touch) 4 else 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun GroupLabel(text: String) = Text(text.uppercase(), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp), maxLines = 1)

/**
 * Ligne de choix partagée (panneaux du lecteur, boîtes de choix des réglages). L'option ACTIVE porte une coche ✓ et un
 * libellé accent en gras, QUEL QUE SOIT le focus ; le focus garde son rendu habituel (fond clair + anneau). Avant, le
 * choix actif (liseré rouge) et la ligne focalisée (fond clair) se confondaient à distance.
 */
@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    heightPx: Int = 64,
    fontPx: Int = 22,
    bg: Color = Ux.SurfaceDeep,
    selectedBg: Color = Ux.Surface,
    ringWidth: androidx.compose.ui.unit.Dp = 4.design,
    leading: (@Composable () -> Unit)? = null,
    /** Description sous le libellé (facultative). */
    sub: String? = null,
) {
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(16.design), bg = if (selected) selectedBg else bg, ringWidth = ringWidth, focusedScale = 1f, modifier = modifier.fillMaxWidth().settingsRowHeight(touch, heightPx)) { f ->
        Row(if (touch) Modifier.fillMaxWidth().padding(horizontal = 22.design, vertical = 10.design) else Modifier.fillMaxSize().padding(horizontal = 22.design), verticalAlignment = Alignment.CenterVertically) {
            // Place de la coche toujours réservée : les libellés restent alignés, cochés ou non.
            Box(Modifier.width(36.design), contentAlignment = Alignment.CenterStart) {
                if (selected) DIcon(Icons.Check, 26.design, if (f) Ux.TextOnLight else Ux.AccentInk, strokeWidth = 3.2f)
            }
            if (leading != null) { leading(); Spacer(Modifier.width(16.design)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(
                    label, color = when { f -> Ux.TextOnLight; selected -> Ux.AccentInk; else -> Ux.Text },
                    fontFamily = Manrope, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = fontPx.spx,
                    maxLines = if (touch) 2 else 1, overflow = TextOverflow.Ellipsis,
                )
                if (!sub.isNullOrEmpty()) Text(sub, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = (fontPx - 2).spx, maxLines = if (touch) 3 else 1, overflow = TextOverflow.Ellipsis)
            }
            if (!hint.isNullOrEmpty()) {
                Spacer(Modifier.width(12.design))
                Text(hint, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = fontPx.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.design))
            }
        }
    }
}

/** Boîte de choix modale (liste de valeurs, la courante cochée) — remplace les anciennes puces. */
@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T?, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    // Focus initial sur l'option COCHÉE (et non la première) : « ↓ puis OK » depuis Cloudflare choisissait Google, le focus
    // partant de « Système ». La liste défile d'abord jusqu'à elle (option hors écran dans une longue liste).
    val selectedIndex = options.indexOfFirst { it.first == selected }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    val selectedFocus = androidx.compose.runtime.remember { FocusRequester() }
    if (selectedIndex >= 0) androidx.compose.runtime.LaunchedEffect(Unit) {
        repeat(10) { if (runCatching { selectedFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect; kotlinx.coroutines.delay(50) }
    }
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(Modifier.widthIn(min = 560.design, max = 760.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design), verticalArrangement = Arrangement.spacedBy(12.design)) {
            Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.design))
            LazyColumn(Modifier.heightIn(max = 640.design), state = listState, verticalArrangement = Arrangement.spacedBy(10.design)) {
                items(options, key = { it.second }) { (key, label) ->
                    ChoiceRow(label, key == selected, { onPick(key) }, heightPx = 68, fontPx = 24, bg = Ux.Surface, selectedBg = Ux.Surface2, ringWidth = 5.design,
                        modifier = if (key == selected) Modifier.focusRequester(selectedFocus) else Modifier)
                }
            }
        }
    }
}

/** Affiche 2:3 de taille imposée par la colonne (jamais par l'image) + titre sur une ligne. */
@Composable
fun VodCard(
    title: String, poster: String?, modifier: Modifier, kind: com.ultratv.tv.nativeapp.data.tmdb.TmdbKind? = null, year: Int? = null,
    /** En favori : petit cœur sur l'affiche. */
    favorite: Boolean = false,
    /** Appui long (ex. : ajouter aux favoris depuis la recherche). */
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    FocusSurface(onClick = onClick, onLongClick = onLongClick, shape = RoundedCornerShape(16.design), bg = Color.Transparent, focusedBg = Color.Transparent, ringWidth = 5.design, focusedScale = 1f, modifier = modifier) { _ ->
        Column(verticalArrangement = Arrangement.spacedBy(10.design)) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                PosterImage(poster, title, Modifier.fillMaxSize(), radius = 16, kind = kind, year = year)
                if (favorite) Box(Modifier.align(Alignment.TopEnd).padding(10.design).size(40.design).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xCC0A0A0C)), contentAlignment = Alignment.Center) {
                    DIcon(Icons.Heart, 22.design, Ux.AccentInk, fill = true)
                }
            }
            Text(title, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


object StateIcons {
    const val Offline = "M2 9a16 16 0 0 1 20 0M5 13a11 11 0 0 1 14 0M9 17a5 5 0 0 1 6 0M12 21h.01M3 3l18 18"
    const val Warning = "M12 9v4M12 17h.01M10.3 3.9L1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"
    const val Download = "M12 3v12M7 10l5 5 5-5M5 21h14"
}

/**
 * Carte d'état (maquette Etats) : pastille ronde 80 px, titre Sora 38, texte, progression facultative, boutons.
 * Sert aux états hors ligne, erreur de source, vide et mise à jour.
 */
@Composable
fun StateCard(
    title: String,
    body: String,
    iconPath: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Color = Ux.Accent,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    progress: Float? = null,
    primaryFocus: FocusRequester? = null,
    /** Points affichés sous le texte (ex. nouveautés d'une mise à jour), une ou deux lignes chacun. */
    details: List<String> = emptyList(),
    detailsTitle: String? = null,
) {
    Column(modifier.clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(44.design), verticalArrangement = Arrangement.spacedBy(20.design)) {
        Box(Modifier.width(80.design).height(80.design).clip(RoundedCornerShape(40.design)).background(badge), contentAlignment = Alignment.Center) {
            DIcon(iconPath, 40.design, if (badge == Ux.Accent) Ux.OnAccent else Ux.White, strokeWidth = 2f)
        }
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 38.spx, lineHeight = 42.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(body, color = Ux.Text2, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx, maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (details.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
            if (detailsTitle != null) Text(detailsTitle, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx)
            details.forEach { Text("•  $it", color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx, lineHeight = 30.spx, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (progress != null) ProgressLine(progress, Modifier.fillMaxWidth().clip(RoundedCornerShape(5.design)), heightPx = 10, track = Ux.Surface2)
        Row(horizontalArrangement = Arrangement.spacedBy(16.design), modifier = Modifier.padding(top = 4.design)) {
            PillButton(primaryLabel, onPrimary, heightPx = 64, hPadPx = 34, fontPx = 22, weight = FontWeight.Bold, bg = Ux.Cta, modifier = if (primaryFocus != null) Modifier.focusRequester(primaryFocus) else Modifier)
            if (secondaryLabel != null) PillButton(secondaryLabel, onSecondary, heightPx = 64, hPadPx = 30, fontPx = 22)
        }
    }
}
