package com.ultratv.tv.nativeapp.data.kairo

import com.ultratv.tv.nativeapp.LogSanitizer
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.nav.DeepLink
import java.net.URI
import java.security.MessageDigest

/**
 * Qui peut lire le fournisseur « en cours / suivant » (contrat Kairo, docs/superpowers/specs/2026-11-21-integration-kairo.md) :
 * le paquet [PACKAGE] signé par une clé de [ALLOWED_SHA256]. Pas de permission déclarée (les deux applis n'ont pas la même clé).
 */
object KairoAccess {
    const val PACKAGE = "com.vantaflow.kairo"

    /**
     * Empreintes SHA-256 des certificats de signature autorisés. Une seule entrée, convenue avec Kairo le 2026-11-21 et
     * revérifiée par apksigner sur les APK Kairo 1.26 et 1.27. Une nouvelle clé s'ajoute par une nouvelle version d'Ultra TV.
     */
    val ALLOWED_SHA256: List<String> = listOf("b5c0901798aa6a67b3cd619c56c27057431eec2681c9b1799f9afc2aba5e8c3e")

    fun normalize(digest: String) = digest.replace(":", "").trim().lowercase()

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * [callingPackage] : paquet annoncé par l'appelant ; [uidPackages] : paquets réellement associés à l'UID appelant ;
     * [signerDigests] : empreintes des signataires ACTUELS du paquet. Tous les signataires doivent être autorisés.
     */
    fun isAllowed(callingPackage: String?, uidPackages: List<String>, signerDigests: List<String>, allowed: List<String> = ALLOWED_SHA256): Boolean {
        if (callingPackage != PACKAGE || PACKAGE !in uidPackages || signerDigests.isEmpty()) return false
        val ok = allowed.map(::normalize).toSet()
        return signerDigests.all { normalize(it) in ok }
    }
}

/** Règles pures du fournisseur « en cours / suivant » : sélection, lignes, et filtrage de tout ce qui pourrait fuir. */
object KairoNowPlan {
    const val MAX = 20

    val COLUMNS = arrayOf(
        "provider_id", "remote_id", "name", "number", "logo", "now_title", "now_start_ms", "now_end_ms", "now_image",
        "next_title", "next_start_ms", "next_end_ms", "deep_link", "locked",
    )
    const val PROFILE_COLUMN = "kids"

    /** Ce qui ne doit jamais sortir : hôte du serveur de la source, identifiant et mot de passe. */
    data class Secrets(val hosts: Set<String>, val words: Set<String>) {
        companion object {
            fun of(baseUrl: String?, username: String?, password: String?): Secrets {
                val host = baseUrl?.let { runCatching { URI(it.trim()).host }.getOrNull() }?.lowercase()
                return Secrets(setOfNotNull(host?.takeIf { it.isNotBlank() }), setOfNotNull(username, password).map { it.trim().lowercase() }.filter { it.length >= 3 }.toSet())
            }
        }
        fun leaks(s: String): Boolean { val l = s.lowercase(); return hosts.any { it in l } || words.any { it in l } }
    }

    data class Row(
        val providerId: Long, val remoteId: String, val name: String, val number: Int?, val logo: String,
        val nowTitle: String, val nowStartMs: Long, val nowEndMs: Long, val nowImage: String,
        val nextTitle: String, val nextStartMs: Long, val nextEndMs: Long, val deepLink: String, val locked: Int,
    ) {
        fun values(): Array<Any?> = arrayOf(providerId, remoteId, name, number, logo, nowTitle, nowStartMs, nowEndMs, nowImage, nextTitle, nextStartMs, nextEndMs, deepLink, locked)
    }

    /** Favoris d'abord puis chaînes récentes, sans doublon ni séparateur, hors catégories masquées par le profil ; au plus [MAX]. */
    fun select(favorites: List<ChannelEntity>, recent: List<ChannelEntity>, hiddenCategoryKeys: Set<String>): List<ChannelEntity> =
        (favorites + recent).asSequence()
            .filter { !it.isSeparator && it.title.isNotBlank() }
            .filter { c -> c.categoryId == null || "LIVE:${c.providerId}:${c.categoryId}" !in hiddenCategoryKeys }
            .distinctBy { it.providerId to it.remoteId }
            .take(MAX).toList()

