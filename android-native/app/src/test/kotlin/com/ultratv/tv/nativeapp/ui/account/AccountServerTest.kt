package com.ultratv.tv.nativeapp.ui.account

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.ultratv.tv.nativeapp.data.xtream.XtreamAccount
import com.ultratv.tv.nativeapp.i18n.AppLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Écran « Abonnement » : l'adresse affichée est celle SAISIE par l'utilisateur (source), jamais `server_info.url` renvoyée par
 * le panneau (serveur amont d'un revendeur, retour d'un utilisateur du 22/11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountServerTest {
    @get:Rule val rule = createComposeRule()

    @Test fun adresseSaisie_sansIdentifiantNiParametre() {
        assertEquals("http://aaaaa.example:8080", AccountServer.displayed("http://aaaaa.example:8080/"))
        assertEquals("http://aaaaa.example/c", AccountServer.displayed("http://user:secret@aaaaa.example/c?mac=00:11"))
        assertNull(AccountServer.displayed(""))
        assertNull(AccountServer.displayed(null))
    }

    @Test fun carte_afficheLAdresseSaisie_etJamaisCelleDuPanneau() {
        val account = XtreamAccount(status = "Active", expiresAt = null, activeConnections = 0, maxConnections = 1, createdAt = null, trial = false, server = "bbbbb.upstream")
        val st = AccountState(sourceName = "Ma source", xtream = true, account = account, loading = false, serverAddress = "http://aaaaa.example")
        rule.setContent { IptvAccountCard(st, AccountStrings(AppLang.French)) }
        rule.onNodeWithText("http://aaaaa.example").assertExists()
        assertEquals(0, rule.onAllNodesWithText("bbbbb.upstream", substring = true).fetchSemanticsNodes().size)
    }
}
