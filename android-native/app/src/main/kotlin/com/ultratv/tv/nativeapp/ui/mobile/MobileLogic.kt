package com.ultratv.tv.nativeapp.ui.mobile

import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.ui.player.engine.AspectMode

/**
 * Logique PURE du mode tactile (aucune dépendance Android) : testée à part, partagée par les écrans.
 * La TV ne passe jamais par ici : [navLayoutFor] renvoie [NavLayout.TV_RAIL] dès que l'appareil est une télévision.
 */

/** Disposition de navigation : rail D-pad (TV), barre d'onglets (compact), rail latéral tactile (medium / expanded). */
enum class NavLayout { TV_RAIL, BOTTOM_TABS, SIDE_RAIL }

/** Classes de taille de fenêtre (seuils Material 3 : 600 dp et 840 dp). */
enum class WindowClass { COMPACT, MEDIUM, EXPANDED }

const val MEDIUM_MIN_DP = 600f
const val EXPANDED_MIN_DP = 840f
/** Sous cette hauteur (téléphone en paysage), le rail latéral n'aurait pas la place de ses entrées. */
const val COMPACT_HEIGHT_DP = 480f

fun windowClassFor(widthDp: Float): WindowClass = when {
    widthDp < MEDIUM_MIN_DP -> WindowClass.COMPACT
    widthDp < EXPANDED_MIN_DP -> WindowClass.MEDIUM
    else -> WindowClass.EXPANDED
}

fun navLayoutFor(tv: Boolean, widthDp: Float, heightDp: Float): NavLayout = when {
    tv -> NavLayout.TV_RAIL
    windowClassFor(widthDp) == WindowClass.COMPACT || heightDp < COMPACT_HEIGHT_DP -> NavLayout.BOTTOM_TABS
    else -> NavLayout.SIDE_RAIL
}

/** Onglets de la barre du bas (maquette MobileTabs) : Accueil, Direct, Guide, Films, Plus. */
enum class MobileTab { HOME, LIVE, GUIDE, MOVIES, MORE }

/** Destinations ouvertes par « Plus ». */
val MORE_ROUTES = listOf(Routes.SERIES, Routes.SEARCH, Routes.FAVORITES, "recordings", Routes.SETTINGS, "profiles")

fun tabForRoute(route: String?): MobileTab = when {
    route == null || route == Routes.HOME -> MobileTab.HOME
    route == Routes.LIVE || route.startsWith("player") -> MobileTab.LIVE
    route == Routes.GUIDE -> MobileTab.GUIDE
    route == Routes.MOVIES || route.startsWith("movies/") -> MobileTab.MOVIES
    else -> MobileTab.MORE
}

/** Le lecteur est plein écran : ni barre d'onglets, ni rail. */
fun isFullscreenRoute(route: String?): Boolean = route?.startsWith("player") == true

/** Entrée du rail latéral tactile : la route la plus proche porte l'état actif. */
fun railRouteFor(route: String?): String = when {
    route == null -> Routes.HOME
    route.startsWith("player") -> Routes.LIVE
    route.startsWith("movies/") -> Routes.MOVIES
    route.startsWith("series/") -> Routes.SERIES
    route == "categories" || route == "diagnostic" || route == "locked-channels" -> Routes.SETTINGS
    else -> route
}

/** Nombre de colonnes d'une grille d'affiches : au moins 2, cellules d'environ [minCellDp]. */
fun gridColumns(widthDp: Float, minCellDp: Float = 120f, gutterDp: Float = 12f): Int =
    ((widthDp + gutterDp) / (minCellDp + gutterDp)).toInt().coerceAtLeast(2)

/** Tablette en largeur : liste + détail côte à côte (Direct, fiches). */
fun usesTwoPane(widthDp: Float): Boolean = widthDp >= EXPANDED_MIN_DP

// ───────────────────────── Gestes du lecteur ─────────────────────────

enum class TapZone { LEFT, CENTER, RIGHT }

/** Tiers gauche / centre / droit de l'écran (double appui : −10 s / +10 s sur les côtés). */
fun tapZone(x: Float, width: Float): TapZone {
    if (width <= 0f) return TapZone.CENTER
    val f = x / width
    return when { f < 1f / 3f -> TapZone.LEFT; f > 2f / 3f -> TapZone.RIGHT; else -> TapZone.CENTER }
}

const val SEEK_STEP_MS = 10_000L

/** Saut du double appui : seulement en VOD (le direct n'a pas de position), jamais au centre. */
fun doubleTapSeekMs(zone: TapZone, isLive: Boolean): Long? = when {
    isLive -> null
    zone == TapZone.LEFT -> -SEEK_STEP_MS
    zone == TapZone.RIGHT -> SEEK_STEP_MS
    else -> null
}

/** Nouvelle position après un saut, bornée à [0 ; durée] quand celle-ci est connue. */
fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val t = (positionMs + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0) t.coerceAtMost(durationMs) else t
}

enum class DragRole { BRIGHTNESS, VOLUME, ZAP, NONE }

/**
 * Rôle d'un glissement vertical selon le point de départ : tiers gauche = luminosité, tiers droit = volume ;
 * au centre, en direct, un balayage change de chaîne (en VOD le centre ne fait rien).
 */
fun dragRole(startX: Float, width: Float, isLive: Boolean): DragRole = when (tapZone(startX, width)) {
    TapZone.LEFT -> DragRole.BRIGHTNESS
    TapZone.RIGHT -> DragRole.VOLUME
    TapZone.CENTER -> if (isLive) DragRole.ZAP else DragRole.NONE
}

/**
 * Niveau (0..1) après un glissement vertical : glisser vers le HAUT augmente. Un balayage de toute
 * la hauteur couvre toute la plage ([sensitivity] = 1).
 */
fun levelAfterDrag(start: Float, totalDy: Float, heightPx: Float, sensitivity: Float = 1f): Float {
    if (heightPx <= 0f) return start.coerceIn(0f, 1f)
    return (start - totalDy / heightPx * sensitivity).coerceIn(0f, 1f)
}

/** Zapping par balayage : vers le haut = chaîne suivante, vers le bas = précédente ; en dessous du seuil, rien. */
fun zapFor(totalDy: Float, thresholdPx: Float): Int? = when {
    totalDy <= -thresholdPx -> +1
    totalDy >= thresholdPx -> -1
    else -> null
}

private val PINCH_CYCLE = listOf(AspectMode.FIT, AspectMode.FILL, AspectMode.ZOOM)

/** Pincer pour agrandir passe à FILL puis ZOOM ; pincer pour réduire revient d'un cran. Les formats forcés (16:9, 4:3) repartent de FIT. */
fun aspectAfterPinch(current: AspectMode, scale: Float): AspectMode {
    val i = PINCH_CYCLE.indexOf(current).let { if (it < 0) 0 else it }
    return when {
        scale >= 1.12f -> PINCH_CYCLE[(i + 1).coerceAtMost(PINCH_CYCLE.lastIndex)]
        scale <= 0.88f -> PINCH_CYCLE[(i - 1).coerceAtLeast(0)]
        else -> current
    }
}

/** Image dans l'image : seulement pendant une lecture, et pas sur un écran qui n'a aucun flux. */
fun shouldEnterPip(hasPlayback: Boolean, playerShown: Boolean): Boolean = hasPlayback && playerShown
