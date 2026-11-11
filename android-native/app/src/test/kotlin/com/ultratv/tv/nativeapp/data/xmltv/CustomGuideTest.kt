package com.ultratv.tv.nativeapp.data.xmltv

import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.prefs.ProviderLimitsStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Guide personnalisé : forme de l'adresse, vérification réseau (faux serveur en mémoire), remplacer / compléter par source. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class CustomGuideTest {
    private val server = FakeFeedServer()
    private val checker = GuideChecker(OkHttpClient.Builder().addInterceptor(server.interceptor).build())

    @After fun tearDown() { server.closeAll() }

    private fun gz(b: ByteArray) = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()
    private val xmltv = "<?xml version=\"1.0\"?>\n<!DOCTYPE tv SYSTEM \"xmltv.dtd\">\n<tv generator-info-name=\"x\"><channel id=\"a\"/></tv>".toByteArray()
    private fun check(url: String) = runBlocking { checker.check(url) }

    // ---- forme de l'adresse ----

    private fun problem(raw: String) = (GuideAddress.validate(raw) as? GuideAddress.Validation.Invalid)?.problem

    @Test fun adresse_httpEtHttps_acceptees() {
        assertTrue(GuideAddress.validate("http://guide.invalid/epg.xml") is GuideAddress.Validation.Ok)
        assertTrue(GuideAddress.validate("  https://guide.invalid/a/epg.xml.gz ") is GuideAddress.Validation.Ok)
        assertTrue(GuideAddress.validate("HTTPS://guide.invalid/EPG.XML.GZ") is GuideAddress.Validation.Ok)
    }

    @Test fun adresse_queryString_toleree() {
        assertTrue(GuideAddress.validate("https://guide.invalid/epg.xml.gz?token=abc&x=1") is GuideAddress.Validation.Ok)
        assertTrue(GuideAddress.validate("http://guide.invalid:8080/epg.xml?u=a#frag") is GuideAddress.Validation.Ok)
    }

    @Test fun adresse_refuseFtpEtAutresSchemas() {
        assertEquals(GuideAddress.Problem.SCHEME, problem("ftp://guide.invalid/epg.xml"))
        assertEquals(GuideAddress.Problem.SCHEME, problem("guide.invalid/epg.xml"))
        assertEquals(GuideAddress.Problem.SCHEME, problem("file:///sdcard/epg.xml"))
    }

    @Test fun adresse_refuseSansExtensionXml() {
        assertEquals(GuideAddress.Problem.EXTENSION, problem("https://guide.invalid/epg"))
        assertEquals(GuideAddress.Problem.EXTENSION, problem("https://guide.invalid/epg.gz"))
        assertEquals(GuideAddress.Problem.EXTENSION, problem("https://guide.invalid/epg.xml.zip"))
        assertEquals(GuideAddress.Problem.EXTENSION, problem("https://guide.invalid/xmltv.php?a=epg.xml"))   // l'extension doit être dans le chemin
        assertEquals(GuideAddress.Problem.EXTENSION, problem("https://guide.invalid/"))
    }

    @Test fun adresse_refuseVideEtSansHote() {
        assertEquals(GuideAddress.Problem.EMPTY, problem("   "))
        assertEquals(GuideAddress.Problem.HOST, problem("https:///epg.xml"))
        assertEquals(GuideAddress.Problem.HOST, problem("https://guide invalide/epg.xml"))
    }

    @Test fun hoteSeul_sansJeton() = assertEquals("guide.invalid", GuideAddress.hostOf("https://guide.invalid/epg.xml?token=secret"))

    // ---- vérification réseau ----

    @Test fun verif_xmltvOk() {
        server.add("epg.xml", xmltv)
        assertEquals(GuideCheck.Ok, check("http://guide.invalid/epg.xml"))
        assertTrue(server.requests.single().startsWith("http://guide.invalid/"))
    }

    @Test fun verif_gzipOk_detecteParOctetsMagiques() {
        // Servi sous un nom .xml mais compressé : seuls les octets magiques le disent.
        server.add("epg.xml", gz(xmltv))
        server.add("epg.xml.gz", gz(xmltv))
        assertEquals(GuideCheck.Ok, check("https://guide.invalid/epg.xml"))
        assertEquals(GuideCheck.Ok, check("https://guide.invalid/epg.xml.gz?t=1"))
    }

    @Test fun verif_404_codeHttp() = assertEquals(GuideCheck.Http(404), check("https://guide.invalid/absent.xml"))

    @Test fun verif_serveurEnErreur_codeHttp() {
        server.add("epg.xml", xmltv).apply { failFirst = 5; failCode = 503 }
        assertEquals(GuideCheck.Http(503), check("https://guide.invalid/epg.xml"))
    }

    @Test fun verif_pageHtml_pasUnGuide() {
        server.add("epg.xml", "<!DOCTYPE html><html><body>Connexion requise</body></html>".toByteArray())
        assertEquals(GuideCheck.NotXmltv, check("https://guide.invalid/epg.xml"))
    }

    @Test fun verif_gzipIllisible_pasUnGuide() {
        server.add("epg.xml.gz", byteArrayOf(0x1f, 0x8b.toByte(), 0, 1, 2, 3))
        assertEquals(GuideCheck.NotXmltv, check("https://guide.invalid/epg.xml.gz"))
    }

    @Test fun verif_injoignable() {
        val failing = GuideChecker(OkHttpClient.Builder().addInterceptor { throw java.io.IOException("réseau coupé") }.build())
        assertEquals(GuideCheck.Unreachable, runBlocking { failing.check("https://guide.invalid/epg.xml") })
    }

    @Test fun verif_adresseInvalide_aucuneRequete() {
        assertEquals(GuideCheck.Invalid(GuideAddress.Problem.SCHEME), check("ftp://guide.invalid/epg.xml"))
        assertEquals(GuideCheck.Invalid(GuideAddress.Problem.EXTENSION), check("https://guide.invalid/epg"))
        assertTrue(server.requests.isEmpty())
    }

    // ---- remplacer / compléter ----

    private val custom = "https://guide.invalid/perso.xml.gz"

    @Test fun plan_sansGuidePerso_commeAvant() {
        assertEquals(GuidePlan(null, null, true), GuidePlan.of("XTREAM", emptyList(), null))
        assertEquals(GuidePlan(null, null, false), GuidePlan.of("M3U", emptyList(), null))
        assertEquals(GuidePlan(null, null, true), GuidePlan.of("M3U", listOf("http://x.invalid/g.xml"), null))
        assertEquals(GuidePlan(null, null, false), GuidePlan.of("STALKER", emptyList(), null))
    }

    @Test fun plan_remplacer_seulGuidePerso_pourToutesLesSources() {
        for (kind in listOf("XTREAM", "M3U", "M3U_LOCAL", "STALKER")) {
            val p = GuidePlan.of(kind, listOf("http://x.invalid/g.xml"), CustomGuide(custom, GuideMode.REPLACE))
            assertEquals(GuidePlan(custom, null, true), p)
        }
    }

    @Test fun plan_completer_guideFournisseurPuisPerso() {
        assertEquals(GuidePlan(null, custom, true), GuidePlan.of("XTREAM", emptyList(), CustomGuide(custom, GuideMode.COMPLETE)))
        // M3U sans url-tvg : le guide perso suffit à rendre la source « avec guide ».
        assertEquals(GuidePlan(null, custom, true), GuidePlan.of("M3U", emptyList(), CustomGuide(custom, GuideMode.COMPLETE)))
    }

    @Test fun plan_stalker_aUnGuideDesQuUneAdressePersoExiste() {
        assertEquals(false, GuidePlan.of("STALKER", emptyList(), null).hasFeed)
        assertEquals(true, GuidePlan.of("STALKER", emptyList(), CustomGuide(custom, GuideMode.COMPLETE)).hasFeed)
        assertEquals(true, GuidePlan.of("STALKER", emptyList(), CustomGuide(custom, GuideMode.REPLACE)).hasFeed)
    }

    // Retour utilisateur : playlist M3U sans url-tvg + guide complémentaire personnalisé des Réglages = « aucune information ».
    @Test fun plan_guideComplementaireSeul_m3uSansUrlTvg() {
        assertEquals(GuidePlan(null, null, true, extraOnly = true), GuidePlan.of("M3U", emptyList(), null, globalExtra = true))
        assertEquals(GuidePlan(null, null, true, extraOnly = true), GuidePlan.of("M3U_LOCAL", emptyList(), null, globalExtra = true))
        assertEquals(GuidePlan(null, null, true, extraOnly = true), GuidePlan.of("STALKER", emptyList(), null, globalExtra = true))
    }

    @Test fun plan_guideComplementaireSeul_pasPourUneSourceAvecGuidePropre() {
        assertEquals(GuidePlan(null, null, true), GuidePlan.of("M3U", listOf("http://x.invalid/g.xml"), null, globalExtra = true))
        assertEquals(GuidePlan(null, null, true), GuidePlan.of("XTREAM", emptyList(), null, globalExtra = true))
        // Guide complémentaire désactivé : source sans guide, comme avant.
        assertEquals(GuidePlan(null, null, false), GuidePlan.of("M3U", emptyList(), null, globalExtra = false))
    }

    @Test fun plan_guideDeLaSource_prioriteSurLeComplementGlobal() {
        assertEquals(GuidePlan(custom, null, true), GuidePlan.of("M3U", emptyList(), CustomGuide(custom, GuideMode.REPLACE), globalExtra = true))
        assertEquals(GuidePlan(null, custom, true), GuidePlan.of("M3U", emptyList(), CustomGuide(custom, GuideMode.COMPLETE), globalExtra = true))
    }

    @Test fun plan_adressePersoInvalide_ignoree() {
        assertEquals(GuidePlan(null, null, false), GuidePlan.of("STALKER", emptyList(), CustomGuide("ftp://x.invalid/g.xml", GuideMode.REPLACE)))
    }

    @Test fun mode_inconnuOuAbsent_completer() {
        assertEquals(GuideMode.COMPLETE, GuideMode.parse(null))
        assertEquals(GuideMode.COMPLETE, GuideMode.parse("n'importe quoi"))
        assertEquals(GuideMode.REPLACE, GuideMode.parse("replace"))
    }

    // ---- stockage par source (sans migration Room) ----

    @Test fun stockage_parIdentifiantDeSource() {
        val store = ProviderLimitsStore(ApplicationProvider.getApplicationContext())
        assertNull(store.customEpg(7))
        store.setCustomEpg(7, CustomGuide(custom, GuideMode.REPLACE))
        store.setCustomEpg(8, CustomGuide("http://x.invalid/b.xml", GuideMode.COMPLETE))
        assertEquals(CustomGuide(custom, GuideMode.REPLACE), store.customEpg(7))
        assertEquals(CustomGuide("http://x.invalid/b.xml", GuideMode.COMPLETE), store.customEpg(8))
        // Indépendant du guide annoncé par l'en-tête M3U, réécrit à chaque synchro.
        store.setEpgUrls(7, listOf("http://x.invalid/g.xml"))
        store.setEpgUrls(7, emptyList())
        assertEquals(CustomGuide(custom, GuideMode.REPLACE), store.customEpg(7))
        store.setCustomEpg(7, null)
        assertNull(store.customEpg(7))
        assertEquals(CustomGuide("http://x.invalid/b.xml", GuideMode.COMPLETE), store.customEpg(8))
    }
}
