package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.accentColor
import com.ultratv.tv.nativeapp.i18n.accentName
import com.ultratv.tv.nativeapp.i18n.uiSizeLabel
import com.ultratv.tv.nativeapp.i18n.uiSizeName
import com.ultratv.tv.nativeapp.ui.common.RequestInitialFocus
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.AccentChoice
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.DarkPalette
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.LightPalette
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.UiSize
import com.ultratv.tv.nativeapp.ui.design.UxPalette
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.paletteFor
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.theme.isLightTheme

/**
 * Choix de l'apparence par aperçus : tuiles de thème (mini-écran aux couleurs du thème), pastilles de couleur d'accent et
 * taille de l'interface. Partagé par l'assistant de première configuration (étape « Apparence ») et Réglages › Affichage.
 * Chaque choix s'applique tout de suite : l'écran entier sert d'aperçu.
 */
object AppearancePicker {
    /** Ordre des tuiles : celui des thèmes ([AppTheme.entries]), les 10 thèmes. */
    val themes: List<AppTheme> get() = AppTheme.entries

    /** Palettes dessinées dans la tuile : une seule, ou deux moitiés (sombre / claire) pour « Automatique ». */
    fun previewPalettes(theme: AppTheme): List<UxPalette> =
        if (theme == AppTheme.AUTO) listOf(DarkPalette, LightPalette)
        else listOf(paletteFor(isLightTheme(theme, systemDark = true), theme))

    /** Accent montré dans l'aperçu : celui du thème, sauf si l'utilisateur en a choisi un autre. */
    fun previewAccent(palette: UxPalette, accent: AccentChoice): Color =
        if (accent == AccentChoice.DEFAULT) palette.accent else Color(accent.argb)

    /**
     * Colonnes de la grille de thèmes pour une largeur disponible : tuiles d'au moins [minTile], entre 2 et 5 colonnes
     * (5 = deux rangées équilibrées de 5 thèmes). Pur.
     */
    fun columns(availableDp: Float, minTileDp: Float, gapDp: Float): Int =
        (((availableDp + gapDp) / (minTileDp + gapDp)).toInt()).coerceIn(2, 5)

    const val THEME_TAG = "appearance-theme-"
    const val ACCENT_TAG = "appearance-accent-"
    const val SIZE_TAG = "appearance-size-"
}

/** Intitulé de section (même style que les groupes des Réglages), suivi de la valeur actuelle. */
@Composable
private fun SectionLabel(title: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp))
        if (value != null) {
            Spacer(Modifier.width(12.design))
            Text(value, color = Ux.AccentInk, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Grille des 10 thèmes. [initialFocus] : la tuile du thème actif prend le focus à l'ouverture (TV), pour que la
 * télécommande arrive directement sur les aperçus. [title] : intitulé facultatif au-dessus de la grille.
 */
@Composable
fun ThemePicker(selected: AppTheme, accent: AccentChoice, onPick: (AppTheme) -> Unit, modifier: Modifier = Modifier, title: String? = null, initialFocus: Boolean = false) {
    val D = LocalDs.current
    val touch = LocalTouch.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.design)) {
        if (title != null) SectionLabel(title, themeLabel(D, selected))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = if (touch) 10.dp else 16.design
            val cols = AppearancePicker.columns(maxWidth.value, if (touch) 96f else 88f, gap.value)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                AppearancePicker.themes.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { t ->
                            ThemeTile(t, t == selected, accent, { onPick(t) }, Modifier.weight(1f), initialFocus = initialFocus && t == selected)
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeTile(theme: AppTheme, selected: Boolean, accent: AccentChoice, onClick: () -> Unit, modifier: Modifier, initialFocus: Boolean) {
    val D = LocalDs.current
    val touch = LocalTouch.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    if (initialFocus) RequestInitialFocus(requester, hasFocus = { focused })
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(18.design), bg = if (selected) Ux.Surface2 else Ux.SurfaceDeep,
        ringWidth = 5.design, focusedScale = 1.04f,
        modifier = modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused }
            .semantics { this.selected = selected }.testTag(AppearancePicker.THEME_TAG + theme.name),
    ) { f ->
        Column(Modifier.fillMaxWidth().padding(8.design), verticalArrangement = Arrangement.spacedBy(8.design)) {
            val previewH: Dp = if (touch) 64.dp else 96.design
            Box(
                Modifier.fillMaxWidth().height(previewH).clip(RoundedCornerShape(12.design))
                    .then(if (selected) Modifier.border(3.design, Ux.Accent, RoundedCornerShape(12.design)) else Modifier),
            ) {
                Row(Modifier.fillMaxSize()) {
                    AppearancePicker.previewPalettes(theme).forEach { p -> MiniScreen(p, AppearancePicker.previewAccent(p, accent), Modifier.weight(1f).fillMaxHeight()) }
                }
                if (selected) Box(
                    Modifier.align(Alignment.TopEnd).padding(6.design).size(if (touch) 22.dp else 30.design).clip(CircleShape).background(Ux.Accent),
                    contentAlignment = Alignment.Center,
                ) { DIcon(Icons.Check, if (touch) 14.dp else 18.design, Ux.OnAccent, strokeWidth = 3f) }
            }
            Text(
                themeLabel(D, theme), color = if (f) Ux.TextOnLight else if (selected) Ux.Text else Ux.Text2, fontFamily = Manrope,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 20.spx,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Mini-écran : rail, bandeau d'accent, deux cartes et une ligne de texte, aux couleurs de [p]. Dessin statique. */
@Composable
private fun MiniScreen(p: UxPalette, accent: Color, modifier: Modifier) {
    Row(modifier.background(p.bg)) {
        Column(Modifier.fillMaxHeight().width(14.design).background(p.rail).padding(top = 8.design), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.design)) {
            Box(Modifier.size(6.design).clip(CircleShape).background(accent))
            Box(Modifier.size(6.design).clip(CircleShape).background(p.text3))
            Box(Modifier.size(6.design).clip(CircleShape).background(p.text3))
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(7.design), verticalArrangement = Arrangement.spacedBy(5.design)) {
            Box(Modifier.fillMaxWidth(0.55f).height(6.design).clip(RoundedCornerShape(3.design)).background(p.text))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.design)) {
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(5.design)).background(accent))
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(5.design)).background(p.card))
            }
            Box(Modifier.fillMaxWidth(0.8f).height(5.design).clip(RoundedCornerShape(3.design)).background(p.text3))
        }
    }
}

