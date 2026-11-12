package com.ultratv.tv.nativeapp.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.design.Icons
import com.ultratv.tv.nativeapp.ui.design.PaneTitle
import com.ultratv.tv.nativeapp.ui.design.PrefRow
import com.ultratv.tv.nativeapp.ui.design.SettingsCard
import com.ultratv.tv.nativeapp.ui.design.SwitchPrefRow
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.common.design
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

/**
 * Rendu des Réglages v2 (disposition TV) avec des données fictives neutres, sombre et clair, écrit en PNG sous
 * `build/outputs/ui-v2/` (validation visuelle). Fenêtre 960 × 540 dp à 2,0 (= 1920 × 1080 px).
 * Rubrique Lecture ouverte, focus sur la 3e ligne du volet (comme la maquette).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class SettingsRenderTest {
    /** Langue de l'interface du rendu (variantes anglaises pour le README et le site en anglais). */
    private var lang: AppLang = AppLang.French
    @get:Rule val rule = createComposeRule()

    private val outDir = File("build/outputs/ui-v2").also { it.mkdirs() }

    @After fun resetTheme() { Ux.themeLight = false }

    private val rubs = listOf(
        SettingsRubUi(Icons.List, "Sources", "3 sources · Xtream, M3U"),
        SettingsRubUi(Icons.Live, "Synchronisation", "Il y a 12 min"),
        SettingsRubUi(Icons.Guide, "Catégories", "120 actives · 8 désactivées"),
        SettingsRubUi(Icons.Settings, "Affichage", "Thème sombre · Sport : désactivé"),
        SettingsRubUi(Icons.Play, "Lecture", "Moteur, qualité, direct"),
        SettingsRubUi(Icons.Account, "Contrôle parental", "Code et chaînes verrouillées"),
        SettingsRubUi(Icons.Globe, "Langues", "Français"),
        SettingsRubUi(Icons.Heart, "Profils", "Qui regarde, code, enfants"),
        SettingsRubUi(Icons.Record, "Mémoire et stockage", "Images, base, espace libre"),
        SettingsRubUi(Icons.Search, "Messages", "Annonces reçues de l'équipe"),
        SettingsRubUi(Icons.Info, "À propos", "Version 1.2.97"),
    )

    private fun render(light: Boolean, file: String) {
        I18n.init(ApplicationProvider.getApplicationContext())
        Ux.themeLight = light
        Ux.playerActive = false
        val focusRow = FocusRequester()
        val rubFocus = List(rubs.size) { FocusRequester() }
        rule.setContent {
            val D = designStringsFor(lang)
            CompositionLocalProvider(LocalDs provides D, LocalStrings provides stringsFor(lang)) {
                // Mode clavier (télécommande) : sans lui, les éléments cliquables ne prennent pas le focus.
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                Column(Modifier.fillMaxSize().background(Ux.Bg)) {
                    SettingsTvLayout(
                        title = "Réglages", rubs = rubs, selected = 4, onSelect = {}, rubFocus = rubFocus, modalOpen = true,
                    ) {
                        PaneTitle("Lecture", "Moteur, décodage, qualité et comportement du direct.")
                        SettingsCard {
                            PrefRow("Moteur de lecture", "Automatique", hint = "Automatique : le meilleur moteur est choisi, avec repli si besoin") {}
                            PrefRow("Surface vidéo", "Automatique", hint = "SurfaceView recommandée pour le HDR et la 4K") {}
                            SwitchPrefRow("Aperçu vidéo dans le Direct", true, Modifier.focusRequester(focusRow), hint = "La chaîne sélectionnée se lit à côté de la liste") {}
                            SwitchPrefRow("Son de l'aperçu", false) {}
                            SwitchPrefRow("Fréquence d'image automatique", true, hint = "Adapte l'écran à la vidéo (24, 25, 50 ou 60 images/s)") {}
                            PrefRow("Mémoire tampon", "Équilibrée") {}
                            PrefRow("Sous-titres", "Moyens", hint = "Taille, couleur, fond") {}
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.ShiftLeft) }
        rule.waitForIdle()
        rule.runOnIdle { focusRow.requestFocus() }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, file).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("Réglages capture : ${File(outDir, file).absolutePath} (${bmp.width}x${bmp.height})")
        assertEquals(1920, bmp.width); assertEquals(1080, bmp.height)
        assertTrue(File(outDir, file).length() > 10_000)
    }

    @Test fun capture_tv_fr_sombre() { render(false, "settings-tv-fr-dark.png") }
    @Test fun capture_tv_fr_clair() { render(true, "settings-tv-fr-light.png") }

    @Test fun capture_tv_en_sombre() { lang = AppLang.English; render(false, "settings-tv-en-dark.png") }
}
