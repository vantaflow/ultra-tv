package com.ultratv.tv.nativeapp.data.discovery

import com.ultratv.tv.nativeapp.data.discovery.MediaDiscovery.Found
import com.ultratv.tv.nativeapp.data.discovery.MediaDiscovery.Kind
import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic.ConnKind
import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic.LibraryChoice
import com.ultratv.tv.nativeapp.data.discovery.MediaLibraryLogic.Status
import com.ultratv.tv.nativeapp.data.mediaserver.Plex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDiscoveryTest {
    @Test fun messagesExacts() {
        assertEquals("Who is JellyfinServer?", MediaDiscovery.JELLYFIN_MSG)
        assertEquals("who is EmbyServer?", MediaDiscovery.EMBY_MSG)
        assertEquals("M-SEARCH * HTTP/1.1\r\n\r\n", MediaDiscovery.GDM_MSG)
    }

    @Test fun jellyfin_reponseJson() {
        val f = MediaDiscovery.parseMediaReply("""{"Address":"http://192.168.1.20:8096/","Id":"abc","Name":"Salon","EndpointAddress":null}""", Kind.JELLYFIN, "192.168.1.20")
        assertEquals(Found(Kind.JELLYFIN, "Salon", "http://192.168.1.20:8096", "abc"), f)
    }

    @Test fun emby_typeDonneParLaSonde() {
        val f = MediaDiscovery.parseMediaReply("""{"Address":"http://192.168.1.31:8096","Id":"e1","Name":"Bureau"}""", Kind.EMBY, "192.168.1.31")!!
        assertEquals(Kind.EMBY, f.kind)
        assertEquals("Bureau", f.name)
    }

    @Test fun reponseSansAdresseValide_repliSurExpediteur_etNomParDefaut() {
        val f = MediaDiscovery.parseMediaReply("""{"Address":"pas une adresse","Id":""}""", Kind.EMBY, "10.0.0.5")!!
        assertEquals("http://10.0.0.5:8096", f.address)
        assertEquals("Emby", f.name)
        assertEquals("http://10.0.0.5:8096", f.id)
    }

    @Test fun reponseIllisible_ignoree() {
        assertNull(MediaDiscovery.parseMediaReply("n'importe quoi", Kind.JELLYFIN, "10.0.0.5"))
        assertNull(MediaDiscovery.parseMediaReply("null", Kind.JELLYFIN, "10.0.0.5"))
        assertNull(MediaDiscovery.parseMediaReply("""{"Id":"x"}""", Kind.EMBY, null))
    }

    @Test fun plexGdm_entetes() {
        val msg = "HTTP/1.0 200 OK\r\nContent-Type: plex/media-player\r\nResource-Identifier: m-123\r\nName: NAS-Plex\r\nPort: 32400\r\nVersion: 1.40\r\n\r\n"
        assertEquals(Found(Kind.PLEX, "NAS-Plex", "http://192.168.1.40:32400", "m-123"), MediaDiscovery.parseGdmReply(msg, "192.168.1.40"))
    }

    @Test fun plexGdm_portAbsentOuAbsurde_et_reponseNon200() {
        assertEquals("http://10.0.0.2:32400", MediaDiscovery.parseGdmReply("HTTP/1.0 200 OK\r\nPort: 99999\r\nName: X\r\n", "10.0.0.2")!!.address)
        assertNull(MediaDiscovery.parseGdmReply("HTTP/1.0 404 Not Found\r\n", "10.0.0.2"))
        assertNull(MediaDiscovery.parseGdmReply("HTTP/1.0 200 OK\r\n", null))
    }

    @Test fun fusion_dedoublonne_et_trie() {
        val a = Found(Kind.JELLYFIN, "Zeta", "http://a", "1")
        val b = Found(Kind.PLEX, "alpha", "http://b", "2")
        assertEquals(listOf(b, a), MediaDiscovery.merge(listOf(a, null, b, a.copy(address = "http://autre"))))
    }

    // ───────── Étape 3 : choix des bibliothèques ─────────

    private val libs = listOf(LibraryChoice("MOVIE", "1", "Films"), LibraryChoice("SERIES", "1", "Séries"), LibraryChoice("MOVIE", "2", "Docs"))

    @Test fun bibliotheques_toutesCocheesAuDepart_etRienADesactiver() {
        val checked = MediaLibraryLogic.allChecked(libs)
        assertEquals(3, checked.size)
        assertTrue(MediaLibraryLogic.toDisable(libs, checked).isEmpty())
    }

    @Test fun bibliotheques_decochees_sontDesactivees_memeIdentifiantFilmsEtSeries() {
        val checked = MediaLibraryLogic.toggle(MediaLibraryLogic.allChecked(libs), "SERIES:1")
        assertEquals(listOf(libs[1]), MediaLibraryLogic.toDisable(libs, checked))
        assertEquals(MediaLibraryLogic.allChecked(libs), MediaLibraryLogic.toggle(checked, "SERIES:1"))
    }

    @Test fun importer_exigeAuMoinsUneCase() {
        assertFalse(MediaLibraryLogic.canImport(emptySet()))
        assertTrue(MediaLibraryLogic.canImport(setOf("MOVIE:1")))
    }

    // ───────── Plex : nature de la connexion ─────────

    private fun conn(local: Boolean, relay: Boolean) = Plex.Connection("http://x", local, relay, false, false)

    @Test fun plex_natureDeConnexion() {
        assertEquals(ConnKind.RELAY, MediaLibraryLogic.connKind(conn(local = false, relay = true)))
        assertEquals(ConnKind.LOCAL, MediaLibraryLogic.connKind(conn(local = true, relay = false)))
        assertEquals(ConnKind.REMOTE, MediaLibraryLogic.connKind(conn(local = false, relay = false)))
        val s = Plex.Server("Salon", "m", "t", true, listOf(conn(false, true), conn(true, false)))
        assertEquals(ConnKind.RELAY, MediaLibraryLogic.bestConnKind(s))
        assertNull(MediaLibraryLogic.bestConnKind(s.copy(connections = emptyList())))
    }

    // ───────── État En ligne / Relais / Hors ligne ─────────

    @Test fun etat_surSondeEtDerniereSynchro() {
        assertEquals(Status.ONLINE, MediaLibraryLogic.status("JELLYFIN", "http://192.168.1.20:8096", true, 0))
        assertEquals(Status.OFFLINE, MediaLibraryLogic.status("JELLYFIN", "http://192.168.1.20:8096", false, 5))
        assertEquals(Status.ONLINE, MediaLibraryLogic.status("NAS", "smb://nas/videos", null, 5))
        assertEquals(Status.OFFLINE, MediaLibraryLogic.status("NAS", "smb://nas/videos", null, 0))
    }

    @Test fun etat_plexRelais() {
        assertEquals(Status.RELAY, MediaLibraryLogic.status("PLEX", "https://81-2-3-4.abcd.plex.direct:8443", true, 1))
        assertEquals(Status.ONLINE, MediaLibraryLogic.status("PLEX", "https://192-168-1-40.abcd.plex.direct:32400", true, 1))
        assertEquals(Status.ONLINE, MediaLibraryLogic.status("PLEX", "https://192-168-1-40.abcd.plex.direct:8443", true, 1))
        assertEquals(Status.OFFLINE, MediaLibraryLogic.status("PLEX", "https://81-2-3-4.abcd.plex.direct:8443", false, 1))
    }

    @Test fun cibleDeSonde() {
        assertEquals("192.168.1.20" to 8096, MediaLibraryLogic.probeTarget("http://192.168.1.20:8096"))
        assertEquals("nas.local" to 445, MediaLibraryLogic.probeTarget("smb://nas.local/videos"))
        assertEquals("srv" to 443, MediaLibraryLogic.probeTarget("https://srv/x"))
        assertEquals("srv" to 80, MediaLibraryLogic.probeTarget("http://user@srv"))
        assertNull(MediaLibraryLogic.probeTarget("pas une adresse"))
    }

    @Test fun progression_et_compteARebours() {
        assertEquals(0.5f, MediaLibraryLogic.progress(50, 100), 0.001f)
        assertEquals(0f, MediaLibraryLogic.progress(50, 0), 0f)
        assertEquals(1f, MediaLibraryLogic.progress(500, 100), 0f)
        assertEquals("14:05", MediaLibraryLogic.countdown(14 * 60_000L + 5_400))
        assertEquals("0:00", MediaLibraryLogic.countdown(-10))
    }

    @Test fun nas_nomDeFichier_et_titreReconnu() {
        val link = com.ultratv.tv.nativeapp.data.nas.Nas.NasLink.build(3, listOf("Films", "Interstellar.2014.1080p.mkv"))
        assertEquals("Interstellar.2014.1080p.mkv", MediaLibraryLogic.nasFileName(link))
        assertNull(MediaLibraryLogic.nasFileName("http://x"))
        assertEquals("Interstellar (2014)", MediaLibraryLogic.recognisedTitle("Interstellar", 2014))
        assertEquals("Wonka", MediaLibraryLogic.recognisedTitle("Wonka", null))
    }

    @Test fun logosParType() {
        assertEquals("J", MediaLibraryLogic.logo("JELLYFIN").first)
        assertEquals("P", MediaLibraryLogic.logo(Kind.PLEX).first)
        assertEquals("N", MediaLibraryLogic.logo("NAS").first)
    }
}
