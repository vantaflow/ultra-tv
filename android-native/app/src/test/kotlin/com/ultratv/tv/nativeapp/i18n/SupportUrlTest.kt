package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.ui.design.qrMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** « Soutenir Ultra TV » : page de dons PayPal du site (seul moyen de don), QR généré sur l'appareil. */
class SupportUrlTest {
    @Test fun pageDeDonsPayPal() = assertEquals("https://ultratv.dpdns.org/donate", SUPPORT_URL)

    @Test fun qrGenereLocalement() {
        val m = qrMatrix(SUPPORT_URL)
        assertTrue(m.isNotEmpty())
        assertEquals(m.size, m[0].size)
    }
}
