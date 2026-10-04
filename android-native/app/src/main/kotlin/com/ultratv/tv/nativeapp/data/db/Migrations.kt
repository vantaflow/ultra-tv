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
        db.execSQL("ALTER TABLE `provider` ADD COLUMN `categoryFilter` INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE `category` ADD COLUMN `enabled` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE `category` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `category` ADD COLUMN `lang` TEXT NOT NULL DEFAULT ''")
        for (col in listOf("lastLiveSyncAt", "lastVodSyncAt", "lastSeriesSyncAt", "lastEpgSyncAt")) {
            db.execSQL("ALTER TABLE `provider` ADD COLUMN `$col` INTEGER NOT NULL DEFAULT 0")
        }
        db.execSQL("ALTER TABLE `episode` ADD COLUMN `image` TEXT")
        for (t in listOf("channel", "movie", "series")) {
            db.execSQL("ALTER TABLE `$t` ADD COLUMN `sortKey` TEXT NOT NULL DEFAULT ''")
            if (t == "channel") {
                db.execSQL("ALTER TABLE `channel` ADD COLUMN `num` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `channel` ADD COLUMN `seq` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `channel` ADD COLUMN `junk` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `channel` ADD COLUMN `isSeparator` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `channel` ADD COLUMN `country` TEXT")
                db.execSQL("ALTER TABLE `channel` ADD COLUMN `quality` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `channel` ADD COLUMN `flags` INTEGER NOT NULL DEFAULT 0")
            }
            db.execSQL("ALTER TABLE `$t` ADD COLUMN `lang` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$t` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE `$t` SET `title` = `name`, `sortKey` = lower(`name`)")
            if (t != "channel") {
                db.execSQL("ALTER TABLE `$t` ADD COLUMN `backdrop` TEXT")
                db.execSQL("ALTER TABLE `$t` ADD COLUMN `genre` TEXT")
                db.execSQL("ALTER TABLE `$t` ADD COLUMN `cast` TEXT")
            }
            if (t == "movie") db.execSQL("ALTER TABLE `movie` ADD COLUMN `duration` TEXT")
            db.execSQL("DROP INDEX IF EXISTS `index_${t}_providerId`")
            db.execSQL("DROP INDEX IF EXISTS `index_${t}_providerId_categoryId`")
            if (t == "channel") {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channel_providerId_num_sortKey` ON `channel` (`providerId`, `num`, `sortKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_channel_providerId_categoryId_num_sortKey` ON `channel` (`providerId`, `categoryId`, `num`, `sortKey`)")
            } else {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_${t}_providerId_sortKey` ON `$t` (`providerId`, `sortKey`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_${t}_providerId_categoryId_sortKey` ON `$t` (`providerId`, `categoryId`, `sortKey`)")
            }
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `${t}_fts` USING FTS4(`name` TEXT NOT NULL, tokenize=unicode61, content=`$t`)")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${t}_fts_BEFORE_UPDATE BEFORE UPDATE ON `$t` BEGIN DELETE FROM `${t}_fts` WHERE `docid`=OLD.`rowid`; END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${t}_fts_BEFORE_DELETE BEFORE DELETE ON `$t` BEGIN DELETE FROM `${t}_fts` WHERE `docid`=OLD.`rowid`; END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${t}_fts_AFTER_UPDATE AFTER UPDATE ON `$t` BEGIN INSERT INTO `${t}_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END")
            db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${t}_fts_AFTER_INSERT AFTER INSERT ON `$t` BEGIN INSERT INTO `${t}_fts`(`docid`, `name`) VALUES (NEW.`rowid`, NEW.`name`); END")
            db.execSQL("INSERT INTO `${t}_fts`(`${t}_fts`) VALUES('rebuild')")
        }
    }
}

