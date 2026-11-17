package com.ultratv.tv.nativeapp.data.mediaserver

import kotlinx.coroutines.runBlocking
import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plex : logique pure sur des réponses simulées (PIN, serveurs, bibliothèques, éléments, adresses, reprise, rapports). */
class PlexLogicTest {
    private val resources = """[
      {"name":"Salon","provides":"server","clientIdentifier":"mach1","accessToken":"srv-tok","owned":true,"connections":[
        {"protocol":"https","address":"1.2.3.4","port":32400,"uri":"https://1-2-3-4.abc.plex.direct:32400","local":false,"relay":false,"IPv6":false},
        {"protocol":"https","address":"relay","port":8443,"uri":"https://relay.plex.direct:8443","local":false,"relay":true,"IPv6":false},
        {"protocol":"http","address":"192.168.1.5","port":32400,"uri":"http://192.168.1.5:32400","local":true,"relay":false,"IPv6":false},
        {"protocol":"https","address":"192.168.1.5","port":32400,"uri":"https://192-168-1-5.abc.plex.direct:32400","local":true,"relay":false,"IPv6":false}]},
      {"name":"Téléphone","provides":"client,player","clientIdentifier":"phone","accessToken":"x","connections":[]},
      {"name":"Ami","provides":"server","clientIdentifier":"mach2","accessToken":"ami-tok","owned":false,"connections":[{"protocol":"http","address":"9.9.9.9","port":32400,"uri":"http://9.9.9.9:32400/","local":false,"relay":false}]},
      {"name":"SansJeton","provides":"server","clientIdentifier":"mach3","connections":[{"uri":"http://x:1"}]}
    ]"""

    @Test fun pin_analyse() {
        val p = Plex.parsePin("""{"id":12345,"code":"ABCD","authToken":null,"expiresIn":1800}""")!!
        assertEquals(12345L, p.id); assertEquals("ABCD", p.code); assertNull(p.authToken); assertEquals(1800L, p.expiresInSec)
        assertEquals("tok", Plex.parsePin("""{"id":1,"code":"Z","authToken":"tok"}""")!!.authToken)
        assertNull(Plex.parsePin("pas du json")); assertNull(Plex.parsePin("""{"id":1}"""))
    }

    @Test fun enTetes_identifiantStable_etJetonSeulementSiConnu() {
        val h = Plex.headers("inst-1", null)
        assertEquals("inst-1", h["X-Plex-Client-Identifier"]); assertEquals("Ultra TV", h["X-Plex-Product"])
        assertEquals("application/json", h["Accept"]); assertTrue(h.containsKey("X-Plex-Version")); assertFalse(h.containsKey("X-Plex-Token"))
        assertEquals("t", Plex.headers("inst-1", "t")["X-Plex-Token"])
        assertEquals(mapOf("X-Plex-Token" to "t", "X-Plex-Client-Identifier" to "i"), Plex.playHeaders("t", "i"))
    }

    @Test fun serveurs_seulementLesServeursAvecJeton_proprietaireDabord_connexionsClassees() {
        val s = Plex.parseServers(resources)
        assertEquals(listOf("Salon", "Ami"), s.map { it.name })
        val salon = s[0]
        assertEquals("mach1", salon.machineId); assertEquals("srv-tok", salon.accessToken)
        // locale (https avant http), puis distante directe, puis relais
        assertEquals(
            listOf("https://192-168-1-5.abc.plex.direct:32400", "http://192.168.1.5:32400", "https://1-2-3-4.abc.plex.direct:32400", "https://relay.plex.direct:8443"),
            salon.connections.map { it.uri },
        )
        assertEquals("http://9.9.9.9:32400", s[1].connections.single().uri)   // « / » final retiré
        assertEquals(emptyList<Plex.Server>(), Plex.parseServers("pas du json"))
    }

    @Test fun serveur_toString_neMontrePasLeJeton() {
        assertFalse(Plex.parseServers(resources)[0].toString().contains("srv-tok"))
    }

