package com.ultratv.tv.nativeapp.data.nas

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity

/**
 * Partages réseau (NAS) en SMB comme source VOD : toute la LOGIQUE sans réseau — adresse du partage, liens internes, parcours
 * borné d'une arborescence, conversion en entrées du catalogue local. L'accès SMB lui-même ([NasSmb]) n'en est qu'une fine couche.
 *
 * Stockage d'une source (`ProviderEntity`) : `kind` = [KIND], `baseUrl` = `smb://hôte[:port]/partage[/dossier]` (sans identifiants),
 * `username` = identifiant (vide = invité), `password` = mot de passe (chiffré au repos comme celui de toute source).
 * Les identifiants n'apparaissent JAMAIS dans une adresse stockée ni dans un journal : les films et épisodes portent un lien interne
 * [NasLink], et l'accès au fichier passe par un petit relais sur 127.0.0.1 ([NasRelay]).
 */
object Nas {
    const val KIND = "NAS"

    fun isNasKind(kind: String?): Boolean = kind == KIND

    /** Bornes du parcours : un NAS énorme ou une boucle de liens ne doit jamais figer l'appli ni saturer la base. */
    data class Limits(val maxDepth: Int = 8, val maxFiles: Int = 50_000, val maxFolders: Int = 8_000)

    /** Partage désigné par une adresse : [path] = dossier de départ dans le partage (segments, éventuellement vide). */
    data class Address(val host: String, val port: Int, val share: String, val path: List<String>) {
        /** Adresse canonique stockée (sans identifiants). */
        fun url(): String = "smb://" + host + (if (port != 445) ":$port" else "") + "/" + share + path.joinToString("") { "/$it" }

        /** Nom affichable de la racine : dernier dossier, sinon le partage. */
        val rootName: String get() = path.lastOrNull() ?: share
    }

    /**
     * Adresse saisie → [Address] : `smb://hôte/partage[/dossier]`, `\\hôte\partage\dossier` ou `hôte/partage`. Null si l'hôte ou le
     * partage manque, ou si l'adresse contient des identifiants (ils se saisissent dans leurs propres champs).
     */
    fun parseAddress(input: String): Address? {
        var s = input.trim()
        if (s.isEmpty() || s.any { it.isISOControl() }) return null
        s = s.replace('\\', '/')
        val scheme = s.substringBefore("://", "")
        if (scheme.isNotEmpty() && !scheme.equals("smb", ignoreCase = true)) return null
        if (scheme.isNotEmpty()) s = s.substringAfter("://")
        s = s.trimStart('/').substringBefore('?').substringBefore('#')
        val parts = s.split('/').filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val hostPort = parts[0]
        if ('@' in hostPort) return null
        val host = hostPort.substringBefore(':').trim()
        val port = if (':' in hostPort) hostPort.substringAfter(':').toIntOrNull()?.takeIf { it in 1..65535 } ?: return null else 445
        if (host.isEmpty() || !host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) return null
        val rest = parts.drop(1)
        if (rest.any { it == ".." || it == "." }) return null
        return Address(host, port, rest[0], rest.drop(1))
    }

    // ───────────────────────── Parcours ─────────────────────────

    /** Entrée d'un dossier : nom seul, taille en octets, date de modification (secondes depuis 1970, 0 si inconnue). */
    data class Entry(val name: String, val isDir: Boolean, val size: Long = 0, val mtime: Long = 0)

    /** Source de listage (SMB en production, faux dossier en test) : [path] = segments depuis le dossier de départ. */
    fun interface Lister { fun list(path: List<String>): List<Entry> }

    /** Fichier vidéo trouvé : [path] = segments depuis le dossier de départ (le dernier est le nom du fichier). */
    data class VideoFile(val path: List<String>, val size: Long, val mtime: Long) {
        val name: String get() = path.last()
        val folders: List<String> get() = path.dropLast(1)
    }