    /**
     * [lockedKeys] : « fournisseur:identifiant » des chaînes verrouillées ; [lockedCategories] : catégories LIVE verrouillées ;
     * [epg] : (en cours, suivant) par identifiant local de chaîne. Une chaîne verrouillée n'a ni titres ni horaires.
     */
    fun rows(channels: List<ChannelEntity>, lockedKeys: Set<String>, lockedCategories: Set<String>, epg: Map<Long, Pair<EpgEntity?, EpgEntity?>>, secrets: Secrets): List<Row> =
        channels.take(MAX).map { c ->
            val locked = "${c.providerId}:${c.remoteId}" in lockedKeys || (c.categoryId != null && c.categoryId in lockedCategories)
            val (now, next) = if (locked) null to null else epg[c.id] ?: (null to null)
            val nowTitle = now?.let { safeText(it.title, secrets) }.orEmpty()
            val nextTitle = next?.let { safeText(it.title, secrets) }.orEmpty()
            Row(
                providerId = c.providerId, remoteId = c.remoteId, name = safeText(c.title, secrets), number = c.num.takeIf { it > 0 },
                logo = safeLogo(c.logo, secrets),
                nowTitle = nowTitle, nowStartMs = if (nowTitle.isEmpty()) 0L else now!!.startMs, nowEndMs = if (nowTitle.isEmpty()) 0L else now!!.endMs,
                nowImage = "",
                nextTitle = nextTitle, nextStartMs = if (nextTitle.isEmpty()) 0L else next!!.startMs, nextEndMs = if (nextTitle.isEmpty()) 0L else next!!.endMs,
                deepLink = DeepLink.live(c.providerId, c.remoteId), locked = if (locked) 1 else 0,
            )
        }

    private val URLISH = Regex("""[a-z][a-z0-9+.-]*://|%3a%2f%2f|%2f%2f""", RegexOption.IGNORE_CASE)
    private val MAC = Regex("""([0-9a-f]{2}[:-]){5}[0-9a-f]{2}|[0-9a-f]{4}\.[0-9a-f]{4}\.[0-9a-f]{4}""", RegexOption.IGNORE_CASE)
    private val SPECIAL = Regex("""[={}"%@<>;\\]""")
    private val IPV4 = Regex("""\b\d{1,3}(\.\d{1,3}){3}\b""")
    private val IPV6 = Regex("""[0-9a-f]{0,4}::[0-9a-f]{0,4}|\b[0-9a-f]{1,4}(:[0-9a-f]{1,4}){3,}\b""", RegexOption.IGNORE_CASE)
    private val LONG_WORD = Regex("""[A-Za-z0-9+/_.-]{20,}""")
    private val SECRET_WORDS = Regex("""token|cookie|bearer|basic\s|eyj|mailto:|passw|secret|signature""", RegexOption.IGNORE_CASE)

    /**
     * Nom de chaîne ou titre de programme : gardé tel quel s'il ressemble à un titre, sinon vide. Tout ce qui ressemble à une
     * adresse, un paramètre, un en-tête, une adresse IP ou MAC, un jeton, ou contient un identifiant de la source est retiré.
     */
    fun safeText(s: String?, secrets: Secrets): String {
        val t = s?.trim().orEmpty()
        if (t.isEmpty()) return ""
        val suspicious = URLISH.containsMatchIn(t) || MAC.containsMatchIn(t) || SPECIAL.containsMatchIn(t) || IPV4.containsMatchIn(t) ||
            IPV6.containsMatchIn(t) || SECRET_WORDS.containsMatchIn(t) || secrets.leaks(t) || LogSanitizer.sanitize(t) != t ||
            LONG_WORD.findAll(t).any { w -> w.value.any(Char::isDigit) && w.value.any(Char::isLetter) }
        return if (suspicious) "" else t
    }

    /**
     * Adresse d'image : http(s) seulement, sans identifiants, sans paramètres ni fragment, hors serveur de la source, sans
     * identifiant ou mot de passe de la source dans le chemin ; sinon vide.
     */
    fun safeLogo(s: String?, secrets: Secrets): String {
        val t = s?.trim().orEmpty()
        if (t.isEmpty() || t.any { it.isWhitespace() } || '@' in t) return ""
        val u = runCatching { URI(t) }.getOrNull() ?: return ""
        val scheme = u.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return ""
        val host = u.host?.lowercase() ?: return ""
        if (u.rawUserInfo != null || u.rawQuery != null || u.rawFragment != null) return ""
        if (host in secrets.hosts || secrets.leaks(t) || LogSanitizer.sanitize(t) != t) return ""
        return t
    }
}
