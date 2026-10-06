package com.ultratv.tv.nativeapp.data.repo

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.transform

/**
 * Au plus une valeur toutes les [ms] millisecondes, la PREMIÈRE tout de suite, puis toujours la plus récente.
 * Room ré-émet ses requêtes (COUNT, GROUP BY…) à CHAQUE écriture de la table : pendant une synchro, des centaines
 * de fois par seconde. Une seule souscription en amont (merge(take(1), drop(1).sample()) en lançait deux).
 */
fun <T> Flow<T>.atMostEvery(ms: Long): Flow<T> = conflate().transform { emit(it); delay(ms) }
