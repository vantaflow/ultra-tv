package com.ultratv.tv.nativeapp.data.trakt

import com.ultratv.tv.nativeapp.data.db.CatalogLite
import org.json.JSONArray
import org.json.JSONObject

/** Un film ou une série de Trakt. `keys` = clés de rapprochement précalculées par le Worker (voir [TraktMatch]). */
data class TraktItem(val isShow: Boolean, val tmdb: Int?, val year: Int?, val title: String, val keys: List<String>)

/** Série vue : l'item, ses épisodes vus (« SxE », « 1x2 ») et l'heure de la dernière vue (ms, 0 = inconnue). */
data class TraktWatchedShow(val item: TraktItem, val episodes: Set<String>, val lastWatchedAt: Long = 0L)

/** Film ou série noté par l'utilisateur (1..10). */
data class TraktRated(val item: TraktItem, val rating: Int)

/** Liste personnelle Trakt (/users/me/lists) : identifiant Trakt, nom, films et séries dans l'ordre de la liste. */
data class TraktUserList(val id: String, val name: String, val items: List<TraktItem>)

/**
 * Lecture en pause chez Trakt (/sync/playback). Épisode : [item] est la SÉRIE, [season]/[episode] sont renseignés.
 * [progress] en %, [pausedAt] en ms, [runtimeMin] = durée Trakt en minutes (null si inconnue).
 */
data class TraktPlayback(val item: TraktItem, val season: Int?, val episode: Int?, val progress: Double, val pausedAt: Long, val runtimeMin: Int?)

/**
 * Bibliothèque Trakt du compte (GET /api/device/trakt/library). [EMPTY] = compte non lié, jamais chargé ou échec :
 * dans tous ces cas, RIEN de Trakt ne s'affiche.
 */
