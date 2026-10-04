package com.ultratv.tv.nativeapp.data.tv

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.nav.DeepLink

/** Règles pures de l'intégration Google TV (testables sans Android). */
object WatchNextPlan {
    const val MAX = 10
    /** Moins d'une minute vue : pas encore « en cours ». */
    const val MIN_POSITION_MS = 60_000L
    private const val END_MARGIN_MS = 60_000L

    /** Film ou épisode commencé et pas terminé (même règle que « Continuer à regarder »). */
    fun inProgress(h: WatchHistoryEntity): Boolean =
        (h.kind == "MOVIE" || h.kind == "EPISODE") && h.positionMs >= MIN_POSITION_MS &&
            (h.durationMs == 0L || h.positionMs < h.durationMs - END_MARGIN_MS)

    fun internalId(h: WatchHistoryEntity) = "${h.kind}:${h.providerId}:${h.remoteId}"

    fun deepLink(h: WatchHistoryEntity) = when (h.kind) {
        "EPISODE" -> DeepLink.episode(h.providerId, h.remoteId)
        else -> DeepLink.movie(h.providerId, h.remoteId)
    }

    /** Les plus récents d'abord ; un seul épisode par série (le dernier regardé), au plus [MAX]. Sans affiche : écartés. */
    fun select(history: List<WatchHistoryEntity>): List<WatchHistoryEntity> =
        history.filter { inProgress(it) && !it.poster.isNullOrBlank() }
            .sortedByDescending { it.watchedAt }
            .distinctBy { if (it.kind == "EPISODE") "S:${it.providerId}:${it.parentRemoteId ?: it.remoteId}" else internalId(it) }
            .take(MAX)
}

object FavoritesChannelPlan {
    const val MAX = 20

    fun select(channels: List<ChannelEntity>): List<ChannelEntity> =
        channels.filter { it.title.isNotBlank() }.distinctBy { it.providerId to it.remoteId }.take(MAX)

    fun deepLink(c: ChannelEntity) = DeepLink.live(c.providerId, c.remoteId)

    /** Empreinte de la sélection : on ne republie la chaîne que si elle a changé. */
    fun signature(selected: List<ChannelEntity>): String =
        selected.joinToString("|") { "${it.providerId}:${it.remoteId}:${it.title}:${it.logo.orEmpty()}" }.hashCode().toString()
}
