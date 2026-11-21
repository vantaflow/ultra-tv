package com.ultratv.tv.nativeapp.data.discovery

import com.ultratv.tv.nativeapp.data.mediaserver.Plex

/**
 * Logique pure (sans réseau ni Android) de l'assistant « Ma bibliothèque » et de l'écran « Mes bibliothèques ».
 */
object MediaLibraryLogic {
    /** Bibliothèque proposée à l'étape 3 : [kind] = `MOVIE` | `SERIES`, [remoteId] = identifiant côté serveur. */
    data class LibraryChoice(val kind: String, val remoteId: String, val name: String)

    /** Bibliothèques à DÉSACTIVER avant la première synchro : celles qui ne sont pas cochées. */
    fun toDisable(all: List<LibraryChoice>, checked: Set<String>): List<LibraryChoice> = all.filter { keyOf(it) !in checked }

    /** Clé de case à cocher (un même identifiant peut exister en films et en séries). */
    fun keyOf(c: LibraryChoice) = "${c.kind}:${c.remoteId}"

    /** Tout est coché au départ. */
    fun allChecked(all: List<LibraryChoice>): Set<String> = all.map { keyOf(it) }.toSet()

    /** On ne peut importer que si au moins une bibliothèque est cochée. */
    fun canImport(checked: Set<String>) = checked.isNotEmpty()

    /** Bascule une case. */
    fun toggle(checked: Set<String>, key: String): Set<String> = if (key in checked) checked - key else checked + key

    /** Nature de la connexion retenue d'un serveur Plex : locale, relais Plex ou distante. */
    enum class ConnKind { LOCAL, REMOTE, RELAY }

    fun connKind(c: Plex.Connection): ConnKind = when {
        c.relay -> ConnKind.RELAY
        c.local -> ConnKind.LOCAL
        else -> ConnKind.REMOTE
    }

    /** Meilleure connexion annoncée d'un serveur (locale, puis distante, puis relais : l'ordre de [Plex.Server.connections]). */
    fun bestConnKind(s: Plex.Server): ConnKind? = s.connections.firstOrNull()?.let(::connKind)

    /** État d'une source dans « Mes bibliothèques ». */
    enum class Status { ONLINE, RELAY, OFFLINE }

    /**
     * [reachable] : résultat de la sonde de joignabilité (null = pas encore connu : on se fie à la dernière synchro réussie).
     * Un serveur Plex joint par une adresse publique sur un port de relais est présenté « Relais ».
     */
    fun status(kind: String, baseUrl: String, reachable: Boolean?, lastSyncAt: Long): Status {
        val up = reachable ?: (lastSyncAt > 0)
        if (!up) return Status.OFFLINE
        return if (kind == Plex.KIND && looksLikeRelay(baseUrl)) Status.RELAY else Status.ONLINE
    }

    /** Heuristique : adresse Plex `*.plex.direct` dont l'IP encodée n'est pas privée (relais ou accès distant par le port 8443). */
    fun looksLikeRelay(baseUrl: String): Boolean {
        val host = baseUrl.substringAfter("://").substringBefore('/').substringBefore(':')
        if (!host.endsWith(".plex.direct")) return false
        val port = baseUrl.substringAfter("://").substringBefore('/').substringAfter(':', "").toIntOrNull()
        val ip = host.substringBefore('.').replace('-', '.')
        return port == 8443 && !isPrivateIp(ip)
    }

