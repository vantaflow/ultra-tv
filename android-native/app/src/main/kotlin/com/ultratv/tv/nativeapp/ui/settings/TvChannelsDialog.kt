package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.ultratv.tv.nativeapp.data.tv.TvCategoryRef
import com.ultratv.tv.nativeapp.data.tv.TvChannelsChoice
import com.ultratv.tv.nativeapp.data.tv.TvHomeChannel
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.doneLabel
import com.ultratv.tv.nativeapp.i18n.resetDefault
import com.ultratv.tv.nativeapp.i18n.tvChannelName
import com.ultratv.tv.nativeapp.i18n.tvChannelsAddCategory
import com.ultratv.tv.nativeapp.i18n.tvChannelsCategories
import com.ultratv.tv.nativeapp.i18n.tvChannelsCategoriesHint
import com.ultratv.tv.nativeapp.i18n.tvChannelsHint
import com.ultratv.tv.nativeapp.i18n.tvChannelsLabel
import com.ultratv.tv.nativeapp.i18n.tvChannelsNoCategory
import com.ultratv.tv.nativeapp.i18n.tvChannelsSportNeeds
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.ChoiceDialog
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Switch
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.designT
import com.ultratv.tv.nativeapp.ui.design.spx

/**
 * Réglages › Affichage › Chaînes sur l'écran d'accueil Android TV (TV seulement) : chaque chaîne s'active d'un clic
 * (enregistré aussitôt). Activer : la chaîne est publiée puis le système demande confirmation ; désactiver : la chaîne est
 * retirée de l'écran d'accueil. Catégories du Direct : au plus [TvChannelsChoice.MAX_CATEGORIES], de la source active.
 *
 * [categories] : catégories du Direct proposées (identifiant source → nom) ; [providerId] : source active (null = aucune).
 */
@Composable
internal fun TvChannelsDialog(
    initial: TvChannelsChoice,
    sportOn: Boolean,
    providerId: Long?,
    categories: List<Pair<String, String>>,
    onSave: (TvChannelsChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val D = LocalDs.current
    var choice by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    fun update(c: TvChannelsChoice) { choice = c; onSave(c) }
    val names = remember(categories) { categories.toMap() }

    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            Modifier.widthIn(min = 720.design, max = 980.design).clip(RoundedCornerShape(28.design)).background(Ux.SurfaceDeep).padding(40.design),
            verticalArrangement = Arrangement.spacedBy(12.design),
        ) {
            Text(D.tvChannelsLabel, color = Ux.Text, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 34.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(D.tvChannelsHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.design))
            Column(Modifier.heightIn(max = 600.design).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.design)) {
                TvHomeChannel.entries.forEachIndexed { i, c ->
                    val needsSport = c == TvHomeChannel.SPORT_LIVE && !sportOn
                    ToggleLine(
                        label = D.tvChannelName(c), hint = if (needsSport) D.tvChannelsSportNeeds else null,
                        on = choice.isOn(c) && !needsSport,
                        modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        onClick = { if (!needsSport) update(choice.toggled(c)) },
                    )
                }
                Text(D.tvChannelsCategories, color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = 1, modifier = Modifier.padding(top = 10.design))
                Text(D.tvChannelsCategoriesHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 2, overflow = TextOverflow.Ellipsis)
                for (ref in choice.categoriesFor(providerId)) {
                    ToggleLine(label = names[ref.categoryId] ?: ref.categoryId, hint = null, on = true, onClick = { update(choice.withoutCategory(ref)) })
                }
                if (providerId != null && choice.categoriesFor(providerId).size < TvChannelsChoice.MAX_CATEGORIES) {
                    PillButton(D.tvChannelsAddCategory, { picking = true }, heightPx = 60, hPadPx = 30, fontPx = 22)
                }
            }
            Row(Modifier.padding(top = 8.design), horizontalArrangement = Arrangement.spacedBy(16.design)) {
                PillButton(D.doneLabel, onDismiss, heightPx = 64, hPadPx = 34, fontPx = 22, weight = FontWeight.Bold, bg = Ux.Cta)
                PillButton(D.resetDefault, { update(TvChannelsChoice.DEFAULT) }, heightPx = 64, hPadPx = 30, fontPx = 22)
            }
        }
    }
    if (picking && providerId != null) {
        val taken = choice.categoriesFor(providerId).map { it.categoryId }.toSet()
        val options = categories.filter { it.first !in taken }
        if (options.isEmpty()) {
            ChoiceDialog(D.tvChannelsCategories, listOf("" to D.tvChannelsNoCategory), null, { picking = false }, { picking = false })
        } else {
            ChoiceDialog(D.tvChannelsAddCategory, options, null, { id -> update(choice.withCategory(TvCategoryRef(providerId, id))); picking = false }, { picking = false })
        }
    }
}

@Composable
private fun ToggleLine(label: String, hint: String?, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(16.design), bg = Ux.Surface, ringWidth = 5.design, focusedScale = 1f,
        modifier = modifier.fillMaxWidth().height(if (hint == null) 68.designT else 88.designT),
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 22.design), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = if (f) Ux.TextOnLight else if (on) Ux.Text else Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (hint != null) Text(hint, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 18.spx, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(14.design))
            Switch(on, inverted = f)
        }
    }
}
