package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.ui.design.qrMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** « Soutenir Ultra TV » : don direct sur PayPal (seul moyen de don), QR généré sur l'appareil. */
class SupportUrlTest {
    @Test fun lienPayPalDirect() = assertEquals("https://paypal.me/kbenazzouz", SUPPORT_URL)

    @Test fun qrGenereLocalement() {
        val m = qrMatrix(SUPPORT_URL)
        assertTrue(m.isNotEmpty())
        assertEquals(m.size, m[0].size)
    }
}
