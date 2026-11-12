package com.ultratv.tv.nativeapp.ui.sport

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.sport.SportEntry
import com.ultratv.tv.nativeapp.data.sport.SportKind
import com.ultratv.tv.nativeapp.data.sport.SportLogic
import com.ultratv.tv.nativeapp.data.sport.matchLineOf
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.components.RAIL_COLLAPSED_PX
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Calendar

/**
 * Rendu de l'écran Sport TV v2 (maquette Sport.dc.html) : données fictives neutres, PNG sous `build/outputs/ui-v2/`.
 * Fenêtre 960 × 540 dp à 2,0 (= 1920 × 1080 px). Vérifie aussi la décomposition des titres et la détection de discipline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SportRenderTest {
    @get:Rule val rule = createComposeRule()

    private val outDir = File("build/outputs/ui-v2").also { it.mkdirs() }

    @After fun resetTheme() { Ux.themeLight = false }

    private val now: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 36); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun min(m: Int) = m * 60_000L

    private fun channel(id: Long, num: Int, name: String) =
        ChannelEntity(id = id, providerId = 1, remoteId = "$id", name = name, logo = null, categoryId = "sport", streamUrl = "u", num = num)

    private val bein1 = channel(1, 12, "beIN Sports 1")
    private val bein3 = channel(2, 14, "beIN Sports 3")
    private val canal = channel(3, 21, "Canal+ Sport")
    private val euro = channel(4, 30, "Eurosport 1")
    private val rmc = channel(5, 31, "RMC Sport 1")
    private val dazn = channel(6, 32, "DAZN 1")
    private val bein2 = channel(7, 13, "beIN Sports 2")

    private fun entry(ch: ChannelEntity, title: String, start: Long, end: Long) =
        SportEntry(ch, EpgEntity(id = start + ch.id, channelId = ch.id, title = title, description = null, startMs = start, endMs = end), sportChannel = true, strict = true)

    private fun state(): SportState {
        val all = listOf(
            entry(bein1, "Ligue 1 : PSG – Marseille", now - min(62), now + min(58)),
            entry(bein3, "Liga : Real Madrid – Séville", now - min(31), now + min(89)),
            entry(canal, "NBA : Celtics – Lakers", now - min(40), now + min(60)),
            entry(euro, "ATP Paris : Sinner – Rune", now - min(30), now + min(90)),
            entry(bein2, "Serie A : Inter – Milan", now + min(129), now + min(240)),
            entry(bein1, "Ligue 1 : Lyon – Lille", now + 21 * 60 * min(1) - min(36), now + 23 * 60 * min(1)),
            entry(canal, "Ligue des champions : PSG – Barcelone", now + 24 * 60 * min(1), now + 26 * 60 * min(1)),
            entry(canal, "Formule 1 : Qualifications du Grand Prix", now + 41 * 60 * min(1), now + 43 * 60 * min(1)),
            entry(rmc, "UFC 320", now + 44 * 60 * min(1), now + 46 * 60 * min(1)),
        )
        return SportState(
            loading = false, hasSource = true, hasGuide = true,
            live = SportLogic.liveNow(all, now), upcoming = SportLogic.upcoming(all, now),
            channels = listOf(bein1, bein2, canal, rmc, euro, dazn),
        )
    }

    private fun render(light: Boolean, file: String, reminded: Set<String>) {
        I18n.init(ApplicationProvider.getApplicationContext())
        Ux.themeLight = light
        val st = state()
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(AppLang.French), LocalStrings provides stringsFor(AppLang.French), LocalTouch provides false,
            ) {
                val D = LocalDs.current
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                val clock = remember { mutableLongStateOf(now) }
                Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                    Box(Modifier.fillMaxHeight().width(RAIL_COLLAPSED_PX.design).background(Ux.Rail))
                    Box(Modifier.padding(start = RAIL_COLLAPSED_PX.design)) {
                        SportTvContent(
                            st = st, D = D, clock = clock, reminded = reminded,
                            onPlay = {}, onRemind = {}, onRecord = {}, onGoSettings = {},
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Sport capture : ${File(outDir, file).absolutePath} (${bmp.width}x${bmp.height})")
    }

    @Test fun capture_tv_fr_sombre() {
        // Deux rappels déjà posés : « Rappel activé » dans la liste « À venir ».
        val s = state()
        val keys = s.upcoming.flatMap { it.entries }.filter { it.prog.title.contains("Inter") || it.prog.title.contains("Barcelone") }.map { "${it.channel.id}-${it.prog.startMs}" }.toSet()
        render(light = false, file = "sport-tv-fr.png", reminded = keys)
    }

    @Test fun capture_tv_fr_clair() { render(light = true, file = "sport-tv-fr-light.png", reminded = emptySet()) }

    @Test fun capture_dimensions() {
        render(light = false, file = "sport-tv-fr.png", reminded = emptySet())
        val bmp = rule.onRoot().captureToImage()
        assertEquals(1920, bmp.width); assertEquals(1080, bmp.height)
        assertTrue(File(outDir, "sport-tv-fr.png").length() > 10_000)
    }

    @Test fun titre_deRencontre_competitionEtEquipes() {
        val m = matchLineOf("Ligue 1 : PSG – Marseille")
        assertEquals("Ligue 1", m.competition); assertEquals("PSG", m.home); assertEquals("Marseille", m.away)
        val v = matchLineOf("Lakers vs Celtics")
        assertNull(v.competition); assertEquals("Lakers", v.home); assertEquals("Celtics", v.away)
    }

    @Test fun titre_sansSeparateur_resteEntier() {
        val m = matchLineOf("UFC 320")
        assertNull(m.home); assertNull(m.away); assertEquals("UFC 320", m.title)
    }

    @Test fun discipline_reconnueParMotsEntiers() {
        assertEquals(SportKind.FOOTBALL, SportKind.of("Ligue 1 : PSG – Marseille"))
        assertEquals(SportKind.BASKET, SportKind.of("NBA : Celtics – Lakers"))
        assertEquals(SportKind.TENNIS, SportKind.of("ATP Paris : Sinner – Rune"))
        assertEquals(SportKind.COMBAT, SportKind.of("UFC 320"))
        assertEquals(SportKind.MOTOR, SportKind.of("Formule 1 : Qualifications"))
        // Une série n'est pas un match de « Serie A ».
        assertNull(SportKind.of("Série policière : épisode 4"))
    }
}
