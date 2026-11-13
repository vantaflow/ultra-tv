package com.ultratv.tv.nativeapp.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.db.titleSortKeyOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clés de tri A–Z des films et séries déjà en base avant la clé sans article ([titleSortKeyOf]) : recalculées une
 * seule fois, au premier choix du tri « A–Z » (les synchros suivantes écrivent directement la bonne clé). Par tranches
 * de clé primaire, une transaction par tranche : la base reste utilisable pendant le calcul.
 */
@Singleton
class TitleSortKeys @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: UltraDb,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
) {
    private val lock = Mutex()
    @Volatile private var done = false

    suspend fun ensure() {
        if (done) return
        lock.withLock {
            if (done) return
            val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (sp.getInt(KEY, 0) >= VERSION) { done = true; return }
            withContext(Dispatchers.IO) {
                runCatching {
                    var after = 0L
                    while (true) {
                        val chunk = movieDao.titleChunk(after, CHUNK)
                        if (chunk.isEmpty()) break
                        db.withTransaction { chunk.forEach { movieDao.setSortKey(it.id, titleSortKeyOf(it.title)) } }
                        after = chunk.last().id
                    }
                    after = 0L
                    while (true) {
                        val chunk = seriesDao.titleChunk(after, CHUNK)
                        if (chunk.isEmpty()) break
                        db.withTransaction { chunk.forEach { seriesDao.setSortKey(it.id, titleSortKeyOf(it.title)) } }
                        after = chunk.last().id
                    }
                    sp.edit().putInt(KEY, VERSION).apply()
                }
            }
            done = true      // échec : on n'insiste pas pendant cette session (l'ordre reste lisible, clés anciennes)
        }
    }

    private companion object {
        const val PREFS = "title_sort_keys"
        const val KEY = "version"
        const val VERSION = 1
        const val CHUNK = 2_000
    }
}
