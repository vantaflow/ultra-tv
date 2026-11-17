package com.ultratv.tv.nativeapp.ui.sport

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.sport.LeagueMatch
import com.ultratv.tv.nativeapp.data.sport.LeaguePrefs
import com.ultratv.tv.nativeapp.data.sport.MatchSheet
import com.ultratv.tv.nativeapp.data.sport.MatchState
import com.ultratv.tv.nativeapp.data.sport.SportEntry
import com.ultratv.tv.nativeapp.data.sport.SportLogic
import com.ultratv.tv.nativeapp.data.sport.StandingRow
import com.ultratv.tv.nativeapp.data.sport.StatItem
import com.ultratv.tv.nativeapp.data.sport.TimelineItem
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Calendar

/** Rendu de l'accueil Sport unifié, de la feuille de match et du classement complet (PNG sous `build/outputs/ui-v2/`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SportHubRenderTest {
    @get:Rule val rule = createComposeRule()
    private val outDir = File("build/outputs/ui-v2").also { it.mkdirs() }
    @After fun resetTheme() { Ux.themeLight = false }

    private val now: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 8); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun min(m: Int) = m * 60_000L

    private fun channel(id: Long, num: Int, name: String, q: Int) =
        ChannelEntity(id = id, providerId = 1, remoteId = "$id", name = name, logo = null, categoryId = "sport", streamUrl = "u", num = num, quality = q)
    private val c1 = channel(1, 101, "Stade 1 4K", 4); private val c2 = channel(2, 145, "Stade 1 HD", 2); private val c3 = channel(3, 312, "Stade 1 SD", 1)
    private val c4 = channel(4, 21, "Écran+ Sport", 2)

    private fun entry(ch: ChannelEntity, title: String, start: Long, end: Long) =
        SportEntry(ch, EpgEntity(id = start + ch.id, channelId = ch.id, title = title, description = null, startMs = start, endMs = end), sportChannel = true, strict = true)

    private val all = listOf(
        entry(c1, "Ligue 1 : Paris SG – Marseille", now - min(67), now + min(53)),
        entry(c2, "Ligue 1 : Paris SG – Marseille", now - min(67), now + min(53)),
        entry(c3, "Ligue 1 : Paris SG – Marseille", now - min(67), now + min(53)),
        entry(c4, "NBA : Celtics – Lakers", now - min(40), now + min(60)),
        entry(c2, "Liga : Real Madrid – FC Barcelone", now + min(37), now + min(130)),
        entry(c4, "Serie A : Inter – Milan", now + min(52), now + min(150)),
        entry(c4, "Canal Football Club", now + min(112), now + min(200)),
    )
    private fun st() = SportState(
        loading = false, hasSource = true, hasGuide = true, live = SportLogic.liveNow(all, now), upcoming = SportLogic.upcoming(all, now),
        channels = listOf(c1, c4), alsoOn = com.ultratv.tv.nativeapp.data.sport.SportMatching.alsoOnMap(all), entries = all,
    )

    private val psgOm = LeagueMatch("fd-123", now - min(67), null, "Paris SG", "Marseille", null, null, 2, 1, MatchState.LIVE, "67", 14)
    private fun rows(): List<StandingRow> {
        val d = listOf(
            Triple("Paris SG", 11, 1), Triple("Marseille", 9, 2), Triple("Monaco", 8, 3), Triple("Lille", 7, 4), Triple("Lens", 7, 3), Triple("Nice", 6, 4),
            Triple("Lyon", 6, 3), Triple("Rennes", 5, 4), Triple("Strasbourg", 5, 3), Triple("Nantes", 4, 4), Triple("Le Havre", 3, 3), Triple("Angers", 2, 4),
        )
        return d.mapIndexed { i, (t, w, n) -> StandingRow(i + 1, t, null, null, w + n + 2, w, n, 2, 21 - i * 2, w * 3 + n, "WWDLW".drop(i % 2)) }
    }
    private fun lgState(noSpoiler: Boolean = false) = LeaguesState(
        prefs = LeaguePrefs.DEFAULT, noSpoiler = noSpoiler, selected = "ligue1", status = LeagueStatus.OK,
        matches = listOf(psgOm), table = rows(),
    )

    private fun capture(file: String, light: Boolean = false, touch: Boolean = false, content: @Composable () -> Unit) {
        I18n.init(ApplicationProvider.getApplicationContext())
        Ux.themeLight = light
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(AppLang.French), LocalStrings provides stringsFor(AppLang.French), LocalTouch provides touch) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                Box(Modifier.fillMaxSize().background(Ux.Bg)) { content() }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Sport capture : ${File(outDir, file).absolutePath} (${bmp.width}x${bmp.height})")
    }

    @Composable private fun home(hub: HubState, touch: Boolean) {
        val D = LocalDs.current
        val clock = remember { mutableLongStateOf(now) }
        if (touch) SportTouchContent(st(), D, clock, emptySet(), {}, {}, {}, {}, hub = hub)
        else SportTvContent(st(), D, clock, emptySet(), {}, {}, {}, {}, hub = hub)
    }

    @Test fun accueil_tv_ligueChoisie_scoreEtClassement() {
        capture("sport-hub-tv.png") { home(HubState(lg = lgState(), all = false), false) }
        assertTrue(rule.onAllNodesWithText("Marseille").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Classement complet").fetchSemanticsNodes().isNotEmpty())
        assertEquals(1920, rule.onRoot().captureToImage().width)
    }

    @Test fun accueil_tv_tout_sansSpoiler_scoreMasque() {
        capture("sport-hub-tv-all-nospoiler.png") { home(HubState(lg = lgState(noSpoiler = true), all = true), false) }
        assertTrue(rule.onAllNodesWithText("Canal Football Club").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Afficher les résultats").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun accueil_tv_clair() { capture("sport-hub-tv-light.png", light = true) { home(HubState(lg = lgState(), all = false), false) } }

    @Test fun accueil_telephone_uneColonne() {
        capture("sport-hub-phone.png", touch = true) { home(HubState(lg = lgState(), all = false), true) }
        assertTrue(rule.onAllNodesWithText("Marseille").fetchSemanticsNodes().isNotEmpty())
    }

    private val sheet = MatchSheet(
        psgOm, "Parc des Princes",
        listOf(TimelineItem("goal", null, true, 12, "Dembélé", null), TimelineItem("card", "yellow", false, 29, "Rabiot", null), TimelineItem("goal", null, false, 44, "Greenwood", null), TimelineItem("goal", null, true, 58, "Barcola", null)),
        listOf(StatItem("Possession", 61, 39), StatItem("Tirs", 14, 7), StatItem("Tirs cadrés", 6, 3), StatItem("Corners", 5, 2), StatItem("Fautes", 9, 12)),
    )

    @Test fun feuilleDeMatch_scoreAffiche_troisColonnes() {
        capture("sport-hub-match.png") {
            MatchSheetScreen(LocalDs.current, psgOm, sheet, false, noSpoiler = false, revealed = false, channels = all.take(3), onReveal = {}, onPlay = {}, onClose = {})
        }
        assertTrue(rule.onAllNodesWithText("Dembélé").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Stade 1").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun feuilleDeMatch_sansSpoiler_boutonAfficherLeScore() {
        capture("sport-hub-match-nospoiler.png") {
            MatchSheetScreen(LocalDs.current, psgOm, sheet, false, noSpoiler = true, revealed = false, channels = all.take(3), onReveal = {}, onPlay = {}, onClose = {})
        }
        assertTrue(rule.onAllNodesWithText("Afficher le score").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Dembélé").fetchSemanticsNodes().isEmpty())
    }

    @Test fun feuilleProgramme_sansScore_choixDeLaChaine() {
        val cm = com.ultratv.tv.nativeapp.data.sport.LiveCardModel(
            "g1", "Qualifications de la CAN", null, "RD Congo", "Sierra Leone", "Football : Qualifications de la CAN | RD Congo / Sierra Leone",
            null, null, null, listOf(all[0], all[1], all[2]), 0.5f,
        )
        capture("sport-hub-programme.png") {
            MatchSheetScreen(
                LocalDs.current, com.ultratv.tv.nativeapp.data.sport.SportHubLogic.guideMatch(cm), null, false, noSpoiler = false, revealed = false,
                channels = cm.channels, onReveal = {}, onPlay = {}, onClose = {}, league = cm.league, programme = cm.channels.first(),
            )
        }
        assertTrue(rule.onAllNodesWithText("RD Congo").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Stade 1 SD").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun classementComplet_etMesLigues() {
        capture("sport-hub-standings.png") {
            StandingsScreen(LocalDs.current, lgState(), LeagueActions({}, { _, _ -> }, { _, _ -> }, {}), customize = false, onClose = {})
        }
        assertTrue(rule.onAllNodesWithText("Strasbourg").fetchSemanticsNodes().isNotEmpty())
        assertTrue(rule.onAllNodesWithText("Mes ligues").fetchSemanticsNodes().isNotEmpty())
    }
}
