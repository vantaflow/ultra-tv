package com.ultratv.tv.nativeapp.ui.home

import androidx.compose.runtime.Immutable
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import com.ultratv.tv.nativeapp.data.trakt.TraktCard
import com.ultratv.tv.nativeapp.ui.catalog.PosterItem

/**
 * Logique PURE de l'accueil (aucune dépendance à la composition) : ordre et visibilité des rangées, encart du haut,
 * méta du hero, progression des cartes « Reprendre », position de défilement vertical (pivot). Testée à part.
 *
 * Les modèles ci-dessous sont construits HORS du fil principal (ViewModel, Dispatchers.Default) et déclarés
 * @Immutable : la composition ne fabrique plus aucune liste et une rangée inchangée est sautée à la recomposition.
 */

/** Rangées de l'accueil, dans l'ordre d'affichage. [contentType] regroupe les rangées de même forme (réutilisation). */
enum class HomeSectionId(val contentType: String) {
    // Accueil v2 : raccourcis (Direct, Films, Séries…) et rangée « Direct » juste sous l'image « À la une », puis le Top 10.
    SHORTCUTS("shortcuts"),
    CHANNELS("channels"),
    TOP10("top10"),
    RESUME("resume"),
    TRAKT_UP_NEXT("posters"),
    // Rangées calculées en local (sans Trakt), montées juste sous « Reprendre » pour qu'on les remarque.
    BECAUSE_WATCHED("posters"),
    TOP_MOVIES("posters"),
    TOP_SERIES("posters"),
    TRAKT_WATCHLIST("posters"),
    TRAKT_RECOMMENDED("posters"),
    TRAKT_TRENDING("posters"),
    TRAKT_POPULAR("posters"),
    // Listes personnelles Trakt (au plus 5, voir TraktPrefs.MAX_LIST_ROWS), dans l'ordre de Trakt.
    TRAKT_LIST_1("posters"),
    TRAKT_LIST_2("posters"),
    TRAKT_LIST_3("posters"),
    TRAKT_LIST_4("posters"),
    TRAKT_LIST_5("posters"),
    RECENT_CHANNELS("resume"),
    LATEST_MOVIES("posters"),
    LATEST_SERIES("posters"),
}

/** Rangées montrées par l'accueil tactile (pas de « regardées récemment » ni de « derniers ajouts », comme avant). */
val MOBILE_SECTIONS: Set<HomeSectionId> = setOf(
    HomeSectionId.RESUME, HomeSectionId.TRAKT_WATCHLIST, HomeSectionId.TRAKT_RECOMMENDED,
    HomeSectionId.TRAKT_TRENDING, HomeSectionId.TRAKT_POPULAR, HomeSectionId.CHANNELS,
    HomeSectionId.TRAKT_UP_NEXT, HomeSectionId.TRAKT_LIST_1, HomeSectionId.TRAKT_LIST_2, HomeSectionId.TRAKT_LIST_3,
    HomeSectionId.TRAKT_LIST_4, HomeSectionId.TRAKT_LIST_5,
    HomeSectionId.BECAUSE_WATCHED, HomeSectionId.TOP_MOVIES, HomeSectionId.TOP_SERIES,
    HomeSectionId.SHORTCUTS,
)

/** Rangées « liste Trakt », dans l'ordre. */
val TRAKT_LIST_SECTIONS: List<HomeSectionId> = listOf(
    HomeSectionId.TRAKT_LIST_1, HomeSectionId.TRAKT_LIST_2, HomeSectionId.TRAKT_LIST_3, HomeSectionId.TRAKT_LIST_4, HomeSectionId.TRAKT_LIST_5,
)

/** Index de la liste Trakt affichée par cette rangée (0..4), ou -1. */
val HomeSectionId.traktListIndex: Int get() = TRAKT_LIST_SECTIONS.indexOf(this)

/** Liste personnelle Trakt prête à afficher (nom tel que sur Trakt). */
@Immutable
data class TraktListUi(val id: String, val name: String, val cards: List<PosterCardUi>)

/** Carte « Reprendre » prête à afficher : titre déjà nettoyé (regex hors fil principal), progression calculée. */
@Immutable
data class ResumeCardUi(
    val key: String,
    val entry: WatchHistoryEntity,
    val title: String,
    val progress: Float,
    /** Minutes restantes, null si la durée est inconnue (aucune méta affichée). */
    val minutesLeft: Int?,
)

