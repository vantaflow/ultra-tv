package com.ultratv.tv.nativeapp.ui.player.subtitles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ultratv.tv.nativeapp.data.subtitles.SubBackground
import com.ultratv.tv.nativeapp.data.subtitles.SubColor
import com.ultratv.tv.nativeapp.data.subtitles.SubOutline
import com.ultratv.tv.nativeapp.data.subtitles.SubPosition
import com.ultratv.tv.nativeapp.data.subtitles.SubSize
import com.ultratv.tv.nativeapp.data.subtitles.SubtitleHit
import com.ultratv.tv.nativeapp.data.subtitles.SubtitleStyle
import com.ultratv.tv.nativeapp.data.prefs.LanguagePrefs
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.player.PlayerExtraStrings
import com.ultratv.tv.nativeapp.ui.player.engine.TrackInfo
import kotlinx.coroutines.launch

private enum class Page { MAIN, AUDIO_LANGS, TEXT_LANGS, SEARCH }

/**
 * Panneau « Sous-titres » (maquette SousTitres) : lignes « ‹ valeur › » (◄ ► changent, OK passe à la suivante) avec
 * aperçu en direct sur la vidéo. Le style est enregistré à chaque changement.
 */
@Composable
fun SubtitlePanel(
    X: PlayerExtraStrings, vm: SubtitleViewModel, tracks: List<TrackInfo>, delaySupported: Boolean, isMovie: Boolean, movieTitle: String,
    onSelectTrack: (String?) -> Unit, onStyle: (SubtitleStyle) -> Unit, onDownloaded: (String) -> Unit, onClose: () -> Unit,
) {
    val settings by vm.settings.collectAsState()
    val style = settings.style
    val online by vm.onlineAvailable.collectAsState()
    var page by remember { mutableStateOf(Page.MAIN) }
    LaunchedEffect(Unit) { vm.refreshOnline() }
    fun set(s: SubtitleStyle) { vm.setStyle(s); onStyle(s) }
    ModalFocusScope(onBack = { if (page == Page.MAIN) onClose() else page = Page.MAIN }, modifier = Modifier.background(Color.Transparent), contentAlignment = Alignment.CenterEnd) {
        // Aperçu : le texte tel qu'il sera rendu, à gauche de l'écran.
        Box(Modifier.fillMaxSize().padding(end = 640.design, bottom = 120.design), contentAlignment = Alignment.BottomCenter) { SubtitlePreview(style, X.previewText) }
        Column(
            Modifier.fillMaxHeight().width(640.design).background(Ux.Rail).border(1.design, Ux.Surface2).padding(horizontal = 56.design, vertical = 54.design),
            verticalArrangement = Arrangement.spacedBy(14.design),
        ) {
            Text(X.subtitlesTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, maxLines = 1)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.design)) {
                when (page) {
                    Page.MAIN -> {
                        val ids = listOf<String?>(null) + tracks.map { it.id }
                        val sel = tracks.firstOrNull { it.selected }?.id
                        fun label(id: String?) = if (id == null) X.off else tracks.first { it.id == id }.label
                        ValueRow(X.track, label(sel)) { st -> onSelectTrack(SubtitleLogic.cycle(ids, sel, st)) }
                        ValueRow(X.size, X.sizeName(style.size)) { st -> set(style.copy(size = SubtitleLogic.cycle(SubSize.entries, style.size, st))) }
                        ValueRow(X.color, X.colorName(style.color)) { st -> set(style.copy(color = SubtitleLogic.cycle(SubColor.entries, style.color, st))) }
                        ValueRow(X.background, X.backgroundName(style.background)) { st -> set(style.copy(background = SubtitleLogic.cycle(SubBackground.entries, style.background, st))) }
                        ValueRow(X.outline, X.outlineName(style.outline)) { st -> set(style.copy(outline = SubtitleLogic.cycle(SubOutline.entries, style.outline, st))) }
                        ValueRow(X.position, X.positionName(style.position)) { st -> set(style.copy(position = SubtitleLogic.cycle(SubPosition.entries, style.position, st))) }
                        ValueRow(X.delay, if (delaySupported) SubtitleLogic.delayLabel(style.delayMs) else X.delayVlcOnly, enabled = delaySupported) { st -> set(style.withDelay(SubtitleLogic.stepDelay(style.delayMs, st))) }
                        ValueRow(X.audioLangs, settings.languages.audio.joinToString(" ").uppercase().ifBlank { "—" }) { page = Page.AUDIO_LANGS }
                        ValueRow(X.textLangs, settings.languages.text.joinToString(" ").uppercase().ifBlank { "—" }) { page = Page.TEXT_LANGS }
                    }
                    Page.AUDIO_LANGS -> LangPicker(X, settings.languages.audio) { vm.setLanguages(LanguagePrefs(it, settings.languages.text)) }
                    Page.TEXT_LANGS -> LangPicker(X, settings.languages.text) { vm.setLanguages(LanguagePrefs(settings.languages.audio, it)) }
                    Page.SEARCH -> SearchPage(X, vm, onDownloaded = onDownloaded)
                }
            }
            if (page == Page.MAIN) {
                val canSearch = online && isMovie
                PillButton(X.searchOnline, { if (canSearch) { vm.search(movieTitle); page = Page.SEARCH } }, heightPx = 64, hPadPx = 36, fontPx = 22, weight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                if (!canSearch) Text(if (!isMovie) X.searchMoviesOnly else X.searchNeedsProxy, color = Ux.Text3, fontFamily = Manrope, fontSize = 18.spx, maxLines = 2)
            }
            PillButton(if (page == Page.MAIN) X.close else X.back, { if (page == Page.MAIN) onClose() else page = Page.MAIN }, heightPx = 64, hPadPx = 36, fontPx = 22, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Texte d'aperçu : rendu avec les réglages courants (contour = ombre). */
@Composable
fun SubtitlePreview(style: SubtitleStyle, text: String) {
    val outline = when (style.outline) { SubOutline.NONE -> null; SubOutline.THIN -> Shadow(Color.Black, blurRadius = 3f); SubOutline.THICK -> Shadow(Color.Black, blurRadius = 8f) }
    Text(
        text, textAlign = TextAlign.Center,
        style = TextStyle(color = Color(0xFF000000.toInt() or style.color.rgb), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = style.size.px1080.spx, lineHeight = (style.size.px1080 * 1.3f).spx, shadow = outline),
        modifier = Modifier.background(Color.Black.copy(alpha = style.background.alpha), RoundedCornerShape(8.design)).padding(horizontal = 18.design, vertical = 8.design),
    )
}

private val Float.spx get() = androidx.compose.ui.unit.TextUnit(this / 2f, androidx.compose.ui.unit.TextUnitType.Sp)

/** Ligne « Libellé  ‹ valeur › » de 72 px : ◄ ► changent la valeur, OK passe à la suivante. */
@Composable
private fun ValueRow(label: String, value: String, enabled: Boolean = true, onStep: (Int) -> Unit) {
    FocusSurface(
        onClick = { if (enabled) onStep(1) }, shape = RoundedCornerShape(16.design), bg = Ux.SurfaceDeep, ringWidth = 4.design, focusedScale = 1f,
        modifier = Modifier.fillMaxWidth().height(72.design).onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) false
            else when (ev.key) { Key.DirectionLeft -> { if (enabled) onStep(-1); true }; Key.DirectionRight -> { if (enabled) onStep(1); true }; else -> false }
        },
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 24.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = if (f) Ux.TextOnLight else if (enabled) Ux.Text else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1)
            Text("‹ $value ›", color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Liste ordonnée : l'ordre de sélection est l'ordre de préférence (rang affiché). */
@Composable
private fun LangPicker(X: PlayerExtraStrings, selected: List<String>, onChange: (List<String>) -> Unit) {
    Text(X.langOrderHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 2)
    SubtitleLogic.LANGUAGES.forEach { code ->
        val rank = selected.indexOf(code)
        ValueRow(java.util.Locale.forLanguageTag(code).getDisplayLanguage(java.util.Locale.forLanguageTag(code)).replaceFirstChar { it.uppercase() }, if (rank >= 0) "#${rank + 1}" else "—") { onChange(SubtitleLogic.toggleLanguage(selected, code)) }
    }
}

@Composable
private fun SearchPage(X: PlayerExtraStrings, vm: SubtitleViewModel, onDownloaded: (String) -> Unit) {
    val hits by vm.hits.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    when {
        hits == null -> Text(X.searching, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
        hits!!.isEmpty() -> Text(X.noSubtitleFound, color = Ux.Text2, fontFamily = Manrope, fontSize = 22.spx)
        else -> hits!!.take(15).forEach { h: SubtitleHit ->
            ValueRow(h.release.ifBlank { h.id }, h.language.uppercase(), enabled = !busy) {
                busy = true
                scope.launch { vm.download(h)?.let(onDownloaded); busy = false }
            }
        }
    }
}
