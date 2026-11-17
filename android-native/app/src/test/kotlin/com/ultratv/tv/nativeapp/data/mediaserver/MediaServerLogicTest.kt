package com.ultratv.tv.nativeapp.data.mediaserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logique pure des serveurs Jellyfin / Emby, sur des réponses d'API simulées (forme réelle des deux serveurs). */
class MediaServerLogicTest {

    // ───── Adresse du serveur ─────

    @Test fun adresse_schemaAjouteEtPageWebRetiree() {
        assertEquals("http://192.168.1.10:8096", MediaServer.normalizeServerUrl("192.168.1.10:8096"))
        assertEquals("https://jf.exemple.org", MediaServer.normalizeServerUrl(" https://jf.exemple.org/ "))
        assertEquals("http://nas:8096", MediaServer.normalizeServerUrl("http://nas:8096/web/index.html#!/home"))
        assertEquals("http://nas:8096/jellyfin", MediaServer.normalizeServerUrl("nas:8096/jellyfin/web/"))
        assertEquals("http://nas:8096", MediaServer.normalizeServerUrl("http://nas:8096?x=1"))
    }

    @Test fun adresse_invalide() {
        assertNull(MediaServer.normalizeServerUrl(""))
        assertNull(MediaServer.normalizeServerUrl("   "))
        assertNull(MediaServer.normalizeServerUrl("ftp://nas"))
        assertNull(MediaServer.normalizeServerUrl("http://"))
        assertNull(MediaServer.normalizeServerUrl("mon serveur"))
    }

    // ───── Authentification ─────

    @Test fun enteteAuthorization_contientAppareilEtJeton_guillemetsRetires() {
        val h = MediaServer.authorization("dev\"1", token = "tok")
        assertEquals("MediaBrowser Client=\"Ultra TV\", Device=\"Android\", DeviceId=\"dev1\", Version=\"1\", Token=\"tok\"", h)
        assertFalse(MediaServer.authorization("d").contains("Token"))
    }

    @Test fun corpsAuth_echappeLesCaracteresSpeciaux() {
        assertEquals("""{"Username":"a\"b","Pw":"p\\w"}""", MediaServer.authBody("a\"b", "p\\w"))
    }

    @Test fun parseAuth_reponseJellyfin() {
        val a = MediaServer.parseAuth("""{"User":{"Name":"lilou","ServerId":"srv1","Id":"u123","HasPassword":true},"SessionInfo":{},"AccessToken":"abc123","ServerId":"srv1"}""")
        assertEquals(MediaServer.Auth("abc123", "u123", "lilou", "srv1"), a)
    }

    @Test fun parseAuth_incomplete() {
        assertNull(MediaServer.parseAuth("""{"User":{"Id":"u"}}"""))
        assertNull(MediaServer.parseAuth("""{"AccessToken":"t"}"""))
        assertNull(MediaServer.parseAuth("<html>"))
        assertNull(MediaServer.parseAuth(""))
    }

    @Test fun pickUser_nomPuisAdministrateurPuisPremier() {
        val users = """[{"Name":"enfant","Id":"1","Policy":{"IsAdministrator":false}},{"Name":"Papa","Id":"2","Policy":{"IsAdministrator":true}},{"Name":"maman","Id":"3"}]"""
        assertEquals("3", MediaServer.pickUser(users, "MAMAN")?.id)
        assertEquals("2", MediaServer.pickUser(users, "")?.id)
        assertEquals("2", MediaServer.pickUser(users, "inconnu")?.id)
        assertEquals("1", MediaServer.pickUser("""[{"Name":"seul","Id":"1"}]""", "")?.id)
        assertNull(MediaServer.pickUser("[]", ""))
        assertNull(MediaServer.pickUser("{}", ""))
    }

    // ───── Bibliothèques ─────

    @Test fun parseViews_gardeFilmsEtSeriesSeulement() {
        val views = MediaServer.parseViews(
            """{"Items":[
              {"Name":"Films","Id":"m1","CollectionType":"movies"},
              {"Name":"Séries","Id":"s1","CollectionType":"tvshows"},
              {"Name":"Musique","Id":"x1","CollectionType":"music"},
              {"Name":"Mélange","Id":"x2"},
              {"Name":"Sans id","CollectionType":"movies"}
            ],"TotalRecordCount":5}""",
        )
        assertEquals(listOf(MediaServer.Library("m1", "Films", "movies"), MediaServer.Library("s1", "Séries", "tvshows")), views)
    }

    // ───── Éléments ─────

    private val moviePage = """{"Items":[
      {"Name":"Le Voyage","Id":"abc","Type":"Movie","ProductionYear":2019,"CommunityRating":7.4,"Overview":"Un long voyage.",
       "Genres":["Drame","Aventure"],"People":[{"Name":"Ada","Type":"Actor"},{"Name":"Bob","Type":"Director"},{"Name":"Cy","Type":"Actor"}],
       "RunTimeTicks":54000000000,"DateCreated":"2024-03-01T10:20:30.1234567Z",
       "MediaSources":[{"Id":"src1","Container":"mkv,webm","RunTimeTicks":54000000000}],
       "ImageTags":{"Primary":"tagP"},"BackdropImageTags":["tagB"],
       "UserData":{"PlaybackPositionTicks":6000000000,"Played":false}},
      {"Name":"Minimal","Id":"min"},
      {"Id":"sansnom"}
    ],"TotalRecordCount":3,"StartIndex":0}"""

