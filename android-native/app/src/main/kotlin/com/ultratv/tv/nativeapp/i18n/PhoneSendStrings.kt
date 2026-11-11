package com.ultratv.tv.nativeapp.i18n

/**
 * Libellés de « Depuis le téléphone » : envoyer une playlist depuis un téléphone ou un ordinateur, sans compte
 * (FR · EN · ES · AR). Fichier à part pour ne pas toucher à [DesignStrings].
 */
private fun DesignStrings.t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
    AppLang.French -> fr
    AppLang.Spanish -> es
    AppLang.Arabic -> ar
    else -> en
}

val DesignStrings.cardPhone get() = t("From your phone", "Depuis le téléphone", "Desde el teléfono", "من الهاتف")
val DesignStrings.cardPhoneDesc get() = t("Send the source from a phone or a computer, no typing with the remote", "Envoyez la source depuis un téléphone ou un ordinateur, sans saisie à la télécommande", "Envía la fuente desde un teléfono o un ordenador, sin teclear con el mando", "أرسل المصدر من هاتف أو حاسوب دون الكتابة بالتحكم عن بعد")
val DesignStrings.phoneEyebrow get() = t("SEND FROM YOUR PHONE", "ENVOYER DEPUIS VOTRE TÉLÉPHONE", "ENVIAR DESDE TU TELÉFONO", "الإرسال من هاتفك")
val DesignStrings.phoneTitle get() = t("Send your playlist from a phone or a computer", "Envoyez votre playlist depuis un téléphone ou un ordinateur", "Envía tu lista desde un teléfono o un ordenador", "أرسل قائمتك من هاتف أو حاسوب")
fun DesignStrings.phoneStep1(address: String) = when (lang) {
    AppLang.French -> "Scannez le QR code, ou ouvrez $address"
    AppLang.Spanish -> "Escanea el código QR, o abre $address"
    AppLang.Arabic -> "امسح رمز QR، أو افتح $address"
    else -> "Scan the QR code, or open $address"
}.trim()
val DesignStrings.phoneStep2 get() = t("Choose the source type and fill in the form", "Choisissez le type de source et remplissez le formulaire", "Elige el tipo de fuente y rellena el formulario", "اختر نوع المصدر واملأ النموذج")
val DesignStrings.phoneStep3 get() = t("Send: the source is added here and synced. No account needed.", "Envoyez : la source est ajoutée ici et synchronisée. Aucun compte nécessaire.", "Envía: la fuente se añade aquí y se sincroniza. No hace falta cuenta.", "أرسل: يُضاف المصدر هنا ويُزامَن. لا حاجة إلى حساب.")
val DesignStrings.phoneScanHint get() = t("Scan with your phone", "Scannez avec votre téléphone", "Escanea con tu teléfono", "امسح الرمز بهاتفك")
val DesignStrings.phoneAddressLabel get() = t("OR OPEN", "OU OUVREZ", "O ABRE", "أو افتح")
val DesignStrings.phoneCodeLabel get() = t("CODE (IF ASKED)", "CODE (SI DEMANDÉ)", "CÓDIGO (SI SE PIDE)", "الرمز (عند الطلب)")
val DesignStrings.phoneRequesting get() = t("Requesting a code…", "Demande d’un code…", "Solicitando un código…", "جارٍ طلب الرمز…")
fun DesignStrings.phoneWaiting(remaining: String) = when (lang) {
    AppLang.French -> "En attente du téléphone · expire dans $remaining"
    AppLang.Spanish -> "Esperando al teléfono · caduca en $remaining"
    AppLang.Arabic -> "بانتظار الهاتف · ينتهي خلال $remaining"
    else -> "Waiting for the phone · expires in $remaining"
}
val DesignStrings.phoneFailed get() = t("The code could not be created or has expired. Try a new code.", "Le code n’a pas pu être créé ou a expiré. Demandez un nouveau code.", "No se pudo crear el código o ha caducado. Pide un código nuevo.", "تعذّر إنشاء الرمز أو انتهت صلاحيته. اطلب رمزًا جديدًا.")
val DesignStrings.phoneReceived get() = t("Source received from your phone", "Source reçue depuis votre téléphone", "Fuente recibida desde tu teléfono", "تم استلام المصدر من هاتفك")
