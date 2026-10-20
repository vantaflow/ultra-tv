package com.ultratv.tv.nativeapp.ui.live

import com.ultratv.tv.nativeapp.ui.live.LivePreviewPolicy.Action
import com.ultratv.tv.nativeapp.ui.player.engine.LiveOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePreviewPolicyTest {

    @Test
    fun active_autoDesactiveSurBoxFaibleMemoireEtTactile() {
        assertTrue(LivePreviewPolicy.enabled("auto", lowRam = false, touch = false))
        assertFalse(LivePreviewPolicy.enabled("auto", lowRam = true, touch = false))
        assertTrue(LivePreviewPolicy.enabled("on", lowRam = true, touch = false))
        assertFalse(LivePreviewPolicy.enabled("off", lowRam = false, touch = false))
        assertFalse(LivePreviewPolicy.enabled("on", lowRam = false, touch = true))
    }

    @Test
    fun delai_borne() {
        assertEquals(700L, LivePreviewPolicy.delayMs(LivePreviewPolicy.DEFAULT_DELAY_MS))
        assertEquals(300L, LivePreviewPolicy.delayMs(0))
        assertEquals(3_000L, LivePreviewPolicy.delayMs(60_000))
    }

    @Test
    fun decision() {
        // Rien ne joue : on lance.
        assertEquals(Action.START, LivePreviewPolicy.decide(LiveOwner.NONE, null, "1:a", locked = false, holdAfterReturn = false))
        // La ligne joue déjà : aucun rechargement.
        assertEquals(Action.KEEP, LivePreviewPolicy.decide(LiveOwner.PREVIEW, "1:a", "1:a", false, false))
        // Autre ligne : on enchaîne.
        assertEquals(Action.START, LivePreviewPolicy.decide(LiveOwner.PREVIEW, "1:a", "1:b", false, false))
        // Retour du plein écran, focus restauré sur l'ancienne ligne : la chaîne du dernier zap reste.
        assertEquals(Action.KEEP, LivePreviewPolicy.decide(LiveOwner.PREVIEW, "1:z", "1:a", false, holdAfterReturn = true))
        // Plein écran en cours : l'aperçu ne touche à rien.
        assertEquals(Action.KEEP, LivePreviewPolicy.decide(LiveOwner.FULLSCREEN, "1:a", "1:b", false, false))
        // Chaîne verrouillée : jamais d'aperçu (code parental), l'aperçu en cours s'arrête.
        assertEquals(Action.STOP, LivePreviewPolicy.decide(LiveOwner.PREVIEW, "1:a", "1:b", locked = true, holdAfterReturn = false))
        assertEquals(Action.KEEP, LivePreviewPolicy.decide(LiveOwner.NONE, null, "1:b", locked = true, holdAfterReturn = false))
    }
}
