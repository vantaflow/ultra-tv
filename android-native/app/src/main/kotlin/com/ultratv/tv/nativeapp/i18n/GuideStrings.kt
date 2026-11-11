package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.data.xmltv.GuideAddress
import com.ultratv.tv.nativeapp.data.xmltv.GuideCheck
import com.ultratv.tv.nativeapp.data.xmltv.GuideMode

/**
 * Libellés du guide personnalisé : adresse du guide complémentaire (Réglages › Synchronisation) et « Guide de cette
 * source » (FR · EN · ES · AR). Fichier à part pour ne pas toucher à [DesignStrings].
 */
private fun DesignStrings.t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
    AppLang.French -> fr
    AppLang.Spanish -> es
    AppLang.Arabic -> ar
    else -> en
}

val DesignStrings.guideCustomOption get() = t("Custom address…", "Adresse personnalisée…", "Dirección personalizada…", "عنوان مخصص…")
fun DesignStrings.guideCustomLabel(host: String) = t("Custom · $host", "Personnalisé · $host", "Personalizado · $host", "مخصص · $host")
val DesignStrings.guideDialogTitle get() = t("Custom guide address", "Adresse de guide personnalisée", "Dirección de guía personalizada", "عنوان دليل مخصص")
val DesignStrings.guideDialogSubtitle get() = t("XMLTV guide, http:// or https:// address ending in .xml or .xml.gz", "Guide XMLTV, adresse http:// ou https:// finissant par .xml ou .xml.gz", "Guía XMLTV, dirección http:// o https:// que termine en .xml o .xml.gz", "دليل XMLTV، عنوان http:// أو https:// ينتهي بـ .xml أو .xml.gz")
val DesignStrings.guideAddressLabel get() = t("Guide address (XMLTV)", "Adresse du guide (XMLTV)", "Dirección de la guía (XMLTV)", "عنوان الدليل (XMLTV)")
val DesignStrings.guideVerifyAndSave get() = t("Check and save", "Vérifier et enregistrer", "Comprobar y guardar", "تحقق واحفظ")
val DesignStrings.guideSave get() = t("Save", "Enregistrer", "Guardar", "حفظ")
val DesignStrings.guideVerifying get() = t("Checking the address…", "Vérification de l’adresse…", "Comprobando la dirección…", "جارٍ التحقق من العنوان…")
val DesignStrings.guideSaved get() = t("Guide address saved", "Adresse du guide enregistrée", "Dirección de la guía guardada", "تم حفظ عنوان الدليل")
val DesignStrings.guideRemoved get() = t("Guide of this source removed", "Guide de cette source retiré", "Guía de esta fuente eliminada", "تمت إزالة دليل هذا المصدر")
val DesignStrings.guideFromPhone get() = t("Send it from your phone", "L’envoyer depuis le téléphone", "Enviarla desde el teléfono", "أرسله من الهاتف")

/** Message d'erreur (ou null si l'adresse est bonne) pour un verdict de vérification. */
fun DesignStrings.guideCheckMessage(c: GuideCheck): String? = when (c) {
    GuideCheck.Ok -> null
    is GuideCheck.Invalid -> when (c.problem) {
        GuideAddress.Problem.EMPTY -> t("Enter an address.", "Saisissez une adresse.", "Introduce una dirección.", "أدخل عنوانًا.")
        GuideAddress.Problem.SCHEME -> t("The address must start with http:// or https://.", "L’adresse doit commencer par http:// ou https://.", "La dirección debe empezar por http:// o https://.", "يجب أن يبدأ العنوان بـ http:// أو https://.")
        GuideAddress.Problem.HOST -> t("Invalid address (no host name).", "Adresse invalide (pas de nom d’hôte).", "Dirección no válida (sin nombre de host).", "عنوان غير صالح (بلا اسم مضيف).")
        GuideAddress.Problem.EXTENSION -> t("The address must end with .xml or .xml.gz.", "L’adresse doit finir par .xml ou .xml.gz.", "La dirección debe terminar en .xml o .xml.gz.", "يجب أن ينتهي العنوان بـ .xml أو .xml.gz.")
    }
    GuideCheck.Unreachable -> t("Address unreachable. Check it and your connection.", "Adresse injoignable. Vérifiez-la et votre connexion.", "Dirección inaccesible. Compruébala y tu conexión.", "العنوان غير قابل للوصول. تحقق منه ومن اتصالك.")
    is GuideCheck.Http -> t("The server answered with an error (HTTP ${c.code}).", "Le serveur a répondu par une erreur (HTTP ${c.code}).", "El servidor respondió con un error (HTTP ${c.code}).", "ردّ الخادم بخطأ (HTTP ${c.code}).")
    GuideCheck.NotXmltv -> t("This address is not an XMLTV guide.", "Cette adresse n’est pas un guide XMLTV.", "Esta dirección no es una guía XMLTV.", "هذا العنوان ليس دليل XMLTV.")
}

// ---- Guide de cette source ----

val DesignStrings.sourceGuide get() = t("Guide of this source", "Guide de cette source", "Guía de esta fuente", "دليل هذا المصدر")
val DesignStrings.sourceGuideTitle get() = t("Guide of this source", "Guide de cette source", "Guía de esta fuente", "دليل هذا المصدر")
val DesignStrings.sourceGuideSubtitle get() = t("Optional. Leave the address empty to remove it.", "Facultatif. Laissez l’adresse vide pour le retirer.", "Opcional. Deja la dirección vacía para quitarla.", "اختياري. اترك العنوان فارغًا لإزالته.")
val DesignStrings.sourceGuideModeLabel get() = t("Mode", "Mode", "Modo", "الوضع")
fun DesignStrings.sourceGuideMode(m: GuideMode) = when (m) {
    GuideMode.REPLACE -> t("Replace the provider’s guide", "Remplacer le guide du fournisseur", "Reemplazar la guía del proveedor", "استبدال دليل المزوّد")
    GuideMode.COMPLETE -> t("Complete the provider’s guide", "Compléter le guide du fournisseur", "Completar la guía del proveedor", "إكمال دليل المزوّد")
}
fun DesignStrings.sourceGuideModeHint(m: GuideMode) = when (m) {
    GuideMode.REPLACE -> t("Only this address is used.", "Seule cette adresse est utilisée.", "Solo se usa esta dirección.", "يُستخدم هذا العنوان فقط.")
    GuideMode.COMPLETE -> t("Used for channels left without a programme.", "Utilisée pour les chaînes restées sans programme.", "Se usa para los canales que se quedan sin programa.", "يُستخدم للقنوات التي بقيت بلا برنامج.")
}
fun DesignStrings.sourceGuideValue(m: GuideMode, host: String) = t(
    "${if (m == GuideMode.REPLACE) "Replaces" else "Completes"} · $host",
    "${if (m == GuideMode.REPLACE) "Remplace" else "Complète"} · $host",
    "${if (m == GuideMode.REPLACE) "Reemplaza" else "Completa"} · $host",
    "${if (m == GuideMode.REPLACE) "يستبدل" else "يُكمل"} · $host",
)