    fun isPrivateIp(ip: String): Boolean {
        val p = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31) || p[0] == 127
    }

    /** Hôte et port à sonder pour savoir si la source répond (http(s) ou smb://) ; null si l'adresse est illisible. */
    fun probeTarget(baseUrl: String): Pair<String, Int>? {
        val scheme = baseUrl.substringBefore("://", "").lowercase()
        val rest = baseUrl.substringAfter("://", "").substringBefore('/')
        if (rest.isBlank()) return null
        val hp = rest.substringAfterLast('@')
        val port = hp.substringAfterLast(':', "").toIntOrNull()
        val host = (if (port != null) hp.substringBeforeLast(':') else hp).ifBlank { return null }
        return host to (port ?: when (scheme) { "https" -> 443; "smb" -> 445; else -> 80 })
    }

    /** Fraction de lecture (0..1) d'un titre en cours ; 0 sans durée connue. */
    fun progress(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0 || positionMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** Compte à rebours d'expiration du code Plex, « m:ss » (jamais négatif). */
    fun countdown(msLeft: Long): String {
        val s = (msLeft.coerceAtLeast(0) / 1000)
        return "%d:%02d".format(s / 60, s % 60)
    }

    /** Nom de fichier d'un lien `nas://` : dernier segment du chemin, pour le panneau « Reconnu automatiquement ». */
    fun nasFileName(streamUrl: String): String? =
        com.ultratv.tv.nativeapp.data.nas.Nas.NasLink.parse(streamUrl)?.path?.lastOrNull()

    /** Libellé « titre (année) » d'un titre reconnu. */
    fun recognisedTitle(title: String, year: Int?): String = if (year != null && year > 0) "$title ($year)" else title

    /** Affiche de la rangée d'un serveur : [progress] > 0 pour un titre en cours de lecture. */
    data class Tile(val isSeries: Boolean, val id: Long, val title: String, val poster: String?, val progress: Float)

    /** Affiches par rangée de « Mes bibliothèques » ; au plus [LIBRARY_SHELVES] rangées « bibliothèque » par serveur. */
    const val SHELF_MAX = 20
    const val LIBRARY_SHELVES = 6

    /**
     * Rangées d'un serveur dans l'ordre : Reprendre, Ajoutés récemment, Films, Séries, puis ses bibliothèques. Une rangée vide est
     * omise ; la rangée « récents » qui reprendrait exactement « Films » ou « Séries » seuls (serveur sans l'autre type) est omise.
     */
    fun shelfPlan(
        resume: List<Tile>, recent: List<Tile>, movies: List<Tile>, series: List<Tile>, libraries: List<com.ultratv.tv.nativeapp.ui.library.Shelf>,
    ): List<com.ultratv.tv.nativeapp.ui.library.Shelf> {
        val k = com.ultratv.tv.nativeapp.ui.library.ShelfKind.entries
        val dupRecent = (movies.isEmpty() || series.isEmpty()) && recent.map { it.isSeries to it.id } == (movies + series).map { it.isSeries to it.id }
        return buildList {
            if (resume.isNotEmpty()) add(com.ultratv.tv.nativeapp.ui.library.Shelf(k[0], "", resume, null))
            if (recent.isNotEmpty() && !dupRecent) add(com.ultratv.tv.nativeapp.ui.library.Shelf(k[1], "", recent, null))
            if (movies.isNotEmpty()) add(com.ultratv.tv.nativeapp.ui.library.Shelf(k[2], "", movies, false))
            if (series.isNotEmpty()) add(com.ultratv.tv.nativeapp.ui.library.Shelf(k[3], "", series, true))
            addAll(libraries)
        }
    }

    /**
     * Rangée « Reprendre » : une tuile par titre, la plus récente d'abord, au plus [max]. Deux épisodes entamés d'une même
     * série donnent chacun la tuile de la série : sans dédoublonnage la rangée portait deux clés identiques et plantait.
     */
    fun resumeTiles(started: List<Tile>, max: Int): List<Tile> = started.distinctBy { it.isSeries to it.id }.take(max)

    /** Rangée d'affiches : titres en cours d'abord, puis derniers ajouts, sans doublon, au plus [max]. */
    fun pickTiles(progressed: List<Tile>, latest: List<Tile>, max: Int): List<Tile> =
        (progressed + latest).distinctBy { it.isSeries to it.id }.take(max)

    /** Initiale et couleur du logo d'un type de source (maquette : J violet, E vert, P ambre, N bleu). */
    fun logo(kind: String): Pair<String, Long> = when (kind) {
        "JELLYFIN" -> "J" to 0xFF7C3AED
        "EMBY" -> "E" to 0xFF16A34A
        Plex.KIND -> "P" to 0xFFCA8A04
        else -> "N" to 0xFF2563EB
    }

    fun logo(kind: MediaDiscovery.Kind) = logo(kind.name)
}
