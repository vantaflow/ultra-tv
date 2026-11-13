package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgDao
import com.ultratv.tv.nativeapp.data.db.VodInfoDao
import com.ultratv.tv.nativeapp.data.db.VodInfoEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeDao
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.FavoriteDao
import com.ultratv.tv.nativeapp.data.db.FavoriteEntity
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-side catalog repository — joins providers with content tables.
 * Most flows take a provider id; when null, callers should resolve the active
 * provider via [ProviderRepository.firstActive] first.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val providerDao: ProviderDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val epgDao: EpgDao,
    private val vodInfoDao: VodInfoDao,
    private val genreDao: com.ultratv.tv.nativeapp.data.db.GenreDao,
    private val tmdb: com.ultratv.tv.nativeapp.data.tmdb.TmdbRepository,
    private val xtream: XtreamClient,
    private val stalker: com.ultratv.tv.nativeapp.data.stalker.StalkerClient,
    private val profiles: com.ultratv.tv.nativeapp.data.profile.ProfileRepository,
) {
    fun heroSeries(pid: Long): Flow<SeriesEntity?> = seriesDao.observeHero(pid)
    fun heroMovie(pid: Long): Flow<MovieEntity?> = movieDao.observeHero(pid)
    fun heroSeriesList(pid: Long, limit: Int): Flow<List<com.ultratv.tv.nativeapp.data.db.SeriesEntity>> = seriesDao.observeHeroes(pid, limit)
    fun heroMovieList(pid: Long, limit: Int): Flow<List<MovieEntity>> = movieDao.observeHeroes(pid, limit)
    fun latestMovies(pid: Long, limit: Int): Flow<List<MovieEntity>> = movieDao.observeLatest(pid, limit)
    fun latestSeries(pid: Long, limit: Int): Flow<List<com.ultratv.tv.nativeapp.data.db.SeriesEntity>> = seriesDao.observeLatest(pid, limit)
    fun topRatedMovies(pid: Long, limit: Int): Flow<List<MovieEntity>> = movieDao.observeTopRated(pid, limit)
    fun topRatedSeries(pid: Long, limit: Int): Flow<List<com.ultratv.tv.nativeapp.data.db.SeriesEntity>> = seriesDao.observeTopRated(pid, limit)

    /** Image paysage TMDB déjà connue (cache de l'enrichissement), sans requête réseau. */
    suspend fun cachedTmdbBackdrop(m: MovieEntity): String? = tmdb.cachedBackdrop(com.ultratv.tv.nativeapp.data.tmdb.TmdbKind.MOVIE, m.providerId, m.remoteId)

    /** Image paysage TMDB (résolue au besoin, une requête) ; null sans Worker ou titre inconnu. */
    suspend fun resolveTmdbBackdrop(m: MovieEntity): String? = tmdb.backdropFor(m)

    /**
     * Films proches d'un film regardé, classés par ressemblance ([SimilarMovies.rank]) : genres en commun (tous, pas
     * seulement le premier), même réalisateur, acteurs en commun, époque proche. Plus de repli sur la catégorie du
     * fournisseur (« FR | Films 2024 » n'a aucun rapport de thème) : sans assez de films proches, la rangée se masque.
     * Les recommandations TMDB présentes au catalogue (titre normalisé + année ±1) passent en premier, dans l'ordre de TMDB ;
     * le classement local complète, ou répond seul hors ligne / sans TMDB.
     * null si le film n'est plus au catalogue. Titre du film de départ en premier.
     */
    suspend fun similarMovies(pid: Long, remoteId: String, limit: Int): Pair<String, List<MovieEntity>>? {
        val seed = movieDao.byRemoteId(pid, remoteId) ?: return null
        val info = vodInfoDao.get(pid, remoteId)
        // D'abord les recommandations TMDB présentes au catalogue (les films des sources Xtream n'ont presque jamais de genre
        // dans la liste), puis le classement local. Hors ligne / appareil non appairé / film inconnu de TMDB : local seul.
        val fromTmdb = tmdbRelated(pid, seed, info?.tmdbId, limit)
        if (fromTmdb.size >= limit) return seed.title to fromTmdb
        val local = localSimilar(pid, seed, info, limit)
        return seed.title to RelatedMovies.complete(fromTmdb, local, limit) { it.id }
    }

    /** Films du catalogue recommandés par TMDB pour [seed], dans l'ordre de TMDB ; vide si TMDB ne répond pas. */
    private suspend fun tmdbRelated(pid: Long, seed: MovieEntity, sourceTmdbId: String?, limit: Int): List<MovieEntity> = try {
        val credits = tmdb.relatedMovies(seed, sourceTmdbId)
        if (credits.isEmpty()) emptyList()
        else RelatedMovies.match(credits, seed.id, seed.title, limit, { it.id }, { it.title }, { it.year }, accept = { !it.poster.isNullOrBlank() }) { q ->
            FtsQuery.of(q)?.let { movieDao.searchFts(pid, it, 12) }.orEmpty()
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun localSimilar(pid: Long, seed: MovieEntity, info: VodInfoEntity?, limit: Int): List<MovieEntity> {
        val remoteId = seed.remoteId
        val seedFacts = SimilarMovies.Facts(
            genres = SimilarMovies.genres(seed.genre ?: info?.genre),
            people = SimilarMovies.people(seed.cast ?: info?.cast, max = 4),
            director = SimilarMovies.people(info?.director, max = 1).firstOrNull(),
            year = seed.year,
        )
        // Genres automatiques (title_genre) : utilisés quand le film de départ n'a pas de genre texte, ou pour compléter la liste.
        val seedCodes = genreDao.genresOf(pid, "MOVIE", listOf(remoteId)).map { it.genre }.toSet()
        if (seedFacts.genres.isEmpty() && seedFacts.people.isEmpty() && seedFacts.director == null && seedCodes.isEmpty()) return emptyList()
        // Candidats : chaque genre du film (pas seulement le premier), puis réalisateur et acteurs principaux.
        val pool = buildList {
            // Requêtes avec le genre tel qu'écrit dans la base (LIKE), la comparaison se fait ensuite sur la forme normalisée.
            SimilarMovies.rawGenres(seed.genre ?: info?.genre).take(3).forEach { g -> addAll(movieDao.byGenre(pid, g, seed.id, SIMILAR_POOL)) }
            (listOfNotNull(seedFacts.director) + seedFacts.people.take(2)).forEach { n -> addAll(movieDao.byPerson(pid, n, SIMILAR_POOL / 4)) }
        }.distinctBy { it.id }.filter { it.id != seed.id && !it.poster.isNullOrBlank() }
        val ranked = SimilarMovies.rank(seed.title, seedFacts, pool.map { m ->
            m to SimilarMovies.Facts(SimilarMovies.genres(m.genre), SimilarMovies.people(m.cast, max = 6), null, m.year)
        }, limit) { it.title }
        if (ranked.size >= limit || seedCodes.isEmpty()) return ranked
        // Complément par genres automatiques : candidats sans genre texte (ou genre texte différent), comparés par code.
        val codePool = seedCodes.take(3).flatMap { g -> movieDao.byAutoGenre(pid, g, seed.id, SIMILAR_POOL) }
            .distinctBy { it.id }.filter { !it.poster.isNullOrBlank() && ranked.none { r -> r.id == it.id } }
        val codesById = codePool.map { it.remoteId }.chunked(900)
            .flatMap { genreDao.genresOf(pid, "MOVIE", it) }.groupBy({ it.remoteId }, { it.genre })
        val seedByCode = SimilarMovies.Facts(seedCodes, seedFacts.people, seedFacts.director, seed.year)
        val extra = SimilarMovies.rank(seed.title, seedByCode, codePool.map { m ->
            m to SimilarMovies.Facts(codesById[m.remoteId].orEmpty().toSet(), SimilarMovies.people(m.cast, max = 6), null, m.year)
        }, limit - ranked.size) { it.title }
        return ranked + extra
    }
    fun firstChannels(pid: Long, limit: Int): Flow<List<ChannelEntity>> = channelDao.observeFirstClean(pid, limit)
    fun channelsWithLogo(pid: Long, limit: Int): Flow<List<ChannelEntity>> = channelDao.observeTopWithLogo(pid, limit)
    fun favoriteChannels(pid: Long, limit: Int): Flow<List<ChannelEntity>> =
        profiles.currentId.flatMapLatest { channelDao.observeFavoritesTop(pid, limit, it) }
    fun channels(pid: Long): Flow<List<ChannelEntity>> = channelDao.observeForProvider(pid)
    fun topChannels(pid: Long, limit: Int): Flow<List<ChannelEntity>> = channelDao.observeTop(pid, limit)
    fun topMovies(pid: Long, limit: Int): Flow<List<MovieEntity>> = movieDao.observeTop(pid, limit)
    fun topSeries(pid: Long, limit: Int): Flow<List<SeriesEntity>> = seriesDao.observeTop(pid, limit)
    fun channelsForCategory(pid: Long, categoryRemoteId: String): Flow<List<ChannelEntity>> =
        channelDao.observeForCategory(pid, categoryRemoteId)
    // Par paquets de 900 identifiants (limite de variables SQLite) : aucun favori n'est perdu au-delà.
    fun moviesByRemoteIds(pid: Long, ids: List<String>): Flow<List<MovieEntity>> =
        if (ids.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else kotlinx.coroutines.flow.combine(ids.chunked(900).map { movieDao.observeByRemoteIds(pid, it) }) { parts -> parts.flatMap { it } }
    fun seriesByRemoteIds(pid: Long, ids: List<String>): Flow<List<SeriesEntity>> =
        if (ids.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else kotlinx.coroutines.flow.combine(ids.chunked(900).map { seriesDao.observeByRemoteIds(pid, it) }) { parts -> parts.flatMap { it } }
    fun episodes(seriesId: Long): Flow<List<EpisodeEntity>> = episodeDao.observeForSeries(seriesId)

    fun categories(pid: Long, kind: String): Flow<List<CategoryEntity>> =
        categoryDao.observeForProviderKind(pid, kind)

    suspend fun channelById(id: Long): ChannelEntity? = channelDao.byId(id)
    suspend fun movieById(id: Long): MovieEntity? = movieDao.byId(id)
    suspend fun seriesById(id: Long): SeriesEntity? = seriesDao.byId(id)
    suspend fun seriesByRemote(pid: Long, remoteId: String): SeriesEntity? = seriesDao.byRemoteId(pid, remoteId)
    suspend fun episodeById(id: Long): EpisodeEntity? = episodeDao.byId(id)

    /**
     * Lazily syncs episodes when the user opens a series detail. Dispatches
     * to the right client based on provider kind — Xtream uses `get_series_info`
     * (per-season episode map).
     * M3U has no concept of series and is silently skipped.
     */
    suspend fun loadEpisodes(seriesId: Long) {
        val s = seriesDao.byId(seriesId) ?: return
        val p = providerDao.byId(s.providerId) ?: return
        val now = System.currentTimeMillis()
        // Fiche déjà chargée il y a moins de 30 min et épisodes présents : pas de nouveau get_series_info à chaque ouverture.
        val last = episodesFetchedAt[seriesId]
        if (last != null && now - last < 30 * 60_000L && episodeDao.forSeries(seriesId).isNotEmpty()) return
        val eps = when (p.kind) {
            "XTREAM" -> {
                val d = xtream.fetchSeriesDetail(p, s.remoteId, s.id) ?: return
                // Mise à jour seulement si quelque chose change : chaque écriture de « series » relance les requêtes de l'accueil.
                if (d.plot != s.plot || d.genre != s.genre || d.cast != s.cast || d.backdrop != s.backdrop || d.year != s.year || d.rating != s.rating)
                    seriesDao.updateInfo(s.id, d.plot, d.genre, d.cast, d.backdrop, d.year, d.rating)
                d.episodes
            }
            // Portail Stalker : saisons/épisodes du portail, ou tableau `series` de la série (voir StalkerClient.episodes).
            "STALKER" -> stalker.episodes(p, s).ifEmpty { return }
            else -> return
        }
        episodesFetchedAt[seriesId] = now
        // Liste identique (hors identifiants internes) : rien à réécrire, pas de clignotement ni de perte de focus.
        val current = episodeDao.forSeries(seriesId).map { it.copy(id = 0) }
        if (current.sortedWith(EPISODE_ORDER) == eps.map { it.copy(id = 0) }.sortedWith(EPISODE_ORDER)) return
        episodeDao.replaceForSeries(seriesId, eps)
    }

    private val episodesFetchedAt = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    /**
     * Détails d'un film : cache Room (TTL 7 jours ; 1 jour si la source n'a rien renvoyé), sinon get_vod_info.
     * Xtream seulement ; une erreur réseau rend le cache périmé s'il existe, sinon null (la fiche reste lisible).
     */
    suspend fun vodInfo(m: MovieEntity, now: Long = System.currentTimeMillis()): VodInfoEntity? {
        val cached = vodInfoDao.get(m.providerId, m.remoteId)
        if (cached != null && now - cached.fetchedAt < vodInfoTtlMs(cached)) return cached
        val p = providerDao.byId(m.providerId)
        if (p == null || p.kind != "XTREAM") return cached
        val fetched = runCatching { xtream.fetchVodInfo(p, m.remoteId) }.getOrNull() ?: return cached
        val row = VodInfoEntity(
            providerId = m.providerId, remoteId = m.remoteId, plot = fetched.plot, cast = fetched.cast, director = fetched.director,
            genre = fetched.genre, duration = fetched.duration, releaseDate = fetched.releaseDate, rating = fetched.rating,
            backdrop = fetched.backdrop, trailer = fetched.trailer, tmdbId = fetched.tmdbId, country = fetched.country,
            originalName = fetched.originalName, fetchedAt = now,
        )
        vodInfoDao.upsert(row)
        return row
    }

    /**
     * Recherche plein texte (FTS4) : instantanée même sur 180 000 titres. Une seule entrée par œuvre
     * ([SearchDedup]) ; les programmes EPG (7 jours, une ligne par chaîne) s'ajoutent aux chaînes/films/séries.
     */
    suspend fun search(pid: Long, query: String, limit: Int = 40, nowMs: Long = System.currentTimeMillis(), includePrograms: Boolean = true): SearchResults {
        val match = FtsQuery.of(query) ?: return SearchResults()
        val cap = limit * 3
        val rawMovies = movieDao.searchFts(pid, match, cap)
        val rawSeries = seriesDao.searchFts(pid, match, cap)
        // Mot courant : la recherche plein texte rend ses lignes dans l'ordre de la table et s'arrête au plafond, le titre
        // cherché pouvait être au-delà. On ajoute alors, en tête, les titres qui commencent par la saisie.
        val prefix = query.trim().takeIf { it.length >= 2 }?.let { likePrefix(it) }
        val mHead = if (prefix != null && rawMovies.size >= cap) movieDao.titlePrefix(pid, prefix, PREFIX_LIMIT) else emptyList()
        val sHead = if (prefix != null && rawSeries.size >= cap) seriesDao.titlePrefix(pid, prefix, PREFIX_LIMIT) else emptyList()
        var movies = SearchDedup.movies((mHead + rawMovies).distinctBy { it.id }).take(limit)
        var series = SearchDedup.series((sHead + rawSeries).distinctBy { it.id }).take(limit)
        // Aucun film ni série : repli tolérant aux fautes de frappe (candidats par début de mot, classés par ressemblance).
        var fuzzy = false
        if (movies.isEmpty() && series.isEmpty() && query.trim().length >= 4) {
            FuzzySearch.candidateMatch(query)?.let { loose ->
                fuzzy = true
                movies = SearchDedup.movies(FuzzySearch.rank(query, movieDao.searchFts(pid, loose, FUZZY_POOL), { it.title }, limit * 3)).take(limit)
                series = SearchDedup.series(FuzzySearch.rank(query, seriesDao.searchFts(pid, loose, FUZZY_POOL), { it.title }, limit * 3)).take(limit)
            }
        }
        return SearchResults(
            channels = channelDao.searchFts(pid, match, limit),
            movies = movies,
            series = series,
            // Programmes : LIKE sur tout le guide (pas d'index possible) — seulement à partir de 3 caractères.
            programs = if (includePrograms && query.trim().length >= 3) searchPrograms(pid, query, nowMs) else emptyList(),
            fuzzy = fuzzy && (movies.isNotEmpty() || series.isNotEmpty()),
        )
    }

    suspend fun searchPrograms(pid: Long, query: String, nowMs: Long = System.currentTimeMillis()): List<ProgramHit> {
        val pattern = EpgSearch.likePattern(query) ?: return emptyList()
        val rows = epgDao.searchPrograms(pid, pattern, nowMs, nowMs + EpgSearch.WINDOW_MS, EpgSearch.SQL_LIMIT)
        return EpgSearch.select(rows, query, nowMs)
    }

    suspend fun channelsByRemoteIds(pid: Long, ids: List<String>): List<ChannelEntity> =
        ids.chunked(500).flatMap { channelDao.byRemoteIds(pid, it) }

    suspend fun searchChannels(pid: Long, query: String, limit: Int = 60): List<ChannelEntity> {
        val match = FtsQuery.of(query) ?: return emptyList()
        return channelDao.searchFts(pid, match, limit)
    }

    fun favoritesByKind(pid: Long, kind: String): Flow<List<FavoriteEntity>> =
        profiles.currentId.flatMapLatest { favoriteDao.observeForKind(it, pid, kind) }

    /** Compteurs des raccourcis de l'accueil (COUNT sur index, observés ; l'appelant les espace pendant une synchro). */
    fun channelCount(pid: Long): Flow<Int> = channelDao.observeCount(pid)
    fun movieCount(pid: Long): Flow<Int> = movieDao.observeCount(pid)
    fun seriesCount(pid: Long): Flow<Int> = seriesDao.observeCount(pid)
    fun allFavoritesCount(pid: Long): Flow<Int> = profiles.currentId.flatMapLatest { favoriteDao.observeCountAll(it, pid) }

    fun favoriteCount(pid: Long, kind: String): Flow<Int> =
        profiles.currentId.flatMapLatest { favoriteDao.observeCount(it, pid, kind) }

    fun isFavorite(pid: Long, kind: String, rid: String): Flow<Boolean> =
        profiles.currentId.flatMapLatest { favoriteDao.observeIsFavorite(it, pid, kind, rid) }

    suspend fun setFavorite(pid: Long, kind: String, rid: String, on: Boolean) {
        val prof = profiles.currentIdNow
        if (on) favoriteDao.add(FavoriteEntity(pid, kind, rid, prof))
        else favoriteDao.remove(prof, pid, kind, rid)
    }

    suspend fun setCategoryLocked(catId: Long, locked: Boolean) =
        categoryDao.setLocked(catId, locked)

    fun upcomingEpg(channelId: Long): kotlinx.coroutines.flow.Flow<List<EpgEntity>> =
        epgDao.observeUpcoming(channelId, System.currentTimeMillis())

    private val shortEpgTried = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val shortEpgGate = kotlinx.coroutines.sync.Semaphore(3)

    /**
     * Secours quand le guide XMLTV n'est pas (encore) là : programme court du fournisseur pour les chaînes données,
     * au plus une tentative par chaîne toutes les 15 min, 3 requêtes à la fois. Renvoie true si au moins une a été écrite.
     */
    suspend fun ensureShortEpg(channelIds: List<Long>): Boolean = kotlinx.coroutines.coroutineScope {
        val now = System.currentTimeMillis()
        val todo = channelIds.filter { now - (shortEpgTried[it] ?: 0L) > 15 * 60_000L }.take(12)
        todo.forEach { shortEpgTried[it] = now }
        todo.map { id -> async { shortEpgGate.withPermit { refreshShortEpgCount(id) > 0 } } }.awaitAll().any { it }
    }

    private suspend fun refreshShortEpgCount(channelId: Long): Int {
        val ch = channelDao.byId(channelId) ?: return 0
        val p = providerDao.byId(ch.providerId) ?: return 0
        if (p.kind != "XTREAM" && p.kind != "STALKER") return 0
        var rows = runCatching { shortEpgOf(p, ch) }.getOrDefault(emptyList())
        // Chaîne décalée sans programme propre : celui de la chaîne de base, décalé de N heures.
        // Sinon, même chaîne sous le même nom dans une autre catégorie (seule l'une porte souvent l'identifiant EPG).
        if (rows.isEmpty()) (TimeshiftChannels.parse(ch.title) ?: (ch.title.trim() to 0)).let { (base, h) ->
            val shift = h * 3_600_000L
            val now = System.currentTimeMillis()
            val baseIds = channelDao.idsByTitle(p.id, base).filter { it != ch.id }
            for (bid in baseIds) {
                var src = epgDao.rangeForChannels(listOf(bid), now - shift - 30 * 60_000, now + 6 * 3_600_000L)
                if (src.isEmpty()) { refreshShortEpg(bid); src = epgDao.rangeForChannels(listOf(bid), now - shift - 30 * 60_000, now + 6 * 3_600_000L) }
                if (src.isNotEmpty()) { rows = src.map { it.copy(id = 0, channelId = ch.id, startMs = it.startMs + shift, endMs = it.endMs + shift) }; break }
            }
        }
        if (rows.isNotEmpty()) { epgDao.deleteForChannel(ch.id); epgDao.upsertAll(rows) }
        return rows.size
    }

    /** Programme court selon le type de source (Xtream `get_short_epg&stream_id`, Stalker `itv/get_short_epg&ch_id`). */
    private suspend fun shortEpgOf(p: com.ultratv.tv.nativeapp.data.db.ProviderEntity, ch: ChannelEntity): List<EpgEntity> = when (p.kind) {
        "STALKER" -> stalker.shortEpg(p, ch.remoteId, ch.id)
        "XTREAM" -> xtream.fetchShortEpg(p, ch.remoteId, ch.id)
        else -> emptyList()
    }

    /** Pulls short EPG for one channel from Xtream. Safe to call repeatedly — no-op on failure. */
    suspend fun refreshShortEpg(channelId: Long) {
        val ch = channelDao.byId(channelId) ?: return
        val p = providerDao.byId(ch.providerId) ?: return
        val rows = runCatching { shortEpgOf(p, ch) }.getOrDefault(emptyList())
        if (rows.isNotEmpty()) {
            epgDao.deleteForChannel(ch.id)
            epgDao.upsertAll(rows)
        }
    }
}

/** Motif LIKE « commence par » : jokers %, _ et \\ de la saisie échappés. */
internal fun likePrefix(q: String): String = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

private const val PREFIX_LIMIT = 200

data class SearchResults(
    val channels: List<ChannelEntity> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val programs: List<ProgramHit> = emptyList(),
    /** Films et séries trouvés seulement par le repli tolérant aux fautes (aucun titre ne contient la saisie). */
    val fuzzy: Boolean = false,
)

private const val DAY_MS = 24L * 3_600_000

/** Durée de validité d'une ligne de cache : 7 jours si elle porte des détails, 1 jour si elle est vide. */
internal fun vodInfoTtlMs(v: VodInfoEntity): Long =
    if (v.plot == null && v.cast == null && v.genre == null && v.backdrop == null) DAY_MS else 7 * DAY_MS

private val EPISODE_ORDER = compareBy<com.ultratv.tv.nativeapp.data.db.EpisodeEntity>({ it.season }, { it.episode }, { it.remoteId })

/** Premier genre d'une fiche (« Action, Aventure » → « Action »), assez long pour un LIKE utile ; null sinon. */
fun similarGenreKey(genre: String?): String? =
    genre?.split(',', '/', '|', ';')?.map { it.trim() }?.firstOrNull { it.length >= 3 }?.replace("%", "")?.replace("_", "")?.takeIf { it.length >= 3 }

/** Candidats lus par genre pour « Parce que vous avez regardé… » (classés ensuite en mémoire). */
private const val SIMILAR_POOL = 200

/** Candidats lus pour la recherche approchée (classés ensuite en mémoire). */
private const val FUZZY_POOL = 800
