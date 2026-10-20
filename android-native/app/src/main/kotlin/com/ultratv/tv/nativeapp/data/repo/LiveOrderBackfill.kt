package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remplissage DIFFÉRÉ de `channel.catRank` après la migration 17 → 18 (qui n'ajoute que la colonne et l'index : réécrire
 * 55 000 lignes et leur index plein texte pendant l'ouverture de la base figeait l'eMMC d'une box plusieurs secondes).
 * Ici : en tâche de fond, par lots de 2 000 lignes (une transaction chacun, une courte pause entre deux), source par
 * source. Tant qu'une source n'est pas remplie, le Direct garde l'ancienne requête jointe (même ordre, plus lente).
 *
 * Idempotent et sans effet une fois tout rempli (une recherche d'index par source). Si l'utilisateur réordonne ses
 * catégories pendant le remplissage, la passe est refaite avec les nouveaux rangs.
 */
@Singleton
class LiveOrderBackfill @Inject constructor(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val _running = MutableStateFlow(false)

    /** Vrai pendant l'écriture : le Direct suspend ses relectures (compteurs, liste) comme pendant une synchro. */
    val running: StateFlow<Boolean> = _running

    /** Lance le remplissage s'il reste des lignes sans rang (sans effet s'il tourne déjà). */
    fun ensure(delayMs: Long = 0) {
        if (lock.isLocked) return
        scope.launch {
            if (delayMs > 0) delay(delayMs)
            runCatching { runNow() }
        }
    }

    /** Remplit toutes les sources concernées ; renvoie le nombre de lignes réécrites. */
    suspend fun runNow(batch: Int = BATCH, pauseMs: Long = PAUSE_MS): Int = lock.withLock {
        var total = 0
        val pids = channelDao.unrankedProviders()
        if (pids.isEmpty()) return@withLock 0
        _running.value = true
        try {
            for (pid in pids) {
                var ranks = LiveOrder.ranks(categoryDao.forProviderKind(pid, LiveOrder.KIND))
                while (true) {
                    total += LiveOrder.fill(channelDao, pid, ranks, batch) { if (pauseMs > 0) delay(pauseMs) }
                    // Catégories réordonnées entre-temps : certains lots portent l'ancien rang, on repasse.
                    val now = LiveOrder.ranks(categoryDao.forProviderKind(pid, LiveOrder.KIND))
                    if (now == ranks) break
                    ranks = now
                }
            }
        } finally {
            _running.value = false
        }
        total
    }

    private companion object {
        const val BATCH = 2_000
        const val PAUSE_MS = 40L
    }
}