class TraktLibrary(
    val linked: Boolean,
    val updatedAt: Long,
    val watchlist: List<TraktItem>,
    val recommendations: List<TraktItem>,
    val watchedMovies: List<TraktItem>,
    val watchedShows: List<TraktWatchedShow>,
    /** Tendances et populaires de Trakt (films et séries mêlés, ordre de Trakt) ; absents de l'ancienne réponse = vides. */
    val trending: List<TraktItem> = emptyList(),
    val popular: List<TraktItem> = emptyList(),
    val ratedMovies: List<TraktRated> = emptyList(),
    val ratedShows: List<TraktRated> = emptyList(),
    val lists: List<TraktUserList> = emptyList(),
    val playback: List<TraktPlayback> = emptyList(),
) {
    // Clé de rapprochement → années des films vus (une clé peut couvrir un remake : on compare l'année).
    private val movieIndex: Map<String, List<Int?>> by lazy {
        buildMap<String, MutableList<Int?>> { for (m in watchedMovies) for (k in m.keys) getOrPut(k) { mutableListOf() }.add(m.year) }
    }
    private val showIndex: Map<String, List<TraktWatchedShow>> by lazy {
        buildMap<String, MutableList<TraktWatchedShow>> { for (s in watchedShows) for (k in s.item.keys) getOrPut(k) { mutableListOf() }.add(s) }
    }

    val hasWatched: Boolean get() = watchedMovies.isNotEmpty() || watchedShows.isNotEmpty()

    /** Ce film du catalogue est-il vu sur Trakt ? */
    fun isMovieWatched(title: String, year: Int?): Boolean {
        if (!linked || watchedMovies.isEmpty()) return false
        val years = movieIndex[TraktMatch.matchKey(title)] ?: return false
        return years.any { TraktMatch.yearsCompatible(year, it) }
    }

    /** Épisodes vus (« SxE ») de cette série du catalogue ; vide si la série n'est pas dans l'historique Trakt. */
    fun watchedEpisodes(title: String, year: Int?): Set<String> {
        if (!linked || watchedShows.isEmpty()) return emptySet()
        val shows = showIndex[TraktMatch.matchKey(title)] ?: return emptySet()
        val out = HashSet<String>()
        for (s in shows) if (TraktMatch.yearsCompatible(year, s.item.year)) out += s.episodes
        return out
    }

    /** Ce film / cette série du catalogue est-il dans la watchlist Trakt ? */
    fun isInWatchlist(isShow: Boolean, title: String, year: Int?): Boolean {
        if (!linked) return false
        val k = TraktMatch.matchKey(title).ifEmpty { return false }
        return watchlist.any { it.isShow == isShow && it.matches(k, year) }
    }

    /** Note (1..10) donnée sur Trakt à ce film / cette série du catalogue, ou null. */
    fun ratingFor(isShow: Boolean, title: String, year: Int?): Int? {
        if (!linked) return null
        val k = TraktMatch.matchKey(title).ifEmpty { return null }
        return (if (isShow) ratedShows else ratedMovies).firstOrNull { it.item.matches(k, year) }?.rating
    }

    // ── Mises à jour locales immédiates (après une écriture réussie, avant la relecture du Worker) ──

    /** Ajout / retrait de la watchlist. */
    fun withWatchlist(isShow: Boolean, title: String, year: Int?, add: Boolean): TraktLibrary {
        val k = TraktMatch.matchKey(title)
        val rest = watchlist.filterNot { it.isShow == isShow && it.matches(k, year) }
        return copy(watchlist = if (add) listOf(localItem(isShow, title, year)) + rest else rest)
    }

    /** Film marqué vu / non vu. */
    fun withMovieWatched(title: String, year: Int?, watched: Boolean): TraktLibrary {
        val k = TraktMatch.matchKey(title)
        val rest = watchedMovies.filterNot { it.matches(k, year) }
        return copy(watchedMovies = if (watched) rest + localItem(false, title, year) else rest)
    }

    /** Épisodes (« SxE ») d'une série marqués vus / non vus ; la série devient la plus récemment vue si ajout. */
    fun withEpisodes(title: String, year: Int?, episodes: Set<String>, watched: Boolean, now: Long = System.currentTimeMillis()): TraktLibrary {
        val k = TraktMatch.matchKey(title)
        var found = false
        val shows = watchedShows.mapNotNull { s ->
            if (!s.item.matches(k, year)) return@mapNotNull s
            found = true
            val eps = if (watched) s.episodes + episodes else s.episodes - episodes
            if (eps.isEmpty()) null else s.copy(episodes = eps, lastWatchedAt = if (watched) now else s.lastWatchedAt)
        }
        val out = if (!found && watched) shows + TraktWatchedShow(localItem(true, title, year), episodes, now) else shows
        return copy(watchedShows = out)
    }

    /** Note donnée (1..10) ou retirée (0). */
    fun withRating(isShow: Boolean, title: String, year: Int?, rating: Int): TraktLibrary {
        val k = TraktMatch.matchKey(title)
        val list = if (isShow) ratedShows else ratedMovies
        val rest = list.filterNot { it.item.matches(k, year) }
        val next = if (rating in 1..10) listOf(TraktRated(localItem(isShow, title, year), rating)) + rest else rest
        return if (isShow) copy(ratedShows = next) else copy(ratedMovies = next)
    }

    private fun TraktItem.matches(key: String, y: Int?) = key.isNotEmpty() && key in keys && TraktMatch.yearsCompatible(y, year)

    private fun localItem(isShow: Boolean, title: String, year: Int?) =
        TraktItem(isShow, null, year, title, listOf(TraktMatch.matchKey(title)).filter { it.isNotEmpty() })

    private fun copy(
        watchlist: List<TraktItem> = this.watchlist,
        watchedMovies: List<TraktItem> = this.watchedMovies,
        watchedShows: List<TraktWatchedShow> = this.watchedShows,
        ratedMovies: List<TraktRated> = this.ratedMovies,
        ratedShows: List<TraktRated> = this.ratedShows,
    ) = TraktLibrary(linked, updatedAt, watchlist, recommendations, watchedMovies, watchedShows, trending, popular, ratedMovies, ratedShows, lists, playback)

    companion object {
        val EMPTY = TraktLibrary(false, 0L, emptyList(), emptyList(), emptyList(), emptyList())

        /** Clé « SxE » d'un épisode, comme dans `watched.shows[].episodes`. */
        fun episodeKey(season: Int, episode: Int) = "${season}x$episode"

        private fun items(a: JSONArray?): List<TraktItem> {
            if (a == null) return emptyList()
            val out = ArrayList<TraktItem>(a.length())
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                out += item(o)
            }
            return out
        }

        private fun item(o: JSONObject): TraktItem {
            val keysJson = o.optJSONArray("keys")
            val keys = ArrayList<String>(keysJson?.length() ?: 0)
            if (keysJson != null) for (i in 0 until keysJson.length()) keysJson.optString(i, "").takeIf { it.isNotEmpty() }?.let(keys::add)
            return TraktItem(
                isShow = o.optString("type") == "show",
                tmdb = if (o.isNull("tmdb")) null else o.optInt("tmdb").takeIf { it > 0 },
                year = if (o.isNull("year")) null else o.optInt("year").takeIf { it > 0 },
                title = o.optString("title", ""),
                keys = keys,
            )
        }

        private fun optInt(o: JSONObject, name: String): Int? = if (!o.has(name) || o.isNull(name)) null else o.optInt(name)

        private fun rated(a: JSONArray?): List<TraktRated> {
            if (a == null) return emptyList()
            val out = ArrayList<TraktRated>(a.length())
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val r = o.optInt("rating", 0)
                if (r in 1..10) out += TraktRated(item(o), r)
            }
            return out
        }

        private fun lists(a: JSONArray?): List<TraktUserList> {
            if (a == null) return emptyList()
            val out = ArrayList<TraktUserList>(a.length())
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                val name = o.optString("name", "")
                if (id.isNotEmpty() && name.isNotEmpty()) out += TraktUserList(id, name, items(o.optJSONArray("items")))
            }
            return out
        }

        private fun playback(a: JSONArray?): List<TraktPlayback> {
            if (a == null) return emptyList()
            val out = ArrayList<TraktPlayback>(a.length())
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val it = item(o)
                val progress = o.optDouble("progress", 0.0)
                if (!(progress > 0.0 && progress < 100.0)) continue
                val season = optInt(o, "season")
                val episode = optInt(o, "episode")
                if (it.isShow && (season == null || episode == null)) continue
                out += TraktPlayback(it, if (it.isShow) season else null, if (it.isShow) episode else null, progress, o.optLong("pausedAt", 0L), optInt(o, "runtime")?.takeIf { r -> r > 0 })
            }
            return out
        }

        /** Analyse la réponse du Worker. Lève une exception si le JSON est illisible (l'appelant garde l'ancienne valeur). */
        fun parse(body: String): TraktLibrary {
            val o = JSONObject(body)
            if (!o.optBoolean("linked", false)) return EMPTY
            val watched = o.optJSONObject("watched")
            val shows = watched?.optJSONArray("shows")
            val watchedShows = ArrayList<TraktWatchedShow>()
            if (shows != null) for (i in 0 until shows.length()) {
                val so = shows.optJSONObject(i) ?: continue
                val eps = so.optJSONArray("episodes")
                val set = HashSet<String>()
                if (eps != null) for (j in 0 until eps.length()) eps.optString(j, "").takeIf { it.isNotEmpty() }?.let(set::add)
                watchedShows += TraktWatchedShow(item(so), set, so.optLong("lastWatchedAt", 0L))
            }
            val ratings = o.optJSONObject("ratings")
            return TraktLibrary(
                linked = true,
                updatedAt = o.optLong("updatedAt", 0L),
                watchlist = items(o.optJSONArray("watchlist")),
                recommendations = items(o.optJSONArray("recommendations")),
                watchedMovies = items(watched?.optJSONArray("movies")),
                watchedShows = watchedShows,
                trending = items(o.optJSONArray("trending")),
                popular = items(o.optJSONArray("popular")),
                ratedMovies = rated(ratings?.optJSONArray("movies")),
                ratedShows = rated(ratings?.optJSONArray("shows")),
                lists = lists(o.optJSONArray("lists")),
                playback = playback(o.optJSONArray("playback")),
            )
        }
    }
}

