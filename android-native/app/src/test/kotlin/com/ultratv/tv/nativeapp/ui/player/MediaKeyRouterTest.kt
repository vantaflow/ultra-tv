package com.ultratv.tv.nativeapp.ui.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaKeyRouterTest {
    @Test fun touches_vers_actions() {
        assertEquals(MediaKeyAction.TOGGLE, MediaKeyRouter.actionFor(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(MediaKeyAction.TOGGLE, MediaKeyRouter.actionFor(KeyEvent.KEYCODE_HEADSETHOOK))
        assertEquals(MediaKeyAction.PLAY, MediaKeyRouter.actionFor(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(MediaKeyAction.PAUSE, MediaKeyRouter.actionFor(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(MediaKeyAction.SEEK_FORWARD, MediaKeyRouter.actionFor(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertEquals(MediaKeyAction.SEEK_BACK, MediaKeyRouter.actionFor(KeyEvent.KEYCODE_MEDIA_REWIND))
        assertEquals(MediaKeyAction.NEXT, MediaKeyRouter.actionFor(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(MediaKeyAction.PREVIOUS, MediaKeyRouter.actionFor(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
    }

    @Test fun volume_et_autres_touches_ne_sont_pas_routes() {
        assertEquals(MediaKeyAction.NONE, MediaKeyRouter.actionFor(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(MediaKeyAction.NONE, MediaKeyRouter.actionFor(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals(MediaKeyAction.NONE, MediaKeyRouter.actionFor(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test fun bascule_selon_etat_reel() {
        assertEquals(false, MediaKeyRouter.wantPlaying(MediaKeyAction.TOGGLE, enginePlaying = true))
        assertEquals(true, MediaKeyRouter.wantPlaying(MediaKeyAction.TOGGLE, enginePlaying = false))
        assertEquals(true, MediaKeyRouter.wantPlaying(MediaKeyAction.PLAY, enginePlaying = true))
        assertEquals(false, MediaKeyRouter.wantPlaying(MediaKeyAction.PAUSE, enginePlaying = false))
        assertNull(MediaKeyRouter.wantPlaying(MediaKeyAction.NEXT, enginePlaying = true))
    }

    @Test fun suivant_precedent_direct_zappe() {
        assertEquals(SkipTarget.CHANNEL_NEXT, MediaKeyRouter.skipTarget(MediaKeyAction.NEXT, true, false, false))
        assertEquals(SkipTarget.CHANNEL_PREVIOUS, MediaKeyRouter.skipTarget(MediaKeyAction.PREVIOUS, true, false, false))
    }

    @Test fun suivant_precedent_vod_episode_seulement_si_voisin() {
        assertEquals(SkipTarget.EPISODE_NEXT, MediaKeyRouter.skipTarget(MediaKeyAction.NEXT, false, true, false))
        assertEquals(SkipTarget.NONE, MediaKeyRouter.skipTarget(MediaKeyAction.NEXT, false, false, true))
        assertEquals(SkipTarget.EPISODE_PREVIOUS, MediaKeyRouter.skipTarget(MediaKeyAction.PREVIOUS, false, false, true))
        assertEquals(SkipTarget.NONE, MediaKeyRouter.skipTarget(MediaKeyAction.PREVIOUS, false, true, false))
    }

    @Test fun autre_action_ne_saute_pas() {
        assertEquals(SkipTarget.NONE, MediaKeyRouter.skipTarget(MediaKeyAction.TOGGLE, true, true, true))
        assertTrue(MediaKeyRouter.isSkip(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertFalse(MediaKeyRouter.isSkip(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    @Test fun seul_l_appui_initial_compte() {
        assertTrue(MediaKeyRouter.isInitialPress(down = true, repeatCount = 0))
        assertFalse(MediaKeyRouter.isInitialPress(down = true, repeatCount = 1))
        assertFalse(MediaKeyRouter.isInitialPress(down = false, repeatCount = 0))
    }

    @Test fun anti_doublon_meme_direction_rapprochee() {
        val d = SkipDebounce(600)
        assertTrue(d.accept(true, 1_000))
        assertFalse(d.accept(true, 1_200))
        assertTrue(d.accept(false, 1_300))
        assertTrue(d.accept(true, 2_500))
    }
}
