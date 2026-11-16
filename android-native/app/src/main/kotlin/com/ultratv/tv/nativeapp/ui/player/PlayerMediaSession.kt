package com.ultratv.tv.nativeapp.ui.player

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.PositionSupplier
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ultratv.tv.nativeapp.ui.player.engine.Phase
import com.ultratv.tv.nativeapp.ui.player.engine.PlayerEngine

/**
 * Session média système (androidx.media3.session) du lecteur plein écran : titre, chaîne / série, image et durée affichés
 * par Google TV / Fire TV / l'écran de verrouillage, et commandes lecture / pause / avance / retour des télécommandes
 * vocales, des touches média et du Bluetooth.
 *
 * Elle ne contient PAS de lecteur : un faux [Player] ([EnginePlayer]) reflète le moteur en cours de la [PlaybackSession]
 * (Media3 comme LibVLC, qui change au repli) et lui relaie les commandes. Le lecteur existant n'est donc ni remplacé ni
 * enveloppé : sans session (échec de création), la lecture fonctionne comme avant.
 */
@UnstableApi
class PlayerMediaSession private constructor(
    private val player: EnginePlayer,
    private val session: MediaSession,
) {
    /** Nouvel élément (titre, image…) : mis à jour tout de suite pour le système. */
    fun setNowPlaying(now: NowPlaying) { player.setNowPlaying(now) }

    /** État du moteur changé (lecture, pause, phase) ou position à rafraîchir. */
    fun refresh() { player.refresh() }

    fun release() {
        runCatching { session.release() }
        runCatching { player.release() }
    }

    companion object {
        private var counter = 0

        /** Crée la session ; null si le système la refuse (jamais d'exception : le lecteur ne doit pas en dépendre). */
        fun create(ctx: Context, engine: () -> PlayerEngine?, phase: () -> Phase): PlayerMediaSession? = runCatching {
            val player = EnginePlayer(engine, phase)
            val builder = MediaSession.Builder(ctx, player).setId("ultratv-player-${counter++}")
            ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                builder.setSessionActivity(PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            PlayerMediaSession(player, builder.build())
        }.getOrNull()
    }
}

/**
 * [Player] minimal reflétant un [PlayerEngine] : lecture / pause, avance / retour (10 s) et position. Les appels viennent
 * du fil principal (boucle d'application). Ne gère ni liste de lecture ni préparation : un seul élément, le flux en cours.
 */
@UnstableApi
internal class EnginePlayer(
    private val engine: () -> PlayerEngine?,
    private val phase: () -> Phase,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private var now = NowPlaying(title = "")

    fun setNowPlaying(n: NowPlaying) { if (n != now) { now = n; invalidateState() } }
    fun refresh() = invalidateState()

    override fun getState(): State {
        val e = engine()
        val ph = phase()
        val duration = e?.durationMs ?: -1L
        val seekable = MediaSessionLogic.seekable(now.live, duration)
        val commands = Player.Commands.Builder().addAll(
            Player.COMMAND_PLAY_PAUSE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_METADATA, Player.COMMAND_GET_TIMELINE,
        ).apply {
            if (seekable) addAll(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SEEK_FORWARD, Player.COMMAND_SEEK_BACK)
        }.build()
        val meta = MediaMetadata.Builder().setTitle(now.title).setSubtitle(now.subtitle).setDisplayTitle(now.title)
            .setArtworkUri(now.artworkUrl?.let { Uri.parse(it) })
            .setIsPlayable(true).build()
        val item = MediaItemData.Builder("ultratv-current")
            .setMediaItem(MediaItem.Builder().setMediaId("ultratv-current").setMediaMetadata(meta).build())
            .setMediaMetadata(meta)
            .setIsSeekable(seekable)
            .setIsDynamic(now.live)
            .setDurationUs(if (seekable) duration * 1000 else C.TIME_UNSET)
            .build()
        val playWhenReady = MediaSessionLogic.playWhenReady(ph, e?.isPlaying)
        val position = e?.positionMs?.coerceAtLeast(0) ?: 0L
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(MediaSessionLogic.playbackState(ph))
            .setPlaylist(listOf(item))
            .setCurrentMediaItemIndex(0)
            // Lecture en cours : la position avance seule entre deux rafraîchissements.
            .setContentPositionMs(if (ph == Phase.PLAYING && playWhenReady) PositionSupplier.getExtrapolating(position, 1f) else PositionSupplier.getConstant(position))
            .setSeekBackIncrementMs(SEEK_STEP_MS)
            .setSeekForwardIncrementMs(SEEK_STEP_MS)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        engine()?.let { if (playWhenReady) it.play() else it.pause() }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        engine()?.seekTo(positionMs.coerceAtLeast(0))
        return Futures.immediateVoidFuture()
    }

    private companion object {
        const val SEEK_STEP_MS = 10_000L
    }
}