/** Carte Trakt affichable : un élément du catalogue de l'utilisateur. */
data class TraktCard(val isShow: Boolean, val id: Long, val title: String, val poster: String?, val year: Int?, val rating: Double?)

/** Liste personnelle Trakt prête à afficher (seulement ses éléments disponibles dans la playlist). */
data class TraktListRow(val id: String, val name: String, val cards: List<TraktCard>)

/** « À suivre » : la série du catalogue et le prochain épisode DISPONIBLE dans la playlist. */
data class TraktUpNextCard(val card: TraktCard, val season: Int, val episode: Int)

/** Les rangées Trakt de l'accueil : UNIQUEMENT des éléments disponibles dans la playlist chargée. */
data class TraktRows(
    val watchlist: List<TraktCard>,
    val recommendations: List<TraktCard>,
    val trending: List<TraktCard> = emptyList(),
    val popular: List<TraktCard> = emptyList(),
    val lists: List<TraktListRow> = emptyList(),
    val upNext: List<TraktUpNextCard> = emptyList(),
) {
    val isEmpty: Boolean get() = watchlist.isEmpty() && recommendations.isEmpty() && trending.isEmpty() && popular.isEmpty() &&
        lists.all { it.cards.isEmpty() } && upNext.isEmpty()
    companion object { val EMPTY = TraktRows(emptyList(), emptyList()) }
}

