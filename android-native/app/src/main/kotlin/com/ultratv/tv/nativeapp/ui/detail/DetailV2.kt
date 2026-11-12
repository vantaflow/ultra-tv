package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.favoritesShort
import com.ultratv.tv.nativeapp.ui.common.FavoriteToggleViewModel
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.AvatarImage
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.ProgressLine
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.design.startToEndBrush
import com.ultratv.tv.nativeapp.ui.movies.Skeleton
import kotlinx.coroutines.launch

/**
 * Fiches film / série de la nouvelle interface (maquettes FicheFilm, FicheSerie), rendu TV.
 * Composants SANS ViewModel : les parties liées à Hilt (favori, téléchargement, Trakt) arrivent par des « slots », ce qui
 * permet de rendre l'écran avec des données fictives dans les tests. Les couleurs viennent de [Ux] (palette sombre ou claire).
 */

/** Une case de la rangée de notes (« 7,6 / IMDb »). */
@Immutable
class RatingCell(val value: String, val label: String)

/** Une personne de la distribution ; [role] null quand la source ne le donne pas. */
@Immutable
class CastUi(val name: String, val role: String?, val photo: String?)

/** Tout ce que l'écran film affiche, déjà calculé par l'écran parent. */
@Immutable
class MovieDetailTvModel(
    val title: String,
    val backdrop: String?,
    val poster: String?,
    val chips: List<String>,
    val ratings: List<RatingCell>,
    val synopsis: String?,
    val skeleton: Boolean,
    val primaryLabel: String,
    /** « Depuis le début » : null = pas de second bouton (film pas commencé). */
    val fromStartLabel: String?,
    val trailerLabel: String,
    /** Fraction déjà vue (0 = pas de barre) et ligne « Reprendre à … · reste … ». */
    val progress: Float,
    val progressLine: String?,
    val cast: List<CastUi>,
    val castTitle: String,
    val backDescription: String,
)

private val HMS_RE = Regex("""\d{1,2}:\d{2}(:\d{2})?""")

/** « 01:52:00 » ou « 112 » → minutes ; null si illisible ou nul. */
fun durationMinutes(raw: String?): Int? {
    val t = raw?.trim().orEmpty()
    val mins = when {
        t.isEmpty() -> null
        t.all { it.isDigit() } -> t.toIntOrNull()
        HMS_RE.matches(t) -> t.split(':').let { it[0].toInt() * 60 + it[1].toInt() }
        else -> null
    }
    return mins?.takeIf { it > 0 }
}

/** Libellé « Durée » de la rangée de notes. */
fun durationLabel(d: DesignStrings): String = I18n.string(d.lang, R.string.v2_duration_label)

// ───────────────────────── Composants communs ─────────────────────────

/** Bouton « retour » rond (56 px) ; la touche Retour de la télécommande reste gérée par la navigation. */
@Composable
fun V2BackButton(description: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onBack, shape = CircleShape, bg = Ux.Button, focusedScale = 1f, ringWidth = 4.design,
        modifier = modifier.size(56.design).semantics { contentDescription = description },
    ) { f ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DIcon(Icons.Chevron, 24.design, if (f) Ux.TextOnLight else Ux.Text, strokeWidth = 2.2f)
        }
    }
}

