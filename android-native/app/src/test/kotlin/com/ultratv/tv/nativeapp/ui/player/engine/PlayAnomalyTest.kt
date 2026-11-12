package com.ultratv.tv.nativeapp.ui.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlayAnomalyTest {
    @Test fun bascule_decrit_moteurs_format_sans_adresse() {
        val l = PlayAnomaly.describe(PlayErrorKind.NO_PICTURE, null, Combo(EngineKind.EXO, DecoderMode.AUTO), Combo(EngineKind.EXO, DecoderMode.SOFTWARE), true, "3840×2160", "hevc")
        assertEquals("fallback kind=NO_PICTURE from=EXO/AUTO to=EXO/SOFTWARE live=true res=3840×2160 codec=hevc", l)
        assertFalse(l.contains("http://"))
    }

    @Test fun echec_final_avec_code_http() {
        val l = PlayAnomaly.describe(PlayErrorKind.REFUSED, 403, Combo(EngineKind.VLC, DecoderMode.AUTO), null, false, null, null)
        assertEquals("failed kind=REFUSED http=403 from=VLC/AUTO live=false", l)
    }
}