    @Test fun bibliotheques_filmsEtSeriesSeulement() {
        val l = Plex.parseSections("""{"MediaContainer":{"Directory":[{"key":"1","title":"Films","type":"movie"},{"key":"2","title":"Séries","type":"show"},{"key":"3","title":"Musique","type":"artist"}]}}""")
        assertEquals(listOf(MediaServer.Library("1", "Films", "movies"), MediaServer.Library("2", "Séries", "tvshows")), l)
    }

    private val movies = """{"MediaContainer":{"size":1,"totalSize":650,"Metadata":[
      {"ratingKey":"101","type":"movie","title":"Le Film","year":1999,"audienceRating":8.2,"summary":"Résumé","thumb":"/library/metadata/101/thumb/17","art":"/library/metadata/101/art/17",
       "duration":5400000,"addedAt":1700000000,"viewOffset":120000,"Genre":[{"tag":"Drame"},{"tag":"Action"}],"Role":[{"tag":"A"},{"tag":"B"}],
       "Media":[{"container":"mkv","Part":[{"id":9,"key":"/library/parts/9/1700/file.mkv","container":"mkv"}]}]}]}}"""

    @Test fun elements_analyseEtPagination() {
        val page = Plex.parseItems(movies)
        assertEquals(650, page.total)
        val m = page.items.single()
        assertEquals("101", m.ratingKey); assertEquals(5_400_000L, m.durationMs); assertEquals("/library/parts/9/1700/file.mkv", m.partKey)
        assertEquals("mkv", m.container); assertEquals(120_000L, m.viewOffsetMs); assertEquals(listOf("Drame", "Action"), m.genres)
        assertEquals(0, Plex.parseItems("n'importe quoi").items.size)
        assertNull(Plex.parseItem("""{"MediaContainer":{"Metadata":[]}}"""))
    }

    @Test fun conversion_catalogue_sansJetonDansLesAdresses() {
        val base = "http://192.168.1.5:32400"
        val it = Plex.parseItems(movies).items.single()
        val mv = Plex.toMovie(7, base, it, "1")
        assertEquals("mediaserver://7/101", mv.streamUrl); assertEquals("01:30:00", mv.duration); assertEquals("Drame, Action", mv.genre)
        assertEquals(1700000000L, mv.addedKey); assertEquals(8.2, mv.rating)
        assertTrue(mv.poster!!.startsWith("$base/photo/:/transcode?"))
        assertTrue(mv.poster!!.contains("url=%2Flibrary%2Fmetadata%2F101%2Fthumb%2F17"))
        for (u in listOf(mv.poster!!, mv.backdrop!!, mv.streamUrl!!)) assertFalse(u, u.contains("Token", ignoreCase = true))
        assertEquals("mediaserver://7/101", MediaLink.build(7, "101"))
        assertEquals(MediaLink.Parsed(7, "101"), MediaLink.parse(mv.streamUrl!!))
    }

    @Test fun episodes_saisonsEtNumeros() {
        val txt = """{"MediaContainer":{"Metadata":[
          {"ratingKey":"201","type":"episode","title":"Pilote","parentIndex":1,"index":1,"thumb":"/library/metadata/201/thumb/1"},
          {"ratingKey":"202","type":"episode","title":"Sans index"}]}}"""
        val eps = Plex.parseItems(txt).items.mapIndexed { i, it -> Plex.toEpisode(3, "http://s", 55, it, i + 1) }
        assertEquals(listOf(1 to 1, 1 to 2), eps.map { it.season to it.episode })
        assertEquals("mediaserver://3/201", eps[0].streamUrl); assertEquals(55L, eps[0].seriesId)
    }

    @Test fun lecture_directe_urlEtEnTetes() {
        assertEquals("http://s:32400/library/parts/9/1700/file.mkv", Plex.playUrl("http://s:32400", "/library/parts/9/1700/file.mkv"))
        assertNull(Plex.playUrl("http://s", null)); assertNull(Plex.playUrl("http://s", "pas-un-chemin"))
    }

