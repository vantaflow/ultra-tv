package com.ultratv.tv.nativeapp.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ANR réel (MatcherNative.findImpl / setInputImpl sur le fil principal, Sharp Google TV) : les noms de chaînes étaient
 * repassés dans une dizaine d'expressions à chaque composition d'une case du guide, à chaque zapping et à chaque clic
 * (TitleCleaner.clean, ChannelNameParser.parse). Résultat mémorisé par nom : la seconde demande ne refait aucune regex.
 */
class NameMemoTest {
    private val names = (0 until 400).map { i -> "FR| ##### TF1 $i HD ᴿᴬᵂ (FR) | 4K-A+ - M6 $i FHD HEVC (2021)" }

    @Test fun resultatsIdentiquesAuCalculDirect() {
        for (n in names.take(50) + listOf("", "   ", "NL: NPO 1 HD", "[VIP] FR| TF1", "##### SPORT #####")) {
            assertEquals(TitleCleaner.cleanUncached(n, true), TitleCleaner.clean(n, live = true))
            assertEquals(TitleCleaner.cleanUncached(n, false), TitleCleaner.clean(n))
            assertEquals(ChannelNameParser.parseUncached(n), ChannelNameParser.parse(n))
        }
    }

    @Test fun secondeDemande_memeInstance_aucuneRegexRefaite() {
        val n = "FR| TF1 HD (FR)"
        assertSame(TitleCleaner.clean(n, live = true), TitleCleaner.clean(n, live = true))
        assertSame(ChannelNameParser.parse(n), ChannelNameParser.parse(n))
        // live / non live : deux entrées distinctes.
        assertEquals(TitleCleaner.cleanUncached(n, false), TitleCleaner.clean(n, live = false))
    }

    @Test fun mesureAvantApres() {
        names.forEach { TitleCleaner.clean(it, live = true); ChannelNameParser.parse(it) } // chauffe + remplissage
        val rounds = 25
        val t0 = System.nanoTime()
        repeat(rounds) { names.forEach { TitleCleaner.cleanUncached(it, true); ChannelNameParser.parseUncached(it) } }
        val before = System.nanoTime() - t0
        val t1 = System.nanoTime()
        repeat(rounds) { names.forEach { TitleCleaner.clean(it, live = true); ChannelNameParser.parse(it) } }
        val after = System.nanoTime() - t1
        println("NameMemo : ${rounds * names.size} noms, sans cache ${before / 1_000_000} ms, avec cache ${after / 1_000_000} ms")
        assertTrue("cache plus lent que le calcul (${after / 1_000_000} ms contre ${before / 1_000_000} ms)", after < before)
    }

    @Test fun lruBorne() {
        val memo = NameMemo<String, Int>(3)
        var calls = 0
        listOf("a", "b", "c", "a", "d", "b").forEach { memo.getOrPut(it) { calls++; it.length } }
        // a, b, c calculés ; a relu ; d évince b (le moins récent) ; b recalculé.
        assertEquals(5, calls)
    }
}