/** Sérialisation des rangées calculées (par source) : affichées tout de suite au lancement suivant, recalculées ensuite. */
object TraktRowsCodec {
    private fun enc(cards: List<TraktCard>) = JSONArray().also { a ->
        for (c in cards) a.put(JSONObject().put("s", c.isShow).put("id", c.id).put("t", c.title)
            .put("p", c.poster ?: JSONObject.NULL).put("y", c.year ?: JSONObject.NULL).put("r", c.rating ?: JSONObject.NULL))
    }

    private fun dec(a: JSONArray?): List<TraktCard> {
        if (a == null) return emptyList()
        val out = ArrayList<TraktCard>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out += TraktCard(
                isShow = o.optBoolean("s"),
                id = o.optLong("id"),
                title = o.optString("t", ""),
                poster = if (o.isNull("p")) null else o.optString("p").takeIf { it.isNotEmpty() },
                year = if (o.isNull("y")) null else o.optInt("y"),
                rating = if (o.isNull("r")) null else o.optDouble("r"),
            )
        }
        return out
    }

    fun encode(byProvider: Map<Long, TraktRows>): String = JSONObject().also { root ->
        for ((pid, r) in byProvider) root.put(pid.toString(), JSONObject()
            .put("w", enc(r.watchlist)).put("r", enc(r.recommendations)).put("t", enc(r.trending)).put("p", enc(r.popular))
            .put("l", JSONArray().also { a -> for (l in r.lists) a.put(JSONObject().put("i", l.id).put("n", l.name).put("c", enc(l.cards))) })
            .put("u", JSONArray().also { a ->
                val cards = enc(r.upNext.map { it.card })
                r.upNext.forEachIndexed { i, u -> a.put(cards.getJSONObject(i).put("se", u.season).put("ep", u.episode)) }
            }))
    }.toString()

    /** Jamais d'exception : un fichier illisible donne une table vide (les rangées sont alors simplement recalculées). */
    fun decode(json: String?): Map<Long, TraktRows> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(json)
            val out = HashMap<Long, TraktRows>()
            for (k in root.keys()) {
                val pid = k.toLongOrNull() ?: continue
                val o = root.optJSONObject(k) ?: continue
                val lists = ArrayList<TraktListRow>()
                o.optJSONArray("l")?.let { a ->
                    for (i in 0 until a.length()) {
                        val l = a.optJSONObject(i) ?: continue
                        lists += TraktListRow(l.optString("i", ""), l.optString("n", ""), dec(l.optJSONArray("c")))
                    }
                }
                val upNext = ArrayList<TraktUpNextCard>()
                o.optJSONArray("u")?.let { a ->
                    val cards = dec(a)
                    for (i in 0 until a.length()) {
                        val u = a.optJSONObject(i) ?: continue
                        cards.getOrNull(i)?.let { c -> upNext += TraktUpNextCard(c, u.optInt("se"), u.optInt("ep")) }
                    }
                }
                out[pid] = TraktRows(dec(o.optJSONArray("w")), dec(o.optJSONArray("r")), dec(o.optJSONArray("t")), dec(o.optJSONArray("p")), lists, upNext)
            }
            out
        } catch (_: Exception) { emptyMap() }
    }
}

/** Rapprochement pur Trakt ↔ catalogue (testé sans Android ni base). */
object TraktAvailability {
    /** Toutes les clés recherchées pour un type donné (films ou séries) : sert à ne garder du catalogue que le nécessaire. */
    fun wantedKeys(lists: List<List<TraktItem>>, shows: Boolean): Set<String> {
        val out = HashSet<String>()
        for (l in lists) for (it in l) if (it.isShow == shows) out.addAll(it.keys)
        return out
    }

    /** Premiers mots des clés recherchées : ensemble de l'écrémage de [mayMatch]. */
    fun firstWordsOf(wanted: Set<String>): Set<String> =
        wanted.mapNotNullTo(HashSet()) { k -> k.substringBefore(' ').takeIf { it.isNotEmpty() } }