    @Test fun parseItems_champsEtTotal() {
        val page = MediaServer.parseItems(moviePage)
        assertEquals(3, page.total)
        assertEquals(2, page.items.size)   // l'élément sans nom est ignoré
        val m = page.items[0]
        assertEquals("abc", m.id); assertEquals(2019, m.year); assertEquals(7.4, m.rating!!, 0.001)
        assertEquals(listOf("Drame", "Aventure"), m.genres)
        assertEquals(listOf("Ada", "Cy"), m.cast)   // acteurs seulement
        assertEquals("mkv", m.container); assertEquals("src1", m.mediaSourceId)
        assertEquals("tagP", m.primaryTag); assertEquals("tagB", m.backdropTag)
        assertEquals(6_000_000_000L, m.positionTicks)
        val min = page.items[1]
        assertNull(min.year); assertNull(min.container); assertEquals(0L, min.positionTicks); assertTrue(min.genres.isEmpty())
    }

    @Test fun parseItems_illisible() {
        assertEquals(MediaServer.Page(0, emptyList()), MediaServer.parseItems("pas du json"))
        assertEquals(0, MediaServer.parseItems("""{"Items":[]}""").total)
    }

    @Test fun toMovie_lienInterneSansJeton_afficheDuServeur() {
        val base = "http://nas:8096"
        val m = MediaServer.toMovie(7L, base, MediaServer.parseItems(moviePage).items[0], "lib1")
        assertEquals("mediaserver://7/abc", m.streamUrl)
        assertEquals("abc", m.remoteId); assertEquals("lib1", m.categoryId); assertEquals(7L, m.providerId)
        assertEquals("http://nas:8096/Items/abc/Images/Primary?maxWidth=480&quality=90&tag=tagP", m.poster)
        assertEquals("http://nas:8096/Items/abc/Images/Backdrop?maxWidth=1280&quality=90&tag=tagB", m.backdrop)
        assertEquals("Drame, Aventure", m.genre); assertEquals("Ada, Cy", m.cast)
        assertEquals("01:30:00", m.duration)
        assertEquals("mkv", m.container)
        assertEquals(1709288430L, m.addedKey)
        assertFalse(m.streamUrl.contains("api_key")); assertFalse((m.poster ?: "").contains("api_key"))
    }

    @Test fun toMovie_sansImageNiGenre() {
        val m = MediaServer.toMovie(1, "http://h", MediaServer.parseItems(moviePage).items[1], null)
        assertNull(m.poster); assertNull(m.backdrop); assertNull(m.genre); assertNull(m.duration); assertEquals(0L, m.addedKey)
    }

    @Test fun toSeries_etEpisodes() {
        val series = MediaServer.parseItems("""{"Items":[{"Name":"La Série","Id":"ser1","Type":"Series","ProductionYear":2020,"ImageTags":{"Primary":"t"}}],"TotalRecordCount":1}""").items[0]
        val s = MediaServer.toSeries(3, "http://h", series, "lib")
        assertEquals("ser1", s.remoteId); assertEquals("lib", s.categoryId); assertEquals("http://h/Items/ser1/Images/Primary?maxWidth=480&quality=90&tag=t", s.poster)
        val eps = MediaServer.parseItems(
            """{"Items":[
              {"Name":"Pilote","Id":"e1","Type":"Episode","ParentIndexNumber":1,"IndexNumber":1,"RunTimeTicks":27000000000,"MediaSources":[{"Id":"e1","Container":"mp4"}]},
              {"Name":"Sans index","Id":"e2","Type":"Episode"}
            ],"TotalRecordCount":2}""",
        ).items
        val e1 = MediaServer.toEpisode(3, "http://h", 99, eps[0], 1)
        assertEquals(99L, e1.seriesId); assertEquals(1, e1.season); assertEquals(1, e1.episode); assertEquals("mediaserver://3/e1", e1.streamUrl)
        assertEquals("00:45:00", e1.duration); assertEquals("mp4", e1.container)
        val e2 = MediaServer.toEpisode(3, "http://h", 99, eps[1], 2)
        assertEquals(1, e2.season); assertEquals(2, e2.episode)   // saison 1 et position dans la liste par défaut
    }

    // ───── Adresses de lecture ─────

