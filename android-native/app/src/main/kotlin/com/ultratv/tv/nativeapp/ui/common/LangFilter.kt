package com.ultratv.tv.nativeapp.ui.common

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.LangCount
import com.ultratv.tv.nativeapp.data.repo.LangView
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.design.*

/** Nom lisible d'un code de la colonne `lang` : « fr » → « Français » ; « MULTI » → « Multilingue » ; « » → « Non déterminé ». */
fun langLabel(code: String, D: DesignStrings): String = when (code) {
    "" -> D.langUndetermined
    "MULTI" -> D.langMulti
    else -> java.util.Locale(code).getDisplayLanguage(java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() }.ifBlank { code.uppercase() }
}

/** Texte de la pilule : « Langues : toutes » / « Langues : FR · AR » (au plus 3 codes, puis « +n »). */
fun langPillText(view: LangView, D: DesignStrings): String {
    val sel = view.selected?.takeIf { it.isNotEmpty() } ?: return D.langPill(D.langAll)
    val codes = sel.sortedBy { if (it == "") "~" else it }.map { if (it == "") "?" else it.uppercase() }
    return D.langPill(if (codes.size <= 3) codes.joinToString(" · ") else codes.take(3).joinToString(" · ") + " +${codes.size - 3}")
}

/** Pilule « Langues : … » d'une vue ; OK ouvre [LangViewPanel]. */
@Composable
fun LangPill(view: LangView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val D = LocalDs.current
    val active = view.useLang == 1
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(24.design), bg = if (active) Ux.Surface2 else Ux.Surface, ringWidth = 4.design, focusedScale = 1f, modifier = modifier.height(48.design)) { f ->
        Row(Modifier.height(48.design).padding(horizontal = 22.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.design)) {
            DIcon(Icons.Globe, 22.design, if (f) Ux.TextOnLight else if (active) Ux.Accent else Ux.Text3, strokeWidth = 2f)
            Text(langPillText(view, D), color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Panneau latéral à cases à cocher : filtre TEMPORAIRE de la vue (rien n'est enregistré dans les réglages). */
@Composable
fun LangViewPanel(counts: List<LangCount>, view: LangView, onToggle: (String) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    val D = LocalDs.current
    val rows = counts.filter { it.n > 0 }.sortedWith(compareBy<LangCount> { it.lang == "" }.thenByDescending { it.n })
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim), contentAlignment = Alignment.CenterEnd) {
        Column(Modifier.fillMaxHeight().width(640.design).background(Ux.Rail).border(1.design, Ux.Surface2).padding(horizontal = 56.design, vertical = 54.design), verticalArrangement = Arrangement.spacedBy(18.design)) {
            Text(D.langPanelTitle, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, maxLines = 1)
            Text(D.langPanelHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 24.spx, lineHeight = 32.spx, maxLines = 3)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.design)) {
                item(key = "all") { CheckRow(D.langAll, "", view.useLang == 0) { onClear() } }
                items(rows, key = { it.lang }) { r ->
                    CheckRow(langLabel(r.lang, D), java.text.NumberFormat.getIntegerInstance().format(r.n), view.selected?.contains(r.lang) == true) { onToggle(r.lang) }
                }
            }
            PillButton(D.close, onDismiss, heightPx = 64, hPadPx = 36, fontPx = 22, weight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CheckRow(label: String, count: String, checked: Boolean, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(16.design), bg = Ux.SurfaceDeep, ringWidth = 4.design, focusedScale = 1f, modifier = Modifier.fillMaxWidth().height(64.design)) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 22.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.design)) {
            Box(
                Modifier.size(32.design).clip(RoundedCornerShape(8.design)).background(if (checked) Ux.Accent else Ux.Surface2),
                contentAlignment = Alignment.Center,
            ) { if (checked) DIcon(Icons.Check, 22.design, Ux.White, strokeWidth = 3f) }
            Text(label, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (count.isNotEmpty()) Text(count, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
        }
    }
}

/** Petit badge de langue d'un élément (« FR », « MULTI ») ; rien si la langue est inconnue. */
@Composable
fun LangBadge(lang: String, focused: Boolean = false, modifier: Modifier = Modifier) {
    if (lang.isBlank()) return
    val multi = lang == LangViewMulti
    Text(
        if (multi) "MULTI" else lang.uppercase(), color = if (focused) Ux.White else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.spx, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(6.design)).background(if (focused) Ux.Accent else if (multi) Ux.Surface2 else Ux.Surface).padding(horizontal = 8.design, vertical = 3.design),
    )
}

private const val LangViewMulti = "MULTI"