/** Affiche d'une rangée (catalogue ou Trakt) : clé unique dans la rangée et type d'œuvre pour le repli TMDB. */
@Immutable
data class PosterCardUi(val key: String, val item: PosterItem, val isShow: Boolean, val kind: TmdbKind)

/** Carte chaîne : programme en cours déjà résolu (titre seul, rafraîchi toutes les minutes). */
@Immutable
data class ChannelCardUi(val channel: ChannelEntity, val nowTitle: String?, val timeRange: String? = null)

/** Compteurs des raccourcis (requêtes COUNT, hors fil principal). */
@Immutable
data class HomeCounts(val channels: Int = 0, val movies: Int = 0, val series: Int = 0, val favorites: Int = 0) {
    /** Rien de synchronisé : les raccourcis seraient tous à zéro, on ne les montre pas. */
    val any: Boolean get() = channels + movies + series > 0
}

/** Raccourcis de l'accueil, dans l'ordre d'affichage. */
enum class ShortcutId { LIVE, MOVIES, SERIES, FAVORITES, GUIDE }

/** Raccourci prêt à afficher : [count] nul = pas de compteur (Guide) ; logos de chaînes (Direct) et vignette (Films, Séries). */
@Immutable
data class ShortcutUi(val id: ShortcutId, val count: Int?, val logos: List<String> = emptyList(), val thumb: String? = null)

/** Les cinq raccourcis, ou rien tant qu'aucun contenu n'est synchronisé. */
fun shortcutsOf(counts: HomeCounts, channelLogos: List<String>, moviePoster: String?, seriesPoster: String?): List<ShortcutUi> =
    if (!counts.any) emptyList() else listOf(
        ShortcutUi(ShortcutId.LIVE, counts.channels, logos = channelLogos.filter { it.isNotBlank() }.take(3)),
        ShortcutUi(ShortcutId.MOVIES, counts.movies, thumb = moviePoster?.takeIf { it.isNotBlank() }),
        ShortcutUi(ShortcutId.SERIES, counts.series, thumb = seriesPoster?.takeIf { it.isNotBlank() }),
        ShortcutUi(ShortcutId.FAVORITES, counts.favorites),
        ShortcutUi(ShortcutId.GUIDE, null),
    )

/** Compteur avec séparateur de milliers de la langue (« 5 347 », « 5,347 »). */
fun formatCount(n: Int, locale: java.util.Locale): String = java.text.NumberFormat.getIntegerInstance(locale).format(n.coerceAtLeast(0))

/** Nombre de titres du « Top 10 aujourd'hui ». */
const val TOP10_SIZE = 10

/**
 * « Top 10 aujourd'hui » : les tendances du jour (Trakt, déjà filtrées sur la source) si elles sont assez nombreuses ;
 * sinon les films les mieux notés. Moins de [MIN_LOCAL_ROW] titres : pas de rangée.
 */
fun top10Of(trending: List<PosterCardUi>, topMovies: List<PosterCardUi>): List<PosterCardUi> {
    val src = (if (trending.size >= MIN_LOCAL_ROW) trending else topMovies).distinctKeys { it.key }
    return if (src.size >= MIN_LOCAL_ROW) src.take(TOP10_SIZE) else emptyList()
}

/** Toutes les rangées de l'accueil en UNE valeur immuable ; [order] dit lesquelles existent et dans quel ordre. */
@Immutable
data class HomeRows(
    val resume: List<ResumeCardUi> = emptyList(),
    val watchlist: List<PosterCardUi> = emptyList(),
    val recommended: List<PosterCardUi> = emptyList(),
    val trending: List<PosterCardUi> = emptyList(),
    val popular: List<PosterCardUi> = emptyList(),
    /** « À suivre (Trakt) » : séries regardées dont l'épisode suivant est disponible (titre « Série · S02E05 »). */
    val upNext: List<PosterCardUi> = emptyList(),
    val traktLists: List<TraktListUi> = emptyList(),
    val recentChannels: List<ResumeCardUi> = emptyList(),
    val latestMovies: List<PosterCardUi> = emptyList(),
    val latestSeries: List<PosterCardUi> = emptyList(),
    /** « Parce que vous avez regardé [becauseTitle] » : films proches du dernier film regardé. */
    val becauseTitle: String = "",
    val because: List<PosterCardUi> = emptyList(),
    val topMovies: List<PosterCardUi> = emptyList(),
    val topSeries: List<PosterCardUi> = emptyList(),
    val channels: List<ChannelCardUi> = emptyList(),
    val shortcuts: List<ShortcutUi> = emptyList(),
    val top10: List<PosterCardUi> = emptyList(),
    val showingFavorites: Boolean = false,
    /** Ordre et rangées masquées choisis par l'utilisateur (Réglages › Affichage). */
    val layout: HomeLayout = HomeLayout.DEFAULT,
) {
    /** Rangées non vides dans l'ordre d'affichage (calculé une fois, à la construction, hors fil principal). */
    val order: List<HomeSectionId> = homeSectionOrder(this)

    companion object { val EMPTY = HomeRows() }
}