    @Test fun playUrl_lectureDirecte() {
        assertEquals("http://h/Videos/abc/stream.mkv?static=true&MediaSourceId=src1", MediaServer.playUrl("http://h", "abc", "src1", "MKV"))
        assertEquals("http://h/Videos/abc/stream?static=true", MediaServer.playUrl("http://h", "abc"))
        // Conteneur suspect : pas d'extension.
        assertEquals("http://h/Videos/abc/stream?static=true", MediaServer.playUrl("http://h", "abc", null, "../x"))
        assertFalse(MediaServer.playUrl("http://h", "abc", "s", "mkv").contains("api_key"))
    }

    @Test fun playHeaders_jetonDansUnEntete() {
        assertEquals(mapOf("X-Emby-Token" to "tok"), MediaServer.playHeaders("tok"))
    }

    @Test fun requetes() {
        assertEquals("http://h/Users/u/Items?ParentId=lib&IncludeItemTypes=Movie&Recursive=true&Fields=${MediaServer.LIST_FIELDS}&SortBy=SortName&SortOrder=Ascending&StartIndex=300&Limit=300", MediaServer.itemsUrl("http://h", "u", "lib", "Movie", 300))
        assertEquals("http://h/Shows/ser/Episodes?UserId=u&Fields=Overview,MediaSources,RunTimeTicks,ImageTags", MediaServer.episodesUrl("http://h", "ser", "u"))
        assertEquals("http://h/Users/u/Items/i", MediaServer.itemUrl("http://h", "u", "i"))
    }

    // ───── Lien interne ─────

    @Test fun lien_allerRetour() {
        assertEquals("mediaserver://12/abc-DEF_1", MediaLink.build(12, "abc-DEF_1"))
        assertEquals(MediaLink.Parsed(12, "abc-DEF_1"), MediaLink.parse("mediaserver://12/abc-DEF_1"))
        assertTrue(MediaLink.isMedia("mediaserver://1/x")); assertFalse(MediaLink.isMedia("stalker://1/vod?cmd=x")); assertFalse(MediaLink.isMedia(null))
    }

    @Test fun lien_invalide() {
        assertNull(MediaLink.parse("http://h/x"))
        assertNull(MediaLink.parse("mediaserver://abc/x"))
        assertNull(MediaLink.parse("mediaserver://1/"))
        assertNull(MediaLink.parse("mediaserver://1/../etc"))
        assertNull(MediaLink.parse("mediaserver:///x"))
    }

    // ───── Reprise ─────

    @Test fun ticks_conversion() {
        assertEquals(600_000L, MediaServer.ticksToMs(6_000_000_000L))
        assertEquals(6_000_000_000L, MediaServer.msToTicks(600_000L))
        assertEquals(0L, MediaServer.msToTicks(-5))
        assertEquals(0L, MediaServer.ticksToMs(-1))
    }

    @Test fun reprise_serveurPrimeSurLocal() {
        val withPos = MediaServer.parseItem("""{"Name":"x","Id":"i","UserData":{"PlaybackPositionTicks":6000000000,"Played":false}}""")
        assertEquals(600_000L, MediaServer.resumeMs(withPos, 42_000L))
    }

    @Test fun reprise_vuEnEntier_repartDuDebut_sinonLocal() {
        val played = MediaServer.parseItem("""{"Name":"x","Id":"i","UserData":{"PlaybackPositionTicks":0,"Played":true}}""")
        assertEquals(0L, MediaServer.resumeMs(played, 42_000L))
        val none = MediaServer.parseItem("""{"Name":"x","Id":"i","UserData":{"PlaybackPositionTicks":0,"Played":false}}""")
        assertEquals(42_000L, MediaServer.resumeMs(none, 42_000L))
        assertEquals(42_000L, MediaServer.resumeMs(null, 42_000L))   // serveur injoignable
    }

    @Test fun rapport_corps() {
        assertEquals(
            """{"ItemId":"abc","MediaSourceId":"src1","PositionTicks":6000000000,"IsPaused":true,"PlayMethod":"DirectPlay","CanSeek":true}""",
            MediaServer.reportBody("abc", "src1", 600_000L, true),
        )
        assertFalse(MediaServer.reportBody("abc", null, 0, false).contains("MediaSourceId"))
        assertEquals("/Sessions/Playing/Progress", MediaServer.Report.PROGRESS.path)
        assertEquals("/Sessions/Playing/Stopped", MediaServer.Report.STOP.path)
        assertEquals("/Sessions/Playing", MediaServer.Report.START.path)
    }

    @Test fun durees_et_dates() {
        assertNull(MediaServer.durationText(null)); assertNull(MediaServer.durationText(0))
        assertEquals("00:00:30", MediaServer.durationText(300_000_000L))
        assertEquals(0L, MediaServer.addedKey("pas une date"))
        assertEquals(0L, MediaServer.addedKey(null))
        assertNotNull(MediaServer.addedKey("2024-03-01T10:20:30Z").takeIf { it > 0 })
    }

    @Test fun genresDeServeur() {
        assertTrue(MediaServer.isMediaKind("JELLYFIN")); assertTrue(MediaServer.isMediaKind("EMBY"))
        assertFalse(MediaServer.isMediaKind("XTREAM")); assertFalse(MediaServer.isMediaKind(null))
    }
}
