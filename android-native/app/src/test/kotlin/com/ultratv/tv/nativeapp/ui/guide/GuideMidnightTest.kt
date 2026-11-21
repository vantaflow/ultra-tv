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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.paging.Pager
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.Text
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.repo.PagingMemory
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.design
import com.ultratv.tv.nativeapp.ui.common.getOrNull
import com.ultratv.tv.nativeapp.ui.common.peekOrNull
import com.ultratv.tv.nativeapp.ui.common.rememberSafeItemKey
import com.ultratv.tv.nativeapp.ui.live.CATEGORY_ALL
import com.ultratv.tv.nativeapp.ui.live.DirectCategory
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
 * I2 : ▶ au bord qui franchit minuit, sur une ligne PROFONDE d'une vraie liste paginée (même configuration que le Guide :
 * lot initial de 30, pas d'emplacements réservés), reconstruite quand la plage de la liste ([GUIDE_CHANNEL_RANGE]) change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class GuideMidnightTest {
    @get:Rule val rule = createComposeRule()

    private fun at(h: Int, m: Int, dayOffset: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, dayOffset)
    }.timeInMillis

    private val chans = (1..60).map { ChannelEntity(id = it.toLong(), providerId = 1, remoteId = "$it", name = "Chaîne $it", logo = null, categoryId = "c", streamUrl = "u", num = it) }
    private val all: Map<Long, List<EpgEntity>> = chans.associate { c ->
        val out = ArrayList<EpgEntity>()
        var t = at(0, 0); var i = 1
        val lens = listOf(50, 70, 40, 60, 90, 45)
        while (t < at(0, 0) + 48 * 3_600_000L) {
            val len = lens[(i + c.id.toInt()) % lens.size] * 60_000L
            out += EpgEntity(id = c.id * 1000 + i, channelId = c.id, title = "P${c.id}-$i", description = null, startMs = t, endMs = t + len)
            t += len; i++
        }
        c.id to out
    }

    private var windowStart by mutableStateOf(at(22, 30))
    private var programmes by mutableStateOf<Map<Long, List<EpgEntity>>>(emptyMap())
    private val cache = GuideProgrammeCache()
    private var lastVisible = emptyList<Long>()
    private var sources = 0

    private fun vmOnVisible(ids: List<Long>) {
        if (ids.isEmpty()) return
        val (from, to) = guideNeededRange(windowStart)
        val missing = cache.missing(ids, from, to)
        if (missing.isEmpty()) return
        val (f, t) = guideFetchRange(windowStart)
        if (cache.put(missing, f, t, missing.flatMap { id -> all[id].orEmpty().filter { it.endMs >= f && it.startMs <= t } })) programmes = cache.snapshot()
    }

    /** Comme la requête Room paginée : LIMIT / OFFSET, toujours relue depuis le début par un nouveau Pager. */
    private inner class ListSource : PagingSource<Int, ChannelEntity>() {
        init { sources++ }
        override fun getRefreshKey(state: PagingState<Int, ChannelEntity>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ChannelEntity> {
            val start = params.key ?: 0
            val end = minOf(chans.size, start + params.loadSize)
            return LoadResult.Page(chans.subList(start, end), null, if (end >= chans.size) null else end)
        }
    }

    private fun focusedText(): String = runCatching {
        rule.onNode(isFocused()).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString(" / ") { it.text } ?: "(focus sans texte)"
    }.getOrDefault("(aucun focus)")

    private fun press(k: Key) { rule.onRoot().performKeyInput { keyDown(k); keyUp(k) }; rule.waitForIdle() }

    @Test fun i2_droite_au_bord_qui_franchit_minuit_garde_la_chaine_profonde_et_le_focus() {
        I18n.init(ApplicationProvider.getApplicationContext())
        // La liste paginée du Guide : reconstruite quand sa plage change (même règle que GuideGridViewModel.channels).
        val pages = snapshotFlow { windowStart; GUIDE_CHANNEL_RANGE }.distinctUntilChanged()
            .flatMapLatest { Pager(PagingMemory.config(20, 10, 30)) { ListSource() }.flow }
        val clock = mutableLongStateOf(at(22, 40))
        rule.setContent {
            CompositionLocalProvider(LocalDs provides designStringsFor(AppLang.French), LocalStrings provides stringsFor(AppLang.French), LocalTouch provides false) {
                val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
                androidx.compose.runtime.SideEffect { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
                val items = remember { pages }.collectAsLazyPagingItems()
                Box(Modifier.fillMaxSize().padding(start = 48.design)) {
                    GuideTvContent(
                        channelCount = items.itemCount, channelAt = { items.getOrNull(it) }, peekAt = { items.peekOrNull(it) },
                        itemKey = rememberSafeItemKey(items) { it.id },
                        progsFor = { programmes[it].orEmpty() }, loadedFor = { programmes.containsKey(it) }, windowStart = windowStart, day = 0, D = LocalDs.current, clock = clock,
                        groups = listOf(DirectCategory(CATEGORY_ALL, null, chans.size)), group = CATEGORY_ALL, guideLoad = GuideLoad.Idle, nameColPx = 260,
                        groupName = { "Toutes" }, nowProvider = { at(22, 40) },
                        onDay = {}, onWindowStart = { windowStart = it; vmOnVisible(lastVisible) }, onGroup = {},
                        onVisible = { ids -> lastVisible = ids; vmOnVisible(ids) }, onRetry = {},
                        onSelect = { _, _ -> }, onWatch = {}, onRemind = { _, _ -> }, onRecord = { _, _ -> },
                        preview = { _, m -> Box(m) { Text("—") } },
                    )
                }
            }
        }
        rule.waitForIdle()
        press(Key.ShiftLeft)
        assertTrue("focus initial : ${focusedText()}", focusedText().startsWith("P1-"))
        // Descente jusqu'à la chaîne 45 (au-delà du premier lot de 30).
        repeat(44) { press(Key.DirectionDown) }
        assertTrue("chaîne 45 attendue : ${focusedText()}", focusedText().startsWith("P45-"))
        val sourcesBefore = sources
        // ▶ jusqu'à franchir minuit : la fenêtre passe au lendemain, la chaîne 45 garde le focus à chaque appui.
        var n = 0
        while (windowStart < at(0, 0, 1) && n < 12) {
            press(Key.DirectionRight); n++
            assertTrue("appui $n (fenêtre ${(windowStart - at(0, 0)) / 60_000} min) : focus attendu sur la chaîne 45, trouvé ${focusedText()}", focusedText().startsWith("P45-"))
        }
        assertTrue("minuit franchi", windowStart >= at(0, 0, 1))
        press(Key.DirectionRight)
        assertTrue("après minuit : ${focusedText()}", focusedText().startsWith("P45-"))
        assertEquals("la liste paginée n'est pas reconstruite", sourcesBefore, sources)
    }
}
