package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.paymentSoon
import com.ultratv.tv.nativeapp.i18n.paymentSub
import com.ultratv.tv.nativeapp.i18n.paymentTitle
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.design.*

/**
 * Ligne « Paiement par carte » de Réglages › À propos : visible mais grisée, « Bientôt disponible ». Même gabarit qu'une
 * [PrefRow] (hauteur, forme, anneau du thème) mais contenu à 50 %, sans chevron. Elle reste focalisable (▲ ▼ traversent la
 * liste, le lecteur d'écran la lit comme désactivée) ; OK et le toucher ne font rien. Aucun prix, lien ni requête réseau :
 * le paiement sera branché plus tard.
 */
@Composable
fun PaymentRow(D: DesignStrings = LocalDs.current) {
    val touch = com.ultratv.tv.nativeapp.ui.mobile.LocalTouch.current
    FocusSurface(
        onClick = { },
        shape = settingsRowShape(), bg = Ux.SurfaceDeep, ringWidth = settingsRowRing(), focusedScale = 1.0f,
        modifier = Modifier.fillMaxWidth().settingsRowHeight(touch, 84).testTag("settings-payment").semantics(mergeDescendants = true) { disabled() },
    ) { f ->
        Row(settingsRowInner(touch).alpha(0.5f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.design)) {
                Text(D.paymentTitle, color = if (f) Ux.TextOnLight else Ux.Text, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = if (touch) 2 else 1, overflow = TextOverflow.Ellipsis)
                Text(D.paymentSub, color = if (f) Ux.OnFocus2 else Ux.Text3, fontFamily = Manrope, fontSize = 22.spx, maxLines = if (touch) 3 else 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.design))
            Text(D.paymentSoon, color = if (f) Ux.TextOnLight else Ux.Text2, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.spx, maxLines = if (touch) 2 else 1, overflow = TextOverflow.Ellipsis, textAlign = if (touch) TextAlign.End else null, modifier = Modifier.widthIn(max = if (touch) 300.design else 420.design))
        }
    }
}
