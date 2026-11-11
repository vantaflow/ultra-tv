package com.ultratv.tv.nativeapp.i18n

/**
 * Libellés de « Télécommande sur le téléphone » : recherche et télécommande servies sur le réseau local, sans compte
 * (FR · EN · ES · AR). Fichier à part pour ne pas toucher à [DesignStrings].
 */
private fun DesignStrings.t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
    AppLang.French -> fr
    AppLang.Spanish -> es
    AppLang.Arabic -> ar
    else -> en
}

val DesignStrings.remoteTitle get() = t("Remote on your phone", "Télécommande sur le téléphone", "Mando en el teléfono", "جهاز تحكم على الهاتف")
val DesignStrings.remoteHint get() = t("Search and control the TV from your phone, on the same Wi-Fi", "Rechercher et piloter la TV depuis votre téléphone, sur le même Wi-Fi", "Busca y controla la TV desde tu teléfono, en la misma Wi-Fi", "ابحث وتحكّم بالتلفاز من هاتفك عبر شبكة Wi-Fi نفسها")
val DesignStrings.remoteEyebrow get() = t("REMOTE ON YOUR PHONE", "TÉLÉCOMMANDE SUR LE TÉLÉPHONE", "MANDO EN EL TELÉFONO", "جهاز تحكم على الهاتف")
val DesignStrings.remoteHeadline get() = t("Search and control your TV from your phone", "Cherchez et pilotez votre TV depuis votre téléphone", "Busca y controla tu TV desde tu teléfono", "ابحث وتحكّم بتلفازك من هاتفك")
val DesignStrings.remoteStep1 get() = t("Connect your phone to the same Wi-Fi as this device", "Connectez votre téléphone au même Wi-Fi que cet appareil", "Conecta tu teléfono a la misma Wi-Fi que este dispositivo", "صِل هاتفك بشبكة Wi-Fi نفسها التي يتصل بها هذا الجهاز")
fun DesignStrings.remoteStep2(address: String) = when (lang) {
    AppLang.French -> "Scannez le QR code, ou ouvrez $address (le lien contient une clé privée)"
    AppLang.Spanish -> "Escanea el código QR, o abre $address (el enlace incluye una clave privada)"
    AppLang.Arabic -> "امسح رمز QR، أو افتح $address (يحتوي الرابط على مفتاح خاص)"
    else -> "Scan the QR code, or open $address (the link contains a private key)"
}.trim()
val DesignStrings.remoteStep3 get() = t("Search, tap a result to open it here, then use the remote bar", "Cherchez, touchez un résultat pour l’ouvrir ici, puis utilisez la barre de télécommande", "Busca, toca un resultado para abrirlo aquí y usa la barra de mando", "ابحث، المس نتيجة لفتحها هنا، ثم استخدم شريط التحكم")
val DesignStrings.remoteOnlyForeground get() = t("Active only while Ultra TV is on screen. Nothing leaves your local network.", "Actif seulement quand Ultra TV est à l’écran. Rien ne sort de votre réseau local.", "Activo solo con Ultra TV en pantalla. Nada sale de tu red local.", "يعمل فقط عندما يكون Ultra TV على الشاشة. لا يخرج شيء من شبكتك المحلية.")
val DesignStrings.remoteEnable get() = t("Turn on", "Activer", "Activar", "تشغيل")
val DesignStrings.remoteDisable get() = t("Turn off", "Désactiver", "Desactivar", "إيقاف")
val DesignStrings.remoteNewKey get() = t("New key", "Nouvelle clé", "Nueva clave", "مفتاح جديد")
val DesignStrings.remoteNewKeyHint get() = t("A new key stops the old links and QR codes from working.", "Une nouvelle clé invalide les anciens liens et QR codes.", "Una clave nueva invalida los enlaces y códigos QR anteriores.", "يؤدي المفتاح الجديد إلى إبطال الروابط ورموز QR السابقة.")
val DesignStrings.remoteOff get() = t("The remote is off. Turn it on to get the QR code.", "La télécommande est désactivée. Activez-la pour obtenir le QR code.", "El mando está desactivado. Actívalo para obtener el código QR.", "جهاز التحكم متوقف. شغّله للحصول على رمز QR.")
val DesignStrings.remoteNoNetwork get() = t("No local network found. Connect this device to Wi-Fi or Ethernet.", "Aucun réseau local trouvé. Connectez cet appareil au Wi-Fi ou à l’Ethernet.", "No se encontró red local. Conecta este dispositivo a Wi-Fi o Ethernet.", "لم يُعثر على شبكة محلية. صِل هذا الجهاز بشبكة Wi-Fi أو إيثرنت.")
val DesignStrings.remoteFailed get() = t("The remote could not start. Try again.", "La télécommande n’a pas pu démarrer. Réessayez.", "No se pudo iniciar el mando. Inténtalo de nuevo.", "تعذّر تشغيل جهاز التحكم. حاول مجددًا.")
val DesignStrings.remoteScanHint get() = t("Scan with your phone", "Scannez avec votre téléphone", "Escanea con tu teléfono", "امسح الرمز بهاتفك")
val DesignStrings.remoteStatusOn get() = t("Ready · waiting for the phone", "Prêt · en attente du téléphone", "Listo · esperando al teléfono", "جاهز · بانتظار الهاتف")
