package com.ultratv.tv.nativeapp.ui.categories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.ultratv.tv.nativeapp.data.db.ManualChannelEntity
import com.ultratv.tv.nativeapp.data.repo.ManualChannels
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.design.Text
import com.ultratv.tv.nativeapp.ui.settings.AddProviderDialog
import com.ultratv.tv.nativeapp.ui.settings.FormField

/** Résultat du formulaire : champs saisis et catégorie choisie (identifiant fournisseur, ou [ManualChannels.CUSTOM_CAT]). */
data class ManualChannelForm(val name: String, val url: String, val logo: String, val epg: String, val categoryId: String)

/**
 * Ajout / modification d'une chaîne manuelle. [existing] null = ajout. [categories] : catégories du Direct de la source
 * (identifiant, libellé) ; « Mes chaînes » est toujours proposée en dernier choix. Le sélecteur de catégorie est UN seul
 * élément focalisable (OK passe à la suivante) : les flèches haut / bas le traversent comme n'importe quel champ.
 * L'adresse n'est jamais journalisée (identifiants possibles).
 */
@Composable
fun ManualChannelDialog(
    existing: ManualChannelEntity?,
    categories: List<Pair<String, String>>,
    defaultCategory: String?,
    onDismiss: () -> Unit,
    onSave: (ManualChannelForm) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val D = LocalDs.current
    val M = remember(D.lang) { ManualChannelStrings(D.lang) }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var url by remember { mutableStateOf(existing?.streamUrl.orEmpty()) }
    var logo by remember { mutableStateOf(existing?.logo.orEmpty()) }
    var epg by remember { mutableStateOf(existing?.epgChannelId.orEmpty()) }
    val choices = categories + (ManualChannels.CUSTOM_CAT to M.catCustom)
    var cat by remember { mutableStateOf(existing?.categoryId?.takeIf { id -> choices.any { it.first == id } } ?: defaultCategory?.takeIf { id -> choices.any { it.first == id } } ?: ManualChannels.CUSTOM_CAT) }
    var confirmDelete by remember { mutableStateOf(false) }
    val urlOk = url.isBlank() || ManualChannels.isValidStreamUrl(url)
    val logoOk = ManualChannels.isValidLogoUrl(logo)
    val canSubmit = name.isNotBlank() && ManualChannels.isValidStreamUrl(url) && logoOk
    AddProviderDialog(
        title = if (existing == null) M.add else M.editTitle,
        subtitle = M.sub,
        icon = Icons.List,
        onDismiss = onDismiss,
        onSubmit = { onSave(ManualChannelForm(name.trim(), url.trim(), logo.trim(), epg.trim(), cat)) },
        canSubmit = canSubmit,
        submitLabel = M.save,
    ) {
        FormField(M.name, name, { name = it }, autoFocus = true, required = true, testTag = "field-name")
        FormField(M.url, url, { url = it }, keyboardType = KeyboardType.Uri, placeholder = "http://hote.tld/flux.m3u8", required = true,
            error = if (urlOk) null else M.badUrl, testTag = "field-url")
        FormField(M.logo, logo, { logo = it }, keyboardType = KeyboardType.Uri, placeholder = "https://hote.tld/logo.png",
            error = if (logoOk) null else M.badUrl, testTag = "field-logo")
        FormField(M.epg, epg, { epg = it }, testTag = "field-epg")
        // Sélecteur de catégorie : OK passe à la suivante (boucle), un seul élément focalisable.
        FocusSurface(
            onClick = { val i = choices.indexOfFirst { it.first == cat }; cat = choices[(i + 1) % choices.size].first },
            shape = RoundedCornerShape(16.design), bg = Ux.SurfaceDeep, ringWidth = 3.design, focusedScale = 1.0f, focusedBg = Ux.SurfaceDeep,
            modifier = Modifier.fillMaxWidth().height(72.design).testTag("field-category"),
        ) { f ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.design), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.design)) {
                Text(M.category, color = Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = 1)
                Text(choices.firstOrNull { it.first == cat }?.second.orEmpty(), color = Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (f) Text(M.catHint, color = Ux.Text3, fontFamily = Manrope, fontSize = 20.spx, maxLines = 1)
            }
        }
        if (existing != null && onDelete != null) {
            // Suppression en deux temps : le premier OK demande confirmation (libellé en rouge), le second supprime.
            FocusSurface(
                onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                shape = RoundedCornerShape(36.design), bg = if (confirmDelete) Ux.Err else Ux.Surface2,
                modifier = Modifier.height(72.design).testTag("manual-delete"),
            ) { f ->
                Box(Modifier.height(72.design).padding(horizontal = 36.design), contentAlignment = Alignment.Center) {
                    Text(M.delete + if (confirmDelete) " ?" else "", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, color = if (f) Ux.TextOnLight else Ux.Text, maxLines = 1)
                }
            }
        }
    }
}
