package com.ultratv.tv.nativeapp.ui.guide

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.components.RAIL_COLLAPSED_PX
import com.ultratv.tv.nativeapp.ui.design.Manrope
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.design.spx
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_FAVORITES
import com.ultratv.tv.nativeapp.ui.live.DirectCategory
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

/**
 * Rendu du Guide TV v2 (maquettes Guide.dc.html / GuideClair.dc.html) : programmes fictifs neutres, catégories « réelles »
 * (Favoris, Toutes, quatre catégories avec leur nombre de chaînes), PNG sous `build/outputs/ui-v2/`.
 * Fenêtre 960 × 540 dp à 2,0 (= 1920 × 1080 px).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class GuideRenderTest {
    /** Langue de l'interface du rendu (variantes anglaises pour le README et le site en anglais). */
    private var lang: AppLang = AppLang.French
    @get:Rule val rule = createComposeRule()

    private val outDir = File("build/outputs/ui-v2").also { it.mkdirs() }

    @After fun resetTheme() { Ux.themeLight = false }

    private fun at(h: Int, m: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun channel(id: Long, num: Int, name: String) =
        ChannelEntity(id = id, providerId = 1, remoteId = "$id", name = name, logo = null, categoryId = "c3", streamUrl = "u", num = num)

    private val chans = listOf(
        channel(1, 1, "Écran+ Cinéma"), channel(2, 2, "Cinétoile Classic"), channel(3, 3, "Cinétoile Club"),
        channel(4, 4, "Cinétoile Émotion"), channel(5, 5, "Grand Écran"),
    )

    private fun prog(ch: Long, i: Int, title: String, s: Pair<Int, Int>, e: Pair<Int, Int>, desc: String? = null) =
        EpgEntity(id = ch * 10 + i, channelId = ch, title = title, description = desc, startMs = at(s.first, s.second), endMs = at(e.first, e.second))

    private val programmes: Map<Long, List<EpgEntity>> = mapOf(
        1L to listOf(
            prog(1, 1, "IA, au cœur du cinéma", 0 to 24, 2 to 13, "Le prince Mychkine revient à Saint-Pétersbourg après un séjour en Suisse et retrouve un parent éloigné."),
            prog(1, 2, "The Last Refuge", 2 to 13, 3 to 43), prog(1, 3, "La disparition", 3 to 43, 4 to 30),
        ),
        2L to listOf(prog(2, 1, "L'idiot", 0 to 48, 2 to 20), prog(2, 2, "Gervaise", 2 to 20, 4 to 14)),
        3L to listOf(prog(3, 1, "Slow", 0 to 55, 2 to 39), prog(3, 2, "Le système Victoria", 2 to 39, 4 to 21)),
        4L to listOf(prog(4, 1, "Dans la cuisine des Nguyen", 1 to 17, 2 to 48), prog(4, 2, "8 femmes", 2 to 48, 4 to 45)),
        5L to listOf(prog(5, 1, "Dune", 0 to 30, 3 to 5), prog(5, 2, "Courts métrages", 3 to 5, 4 to 30)),
    )

    private val groups = listOf(
        DirectCategory(CATEGORY_FAVORITES, null, 12), DirectCategory(CATEGORY_ALL, null, 1240),
        DirectCategory("c1", "Actualités", 86), DirectCategory("c2", "Sport", 143),
        DirectCategory("c3", "Cinéma", 39), DirectCategory("c4", "Jeunesse", 38),
    )

    private fun render(light: Boolean, file: String) {
        I18n.init(ApplicationProvider.getApplicationContext())
        Ux.themeLight = light
        val windowStart = at(1, 0)
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang), LocalTouch provides false,
            ) {
                val D = LocalDs.current
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                // 01:31 : l'heure de la maquette (ligne et pastille « maintenant » dans la fenêtre 01:00 – 04:00).
                val clock = remember { mutableLongStateOf(at(1, 31)) }
                Box(Modifier.fillMaxSize().background(Ux.Bg)) {
                    Box(Modifier.fillMaxHeight().width(RAIL_COLLAPSED_PX.design).background(Ux.Rail))
                    Box(Modifier.padding(start = RAIL_COLLAPSED_PX.design)) {
                        GuideTvContent(
                            channelCount = chans.size, channelAt = { chans[it] }, peekAt = { chans[it] }, itemKey = { chans[it].id },
                            programmes = programmes, windowStart = windowStart, day = 0, D = D, clock = clock,
                            groups = groups, group = "c3", guideLoad = GuideLoad.Idle, nameColPx = 260,
                            groupName = { g ->
                                when (g.id) { CATEGORY_FAVORITES -> D.catFavorites; CATEGORY_ALL -> D.catAll; else -> g.name.orEmpty() }
                            },
                            nowProvider = { at(1, 31) },
                            onDay = {}, onWindowStart = {}, onGroup = {}, onVisible = {}, onRetry = {},
                            onSelect = { _, _ -> }, onWatch = {}, onRemind = { _, _ -> }, onRecord = { _, _ -> },
                            preview = { _, m ->
                                Box(m.clip(RoundedCornerShape(24.design)).background(Ux.Tone), contentAlignment = Alignment.Center) {
                                    Text("APERÇU", color = Ux.Muted, fontFamily = Manrope, fontSize = 20.spx, letterSpacing = 4.spx)
                                }
                            },
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        // Le focus initial (première case de la première ligne) alimente le panneau du programme ; une touche active le mode clavier.
        rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
        rule.waitForIdle()
        // Focus sur « L'idiot » (comme la maquette) : le panneau du haut affiche ce programme. Sans effet bloquant si le nœud n'accepte pas le focus.
        runCatching {
            rule.onNodeWithText("L'idiot").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
            rule.waitForIdle()
        }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Guide capture : ${File(outDir, file).absolutePath} (${bmp.width}x${bmp.height})")
    }

    @Test fun capture_tv_fr_sombre() { render(light = false, file = "guide-tv-fr.png") }

    @Test fun capture_tv_fr_clair() { render(light = true, file = "guide-tv-fr-light.png") }

    @Test fun capture_dimensions() {
        render(light = false, file = "guide-tv-fr.png")
        val bmp = rule.onRoot().captureToImage()
        assertEquals(1920, bmp.width); assertEquals(1080, bmp.height)
        assertTrue(File(outDir, "guide-tv-fr.png").length() > 10_000)
    }

    @Test fun capture_tv_en_sombre() { lang = AppLang.English; render(light = false, file = "guide-tv-en.png") }
}
