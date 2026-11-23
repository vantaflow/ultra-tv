package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ultratv.tv.nativeapp.data.prefs.AppTheme
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp
import com.ultratv.tv.nativeapp.ui.design.AccentChoice
import com.ultratv.tv.nativeapp.ui.design.GroupLabel
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.PaneTitle
import com.ultratv.tv.nativeapp.ui.design.PrefRow
import com.ultratv.tv.nativeapp.ui.design.SettingsCard
import com.ultratv.tv.nativeapp.ui.design.SwitchPrefRow
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.detail.resetDetailHost
import com.ultratv.tv.nativeapp.ui.detail.setDetail
import com.ultratv.tv.nativeapp.ui.mobile.LocalUiHeightDp
import com.ultratv.tv.nativeapp.ui.onboarding.brokenWords
import com.ultratv.tv.nativeapp.ui.onboarding.ellipsizedTexts
import com.ultratv.tv.nativeapp.ui.onboarding.savePng
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Mode des Réglages selon l'appareil : téléphone inchangé, tablette à deux volets dès 600 dp (portrait et paysage). Pur. */
class SettingsModeTest {
    @Test fun tv_toujoursDeuxVoletsDeLaMaquette() {
        assertEquals(SettingsMode.TV, settingsModeFor(touch = false, widthDp = 960f))
        assertEquals(SettingsMode.TV, settingsModeFor(touch = false, widthDp = 400f))
    }

    @Test fun telephone_sous600dp() {
        assertEquals(SettingsMode.PHONE, settingsModeFor(touch = true, widthDp = 360f))
        assertEquals(SettingsMode.PHONE, settingsModeFor(touch = true, widthDp = 599f))
    }

    @Test fun tablette_portraitEtPaysage() {
        assertEquals(SettingsMode.TABLET, settingsModeFor(touch = true, widthDp = 600f))
        assertEquals(SettingsMode.TABLET, settingsModeFor(touch = true, widthDp = 800f))     // 10 pouces en portrait
        assertEquals(SettingsMode.TABLET, settingsModeFor(touch = true, widthDp = 1280f))    // 10 pouces en paysage
        // Taille d'interface « Grande » (× 1,1) en portrait : 727 dp, toujours deux volets.
        assertEquals(SettingsMode.TABLET, settingsModeFor(touch = true, widthDp = 800f / 1.1f))
    }

    @Test fun agrandissement_plafonneSelonLaLargeurDisponible() {
        assertEquals(1.2f, tabletSettingsScale(1192f), 0.001f)          // paysage, rail retiré
        assertEquals(1.2f, tabletSettingsScale(712f), 0.001f)           // portrait, rail retiré
        val large = tabletSettingsScale(800f / 1.1f - 88f)              // portrait, interface Grande
        assertTrue(large in 1f..1.2f)
        assertEquals(560f, (800f / 1.1f - 88f) / large, 0.5f)
        assertEquals(1f, tabletSettingsScale(500f), 0.001f)             // jamais de réduction
    }

    @Test fun voletDesRubriques_proportionneEtBorne() {
        assertEquals(256f, tabletSettingsMetrics(500f).railDp, 0.01f)
        assertEquals(330f, tabletSettingsMetrics(1100f).railDp, 0.01f)
        val portrait = tabletSettingsMetrics(800f / TABLET_SETTINGS_SCALE)
        assertTrue(portrait.railDp in 256f..330f)
    }
}

private const val PREF_ROW_PROBE = "pref-row-probe"

