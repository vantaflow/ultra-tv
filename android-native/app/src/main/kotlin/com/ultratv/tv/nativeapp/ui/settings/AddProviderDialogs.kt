package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.ui.design.DIcon
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.common.ModalFocusScope
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.leaveOnVerticalDpad
import com.ultratv.tv.nativeapp.ui.design.FocusSurface
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Sora
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx

/** Adresse http(s) exploitable : schéma http/https et un hôte non vide. */
fun isValidHttpUrl(raw: String): Boolean {
    val s = raw.trim()
    if (!(s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true))) return false
    val host = runCatching { java.net.URI(s).host }.getOrNull()
    return !host.isNullOrBlank()
}

/**
 * Formulaire modal (maquette AjoutSource) : 1000 px, en-tête icône + titre + sous-titre, champs de 72 px,
 * « Annuler » / « Ajouter et synchroniser » à droite, aide télécommande en pied.
 * Les champs de saisie n'apparaissent que dans ces dialogues : défiler les Réglages au D-pad
 * ne tombe jamais sur un champ qui ferait surgir le clavier.
 */
@Composable
fun AddProviderDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
    icon: String = Icons.Monitor,
    subtitle: String? = null,
    submitLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val S = LocalStrings.current
    val D = LocalDs.current
    ModalFocusScope(onBack = onDismiss, modifier = Modifier.background(Ux.Scrim)) {
        Column(
            modifier = Modifier
                .width(1000.design)
                .clip(RoundedCornerShape(32.design))
                .background(Ux.SurfaceDeep)
                .border(1.design, Ux.Surface2, RoundedCornerShape(32.design))
                .padding(56.design),
            verticalArrangement = Arrangement.spacedBy(28.design),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.design)) {
                Box(Modifier.size(64.design).clip(RoundedCornerShape(16.design)).background(Ux.Accent), contentAlignment = Alignment.Center) {
                    DIcon(icon, 32.design, Ux.White, strokeWidth = 2f)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.design)) {
                    Text(title, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 40.spx, color = Ux.Text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, fontFamily = Manrope, fontSize = 20.spx, color = Ux.Text3, maxLines = 1)
                }
            }
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(20.design, Alignment.End), modifier = Modifier.fillMaxWidth().padding(top = 8.design)) {
                FocusSurface(onClick = onDismiss, shape = RoundedCornerShape(36.design), bg = Ux.Surface2, modifier = Modifier.height(72.design).testTag("dialog-cancel")) { f ->
                    Box(Modifier.height(72.design).padding(horizontal = 36.design), contentAlignment = Alignment.Center) {
                        Text(S.cancel, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 24.spx, color = if (f) Ux.TextOnLight else Ux.Text)
                    }
                }
                FocusSurface(onClick = { if (canSubmit) onSubmit() }, shape = RoundedCornerShape(36.design), bg = if (canSubmit) Ux.Cta else Ux.Surface, modifier = Modifier.height(72.design).testTag("dialog-submit")) { f ->
                    Box(Modifier.height(72.design).padding(horizontal = 44.design), contentAlignment = Alignment.Center) {
                        Text(submitLabel ?: D.addAndSync, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx,
                            color = when { !canSubmit -> Ux.Muted; f || canSubmit -> Ux.TextOnLight; else -> Ux.Text })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(32.design)) {
                Text(D.formHintFields, fontFamily = Manrope, fontSize = 18.spx, color = Ux.Text3, maxLines = 1)
                Text(D.formHintIme, fontFamily = Manrope, fontSize = 18.spx, color = Ux.Text3, maxLines = 1)
                Text(D.formHintBack, fontFamily = Manrope, fontSize = 18.spx, color = Ux.Text3, maxLines = 1)
            }
        }
    }
}

/**
 * Champ texte de formulaire. D-pad haut/bas le quittent toujours ([leaveOnVerticalDpad]) ;
 * l'action IME « Suivant » descend au champ suivant, « OK » ([last]) descend sur « Ajouter ».
 * [required] : « Champ requis » apparaît quand le champ est vide après l'avoir quitté.
 */
