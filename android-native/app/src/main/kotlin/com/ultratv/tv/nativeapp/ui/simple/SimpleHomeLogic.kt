package com.ultratv.tv.nativeapp.ui.simple

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.nav.StartScreen
import com.ultratv.tv.nativeapp.nav.StartupTarget
import com.ultratv.tv.nativeapp.nav.startupTarget

/**
 * Mode simple (parents, grands-parents) : logique PURE, sans Android, testée à part.
 * Les préférences viennent de [UserPrefs.simpleMode], déjà résolue par profil (surcharge de ProfilePrefs).
 */

/** Écran de la destination « accueil » : l'accueil habituel, ou l'écran des grandes tuiles. */
enum class HomeKind { STANDARD, SIMPLE }

fun homeKindFor(prefs: UserPrefs): HomeKind = if (prefs.simpleMode) HomeKind.SIMPLE else HomeKind.STANDARD

/** Les grandes tuiles de l'écran simple, dans l'ordre de lecture (gauche → droite, haut → bas). */
enum class SimpleTile(val route: String?) {
    LIVE(Routes.SIMPLE_CHANNELS), RESUME(null), MOVIES(Routes.MOVIES), SERIES(Routes.SERIES), FAVORITES(Routes.FAVORITES), SEARCH(Routes.SEARCH),
}

object SimpleHome {
    /** Nombre de colonnes de la grille (TV) : 3 tuiles de 1/3 de largeur restent très lisibles à 3 m. */
    const val COLUMNS = 3

    /** Nombre maximal de chaînes de la liste à gros caractères (liste courte = écran fluide sur une box lente). */
    const val MAX_CHANNELS = 120

    /**
     * Tuiles affichées : TOUJOURS les six, dans le même ordre. Une grille qui change de forme quand la reprise arrive
     * (lecture asynchrone) ferait perdre le focus à la tuile visée ; « Reprendre » sans rien à reprendre mène aux films.
     */
    fun tiles(): List<SimpleTile> = SimpleTile.entries

    /** Tuiles sous la carte « Reprendre » (nouvel accueil) : toujours les cinq, dans le même ordre. */
    fun mainTiles(): List<SimpleTile> = SimpleTile.entries.filter { it != SimpleTile.RESUME }

    /** Colonnes des tuiles du nouvel accueil : 5 sur TV (une seule rangée), 2 sur un téléphone étroit, 3 sinon en tactile. */
    fun mainColumnsFor(touch: Boolean, widthDp: Float): Int = when { !touch -> 5; widthDp < 600f -> 2; else -> 3 }

    /** Avancement 0..1 d'une reprise (null si la durée est inconnue). */
    fun progressOf(h: WatchHistoryEntity?): Float? =
        h?.takeIf { it.durationMs > 0 }?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }

    /** Minutes restantes d'une reprise (au moins 1 ; null si la durée est inconnue). */
    fun minutesLeft(h: WatchHistoryEntity?): Int? =
        h?.takeIf { it.durationMs > 0 }?.let { (((it.durationMs - it.positionMs).coerceAtLeast(0) + 59_999) / 60_000).toInt().coerceAtLeast(1) }

    /** Colonnes : 3 sur TV et grand écran, 2 sur un téléphone étroit (tactile, moins de 600 dp). */
    fun columnsFor(touch: Boolean, widthDp: Float): Int = if (touch && widthDp < 600f) 2 else COLUMNS

    /** Grille en lignes de [columns] tuiles (la dernière ligne peut être plus courte). */
    fun rows(tiles: List<SimpleTile>, columns: Int = COLUMNS): List<List<SimpleTile>> = tiles.chunked(columns.coerceAtLeast(1))

    /**
     * Élément à reprendre : le plus récent film ou épisode commencé (jamais une chaîne en direct) dont l'adresse de
     * lecture est connue (une reprise reçue d'un autre appareil peut ne pas en avoir).
     */
    fun resumeItem(continueWatching: List<WatchHistoryEntity>): WatchHistoryEntity? =
        continueWatching.firstOrNull { it.kind != "LIVE" && it.streamUrl.isNotBlank() }

    /** Ce que fait la tuile « Reprendre ». */
    sealed interface ResumeAction {
        data class Play(val item: WatchHistoryEntity) : ResumeAction
        /** Rien à reprendre : on propose de choisir un film. */
        data class Browse(val route: String) : ResumeAction
    }

    fun resumeAction(item: WatchHistoryEntity?): ResumeAction =
        if (item == null) ResumeAction.Browse(Routes.MOVIES) else ResumeAction.Play(item)

    /** Dernière chaîne regardée jouable (adresse connue). */
    fun lastLive(recentLive: List<WatchHistoryEntity>): WatchHistoryEntity? =
        recentLive.firstOrNull { it.kind == "LIVE" && it.streamUrl.isNotBlank() }

    /** Ce que fait la tuile « Télé en direct ». */
    sealed interface LiveAction {
        /** Lance directement la dernière chaîne ; [needsPin] si elle est verrouillée (code parental d'abord). */
        data class PlayLast(val item: WatchHistoryEntity, val needsPin: Boolean) : LiveAction
        /** Aucune chaîne regardée : liste à gros caractères (favoris d'abord). */
        data object ShowList : LiveAction
    }

    fun liveAction(lastLive: WatchHistoryEntity?, locked: Set<String>): LiveAction =
        if (lastLive == null) LiveAction.ShowList
        else LiveAction.PlayLast(lastLive, needsPin = "${lastLive.providerId}:${lastLive.remoteId}" in locked)

    /** Liste de chaînes du mode simple : favoris d'abord, puis le reste, sans doublon ni séparateur, plafonnée à [max]. */
    fun orderChannels(favorites: List<ChannelEntity>, others: List<ChannelEntity>, max: Int = MAX_CHANNELS): List<ChannelEntity> =
        (favorites + others).filter { !it.isSeparator }.distinctBy { it.id }.take(max)

    /** Les chaînes qui ouvrent la liste sont-elles des favoris ? (pour l'étiquette de rangée). */
    fun isFavorite(c: ChannelEntity, favoriteIds: Set<Long>): Boolean = c.id in favoriteIds

    /**
     * Écran d'ouverture : en mode simple, l'écran des tuiles est TOUJOURS le point de départ (une rubrique ouverte
     * d'office ferait perdre une personne peu à l'aise) ; seul « dernière chaîne » est respecté, c'est un choix explicite
     * de lecture automatique.
     */
    fun startupTarget(simple: Boolean, screen: StartScreen, lastScreen: String? = null): StartupTarget {
        val t = startupTarget(screen, lastScreen)
        return if (simple && t is StartupTarget.Route) StartupTarget.Stay else t
    }

    /** Le rail de navigation est masqué sur l'écran des tuiles (les tuiles remplacent le menu) ; ailleurs il reste. */
    fun railHidden(simple: Boolean, route: String?): Boolean = simple && route in SIMPLE_ROUTES

    /** Écrans qui ont leur équivalent simple (sans menu latéral : chacun a son bouton retour et Retour y ramène). */
    private val SIMPLE_ROUTES = setOf(Routes.HOME, Routes.SIMPLE_CHANNELS, Routes.MOVIES, Routes.SERIES, Routes.MOVIE_DETAIL, Routes.SERIES_DETAIL, Routes.FAVORITES, Routes.SEARCH, Routes.SETTINGS)

    /**
     * La sortie du mode simple demande le code parental SI un code existe (sans code : libre, comme partout ailleurs).
     * L'activation est toujours libre.
     */
    fun exitNeedsPin(parentalPinSet: Boolean): Boolean = parentalPinSet
}
