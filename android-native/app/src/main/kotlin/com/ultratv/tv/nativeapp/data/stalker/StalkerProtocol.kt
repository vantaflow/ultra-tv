package com.ultratv.tv.nativeapp.data.stalker

import java.security.MessageDigest

/**
 * Portail Stalker / Ministra (boîtiers MAG) : éléments PURS du protocole, sans réseau ni Android.
 * Implémenté d'après le fonctionnement public du middleware (requêtes `?type=…&action=…&JsHttpRequest=1-xml`).
 *
 * Stockage d'une source Stalker SANS changement de schéma ([com.ultratv.tv.nativeapp.data.db.ProviderEntity]) :
 *  - `kind` = « STALKER », `baseUrl` = adresse du portail TELLE QUE SAISIE (…/c/ ou …/stalker_portal/c/) ;
 *  - `username` = adresse MAC normalisée (00:1A:79:XX:XX:XX, majuscules) ;
 *  - `password` = identifiant et mot de passe facultatifs du portail, empaquetés « login\u0000motdepasse » ([StalkerCreds]),
 *    vide s'il n'y en a pas ; chiffré au repos par le DAO comme le mot de passe Xtream (SecretBox).
 * Les éléments du catalogue gardent la commande Stalker (`cmd`) dans une adresse `stalker://…` ([StalkerLink]) : le vrai
 * lien de lecture, à durée de vie courte, n'est demandé au portail (create_link) qu'au moment de la lecture.
 */
object StalkerMac {
    private val NON_HEX = Regex("[^0-9A-Fa-f]")

    /** Préfixe des boîtiers MAG, proposé d'office dans le formulaire. */
    const val DEFAULT_PREFIX = "00:1A:79:"

    /** « 00-1a-79-aa-bb-cc », « 001a79aabbcc »… → « 00:1A:79:AA:BB:CC » ; null si ce n'est pas une adresse MAC complète. */
    fun normalize(raw: String): String? {
        val hex = raw.replace(NON_HEX, "").uppercase()
        if (hex.length != 12) return null
        return hex.chunked(2).joinToString(":")
    }

    fun isValid(raw: String): Boolean = normalize(raw) != null

    /**
     * Saisie à la télécommande : ne garde que les chiffres hexadécimaux (12 au plus), en majuscules, et remet les « : »
     * tout seuls. L'utilisateur ne tape jamais de séparateur.
     */
    fun formatTyping(raw: String): String {
        val hex = raw.replace(NON_HEX, "").uppercase().take(12)
        val pairs = hex.chunked(2)
        val joined = pairs.joinToString(":")
        // Paire complète (et adresse incomplète) : le « : » suivant est affiché d'avance, comme sur un boîtier.
        return if (hex.length in 2..10 && hex.length % 2 == 0) "$joined:" else joined
    }

    /** Affichage sans divulguer l'adresse : seuls les deux derniers octets restent lisibles. */
    fun mask(mac: String): String {
        val n = normalize(mac) ?: return "••:••:••:••:••:••"
        return "••:••:••:••:" + n.takeLast(5)
    }
}

/** Identifiant / mot de passe facultatifs du portail, rangés dans `ProviderEntity.password` (voir [StalkerMac]). */
object StalkerCreds {
    private const val SEP = '\u0000'

    fun pack(login: String, password: String): String =
        if (login.isEmpty() && password.isEmpty()) "" else "$login$SEP$password"

    /** (identifiant, mot de passe) ; une valeur sans séparateur est un mot de passe seul (identifiant vide). */
    fun unpack(stored: String): Pair<String, String> {
        if (stored.isEmpty()) return "" to ""
        val i = stored.indexOf(SEP)
        return if (i < 0) "" to stored else stored.substring(0, i) to stored.substring(i + 1)
    }
}

/** Adresse interne `stalker://<source>/<type>?cmd=…[&series=N]` stockée à la place d'une URL de flux. */
object StalkerLink {
    const val SCHEME = "stalker://"

    data class Parsed(val providerId: Long, val type: String, val cmd: String, val series: Int?)

    fun isStalker(url: String?): Boolean = url != null && url.startsWith(SCHEME)

    fun build(providerId: Long, type: String, cmd: String, series: Int? = null): String =
        "$SCHEME$providerId/$type?cmd=${enc(cmd)}" + (series?.let { "&series=$it" } ?: "")

    fun parse(url: String): Parsed? {
        if (!isStalker(url)) return null
        val rest = url.removePrefix(SCHEME)
        val slash = rest.indexOf('/')
        if (slash <= 0) return null
        val pid = rest.substring(0, slash).toLongOrNull() ?: return null
        val q = rest.indexOf('?', slash)
        val type = (if (q < 0) rest.substring(slash + 1) else rest.substring(slash + 1, q)).ifBlank { return null }
        val params = if (q < 0) emptyMap() else rest.substring(q + 1).split('&').mapNotNull { kv ->
            val i = kv.indexOf('=')
            if (i <= 0) null else kv.substring(0, i) to java.net.URLDecoder.decode(kv.substring(i + 1), "UTF-8")
        }.toMap()
        val cmd = params["cmd"] ?: return null
        return Parsed(pid, type, cmd, params["series"]?.toIntOrNull())
    }