/** Bouton pilule : [primary] = couleur de focus au repos (Regarder / Reprendre), sinon fond bouton ; au focus, fond de focus + anneau. */
@Composable
fun V2Pill(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false,
    heightPx: Int = 64, fontPx: Int = 20, hPadPx: Int = 28, iconPath: String? = null, iconFill: Boolean = false,
) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape((heightPx / 2).design), bg = if (primary) Ux.Cta else Ux.Button,
        focusedBg = Ux.Cta, focusedScale = 1f, ringWidth = 4.design, modifier = modifier.height(heightPx.designT),
    ) { f ->
        val ink = if (primary || f) Ux.TextOnLight else Ux.Text
        Row(Modifier.padding(horizontal = hPadPx.design).height(heightPx.designT), verticalAlignment = Alignment.CenterVertically) {
            if (iconPath != null) {
                DIcon(iconPath, (fontPx + 2).design, ink, fill = iconFill, strokeWidth = 2.2f)
                Spacer(Modifier.width(10.design))
            }
            Text(label, color = ink, fontFamily = Manrope, fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.Bold, fontSize = fontPx.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Bouton rond à icône seule (favori). [active] : icône accent quand l'état est « oui ». */
@Composable
fun V2IconButton(iconPath: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, sizePx: Int = 64) {
    FocusSurface(
        onClick = onClick, shape = CircleShape, bg = Ux.Button, focusedBg = Ux.Cta, focusedScale = 1f, ringWidth = 4.design,
        modifier = modifier.size(sizePx.design).semantics { contentDescription = description },
    ) { f ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DIcon(iconPath, 24.design, if (f) Ux.TextOnLight else if (active) Ux.AccentInk else Ux.Text, strokeWidth = 2f, fill = active)
        }
    }
}

/** Bouton favori rond lié au même magasin que le cœur historique (Hilt : hors tests de rendu). */
@Composable
fun FavoriteIconButton(kind: String, remoteId: String, sizePx: Int = 64, vm: FavoriteToggleViewModel = hiltViewModel()) {
    LaunchedEffect(kind, remoteId) { vm.set(kind, remoteId) }
    val on by vm.isFav.collectAsState()
    V2IconButton(Icons.Heart, LocalDs.current.favoritesShort, { vm.toggle() }, active = on, sizePx = sizePx)
}

/** Pastille d'info (année, genre, qualité, langue). */
@Composable
private fun V2Chip(text: String) {
    Text(
        text, color = Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.spx, maxLines = 1,
        modifier = Modifier.clip(CircleShape).background(Ux.Card).padding(horizontal = 14.design, vertical = 7.design),
    )
}

@Composable
private fun V2RatingsRow(cells: List<RatingCell>) {
    Row(horizontalArrangement = Arrangement.spacedBy(28.design), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, c ->
            if (i > 0) Box(Modifier.width(2.design).height(44.design).background(Ux.Text.copy(alpha = 0.15f)))
            Column {
                Text(c.value, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.spx, maxLines = 1)
                Text(c.label, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.spx, maxLines = 1)
            }
        }
    }
}

/** Distribution : avatars ronds, nom et rôle ; OK ouvre la page de la personne. */
@Composable
fun V2CastRow(
    title: String, people: List<CastUi>, onOpenPerson: (String) -> Unit, modifier: Modifier = Modifier,
    avatarPx: Int = 88, cellPx: Int = 120, namePx: Int = 16, rolePx: Int = 14,
) {
    if (people.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.design)) {
        Text(title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
            for (p in people) FocusSurface(
                onClick = { onOpenPerson(p.name) }, shape = RoundedCornerShape(20.design), bg = Color.Transparent, focusedBg = Ux.Button,
                focusedScale = 1f, ringWidth = 0.dp, modifier = Modifier.width(cellPx.design),
            ) { _ ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.design)) {
                    AvatarImage(p.photo, p.name, Modifier.size(avatarPx.design))
                    Text(p.name, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = namePx.spx, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!p.role.isNullOrBlank()) Text(p.role, color = Ux.Text3, fontFamily = Manrope, fontSize = rolePx.spx, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ───────────────────────── Fiche film ─────────────────────────

/**
 * Fiche film TV : fond à droite (voile plein à gauche), puis retour, pastilles, titre, notes, synopsis, reprise, actions et
 * Distribution ; les titres proches ([below]) sont plus bas, le focus qui y entre fait défiler la fiche.
 * [actionsExtra] vient à la suite des boutons (téléchargement), [afterActions] sous eux (Trakt).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MovieDetailTv(
    m: MovieDetailTvModel,
    onPrimary: () -> Unit,
    onFromStart: () -> Unit,
    onTrailer: (() -> Unit)?,
    onBack: () -> Unit,
    onOpenPerson: (String) -> Unit,
    playRequester: FocusRequester,
    onPlayFocusChanged: (Boolean) -> Unit,
    favorite: @Composable () -> Unit,
    actionsExtra: @Composable () -> Unit = {},
    afterActions: @Composable () -> Unit = {},
    below: (@Composable () -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize().background(Ux.Bg)) {
        val screenH = maxHeight
        DetailBackdrop(m.backdrop, m.poster, m.title)
        // Voile : couleur de fond pleine sur la colonne de texte, fondu vers l'image ; lisière basse pour la rangée suivante.
        Box(Modifier.fillMaxSize().background(startToEndBrush(0f to Ux.Bg, 0.45f to Ux.Bg, 0.62f to Ux.Bg.copy(alpha = 0.55f), 0.88f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.7f to Color.Transparent, 1f to Ux.Bg.copy(alpha = 0.9f))))
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            // ─── En-tête : le focus qui y revient remonte la fiche ───
            Column(
                Modifier.fillMaxWidth().heightIn(min = screenH - 120.design).padding(start = 72.design, top = 56.design, bottom = 40.design)
                    .focusGroup().onFocusChanged { if (it.hasFocus) scope.launch { scroll.animateScrollTo(0, tween(160)) } },
            ) {
                Column(Modifier.width(880.design), verticalArrangement = Arrangement.spacedBy(18.design)) {
                    V2BackButton(m.backDescription, onBack)
                    Spacer(Modifier.height(26.design))
                    if (m.skeleton) Skeleton(Modifier.width(420.design).height(34.design))
                    else if (m.chips.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.design)) { m.chips.forEach { V2Chip(it) } }
                    Text(m.title, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 84.spx, lineHeight = 82.spx, letterSpacing = (-1.25).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!m.skeleton && m.ratings.isNotEmpty()) V2RatingsRow(m.ratings)
                    if (m.skeleton) Column(verticalArrangement = Arrangement.spacedBy(12.design)) {
                        Skeleton(Modifier.fillMaxWidth(0.9f).height(24.design)); Skeleton(Modifier.fillMaxWidth(0.9f).height(24.design)); Skeleton(Modifier.width(520.design).height(24.design))
                    } else m.synopsis?.let {
                        Text(it, color = Ux.Text2, fontFamily = Manrope, fontSize = 23.spx, lineHeight = 36.spx, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (m.progressLine != null) Column(verticalArrangement = Arrangement.spacedBy(8.design)) {
                        if (m.progress > 0f) ProgressLine(m.progress, Modifier.fillMaxWidth().clip(RoundedCornerShape(3.design)), heightPx = 6, track = Ux.Text.copy(alpha = 0.12f))
                        Text(m.progressLine, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.design), verticalAlignment = Alignment.CenterVertically) {
                        V2Pill(
                            m.primaryLabel, onPrimary, primary = true, heightPx = 64, fontPx = 21, hPadPx = 32, iconPath = Icons.Play, iconFill = true,
                            modifier = Modifier.focusRequester(playRequester).onFocusChanged { onPlayFocusChanged(it.isFocused) },
                        )
                        if (m.fromStartLabel != null) V2Pill(m.fromStartLabel, onFromStart)
                        if (onTrailer != null) V2Pill(m.trailerLabel, onTrailer)
                        favorite()
                        actionsExtra()
                    }
                    afterActions()
                    if (m.skeleton) Row(horizontalArrangement = Arrangement.spacedBy(16.design)) {
                        repeat(5) { Column(Modifier.width(120.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.design)) { Skeleton(Modifier.size(88.design), circle = true); Skeleton(Modifier.width(80.design).height(16.design)) } }
                    } else V2CastRow(m.castTitle, m.cast, onOpenPerson, Modifier.padding(top = 4.design))
                }
            }
            // ─── Titres proches : le focus qui y entre amène la rangée à l'écran ───
            if (below != null) Column(
                Modifier.fillMaxWidth().padding(start = 72.design, end = 72.design, bottom = 60.design)
                    .onFocusChanged { if (it.hasFocus) scope.launch { scroll.animateScrollTo(scroll.maxValue, tween(160)) } }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(28.design),
            ) { below() }
        }
    }
}