    @Test fun adressesDeRequete() {
        assertEquals("http://s/library/sections/2/all?type=2&X-Plex-Container-Start=300&X-Plex-Container-Size=300", Plex.itemsUrl("http://s", "2", 2, 300))
        assertEquals("http://s/library/metadata/5/allLeaves", Plex.episodesUrl("http://s", "5"))
        assertEquals("https://plex.tv/api/v2/pins/42", Plex.pinCheckUrl(42))
        assertFalse(Plex.itemsUrl("http://s", "1", 1, 0).contains("Token", ignoreCase = true))
    }

    @Test fun reprise_serveurPrime_vuEnEntierRepartDuDebut() {
        fun item(off: Long, views: Int) = Plex.parseItem("""{"MediaContainer":{"Metadata":[{"ratingKey":"1","title":"t","viewOffset":$off,"viewCount":$views}]}}""")
        assertEquals(60_000L, Plex.resumeMs(item(60_000, 0), 5_000))
        assertEquals(0L, Plex.resumeMs(item(0, 1), 5_000))     // vu en entier : du début, pas la position locale périmée
        assertEquals(5_000L, Plex.resumeMs(item(0, 0), 5_000))
        assertEquals(5_000L, Plex.resumeMs(null, 5_000))
    }

    @Test fun rapports_timeline() {
        assertEquals("playing", Plex.timelineState(MediaServer.Report.START, false))
        assertEquals("playing", Plex.timelineState(MediaServer.Report.PROGRESS, false))
        assertEquals("paused", Plex.timelineState(MediaServer.Report.PROGRESS, true))
        assertEquals("stopped", Plex.timelineState(MediaServer.Report.STOP, true))
        val u = Plex.timelineUrl("http://s", "101", "stopped", 4000, 5400000)
        assertTrue(u.startsWith("http://s/:/timeline?ratingKey=101&key=%2Flibrary%2Fmetadata%2F101"))
        assertTrue(u.contains("state=stopped&time=4000&duration=5400000"))
        assertFalse(u.contains("Token", ignoreCase = true))
    }

    @Test fun plexEstUnTypeDeServeurMultimedia() {
        assertTrue(MediaServer.isMediaKind("PLEX")); assertTrue(MediaServer.isMediaKind("JELLYFIN")); assertFalse(MediaServer.isMediaKind("XTREAM"))
    }

    // ───────────── Attente du code PIN ─────────────

    private fun pin(expires: Long? = null) = Plex.Pin(7, "WXYZ", null, expires)

    @Test fun pin_jetonRecuApresQuelquesSondes() = runBlocking {
        var polls = 0; var shown: String? = null; val waits = ArrayList<Long>()
        val r = awaitPlexToken({ pin() }, { id -> assertEquals(7L, id); if (++polls < 3) null else "acct-tok" }, { shown = it }, { waits += it })
        assertEquals(PlexLinkResult.Token("acct-tok"), r); assertEquals("WXYZ", shown); assertEquals(3, waits.size); assertEquals(Plex.PIN_POLL_MS, waits[0])
    }

    @Test fun pin_expireApres15min_sansJeton() = runBlocking {
        var polls = 0
        val r = awaitPlexToken({ pin() }, { polls++; null }, {}, { })
        assertEquals(PlexLinkResult.Expired, r)
        assertEquals((Plex.PIN_TTL_MS / Plex.PIN_POLL_MS).toInt(), polls)
    }

    @Test fun pin_expireSelonLeServeur() = runBlocking {
        var polls = 0
        assertEquals(PlexLinkResult.Expired, awaitPlexToken({ pin(expires = 10) }, { polls++; null }, {}, { }))
        assertEquals(4, polls)   // 10 s / 2,5 s
    }

    @Test fun pin_404_expiré_erreurReseauIgnoree() = runBlocking {
        var n = 0
        assertEquals(PlexLinkResult.Expired, awaitPlexToken({ pin() }, { if (++n == 1) throw java.io.IOException("réseau") else throw HttpStatusException(404) }, {}, { }))
        assertEquals(2, n)
        assertNotNull(PlexLinkResult.Token("x"))
    }
}
