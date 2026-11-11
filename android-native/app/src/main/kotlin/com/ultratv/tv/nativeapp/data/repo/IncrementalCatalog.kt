package com.ultratv.tv.nativeapp.data.repo

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity

/**
 * Écriture INCRÉMENTALE d'une grosse table du catalogue (films, séries). Avant, chaque synchro supprimait puis
 * réinsérait tout (180 000 lignes, leurs index et l'index de recherche plein texte via les déclencheurs FTS), et
 * les identifiants changeaient à chaque fois. Ici :
 *  - nouvelle ligne → INSERT OR IGNORE ;
 *  - ligne existante → UPDATE seulement si une valeur a changé (les déclencheurs FTS ne s'exécutent que pour elles) ;
 *  - lignes absentes de la nouvelle liste → supprimées en fin de passe (table temporaire des identifiants vus).
 * PREMIÈRE écriture (table entièrement vide) : mode « en masse » — les index secondaires et les déclencheurs FTS sont retirés
 * pendant l'insertion puis recréés / reconstruits UNE fois à la fin (construction triée, bien plus rapide que 7 index et
 * l'index plein texte mis à jour ligne par ligne), sans table des identifiants vus ni tentative de mise à jour. Tout reste
 * dans la transaction de l'appelant : en cas d'échec, tout est annulé, index et déclencheurs compris.
 * Compatible SQLite 3.22 (Android 9, beaucoup de box) : pas de syntaxe UPSERT.
 * Les colonnes [keepIfNull] (enrichies plus tard par get_vod_info / TMDB) ne sont pas effacées par une liste qui ne
 * les fournit pas.
 */
