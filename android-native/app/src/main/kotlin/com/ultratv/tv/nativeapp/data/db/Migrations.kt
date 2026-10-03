package com.ultratv.tv.nativeapp.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 10 → 11 : synchro incrémentale par partie (horodatages par fournisseur), clé de tri indexée
 * (pagination servie par l'index) et recherche plein texte FTS4. Aucune donnée n'est perdue :
 * favoris, historique et catalogue existants sont conservés ; `sortKey` est recalculé en SQL
 * (minuscules ASCII) puis affiné à la prochaine synchro.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (col in listOf("lastLiveSyncAt", "lastVodSyncAt", "lastSeriesSyncAt", "lastEpgSyncAt")) {
            db.execSQL("ALTER TABLE `provider` ADD COLUMN `$col` INTEGER NOT NULL DEFAULT 0")
        }
        for (t in listOf("channel", "movie", "series")) {
            db.execSQL("ALTER TABLE `$t` ADD COLUMN `sortKey` TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `$t` SET `sortKey` = lower(`name`)")
            db.execSQL("DROP INDEX IF EXISTS `index_${t}_providerId`")
            db.execSQL("DROP INDEX IF EXISTS `index_${t}_providerId_categoryId`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${t}_providerId_sortKey` ON `$t` (`providerId`, `sortKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_${t}_providerId_categoryId_sortKey` ON `$t` (`providerId`, `categoryId`, `sortKey`)")
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `${t}_fts` USING FTS4(`name` TEXT NOT NULL, tokenize=unicode61, content=`$t`)")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${t}_fts_BEFORE_UPDATE BEFORE UPDATE ON `$t` BEGIN DELETE FROM `${t}_fts` WHERE `docid`=OLD.`rowid`; END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${t}_fts_BEFORE_DELETE BEFORE DELETE ON `$t` BEGIN DELETE FROM `${t}_fts` WHERE `docid`=OLD.`rowid`; END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${t}_fts_AFTER_UPDATE AFTER UPDATE ON `$t` BEGIN INSERT INTO `${t}_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${t}_fts_AFTER_INSERT AFTER INSERT ON `$t` BEGIN INSERT INTO `${t}_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END")
            db.execSQL("INSERT INTO `${t}_fts`(`${t}_fts`) VALUES('rebuild')")
        }
    }
}
