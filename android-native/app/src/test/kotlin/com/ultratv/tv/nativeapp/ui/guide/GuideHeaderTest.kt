package com.ultratv.tv.nativeapp.ui.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.i18n.sportRemind
import com.ultratv.tv.nativeapp.ui.common.design
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

/**
 * En-tête du Guide TV (hauteur FIXE de 300 unités de maquette) : « Regarder » / « Me rappeler » / « Enregistrer » restent
 * entièrement visibles et focalisables quel que soit le titre, la barre « en cours », la description, la ligne de
 * progression du chargement, la taille de texte choisie et le sens d'écriture. Densité 2,0 : 1 unité de maquette = 1 px.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class GuideHeaderTest {
    @get:Rule val rule = createComposeRule()

    @After fun reset() { Ux.textScale = 1f }

    private val now = 10_000_000_000L
    private val longTitle = "Rugby : Championnat des nations | Angleterre / Nouvelle-Zélande"
    private val longDesc = "Le XV de la Rose reçoit les All Blacks à Twickenham pour le dernier match de la tournée d'automne, " +
        "avec les commentaires et les analyses de nos consultants en plateau avant, pendant et après la rencontre."

    private val chan = ChannelEntity(id = 1, providerId = 1, remoteId = "1", name = "Sport 1", logo = null, categoryId = "c", streamUrl = "u", num = 1)

    private fun prog(title: String, desc: String?, live: Boolean) = EpgEntity(
        id = 11, channelId = 1, title = title, description = desc,
        startMs = if (live) now - 30 * 60_000L else now + 3_600_000L, endMs = if (live) now + 30 * 60_000L else now + 7_200_000L,
    )

    private var watchLabel = ""

    private class Result(val header: Rect, val buttons: Map<String, Rect>)

    private fun render(p: EpgEntity, lang: AppLang, progressLine: String?, scale: Float, rtl: Boolean): Result {
        I18n.init(ApplicationProvider.getApplicationContext())
        Ux.textScale = scale
        var header = Rect.Zero
        var labels = emptyList<String>()
        rule.setContent {
            val d = designStringsFor(lang)
            labels = listOf(d.watch, d.pRecord) + if (p.startMs > now) listOf(d.sportRemind) else emptyList()
            watchLabel = d.watch
            CompositionLocalProvider(
                LocalDs provides d, LocalStrings provides stringsFor(lang), LocalTouch provides false,
                // Comme ProvideUiScale sur TV : densité 2 (1 unité de maquette = 1 px), police × taille de texte choisie.
                LocalDensity provides Density(2f, scale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                val sel = remember { mutableStateOf<Pair<ChannelEntity, EpgEntity>?>(chan to p) }
                val clock = remember { mutableLongStateOf(now) }
                Box(Modifier.width(1100.design).height(300.design).onGloballyPositioned { header = it.boundsInRoot() }) {
                    ProgramInfo(sel, clock, d, progressLine, Modifier.fillMaxHeight(), onWatch = {}, onRemind = { _, _ -> }, onRecord = { _, _ -> })
                }
            }
        }
        rule.waitForIdle()
        val b = labels.associateWith { l -> rule.onNodeWithText(l).fetchSemanticsNode().boundsInRoot }
        return Result(header, b)
    }

    private fun check(r: Result, scale: Float) {
        assertEquals(300f, r.header.height, 0.5f)
        val expected = 56f * scale
        r.buttons.forEach { (label, b) ->
            assertEquals("« $label » doit garder sa hauteur entière", expected, b.height, 1f)
            assertTrue("« $label » dépasse le bas de l'en-tête : ${b.bottom} > ${r.header.bottom}", b.bottom <= r.header.bottom + 0.5f)
            assertTrue("« $label » dépasse le haut de l'en-tête : ${b.top} < ${r.header.top}", b.top >= r.header.top - 0.5f)
        }
    }

    private fun worst(lang: AppLang = AppLang.French, scale: Float = 1f, rtl: Boolean = false, live: Boolean = true, title: String = longTitle, desc: String? = longDesc, progress: String? = "Chargement du guide : 123 / 900 chaînes") {
        val r = render(prog(title, desc, live), lang, progress, scale, rtl)
        check(r, scale)
        rule.onNodeWithText(watchLabel).assertIsDisplayed()
        rule.onNodeWithText(watchLabel).performSemanticsAction(SemanticsActions.RequestFocus)
        rule.waitForIdle()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true)).assertExists()
    }

    @Test fun pireCas_titreLong_enCours_description_progression_taillesNormale() = worst()
    @Test fun pireCas_taillePolice_grand() = worst(scale = 1.15f)
    @Test fun pireCas_taillePolice_tresGrand() = worst(scale = 1.3f)
    @Test fun pireCas_programmeAVenir_troisBoutons_tresGrand() = worst(scale = 1.3f, live = false)
    @Test fun pireCas_arabe_rtl() = worst(lang = AppLang.Arabic, rtl = true, title = "الرغبي : بطولة الأمم | إنجلترا / نيوزيلندا الجديدة — مباراة العودة الكبرى من الموسم", scale = 1.3f)
    @Test fun titreCourt_neRegressePas() = worst(title = "Dune", desc = "Court.", progress = null)
    @Test fun titreCourt_sansDescription_trèsGrand() = worst(title = "Dune", desc = null, progress = null, scale = 1.3f)

    @Test fun contenuSecondaire_cede_maisLeTitreResteSurUneLigneEntiere() {
        val r = render(prog(longTitle, longDesc, true), AppLang.French, "Chargement du guide : 1 / 9", 1.3f, false)
        val t = rule.onNodeWithText(longTitle).fetchSemanticsNode().boundsInRoot
        assertTrue("titre sur deux lignes ou rogné : ${t.height}", t.height in 1f..(58f * 1.3f + 1f))
        assertTrue(t.bottom <= r.header.bottom)
    }

    @Test fun placeSuffisante_laDescriptionResteAffichee() {
        render(prog("Dune", longDesc, true), AppLang.French, null, 1f, false)
        rule.onNode(androidx.compose.ui.test.hasText("Le XV de la Rose", substring = true)).assertIsDisplayed()
    }
}
