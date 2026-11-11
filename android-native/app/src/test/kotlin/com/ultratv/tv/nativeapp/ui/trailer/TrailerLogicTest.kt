package com.ultratv.tv.nativeapp.ui.trailer

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logique pure du lecteur de bande-annonce : erreurs, état, sauts bornés, masquage, touches, page HTML. Pas de WebView. */
class TrailerLogicTest {

    // --- erreurs de l'API ---

    @Test fun codesDErreur_regroupes() {
        assertEquals(TrailerErrorKind.NotFound, trailerErrorKind(100))
        assertEquals(TrailerErrorKind.NotFound, trailerErrorKind(105))
        assertEquals(TrailerErrorKind.EmbedBlocked, trailerErrorKind(101))
        assertEquals(TrailerErrorKind.EmbedBlocked, trailerErrorKind(150))
        assertEquals(TrailerErrorKind.Config, trailerErrorKind(152))
        assertEquals(TrailerErrorKind.Config, trailerErrorKind(153))
        assertEquals(TrailerErrorKind.Unknown, trailerErrorKind(2))
        assertEquals(TrailerErrorKind.Unknown, trailerErrorKind(5))
        assertEquals(TrailerErrorKind.Unknown, trailerErrorKind(-1))
    }

    // --- état ---

    @Test fun etat_suitLeLecteur() {
        var s = TrailerState()
        assertEquals(TrailerPhase.Loading, s.phase)
        s = s.reduce(TrailerEvent.Ready)
        assertTrue(s.ready)
        s = s.reduce(TrailerEvent.PlayerState(1)).reduce(TrailerEvent.Time(12.0, 90.0))
        assertEquals(TrailerState(TrailerPhase.Playing, 12.0, 90.0, null, true), s)
        assertTrue(s.playing)
        assertEquals(TrailerPhase.Paused, s.reduce(TrailerEvent.PlayerState(2)).phase)
        assertTrue(s.reduce(TrailerEvent.PlayerState(3)).playing)           // mise en mémoire tampon : on « joue » encore
        assertEquals(TrailerPhase.Ended, s.reduce(TrailerEvent.PlayerState(0)).phase)
        assertEquals(TrailerPhase.Playing, s.reduce(TrailerEvent.PlayerState(-1)).phase)   // non démarrée / prête : ignorée
        assertEquals(TrailerPhase.Playing, s.reduce(TrailerEvent.PlayerState(5)).phase)
    }

    @Test fun etat_dureeNulleOuPositionInvalideIgnorees() {
        val s = TrailerState(positionS = 5.0, durationS = 90.0)
        assertEquals(90.0, s.reduce(TrailerEvent.Time(6.0, 0.0)).durationS, 0.0)
        assertEquals(5.0, s.reduce(TrailerEvent.Time(Double.NaN, 90.0)).positionS, 0.0)
        assertEquals(5.0, s.reduce(TrailerEvent.Time(-3.0, 90.0)).positionS, 0.0)
        assertEquals(6.0, s.reduce(TrailerEvent.Time(6.0, 90.0)).positionS, 0.0)
    }

    @Test fun etat_erreurDefinitive() {
        val e = TrailerState().reduce(TrailerEvent.Error(101))
        assertEquals(TrailerPhase.Error, e.phase)
        assertEquals(TrailerErrorKind.EmbedBlocked, e.error)
        assertEquals(e, e.reduce(TrailerEvent.PlayerState(1)))
        assertEquals(e, e.reduce(TrailerEvent.Time(3.0, 4.0)))
        assertEquals(e, e.reduce(TrailerEvent.Failed(TrailerErrorKind.Unknown)))
    }

    @Test fun etat_echecAppli() {
        val e = TrailerState().reduce(TrailerEvent.Failed(TrailerErrorKind.NoWebView))
        assertEquals(TrailerPhase.Error, e.phase)
        assertEquals(TrailerErrorKind.NoWebView, e.error)
    }

    // --- sauts et progression ---

