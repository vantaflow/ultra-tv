package com.ultratv.tv.nativeapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    private fun assertNoSecrets(input: String, vararg secrets: String) {
        val out = LogSanitizer.sanitize(input)
        for (s in secrets) assertFalse("« $s » fuit dans : $out", out.contains(s))
    }

    @Test fun `parametres username et password d'une URL Xtream sont retires`() =
        assertNoSecrets("HTTP 403 http://h.tv:8080/get.php?username=bob&password=hunter2&type=m3u", "bob", "hunter2")

    @Test fun `ordre inverse des parametres`() =
        assertNoSecrets("http://h.tv/player_api.php?password=hunter2&username=bob", "bob", "hunter2")

    @Test fun `identifiants dans l'URL user colon pass arobase`() =
        assertNoSecrets("http://bob:hunter2@h.tv/list.m3u", "bob", "hunter2")

    @Test fun `segments de chemin live user pass`() =
        assertNoSecrets("player error http://h.tv:80/live/bob/hunter2/1234.ts", "bob", "hunter2")

    @Test fun `segments de chemin movie et series et timeshift`() {
        assertNoSecrets("https://h.tv/movie/bob/hunter2/55.mkv", "bob", "hunter2")
        assertNoSecrets("https://h.tv/series/bob/hunter2/9.mp4", "bob", "hunter2")
        assertNoSecrets("https://h.tv/timeshift/bob/hunter2/60/2025-01-01:10-00/7.ts", "bob", "hunter2")
    }

    @Test fun `jeton dans la requete`() = assertNoSecrets("https://h.tv/l.m3u?token=abcdef123456", "abcdef123456")

    @Test fun `user egal pass egal en texte libre`() = assertNoSecrets("login failed user=bob pass=hunter2", "bob", "hunter2")

    @Test fun `jeton d'appareil et en-tete Bearer`() {
        val t = "utv_" + "A".repeat(43)
        assertNoSecrets("Authorization: Bearer $t", t, "AAAAAAAAAAAA")
        assertNoSecrets("token $t échoué", t)
    }

    @Test fun `texte banal inchange`() {
        val msg = "Player error 404 on channel 12"
        assertEquals(msg, LogSanitizer.sanitize(msg))
    }

    @Test fun `un marqueur de remplacement apparait`() {
        assertTrue(LogSanitizer.sanitize("?password=x").contains("<redacted>"))
    }

    @Test fun `tronque les messages trop longs`() {
        assertTrue(LogSanitizer.sanitize("a".repeat(10_000), 100).length <= 100)
    }

    @Test fun `retire les caracteres de controle`() {
        assertEquals("abc", LogSanitizer.sanitize("a\u0000b\u001bc"))
    }
}
