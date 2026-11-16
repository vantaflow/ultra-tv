package com.ultratv.tv.nativeapp.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeDecodeTest {
    @Test fun pourcentLitteral_gardeTelQuel() {
        assertEquals("100%+p", SafeDecode.url("100%+p"))
        assertEquals("Promo 50%", SafeDecode.url("Promo 50%"))
    }
    @Test fun encodageValide_decode() = assertEquals("a b/é", SafeDecode.url("a+b%2F%C3%A9"))
}
