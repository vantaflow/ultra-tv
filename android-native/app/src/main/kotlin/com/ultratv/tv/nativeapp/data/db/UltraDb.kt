package com.ultratv.tv.nativeapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProviderEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        CategoryEntity::class,
        FavoriteEntity::class,
        EpgEntity::class,
        WatchHistoryEntity::class,
        RecordingEntity::class,
        com.ultratv.tv.nativeapp.data.reminders.ReminderEntity::class,
        ChannelFts::class,
        MovieFts::class,
        SeriesFts::class,
        VodInfoEntity::class,
        com.ultratv.tv.nativeapp.data.tmdb.TmdbInfoEntity::class,
        com.ultratv.tv.nativeapp.data.profile.ProfileEntity::class,
        com.ultratv.tv.nativeapp.data.profile.ProfilePrefEntity::class,
        com.ultratv.tv.nativeapp.data.profile.ProfileHiddenCategoryEntity::class,
    ],
    version = 17,
    exportSchema = true,
)
abstract class UltraDb : RoomDatabase() {
    abstract fun providerDao(): ProviderRawDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun epgDao(): EpgDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun recordingDao(): RecordingDao
    abstract fun vodInfoDao(): VodInfoDao
    abstract fun tmdbDao(): com.ultratv.tv.nativeapp.data.tmdb.TmdbDao
    abstract fun profileDao(): com.ultratv.tv.nativeapp.data.profile.ProfileDao
    abstract fun reminderDao(): com.ultratv.tv.nativeapp.data.reminders.ReminderDao
}
