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
    RESUME("resume"),
    TRAKT_WATCHLIST("posters"),
    TRAKT_RECOMMENDED("posters"),
    TRAKT_TRENDING("posters"),
    TRAKT_POPULAR("posters"),
    RECENT_CHANNELS("resume"),
    LATEST_MOVIES("posters"),
    LATEST_SERIES("posters"),
    CHANNELS("channels"),
}

/** Rangées montrées par l'accueil tactile (pas de « regardées récemment » ni de « derniers ajouts », comme avant). */
val MOBILE_SECTIONS: Set<HomeSectionId> = setOf(
    HomeSectionId.RESUME, HomeSectionId.TRAKT_WATCHLIST, HomeSectionId.TRAKT_RECOMMENDED,
    HomeSectionId.TRAKT_TRENDING, HomeSectionId.TRAKT_POPULAR, HomeSectionId.CHANNELS,
)

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
data class ChannelCardUi(val channel: ChannelEntity, val nowTitle: String?)

/** Toutes les rangées de l'accueil en UNE valeur immuable ; [order] dit lesquelles existent et dans quel ordre. */
@Immutable
data class HomeRows(
    val resume: List<ResumeCardUi> = emptyList(),
    val watchlist: List<PosterCardUi> = emptyList(),
    val recommended: List<PosterCardUi> = emptyList(),
    val trending: List<PosterCardUi> = emptyList(),
    val popular: List<PosterCardUi> = emptyList(),
    val recentChannels: List<ResumeCardUi> = emptyList(),
    val latestMovies: List<PosterCardUi> = emptyList(),
    val latestSeries: List<PosterCardUi> = emptyList(),
    val channels: List<ChannelCardUi> = emptyList(),
    val showingFavorites: Boolean = false,
) {
    /** Rangées non vides dans l'ordre d'affichage (calculé une fois, à la construction, hors fil principal). */
    val order: List<HomeSectionId> = homeSectionOrder(this)

    companion object { val EMPTY = HomeRows() }
}

/** Ordre fixe de la maquette ; une rangée vide n'est jamais affichée (ni titre ni espace). */
fun homeSectionOrder(rows: HomeRows): List<HomeSectionId> = buildList {
    if (rows.resume.isNotEmpty()) add(HomeSectionId.RESUME)
    if (rows.watchlist.isNotEmpty()) add(HomeSectionId.TRAKT_WATCHLIST)
    if (rows.recommended.isNotEmpty()) add(HomeSectionId.TRAKT_RECOMMENDED)
    if (rows.trending.isNotEmpty()) add(HomeSectionId.TRAKT_TRENDING)
    if (rows.popular.isNotEmpty()) add(HomeSectionId.TRAKT_POPULAR)
    if (rows.recentChannels.isNotEmpty()) add(HomeSectionId.RECENT_CHANNELS)
    if (rows.latestMovies.isNotEmpty()) add(HomeSectionId.LATEST_MOVIES)
    if (rows.latestSeries.isNotEmpty()) add(HomeSectionId.LATEST_SERIES)
    if (rows.channels.isNotEmpty()) add(HomeSectionId.CHANNELS)
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

/** Affiche du catalogue (derniers films / séries). */
fun catalogPosterOf(p: PosterItem, isShow: Boolean): PosterCardUi =
    PosterCardUi("${if (isShow) "s" else "m"}-${p.id}", p, isShow, if (isShow) TmdbKind.TV else TmdbKind.MOVIE)

/**
 * Doublons retirés (même clé) : une LazyRow plante sur deux clés identiques. Trakt ou l'historique peuvent
 * renvoyer deux fois le même élément ; le premier, le plus pertinent, est gardé.
 */
fun <T> List<T>.distinctKeys(key: (T) -> String): List<T> = distinctBy(key)

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