/**
 * Ordre de la maquette, réordonné / filtré par la disposition de l'utilisateur ([HomeRows.layout]) ;
 * une rangée vide n'est jamais affichée (ni titre ni espace).
 */
fun homeSectionOrder(rows: HomeRows): List<HomeSectionId> = rows.layout.apply(presentSections(rows))

/** Rangées calculées en local : affichées seulement avec assez de titres (une rangée de 2 affiches fait vide). */
const val MIN_LOCAL_ROW = 4

private fun presentSections(rows: HomeRows): List<HomeSectionId> = buildList {
    if (rows.shortcuts.isNotEmpty()) add(HomeSectionId.SHORTCUTS)
    if (rows.channels.isNotEmpty()) add(HomeSectionId.CHANNELS)
    if (rows.top10.size >= MIN_LOCAL_ROW) add(HomeSectionId.TOP10)
    if (rows.resume.isNotEmpty()) add(HomeSectionId.RESUME)
    if (rows.upNext.isNotEmpty()) add(HomeSectionId.TRAKT_UP_NEXT)
    if (rows.because.size >= MIN_LOCAL_ROW) add(HomeSectionId.BECAUSE_WATCHED)
    if (rows.topMovies.size >= MIN_LOCAL_ROW) add(HomeSectionId.TOP_MOVIES)
    if (rows.topSeries.size >= MIN_LOCAL_ROW) add(HomeSectionId.TOP_SERIES)
    if (rows.watchlist.isNotEmpty()) add(HomeSectionId.TRAKT_WATCHLIST)
    if (rows.recommended.isNotEmpty()) add(HomeSectionId.TRAKT_RECOMMENDED)
    if (rows.trending.isNotEmpty()) add(HomeSectionId.TRAKT_TRENDING)
    if (rows.popular.isNotEmpty()) add(HomeSectionId.TRAKT_POPULAR)
    rows.traktLists.take(TRAKT_LIST_SECTIONS.size).forEachIndexed { i, l -> if (l.cards.isNotEmpty()) add(TRAKT_LIST_SECTIONS[i]) }
    if (rows.recentChannels.isNotEmpty()) add(HomeSectionId.RECENT_CHANNELS)
    if (rows.latestMovies.isNotEmpty()) add(HomeSectionId.LATEST_MOVIES)
    if (rows.latestSeries.isNotEmpty()) add(HomeSectionId.LATEST_SERIES)
}

/** Rangées de l'accueil tactile, dans le même ordre que la TV. */
fun mobileSectionOrder(order: List<HomeSectionId>): List<HomeSectionId> = order.filter { it in MOBILE_SECTIONS }

/** Encart du haut de l'accueil TV : hero, sinon l'état explicatif adapté, sinon rien. */
enum class HomeTopSlot { HERO, NO_SOURCE, UNSUPPORTED, FAILURE, LOADING, NONE }

/**
 * Même priorité qu'avant : hero dès qu'il existe ; tant que Room n'a pas répondu ([loaded] faux) rien du tout
 * (pas de « aucune source » une fraction de seconde) ; puis aucune source → sources toutes non prises en charge
 * → source en erreur → chargement.
 */
fun homeTopSlot(hasHero: Boolean, loaded: Boolean, providerKinds: List<String>, supportedKinds: Set<String>, hasFailure: Boolean): HomeTopSlot = when {
    hasHero -> HomeTopSlot.HERO
    !loaded -> HomeTopSlot.NONE
    providerKinds.isEmpty() -> HomeTopSlot.NO_SOURCE
    providerKinds.none { it in supportedKinds } -> HomeTopSlot.UNSUPPORTED
    hasFailure -> HomeTopSlot.FAILURE
    else -> HomeTopSlot.LOADING
}

