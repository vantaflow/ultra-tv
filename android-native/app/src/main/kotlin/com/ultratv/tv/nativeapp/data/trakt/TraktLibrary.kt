package com.ultratv.tv.nativeapp.data.trakt

import com.ultratv.tv.nativeapp.data.db.CatalogLite
import org.json.JSONArray
import org.json.JSONObject

/** Un film ou une série de Trakt. `keys` = clés de rapprochement précalculées par le Worker (voir [TraktMatch]). */
data class TraktItem(val isShow: Boolean, val tmdb: Int?, val year: Int?, val title: String, val keys: List<String>)

/** Série vue : l'item et ses épisodes vus, au format « SxE » (« 1x2 »). */
data class TraktWatchedShow(val item: TraktItem, val episodes: Set<String>)

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
                watchedShows += TraktWatchedShow(item(so), set)
            }
            return TraktLibrary(
                linked = true,
                updatedAt = o.optLong("updatedAt", 0L),
                watchlist = items(o.optJSONArray("watchlist")),
                recommendations = items(o.optJSONArray("recommendations")),
                watchedMovies = items(watched?.optJSONArray("movies")),
                watchedShows = watchedShows,
            )
        }
    }
}

/** Carte Trakt affichable : un élément du catalogue de l'utilisateur. */
data class TraktCard(val isShow: Boolean, val id: Long, val title: String, val poster: String?, val year: Int?, val rating: Double?)

/** Les deux rangées de l'accueil : UNIQUEMENT des éléments disponibles dans la playlist chargée. */
data class TraktRows(val watchlist: List<TraktCard>, val recommendations: List<TraktCard>) {
    val isEmpty: Boolean get() = watchlist.isEmpty() && recommendations.isEmpty()
    companion object { val EMPTY = TraktRows(emptyList(), emptyList()) }
}

/** Rapprochement pur Trakt ↔ catalogue (testé sans Android ni base). */
object TraktAvailability {
    /** Toutes les clés recherchées pour un type donné (films ou séries) : sert à ne garder du catalogue que le nécessaire. */
    fun wantedKeys(lists: List<List<TraktItem>>, shows: Boolean): Set<String> {
        val out = HashSet<String>()
        for (l in lists) for (it in l) if (it.isShow == shows) out.addAll(it.keys)
        return out
    }

    /**
     * Indexe les entrées du catalogue dont la clé figure dans [wanted] (clé → entrées). Les 50 000 titres d'un gros
     * catalogue ne sont donc jamais tous conservés : seul l'utile reste en mémoire.
     */
    fun indexInto(index: MutableMap<String, MutableList<CatalogLite>>, wanted: Set<String>, entries: List<CatalogLite>) {
        for (e in entries) {
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
