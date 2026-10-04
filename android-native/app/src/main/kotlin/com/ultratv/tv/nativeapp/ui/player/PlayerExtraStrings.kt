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

    // Replay
    val fromStart get() = t("From the start", "Depuis le début", "Desde el principio", "من البداية")
    val backToLive get() = t("Back to live", "Revenir au direct", "Volver al directo", "العودة إلى البث المباشر")

    // Pause du direct
    val delayed get() = t("TIME-SHIFTED", "EN DIFFÉRÉ", "EN DIFERIDO", "مؤجّل")
    val directMark get() = t("LIVE", "DIRECT", "DIRECTO", "مباشر")
    val tsHls get() = t("Pause is unavailable on this stream (HLS)", "Pause du direct indisponible sur ce flux (HLS)", "Pausa no disponible en esta señal (HLS)", "الإيقاف المؤقت غير متاح لهذا البث (HLS)")
    val tsNoSpace get() = t("Not enough free space to pause live TV", "Espace libre insuffisant pour mettre le direct en pause", "Espacio libre insuficiente para pausar el directo", "مساحة التخزين غير كافية لإيقاف البث مؤقتًا")
    val tsFailed get() = t("Live buffer interrupted, back to live", "Tampon interrompu, retour au direct", "Búfer interrumpido, volviendo al directo", "انقطع التخزين المؤقت، العودة إلى المباشر")

    // Veille
    val sleepPill get() = t("Sleep", "Veille", "Dormir", "السكون")
    val sleepTitle get() = t("Sleep timer", "Minuterie de veille", "Temporizador", "مؤقّت السكون")
    val sleepEndOfProgramme get() = t("End of programme", "Fin du programme", "Fin del programa", "نهاية البرنامج")
    val sleepOff get() = t("Cancel timer", "Annuler la minuterie", "Cancelar", "إلغاء المؤقّت")
    val stillThere get() = t("Still there?", "Toujours là ?", "¿Sigues ahí?", "هل ما زلت هنا؟")
    val imHere get() = t("I'm here", "Je suis là", "Sigo aquí", "أنا هنا")
    val sleepNow get() = t("Stop now", "Arrêter maintenant", "Parar ahora", "إيقاف الآن")
    val close get() = t("Close", "Fermer", "Cerrar", "إغلاق")
    fun sleepStopsIn(s: Int) = when (lang) { AppLang.French -> "Arrêt dans $s s"; AppLang.Spanish -> "Se detiene en $s s"; AppLang.Arabic -> "الإيقاف بعد $s ث"; else -> "Stopping in $s s" }
}

@Composable
fun playerExtras(): PlayerExtraStrings {
    val lang = LocalDs.current.lang
    return remember(lang) { PlayerExtraStrings(lang) }
}