    @Test fun clampSeek_bornes() {
        assertEquals(40.0, clampSeek(30.0, TRAILER_SKIP_S.toDouble(), 120.0), 0.0)
        assertEquals(0.0, clampSeek(5.0, -TRAILER_SKIP_S.toDouble(), 120.0), 0.0)
        assertEquals(120.0, clampSeek(115.0, TRAILER_SKIP_S.toDouble(), 120.0), 0.0)
        assertEquals(40.0, clampSeek(30.0, 10.0, 0.0), 0.0)               // durée inconnue : pas de borne haute
        assertEquals(10.0, clampSeek(Double.NaN, 10.0, 100.0), 0.0)
    }

    @Test fun seekEtProgression() {
        assertEquals(40.0, seekFromRatio(0.5f, 80.0), 0.0)
        assertEquals(80.0, seekFromRatio(2f, 80.0), 0.0)
        assertEquals(0.0, seekFromRatio(-1f, 80.0), 0.0)
        assertEquals(0.0, seekFromRatio(0.5f, 0.0), 0.0)
        assertEquals(0f, seekFromRatio(Float.NaN, 80.0).toFloat(), 0f)
        assertEquals(0.25f, progressRatio(20.0, 80.0), 0f)
        assertEquals(1f, progressRatio(200.0, 80.0), 0f)
        assertEquals(0f, progressRatio(5.0, 0.0), 0f)
    }

    @Test fun horloge() {
        assertEquals("0:00", trailerClock(0.0))
        assertEquals("0:09", trailerClock(9.9))
        assertEquals("2:05", trailerClock(125.0))
        assertEquals("1:01:01", trailerClock(3661.0))
        assertEquals("0:00", trailerClock(Double.NaN))
        assertEquals("0:00", trailerClock(-4.0))
    }

    // --- masquage des commandes (3 s) ---

    @Test fun masquage_apres3Secondes() {
        assertFalse(osdShouldHide(lastActivityMs = 1000, nowMs = 3999, keepVisible = false))
        assertTrue(osdShouldHide(lastActivityMs = 1000, nowMs = 4000, keepVisible = false))
        assertEquals(3000L, TRAILER_OSD_MS)
    }

    @Test fun masquage_jamaisEnPauseChargementFinOuErreur() {
        assertFalse(osdShouldHide(0, 100_000, keepVisible = true))
        assertTrue(osdKeepVisible(TrailerState(TrailerPhase.Paused)))
        assertTrue(osdKeepVisible(TrailerState(TrailerPhase.Loading)))
        assertTrue(osdKeepVisible(TrailerState(TrailerPhase.Ended)))
        assertTrue(osdKeepVisible(TrailerState(TrailerPhase.Error)))
        assertFalse(osdKeepVisible(TrailerState(TrailerPhase.Playing)))
    }

    // --- touches ---