/** Méta du hero « 2024 · Drame · ★ 7.8 » ; élément absent masqué, chaîne vide si rien n'est connu. */
fun heroMeta(year: Int?, genre: String?, rating: Double?): String =
    listOfNotNull(year?.toString(), genre?.takeIf { it.isNotBlank() }, rating?.let { "★ %.1f".format(java.util.Locale.ROOT, it) }).joinToString(" · ")

/** Ligne d'infos du hero « 2013 | Drame | 1 h 41 min » ; élément absent masqué (les notes IMDb / RT sont des pastilles à part). */
fun heroInfoLine(year: Int?, genre: String?, runtime: String?): String =
    listOfNotNull(year?.toString(), genre?.takeIf { it.isNotBlank() }, runtime?.takeIf { it.isNotBlank() }).joinToString(" | ")

/** Durée source (« 01:41:00 », « 101 », « 6060 » en secondes) → minutes ; null si inconnue ou illisible. */
fun durationMinutes(raw: String?): Int? {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return null
    val m = when {
        t.all { it.isDigit() } -> t.toIntOrNull()?.let { if (it > 600) it / 60 else it }
        Regex("""\d{1,2}:\d{2}(:\d{2})?""").matches(t) -> t.split(':').let { it[0].toInt() * 60 + it[1].toInt() }
        else -> null
    }
    return m?.takeIf { it in 1..1_500 }
}

/** « 1 h 41 min » / « 45 min » à partir des modèles localisés (« %d h », « %d min »). */
fun runtimeLabel(minutes: Int, hourFmt: String, minFmt: String): String =
    if (minutes >= 60) hourFmt.format(minutes / 60) + (if (minutes % 60 > 0) " " + minFmt.format(minutes % 60) else "") else minFmt.format(minutes)

/** Pastilles de notes du hero : IMDb (sinon la note habituelle en repli) et Rotten Tomatoes. Absent = null. */
data class HeroRatingBadges(val imdb: String?, val fallback: String?, val rt: String?) {
    val isEmpty get() = imdb == null && fallback == null && rt == null
}

fun heroRatingBadges(r: com.ultratv.tv.nativeapp.data.tmdb.ExternalRatings?, fallback: Double?): HeroRatingBadges = HeroRatingBadges(
    imdb = r?.imdb?.let { "IMDb %.1f".format(java.util.Locale.ROOT, it) },
    fallback = if (r?.imdb == null) fallback?.takeIf { it > 0 && it <= 10 }?.let { "★ %.1f".format(java.util.Locale.ROOT, it) } else null,
    rt = r?.rt?.let { "$it%" },
)

