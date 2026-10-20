package com.ultratv.tv.nativeapp.data.net.dns

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress

/** Serveur DNS choisi dans Réglages › Lecture › DNS. [key] = valeur stockée (stable, ne jamais renommer). */
enum class DnsMode(val key: String) {
    SYSTEM("system"), GOOGLE("google"), CLOUDFLARE("cloudflare"), QUAD9("quad9"), DOH("doh"), CUSTOM("custom");

    companion object {
        /** Tolérant : une valeur inconnue (ou absente) retombe sur le DNS du système. */
        fun parse(raw: String?): DnsMode = entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: SYSTEM
    }
}

/** Serveurs DNS classiques (UDP 53) des modes prédéfinis. */
internal val PLAIN_PRESETS: Map<DnsMode, List<String>> = mapOf(
    DnsMode.GOOGLE to listOf("8.8.8.8", "8.8.4.4"),
    DnsMode.CLOUDFLARE to listOf("1.1.1.1", "1.0.0.1"),
    DnsMode.QUAD9 to listOf("9.9.9.9", "149.112.112.112"),
)

/**
 * Fournisseurs DNS-over-HTTPS. [bootstrap] : adresses du serveur DoH lui-même, pour ne pas dépendre du DNS
 * du système (souvent celui qu'on cherche justement à contourner) pour joindre le résolveur.
 */
enum class DohPreset(val key: String, val url: String, val bootstrap: List<String>) {
    CLOUDFLARE("cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001")),
    GOOGLE("google", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844")),
    QUAD9("quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9")),
    CUSTOM("custom", "", emptyList());

    companion object {
        fun parse(raw: String?): DohPreset = entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: CLOUDFLARE
    }
}

/**
 * Réglage DNS complet (local à l'appareil). Les champs propres à un mode sont conservés quand on change de mode,
 * pour que revenir sur « Personnalisé » retrouve les adresses saisies.
 */
data class DnsSettings(
    val mode: DnsMode = DnsMode.SYSTEM,
    val dohPreset: DohPreset = DohPreset.CLOUDFLARE,
    /** URL DoH personnalisée (https:// uniquement), utilisée quand [dohPreset] = CUSTOM. */
    val dohUrl: String = "",
    /** Mode « Personnalisé » : une ou deux adresses IPv4 / IPv6. */
    val primary: String = "",
    val secondary: String = "",
) {
    /** Serveurs UDP effectifs (modes prédéfinis et personnalisé) ; vide = rien d'utilisable. */
    fun plainServers(): List<InetAddress> = when (mode) {
        DnsMode.GOOGLE, DnsMode.CLOUDFLARE, DnsMode.QUAD9 -> PLAIN_PRESETS.getValue(mode).mapNotNull { IpLiteral.parse(it) }
        DnsMode.CUSTOM -> listOf(primary, secondary).mapNotNull { IpLiteral.parse(it) }.distinct()
        else -> emptyList()
    }

    /** URL DoH effective, ou null si absente / invalide (seul https est accepté). */
    fun dohHttpUrl(): HttpUrl? = if (mode != DnsMode.DOH) null else validDohUrl(if (dohPreset == DohPreset.CUSTOM) dohUrl else dohPreset.url)

    fun dohBootstrap(): List<InetAddress> = if (dohPreset == DohPreset.CUSTOM) emptyList() else dohPreset.bootstrap.mapNotNull { IpLiteral.parse(it) }

    /** Un réglage inutilisable (adresse manquante, URL invalide) se comporte comme « Système ». */
    val usable: Boolean get() = when (mode) {
        DnsMode.SYSTEM -> false
        DnsMode.DOH -> dohHttpUrl() != null
        else -> plainServers().isNotEmpty()
    }

    /** Clé du cache de résolutions : changer de serveur invalide de fait les entrées obtenues avec l'ancien. */
    val cacheKey: String get() = "${mode.key}|${dohPreset.key}|$dohUrl|$primary|$secondary"

    /** Enregistrement à plat (SharedPreferences). */
    fun toMap(): Map<String, String> = mapOf(
        K_MODE to mode.key, K_DOH_PRESET to dohPreset.key, K_DOH_URL to dohUrl.trim(), K_PRIMARY to primary.trim(), K_SECONDARY to secondary.trim(),
    )

    companion object {
        const val K_MODE = "dns_mode"
        const val K_DOH_PRESET = "dns_doh_preset"
        const val K_DOH_URL = "dns_doh_url"
        const val K_PRIMARY = "dns_primary"
        const val K_SECONDARY = "dns_secondary"
        val KEYS = listOf(K_MODE, K_DOH_PRESET, K_DOH_URL, K_PRIMARY, K_SECONDARY)

        fun fromMap(get: (String) -> String?): DnsSettings = DnsSettings(
            mode = DnsMode.parse(get(K_MODE)),
            dohPreset = DohPreset.parse(get(K_DOH_PRESET)),
            dohUrl = get(K_DOH_URL).orEmpty().trim(),
            primary = get(K_PRIMARY).orEmpty().trim(),
            secondary = get(K_SECONDARY).orEmpty().trim(),
        )

        /** https:// avec un hôte, sans identifiants : sinon null. */
        fun validDohUrl(raw: String): HttpUrl? {
            val u = raw.trim().toHttpUrlOrNull() ?: return null
            return u.takeIf { it.isHttps && it.host.isNotBlank() && it.username.isEmpty() && it.password.isEmpty() }
        }
    }
}

