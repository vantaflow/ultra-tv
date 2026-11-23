package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.scaledDensity
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.PaneTitle
import com.ultratv.tv.nativeapp.ui.design.Ux
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Carte « Niveau des réglages » en tête de la colonne des rubriques (TV 1920 × 1080), en fr / en / es / ar, à toutes les
 * tailles de texte (1 / 1,15 / 1,3) et aux tailles d'interface extrêmes :
 * - la description garde un interligne normal (avant : celui du style par défaut, un grand vide entre les deux lignes) et
 *   n'est jamais coupée par « … » ;
 * - la carte reste sous le titre, avec la marge du haut, même quand la dernière rubrique (« À propos ») a le focus ;
 * - les rubriques défilent : la dernière, focalisée, est entièrement visible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsLevelRowRenderTest {
    @get:Rule val rule = createComposeRule()

    private val rubs = listOf(
        SettingsRubUi(Icons.List, "Sources", "3 sources · Xtream Codes, Jellyfin, Plex"),
        SettingsRubUi(Icons.Live, "Synchronisation", "Il y a 4 h"),
        SettingsRubUi(Icons.Guide, "Catégories", "135 actives · 1549 désactivées"),
        SettingsRubUi(Icons.Settings, "Affichage", "Sombre · Sport : Désactivé"),
        SettingsRubUi(Icons.Play, "Lecture", "Moteur, qualité, direct"),
        SettingsRubUi(Icons.Account, "Contrôle parental", "Code et chaînes verrouillées"),
        SettingsRubUi(Icons.Globe, "Langues", "Français"),
        SettingsRubUi(Icons.Heart, "Profils", "Favoris, historique et affichage"),
        SettingsRubUi(Icons.Record, "Mémoire et stockage", "Libérer de la place"),
        SettingsRubUi(Icons.Search, "Messages", "Annonces reçues de l'équipe."),
        SettingsRubUi(Icons.Info, "LAST-RUB", "Version 1.3.73"),
    )

    private class Case(val lang: AppLang, val text: Float, val ui: Float)

    @Test fun carte_niveau_interligne_marge_et_defilement() {
        I18n.init(ApplicationProvider.getApplicationContext())
        Ux.themeLight = false
        Ux.playerActive = false
        val rubFocus = List(rubs.size) { FocusRequester() }
        var case by mutableStateOf(Case(AppLang.French, 1f, 1f))
        rule.setContent {
            val c = case
            val base = LocalDensity.current
            val density: Density = scaledDensity(tv = true, heightPx = 1080, base = base, textScale = c.text, ui = c.ui)
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalDs provides designStringsFor(c.lang), LocalStrings provides stringsFor(c.lang),
                LocalLayoutDirection provides if (c.lang == AppLang.Arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                // Thème de l'appli : il fournit le style de texte par défaut (interligne trop haut) qui causait le grand vide.
                com.ultratv.tv.nativeapp.ui.theme.UltraTvTheme {
                    Column(Modifier.fillMaxSize().background(Ux.Bg)) {
                        SettingsTvLayout(
                            title = "Réglages", rubs = rubs, selected = 6, onSelect = {}, rubFocus = rubFocus, modalOpen = true,
                            header = { SettingsLevelRow(SettingsLevel.SIMPLE) {} },
                        ) { PaneTitle("Langues", "Langue de l'application et des contenus.") }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }

        val langs = listOf(AppLang.French, AppLang.English, AppLang.Spanish, AppLang.Arabic)
        for (lang in langs) for (text in listOf(1f, 1.15f, 1.3f)) for (ui in listOf(0.92f, 1f, 1.12f)) {
            val label = "${lang.name} texte=$text interface=$ui"
            rule.runOnIdle { case = Case(lang, text, ui) }
            rule.waitForIdle()

            // 1) Interligne normal, aucune coupure « … ».
            val layout = hintLayout()
            val fontPx = 17f / 2f * text * 2f * ui // 17.spx en px pour une densité 2 × ui et une police × texte
            for (line in 0 until layout.lineCount) {
                val h = layout.getLineBottom(line) - layout.getLineTop(line)
                assertTrue("$label : interligne ${h}px pour un texte de ${fontPx}px", h <= fontPx * 1.5f)
            }
            assertTrue("$label : description coupée", !layout.isLineEllipsized(layout.lineCount - 1))

            // Positions en px (racine) : les dp des nœuds dépendent de la densité de la taille d'interface.
            // 2) La carte reste sous le titre (marge du haut), même avec le focus sur la dernière rubrique.
            rule.runOnIdle { rubFocus.last().requestFocus() }
            rule.waitForIdle()
            val title = rule.onNodeWithTag(SETTINGS_TITLE_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val card = rule.onNodeWithTag(SETTINGS_LEVEL_TAG).fetchSemanticsNode().boundsInRoot
            assertTrue("$label : titre hors de la marge du haut (${title.top})", title.top >= 40f)
            assertTrue("$label : carte collée au bord haut (${card.top} ≤ titre ${title.bottom})", card.top >= title.bottom)

            // 3) Les rubriques défilent : la dernière, focalisée, est entièrement visible dans la colonne.
            // (Pas d'étiquette de test sur la liste : elle deviendrait l'étiquette du focus dans SettingsRtlFocusTest.)
            val screen = rule.onRoot().fetchSemanticsNode().boundsInRoot
            val last = rule.onNodeWithText("LAST-RUB", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$label : dernière rubrique hors de l'écran (${last.top}..${last.bottom} / carte ${card.bottom}, écran ${screen.bottom})",
                last.top >= card.bottom && last.bottom <= screen.bottom + 1f)
            rule.runOnIdle { rubFocus.first().requestFocus() }
            rule.waitForIdle()
        }
    }

    private fun hintLayout(): TextLayoutResult {
        val node = rule.onNodeWithTag(SETTINGS_LEVEL_HINT_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val out = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.first()
    }
}
