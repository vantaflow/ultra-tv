package com.ultratv.tv.nativeapp.ui.design

import com.ultratv.tv.nativeapp.data.config.PairingLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrAndLabelTest {
    @Test fun qr_adresseDuTableauDeBord_matriceCarreeNonVide() {
        val m = qrMatrix("https://ultratv-config.khalilbenaz.workers.dev")
        assertTrue(m.size >= 21)
        assertTrue(m.all { it.size == m.size })
        assertTrue(m.any { row -> row.any { it } })
    }

    @Test fun etiquetteDeBox_stable_formatLisible_etSansIdentifiantBrut() {
        val l = PairingLabel.of("02:00:00:aa:bb:cc")
        assertEquals(l, PairingLabel.of("02:00:00:aa:bb:cc"))
        assertTrue(Regex("UTV-[0-9A-F]{6}").matches(l))
        assertTrue(!l.contains("aa", ignoreCase = true).and(l.contains("bb", ignoreCase = true)))
    }
}
