package com.ultratv.tv.nativeapp.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceClassTest {
    private val gb = 1024L * 1024 * 1024

    @Test fun boxUnGo_estEntreeDeGamme() = assertTrue(DeviceClass.isLowRam(1 * gb, false))
    @Test fun chromecastDeuxGoReportes_estEntreeDeGamme() = assertTrue(DeviceClass.isLowRam((1.9 * gb).toLong(), false))
    @Test fun quatreGo_nEstPasEntreeDeGamme() = assertFalse(DeviceClass.isLowRam(4 * gb, false))
    @Test fun drapeauSysteme_prime() = assertTrue(DeviceClass.isLowRam(8 * gb, true))
    @Test fun memoireInconnue_nEstPasEntreeDeGamme() = assertFalse(DeviceClass.isLowRam(0, false))
}
