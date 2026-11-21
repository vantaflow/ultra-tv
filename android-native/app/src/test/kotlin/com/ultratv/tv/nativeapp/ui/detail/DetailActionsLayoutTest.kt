package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.ultratv.tv.nativeapp.i18n.traktAddWatchlist
import com.ultratv.tv.nativeapp.i18n.traktMarkWatched
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.i18n.trailerLabel
import com.ultratv.tv.nativeapp.i18n.traktMarkUnwatched
import com.ultratv.tv.nativeapp.i18n.traktRemoveWatchlist
import com.ultratv.tv.nativeapp.i18n.traktYourRating
import com.ultratv.tv.nativeapp.ui.design.PillButton
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.downloads.DownloadStrings
import com.ultratv.tv.nativeapp.ui.mobile.MobileStrings
import com.ultratv.tv.nativeapp.ui.trakt.TraktMovieActionsRow
import com.ultratv.tv.nativeapp.ui.trakt.TraktMovieTilesRow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fiche film TV : les boutons d'action (Lecture, Depuis le début, Bande-annonce, favori, Télécharger) et les gestes Trakt
 * (watchlist, vu / non vu, note) affichent TOUJOURS leur libellé entier. Constat sur la box : dans la colonne de gauche de la
 * fiche, « Noter » était écrasé jusqu'à ne garder que son étoile. Libellés les plus longs, 4 langues, texte à 1,3, TV 1080p
 * et 720p (densité normalisée : même largeur de 960 dp). Aucun ViewModel : rangées sans état branchées dans la vraie fiche.
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)   // vraies polices : vraies largeurs de texte
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class DetailActionsLayoutTest {
    @get:Rule val rule = createComposeRule()

    @After fun reset() { Ux.textScale = 1f }

    /** Libellés Trakt les plus longs, écrits à la main (et vérifiés contre les ressources). */
    private class Longest(val lang: AppLang, val watchlist: String, val unwatched: String, val rating: String)

    private val longest = listOf(
        Longest(AppLang.French, "Retirer de ma watchlist Trakt", "Marquer comme non vu", "Ma note 10/10"),
        Longest(AppLang.Spanish, "Quitar de mi lista de Trakt", "Marcar como no visto", "Mi nota 10/10"),
        Longest(AppLang.Arabic, "إزالة من قائمة المشاهدة على Trakt", "وضع علامة كغير مُشاهَد", "تقييمي 10/10"),
        Longest(AppLang.English, "Remove from my Trakt watchlist", "Mark as unwatched", "My rating 10/10"),
    )

    private class Labels(val main: List<String>, val trakt: List<String>)

    private fun labelsFor(l: Longest): Labels {
        val D = com.ultratv.tv.nativeapp.i18n.DesignStrings(l.lang)
        assertEquals(l.watchlist, D.traktRemoveWatchlist)
        assertEquals(l.unwatched, D.traktMarkUnwatched)
        assertEquals(l.rating, D.traktYourRating(10))
        val dl = DownloadStrings(l.lang)
        val download = listOf(dl.download, dl.downloaded, dl.queued, dl.paused, dl.retry).maxBy { it.length }
        val main = listOf(D.resumeAt("1:31:12"), MobileStrings.forLang(l.lang, l.lang.code).fromStart, D.trailerLabel, download)
        return Labels(main, listOf(l.watchlist, l.unwatched, l.rating))
    }

    private val heart = "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"

    /** Langue affichée : une seule composition par test (createComposeRule), la langue change par état. */
    private val current = androidx.compose.runtime.mutableStateOf<Longest?>(null)
    private val currentScale = androidx.compose.runtime.mutableFloatStateOf(1f)
    private var composed = false

    private fun render(l: Longest, scale: Float) {
        I18n.init(ApplicationProvider.getApplicationContext())
        Ux.textScale = scale
        currentScale.floatValue = scale
        current.value = l
        if (!composed) { composed = true; rule.setContent { val cur = current.value; if (cur != null) Screen(cur, currentScale.floatValue) } }
        rule.waitForIdle()
    }

    @androidx.compose.runtime.Composable
    private fun Screen(l: Longest, scale: Float) {
        val labels = remember(l) { labelsFor(l) }
        run {
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDs provides designStringsFor(l.lang), LocalStrings provides stringsFor(l.lang),
                LocalDensity provides Density(d.density, d.fontScale * scale),
                LocalLayoutDirection provides if (l.lang == AppLang.Arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                val req = remember { FocusRequester() }
                MovieDetailTv(
                    m = MovieDetailTvModel(
                        title = "Orbite basse", backdrop = null, poster = null, chips = listOf("2024", "4K"), ratings = emptyList(),
                        synopsis = "Une ingénieure coincée dans une station orbitale.", skeleton = false,
                        primaryLabel = labels.main[0], fromStartLabel = labels.main[1], trailerLabel = labels.main[2],
                        progress = 0.25f, progressLine = null,
                        cast = listOf(CastUi("Nora Valin", "Elsa", null), CastUi("Tom Reyes", null, null)),
                        castTitle = "Distribution", backDescription = "Retour",
                    ),
                    onPrimary = {}, onFromStart = {}, onTrailer = {}, onBack = {}, onOpenPerson = {},
                    playRequester = req, onPlayFocusChanged = {},
                    favorite = { V2IconButton(heart, "Favori", {}) },
                    // Même pilule que MovieDownloadPill (72 / 26), libellé d'état le plus long.
                    actionsExtra = { PillButton(labels.main[3], {}, heightPx = 72, fontPx = 26, iconPath = com.ultratv.tv.nativeapp.ui.downloads.DownloadIcons.Download) },
                    afterActions = {
                        TraktMovieActionsRow(
                            watchlistLabel = labels.trakt[0], inWatchlist = true, onWatchlist = {},
                            watchedLabel = labels.trakt[1], onWatched = {},
                            rateLabel = labels.trakt[2], rated = true, onRate = {},
                        )
                    },
                )
            }
        }
    }

    private fun textNode(label: String): SemanticsNode =
        rule.onAllNodes(SemanticsMatcher("texte « $label »") { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true }, useUnmergedTree = true)
            .fetchSemanticsNodes().also { assertTrue("libellé « $label » absent", it.isNotEmpty()) }.first()

    private fun layoutOf(n: SemanticsNode): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        n.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        return out.first()
    }

    /** Bouton (nœud focalisable) qui porte le libellé. */
    private fun buttonOf(n: SemanticsNode): SemanticsNode {
        var cur: SemanticsNode? = n
        while (cur != null && cur.config.getOrNull(SemanticsProperties.Focused) == null) cur = cur.parent
        return assertNotNull("bouton du libellé", cur).let { cur!! }
    }

    /** Chaque libellé est affiché ENTIER : pas d'ellipse, largeur naturelle du texte ≤ largeur dont il dispose dans son bouton. */
    private fun assertWhole(labels: List<String>, ctx: String, rtl: Boolean = false) {
        // Marge 72 px + colonne 880 px de la maquette (en dp), côté début de ligne (à droite en arabe).
        val w = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val start = with(rule.density) { 36.dp.toPx() }; val end = with(rule.density) { (36 + 440).dp.toPx() }
        val colLeft = if (rtl) w - end else start
        val colRight = if (rtl) w - start else end
        for (label in labels) {
            val n = textNode(label)
            val lay = layoutOf(n)
            val natural = lay.multiParagraph.intrinsics.maxIntrinsicWidth
            val box = n.boundsInRoot
            assertTrue("$ctx « $label » : ellipse", (0 until lay.lineCount).none { lay.isLineEllipsized(it) })
            assertTrue("$ctx « $label » : texte coupé (largeur naturelle $natural > ${box.width})", natural <= box.width + 1f && !lay.hasVisualOverflow)
            val b = buttonOf(n).boundsInRoot
            assertTrue("$ctx « $label » : texte hors de son bouton ($box dans $b)", box.left >= b.left - 1f && box.right <= b.right + 1f)
            assertTrue("$ctx « $label » : bouton hors de la colonne ($b)", b.left >= colLeft - 1f && b.right <= colRight + 1f)
        }
    }

    private fun checkAll(scale: Float, ctx: String) {
        for (l in longest) {
            render(l, scale)
            val labels = labelsFor(l)
            assertWhole(labels.main + labels.trakt, "$ctx ${l.lang.code} x$scale", rtl = l.lang == AppLang.Arabic)
        }
    }

    @Test fun tv1080p_texte13_libellesEntiers_4langues() = checkAll(1.3f, "1080p")

    @Test @Config(qualifiers = "w960dp-h540dp-tvdpi")
    fun tv720p_texte13_libellesEntiers_4langues() = checkAll(1.3f, "720p")

    @Test fun tv1080p_texteNormal_libellesEntiers_4langues() = checkAll(1f, "1080p")

    // ───────────── Télécommande : parcours sans piège quand la rangée Trakt passe sur deux lignes ─────────────

    private fun focusedText(): String {
        val n = rule.onAllNodes(SemanticsMatcher("focalisé") { it.config.getOrNull(SemanticsProperties.Focused) == true }).fetchSemanticsNodes().single()
        return (n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
            ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()).orEmpty()
    }

    private fun press(k: Key) { rule.onRoot().performKeyInput { pressKey(k) }; rule.waitForIdle() }

    private fun focus(label: String) {
        rule.onAllNodes(SemanticsMatcher("bouton « $label »") { n ->
            n.config.getOrNull(SemanticsProperties.Focused) != null && n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
        }).fetchSemanticsNodes().single().let { node ->
            rule.onAllNodes(SemanticsMatcher("id") { it.id == node.id })[0].performSemanticsAction(SemanticsActions.RequestFocus)
        }
        rule.waitForIdle()
        assertEquals(label, focusedText())
    }

    private fun bounds(label: String): Rect = buttonOf(textNode(label)).boundsInRoot

    @Test fun telecommande_rangeeTraktSurDeuxLignes_parcoursSansPiege() {
        val l = longest.first { it.lang == AppLang.Spanish }
        render(l, 1.3f)
        val lab = labelsFor(l)
        val t = lab.trakt
        // Lignes réelles de la rangée Trakt (par position verticale).
        val lines = t.groupBy { bounds(it).top.toInt() }.toSortedMap().values.toList()
        assertTrue("la rangée Trakt passe sur au moins deux lignes à 1,3 en espagnol : $lines", lines.size >= 2)
        // ◀ ▶ dans une ligne.
        for (line in lines) for (i in 0 until line.size - 1) {
            focus(line[i]); press(Key.DirectionRight); assertEquals(line[i + 1], focusedText())
            press(Key.DirectionLeft); assertEquals(line[i], focusedText())
        }
        // ▼ d'une ligne à la suivante, ▲ retour sur la ligne du dessus.
        for (k in 0 until lines.size - 1) {
            focus(lines[k].first()); press(Key.DirectionDown); assertTrue("▼ ligne ${k + 1} → ${focusedText()}", focusedText() in lines[k + 1])
            press(Key.DirectionUp); assertTrue("▲ revient sur la ligne ${k + 1} → ${focusedText()}", focusedText() in lines[k])
        }
        // ▲ depuis la première ligne Trakt : un bouton de la rangée d'actions ; ▼ depuis la dernière : la distribution.
        focus(lines.first().first()); press(Key.DirectionUp)
        val up = focusedText()
        assertTrue("▲ vers les boutons de lecture, obtenu « $up »", up in lab.main || up == "Favori")
        focus(lines.last().first()); press(Key.DirectionDown)
        assertTrue("▼ vers la distribution, obtenu « ${focusedText()} »", focusedText().contains("Nora Valin") || focusedText().contains("Tom Reyes"))
        press(Key.DirectionUp)
        assertTrue("▲ depuis la distribution revient sur Trakt, obtenu « ${focusedText()} »", focusedText() in t)
    }

    @Test fun telecommande_focusGardeQuandLeLibelleChange() {
        // Le bouton focalisé garde le focus quand son libellé change (watchlist ajoutée → « Retirer… », la rangée se réorganise).
        I18n.init(ApplicationProvider.getApplicationContext())
        val D = com.ultratv.tv.nativeapp.i18n.DesignStrings(AppLang.French)
        val state = androidx.compose.runtime.mutableStateOf(false)
        Ux.textScale = 1.3f
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDs provides D, LocalDensity provides Density(d.density, d.fontScale * 1.3f)) {
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
                Box(Modifier.width(440.dp)) {
                    TraktMovieActionsRow(
                        watchlistLabel = if (state.value) "Retirer de ma watchlist Trakt" else "Ajouter à ma watchlist Trakt", inWatchlist = state.value, onWatchlist = { state.value = !state.value },
                        watchedLabel = "Marquer comme non vu", onWatched = {}, rateLabel = "Ma note 10/10", rated = true, onRate = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        focus("Ajouter à ma watchlist Trakt")
        press(Key.DirectionCenter)
        assertEquals("Retirer de ma watchlist Trakt", focusedText())
        press(Key.DirectionCenter)
        assertEquals("Ajouter à ma watchlist Trakt", focusedText())
    }


    // ───────────── Fiche série : bouton à étoile seule (voulu) ─────────────

    @Test fun serie_boutonEtoile_decritPourLeLecteurDEcran_tailleDuCoeur() {
        I18n.init(ApplicationProvider.getApplicationContext())
        rule.setContent {
            androidx.compose.foundation.layout.Row {
                V2IconButton(heart, "Favori", {})
                com.ultratv.tv.nativeapp.ui.trakt.TraktSeriesIconButton(rated = true, description = "Trakt", onClick = {})
            }
        }
        rule.waitForIdle()
        val star = rule.onAllNodes(SemanticsMatcher("étoile") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Trakt") == true }).fetchSemanticsNodes().single()
        val fav = rule.onAllNodes(SemanticsMatcher("cœur") { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Favori") == true }).fetchSemanticsNodes().single()
        assertTrue("l'étoile se focalise à la télécommande", star.config.getOrNull(SemanticsProperties.Focused) != null)
        assertEquals("même cible que le cœur", fav.boundsInRoot.size, star.boundsInRoot.size)
        assertTrue("cible ≥ 32 dp", star.boundsInRoot.width >= with(rule.density) { 32.dp.toPx() } - 0.5f)
    }

    // ───────────── Tactile : trois tuiles, libellés sur plusieurs lignes au lieu d'être coupés ─────────────

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun tactile_tuilesTrakt_libellesEntiers_4langues() {
        I18n.init(ApplicationProvider.getApplicationContext())
        val lang = androidx.compose.runtime.mutableStateOf(longest.first())
        rule.setContent {
            val l = lang.value
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDs provides designStringsFor(l.lang), LocalDensity provides Density(d.density, d.fontScale * 1.3f),
                LocalLayoutDirection provides if (l.lang == AppLang.Arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                // Libellés tactiles : « Ajouter à ma watchlist » (le plus long de la tuile), « Marquer comme vu », la note.
                Box(Modifier.padding(16.dp)) {
                    TraktMovieTilesRow(tileLabels(l)[0], false, {}, tileLabels(l)[1], false, {}, tileLabels(l)[2], true, {})
                }
            }
        }
        for (l in longest) {
            lang.value = l
            rule.waitForIdle()
            for (label in tileLabels(l)) {
                val n = textNode(label)
                val lay = layoutOf(n)
                assertTrue("tactile ${l.lang.code} « $label » : ellipse", (0 until lay.lineCount).none { lay.isLineEllipsized(it) } && !lay.hasVisualOverflow)
            }
        }
    }

    private fun tileLabels(l: Longest): List<String> {
        val D = com.ultratv.tv.nativeapp.i18n.DesignStrings(l.lang)
        return listOf(D.traktAddWatchlist, D.traktMarkWatched, l.rating)
    }
}
