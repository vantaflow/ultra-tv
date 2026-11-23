package com.ultratv.tv.nativeapp.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.AdaptiveProfile
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.data.db.FavoriteEntity
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SyncPart
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.m3u.M3uParser
import com.ultratv.tv.nativeapp.data.parental.ParentalStore
import com.ultratv.tv.nativeapp.data.prefs.LangDisabledStore
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.profile.InMemoryProfileStateStore
import com.ultratv.tv.nativeapp.data.profile.ProfileRepository
import com.ultratv.tv.nativeapp.data.stalker.FakeVodPortal
import com.ultratv.tv.nativeapp.data.stalker.StalkerClient
import com.ultratv.tv.nativeapp.data.stalker.StalkerCreds
import com.ultratv.tv.nativeapp.data.stalker.StalkerEndpointStore
import com.ultratv.tv.nativeapp.data.stalker.StalkerIdentity
import com.ultratv.tv.nativeapp.data.xmltv.XmltvParser
import com.ultratv.tv.nativeapp.data.xtream.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * « Modifier » une source : formulaire prérempli, détection d'un changement de fournisseur, et mise à jour de la MÊME
 * source (identifiant local, favoris et réglages gardés). Données fictives, aucun réseau réel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceEditTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val toClose = mutableListOf<UltraDb>()
    private var savedBox: com.ultratv.tv.nativeapp.data.security.SecretBox? = null
    @After fun cleanup() { toClose.forEach { runCatching { it.close() } }; savedBox?.let { com.ultratv.tv.nativeapp.data.security.SecretBox.shared = it } }

    private fun xtream(url: String = "http://srv.example.invalid:8080", user: String = "alice", pw: String = "secret") =
        ProviderEntity(id = 3, name = "Mon Xtream", kind = "XTREAM", baseUrl = url, username = user, password = pw)

    // ───────────── Règles pures ─────────────

    @Test fun prefill_xtream_etStalker_motDePasseEtIdentiteRepris() {
        val x = SourceEdit.prefill(xtream())
        assertEquals("Mon Xtream", x.name); assertEquals("http://srv.example.invalid:8080", x.url)
        assertEquals("alice", x.user); assertEquals("secret", x.password)
        assertFalse("jamais d'identifiant dans toString", x.toString().contains("alice") || x.toString().contains("secret"))

        val id = StalkerIdentity("SN1", "DEV1", "DEV2", "SIG")
        val st = ProviderEntity(id = 4, name = "Portail", kind = "STALKER", baseUrl = "http://portail.invalid/c", username = "00:1A:79:AB:CD:EF",
            password = StalkerCreds.pack("bob", "pw", id))
        val s = SourceEdit.prefill(st)
        assertEquals("00:1A:79:AB:CD:EF", s.mac); assertEquals("bob", s.login); assertEquals("pw", s.password); assertEquals(id, s.identity)

        val jf = SourceEdit.prefill(ProviderEntity(id = 5, name = "JF", kind = "JELLYFIN", baseUrl = "http://jf.invalid:8096", username = "uid", password = "token"))
        assertEquals("ni identifiant interne ni jeton dans le formulaire", "" to "", jf.user to jf.password)
    }

    @Test fun changementDeFournisseur_hoteOuCompte_pasLeMotDePasseNiLePort() {
        val p = xtream()
        assertFalse(SourceEdit.changesProvider(p, "http://srv.example.invalid:8080", "alice"))
        assertFalse("https et autre port : même fournisseur", SourceEdit.changesProvider(p, "https://SRV.example.invalid:2096/", "alice"))
        assertTrue("autre hôte", SourceEdit.changesProvider(p, "http://autre.invalid:8080", "alice"))
        assertTrue("autre compte", SourceEdit.changesProvider(p, "http://srv.example.invalid:8080", "bob"))

        val st = ProviderEntity(id = 4, name = "P", kind = "STALKER", baseUrl = "http://portail.invalid/c", username = "00:1A:79:AB:CD:EF", password = "")
        assertFalse(SourceEdit.changesProvider(st, "http://portail.invalid/stalker_portal/c/", "00:1a:79:ab:cd:ef"))
        assertTrue("autre MAC", SourceEdit.changesProvider(st, "http://portail.invalid/c", "00:1A:79:00:00:01"))

        val m3u = ProviderEntity(id = 6, name = "L", kind = "M3U", baseUrl = "http://liste.invalid/a.m3u", username = "", password = "")
        assertFalse(SourceEdit.changesProvider(m3u, "https://liste.invalid/a.m3u", ""))
        assertTrue(SourceEdit.changesProvider(m3u, "http://liste.invalid/b.m3u", ""))
    }

    // ───────────── Dépôt ─────────────

    private class Rig(val db: UltraDb, val repo: ProviderRepository)

    private fun rig(fake: okhttp3.Interceptor = FakeVodPortal()): Rig {
        // Mots de passe chiffrés au repos : clé de test (pas de keystore Android sous Robolectric).
        if (savedBox == null) savedBox = com.ultratv.tv.nativeapp.data.security.SecretBox.shared
        val key = javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        com.ultratv.tv.nativeapp.data.security.SecretBox.shared = com.ultratv.tv.nativeapp.data.security.SecretBox { key }
        val db = Room.inMemoryDatabaseBuilder(ctx, UltraDb::class.java).allowMainThreadQueries()
            .addCallback(com.ultratv.tv.nativeapp.data.db.DefaultProfileCallback()).build().also { toClose += it }
        val ok = OkHttpClient.Builder().addInterceptor(fake).build()
        val profiles = ProfileRepository(db.profileDao(), InMemoryProfileStateStore(), CoroutineScope(Dispatchers.Default))
        val repo = ProviderRepository(
            providerDao = ProviderDao(db.providerDao()), channelDao = db.channelDao(), movieDao = db.movieDao(), seriesDao = db.seriesDao(),
            episodeDao = db.episodeDao(), categoryDao = db.categoryDao(), xtream = XtreamClient(ok), m3u = M3uParser(ok), parental = ParentalStore(ctx),
            syncStatus = SyncStatusBus(), xmltv = XmltvParser(ok), epgDao = db.epgDao(), db = db, prefs = UserPreferencesStore(ctx, profiles),
            adaptive = AdaptiveProfile(ctx, NetworkMonitor(ctx)), limits = ProviderLimitsStore(ctx), langDisabled = LangDisabledStore(ctx),
            hiddenChannels = com.ultratv.tv.nativeapp.data.prefs.HiddenChannelsStore(ctx),
            stalker = StalkerClient(ok, StalkerEndpointStore.inMemory()).also { it.retryDelayMs = 1 },
        )
        return Rig(db, repo)
    }

    private fun UltraDb.n(sql: String): Int = openHelper.readableDatabase.query(sql).use { it.moveToFirst(); it.getInt(0) }

    @Test fun modifierXtream_memeSource_favorisGardes_motDePasseSeul_pasDeRemiseAZero() = runBlocking {
        val r = rig()
        val id = r.repo.addXtream("Mon Xtream", "http://srv.example.invalid:8080", "alice", "ancien")
        ProviderDao(r.db.providerDao()).markSynced(id, SyncPart.LIVE, 1_000L)
        r.db.favoriteDao().add(FavoriteEntity(providerId = id, kind = "LIVE", remoteId = "42"))
        val res = r.repo.editXtream(id, "Renommée", "http://srv.example.invalid:8080/", "alice", "nouveau")
        assertEquals(id, res.id); assertFalse(res.providerChanged)
        val p = r.repo.byId(id)!!
        assertEquals("Renommée", p.name); assertEquals("nouveau", p.password); assertEquals("http://srv.example.invalid:8080", p.baseUrl)
        assertEquals("dates de synchro gardées", 1_000L, p.lastLiveSyncAt)
        assertEquals(1, r.db.n("SELECT COUNT(*) FROM provider"))
        assertEquals("favori toujours rattaché", 1, r.db.n("SELECT COUNT(*) FROM favorite WHERE providerId = $id"))
    }

    @Test fun modifierXtream_autreFournisseur_datesRemisesAZero_memeIdentifiant() = runBlocking {
        val r = rig()
        val id = r.repo.addXtream("X", "http://srv.example.invalid:8080", "alice", "pw")
        ProviderDao(r.db.providerDao()).markSynced(id, SyncPart.LIVE, 1_000L)
        val res = r.repo.editXtream(id, "", "http://autre.invalid", "carol", "pw2")
        assertTrue(res.providerChanged); assertEquals(id, res.id)
        val p = r.repo.byId(id)!!
        assertEquals("nom vide : ancien nom gardé", "X", p.name)
        assertEquals(0L, p.lastLiveSyncAt)
        assertEquals("http://autre.invalid", p.baseUrl); assertEquals("carol", p.username)
    }

    @Test fun modifierXtream_versUnCompteDejaPresent_refuse_rienNeChange() = runBlocking {
        val r = rig()
        val a = r.repo.addXtream("A", "http://srv.example.invalid", "alice", "pw")
        r.repo.addXtream("B", "http://srv.example.invalid", "bob", "pw")
        try { r.repo.editXtream(a, "A", "http://srv.example.invalid", "bob", "pw"); fail("doublon attendu") } catch (e: SourceExistsException) { /* attendu */ }
        assertEquals("alice", r.repo.byId(a)!!.username)
    }

    @Test fun modifierStalker_portailVerifie_identiteGardee_memeSource() = runBlocking {
        val r = rig(FakeVodPortal())
        val portal = "http://portail-fictif.invalid/stalker_portal/c"
        val id = r.repo.addStalker("P", portal, "00:1A:79:AB:CD:EF")
        val ident = StalkerIdentity("SN9", "D1", "D2", "S")
        val res = r.repo.editStalker(id, "Portail du salon", portal, "00:1A:79:AB:CD:EF", "", "", ident)
        assertFalse(res.providerChanged)
        val p = r.repo.byId(id)!!
        assertEquals("Portail du salon", p.name)
        assertEquals(ident, StalkerCreds.identity(p.password))
    }

    @Test fun modifierStalker_portailInjoignable_rienNestEnregistre() = runBlocking {
        val inner = FakeVodPortal()
        // Hôte « injoignable » : aucune réponse du portail (connexion refusée).
        val r = rig { chain -> if (chain.request().url.host == "injoignable.invalid") throw java.net.ConnectException("refusé") else inner.intercept(chain) }
        val id = r.repo.addStalker("P", "http://portail-fictif.invalid/stalker_portal/c", "00:1A:79:AB:CD:EF")
        try { r.repo.editStalker(id, "Nouveau", "http://injoignable.invalid/c", "00:1A:79:AB:CD:EF", "", "", StalkerIdentity()); fail("échec attendu") } catch (e: Exception) { /* attendu */ }
        val p = r.repo.byId(id)!!
        assertEquals("P", p.name); assertEquals("http://portail-fictif.invalid/stalker_portal/c", p.baseUrl)
    }

    @Test fun playlistLocale_autreFichier_memeSource_chainesConnuesGardentLeurIdentifiant() = runBlocking {
        val r = rig()
        val v1 = "#EXTM3U\n#EXTINF:-1 tvg-id=\"a\" group-title=\"Info\",Chaîne A\nhttp://flux.invalid/a\n#EXTINF:-1 tvg-id=\"b\" group-title=\"Info\",Chaîne B\nhttp://flux.invalid/b\n"
        val id = r.repo.addM3uFromText("", "liste.m3u", v1)
        val before = r.db.channelDao().idsByRemote(id).associate { it.remoteId to it.id }
        val v2 = v1 + "#EXTINF:-1 tvg-id=\"c\" group-title=\"Sport\",Chaîne C\nhttp://flux.invalid/c\n"
        r.repo.replaceM3uLocal(id, "liste-2.m3u", v2)
        val after = r.db.channelDao().idsByRemote(id).associate { it.remoteId to it.id }
        assertEquals(3, after.size)
        before.forEach { (remote, cid) -> assertEquals("identifiant gardé pour $remote", cid, after[remote]) }
        assertEquals("liste-2.m3u", r.repo.byId(id)!!.baseUrl)
        assertEquals(1, r.db.n("SELECT COUNT(*) FROM provider"))
    }
}
