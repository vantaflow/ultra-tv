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

    // Sous-titres
    val subsPill get() = t("Subtitles", "Sous-titres", "Subtítulos", "الترجمة")
    val subtitlesTitle get() = t("Subtitles", "Sous-titres", "Subtítulos", "الترجمة")
    val previewText get() = t("Subtitle preview\nwith your settings", "Aperçu des sous-titres\navec vos réglages", "Vista previa de subtítulos\ncon tus ajustes", "معاينة الترجمة\nبإعداداتك")
    val off get() = t("Off", "Désactivés", "Desactivados", "متوقفة")
    val track get() = t("Track", "Piste", "Pista", "المسار")
    val size get() = t("Size", "Taille", "Tamaño", "الحجم")
    val color get() = t("Colour", "Couleur", "Color", "اللون")
    val background get() = t("Background", "Fond", "Fondo", "الخلفية")
    val outline get() = t("Outline", "Contour", "Contorno", "الحدود")
    val position get() = t("Position", "Position", "Posición", "الموضع")
    val delay get() = t("Offset", "Décalage", "Desfase", "التأخير")
    val delayVlcOnly get() = t("VLC only", "VLC uniquement", "Solo VLC", "VLC فقط")
    val audioLangs get() = t("Audio languages", "Langues audio", "Idiomas de audio", "لغات الصوت")
    val textLangs get() = t("Subtitle languages", "Langues sous-titres", "Idiomas de subtítulos", "لغات الترجمة")
    val langOrderHint get() = t("Pick in order of preference", "Choisir dans l'ordre de préférence", "Elige por orden de preferencia", "اختر بترتيب التفضيل")
    val searchOnline get() = t("Search subtitles online", "Chercher des sous-titres en ligne", "Buscar subtítulos en línea", "البحث عن ترجمات")
    val searchNeedsProxy get() = t("Pair this device with your Worker to enable it", "Appairez l'appareil à votre Worker pour l'activer", "Vincula el dispositivo a tu Worker para activarlo", "اربط الجهاز بالـ Worker لتفعيله")
    val searchMoviesOnly get() = t("Available for movies only", "Disponible pour les films uniquement", "Solo para películas", "متاح للأفلام فقط")
    val searching get() = t("Searching…", "Recherche…", "Buscando…", "جارٍ البحث…")
    val noSubtitleFound get() = t("No subtitles found", "Aucun sous-titre trouvé", "No se encontraron subtítulos", "لا توجد ترجمات")
    val subtitleAdded get() = t("Subtitles added", "Sous-titres ajoutés", "Subtítulos añadidos", "تمت إضافة الترجمة")
    val back get() = t("Back", "Retour", "Atrás", "رجوع")
    fun sizeName(v: com.ultratv.tv.nativeapp.data.subtitles.SubSize) = when (v) {
        com.ultratv.tv.nativeapp.data.subtitles.SubSize.SMALL -> t("Small", "Petite", "Pequeño", "صغير")
        com.ultratv.tv.nativeapp.data.subtitles.SubSize.MEDIUM -> t("Medium", "Moyenne", "Mediano", "متوسط")
        com.ultratv.tv.nativeapp.data.subtitles.SubSize.LARGE -> t("Large", "Grande", "Grande", "كبير")
        com.ultratv.tv.nativeapp.data.subtitles.SubSize.XLARGE -> t("Extra large", "Très grande", "Muy grande", "كبير جدًا")
    }
    fun colorName(v: com.ultratv.tv.nativeapp.data.subtitles.SubColor) = when (v) {
        com.ultratv.tv.nativeapp.data.subtitles.SubColor.WHITE -> t("White", "Blanc", "Blanco", "أبيض")
        com.ultratv.tv.nativeapp.data.subtitles.SubColor.YELLOW -> t("Yellow", "Jaune", "Amarillo", "أصفر")
        com.ultratv.tv.nativeapp.data.subtitles.SubColor.CYAN -> t("Cyan", "Cyan", "Cian", "سماوي")
        com.ultratv.tv.nativeapp.data.subtitles.SubColor.GREEN -> t("Green", "Vert", "Verde", "أخضر")
    }
    fun backgroundName(v: com.ultratv.tv.nativeapp.data.subtitles.SubBackground) = when (v) {
        com.ultratv.tv.nativeapp.data.subtitles.SubBackground.NONE -> t("None", "Aucun", "Ninguno", "بلا")
        com.ultratv.tv.nativeapp.data.subtitles.SubBackground.SEMI -> t("Semi-transparent", "Semi-transparent", "Semitransparente", "شبه شفاف")
        com.ultratv.tv.nativeapp.data.subtitles.SubBackground.OPAQUE -> t("Opaque", "Opaque", "Opaco", "معتم")
    }
    fun outlineName(v: com.ultratv.tv.nativeapp.data.subtitles.SubOutline) = when (v) {
        com.ultratv.tv.nativeapp.data.subtitles.SubOutline.NONE -> t("None", "Aucun", "Ninguno", "بلا")
        com.ultratv.tv.nativeapp.data.subtitles.SubOutline.THIN -> t("Thin", "Fin", "Fino", "رفيع")
        com.ultratv.tv.nativeapp.data.subtitles.SubOutline.THICK -> t("Thick", "Épais", "Grueso", "سميك")
    }
    fun positionName(v: com.ultratv.tv.nativeapp.data.subtitles.SubPosition) = when (v) {
        com.ultratv.tv.nativeapp.data.subtitles.SubPosition.BOTTOM -> t("Bottom", "Bas", "Abajo", "أسفل")
        com.ultratv.tv.nativeapp.data.subtitles.SubPosition.RAISED -> t("Raised", "Relevé", "Elevado", "مرتفع")
        com.ultratv.tv.nativeapp.data.subtitles.SubPosition.HIGH -> t("High", "Haut", "Alto", "عالٍ")
    }

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