    /**
     * Parcours en largeur, borné (profondeur, nombre de fichiers, nombre de dossiers), qui ne garde que les vidéos : corbeilles,
     * métadonnées de NAS, extras et échantillons sont ignorés. Un dossier illisible (droits) est sauté sans interrompre le reste.
     */
    fun scan(lister: Lister, limits: Limits = Limits(), cancelled: () -> Boolean = { false }): List<VideoFile> {
        val out = ArrayList<VideoFile>()
        val queue = ArrayDeque<List<String>>()
        queue.add(emptyList())
        var folders = 0
        while (queue.isNotEmpty() && out.size < limits.maxFiles && folders < limits.maxFolders) {
            if (cancelled()) break
            val dir = queue.removeFirst()
            folders++
            val entries = try { lister.list(dir) } catch (e: java.io.IOException) { if (dir.isEmpty()) throw e else continue }
            for (e in entries.sortedBy { it.name.lowercase() }) {
                if (e.name == "." || e.name == ".." || e.name.isEmpty() || '/' in e.name || '\\' in e.name) continue
                if (e.isDir) {
                    if (dir.size + 1 <= limits.maxDepth && !NasNames.isIgnoredFolder(e.name)) queue.add(dir + e.name)
                } else if (NasNames.isVideo(e.name) && !NasNames.isIgnored(e.name)) {
                    if (out.size >= limits.maxFiles) break
                    out.add(VideoFile(dir + e.name, e.size, e.mtime))
                }
            }
        }
        return out
    }

    // ───────────────────────── Liens internes ─────────────────────────

    /**
     * Lien interne `nas://<source>/<chemin en base64 URL>` stocké à la place d'une adresse de flux : le chemin est relatif au dossier
     * de départ de la source ; ni hôte ni identifiants dans le lien.
     */
    object NasLink {
        const val SCHEME = "nas://"

        data class Parsed(val providerId: Long, val path: List<String>)

        fun isNas(url: String?): Boolean = url != null && url.startsWith(SCHEME)

        fun build(providerId: Long, path: List<String>): String =
            "$SCHEME$providerId/" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(path.joinToString("/").toByteArray(Charsets.UTF_8))

        fun parse(url: String): Parsed? {
            if (!isNas(url)) return null
            val rest = url.removePrefix(SCHEME)
            val slash = rest.indexOf('/')
            if (slash <= 0) return null
            val pid = rest.substring(0, slash).toLongOrNull() ?: return null
            val b64 = rest.substring(slash + 1).substringBefore('?')
            if (b64.isEmpty() || !b64.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null
            val text = runCatching { String(java.util.Base64.getUrlDecoder().decode(b64), Charsets.UTF_8) }.getOrNull() ?: return null
            val path = text.split('/').filter { it.isNotEmpty() }
            // Jamais de remontée d'arborescence : un lien forgé ne sort pas du dossier de départ.
            if (path.isEmpty() || path.any { it == ".." || it == "." }) return null
            return Parsed(pid, path)
        }
    }

    // ───────────────────────── Catalogue local ─────────────────────────

    /** Catégorie des fichiers posés directement à la racine : son identifiant (le nom affiché est celui du dossier de départ). */
    const val ROOT_CATEGORY = "_root"

    private fun hash(path: List<String>): String =
        java.security.MessageDigest.getInstance("SHA-1").digest(path.joinToString("/").toByteArray(Charsets.UTF_8)).take(8).joinToString("") { "%02x".format(it) }

    /** Épisode reconnu, avant d'être rattaché à la ligne de sa série en base. */
    data class EpisodeDraft(val seriesKey: String, val season: Int, val episode: Int, val title: String, val file: VideoFile)

    /** Résultat de l'indexation : films, séries, catégories des deux types et épisodes par série (clé = `remoteId` de la série). */
    data class Catalog(
        val movieCategories: List<CategoryEntity>, val seriesCategories: List<CategoryEntity>,
        val movies: List<MovieEntity>, val series: List<SeriesEntity>, val episodes: Map<String, List<EpisodeDraft>>,
    )