/**
 * Réglages sur tablette 10 pouces (800 × 1280 et 1280 × 800 dp), taille de texte 1,3 et taille d'interface Grande : deux
 * volets, rubriques et lignes d'au moins 56 dp, aucun libellé coupé. Captures sous `build/outputs/ui-v2/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1300dp-h1300dp-xhdpi")
class SettingsTabletRenderTest {
    @get:Rule val rule = createComposeRule()
    @After fun reset() = resetDetailHost()

    private val rubs = listOf(
        SettingsRubUi(Icons.List, "Sources", "3 sources · Xtream, M3U, Stalker"),
        SettingsRubUi(Icons.Live, "Synchronisation", "Il y a 12 min"),
        SettingsRubUi(Icons.Guide, "Catégories", "120 actives · 8 désactivées"),
        SettingsRubUi(Icons.Settings, "Affichage", "Forêt · Sport : désactivé"),
        SettingsRubUi(Icons.Play, "Lecture", "Moteur, qualité, direct"),
        SettingsRubUi(Icons.Account, "Contrôle parental", "Code et chaînes verrouillées"),
        SettingsRubUi(Icons.Globe, "Langues", "Français"),
        SettingsRubUi(Icons.Heart, "Profils", "Qui regarde, code, enfants"),
        SettingsRubUi(Icons.Record, "Mémoire et stockage", "Images, base, espace libre"),
        SettingsRubUi(Icons.Search, "Messages", "Annonces reçues de l'équipe"),
        SettingsRubUi(Icons.Info, "À propos", "Version 1.3.74"),
    )

    /** « Taille de l'interface : Grande » : toute la densité × 1,1 (comme ProvideUiScale au toucher). */
    @Composable
    private fun LargeUi(w: Int, h: Int, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        val ui = com.ultratv.tv.nativeapp.ui.design.UiSize.LARGE.touchScale
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * ui, base.fontScale),
            LocalUiWidthDp provides w / ui, LocalUiHeightDp provides h / ui, content = content,
        )
    }

    private fun show(w: Int, h: Int, font: Float, largeUi: Boolean, lang: AppLang = AppLang.French) {
        rule.setDetail(tv = false, width = w, height = h, lang = lang, fontScale = font) {
            val body: @Composable () -> Unit = {
                // Rail latéral de la tablette (88 dp) : les Réglages n'ont que le reste de la largeur.
                androidx.compose.foundation.layout.Row(Modifier.fillMaxSize().background(Ux.Bg)) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(88.dp))
                    Box(Modifier.weight(1f).fillMaxSize()) {
                    TabletSettingsScale {
                        SettingsTvLayout(
                            title = "Réglages", rubs = rubs, selected = 3, onSelect = {}, rubFocus = List(rubs.size) { FocusRequester() }, modalOpen = false,
                            header = { SettingsLevelRow(SettingsLevel.SIMPLE) {} }, tablet = true,
                        ) {
                            PaneTitle("Affichage", "Thème, langue, accueil et taille de l'interface.")
                            ThemePicker(AppTheme.FOREST, AccentChoice.DEFAULT, {}, title = "Thème")
                            SettingsCard {
                                PrefRow("Langue", "Français", androidx.compose.ui.Modifier.testTag(PREF_ROW_PROBE)) {}
                                SwitchPrefRow("Numéros des chaînes", true) {}
                                SwitchPrefRow("Rubrique Sport", false, hint = "Matchs en direct et à venir, dans le menu et à l'accueil") {}
                                PrefRow("Ouvrir sur", "Dernier écran", hint = "Écran affiché au lancement de l'application") {}
                            }
                            GroupLabel("Personnalisation")
                            SettingsCard {
                                PrefRow("Couleur d'accent", "Thème") {}
                                PrefRow("Taille de l'interface", "Grande") {}
                                PrefRow("Taille du texte", "Très grande") {}
                            }
                        }
                    }
                    }
                }
            }
            if (largeUi) LargeUi(w, h, body) else body()
        }
    }

    private fun check(w: Int, h: Int, font: Float, largeUi: Boolean, png: String? = null, lang: AppLang = AppLang.French) {
        val ctx = "${w}x$h police $font ${if (largeUi) "interface Grande" else ""} ${lang.code}"
        show(w, h, font, largeUi, lang)
        // Mesures en dp de l'ÉCRAN (densité de la racine) : le contenu des Réglages est, lui, rendu à une densité agrandie.
        val rootDensity = rule.onRoot().fetchSemanticsNode().layoutInfo.density.density
        fun node(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode()
        // Deux volets : rubriques d'un côté, aperçus des thèmes (contenu) de l'autre.
        val rub = node(SETTINGS_RUB_TAG + 0).boundsInRoot
        val theme = node(AppearancePicker.THEME_TAG + AppTheme.FOREST.name).boundsInRoot
        assertTrue("$ctx : rubrique visible", rub.width > 0f)
        for (i in rubs.indices) {
            val hDp = node(SETTINGS_RUB_TAG + i).size.height / rootDensity
            assertTrue("$ctx : rubrique $i haute de $hDp dp (≥ 56 dp attendus)", hDp >= 56f)
        }
        val rowDp = node(PREF_ROW_PROBE).size.height / rootDensity
        assertTrue("$ctx : ligne de réglage haute de $rowDp dp (≥ 56 dp attendus)", rowDp >= 56f)
        assertTrue("$ctx : volet des rubriques ≤ 45 % de la largeur", rub.width / rootDensity <= w * 0.45f)
        assertTrue("$ctx : aperçus des thèmes à côté des rubriques", if (lang.rtl) theme.right <= rub.left else theme.left >= rub.right)
        val cut = rule.ellipsizedTexts()
        assertTrue("$ctx : textes coupés $cut", cut.isEmpty())
        val words = rule.brokenWords()
        assertTrue("$ctx : mots coupés $words", words.isEmpty())
        png?.let { rule.savePng(it) }
    }

    @Test fun portrait_texte13_interfaceGrande() = check(800, 1280, 1.3f, largeUi = true, png = "settings-tablet-portrait-large.png")
    @Test fun paysage_texte13_interfaceGrande() = check(1280, 800, 1.3f, largeUi = true, png = "settings-tablet-landscape-large.png")
    @Test fun portrait_normal() = check(800, 1280, 1f, largeUi = false, png = "settings-tablet-portrait.png")
    @Test fun paysage_normal() = check(1280, 800, 1f, largeUi = false, png = "settings-tablet-landscape.png")
    @Test fun portrait_arabe_texte13() = check(800, 1280, 1.3f, largeUi = true, lang = AppLang.Arabic)
}
