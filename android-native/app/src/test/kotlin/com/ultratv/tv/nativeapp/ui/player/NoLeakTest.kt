package com.ultratv.tv.nativeapp.ui.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertCountEquals
import com.ultratv.tv.nativeapp.data.net.SyncErrorKind
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.UserText
import com.ultratv.tv.nativeapp.ui.player.engine.PlayErrorKind
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Aucun écran du lecteur ni aucun message d'erreur ne doit contenir d'URL (`://`) ni d'identifiant. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1920dp-h1080dp-xhdpi")
class NoLeakTest {
    @get:Rule val rule = createComposeRule()

    private val langs = listOf(AppLang.English, AppLang.French, AppLang.Spanish, AppLang.Arabic)

    @Test fun errorPanel_toutesLesErreurs_toutesLesLangues_neContiennentJamaisDUrl() {
        var lang by androidx.compose.runtime.mutableStateOf(AppLang.English)
        var kind by androidx.compose.runtime.mutableStateOf(PlayErrorKind.entries.first())
        rule.setContent { ErrorPanel(kind, canNext = true, D = DesignStrings(lang), onRetry = {}, onNext = {}, onClose = {}) }
        for (l in langs) for (k in PlayErrorKind.entries) {
            rule.runOnIdle { lang = l; kind = k }
            rule.waitForIdle()
            rule.onAllNodes(hasText("://", substring = true)).assertCountEquals(0)
        }
    }

    @Test fun messagesDeSynchro_toutesLesErreurs_toutesLesLangues_sansUrl() {
        val msgs = mutableListOf<String>()
        rule.setContent { for (lang in langs) for (kind in SyncErrorKind.entries) msgs += stringsFor(lang).sync.messageFor(kind) }
        rule.waitForIdle()
        assertEquals(langs.size * SyncErrorKind.entries.size, msgs.size)
        for (m in msgs) assertFalse(m, m.contains("://"))
    }

    @Test fun userText_masqueUrlsEtIdentifiants() {
        val raw = "Failed to connect to http://serveur-fictif.invalid:8080/live/monuser/monpass/123.ts?username=monuser&password=monpass (timeout)"
        val safe = UserText.safe(raw)
        assertFalse(safe, safe.contains("://")); assertFalse(safe, safe.contains("monuser")); assertFalse(safe, safe.contains("monpass"))
    }

    @Test fun userText_texteSansUrl_resteLisible() = assertEquals("Délai dépassé", UserText.safe("Délai dépassé"))
}