    /** Identifiant d'une série : clé du titre normalisé (stable d'une synchro à l'autre, indépendante des dossiers). */
    private fun seriesRemoteId(key: String) = "s_$key"

    fun buildCatalog(providerId: Long, rootName: String, files: List<VideoFile>): Catalog {
        val movies = ArrayList<MovieEntity>()
        val groups = LinkedHashMap<String, MutableList<Pair<NasNames.Parsed, VideoFile>>>()
        val movieCats = LinkedHashMap<String, String>()
        val seriesCats = LinkedHashMap<String, String>()
        fun catOf(f: VideoFile) = if (f.folders.isEmpty()) ROOT_CATEGORY else f.folders.first()
        fun catName(id: String) = if (id == ROOT_CATEGORY) rootName else id

        for (f in files.sortedBy { it.path.joinToString("/").lowercase() }) {
            val p = NasNames.parse(f.name, f.folders)
            if (p.kind == NasNames.Kind.MOVIE) {
                val cat = catOf(f)
                movieCats.putIfAbsent(cat, catName(cat))
                movies += MovieEntity(
                    providerId = providerId, remoteId = hash(f.path), name = p.title, poster = null, categoryId = cat,
                    streamUrl = NasLink.build(providerId, f.path), container = NasNames.extensionOf(f.name).ifEmpty { null },
                    year = p.year, rating = null, plot = null, addedKey = f.mtime,
                )
            } else {
                val title = p.seriesTitle.orEmpty().ifBlank { rootName }
                groups.getOrPut(NasNames.seriesKey(title).ifEmpty { "x" }) { ArrayList() } += p.copy(seriesTitle = title) to f
            }
        }

        val series = ArrayList<SeriesEntity>()
        val episodes = LinkedHashMap<String, List<EpisodeDraft>>()
        for ((key, items) in groups) {
            val rid = seriesRemoteId(key)
            val cat = catOf(items.first().second)
            seriesCats.putIfAbsent(cat, catName(cat))
            // Nom affiché : l'écriture la plus fréquente parmi les fichiers du groupe.
            val name = items.groupingBy { it.first.seriesTitle!! }.eachCount().maxByOrNull { it.value }!!.key
            series += SeriesEntity(
                providerId = providerId, remoteId = rid, name = name, poster = null, categoryId = cat,
                year = items.firstNotNullOfOrNull { it.first.year }, rating = null, plot = null,
                addedKey = items.maxOf { it.second.mtime },
            )
            episodes[rid] = items.map { (p, f) ->
                EpisodeDraft(rid, p.season ?: 1, p.episode ?: 0, p.title, f)
            }.sortedWith(compareBy({ it.season }, { it.episode }, { it.file.path.joinToString("/") }))
        }

        fun cats(kind: String, m: Map<String, String>) = m.entries.sortedBy { it.value.lowercase() }
            .map { CategoryEntity(providerId = providerId, kind = kind, remoteId = it.key, name = it.value) }
        return Catalog(cats("MOVIE", movieCats), cats("SERIES", seriesCats), movies, series, episodes)
    }

    /** Épisodes d'une série au format de la base ; un titre absent devient « S01E02 » (reconnu comme générique, complété par TMDB). */
    fun toEpisodes(providerId: Long, seriesRowId: Long, drafts: List<EpisodeDraft>): List<EpisodeEntity> {
        var next = 1
        val seen = HashSet<String>()
        return drafts.mapNotNull { d ->
            val rid = hash(d.file.path)
            if (!seen.add(rid)) return@mapNotNull null
            val ep = if (d.episode > 0) d.episode else next
            next = ep + 1
            EpisodeEntity(
                seriesId = seriesRowId, remoteId = rid, season = d.season, episode = ep,
                title = d.title.ifBlank { com.ultratv.tv.nativeapp.i18n.LatinDigits.episodeTag(d.season, ep) },
                streamUrl = NasLink.build(providerId, d.file.path), container = NasNames.extensionOf(d.file.name).ifEmpty { null }, plot = null,
            )
        }
    }
}
