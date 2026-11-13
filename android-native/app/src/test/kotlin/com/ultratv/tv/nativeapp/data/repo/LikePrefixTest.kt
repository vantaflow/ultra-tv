package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class LikePrefixTest {
    @Test fun jokersDeLaSaisieEchappes() {
        assertEquals("nuit%", likePrefix("nuit"))
        assertEquals("100\\% pur%", likePrefix("100% pur"))
        assertEquals("a\\_b%", likePrefix("a_b"))
        assertEquals("c\\\\d%", likePrefix("c\\d"))
    }
}
