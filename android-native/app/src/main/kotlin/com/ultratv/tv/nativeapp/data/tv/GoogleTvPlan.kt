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
    /** Intervalle minimal entre deux écritures de Watch Next dans Google TV. */
    const val MIN_SYNC_INTERVAL_MS = 45_000L

    /** Délai à attendre avant d'écrire : 0 si la dernière écriture est ancienne (ou n'a jamais eu lieu). */
    fun waitMs(lastSyncMs: Long, nowMs: Long, minIntervalMs: Long = MIN_SYNC_INTERVAL_MS): Long =
        if (lastSyncMs <= 0L) 0L else (minIntervalMs - (nowMs - lastSyncMs)).coerceIn(0L, minIntervalMs)

    /** Film ou épisode commencé et pas terminé (même règle que « Continuer à regarder »). */
    fun inProgress(h: WatchHistoryEntity): Boolean =
        (h.kind == "MOVIE" || h.kind == "EPISODE") && h.positionMs >= MIN_POSITION_MS &&
            (h.durationMs == 0L || h.positionMs < h.durationMs - END_MARGIN_MS)

    fun internalId(h: WatchHistoryEntity) = "${h.kind}:${h.providerId}:${h.remoteId}"

    fun deepLink(h: WatchHistoryEntity) = when (h.kind) {
        "LIVE" -> DeepLink.live(h.providerId, h.remoteId)
        "EPISODE" -> DeepLink.episode(h.providerId, h.remoteId)
        else -> DeepLink.movie(h.providerId, h.remoteId)
    }

    /** Les plus récents d'abord ; un seul épisode par série (le dernier regardé), au plus [MAX]. Sans affiche : écartés. */
    fun select(history: List<WatchHistoryEntity>): List<WatchHistoryEntity> =
        history.filter { inProgress(it) && !it.poster.isNullOrBlank() }
            .sortedByDescending { it.watchedAt }
            .distinctBy { if (it.kind == "EPISODE") "S:${it.providerId}:${it.parentRemoteId ?: it.remoteId}" else internalId(it) }
            .take(MAX)

    const val MAX_LIVE = 5
    private const val LIVE_RECENT_MS = 7L * 24 * 3_600_000

    /** Dernières chaînes du direct regardées (7 jours), les plus récentes d'abord : la plupart des usages sont en direct. */
    fun selectLive(history: List<WatchHistoryEntity>, nowMs: Long = System.currentTimeMillis()): List<WatchHistoryEntity> =
        history.filter { it.kind == "LIVE" && it.title.isNotBlank() && nowMs - it.watchedAt < LIVE_RECENT_MS }
            .sortedByDescending { it.watchedAt }
            .distinctBy { internalId(it) }
            .take(MAX_LIVE)
}

object FavoritesChannelPlan {
    const val MAX = 20

    fun select(channels: List<ChannelEntity>): List<ChannelEntity> =
        // Un seul exemplaire par nom : « TF1 » HD / 4K / UHD apparaissaient trois fois sur l'accueil.
        channels.filter { it.title.isNotBlank() }.distinctBy { it.providerId to it.remoteId }.distinctBy { it.title.trim().uppercase() }.take(MAX)

    /** Favoris d'abord, complétés par les dernières chaînes regardées (sans favori, la chaîne d'accueil restait vide). */
    fun select(favorites: List<ChannelEntity>, recent: List<ChannelEntity>): List<ChannelEntity> = select(favorites + recent)

    fun deepLink(c: ChannelEntity) = DeepLink.live(c.providerId, c.remoteId)

    /** Empreinte de la sélection : on ne republie la chaîne que si elle a changé. */
    fun signature(selected: List<ChannelEntity>): String =
        selected.joinToString("|") { "${it.providerId}:${it.remoteId}:${it.title}:${it.logo.orEmpty()}" }.hashCode().toString()
}

/** Chaîne d'accueil « Ultra TV · Nouveautés » : derniers films et séries ajoutés par la source active. */
object NewsChannelPlan {
    const val MAX = 20
    /** Nombre de lignes lues par type (même source que les rangées de l'accueil, avec de la marge pour les sans-affiche). */
    const val FETCH = 30

    enum class Kind { MOVIE, SERIES }

    /** Élément candidat, indépendant de Room (testable sans Android). */
    data class Item(val kind: Kind, val providerId: Long, val remoteId: String, val title: String, val poster: String?, val addedKey: Long)

