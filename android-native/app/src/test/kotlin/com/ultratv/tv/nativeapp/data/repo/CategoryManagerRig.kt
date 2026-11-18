package com.ultratv.tv.nativeapp.data.repo

import android.content.Context
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore
import com.ultratv.tv.nativeapp.data.prefs.LangDisabledStore
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/** Assemble un [CategoryManager] de test (profil courant = le Principal par défaut). */
fun testCategoryManager(
    db: UltraDb,
    ctx: Context,
    langDisabled: LangDisabledStore = LangDisabledStore(ctx),
    hidden: HiddenChannelsStore = HiddenChannelsStore(ctx),
    profiles: ProfileRepository = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default)),
): CategoryManager {
    val hiding = ProfileChannelHiding(db, hidden, SyncCoordinator(ctx), ctx)
    return CategoryManager(db, db.categoryDao(), db.channelDao(), db.movieDao(), db.seriesDao(), SyncCoordinator(ctx), langDisabled, hidden, profiles, hiding, ProfileSwitcher(db, hiding))
}