/** 11 → 12 : cache des détails de film (get_vod_info) et durée des épisodes. Aucune donnée existante n'est touchée. */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `episode` ADD COLUMN `duration` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vod_info` (`providerId` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, `plot` TEXT, `cast` TEXT, `director` TEXT, " +
                "`genre` TEXT, `duration` TEXT, `releaseDate` TEXT, `rating` REAL, `backdrop` TEXT, `trailer` TEXT, `tmdbId` TEXT, `country` TEXT, " +
                "`originalName` TEXT, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`providerId`, `remoteId`))",
        )
    }
}

/**
 * 12 → 13 : enregistrements programmés depuis le guide (fenêtre voulue + nom de chaîne).
 * Migration DÉDIÉE au lot B1 ; aucune donnée existante n'est touchée.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recording` ADD COLUMN `scheduledStartMs` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `recording` ADD COLUMN `scheduledEndMs` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `recording` ADD COLUMN `channelName` TEXT")
    }
}

/**
 * 13 → 14 : cache des fiches TMDB (lot B1). Table nouvelle, aucune donnée existante n'est touchée.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tmdb_info` (`kind` TEXT NOT NULL, `providerId` INTEGER NOT NULL, `remoteId` TEXT NOT NULL, " +
                "`tmdbId` INTEGER, `overview` TEXT, `posterPath` TEXT, `backdropPath` TEXT, `rating` REAL, `cast` TEXT, `trailerKey` TEXT, " +
                "`originalLanguage` TEXT, `lang` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`kind`, `providerId`, `remoteId`))",
        )
    }
}

/**
 * 14 → 15 : PROFILS (suppose les DEUX migrations de B1 en amont : 12 → 13 recording, 13 → 14 tmdb_info). Crée `profile` (le profil existant devient « Principal », id 1), les préférences et catégories
 * masquées par profil, et rattache favoris et historique au profil 1 (clé primaire élargie à `profileId`).
 * Aucune donnée n'est perdue. Sources, catalogue et EPG restent globaux.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER NOT NULL, `initial` TEXT NOT NULL, `isKids` INTEGER NOT NULL, `pinHash` TEXT, `createdAt` INTEGER NOT NULL)")
        db.execSQL("INSERT INTO `profile` (`id`, `name`, `color`, `initial`, `isKids`, `pinHash`, `createdAt`) VALUES (1, 'Principal', ${0xFF3B82F6.toInt()}, 'P', 0, NULL, CAST(strftime('%s','now') AS INTEGER) * 1000)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `profile_pref` (`profileId` INTEGER NOT NULL, `key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`profileId`, `key`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `profile_hidden_category` (`profileId` INTEGER NOT NULL, `categoryKey` TEXT NOT NULL, PRIMARY KEY(`profileId`, `categoryKey`))")

        db.execSQL("ALTER TABLE `favorite` RENAME TO `favorite_old`")
        db.execSQL("CREATE TABLE IF NOT EXISTS `favorite` (`providerId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `remoteId` TEXT NOT NULL, `profileId` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`profileId`, `providerId`, `kind`, `remoteId`))")
        db.execSQL("INSERT INTO `favorite` (`providerId`, `kind`, `remoteId`, `profileId`) SELECT `providerId`, `kind`, `remoteId`, 1 FROM `favorite_old`")
        db.execSQL("DROP TABLE `favorite_old`")

        db.execSQL("ALTER TABLE `watch_history` RENAME TO `watch_history_old`")
        db.execSQL("CREATE TABLE IF NOT EXISTS `watch_history` (`providerId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `remoteId` TEXT NOT NULL, `title` TEXT NOT NULL, `poster` TEXT, `streamUrl` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `watchedAt` INTEGER NOT NULL, `parentRemoteId` TEXT, `profileId` INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(`profileId`, `providerId`, `kind`, `remoteId`))")
        db.execSQL("INSERT INTO `watch_history` (`providerId`, `kind`, `remoteId`, `title`, `poster`, `streamUrl`, `positionMs`, `durationMs`, `watchedAt`, `parentRemoteId`, `profileId`) SELECT `providerId`, `kind`, `remoteId`, `title`, `poster`, `streamUrl`, `positionMs`, `durationMs`, `watchedAt`, `parentRemoteId`, 1 FROM `watch_history_old`")
        db.execSQL("DROP TABLE `watch_history_old`")
    }
}

/** Chaîne complète 10 → version courante : source UNIQUE pour l'application et pour les tests de migration. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
