package com.ultratv.tv.nativeapp.data.downloads

import java.io.File
import java.security.MessageDigest

/**
 * Règles pures du téléchargement (testées sans Android) : quels flux sont téléchargeables, nom et extension du fichier,
 * empreinte de l'URL, contrôle de l'espace libre.
 *
 * Choix HLS : les VOD Xtream (`/movie/…/123.mkv`, `/series/…/456.mp4`, `container_extension`) sont des fichiers directs,
 * téléchargés tels quels. Un VOD en `.m3u8` (rare : certaines listes M3U) est REFUSÉ avec « format non téléchargeable » :
 * Media3 DownloadManager imposerait un second moteur (cache SimpleCache, index, service dédié) et une lecture par
 * CacheDataSource que le moteur VLC ne sait pas lire — pour un cas marginal, la voie simple et robuste est le refus clair.
 */
object DownloadPlan {
    /** Marge gardée libre sur le volume après le téléchargement (le système et la base en ont besoin). */
    const val SPACE_MARGIN_BYTES = 200L * 1024 * 1024

    /** Taille supposée d'un fichier dont le serveur ne donne pas la taille (refus anticipé s'il ne reste pas ça). */
    const val UNKNOWN_SIZE_GUESS_BYTES = 1L * 1024 * 1024 * 1024

    private val EXT = Regex("^[a-z0-9]{2,5}$")

    /**
     * Seuls les fichiers HTTP(S) directs ; pas de HLS / DASH ni de schéma exotique (rtmp, udp…). Un lien de portail
     * Stalker (`stalker://…`, résolu seulement au moment de lire) est accepté : le travailleur le résout puis revérifie.
     */
    fun isDownloadable(url: String): Boolean {
        if (com.ultratv.tv.nativeapp.data.stalker.StalkerLink.isStalker(url)) return true
        val u = url.trim().lowercase()
        if (!(u.startsWith("http://") || u.startsWith("https://"))) return false
        val path = u.substringBefore('?').substringBefore('#')
        return !(path.endsWith(".m3u8") || path.endsWith(".m3u") || path.endsWith(".mpd") || u.contains("output=m3u8") || u.contains("type=m3u8"))
    }

    /** Extension du fichier : celle du flux (`container_extension`), sinon mp4. Jamais plus de 5 caractères alphanumériques. */
    fun extensionOf(url: String, container: String? = null): String {
        container?.trim()?.lowercase()?.takeIf { EXT.matches(it) }?.let { return it }
        val last = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val ext = last.substringAfterLast('.', "").lowercase()
        return ext.takeIf { EXT.matches(it) } ?: "mp4"
    }

    /**
     * Nom de fichier neutre : type + empreinte de (source, type, identifiant). Aucune donnée de la source (hôte,
     * utilisateur, mot de passe, titre) n'apparaît dans le chemin.
     */
    fun fileName(providerId: Long, kind: String, remoteId: String, ext: String): String =
        "${kind.lowercase()}-${sha256("$providerId|$kind|$remoteId").take(20)}.$ext"

    fun partFile(finalFile: File): File = File(finalFile.path + ".part")

    fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * Assez de place ? [remaining] = octets encore à écrire (taille totale − déjà écrit ; ≤ 0 si inconnue, on suppose
     * alors [UNKNOWN_SIZE_GUESS_BYTES]). Toujours garder [SPACE_MARGIN_BYTES] libres.
     */
    fun hasRoom(freeBytes: Long, remaining: Long): Boolean {
        val need = if (remaining > 0) remaining else UNKNOWN_SIZE_GUESS_BYTES
        return freeBytes - need >= SPACE_MARGIN_BYTES
    }

    /** Clé d'un élément du catalogue (lecture hors ligne). */
    fun key(providerId: Long, kind: String, remoteId: String) = "$providerId|$kind|$remoteId"
}

/** Avertissement « connexion » : la plupart des abonnements n'autorisent qu'un flux à la fois, un téléchargement en est un. */
object DownloadConnectionPolicy {
    fun warnOnDownload(streamPlaying: Boolean, maxConnections: Int): Boolean = streamPlaying && maxConnections <= 1
    fun warnOnPlay(downloadsRunning: Boolean, maxConnections: Int): Boolean = downloadsRunning && maxConnections <= 1
}