    /**
     * Condition NÉCESSAIRE (jamais de faux négatif) pour que `matchKey(title)` soit l'une des clés recherchées : la clé
     * ne fait que RETIRER des mots du titre (préfixe pays, qualité, année, article) hormis « & » (devenu « and », d'où
     * l'acceptation d'office). Son premier mot est donc un mot du titre normalisé (accents retirés, minuscules).
     */
    fun mayMatch(title: String, firstWords: Set<String>): Boolean {
        if (firstWords.isEmpty()) return false
        if (title.indexOf('&') >= 0) return true
        var s = title
        if (s.any { it.code >= 0x80 }) {
            val nfd = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            val sb = StringBuilder(nfd.length)
            var i = 0
            while (i < nfd.length) {
                val cp = nfd.codePointAt(i)
                val t = Character.getType(cp)
                if (t != Character.NON_SPACING_MARK.toInt() && t != Character.COMBINING_SPACING_MARK.toInt() && t != Character.ENCLOSING_MARK.toInt()) sb.appendCodePoint(cp)
                i += Character.charCount(cp)
            }
            s = sb.toString()
        }
        s = s.lowercase(java.util.Locale.ROOT)
        var start = -1
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            if (isAlnum(cp)) {
                if (start < 0) start = i
            } else if (start >= 0) {
                if (s.substring(start, i) in firstWords) return true
                start = -1
            }
            i += n
        }
        return start >= 0 && s.substring(start) in firstWords
    }

    private fun isAlnum(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER, Character.MODIFIER_LETTER, Character.OTHER_LETTER,
        Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true
        else -> false
    }

    /** Les rangées d'après les index du catalogue (pur, testé sans base) ; « À suivre » est calculé à part. */
    fun rows(lib: TraktLibrary, movies: Map<String, List<CatalogLite>>, series: Map<String, List<CatalogLite>>) = TraktRows(
        resolve(lib.watchlist, movies, series),
        resolve(lib.recommendations, movies, series),
        resolve(lib.trending, movies, series),
        resolve(lib.popular, movies, series),
        lib.lists.map { TraktListRow(it.id, it.name, resolve(it.items, movies, series)) }.filter { it.cards.isNotEmpty() },
    )

    /**
     * Indexe les entrées du catalogue dont la clé figure dans [wanted] (clé → entrées). Les 50 000 titres d'un gros
     * catalogue ne sont donc jamais tous conservés : seul l'utile reste en mémoire.
     */
    fun indexInto(
        index: MutableMap<String, MutableList<CatalogLite>>,
        wanted: Set<String>,
        entries: List<CatalogLite>,
        firstWords: Set<String>? = firstWordsOf(wanted),
    ) {
        for (e in entries) {
            // Écrémage bon marché : le coûteux matchKey (une dizaine d'expressions régulières) n'est calculé que pour les
            // titres qui peuvent correspondre. Résultat identique à la règle complète (voir mayMatch).
            if (firstWords != null && !mayMatch(e.title, firstWords)) continue
            val k = TraktMatch.matchKey(e.title)
            if (k.isNotEmpty() && k in wanted) index.getOrPut(k) { mutableListOf() }.add(e)
        }
    }

    /**
     * Ne garde que les éléments Trakt présents dans le catalogue, dans l'ordre de Trakt. Une carte par élément Trakt
     * (entrée à l'année exacte d'abord), et une même entrée du catalogue n'apparaît jamais deux fois.
     */
    fun resolve(items: List<TraktItem>, movies: Map<String, List<CatalogLite>>, series: Map<String, List<CatalogLite>>): List<TraktCard> {
        val seen = HashSet<String>()
        val out = ArrayList<TraktCard>()
        for (item in items) {
            val index = if (item.isShow) series else movies
            val candidates = ArrayList<CatalogLite>()
            for (k in item.keys) index[k]?.let { for (e in it) if (TraktMatch.yearsCompatible(e.year, item.year) && e !in candidates) candidates += e }
            if (candidates.isEmpty()) continue
            val exact = item.year?.let { y -> candidates.firstOrNull { it.year == y } }
            val pick = exact ?: candidates.firstOrNull { "${item.isShow}-${it.id}" !in seen } ?: continue
            if (!seen.add("${item.isShow}-${pick.id}")) continue
            out += TraktCard(item.isShow, pick.id, pick.title, pick.poster, pick.year, pick.rating)
        }
        return out
    }
}
