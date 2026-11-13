package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Index de recherche (tables FTS des chaînes, films et séries) : vérifié une fois par lancement, à l'ouverture de la
 * recherche, et réparé s'il le faut.
 *
 * Une première synchro en masse retire les déclencheurs qui tiennent l'index à jour, puis les recrée et reconstruit
 * l'index à la fin ([IncrementalCatalog]). Si l'appli est tuée entre les deux (box mise en veille, mémoire), les
 * déclencheurs restent absents : les titres ajoutés ensuite ne sont jamais indexés et la recherche ne les trouve pas,
 * même avec le mot exact. Ici : déclencheurs manquants recréés, et index reconstruit s'il ne compte pas autant de
 * lignes que sa table. Jamais pendant une synchro (les déclencheurs y sont retirés volontairement).
 */
@Singleton
class SearchIndexHealth @Inject constructor(
    private val db: UltraDb,
    private val sync: SyncStatusBus,
) {
    private val lock = Mutex()
    @Volatile private var checked = false

    suspend fun ensure() {
        if (checked || sync.status.value != null) return
        lock.withLock {
            if (checked) return
            withContext(Dispatchers.IO) {
                runCatching {
                    val w = db.openHelper.writableDatabase
                    for (tb in TABLES) {
                        val triggers = w.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE 'room_fts_content_sync_${tb}_fts_%'").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
                        val rows = w.query("SELECT COUNT(*) FROM `$tb`").use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
                        val indexed = w.query("SELECT COUNT(*) FROM `${tb}_fts_docsize`").use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
                        if (triggers >= 4 && rows == indexed) continue
                        android.util.Log.w("UltraSearch", "index $tb réparé : déclencheurs=$triggers lignes=$rows indexées=$indexed")
                        w.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${tb}_fts_BEFORE_UPDATE BEFORE UPDATE ON `$tb` BEGIN DELETE FROM `${tb}_fts` WHERE `docid`=OLD.`rowid`; END")
                        w.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${tb}_fts_BEFORE_DELETE BEFORE DELETE ON `$tb` BEGIN DELETE FROM `${tb}_fts` WHERE `docid`=OLD.`rowid`; END")
                        w.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${tb}_fts_AFTER_UPDATE AFTER UPDATE ON `$tb` BEGIN INSERT INTO `${tb}_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END")
                        w.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${tb}_fts_AFTER_INSERT AFTER INSERT ON `$tb` BEGIN INSERT INTO `${tb}_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END")
                        w.execSQL("INSERT INTO `${tb}_fts`(`${tb}_fts`) VALUES('rebuild')")
                    }
                }
            }
            checked = true
        }
    }

    private companion object {
        val TABLES = listOf("channel", "movie", "series")
    }
}