@Composable
fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    autoFocus: Boolean = false,
    last: Boolean = false,
    error: String? = null,
    required: Boolean = false,
    testTag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    val D = LocalDs.current
    var touched by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(focused) { if (focused) touched = true }
    // Focus initial sur le champ principal : sans cela le D-pad reste derrière le dialogue.
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }
    val shownError = error ?: if (required && touched && !focused && value.isBlank()) D.fieldRequired else null
    Column(verticalArrangement = Arrangement.spacedBy(10.design), modifier = Modifier.fillMaxWidth()) {
        Text(label, color = if (focused) Ux.Text else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.spx)
        Box(
            Modifier
                .fillMaxWidth()
                .height(72.design)
                .clip(RoundedCornerShape(18.design))
                .background(Ux.Surface)
                .then(
                    when {
                        focused -> Modifier.border(4.design, Ux.Accent, RoundedCornerShape(18.design))
                        shownError != null -> Modifier.border(2.design, Color(0xFFEF4444), RoundedCornerShape(18.design))
                        else -> Modifier
                    },
                )
                .padding(horizontal = 24.design),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Ux.Text, fontFamily = Manrope, fontSize = 24.spx),
                cursorBrush = SolidColor(Ux.Accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = if (last) ImeAction.Done else ImeAction.Next,
                    autoCorrect = false,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { fm.moveFocus(FocusDirection.Down) },
                    onDone = { fm.moveFocus(FocusDirection.Down) },   // « Ajouter »
                ),
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .leaveOnVerticalDpad()
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, color = Ux.Muted, fontFamily = Manrope, fontSize = 24.spx, maxLines = 1)
                    }
                    inner()
                },
            )
        }
        if (shownError != null) {
            Text(shownError, color = Ux.Err, fontFamily = Manrope, fontSize = 18.spx)
        }
    }
}

@Composable
fun XtreamDialog(onDismiss: () -> Unit, onSubmit: (name: String, url: String, user: String, pass: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val S = LocalStrings.current
    val D = LocalDs.current
    val urlOk = url.isBlank() || isValidHttpUrl(url)
    val canSubmit = isValidHttpUrl(url) && user.isNotBlank() && pass.isNotBlank()
    AddProviderDialog(
        title = "Xtream Codes",
        subtitle = D.formSubXtream,
        icon = Icons.Monitor,
        onDismiss = onDismiss,
        onSubmit = { onSubmit(name, url.trim(), user, pass) },
        canSubmit = canSubmit,
    ) {
        // Ordre de la maquette : nom (facultatif), serveur, identifiant, mot de passe. Le focus initial est sur le serveur.
        FormField(S.fieldNameOptional, name, { name = it }, placeholder = D.formNamePh, testTag = "field-name")
        FormField(S.fieldServerUrl, url, { url = it }, keyboardType = KeyboardType.Uri,
            placeholder = "http://serveur:port", autoFocus = true, required = true,
            error = if (urlOk) null else S.wiz.invalidUrl, testTag = "field-url")
        FormField(S.fieldUsername, user, { user = it }, required = true, testTag = "field-user")
        FormField(S.fieldPassword, pass, { pass = it }, password = true, last = true, required = true, placeholder = "••••••••", testTag = "field-pass")
    }
}

@Composable
fun M3uDialog(onDismiss: () -> Unit, onSubmit: (name: String, url: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val S = LocalStrings.current
    val D = LocalDs.current
    val urlOk = url.isBlank() || isValidHttpUrl(url)
    AddProviderDialog(
        title = "M3U",
        subtitle = D.formSubM3u,
        icon = Icons.List,
        onDismiss = onDismiss,
        onSubmit = { onSubmit(name, url.trim()) },
        canSubmit = isValidHttpUrl(url),
    ) {
        FormField(S.fieldNameOptional, name, { name = it }, placeholder = D.formNamePh, testTag = "field-name")
        FormField(S.fieldPlaylistUrl, url, { url = it }, keyboardType = KeyboardType.Uri,
            placeholder = "https://hote.tld/liste.m3u", autoFocus = true, last = true, required = true,
            error = if (urlOk) null else S.wiz.invalidUrl, testTag = "field-url")
    }
}

@Composable
fun StalkerDialog(onDismiss: () -> Unit, onSubmit: (name: String, url: String, mac: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    val S = LocalStrings.current
    val D = LocalDs.current
    val urlOk = url.isBlank() || isValidHttpUrl(url)
    AddProviderDialog(
        title = "Stalker / MAG",
        subtitle = D.formSubStalker,
        icon = Icons.Globe,
        onDismiss = onDismiss,
        onSubmit = { onSubmit(name, url.trim(), mac) },
        canSubmit = isValidHttpUrl(url) && mac.length in 12..17,
    ) {
        FormField(S.fieldNameOptional, name, { name = it }, placeholder = D.formNamePh, testTag = "field-name")
        FormField(S.fieldPortalUrl, url, { url = it }, keyboardType = KeyboardType.Uri,
            placeholder = "http://hote:8080", autoFocus = true, required = true,
            error = if (urlOk) null else S.wiz.invalidUrl, testTag = "field-url")
        FormField(S.fieldDeviceMac, mac, { mac = it.uppercase() }, last = true, required = true,
            placeholder = "00:1A:79:XX:XX:XX", testTag = "field-mac")
    }
}