    /** Lien renvoyé par le portail (`js.cmd`) : préfixe de lecteur retiré (« ffmpeg http://… », « auto http://… »). */
    fun cleanCmd(cmd: String): String {
        var s = cmd.trim()
        for (prefix in listOf("ffmpeg ", "ffrt ", "ffrt2 ", "ffrt3 ", "auto ")) {
            if (s.startsWith(prefix, ignoreCase = true)) { s = s.substring(prefix.length).trim(); break }
        }
        return s
    }

    /** Commande déjà jouable telle quelle (vrai serveur, pas « localhost ») : repli si create_link échoue. */
    fun directUrlOf(cmd: String): String? {
        val s = cleanCmd(cmd)
        if (!(s.startsWith("http://", true) || s.startsWith("https://", true) || s.startsWith("rtmp://", true))) return null
        val host = runCatching { java.net.URI(s.substringBefore(' ')).host }.getOrNull() ?: return null
        return if (host.equals("localhost", true) || host == "127.0.0.1") null else s.substringBefore(' ')
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

/** Adresses du portail : point d'entrée de l'API à sonder, Referer, logos relatifs. */
object StalkerPortal {
    /** Adresse saisie, avec « / » final (Referer attendu par les portails). */
    fun referer(portal: String): String {
        val s = withScheme(portal.trim())
        return if (s.endsWith("/")) s else "$s/"
    }

    /**
     * Points d'entrée possibles, dans l'ordre d'essai : `<saisie>/portal.php`, puis `<saisie sans /c>/server/load.php`,
     * `/stalker_portal/server/load.php`, et `portal.php` à la racine. Une adresse qui finit déjà par .php est prise telle quelle.
     */
    fun endpointCandidates(portal: String): List<String> {
        val raw = withScheme(portal.trim()).substringBefore('?').substringBefore('#')
        if (raw.endsWith(".php", ignoreCase = true)) return listOf(raw)
        val base = raw.trimEnd('/')
        val origin = runCatching { java.net.URI(base).let { "${it.scheme}://${it.rawAuthority}" } }.getOrNull() ?: base
        val withoutC = if (base.endsWith("/c", ignoreCase = true)) base.dropLast(2) else base
        return listOf(
            "$base/portal.php",
            "$withoutC/portal.php",
            "$withoutC/server/load.php",
            "$origin/stalker_portal/server/load.php",
            "$origin/portal.php",
        ).distinct()
    }

    /** Logo / affiche : absolu, relatif à l'hôte (« /stalker_portal/misc/… ») ou simple nom de fichier du portail. */
    fun absolute(portal: String, path: String?): String? {
        val s = path?.trim().orEmpty()
        if (s.isEmpty() || s == "null") return null
        if (s.startsWith("http://", true) || s.startsWith("https://", true)) return s
        val base = withScheme(portal.trim())
        val origin = runCatching { java.net.URI(base).let { "${it.scheme}://${it.rawAuthority}" } }.getOrNull() ?: return null
        if (s.startsWith("/")) return origin + s
        val root = base.substringBefore('?').trimEnd('/').let { if (it.endsWith("/c", true)) it.dropLast(2) else it }
        return "$root/misc/logos/320/$s"
    }

    private fun withScheme(s: String) = if (s.startsWith("http://", true) || s.startsWith("https://", true)) s else "http://$s"
}

/**
 * Identité de boîtier MAG dérivée de la MAC, comme le font couramment les lecteurs : numéro de série, identifiants
 * d'appareil et signature. Tout est surchargeable (certains portails lient le compte à un numéro de série précis).
 */
data class StalkerDevice(
    val mac: String,
    val sn: String = md5(mac).uppercase().take(13),
    val deviceId: String = sha256(mac).uppercase(),
    val deviceId2: String = sha256(mac).uppercase(),
    val signature: String = sha256(md5(mac).uppercase().take(13) + mac).uppercase(),
    val model: String = "MAG250",
) {
    companion object {
        const val USER_AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
        const val X_USER_AGENT = "Model: MAG250; Link: WiFi"

        private fun hex(alg: String, s: String) =
            MessageDigest.getInstance(alg).digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        fun md5(s: String) = hex("MD5", s)
        fun sha256(s: String) = hex("SHA-256", s)
    }
}

/** Dates d'expiration renvoyées par les portails (formats très variés) → instant en ms, null si illimité / illisible. */
object StalkerDates {
    private val PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd.MM.yyyy HH:mm", "dd.MM.yyyy", "dd/MM/yyyy",
        "MMMM d, yyyy, h:mm a", "MMMM d, yyyy, h:mm", "MMMM d, yyyy", "MMM d, yyyy",
    )

    fun parse(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s.startsWith("0000") || s.equals("null", true) || s.equals("unlimited", true)) return null
        s.toLongOrNull()?.let { return if (it <= 0) null else if (it < 100_000_000_000L) it * 1000 else it }
        for (p in PATTERNS) {
            val f = java.text.SimpleDateFormat(p, java.util.Locale.US).apply { isLenient = false; timeZone = java.util.TimeZone.getTimeZone("UTC") }
            val pos = java.text.ParsePosition(0)
            val d = f.parse(s, pos) ?: continue
            if (pos.index == s.length) return d.time
        }
        return null
    }
}