class IncrementalTable<T>(
    private val table: String,
    private val columns: List<String>,
    private val keepIfNull: Set<String>,
    private val values: (T) -> List<Any?>,
    private val remoteIdOf: (T) -> String,
) {
    private val updatable = columns.filter { it != "providerId" && it != "remoteId" }
    private val insertSql = "INSERT OR IGNORE INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})"
    /** Insertion en masse : REPLACE garde la logique « le dernier doublon gagne » de la voie incrémentale. */
    internal val bulkInsertSql = insertSql.replaceFirst("INSERT OR IGNORE", "INSERT OR REPLACE")
    internal val ftsEvents = listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT")
    private val updateSql = buildString {
        append("UPDATE `$table` SET ")
        append(updatable.joinToString { c -> if (c in keepIfNull) "`$c` = COALESCE(?, `$c`)" else "`$c` = ?" })
        append(" WHERE `providerId` = ? AND `remoteId` = ? AND (")
        append(updatable.joinToString(" OR ") { c -> if (c in keepIfNull) "(? IS NOT NULL AND `$c` IS NOT ?)" else "`$c` IS NOT ?" })
        append(")")
    }

    /** Une passe complète, À APPELER DANS UNE TRANSACTION. Retourne le nombre de lignes reçues. */
    class Pass<T> internal constructor(private val t: IncrementalTable<T>, private val db: SupportSQLiteDatabase, private val providerId: Long) {
        /** Table vide : insertion en masse, voir [IncrementalTable]. */
        private val bulk = db.query("SELECT 1 FROM `${t.table}` LIMIT 1").use { !it.moveToFirst() }
        private val ins = db.compileStatement(if (bulk) t.bulkInsertSql else t.insertSql)
        private val upd by lazy { db.compileStatement(t.updateSql) }
        private val seen by lazy { db.compileStatement("INSERT OR IGNORE INTO temp.`sync_seen` (`remoteId`) VALUES (?)") }
        /** (nom, SQL) des index secondaires retirés pendant l'insertion en masse. */
        private val droppedIndexes = ArrayList<String>()
        var inserted = 0; private set
        var updated = 0; private set

        init {
            if (bulk) {
                db.query("SELECT `name`, `sql` FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND `sql` IS NOT NULL AND `sql` NOT LIKE '%UNIQUE%'", arrayOf<Any>(t.table)).use { c ->
                    while (c.moveToNext()) { droppedIndexes += c.getString(1); db.execSQL("DROP INDEX IF EXISTS `${c.getString(0)}`") }
                }
                for (e in t.ftsEvents) db.execSQL("DROP TRIGGER IF EXISTS `room_fts_content_sync_${t.table}_fts_$e`")
            }
        }

        fun write(rows: List<T>) {
            if (bulk) {
                for (r in rows) { ins.clearBindings(); t.values(r).forEachIndexed { i, x -> ins.bindAny(i + 1, x) }; ins.executeInsert(); inserted++ }
                return
            }
            for (r in rows) {
                val v = t.values(r)
                ins.clearBindings(); v.forEachIndexed { i, x -> ins.bindAny(i + 1, x) }
                if (ins.executeInsert() != -1L) inserted++ else {
                    upd.clearBindings()
                    var k = 1
                    val byCol = t.columns.zip(v).toMap()
                    for (c in t.updatable) upd.bindAny(k++, byCol[c])
                    upd.bindLong(k++, providerId); upd.bindString(k++, t.remoteIdOf(r))
                    for (c in t.updatable) { if (c in t.keepIfNull) upd.bindAny(k++, byCol[c]); upd.bindAny(k++, byCol[c]) }
                    if (upd.executeUpdateDelete() > 0) updated++
                }
                seen.clearBindings(); seen.bindString(1, t.remoteIdOf(r)); seen.executeInsert()
            }
        }

        /** Supprime ce qui n'est plus dans la liste et nettoie. Retourne le nombre de lignes supprimées. */
        fun finish(): Int {
            if (bulk) {
                runCatching { ins.close() }
                for (sql in droppedIndexes) db.execSQL(sql)
                val tb = t.table
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${tb}_fts_BEFORE_UPDATE BEFORE UPDATE ON `$tb` BEGIN DELETE FROM `${tb}_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${tb}_fts_BEFORE_DELETE BEFORE DELETE ON `$tb` BEGIN DELETE FROM `${tb}_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${tb}_fts_AFTER_UPDATE AFTER UPDATE ON `$tb` BEGIN INSERT INTO `${tb}_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${tb}_fts_AFTER_INSERT AFTER INSERT ON `$tb` BEGIN INSERT INTO `${tb}_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END")
                db.execSQL("INSERT INTO `${tb}_fts`(`${tb}_fts`) VALUES('rebuild')")
                return 0
            }
            val del = db.compileStatement("DELETE FROM `${t.table}` WHERE `providerId` = ? AND `remoteId` NOT IN (SELECT `remoteId` FROM temp.`sync_seen`)")
            del.bindLong(1, providerId)
            val n = del.executeUpdateDelete()
            db.execSQL("DELETE FROM temp.`sync_seen`")
            for (st in listOf(ins, upd, seen, del)) runCatching { st.close() }
            return n
        }
    }

    fun begin(db: SupportSQLiteDatabase, providerId: Long): Pass<T> {
        db.execSQL("CREATE TEMP TABLE IF NOT EXISTS `sync_seen` (`remoteId` TEXT PRIMARY KEY NOT NULL)")
        db.execSQL("DELETE FROM temp.`sync_seen`")
        return Pass(this, db, providerId)
    }

    companion object {
        val MOVIES = IncrementalTable<MovieEntity>(
            "movie",
            listOf("providerId", "remoteId", "name", "poster", "categoryId", "streamUrl", "container", "year", "rating", "plot",
                "title", "sortKey", "backdrop", "genre", "cast", "duration", "lang", "addedKey"),
            keepIfNull = setOf("plot", "backdrop", "genre", "cast", "duration"),
            values = { m -> listOf(m.providerId, m.remoteId, m.name, m.poster, m.categoryId, m.streamUrl, m.container, m.year, m.rating, m.plot,
                m.title, m.sortKey, m.backdrop, m.genre, m.cast, m.duration, m.lang, m.addedKey) },
            remoteIdOf = { it.remoteId },
        )
        val SERIES = IncrementalTable<SeriesEntity>(
            "series",
            listOf("providerId", "remoteId", "name", "poster", "categoryId", "year", "rating", "plot",
                "title", "sortKey", "backdrop", "genre", "cast", "lang", "addedKey"),
            keepIfNull = setOf("plot", "backdrop", "genre", "cast"),
            values = { s -> listOf(s.providerId, s.remoteId, s.name, s.poster, s.categoryId, s.year, s.rating, s.plot,
                s.title, s.sortKey, s.backdrop, s.genre, s.cast, s.lang, s.addedKey) },
            remoteIdOf = { it.remoteId },
        )
    }
}

private fun SupportSQLiteStatement.bindAny(i: Int, v: Any?) {
    when (v) {
        null -> bindNull(i)
        is Long -> bindLong(i, v)
        is Int -> bindLong(i, v.toLong())
        is Boolean -> bindLong(i, if (v) 1 else 0)
        is Double -> bindDouble(i, v)
        is Float -> bindDouble(i, v.toDouble())
        else -> bindString(i, v.toString())
    }
}
