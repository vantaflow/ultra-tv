package com.ultratv.tv.nativeapp.data.stalker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règles pures du protocole Stalker : MAC, identifiants empaquetés, adresses internes, points d'entrée, dates. */
class StalkerProtocolTest {

    @Test fun mac_normalisee_quelleQueSoitLaSaisie() {
        assertEquals("00:1A:79:AB:CD:EF", StalkerMac.normalize("00-1a-79-ab-cd-ef"))
        assertEquals("00:1A:79:AB:CD:EF", StalkerMac.normalize(" 001a79abcdef "))
        assertEquals("00:1A:79:AB:CD:EF", StalkerMac.normalize("00:1A:79:AB:CD:EF"))
        assertNull(StalkerMac.normalize("00:1A:79:AB:CD"))
        assertNull(StalkerMac.normalize("00:1A:79:AB:CD:EF:01"))
        assertFalse(StalkerMac.isValid("zz:zz:zz:zz:zz:zz"))
    }

    @Test fun mac_saisieTelecommande_separateursAutomatiques() {
        assertEquals("00:1A:", StalkerMac.formatTyping("001a"))
        assertEquals("00:1A:7", StalkerMac.formatTyping("001a7"))
        assertEquals("00:1A:79:AB:CD:EF", StalkerMac.formatTyping("001a79abcdef99"))
        assertEquals("", StalkerMac.formatTyping("::--"))
    }

    @Test fun mac_masquee_seulsLesDeuxDerniersOctets() {
        assertEquals("••:••:••:••:CD:EF", StalkerMac.mask("00:1A:79:AB:CD:EF"))
        assertFalse(StalkerMac.mask("00:1A:79:AB:CD:EF").contains("1A"))
    }

    @Test fun identifiants_empaquetes_allerRetour() {
        assertEquals("", StalkerCreds.pack("", ""))
        assertEquals("" to "", StalkerCreds.unpack(""))
        val packed = StalkerCreds.pack("jean", "s3cr:et")
        assertEquals("jean" to "s3cr:et", StalkerCreds.unpack(packed))
        assertEquals("" to "seul", StalkerCreds.unpack("seul"))
    }

    @Test fun adresseInterne_construiteEtRelue() {
        val url = StalkerLink.build(7, "itv", "ffmpeg http://localhost/ch/1234_")
        assertTrue(StalkerLink.isStalker(url))
        val p = StalkerLink.parse(url)!!
        assertEquals(7L, p.providerId); assertEquals("itv", p.type); assertEquals("ffmpeg http://localhost/ch/1234_", p.cmd); assertNull(p.series)
        val ep = StalkerLink.parse(StalkerLink.build(3, "vod", "eyJ0eXBlIjoibW92aWUifQ==", 4))!!
        assertEquals("vod", ep.type); assertEquals("eyJ0eXBlIjoibW92aWUifQ==", ep.cmd); assertEquals(4, ep.series)
        assertNull(StalkerLink.parse("http://h/live/u/p/1.ts"))
        assertNull(StalkerLink.parse("stalker://x/itv?cmd=a"))
    }

    @Test fun commande_prefixeDeLecteurRetire_etLocalhostNonJouable() {
        assertEquals("http://srv/play/live.php?stream=1&play_token=T", StalkerLink.cleanCmd("ffmpeg http://srv/play/live.php?stream=1&play_token=T"))
        assertEquals("http://srv/a.ts", StalkerLink.cleanCmd("auto http://srv/a.ts"))
        assertEquals("http://srv/a.ts", StalkerLink.cleanCmd("http://srv/a.ts"))
        assertNull(StalkerLink.directUrlOf("ffmpeg http://localhost/ch/12_"))
        assertEquals("http://real.host/x.ts", StalkerLink.directUrlOf("ffmpeg http://real.host/x.ts"))
        assertNull(StalkerLink.directUrlOf("/media/12.mpg"))
    }

    @Test fun pointsDEntree_ordreDEssai() {
        assertEquals(
            listOf("http://h:8080/c/portal.php", "http://h:8080/portal.php", "http://h:8080/server/load.php", "http://h:8080/stalker_portal/server/load.php"),
            StalkerPortal.endpointCandidates("http://h:8080/c/"),
        )
        val sp = StalkerPortal.endpointCandidates("h:80/stalker_portal/c")
        assertEquals("http://h:80/stalker_portal/c/portal.php", sp[0])
        assertTrue("http://h:80/stalker_portal/server/load.php" in sp)
        assertEquals(listOf("http://h/stalker_portal/server/load.php"), StalkerPortal.endpointCandidates("http://h/stalker_portal/server/load.php"))
        assertEquals("http://h:8080/c/", StalkerPortal.referer("http://h:8080/c"))
    }

    @Test fun logos_relatifsRendusAbsolus() {
        assertEquals("http://h/stalker_portal/misc/logos/320/1.png", StalkerPortal.absolute("http://h/stalker_portal/c/", "/stalker_portal/misc/logos/320/1.png"))
        assertEquals("http://h/stalker_portal/misc/logos/320/1.png", StalkerPortal.absolute("http://h/stalker_portal/c/", "1.png"))
        assertEquals("https://cdn/x.png", StalkerPortal.absolute("http://h/c/", "https://cdn/x.png"))
        assertNull(StalkerPortal.absolute("http://h/c/", ""))
    }

    @Test fun appareil_deriveDeLaMac_stable() {
        val a = StalkerDevice("00:1A:79:AB:CD:EF")
        val b = StalkerDevice("00:1A:79:AB:CD:EF")
        assertEquals(a, b)
        assertEquals(13, a.sn.length)
        assertEquals(64, a.deviceId.length)
        assertEquals(a.deviceId.uppercase(), a.deviceId)
        assertFalse(a.sn == StalkerDevice("00:1A:79:AB:CD:00").sn)
    }

    @Test fun dates_formatsCourants() {
        assertEquals(1767139200000L, StalkerDates.parse("2025-12-31 00:00:00"))
        assertEquals(1767139200000L, StalkerDates.parse("2025-12-31"))
        assertEquals(1767139200000L, StalkerDates.parse("1767139200"))
        assertEquals(1767139200000L, StalkerDates.parse("December 31, 2025, 12:00 am"))
        assertNull(StalkerDates.parse("0000-00-00 00:00:00"))
        assertNull(StalkerDates.parse("Unlimited"))
        assertNull(StalkerDates.parse("bientôt"))
    }
}