/** Pastilles des 12 couleurs d'accent ; l'accent actif porte une coche. */
@Composable
fun AccentPicker(selected: AccentChoice, onPick: (AccentChoice) -> Unit, modifier: Modifier = Modifier) {
    val D = LocalDs.current
    val touch = LocalTouch.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.design)) {
        SectionLabel(D.accentColor, D.accentName(selected))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(if (touch) 6.dp else 14.design), verticalArrangement = Arrangement.spacedBy(if (touch) 6.dp else 14.design)) {
            AccentChoice.entries.forEach { a ->
                val on = a == selected
                FocusSurface(
                    onClick = { onPick(a) }, shape = CircleShape, bg = Color.Transparent, focusedBg = Color.Transparent, ringWidth = 5.design, focusedScale = 1.1f,
                    modifier = Modifier.size(if (touch) 44.dp else 60.design).semantics { this.selected = on }.testTag(AppearancePicker.ACCENT_TAG + a.key),
                ) { _ ->
                    Box(Modifier.fillMaxSize().padding(if (touch) 4.dp else 5.design).clip(CircleShape).background(Color(a.argb)), contentAlignment = Alignment.Center) {
                        if (on) DIcon(Icons.Check, if (touch) 18.dp else 26.design, Color(a.onAccent), strokeWidth = 3.2f)
                    }
                }
            }
        }
    }
}

/** Les quatre tailles d'interface, côte à côte ; la taille active est cochée. */
@Composable
fun UiSizePicker(selected: UiSize, onPick: (UiSize) -> Unit, modifier: Modifier = Modifier) {
    val D = LocalDs.current
    val touch = LocalTouch.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.design)) {
        SectionLabel(D.uiSizeLabel, null)
        // Téléphone étroit (ou grand texte) : deux par rangée, pour que « Normale » / « Très grande » ne se coupent pas.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth < 440.dp) 2 else 4
        Column(verticalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 14.design)) {
        UiSize.entries.chunked(perRow).forEach { rowSizes ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (touch) 8.dp else 14.design)) {
            rowSizes.forEach { s ->
                val on = s == selected
                FocusSurface(
                    onClick = { onPick(s) }, shape = RoundedCornerShape(16.design), bg = if (on) Ux.Surface2 else Ux.SurfaceDeep, ringWidth = 5.design, focusedScale = 1.04f,
                    modifier = Modifier.weight(1f).heightIn(min = if (touch) 48.dp else 64.design).semantics { this.selected = on }.testTag(AppearancePicker.SIZE_TAG + s.key),
                ) { f ->
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 10.design, vertical = 10.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        if (on) { DIcon(Icons.Check, if (touch) 14.dp else 20.design, if (f) Ux.TextOnLight else Ux.AccentInk, strokeWidth = 3f); Spacer(Modifier.width(6.design)) }
                        Text(
                            D.uiSizeName(s), color = when { f -> Ux.TextOnLight; on -> Ux.AccentInk; else -> Ux.Text }, fontFamily = Manrope,
                            fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 20.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        }
        }
        }
    }
}
