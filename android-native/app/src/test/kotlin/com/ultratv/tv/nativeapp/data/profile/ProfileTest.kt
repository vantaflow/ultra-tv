package com.ultratv.tv.nativeapp.data.profile

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.FavoriteEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ProfileTest {
    private lateinit var db: UltraDb
    private lateinit var repo: ProfileRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), UltraDb::class.java)
            .allowMainThreadQueries().build()
        runBlocking { db.profileDao().insert(ProfileEntity(name = "Principal", color = 1, initial = "P")) }
        repo = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        waitCurrent()
    }
    @After fun tearDown() { db.close() }

    private fun waitCurrent() = runBlocking { withTimeout(5000) { repo.currentId.first() } }
    private fun <T> run(b: suspend () -> T) = runBlocking { b() }

    @Test fun favoris_parProfil_sontCloisonnes() = run {
        val kids = repo.create("Léo", 2)!!
        val dao = db.favoriteDao()
        dao.add(FavoriteEntity(1, "MOVIE", "a", profileId = 1))
        dao.add(FavoriteEntity(1, "MOVIE", "b", profileId = kids))
        assertEquals(listOf("a"), dao.observeForKind(1, 1, "MOVIE").first().map { it.remoteId })
        assertEquals(listOf("b"), dao.observeForKind(kids, 1, "MOVIE").first().map { it.remoteId })
    }

    @Test fun historique_etReprise_parProfil() = run {
        val other = repo.create("Sam", 2)!!
        val h = db.watchHistoryDao()
        h.upsert(WatchHistoryEntity(1, "MOVIE", "x", "T", null, "u", 100, 9000, 1, profileId = 1))
        h.upsert(WatchHistoryEntity(1, "MOVIE", "x", "T", null, "u", 777, 9000, 1, profileId = other))
        assertEquals(100L, h.positionFor(1, 1, "MOVIE", "x"))
        assertEquals(777L, h.positionFor(other, 1, "MOVIE", "x"))
    }

    @Test fun suppression_purgeLesDonneesDuProfil_etRefuseLeDernier() = run {
        val other = repo.create("Sam", 2)!!
        db.favoriteDao().add(FavoriteEntity(1, "MOVIE", "b", profileId = other))
        assertTrue(repo.delete(other))
        assertTrue(db.favoriteDao().observeForKind(other, 1, "MOVIE").first().isEmpty())
        assertFalse(repo.delete(1))
        assertEquals(1, db.profileDao().count())
    }

    @Test fun pin_estHache_etVerifie() = run {
        val id = repo.create("Papa", 3, pin = "1234")!!
        val p = db.profileDao().byId(id)!!
        assertNotEquals("1234", p.pinHash)
        assertTrue(repo.verifyPin(id, "1234")); assertFalse(repo.verifyPin(id, "0000"))
    }

    @Test fun categoriesMasquees_parProfil() = run {
        val other = repo.create("Sam", 2)!!
        repo.setCategoryHidden("MOVIE:1:5", true)
        assertEquals(setOf("MOVIE:1:5"), repo.hiddenCategoryKeys.first())
        repo.select(other)
        withTimeout(5000) { while (repo.hiddenCategoryKeys.first().isNotEmpty()) kotlinx.coroutines.delay(20) }
    }

    @Test fun preferences_surchargeSeulementLeProfilCourant() = run {
        repo.putPref(ProfilePrefs.THEME, "LIGHT")
        val p = repo.currentPrefs.first()
        assertEquals(AppTheme.LIGHT, p.applyTo(UserPrefs()).theme)
        val other = repo.create("Sam", 2)!!
        repo.select(other)
        val q = withTimeout(5000) { var r = repo.currentPrefs.first(); while (r.theme != null) { kotlinx.coroutines.delay(20); r = repo.currentPrefs.first() }; r }
        assertEquals(AppTheme.DARK, q.applyTo(UserPrefs()).theme)
    }

    @Test fun profilEnfants_masqueLesCategoriesAdultes() = run {
        val kids = repo.create("Léo", 2, isKids = true)!!
        db.openHelper.writableDatabase.execSQL("INSERT INTO category (providerId, kind, remoteId, name, locked, enabled, position, lang) VALUES (1,'LIVE','9','XXX Night',0,1,0,''), (1,'LIVE','10','Sport',0,1,0,'')")
        repo.select(kids)
        val hidden = withTimeout(5000) { var r = repo.hiddenCategoryKeys.first(); while (r.isEmpty()) { kotlinx.coroutines.delay(20); r = repo.hiddenCategoryKeys.first() }; r }
        assertEquals(setOf("LIVE:1:9"), hidden)
    }

    @Test fun regles_kids_et_demarrage() {
        val kid = ProfileEntity(1, "L", 0, "L", isKids = true)
        assertTrue(KidsRules.forcesParentalControl(kid))
        assertTrue(UserPrefs().let { ProfilePrefs().applyTo(it, isKids = true).hideAdultCategories })
        assertFalse(KidsRules.canOpenTechnicalSettings(kid, parentalPinSet = false, pinVerified = true))
        assertFalse(KidsRules.canOpenTechnicalSettings(kid, parentalPinSet = true, pinVerified = false))
        assertTrue(KidsRules.canOpenTechnicalSettings(kid, true, true))
        assertTrue(KidsRules.canOpenTechnicalSettings(kid.copy(isKids = false), false, false))
        assertFalse(StartupRules.shouldAsk(1, StartupMode.ALWAYS_ASK))
        assertTrue(StartupRules.shouldAsk(2, StartupMode.ALWAYS_ASK))
        assertFalse(StartupRules.shouldAsk(2, StartupMode.LAST_PROFILE))
    }
}
