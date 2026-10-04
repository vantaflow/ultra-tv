package com.ultratv.tv.nativeapp.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.LocalDs

/**
 * Libellés des fonctions du lot B2 (zapping, veille, replay, timeshift, sous-titres), à part de
 * [com.ultratv.tv.nativeapp.i18n.DesignStrings] pour ne pas se marcher dessus avec les autres chantiers.
 */
class PlayerExtraStrings(val lang: AppLang) {
    private fun t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
        AppLang.French -> fr
        AppLang.Spanish -> es
        AppLang.Arabic -> ar
        else -> en
    }

    // Zapping
    val recent get() = t("RECENT", "RÉCENTES", "RECIENTES", "الأخيرة")
    val zapHint get() = t("Back: previous channel · ▲▼ zap · 0-9 number", "Retour : chaîne précédente · ▲▼ zapper · 0-9 numéro", "Atrás: canal anterior · ▲▼ zapear · 0-9 número", "رجوع: القناة السابقة · ▲▼ تنقّل · 0-9 رقم")
    val noChannelNumber get() = t("No such channel", "Aucune chaîne à ce numéro", "Ningún canal con ese número", "لا توجد قناة بهذا الرقم")
}

@Composable
fun playerExtras(): PlayerExtraStrings {
    val lang = LocalDs.current.lang
    return remember(lang) { PlayerExtraStrings(lang) }
}