/**
 * Analyse d'une adresse IP littérale SANS résolution DNS (InetAddress.getByName pourrait interroger le réseau
 * pour une chaîne mal formée). IPv4 en notation pointée stricte, IPv6 avec « :: » et queue IPv4 éventuelle,
 * crochets tolérés ; zone (%eth0) refusée.
 */
object IpLiteral {
    fun parse(raw: String?): InetAddress? {
        val s = raw?.trim()?.removePrefix("[")?.removeSuffix("]") ?: return null
        if (s.isEmpty()) return null
        val bytes = if (':' in s) v6(s) else v4(s)
        return bytes?.let { runCatching { InetAddress.getByAddress(it) }.getOrNull() }
    }

    fun isValid(raw: String?): Boolean = parse(raw) != null

    private fun v4(s: String): ByteArray? {
        val parts = s.split('.')
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for ((i, p) in parts.withIndex()) {
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
            if (p.length > 1 && p[0] == '0') return null   // « 010 » : ambigu (octal), refusé
            val n = p.toInt()
            if (n > 255) return null
            out[i] = n.toByte()
        }
        return out
    }

    private fun v6(s: String): ByteArray? {
        if ('%' in s) return null
        val halves = s.split("::")
        if (halves.size > 2) return null
        fun groups(part: String): List<String>? = if (part.isEmpty()) emptyList() else part.split(':').takeIf { g -> g.none { it.isEmpty() } }
        val head = groups(halves[0]) ?: return null
        val tail = (if (halves.size == 2) groups(halves[1]) else emptyList()) ?: return null
        val all = head + tail
        // Queue IPv4 (::ffff:1.2.3.4) : uniquement en dernière position, compte pour deux groupes.
        val v4Tail = all.lastOrNull()?.takeIf { '.' in it }?.let { v4(it) ?: return null }
        if (v4Tail != null && halves.size == 2 && tail.isEmpty()) return null   // « 1.2.3.4:: »
        val hexGroups = if (v4Tail != null) all.dropLast(1) else all
        if (hexGroups.any { g -> g.length > 4 || !g.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } }) return null
        val words = hexGroups.size + if (v4Tail != null) 2 else 0
        if (halves.size == 1 && words != 8) return null
        if (halves.size == 2 && words > 7) return null
        val headWords = (if (halves.size == 2) head else all).let { if (v4Tail != null && halves.size == 1) it.dropLast(1) else it }
        val out = ByteArray(16)
        var i = 0
        for (g in headWords) { val n = g.toInt(16); out[i++] = (n shr 8).toByte(); out[i++] = n.toByte() }
        if (halves.size == 2) {
            val tailHex = if (v4Tail != null) tail.dropLast(1) else tail
            i = 16 - (tailHex.size * 2) - (if (v4Tail != null) 4 else 0)
            for (g in tailHex) { val n = g.toInt(16); out[i++] = (n shr 8).toByte(); out[i++] = n.toByte() }
        }
        if (v4Tail != null) v4Tail.copyInto(out, 12)
        return out
    }
}
