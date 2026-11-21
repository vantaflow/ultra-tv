package com.ultratv.tv.nativeapp.ui.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.live.DirectCategory
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * Compteur de recompositions du Guide TV rejoué à la télécommande (▶ ◀ ▼ ▲ et franchissement du bord de la fenêtre).
 * La sonde ([GuideProbe]) ne compte que ce que le code exécute : un NOMBRE de recompositions, pas une durée.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class GuideRecompositionTest {
    @get:Rule val rule = createComposeRule()

    private fun at(h: Int, m: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val nChannels = 16
    private val chans = (1..nChannels).map { ChannelEntity(id = it.toLong(), providerId = 1, remoteId = "$it", name = "Chaîne $it", logo = null, categoryId = "c", streamUrl = "u", num = it) }

    /** Chaînes sans aucun programme entre 00:00 et 06:00 (donc sans case dans la fenêtre 01:00 – 04:00), mais avec une liste non vide. */
    private var hole: Set<Long> = emptySet()

    /** Programmes de 40 à 90 minutes, sur 3 jours à partir de 00:00, titres « Pc-i » uniques. */
    private val all: Map<Long, List<EpgEntity>> by lazy {
        buildAll().mapValues { (id, l) ->
            (if (id in hole) l.filter { it.endMs <= at(0, 0) || it.startMs >= at(6, 0) } else l).let { r -> if (id in shortGuide) r.filter { it.startMs < at(5, 0) } else r }
                .let { r -> if (id in noTomorrow) r.filter { it.startMs < at(0, 0) + 24 * 3_600_000L } else r }
        }
    }
    /** Chaînes dont le guide s'arrête au premier programme commençant après 05:00 (fin des données). */
    private var shortGuide: Set<Long> = emptySet()
    /** Chaînes sans aucun programme le lendemain. */
    private var noTomorrow: Set<Long> = emptySet()
    /** Changement de jour au clavier : programmes lus plus tard (comme la requête Room), pas dans le même rappel. */
    private var asyncDay = false
    private var readyWindow by mutableStateOf<Long?>(null)

    private fun buildAll(): Map<Long, List<EpgEntity>> = chans.associate { c ->
        val out = ArrayList<EpgEntity>()
        var t = at(0, 0); var i = 1
        val lens = listOf(50, 70, 40, 60, 90, 45)
        while (t < at(0, 0) + 72 * 3_600_000L) {
            val len = lens[(i + c.id.toInt()) % lens.size] * 60_000L
            out += EpgEntity(id = c.id * 1000 + i, channelId = c.id, title = "P${c.id}-$i", description = null, startMs = t, endMs = t + len)
            t += len; i++
        }
        c.id to out
    }

    /** Programmes d'une chaîne qui touchent [from, to] (comme la requête Room `rangeForChannels`). */
    private fun rangeOf(id: Long, from: Long, to: Long) = all[id].orEmpty().filter { it.endMs >= from && it.startMs <= to }

    private val probe = GuideProbe()
    private var windowStart by mutableStateOf(at(1, 0))
    private var programmes by mutableStateOf<Map<Long, List<EpgEntity>>>(emptyMap())
    private var watched = ""
    /** Comme System.currentTimeMillis() : une valeur différente à chaque lecture, dans la même minute (01:31). */
    private var nowTick = 0L
    private var nowBase = at(1, 31)
    private val clock = mutableLongStateOf(at(1, 31))
    private var day by mutableStateOf(0)
    private var dayRequests = 0
    private var rtl = false

    // ── Modèle de vue simulé : la même logique que GuideGridViewModel (cache par plage, plages manquantes seulement). ──
    private val cache = GuideProgrammeCache()
    private var lastVisible = emptyList<Long>()

    private fun vmOnVisible(visibleIds: List<Long>) {
        if (visibleIds.isEmpty()) return
        val (from, to) = guideNeededRange(windowStart)
        val missing = cache.missing(visibleIds, from, to)
        if (missing.isEmpty()) { readyWindow = windowStart; return }
        val (fFrom, fTo) = guideFetchRange(windowStart)
        val rows = missing.flatMap { rangeOf(it, fFrom, fTo) }
        if (cache.put(missing, fFrom, fTo, rows)) programmes = cache.snapshot()
        readyWindow = windowStart
    }

    /** Comme GuideGridViewModel.covers : les programmes des lignes visibles couvrent déjà la fenêtre [w]. */
    private fun covers(w: Long): Boolean { val (f, t) = guideNeededRange(w); return lastVisible.isEmpty() || cache.missing(lastVisible, f, t).isEmpty() }

    private fun mount() {
        I18n.init(ApplicationProvider.getApplicationContext())
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(AppLang.French), LocalStrings provides stringsFor(AppLang.French), LocalTouch provides false,
                LocalGuideProbe provides probe,
                androidx.compose.ui.platform.LocalLayoutDirection provides if (rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
                val D = LocalDs.current
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                Box(Modifier.fillMaxSize().padding(start = 48.design)) {
                    GuideTvContent(
                        channelCount = chans.size, channelAt = { chans[it] }, peekAt = { chans[it] }, itemKey = { chans[it].id },
                        progsFor = { programmes[it].orEmpty() }, loadedFor = { programmes.containsKey(it) }, windowStart = windowStart, day = day, D = D, clock = clock,
                        groups = listOf(DirectCategory(CATEGORY_ALL, null, chans.size)), group = CATEGORY_ALL, guideLoad = GuideLoad.Idle, nameColPx = 260,
                        groupName = { "Toutes" },
                        nowProvider = { nowBase + (nowTick++ % 1_000) },
                        onDay = { d -> dayRequests++; day = d; windowStart = if (d == 0) at(1, 0) else at(20, 0); vmOnVisible(lastVisible) },
                        onDayShift = { d, w -> day = d; windowStart = w; if (!asyncDay) vmOnVisible(lastVisible) },
                        readyFor = { w -> covers(w) || readyWindow == w },
                        onWindowStart = { windowStart = it; vmOnVisible(lastVisible) }, onGroup = {},
                        onVisible = { ids -> lastVisible = ids; vmOnVisible(ids) },
                        onRetry = {},
                        onSelect = { _, _ -> }, onWatch = { watched = it.name }, onRemind = { _, _ -> }, onRecord = { _, _ -> },
                        preview = { sel, m ->
                            // Même lecture que GuidePreviewPane : seule la CHAÎNE focalisée recompose l'aperçu.
                            ProbePreview()
                            val ch = rememberFocusedChannel(sel, null).value
                            Box(m) { Text(ch?.name ?: "—") }
                        },
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { keyDown(Key.ShiftLeft); keyUp(Key.ShiftLeft) }
        rule.waitForIdle()
    }

    private fun press(k: Key) {
        rule.onRoot().performKeyInput { keyDown(k); keyUp(k) }
        rule.waitForIdle()
    }

    private fun focusedText(): String = runCatching {
        rule.onNode(isFocused()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" / ") { it.text } ?: "(focus sans texte)"
    }.getOrDefault("(aucun focus)")

    private fun assertFocusOn(title: String) {
        try { rule.onNode(isFocused()).assertTextContains(title) }
        catch (e: AssertionError) { throw AssertionError("focus attendu sur « $title », trouvé : ${focusedText()}", e) }
    }

    private fun focusOn(title: String) {
        rule.onNode(hasText(title) and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        rule.waitForIdle()
        probe.clear()
    }

    /** Compteurs d'un seul appui, et nombre d'images (frames) qui ont recomposé quelque chose. */
    private class Snap(val rows: Map<Long, Int>, val cells: Map<Long, Int>, val header: Int, val buttons: Int, val preview: Int, val screen: Int, val frames: Int, val focus: String, val detail: String = "") {
        val rowTotal get() = rows.values.sum()
        val cellTotal get() = cells.values.sum()
        override fun toString() = "images=$frames[$detail] écran=$screen lignes=$rowTotal(${rows.size}) cases=$cellTotal(${cells.size}) en-tête=$header boutons=$buttons aperçu=$preview -> focus : $focus"
    }

    private fun total() = probe.screen + probe.rowTotal + probe.cellTotal + probe.header + probe.preview

    /**
     * Joue [key] et rend les compteurs de CET appui. L'horloge est avancée image par image : [Snap.frames] est le nombre
     * d'images qui ont recomposé quelque chose (une sélection écrite « une image plus tard » en ajoute une).
     */
    private fun gesture(name: String, key: Key): Snap {
        probe.clear()
        rule.mainClock.autoAdvance = false
        rule.onRoot().performKeyInput { keyDown(key); keyUp(key) }
        var frames = 0
        val detail = StringBuilder()
        var prev = intArrayOf(0, 0, 0, 0, 0)
        fun cur() = intArrayOf(probe.screen, probe.rowTotal, probe.cellTotal, probe.header, probe.preview)
        fun frame() {
            val c = cur()
            if (!c.contentEquals(prev)) {
                frames++
                detail.append(if (detail.isEmpty()) "" else " | ")
                    .append(listOf("é", "l", "c", "h", "a").indices.filter { c[it] != prev[it] }.joinToString(" ") { "${listOf("é", "l", "c", "h", "a")[it]}${c[it] - prev[it]}" })
            }
            prev = c
        }
        repeat(40) { rule.mainClock.advanceTimeByFrame(); frame() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        frame()
        val snap = Snap(HashMap(probe.rows), HashMap(probe.cells), probe.header, probe.buttons, probe.preview, probe.screen, frames, focusedText(), detail.toString())
        println("COMPTEURS $name : $snap")
        return snap
    }

    // ── Relevé : les compteurs de chaque geste sont affichés (« COMPTEURS … ») ; seul le déplacement du focus est vérifié. ──

    @Test fun releve_droite_gauche_dans_la_ligne() {
        mount()
        focusOn("P1-2")
        gesture("▶ dans la ligne", Key.DirectionRight)
        assertFocusOn("P1-3")
        gesture("◀ dans la ligne", Key.DirectionLeft)
        assertFocusOn("P1-2")
    }

    @Test fun releve_bas_haut() {
        mount()
        focusOn("P1-2")
        for (i in 1..9) { gesture("▼ $i", Key.DirectionDown); assertTrue(focusedText().startsWith("P${i + 1}-")) }
        for (i in 1..2) { gesture("▲ $i", Key.DirectionUp); assertTrue(focusedText().startsWith("P${9 - i + 1}-")) }
    }

    @Test fun releve_bord_de_fenetre() {
        mount()
        // Dernière case de la fenêtre 01:00 – 04:00 sur la ligne 4 : la case qui touche 04:00.
        val lastTitle = rangeOf(4, at(1, 0), at(4, 0)).last().title
        focusOn(lastTitle)
        gesture("▶ au bord de la fenêtre", Key.DirectionRight)
    }

    // ── Plafonds verrouillés (cause 2.1 : l'heure ne fait plus recomposer les lignes ; cause 3.1 : sélection écrite au focus). ──

    private fun channelOf(programId: Long) = programId / 1000

    @Test fun plafond_droite_gauche_dans_la_ligne_ne_touche_que_les_deux_cases() {
        mount()
        focusOn("P1-2")
        val r = gesture("▶ dans la ligne", Key.DirectionRight)
        assertEquals("aucune ligne recomposée", emptySet<Long>(), r.rows.keys)
        assertEquals("seulement la case quittée et la case atteinte", setOf(1002L, 1003L), r.cells.keys)
        assertEquals("l'écran n'est pas recomposé", 0, r.screen)
        assertEquals("en-tête : une fois", 1, r.header)
        assertEquals("même chaîne : l'aperçu n'est pas recomposé", 0, r.preview)
        val l = gesture("◀ dans la ligne", Key.DirectionLeft)
        assertEquals(emptySet<Long>(), l.rows.keys)
        assertEquals(setOf(1002L, 1003L), l.cells.keys)
        assertEquals(1, l.header)
        assertEquals(0, l.preview)
    }

    @Test fun plafond_bas_haut_ne_touche_que_les_deux_cases_et_la_ligne_qui_entre() {
        mount()
        focusOn("P1-2")
        var from = focusedId()
        for (i in 1..9) {
            val r = gesture("▼ $i", Key.DirectionDown)
            val to = focusedId()
            assertTrue("au plus une ligne recomposée (celle qui entre) : ${r.rows.keys}", r.rows.size <= 1)
            // Toute case recomposée est l'ancienne, la nouvelle, ou appartient à la ligne qui entre.
            val stray = r.cells.keys.filter { it != from && it != to && channelOf(it) !in r.rows.keys }
            assertEquals("cases recomposées hors du plan : $stray", emptyList<Long>(), stray)
            assertEquals("en-tête : une fois", 1, r.header)
            assertEquals("changement de chaîne : l'aperçu une fois", 1, r.preview)
            from = to
        }
        for (i in 1..3) {
            val r = gesture("▲ $i", Key.DirectionUp)
            val to = focusedId()
            assertEquals("▲ dans les lignes déjà composées : aucune ligne", emptySet<Long>(), r.rows.keys)
            assertEquals(setOf(from, to), r.cells.keys)
            assertEquals(1, r.header)
            assertEquals(1, r.preview)
            from = to
        }
    }

    private fun focusedId(): Long {
        val t = focusedText().substringBefore(" / ")   // « Pc-i »
        val (c, i) = t.removePrefix("P").split("-").map { it.toLong() }
        return c * 1000 + i
    }

    // ── Cause 1 : bord de la fenêtre. Le focus reste sur la même chaîne, sur le programme qui suit (ou précède) dans le temps. ──

    private fun windowOf(id: Long) = rangeOf(id, at(1, 0), at(4, 0))

    @Test fun plafond_bord_droit_decale_la_fenetre_et_garde_le_focus_sur_la_meme_chaine() {
        mount()
        val inWindow = windowOf(4)
        val last = inWindow.last()
        val next = all.getValue(4).first { it.startMs >= last.endMs }
        focusOn(last.title)
        val r = gesture("▶ au bord de la fenêtre", Key.DirectionRight)
        assertFocusOn(next.title)
        assertEquals("la fenêtre a avancé de 90 min", at(2, 30), windowStart)
        assertTrue("chaque ligne se recompose une seule fois : ${r.rows}", r.rows.values.all { it == 1 })
        assertTrue("l'écran se recompose une seule fois (${r.screen})", r.screen <= 1)
        assertEquals("aucune chaîne n'a disparu ni changé de ligne", 7, r.rows.size.coerceAtMost(7))
        // Encore dix appuis sur ▶ : la fenêtre se décale au besoin, le focus avance dans le temps sans quitter la chaîne 4.
        var prevId = focusedId()
        repeat(10) { n ->
            press(Key.DirectionRight)
            println("COMPTEURS ▶ suite $n : ${focusedText()} (fenêtre ${windowStart})")
            val id = focusedId()
            assertEquals("le focus a quitté la chaîne 4 : ${focusedText()}", 4L, channelOf(id))
            assertTrue("le focus doit avancer dans le temps : $prevId -> $id", id > prevId)
            prevId = id
        }
    }

    @Test fun plafond_bord_en_arabe_le_temps_va_de_droite_a_gauche() {
        rtl = true
        mount()
        // En arabe, la case la plus tardive de la fenêtre est la plus à GAUCHE : GAUCHE au bord avance dans le temps.
        val last = windowOf(4).last()
        val next = all.getValue(4).first { it.startMs >= last.endMs }
        focusOn(last.title)
        gesture("◀ (arabe) au bord", Key.DirectionLeft)
        assertFocusOn(next.title)
        assertEquals(at(2, 30), windowStart)
    }

    @Test fun plafond_bord_en_arabe_droite_au_bord_recule_dans_le_temps_et_garde_la_chaine() {
        rtl = true
        mount()
        // En arabe, la case la plus ancienne de la fenêtre est la plus à DROITE : DROITE au bord recule dans le temps.
        val first = windowOf(5).first()
        val before = all.getValue(5).last { it.endMs <= first.startMs }
        focusOn(first.title)
        gesture("▶ (arabe) au bord", Key.DirectionRight)
        assertFocusOn(before.title)
        assertEquals(at(1, 0) - 90 * 60_000L, windowStart)
    }

    @Test fun plafond_le_focus_initial_ne_reprend_pas_le_focus_quand_les_programmes_changent() {
        mount()
        assertTrue("focus initial sur la première case de la première ligne : ${focusedText()}", focusedText().startsWith("P1-"))
        // Focus sur la pastille « Demain » : les programmes disparaissent puis reviennent (rechargement), le focus y reste.
        rule.onNode(hasText("Demain") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        rule.waitForIdle()
        assertEquals("Demain", focusedText())
        val saved = programmes
        programmes = emptyMap(); rule.waitForIdle()
        programmes = saved; rule.waitForIdle()
        assertEquals("le focus initial a repris le focus : ${focusedText()}", "Demain", focusedText())
    }

    @Test fun plafond_changement_de_jour_au_clavier_garde_la_chaine_focalisee() {
        mount()
        focusOn("P5-3")
        gesture("⏩ jour suivant", Key.MediaFastForward)
        assertTrue("le focus doit revenir sur la chaîne 5 : ${focusedText()}", focusedText().startsWith("P5-"))
        assertEquals(1, day)
    }

    // ── Cause 2 : une arrivée de programmes ne recompose que la ligne concernée. ──

    @Test fun plafond_arrivee_de_programmes_pour_une_chaine_ne_recompose_que_sa_ligne() {
        mount()
        focusOn("P1-2")
        val cur = programmes
        val extra = cur.getValue(3L) + EpgEntity(id = 3_999_000, channelId = 3, title = "Ajout", description = null, startMs = at(11, 0), endMs = at(11, 1))
        probe.clear()
        programmes = cur + (3L to extra)
        rule.waitForIdle()
        println("COMPTEURS arrivée pour une chaîne : $probe cases=${probe.cells} ids3=${programmes.getValue(3L).map { it.id }}")
        assertEquals("seule la ligne de la chaîne 3", setOf(3L), probe.rows.keys)
        assertTrue("seules les cases de cette ligne : ${probe.cells.keys}", probe.cells.keys.all { channelOf(it) == 3L })
        assertEquals("l'écran n'est pas recomposé", 0, probe.screen)
        assertEquals("l'en-tête n'est pas recomposé", 0, probe.header)
        assertEquals(0, probe.preview)
    }

    @Test fun plafond_nouvelles_listes_de_meme_contenu_ne_recomposent_aucune_ligne() {
        mount()
        probe.clear()
        programmes = programmes.mapValues { it.value.toList() }
        rule.waitForIdle()
        assertEquals(emptySet<Long>(), probe.rows.keys)
        assertEquals(0, probe.screen)
    }

    @Test fun plafond_l_horloge_ne_recompose_les_lignes_qu_au_changement_de_minute() {
        mount()
        probe.clear()
        clock.longValue = at(1, 31) + 30_000   // même minute
        rule.waitForIdle()
        assertEquals("même minute : aucune ligne", emptySet<Long>(), probe.rows.keys)
        probe.clear()
        nowBase = at(1, 32); clock.longValue = at(1, 32)
        rule.waitForIdle()
        assertTrue("minute suivante : chaque ligne visible une fois au plus : ${probe.rows}", probe.rows.values.all { it == 1 })
        assertEquals(0, probe.screen)
    }

    // ── Cause 3 : en-tête et aperçu. ──

    @Test fun plafond_boutons_non_recomposes_entre_deux_programmes_a_venir() {
        mount()
        focusOn("P1-4")           // deux programmes à venir de suite : mêmes boutons (Regarder, Me rappeler, Enregistrer)
        val r = gesture("▶ entre deux programmes à venir", Key.DirectionRight)
        assertEquals(0, r.buttons)
        assertEquals(1, r.header)
        assertEquals(0, r.preview)
    }

    @Test fun plafond_le_panneau_du_haut_suit_la_selection_dans_la_meme_image_que_les_cases_ou_la_suivante() {
        mount()
        focusOn("P1-2")
        val r = gesture("▶ dans la ligne", Key.DirectionRight)
        assertTrue("l'en-tête se met à jour au plus une image après les cases (images : ${r.frames})", r.frames <= 2)
        // Le panneau du haut montre le programme focalisé.
        rule.onNode(hasText("P1-3") and !hasClickAction()).assertExists()
    }

    // ── C1 : une ligne sans case dans la fenêtre (chaîne « événement », « Demain » à 20:00) ne bloque jamais l'entrée. ──

    @Test fun c1_premiere_ligne_sans_case_le_focus_initial_va_sur_la_premiere_ligne_qui_en_a() {
        hole = setOf(1L)
        mount()
        assertTrue("focus initial attendu sur la chaîne 2 : ${focusedText()}", focusedText().startsWith("P2-"))
    }

    @Test fun c1_premiere_ligne_sans_case_bas_depuis_les_jours_entre_dans_la_grille() {
        hole = setOf(1L)
        mount()
        rule.onNode(hasText("Demain") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        rule.waitForIdle()
        press(Key.DirectionDown)
        assertTrue("▼ depuis les jours doit entrer dans la grille (chaîne 2) : ${focusedText()}", focusedText().startsWith("P2-"))
    }

    @Test fun c1_deux_premieres_lignes_sans_case_bas_depuis_les_jours_entre_sur_la_troisieme() {
        hole = setOf(1L, 2L)
        mount()
        assertTrue("focus initial : ${focusedText()}", focusedText().startsWith("P3-"))
        rule.onNode(hasText("Demain") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        rule.waitForIdle()
        press(Key.DirectionDown)
        assertTrue("▼ depuis les jours : ${focusedText()}", focusedText().startsWith("P3-"))
    }

    @Test fun c1_une_ligne_vide_au_milieu_se_traverse_sans_piege() {
        hole = setOf(3L)
        mount()
        focusOn(windowOf(2).first { it.startMs <= at(1, 31) && it.endMs > at(1, 31) }.title)
        press(Key.DirectionDown)
        assertTrue("▼ saute la ligne vide (chaîne 3) et va sur la chaîne 4 : ${focusedText()}", focusedText().startsWith("P4-"))
        press(Key.DirectionUp)
        assertTrue("▲ revient sur la chaîne 2 : ${focusedText()}", focusedText().startsWith("P2-"))
    }

    @Test fun c1_grille_sans_aucune_case_affiche_un_message_et_une_action() {
        hole = chans.map { it.id }.toSet()
        mount()
        rule.onNode(hasText("Aucun programme pour cette période", substring = true)).assertExists()
        rule.onNode(hasText("Demain") and hasClickAction()).performSemanticsAction(SemanticsActions.RequestFocus)
        rule.waitForIdle()
        press(Key.DirectionDown)
        assertEquals("▼ depuis les jours va sur l'action du message", "Revenir à maintenant", focusedText())
        val before = dayRequests
        press(Key.Enter)
        assertEquals("l'action ramène à maintenant (onDay(0))", before + 1, dayRequests)
        assertEquals(0, day)
    }

    // ── I1 : fin d'une synchro du guide, programmes réinsérés avec de NOUVEAUX identifiants. ──

    private fun reimport() { programmes = programmes.mapValues { (_, l) -> l.map { it.copy(id = it.id + 500_000) } }; rule.waitForIdle() }

    @Test fun i1_reimport_avec_nouveaux_identifiants_garde_le_focus_sur_le_meme_programme() {
        mount()
        val t = windowOf(4)[1]
        focusOn(t.title)
        reimport()
        assertFocusOn(t.title)
    }

    @Test fun i1_apres_reimport_le_bord_de_fenetre_retrouve_le_programme_voisin() {
        mount()
        val last = windowOf(4).last()
        val next = all.getValue(4).first { it.startMs >= last.endMs }
        focusOn(last.title)
        reimport()
        press(Key.DirectionRight)
        assertFocusOn(next.title)
        assertEquals(at(2, 30), windowStart)
    }

    // ── I3 : ◀ ▶ au-delà des données : la fenêtre est bornée, le focus reste. ──

    @Test fun i3_gauche_au_premier_programme_disponible_ne_decale_plus_la_fenetre() {
        mount()
        val firstIn = windowOf(4).first()
        focusOn(firstIn.title)
        press(Key.DirectionLeft)   // le programme précédent existe (P4-1, 00:00) : la fenêtre recule une fois
        val first = all.getValue(4).first()
        assertFocusOn(first.title)
        val w = windowStart
        repeat(4) { press(Key.DirectionLeft) }
        assertFocusOn(first.title)
        assertEquals("plus rien avant : la fenêtre ne recule plus", w, windowStart)
        assertTrue("le premier programme reste dans la fenêtre", first.startMs >= windowStart)
    }

    @Test fun i3_droite_au_dernier_programme_du_guide_ne_decale_plus_la_fenetre() {
        shortGuide = setOf(4L)
        mount()
        val last = all.getValue(4).last()
        focusOn(windowOf(4).first().title)
        repeat(12) { press(Key.DirectionRight) }
        assertFocusOn(last.title)
        assertTrue("la fenêtre montre encore la fin du dernier programme (${windowStart})", windowStart + GUIDE_WINDOW_MS >= last.endMs && windowStart < last.endMs)
        val w = windowStart
        repeat(3) { press(Key.DirectionRight) }
        assertEquals("au bout du guide : plus de décalage", w, windowStart)
        assertFocusOn(last.title)
    }

    @Test fun droite_sans_voisin_sur_la_ligne_ne_saute_jamais_en_biais_vers_une_autre_chaine() {
        shortGuide = setOf(4L)
        // Fenêtre 03:00 – 06:00 : le dernier programme de la chaîne 4 (04:25 – 05:55) n'atteint pas le bord ; les lignes voisines, si.
        windowStart = at(3, 0)
        mount()
        val last = all.getValue(4).last()
        focusOn(last.title)
        press(Key.DirectionRight)
        assertFocusOn(last.title)
    }

    @Test fun i3_droite_en_arabe_au_premier_programme_ne_decale_plus_la_fenetre() {
        rtl = true
        mount()
        focusOn(windowOf(5).first().title)
        repeat(5) { press(Key.DirectionRight) }   // en arabe, DROITE recule dans le temps
        val first = all.getValue(5).first()
        assertFocusOn(first.title)
        assertTrue(first.startMs >= windowStart)
    }

    // ── I4 : ⏩ ⏪ avec un chargement ASYNCHRONE de l'autre jour. ──

    private fun frames(n: Int) { rule.mainClock.autoAdvance = false; repeat(n) { rule.mainClock.advanceTimeByFrame() }; rule.mainClock.autoAdvance = true }

    @Test fun i4_jour_suivant_asynchrone_le_focus_reste_puis_va_sur_la_meme_chaine_a_la_meme_heure() {
        asyncDay = true
        mount()
        val p = windowOf(5)[2]
        focusOn(p.title)
        rule.mainClock.autoAdvance = false
        rule.onRoot().performKeyInput { keyDown(Key.MediaFastForward); keyUp(Key.MediaFastForward) }
        repeat(6) { rule.mainClock.advanceTimeByFrame() }
        rule.mainClock.autoAdvance = true
        assertEquals(1, day)
        assertFocusOn(p.title)   // pendant le chargement : la case conservée garde le focus
        rule.runOnIdle { vmOnVisible(lastVisible) }   // les programmes du lendemain arrivent
        rule.waitForIdle()
        val day1 = 24 * 3_600_000L
        val expected = all.getValue(5).first { p.startMs + day1 >= it.startMs && p.startMs + day1 < it.endMs }
        assertFocusOn(expected.title)
    }

    @Test fun i4_jour_suivant_chaine_absente_le_focus_va_sur_la_ligne_la_plus_proche() {
        asyncDay = true
        noTomorrow = setOf(5L)
        mount()
        val p = windowOf(5)[2]
        focusOn(p.title)
        press(Key.MediaFastForward)
        assertFocusOn(p.title)
        rule.runOnIdle { vmOnVisible(lastVisible) }
        rule.waitForIdle()
        val f = focusedText()
        assertTrue("focus attendu sur une chaîne voisine (4 ou 6), trouvé : $f", f.startsWith("P4-") || f.startsWith("P6-"))
    }

    @Test fun i4_jour_precedent_revient_sur_la_meme_chaine() {
        asyncDay = true
        mount()
        val p = windowOf(6)[1]
        focusOn(p.title)
        press(Key.MediaFastForward); rule.runOnIdle { vmOnVisible(lastVisible) }; rule.waitForIdle()
        assertTrue(focusedText().startsWith("P6-"))
        press(Key.MediaRewind); rule.runOnIdle { vmOnVisible(lastVisible) }; rule.waitForIdle()
        assertEquals(0, day)
        assertFocusOn(p.title)
    }
}
