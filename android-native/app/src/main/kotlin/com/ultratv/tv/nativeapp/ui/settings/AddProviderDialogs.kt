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
import androidx.compose.foundation.layout.padding
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
import com.ultratv.tv.nativeapp.i18n.LocalStrings
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
 * Formulaire modal plein écran (style maquette : surfaces #1C1C21, anneau accent au focus).
 * Les champs de saisie n'apparaissent que dans ces dialogues : défiler les Réglages au D-pad
 * ne tombe jamais sur un champ qui ferait surgir le clavier.
 */
@Composable
fun AddProviderDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    canSubmit: Boolean,
    content: @Composable () -> Unit,
) {
    ModalFocusScope(
        onBack = onDismiss,
        modifier = Modifier.background(Color.Black.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 480.design, max = 760.design)
                .clip(RoundedCornerShape(28.design))
                .background(Ux.SurfaceDeep)
                .padding(40.design),
            verticalArrangement = Arrangement.spacedBy(16.design),
        ) {
            val S = LocalStrings.current
            Text(title, fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 36.spx, color = Ux.Text)
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(16.design), modifier = Modifier.padding(top = 8.design)) {
                FocusSurface(
                    onClick = { if (canSubmit) onSubmit() },
                    shape = RoundedCornerShape(32.design),
                    modifier = Modifier.testTag("dialog-submit"),
                ) { f ->
                    Box(Modifier.padding(horizontal = 36.design, vertical = 18.design)) {
                        Text(
                            S.wiz.add, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.spx,
                            color = when { !canSubmit -> Ux.Line; f -> Ux.TextOnLight; else -> Ux.Text },
                        )
                    }
                }
                FocusSurface(onClick = onDismiss, shape = RoundedCornerShape(32.design), modifier = Modifier.testTag("dialog-cancel")) { f ->
                    Box(Modifier.padding(horizontal = 36.design, vertical = 18.design)) {
                        Text(S.cancel, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 26.spx,
                            color = if (f) Ux.TextOnLight else Ux.Text)
                    }
                }
            }
        }
    }
}

/**
 * Champ texte de formulaire. D-pad haut/bas le quittent toujours ([leaveOnVerticalDpad]) ;
 * l'action IME « Suivant » descend au champ suivant, « OK » ([last]) descend sur « Ajouter ».
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
    testTag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    // Focus initial sur le champ principal : sans cela le D-pad reste derrière le dialogue.
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.design), modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Ux.Text3, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.spx)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.design))
                .background(Ux.Surface)
                .then(if (focused) Modifier.border(3.design, Ux.Accent, RoundedCornerShape(16.design)) else Modifier)
                .padding(horizontal = 24.design, vertical = 18.design),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Ux.Text, fontFamily = Manrope, fontSize = 28.spx),
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
                        Text(placeholder, color = Ux.Text3, fontFamily = Manrope, fontSize = 28.spx)
                    }
                    inner()
                },
            )
        }
        if (error != null) {
            Text(error, color = Ux.Err, fontFamily = Manrope, fontSize = 20.spx)
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
    val urlOk = url.isBlank() || isValidHttpUrl(url)
    val canSubmit = isValidHttpUrl(url) && user.isNotBlank() && pass.isNotBlank()
    AddProviderDialog(
        title = S.addProviderXtreamTitle,
        onDismiss = onDismiss,
        onSubmit = { onSubmit(name, url.trim(), user, pass) },
        canSubmit = canSubmit,
    ) {
        // Le serveur (obligatoire) passe en premier : il reçoit le focus initial ; le nom, facultatif, est en dernier.
        FormField(S.fieldServerUrl, url, { url = it }, keyboardType = KeyboardType.Uri,
            placeholder = "http://provider.com:8080", autoFocus = true,
            error = if (urlOk) null else S.wiz.invalidUrl, testTag = "field-url")
        FormField(S.fieldUsername, user, { user = it }, testTag = "field-user")
        FormField(S.fieldPassword, pass, { pass = it }, password = true, testTag = "field-pass")
        FormField(S.fieldNameOptional, name, { name = it }, last = true, testTag = "field-name")
    }
}

@Composable
fun M3uDialog(onDismiss: () -> Unit, onSubmit: (name: String, url: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val S = LocalStrings.current
    val urlOk = url.isBlank() || isValidHttpUrl(url)
    AddProviderDialog(
        title = S.addProviderM3uTitle,
        onDismiss = onDismiss,
        onSubmit = { onSubmit(name, url.trim()) },
        canSubmit = isValidHttpUrl(url),
    ) {
        FormField(S.fieldPlaylistUrl, url, { url = it }, keyboardType = KeyboardType.Uri,
            placeholder = "https://host.tld/playlist.m3u", autoFocus = true,
            error = if (urlOk) null else S.wiz.invalidUrl, testTag = "field-url")
        FormField(S.fieldNameOptional, name, { name = it }, last = true, testTag = "field-name")
    }
}

@Composable
fun StalkerDialog(onDismiss: () -> Unit, onSubmit: (name: String, url: String, mac: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    val S = LocalStrings.current
    val urlOk = url.isBlank() || isValidHttpUrl(url)
    AddProviderDialog(
        title = S.addProviderStalkerTitle,
        onDismiss = onDismiss,
        onSubmit = { onSubmit(name, url.trim(), mac) },
        canSubmit = isValidHttpUrl(url) && mac.length in 12..17,
    ) {
        FormField(S.fieldPortalUrl, url, { url = it }, keyboardType = KeyboardType.Uri,
            placeholder = "http://host:8080", autoFocus = true,
            error = if (urlOk) null else S.wiz.invalidUrl, testTag = "field-url")
        FormField(S.fieldDeviceMac, mac, { mac = it.uppercase() },
            placeholder = "00:1A:79:XX:XX:XX", testTag = "field-mac")
        FormField(S.fieldNameOptional, name, { name = it }, last = true, testTag = "field-name")
    }
}
