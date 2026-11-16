package com.ultratv.tv.nativeapp.data.repo

import androidx.paging.PagingConfig

/**
 * Configuration Paging commune aux grilles (Direct, Films, Séries, Guide) : en plus de ne charger que la fenêtre
 * visible, on PLAFONNE le nombre d'éléments gardés en mémoire (`maxSize`). Sans plafond, faire défiler un catalogue de
 * 50 000 entrées finissait par toutes les garder en RAM (plusieurs dizaines de Mo, fatal sur une TV à 128 Mo de tas) ;
 * avec le plafond, les pages très loin de l'écran sont libérées et rechargées depuis Room si l'on remonte.
 */
object PagingMemory {
    /** Nombre de pages gardées autour de la position courante (≈ 15 pages, soit ~100 lignes de poster). */
    const val PAGES_KEPT = 15

    fun config(pageSize: Int, prefetchDistance: Int, initialLoadSize: Int): PagingConfig = PagingConfig(
        pageSize = pageSize,
        prefetchDistance = prefetchDistance,
        initialLoadSize = initialLoadSize,
        enablePlaceholders = false,
        maxSize = maxItems(pageSize, prefetchDistance, initialLoadSize),
    )

    /** Plafond ≥ exigence de Paging (pageSize + 2 × prefetch) et ≥ chargement initial, sinon exception à l'exécution. */
    fun maxItems(pageSize: Int, prefetchDistance: Int, initialLoadSize: Int): Int =
        maxOf(pageSize * PAGES_KEPT, pageSize + 2 * prefetchDistance, initialLoadSize)
}