/** Fraction vue (0..1) ; durée inconnue → 0. */
fun resumeProgress(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

/** Minutes restantes (jamais négatives) ; null si la durée est inconnue. */
fun minutesLeft(positionMs: Long, durationMs: Long): Int? =
    if (durationMs <= 0) null else ((durationMs - positionMs).coerceAtLeast(0) / 60_000).toInt()

/** Carte « Reprendre » à partir d'une ligne d'historique ; [clean] = nettoyage du titre (injecté pour les tests). */
fun resumeCardOf(e: WatchHistoryEntity, keyPrefix: String, clean: (String) -> String): ResumeCardUi =
    ResumeCardUi("$keyPrefix${e.kind}-${e.remoteId}", e, clean(e.title), resumeProgress(e.positionMs, e.durationMs), minutesLeft(e.positionMs, e.durationMs))

/** Affiche Trakt (films et séries mêlés) : la clé distingue film et série de même identifiant. */
fun traktPosterOf(c: TraktCard): PosterCardUi =
    PosterCardUi("${if (c.isShow) "s" else "m"}-${c.id}", PosterItem(c.id, c.title, c.poster, c.year, c.rating), c.isShow, if (c.isShow) TmdbKind.TV else TmdbKind.MOVIE)

/** Carte « À suivre » : affiche de la série, titre suivi de l'épisode à regarder (« Lost · S02E05 »). */
fun traktUpNextPosterOf(u: com.ultratv.tv.nativeapp.data.trakt.TraktUpNextCard): PosterCardUi {
    val c = u.card
    val tag = "S${"%02d".format(u.season)}E${"%02d".format(u.episode)}"
    return PosterCardUi("u-${c.id}", PosterItem(c.id, "${c.title} · $tag", c.poster, c.year, c.rating), true, TmdbKind.TV)
}

/** Affiche du catalogue (derniers films / séries). */
fun catalogPosterOf(p: PosterItem, isShow: Boolean): PosterCardUi =
    PosterCardUi("${if (isShow) "s" else "m"}-${p.id}", p, isShow, if (isShow) TmdbKind.TV else TmdbKind.MOVIE)

/**
 * Doublons retirés (même clé) : une LazyRow plante sur deux clés identiques. Trakt ou l'historique peuvent
 * renvoyer deux fois le même élément ; le premier, le plus pertinent, est gardé.
 */
fun <T> List<T>.distinctKeys(key: (T) -> String): List<T> = distinctBy(key)

/**
 * Une carte par œuvre dans « Reprendre la lecture » : le même film rangé dans deux catégories de la source (4K, Bluray…)
 * a deux identifiants ; seule la lecture la plus récente (la liste arrive du plus récent au plus ancien) reste.
 */
fun List<ResumeCardUi>.oneCardPerTitle(): List<ResumeCardUi> = distinctBy { "${it.entry.kind}|${it.title.trim().lowercase()}" }

/** Position verticale du pivot de l'accueil TV : haut de l'élément focalisé à 30 % de la hauteur visible. */
const val HOME_PIVOT_PARENT_FRACTION = 0.3f
const val HOME_PIVOT_CHILD_FRACTION = 0f

/**
 * Distance de défilement pour amener un élément au pivot (mêmes règles que le pivot TV de Compose Foundation) :
 * haut de l'élément à [parentFraction] de la zone visible ; un élément qui tient dans l'écran mais pas sous le
 * pivot est collé en bas ; plus grand que l'écran, il garde son haut au pivot. La liste borne ensuite le défilement
 * (pas au-delà du début ni de la fin).
 */
fun pivotScrollDistance(
    offset: Float,
    size: Float,
    containerSize: Float,
    parentFraction: Float = HOME_PIVOT_PARENT_FRACTION,
    childFraction: Float = HOME_PIVOT_CHILD_FRACTION,
): Float {
    val childSize = kotlin.math.abs(size)
    val childSmallerThanParent = childSize <= containerSize
    val initialTarget = parentFraction * containerSize - childFraction * childSize
    val spaceUnderPivot = containerSize - initialTarget
    val target = if (childSmallerThanParent && spaceUnderPivot < childSize) containerSize - childSize else initialTarget
    return offset - target
}

/** Nombre de titres « à la une » qui défilent. */
const val HERO_POOL = 6
/** Films candidats lus pour « À la une » : seuls ceux qui ont (ou obtiennent) une image paysage sont retenus. */
const val HERO_CANDIDATES = 20
/** Au plus ce nombre de films sans image paysage voient leur fiche TMDB résolue en arrière-plan pour en trouver une. */
const val HERO_RESOLVE_MAX = 8
/** Durée d'affichage de chaque titre « à la une ». */
const val HERO_ROTATE_MS = 9_000L

/** Alterne séries et films (série d'abord), sans doublon, au plus [max]. */
fun <T> interleaveHeroes(series: List<T>, movies: List<T>, max: Int): List<T> {
    val out = ArrayList<T>(max)
    var i = 0
    while (out.size < max && (i < series.size || i < movies.size)) {
        series.getOrNull(i)?.let { if (it !in out && out.size < max) out += it }
        movies.getOrNull(i)?.let { if (it !in out && out.size < max) out += it }
        i++
    }
    return out
}

/**
 * Sélection « À la une » : uniquement des titres AVEC image paysage ([hasLandscape]), séries et films alternés, au plus [max].
 * Une affiche portrait seule faisait un bandeau vilain. Sans aucun titre paysage, [fallback] autorise l'ancien affichage
 * (affiche seule) en dernier recours ; sinon la liste est vide.
 */
fun <T> pickHeroes(series: List<T>, movies: List<T>, max: Int, fallback: Boolean, hasLandscape: (T) -> Boolean): List<T> {
    val picked = interleaveHeroes(series.filter(hasLandscape), movies.filter(hasLandscape), max)
    return if (picked.isEmpty() && fallback) interleaveHeroes(series, movies, max) else picked
}

/** Films à résoudre auprès de TMDB : sans image paysage, dans l'ordre, au plus [max] ; aucun si la sélection est déjà complète. */
fun <T> heroesToResolve(movies: List<T>, selectedCount: Int, wanted: Int, max: Int, hasLandscape: (T) -> Boolean): List<T> =
    if (selectedCount >= wanted) emptyList() else movies.filterNot(hasLandscape).take(max)
