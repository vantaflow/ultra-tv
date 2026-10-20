package com.ultratv.tv.nativeapp.ui.settings

import com.ultratv.tv.nativeapp.i18n.AppLang

/** Libellés du réglage DNS (FR · EN · ES · AR), dans un fichier dédié. */
class DnsStrings(private val lang: AppLang) {
    private fun t(en: String, fr: String, es: String, ar: String) = when (lang) { AppLang.French -> fr; AppLang.Spanish -> es; AppLang.Arabic -> ar; else -> en }

    val group get() = t("Network", "Réseau", "Red", "الشبكة")
    val server get() = t("DNS server", "Serveur DNS", "Servidor DNS", "خادم DNS")
    val system get() = t("System (default)", "Système (défaut)", "Sistema (predeterminado)", "النظام (افتراضي)")
    val doh get() = t("DNS over HTTPS", "DNS sur HTTPS", "DNS sobre HTTPS", "DNS عبر HTTPS")
    val custom get() = t("Custom", "Personnalisé", "Personalizado", "مخصص")
    val dohProvider get() = t("DNS over HTTPS provider", "Fournisseur DNS sur HTTPS", "Proveedor de DNS sobre HTTPS", "مزود DNS عبر HTTPS")
    val dohUrl get() = t("DNS over HTTPS address", "Adresse DNS sur HTTPS", "Dirección de DNS sobre HTTPS", "عنوان DNS عبر HTTPS")
    val dohUrlSub get() = t("A https:// address, e.g. https://dns.example/dns-query", "Une adresse https://, par ex. https://dns.exemple/dns-query", "Una dirección https://, p. ej. https://dns.ejemplo/dns-query", "عنوان https://، مثل https://dns.example/dns-query")
    val invalidDohUrl get() = t("Enter a valid https:// address", "Saisissez une adresse https:// valide", "Introduce una dirección https:// válida", "أدخل عنوان https:// صالحًا")
    val servers get() = t("DNS addresses", "Adresses DNS", "Direcciones DNS", "عناوين DNS")
    val serversSub get() = t("One or two IPv4 or IPv6 addresses", "Une ou deux adresses IPv4 ou IPv6", "Una o dos direcciones IPv4 o IPv6", "عنوان أو عنوانان IPv4 أو IPv6")
    val primary get() = t("Primary DNS", "DNS primaire", "DNS primario", "DNS الأساسي")
    val secondary get() = t("Secondary DNS (optional)", "DNS secondaire (facultatif)", "DNS secundario (opcional)", "DNS الثانوي (اختياري)")
    val invalidIp get() = t("Invalid IP address", "Adresse IP invalide", "Dirección IP no válida", "عنوان IP غير صالح")
    val notSet get() = t("Not set", "Non défini", "Sin definir", "غير محدد")
    val test get() = t("Test", "Tester", "Probar", "اختبار")
    val testHint get() = t("Resolves your provider's address", "Résout l’adresse de votre fournisseur", "Resuelve la dirección de tu proveedor", "يحلّ عنوان مزودك")
    val testing get() = t("Testing…", "Test en cours…", "Probando…", "جارٍ الاختبار…")
    fun testOk(ip: String, ms: Long) = t("$ip · $ms ms", "$ip · $ms ms", "$ip · $ms ms", "$ip · $ms مللي ثانية")
    fun testFallback(ip: String, reason: String) = t(
        "Server unreachable ($reason) · system DNS: $ip",
        "Serveur injoignable ($reason) · DNS système : $ip",
        "Servidor inaccesible ($reason) · DNS del sistema: $ip",
        "الخادم غير متاح ($reason) · DNS النظام: $ip",
    )
    val testFailed get() = t("Resolution failed", "Échec de la résolution", "Error de resolución", "فشل التحليل")
    val vlcNote get() = t("Not applied to the VLC player", "Non appliqué au lecteur VLC", "No se aplica al reproductor VLC", "لا يُطبَّق على مشغّل VLC")
    val scopeNote get() = t(
        "Applies to catalog, guide, images, cloud and video streams played with ExoPlayer. The VLC player keeps the system DNS. If the server does not answer within 2 s, the system DNS is used.",
        "S’applique au catalogue, au guide, aux images, au cloud et aux flux vidéo lus par ExoPlayer. Le lecteur VLC garde le DNS du système. Si le serveur ne répond pas en 2 s, le DNS du système prend le relais.",
        "Se aplica al catálogo, la guía, las imágenes, la nube y los flujos de vídeo de ExoPlayer. El reproductor VLC usa el DNS del sistema. Si el servidor no responde en 2 s, se usa el DNS del sistema.",
        "يُطبَّق على الكتالوج والدليل والصور والسحابة وبثوث الفيديو في ExoPlayer. يبقى مشغّل VLC على DNS النظام. إذا لم يستجب الخادم خلال ثانيتين، يُستخدم DNS النظام.",
    )
}