    fun internalId(i: Item) = "NEWS:${i.kind.name}:${i.providerId}:${i.remoteId}"

    fun deepLink(i: Item) = when (i.kind) {
        Kind.MOVIE -> DeepLink.movieDetail(i.providerId, i.remoteId)
        Kind.SERIES -> DeepLink.series(i.providerId, i.remoteId)
    }

    /**
     * Films et séries entrelacés (un film, une série, un film…), chacun dans l'ordre addedKey décroissant (l'ordre de
     * l'accueil) ; un type épuisé laisse la place à l'autre. Sans affiche ou sans titre : écartés (un programme
     * d'aperçu exige une image). Sans doublon, au plus [MAX].
     */
    fun select(movies: List<Item>, series: List<Item>): List<Item> {
        fun clean(l: List<Item>) = l.filter { it.title.isNotBlank() && !it.poster.isNullOrBlank() }.sortedByDescending { it.addedKey }
        val m = clean(movies)
        val s = clean(series)
        val out = ArrayList<Item>(MAX)
        var a = 0
        var b = 0
        while (out.size < MAX && (a < m.size || b < s.size)) {
            if (a < m.size) out += m[a++]
            if (out.size < MAX && b < s.size) out += s[b++]
        }
        return out.distinctBy { internalId(it) }
    }

    /** Opérations à appliquer à la chaîne : ids stables ([internalId]) ; l'ordre d'affichage passe par le poids. */
    data class Diff(val insert: List<Item>, val update: List<Pair<Long, Item>>, val deleteRowIds: List<Long>)

    /** [existing] : identifiant interne -> identifiant de ligne déjà publié. Rien n'est supprimé puis recréé pour rien. */
    fun diff(existing: Map<String, Long>, wanted: List<Item>): Diff {
        val keys = wanted.map { internalId(it) }.toSet()
        return Diff(
            insert = wanted.filter { internalId(it) !in existing },
            update = wanted.mapNotNull { i -> existing[internalId(i)]?.let { it to i } },
            deleteRowIds = existing.filterKeys { it !in keys }.values.toList(),
        )
    }

    /** Poids d'affichage : le premier élément a le poids le plus fort (le système trie par poids décroissant). */
    fun weight(index: Int, size: Int) = size - index

    /** Empreinte de la sélection (ordre, titres, affiches) : on ne touche à la chaîne que si elle a changé. */
    fun signature(selected: List<Item>): String =
        selected.joinToString("|") { "${internalId(it)}:${it.title}:${it.poster.orEmpty()}" }.hashCode().toString()
}

/** Règles pures de publication des chaînes d'accueil (testables sans Android). */
object ChannelPublishPlan {
    enum class Mode { DEFAULT, NORMAL }

    /**
     * Google TV n'affiche d'office que la PREMIÈRE chaîne de l'application, et seulement si elle est publiée comme chaîne
     * par défaut (requestChannelBrowsable). La chaîne « Nouveautés », publiée comme chaîne ordinaire quand aucune chaîne
     * « Favoris » n'existait (aucun favori ni historique), n'était donc jamais visible : la première publiée est la
     * chaîne par défaut, les suivantes sont ordinaires.
     */
    fun mode(hasDefaultChannel: Boolean) = if (hasDefaultChannel) Mode.NORMAL else Mode.DEFAULT

    /** Une chaîne enregistrée mais disparue du fournisseur (retirée par l'utilisateur, base TV réinitialisée) est recréée. */
    fun mustRecreate(storedId: Long, existsInProvider: Boolean) = storedId >= 0 && !existsInProvider

    /** Les programmes sont republiés si la sélection a changé OU si le système en a effacé (nombre différent). */
    fun mustRewrite(sameSignature: Boolean, publishedCount: Int, wantedCount: Int) = !sameSignature || publishedCount != wantedCount

    /** Invite « Afficher cette chaîne sur l'accueil » : une seule fois, chaîne existante et pas encore affichée. */
    fun shouldAskBrowsable(channelId: Long, browsable: Boolean, alreadyAsked: Boolean) = channelId >= 0 && !browsable && !alreadyAsked
}

/** Limite la télémétrie : un message n'est émis que s'il change pour sa clé (pas de répétition à chaque synchro). */
class TelemetryGate {
    private val last = HashMap<String, String>()

    @Synchronized
    fun shouldEmit(key: String, message: String): Boolean {
        if (last[key] == message) return false
        last[key] = message
        return true
    }
}
