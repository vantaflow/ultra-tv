package com.ultratv.tv.nativeapp.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Textes de l'interface coupés par « … » (garde-fou contre les régressions) : chaque libellé COURT de l'appli (textes
 * des ressources, dans les six langues) est affiché dans les composants les plus utilisés — ligne de réglage sur
 * téléphone (360 dp), ligne de choix sur TV — et tout libellé qui finit tronqué est signalé.
 *
 * Les débordements connus sont listés dans `src/test/resources/text-overflow-baseline.txt` : le test n'échoue que sur
 * un NOUVEAU débordement (traduction trop longue, composant rétréci). Liste à jour écrite dans
 * `build/outputs/text-overflow/found.txt` (à recopier dans le fichier de référence quand un débordement est accepté).
 * Les données des fournisseurs (noms de chaînes, titres) ne sont pas concernées : seuls les textes de l'appli le sont.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextOverflowTest {
    @get:Rule val rule = createComposeRule()

    private val langs = listOf(AppLang.French, AppLang.English, AppLang.Spanish, AppLang.Arabic, AppLang.Polish, AppLang.PortugueseBr)
    private val prefixes = listOf("ds_", "simple_", "pex_", "perso_", "cloud_", "uxv2_")

    /** Libellés courts (pas de phrase, pas de paramètre) : (nom de la ressource, texte). */
    private fun labels(lang: AppLang): List<Pair<String, String>> =
        R.string::class.java.fields.mapNotNull { f ->
            val name = f.name
            if (prefixes.none { name.startsWith(it) }) return@mapNotNull null
            val text = runCatching { I18n.string(lang, f.getInt(null)) }.getOrNull() ?: return@mapNotNull null
            text.takeIf { TextOverflow.isShortLabel(it) }?.let { name to it }
        }

    private fun clippedTexts(): List<String> {
        val nodes = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
        return nodes.mapNotNull { n ->
            val results = mutableListOf<TextLayoutResult>()
            n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val r = results.firstOrNull() ?: return@mapNotNull null
            val cut = r.lineCount > 0 && r.isLineEllipsized(r.lineCount - 1)
            if (cut) n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } else null
        }
    }

    private fun check(component: String, touch: Boolean, row: @androidx.compose.runtime.Composable (String) -> Unit) {
        I18n.init(ApplicationProvider.getApplicationContext())
        val found = sortedSetOf<String>()
        val langState = androidx.compose.runtime.mutableStateOf(langs.first())
        rule.setContent {
            val l = langState.value
            androidx.compose.runtime.key(l) {
                CompositionLocalProvider(LocalDs provides designStringsFor(l), LocalStrings provides stringsFor(l), LocalTouch provides touch) {
                    val items = androidx.compose.runtime.remember(l) { labels(l) }
                    Column(Modifier.background(Ux.Bg).verticalScroll(rememberScrollState())) { for ((_, t) in items) row(t) }
                }
            }
        }
        for (lang in langs) {
            rule.runOnIdle { langState.value = lang }
            rule.waitForIdle()
            val byText = labels(lang).groupBy({ it.second.trim() }, { it.first })
            val clipped = clippedTexts()
            println("TextOverflow $component ${lang.code} : ${byText.size} libellés, ${clipped.size} textes coupés")
            for (t in clipped) byText[t.trim()]?.forEach { name -> found += "${lang.code}|$component|$name" }
        }
        val out = File("build/outputs/text-overflow").also { it.mkdirs() }
        val report = File(out, "found-$component.txt")
        report.writeText(found.joinToString("\n", postfix = "\n"))
        val baseline = javaClass.classLoader!!.getResource("text-overflow-baseline.txt")?.readText().orEmpty()
            .lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        val fresh = found.filter { it !in baseline }
        if (fresh.isNotEmpty()) fail(
            "Nouveaux textes coupés par « … » (${fresh.size}) — raccourcir la traduction ou élargir le composant, " +
                "sinon les ajouter à text-overflow-baseline.txt :\n" + fresh.joinToString("\n"),
        )
    }

    /** Contrôle du détecteur : un libellé trop long dans 120 dp DOIT être signalé (sinon le garde-fou ne garde rien). */
    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
    @Test fun detecteurSignaleUnTexteCoupe() {
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(AppLang.French), LocalStrings provides stringsFor(AppLang.French)) {
                Column(Modifier.width(120.dp)) { ChoiceRow("Un libellé beaucoup trop long pour cette ligne étroite", selected = false, onClick = {}) }
            }
        }
        rule.waitForIdle()
        org.junit.Assert.assertEquals(listOf("Un libellé beaucoup trop long pour cette ligne étroite"), clippedTexts())
    }

    @Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
    @Test fun ligneDeReglage_telephone() = check("pref-phone", touch = true) { t -> PrefRow(t, "Auto") {} }

    /** Bouton sur une demi-largeur de téléphone (deux boutons côte à côte : « Tout activer / Tout désactiver »). */
    @Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
    @Test fun boutonDemiLargeur_telephone() = check("pill-half-phone", touch = true) { t -> Column(Modifier.width(160.dp)) { PillButton(t, {}, heightPx = 64, hPadPx = 24, fontPx = 22) } }

    @Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
    @Test fun ligneDeChoix_tv() = check("choice-tv", touch = false) { t -> Column(Modifier.width(560.dp)) { ChoiceRow(t, selected = false, onClick = {}) } }
}

/** Règles pures du garde-fou (testées à part). */
object TextOverflow {
    /** Libellé court d'interface : 1 à 32 caractères, ni paramètre (%), ni retour à la ligne, ni phrase (point final). */
    fun isShortLabel(s: String): Boolean = s.isNotBlank() && s.length <= 32 && '%' !in s && '\n' !in s && !s.trimEnd().endsWith('.')
}
