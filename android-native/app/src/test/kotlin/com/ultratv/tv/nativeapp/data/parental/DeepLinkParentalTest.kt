package com.ultratv.tv.nativeapp.data.parental

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.StartupNav
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.repo.LivePlaybackQueue
import com.ultratv.tv.nativeapp.data.repo.PlaybackContext
import com.ultratv.tv.nativeapp.nav.DeepLink
import com.ultratv.tv.nativeapp.nav.DeepLinkHandler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Élément verrouillé lancé depuis l'écran d'accueil du système (lien profond publié avant le verrouillage) : le code
 * parental est exigé AVANT toute lecture ; mauvais code ou annulation : rien n'est lancé.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepLinkParentalTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), UltraDb::class.java).allowMainThreadQueries().build()
    private val playback = PlaybackContext()
    private val handler = DeepLinkHandler(db.channelDao(), playback, LivePlaybackQueue(), db.movieDao(), db.seriesDao(), db.episodeDao(), db.categoryDao())
    private var pid = 0L

    @Before fun setUp() = runBlocking {
        DeepLinkLocks.clear()
        StartupNav.pending.value = null
        StartupNav.pendingRoute.value = null
        pid = db.providerDao().upsert(ProviderEntity(name = "p", kind = "XTREAM", baseUrl = "http://127.0.0.1", username = "u", password = "p", active = true))
        db.categoryDao().upsertAll(listOf(
            CategoryEntity(providerId = pid, kind = "MOVIE", remoteId = "adult", name = "Films verrouillés", locked = true),
            CategoryEntity(providerId = pid, kind = "MOVIE", remoteId = "free", name = "Films"),
            CategoryEntity(providerId = pid, kind = "SERIES", remoteId = "sl", name = "Séries verrouillées", locked = true),
            CategoryEntity(providerId = pid, kind = "LIVE", remoteId = "ll", name = "Direct verrouillé", locked = true),
        ))
        db.movieDao().upsertAll(listOf(
            MovieEntity(providerId = pid, remoteId = "10", name = "Film Verrou", poster = null, categoryId = "adult", streamUrl = "http://m/10", container = null, year = null, rating = null, plot = null),
            MovieEntity(providerId = pid, remoteId = "11", name = "Film Libre", poster = null, categoryId = "free", streamUrl = "http://m/11", container = null, year = null, rating = null, plot = null),
        ))
        db.seriesDao().upsertAll(listOf(SeriesEntity(providerId = pid, remoteId = "s1", name = "Série", poster = null, categoryId = "sl", year = null, rating = null, plot = null)))
        val sid = db.seriesDao().byRemoteId(pid, "s1")!!.id
        db.episodeDao().upsertAll(listOf(EpisodeEntity(seriesId = sid, remoteId = "e1", season = 1, episode = 1, title = "Ep 1", streamUrl = "http://e/1", container = null, plot = null)))
        db.channelDao().upsertAll(listOf(ChannelEntity(providerId = pid, remoteId = "c1", name = "Chaîne", logo = null, categoryId = "ll", streamUrl = "http://c/1")))
    }

    @After fun close() { db.close(); DeepLinkLocks.clear() }

    @Test fun film_verrouille_par_lien_profond_exige_le_code() = runBlocking {
        assertTrue(handler.handle(DeepLink.parse(DeepLink.movie(pid, "10"))!!))
        assertEquals("http://m/10", StartupNav.pending.value?.url)
        assertNotNull("le lecteur doit demander le code", DeepLinkLocks.pendingTitle("http://m/10"))
    }

    @Test fun film_libre_aucun_code() = runBlocking {
        assertTrue(handler.handle(DeepLink.parse(DeepLink.movie(pid, "11"))!!))
        assertNull(DeepLinkLocks.pendingTitle("http://m/11"))
    }

    @Test fun reprise_d_un_episode_de_serie_verrouillee_exige_le_code() = runBlocking {
        assertTrue(handler.handle(DeepLink.parse(DeepLink.episode(pid, "e1"))!!))
        assertEquals("Série", DeepLinkLocks.pendingTitle("http://e/1"))
    }

    @Test fun mauvais_code_rien_ne_se_lance_bon_code_lecture() = runBlocking {
        handler.handle(DeepLink.parse(DeepLink.movie(pid, "10"))!!)
        val verify: suspend (String) -> Boolean = { it == "1234" }
        assertFalse(DeepLinkLocks.tryPin("http://m/10", "0000", verify))
        assertNotNull("toujours en attente après un mauvais code", DeepLinkLocks.pendingTitle("http://m/10"))
        assertTrue(DeepLinkLocks.tryPin("http://m/10", "1234", verify))
        assertNull(DeepLinkLocks.pendingTitle("http://m/10"))
    }

    @Test fun annulation_aucune_lecture_et_nouveau_lien_redemande_le_code() = runBlocking {
        handler.handle(DeepLink.parse(DeepLink.movie(pid, "10"))!!)
        DeepLinkLocks.refuse("http://m/10")
        assertNull(DeepLinkLocks.pendingTitle("http://m/10"))
        handler.handle(DeepLink.parse(DeepLink.movie(pid, "10"))!!)
        assertNotNull(DeepLinkLocks.pendingTitle("http://m/10"))
    }

    @Test fun fiche_verrouillee_ouverte_par_lien_profond_reste_verrouillee() = runBlocking {
        // La fiche s'ouvre sur son panneau verrouillé (DetailLock.PIN) : code exigé avant d'en voir le contenu.
        assertTrue(handler.handle(DeepLink.parse(DeepLink.movieDetail(pid, "10"))!!))
        val m = db.movieDao().byRemoteId(pid, "10")!!
        assertEquals(com.ultratv.tv.nativeapp.ui.detail.DetailLock.PIN,
            com.ultratv.tv.nativeapp.ui.detail.detailLockOf("MOVIE", pid, m.categoryId, db.categoryDao().lockedIds(pid, "MOVIE").toSet(), emptySet(), unlocked = false))
        val s = db.seriesDao().byRemoteId(pid, "s1")!!
        assertEquals(com.ultratv.tv.nativeapp.ui.detail.DetailLock.PIN,
            com.ultratv.tv.nativeapp.ui.detail.detailLockOf("SERIES", pid, s.categoryId, db.categoryDao().lockedIds(pid, "SERIES").toSet(), emptySet(), unlocked = false))
    }

    @Test fun chaine_verrouillee_par_lien_profond_refusee_par_la_porte_du_lecteur() = runBlocking {
        assertTrue(handler.handle(DeepLink.parse(DeepLink.live(pid, "c1"))!!))
        val c = db.channelDao().byRemoteId(pid, "c1")!!
        assertEquals("LIVE", playback.current.value?.kind)
        val gate = ParentalGate(object : LockSource {
            override suspend fun lockedChannelKeys() = emptySet<String>()
            override suspend fun lockedCategoryIds(providerId: Long) = db.categoryDao().lockedIds(providerId, "LIVE").toSet()
        })
        assertFalse("le lecteur affiche la fenêtre de code", gate.admit(c))
    }
}