    @Test fun touches_gaucheDroiteSautentToujours() {
        for (visible in listOf(true, false)) {
            assertEquals(TrailerAction.SkipBack, trailerKeyAction(KeyEvent.KEYCODE_DPAD_LEFT, visible))
            assertEquals(TrailerAction.SkipForward, trailerKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT, visible))
        }
    }

    @Test fun touches_media() {
        assertEquals(TrailerAction.Toggle, trailerKeyAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, true))
        assertEquals(TrailerAction.Toggle, trailerKeyAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, false))
        assertEquals(TrailerAction.Play, trailerKeyAction(KeyEvent.KEYCODE_MEDIA_PLAY, true))
        assertEquals(TrailerAction.Pause, trailerKeyAction(KeyEvent.KEYCODE_MEDIA_PAUSE, true))
        assertEquals(TrailerAction.SkipBack, trailerKeyAction(KeyEvent.KEYCODE_MEDIA_REWIND, true))
        assertEquals(TrailerAction.SkipForward, trailerKeyAction(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, true))
        assertEquals(TrailerAction.Close, trailerKeyAction(KeyEvent.KEYCODE_MEDIA_STOP, true))
        assertEquals(TrailerAction.Toggle, trailerKeyAction(KeyEvent.KEYCODE_SPACE, true))
        assertEquals(TrailerAction.Close, trailerKeyAction(KeyEvent.KEYCODE_ESCAPE, true))
    }

    @Test fun touches_okEtHautBas_selonLAffichageDesCommandes() {
        // Commandes affichées : le bouton focalisé réagit lui-même (la touche n'est pas prise).
        assertNull(trailerKeyAction(KeyEvent.KEYCODE_DPAD_CENTER, true))
        assertNull(trailerKeyAction(KeyEvent.KEYCODE_ENTER, true))
        assertNull(trailerKeyAction(KeyEvent.KEYCODE_DPAD_UP, true))
        assertNull(trailerKeyAction(KeyEvent.KEYCODE_DPAD_DOWN, true))
        // Commandes masquées : OK bascule la lecture, Haut / Bas les réaffichent seulement.
        assertEquals(TrailerAction.Toggle, trailerKeyAction(KeyEvent.KEYCODE_DPAD_CENTER, false))
        assertEquals(TrailerAction.Toggle, trailerKeyAction(KeyEvent.KEYCODE_ENTER, false))
        assertEquals(TrailerAction.Reveal, trailerKeyAction(KeyEvent.KEYCODE_DPAD_UP, false))
        assertEquals(TrailerAction.Reveal, trailerKeyAction(KeyEvent.KEYCODE_DPAD_DOWN, false))
        assertNull(trailerKeyAction(KeyEvent.KEYCODE_A, true))
    }

    // --- ordres envoyés à la page ---

    @Test fun commandesJs() {
        assertEquals("ut.play()", TrailerJs.PLAY)
        assertEquals("ut.pause()", TrailerJs.PAUSE)
        assertEquals("ut.seek(12.50)", TrailerJs.seek(12.5))
        assertEquals("ut.seek(0.00)", TrailerJs.seek(-3.0))
        assertEquals("ut.seek(0.00)", TrailerJs.seek(Double.NaN))
    }

    @Test fun lecturePause_selonLEtat() {
        assertEquals(listOf(TrailerJs.PAUSE), toggleCommands(TrailerState(TrailerPhase.Playing)))
        assertEquals(listOf(TrailerJs.PAUSE), toggleCommands(TrailerState(TrailerPhase.Buffering)))
        assertEquals(listOf(TrailerJs.PLAY), toggleCommands(TrailerState(TrailerPhase.Paused)))
        assertEquals(listOf(TrailerJs.PLAY), toggleCommands(TrailerState(TrailerPhase.Loading)))
        assertEquals(listOf("ut.seek(0.00)", TrailerJs.PLAY), toggleCommands(TrailerState(TrailerPhase.Ended)))
    }

    // --- page HTML ---

    @Test fun page_parametresDuLecteur() {
        val html = trailerPageHtml("dQw4w9WgXcQ")
        assertTrue(html.contains("videoId:'dQw4w9WgXcQ'"))
        assertTrue(html.contains("controls:0"))
        assertTrue(html.contains("rel:0"))
        assertTrue(html.contains("playsinline:1"))
        assertTrue(html.contains("modestbranding:1"))
        assertTrue(html.contains("autoplay:1"))
        assertTrue(html.contains("host:'https://www.youtube-nocookie.com'"))
        assertTrue(html.contains("src=\"https://www.youtube.com/iframe_api\""))
    }

    @Test fun page_origineEtPont() {
        val html = trailerPageHtml("dQw4w9WgXcQ")
        assertTrue(html.contains("origin:'$TRAILER_ORIGIN'"))
        assertTrue(TRAILER_ORIGIN.startsWith("https://"))
        assertTrue(TRAILER_BASE_URL.startsWith("$TRAILER_ORIGIN/"))
        assertTrue(html.contains("window.$TRAILER_BRIDGE"))
        assertTrue(html.contains("window.ut="))
        for (m in listOf("onReady", "onState", "onTime", "onError")) assertTrue(m, html.contains("B.$m("))
        assertTrue(trailerPageHtml("dQw4w9WgXcQ", "https://ultratv.local").contains("origin:'https://ultratv.local'"))
    }

    @Test fun page_refuseUneCleOuUneOrigineSuspecte() {
        for (bad in listOf("", "court", "abcdefghijkl", "dQw4w9WgXc'", "x');alert(1)//", "dQw4w9WgXc<")) {
            val r = runCatching { trailerPageHtml(bad) }
            assertTrue("clé acceptée : $bad", r.exceptionOrNull() is IllegalArgumentException)
        }
        for (bad in listOf("http://exemple.test", "https://a'b", "https://a\"b", "https://a b", "javascript:alert(1)")) {
            val r = runCatching { trailerPageHtml("dQw4w9WgXcQ", bad) }
            assertTrue("origine acceptée : $bad", r.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
