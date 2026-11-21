package com.ultratv.tv.nativeapp.ui.guide

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.adaptive.NetworkMonitor
import com.ultratv.tv.nativeapp.adaptive.ResolvedPlayback
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.ui.common.ChannelDisplay
import com.ultratv.tv.nativeapp.ui.common.LocalChannelDisplay
import com.ultratv.tv.nativeapp.ui.player.engine.AspectMode
import com.ultratv.tv.nativeapp.ui.player.engine.BufferPlanner
import com.ultratv.tv.nativeapp.ui.player.engine.BufferPreset
import com.ultratv.tv.nativeapp.ui.player.engine.CustomBuffer
import com.ultratv.tv.nativeapp.ui.player.engine.DecoderMode
import com.ultratv.tv.nativeapp.ui.player.engine.EngineChoice
import com.ultratv.tv.nativeapp.ui.player.engine.EngineConfig
import com.ultratv.tv.nativeapp.ui.player.engine.EngineEvent
import com.ultratv.tv.nativeapp.ui.player.engine.EngineKind
import com.ultratv.tv.nativeapp.ui.player.engine.EngineStats
import com.ultratv.tv.nativeapp.ui.player.engine.InMemoryChannelPlaybackMemory
import com.ultratv.tv.nativeapp.ui.player.engine.PlaybackSession
import com.ultratv.tv.nativeapp.ui.player.engine.PlayerEngine
import com.ultratv.tv.nativeapp.ui.player.engine.SharedLiveSession
import com.ultratv.tv.nativeapp.ui.player.engine.TrackInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Encadré d'aperçu du Guide : changer de chaîne ne retire jamais la vidéo de la composition (la surface n'est ni détruite
 * ni recréée) ; le logo de la chaîne montrée recouvre l'ancienne image tant que la nouvelle chaîne n'est pas lue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@androidx.media3.common.util.UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuidePreviewVideoTest {
    @get:Rule val rule = createComposeRule()

    private class FakeEngine(override val kind: EngineKind, override val config: EngineConfig) : PlayerEngine {
        val flow = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
        override val view: View = View(ApplicationProvider.getApplicationContext())
        override val events: Flow<EngineEvent> = flow
        override val reusable: Boolean get() = true
        override fun load(url: String, startPositionMs: Long) {}
        override fun play() {}
        override fun pause() {}
        override fun seekTo(ms: Long) {}
        override val isPlaying = true
        override val positionMs = 0L
        override val durationMs = -1L
        override fun audioTracks() = emptyList<TrackInfo>()
        override fun subtitleTracks() = emptyList<TrackInfo>()
        override fun selectAudio(id: String) {}
        override fun selectSubtitle(id: String?) {}
        override fun setAspect(mode: AspectMode) {}
        override fun setSpeed(speed: Float) {}
        override val hasVideo get() = true
        override fun limitQuality(maxHeight: Int, maxBitrateBps: Int?) {}
        override fun stats() = EngineStats()
        override fun setMuted(muted: Boolean) {}
        override fun release() {}
    }

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val settings = ResolvedPlayback(
        engine = EngineChoice.EXO, decoder = DecoderMode.AUTO, buffer = BufferPlanner.resolve(BufferPreset.AUTO, CustomBuffer(), 192, lowRam = false),
        bufferPreset = BufferPreset.AUTO, maxVideoHeight = 0, maxVideoBitrateBps = null, preferH264 = false,
        manualEngine = false, manualDecoder = false, manualBuffer = false,
    )
    private val scope = TestScope(UnconfinedTestDispatcher())
    private val shared = SharedLiveSession(scope)
    private val container = FrameLayout(ctx)
    private val create: () -> PlaybackSession = {
        PlaybackSession(
            ctx, scope, container, { settings }, InMemoryChannelPlaybackMemory(), NetworkMonitor(ctx),
            isLive = true, autoFrameRate = false, userAgent = "test",
            engineFactory = { k, c -> FakeEngine(k, c) },
        )
    }

    private fun ch(id: Long, name: String) = ChannelEntity(id = id, providerId = 1, remoteId = "$id", name = name, logo = null, categoryId = "c", streamUrl = "u$id", num = id.toInt())

    @Test fun changer_de_chaine_garde_la_video_composee_et_montre_le_logo_de_la_nouvelle() {
        val a = ch(1, "Alpha"); val b = ch(2, "Bravo")
        shared.preview("1:1", "1:1", "http://a", muted = true, create)
        val session = shared.session!!
        var channel by mutableStateOf(a)
        var matches by mutableStateOf(true)
        var detached = 0
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { detached++ }
        })
        rule.setContent {
            // Logos masqués : le grand repère montre le nom de la chaîne (vérifiable).
            CompositionLocalProvider(LocalChannelDisplay provides ChannelDisplay(showLogos = false)) {
                Box(Modifier.size(400.dp, 300.dp)) { GuidePreviewVideo(channel, session, shared, matches, Modifier.size(400.dp, 300.dp)) }
            }
        }
        rule.waitForIdle()
        assertTrue("vidéo attachée", container.isAttachedToWindow)
        val host = container.parent

        // ▼ : nouvelle chaîne montrée, l'aperçu lit encore l'ancienne (délai avant le changement de flux).
        channel = b; matches = false
        rule.waitForIdle()
        assertTrue("la vidéo reste attachée pendant le changement de chaîne", container.isAttachedToWindow)
        assertSame("même hôte : aucune surface recréée", host, container.parent)
        rule.onNodeWithText("Bravo").assertExists()

        // Le flux de la nouvelle chaîne démarre sur la même session.
        shared.preview("1:2", "1:2", "http://b", muted = true, create)
        matches = true
        rule.waitForIdle()
        assertSame(host, container.parent)
        assertEquals("jamais détachée d'une chaîne à l'autre", 0, detached)
    }

    @Test fun sans_session_le_logo_seul() {
        rule.setContent {
            CompositionLocalProvider(LocalChannelDisplay provides ChannelDisplay(showLogos = false)) {
                GuidePreviewVideo(ch(3, "Charlie"), null, shared, false, Modifier.size(400.dp, 300.dp))
            }
        }
        rule.onNodeWithText("Charlie").assertExists()
        assertTrue(!container.isAttachedToWindow)
    }
}
