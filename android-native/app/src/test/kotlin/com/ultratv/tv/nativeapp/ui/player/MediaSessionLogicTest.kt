package com.ultratv.tv.nativeapp.ui.player

import androidx.media3.common.Player
import com.ultratv.tv.nativeapp.ui.player.engine.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionLogicTest {
    @Test fun direct_avecProgramme_programmeEnTitre_chaineEnSousTitre() {
        val n = MediaSessionLogic.nowPlaying("LIVE", "TF1", "x", "Le Journal", "https://img/tf1.png")
        assertEquals(NowPlaying("Le Journal", "TF1", "https://img/tf1.png", live = true), n)
    }

    @Test fun direct_sansProgramme_chaineSeule() {
        assertEquals(NowPlaying("TF1", null, null, live = true), MediaSessionLogic.nowPlaying("LIVE", "TF1", "x", "  ", null))
        assertEquals(NowPlaying("TF1", null, null, live = true), MediaSessionLogic.nowPlaying("LIVE", "TF1", "x", null, null))
    }

    @Test fun episode_serieEnSousTitre() {
        val n = MediaSessionLogic.nowPlaying("EPISODE", "Dark · S01E02 · Mensonges", "x", null, "http://i/p.jpg")
        assertEquals("S01E02 · Mensonges", n.title); assertEquals("Dark", n.subtitle); assertFalse(n.live)
    }

    @Test fun episode_sansSeparateur_titreSeul() {
        val n = MediaSessionLogic.nowPlaying("EPISODE", "Dark", "x", null, null)
        assertEquals("Dark", n.title); assertNull(n.subtitle)
    }

    @Test fun film_titreSeul_etRepliSurLeTitreDeLaRoute() {
        assertEquals("Heat", MediaSessionLogic.nowPlaying("MOVIE", "Heat", "x", null, null).title)
        assertEquals("Titre de repli", MediaSessionLogic.nowPlaying(null, null, "Titre de repli", null, null).title)
        assertEquals("Titre de repli", MediaSessionLogic.nowPlaying("MOVIE", "  ", "Titre de repli", null, null).title)
    }

    @Test fun image_seulementAdressesUtilisables() {
        assertEquals("https://a/b.png", MediaSessionLogic.artwork(" https://a/b.png "))
        assertEquals("content://x/y", MediaSessionLogic.artwork("content://x/y"))
        assertNull(MediaSessionLogic.artwork("")); assertNull(MediaSessionLogic.artwork(null)); assertNull(MediaSessionLogic.artwork("logos/tf1.png"))
    }

    @Test fun etat_phaseVersMedia3() {
        assertEquals(Player.STATE_BUFFERING, MediaSessionLogic.playbackState(Phase.LOADING))
        assertEquals(Player.STATE_READY, MediaSessionLogic.playbackState(Phase.PLAYING))
        assertEquals(Player.STATE_ENDED, MediaSessionLogic.playbackState(Phase.ENDED))
        assertEquals(Player.STATE_IDLE, MediaSessionLogic.playbackState(Phase.ERROR))
    }

    @Test fun lectureVoulue() {
        assertTrue(MediaSessionLogic.playWhenReady(Phase.LOADING, false))
        assertTrue(MediaSessionLogic.playWhenReady(Phase.PLAYING, true))
        assertFalse(MediaSessionLogic.playWhenReady(Phase.PLAYING, false))   // pause
        assertTrue(MediaSessionLogic.playWhenReady(Phase.PLAYING, null))     // moteur pas encore là
        assertFalse(MediaSessionLogic.playWhenReady(Phase.ENDED, true)); assertFalse(MediaSessionLogic.playWhenReady(Phase.ERROR, true))
    }

    @Test fun avanceRetour_seulementALaDemandeAvecDureeConnue() {
        assertTrue(MediaSessionLogic.seekable(live = false, durationMs = 5_400_000))
        assertFalse(MediaSessionLogic.seekable(live = true, durationMs = 5_400_000))
        assertFalse(MediaSessionLogic.seekable(live = false, durationMs = -1))
        assertFalse(MediaSessionLogic.seekable(live = false, durationMs = 0))
    }
}
