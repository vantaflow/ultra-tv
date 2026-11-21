package com.ultratv.tv.nativeapp.ui.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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

    /** Programmes de 40 à 90 minutes, de 00:00 à 10:00, titres « Pc-i » uniques. */
    private val all: Map<Long, List<EpgEntity>> = chans.associate { c ->
        val out = ArrayList<EpgEntity>()
        var t = at(0, 0); var i = 1
        val lens = listOf(50, 70, 40, 60, 90, 45)
        while (t < at(10, 0)) {
            val len = lens[(i + c.id.toInt()) % lens.size] * 60_000L
            out += EpgEntity(id = c.id * 100 + i, channelId = c.id, title = "P${c.id}-$i", description = null, startMs = t, endMs = t + len)
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

    // ── Modèle de vue simulé : la logique du ViewModel AVANT correction (cache vidé au décalage, toutes les chaînes visibles rechargées). ──
    private fun vmOnVisible(visibleIds: List<Long>) {
        if (visibleIds.isEmpty()) return
        // Rangées d'avance (la ligne qui entre a déjà ses programmes) : n'ajoute aucune recomposition aux lignes déjà là.
        val ids = (visibleIds + (1..3).map { visibleIds.last() + it }.filter { it <= nChannels }).distinct()
        programmes = programmes + ids.associateWith { normalizeRow(rangeOf(it, windowStart, windowStart + GUIDE_WINDOW_MS)) }
    }
    private var lastVisible = emptyList<Long>()

    private fun mount() {
        I18n.init(ApplicationProvider.getApplicationContext())
        rule.setContent {
            CompositionLocalProvider(
                LocalDs provides designStringsFor(AppLang.French), LocalStrings provides stringsFor(AppLang.French), LocalTouch provides false,
                LocalGuideProbe provides probe,
            ) {
                val D = LocalDs.current
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                val clock = remember { mutableLongStateOf(at(1, 31)) }
                // Fenêtre décalée : ancien comportement, le cache est vidé puis rechargé pour les lignes visibles.
                LaunchedEffect(windowStart) { programmes = emptyMap(); vmOnVisible(lastVisible) }
                Box(Modifier.fillMaxSize().padding(start = 48.design)) {
                    GuideTvContent(
                        channelCount = chans.size, channelAt = { chans[it] }, peekAt = { chans[it] }, itemKey = { chans[it].id },
                        programmes = programmes, windowStart = windowStart, day = 0, D = D, clock = clock,
                        groups = listOf(DirectCategory(CATEGORY_ALL, null, chans.size)), group = CATEGORY_ALL, guideLoad = GuideLoad.Idle, nameColPx = 260,
                        groupName = { "Toutes" },
                        nowProvider = { at(1, 31) + (nowTick++ % 1_000) },
                        onDay = {}, onWindowStart = { windowStart = it }, onGroup = {},
                        onVisible = { ids -> lastVisible = ids; vmOnVisible(ids) },
                        onRetry = {},
                        onSelect = { _, _ -> }, onWatch = { watched = it.name }, onRemind = { _, _ -> }, onRecord = { _, _ -> },
                        preview = { sel, m ->
                            ProbePreview()
                            Box(m) { Text(sel.value?.first?.name ?: "—") }
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
    private class Snap(val rows: Map<Long, Int>, val cells: Map<Long, Int>, val header: Int, val preview: Int, val screen: Int, val frames: Int, val focus: String, val detail: String = "") {
        val rowTotal get() = rows.values.sum()
        val cellTotal get() = cells.values.sum()
        override fun toString() = "images=$frames[$detail] écran=$screen lignes=$rowTotal(${rows.size}) cases=$cellTotal(${cells.size}) en-tête=$header aperçu=$preview -> focus : $focus"
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
        val snap = Snap(HashMap(probe.rows), HashMap(probe.cells), probe.header, probe.preview, probe.screen, frames, focusedText(), detail.toString())
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

    private fun channelOf(programId: Long) = programId / 100

    @Test fun plafond_droite_gauche_dans_la_ligne_ne_touche_que_les_deux_cases() {
        mount()
        focusOn("P1-2")
        val r = gesture("▶ dans la ligne", Key.DirectionRight)
        assertEquals("aucune ligne recomposée", emptySet<Long>(), r.rows.keys)
        assertEquals("seulement la case quittée et la case atteinte", setOf(102L, 103L), r.cells.keys)
        assertEquals("l'écran n'est pas recomposé", 0, r.screen)
        assertTrue("en-tête : une fois au plus (${r.header})", r.header <= 1)
        val l = gesture("◀ dans la ligne", Key.DirectionLeft)
        assertEquals(emptySet<Long>(), l.rows.keys)
        assertEquals(setOf(102L, 103L), l.cells.keys)
        assertTrue(l.header <= 1)
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
            assertTrue("en-tête : une fois au plus (${r.header})", r.header <= 1)
            from = to
        }
        for (i in 1..3) {
            val r = gesture("▲ $i", Key.DirectionUp)
            val to = focusedId()
            assertEquals("▲ dans les lignes déjà composées : aucune ligne", emptySet<Long>(), r.rows.keys)
            assertEquals(setOf(from, to), r.cells.keys)
            from = to
        }
    }

    private fun focusedId(): Long {
        val t = focusedText().substringBefore(" / ")   // « Pc-i »
        val (c, i) = t.removePrefix("P").split("-").map { it.toLong() }
        return c * 100 + i
    }
}
