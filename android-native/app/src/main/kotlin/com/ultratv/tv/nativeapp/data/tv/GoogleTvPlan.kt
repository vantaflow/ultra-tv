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
            // Même film dans deux catégories de la source (4K, Bluray…) : une seule tuile, la lecture la plus récente.
            .distinctBy { if (it.kind == "MOVIE") "M:" + com.ultratv.tv.nativeapp.data.repo.TitleCleaner.historyTitle(it.title).lowercase() else internalId(it) }
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
        // Un même film rangé dans plusieurs catégories (4K, Bluray…) : une seule tuile, la plus récente.
        fun clean(l: List<Item>) = l.filter { it.title.isNotBlank() && !it.poster.isNullOrBlank() }.sortedByDescending { it.addedKey }
            .distinctBy { it.title.trim().lowercase() }
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

/**
 * Fournisseur TV du système (autorité `android.media.tv` : chaînes d'accueil et Watch Next). Sur certains appareils
 * leanback il ne répond pas — NVIDIA Shield de certains builds / régions : fournisseur absent, désactivé, ou invisible
 * pour l'appli (filtrage de visibilité des paquets depuis Android 11) — et chaque écriture du ContentResolver lève
 * « IllegalArgumentException: Unknown URL content://android.media.tv/… » (la lecture, elle, renvoie null sans bruit).
 * La vérification leanback ne le voit pas : la Shield EST un appareil leanback ; la synchro échouait donc à chaque
 * lancement (trois avertissements par processus). Le manifeste déclare désormais l'autorité dans `<queries>` (cause
 * probable sur la Shield) ; cette porte reste le garde-fou pour un fournisseur réellement absent ou désactivé.
 *
 * Règles pures : sonde une fois par processus, indisponibilité signalée au plus une fois par [intervalMs] (horodatage
 * persistant), et toute erreur de résolveur rétrograde la synchro en « indisponible » pour le reste du processus.
 */
class TvProviderGate(private val intervalMs: Long = REPORT_INTERVAL_MS) {
    enum class Decision { PROBE, PUBLISH, SKIP_SILENT, SKIP_REPORT }

    /** null : pas encore sondé dans ce processus. Une fois faux, reste faux (plus de sonde). */
    @Volatile var resolved: Boolean? = null
        private set

    /** Pourquoi le fournisseur est indisponible (pour l'événement de télémétrie) ; vide tant qu'il répond. */
    @Volatile var reason: String = ""
        private set

    /** Résultat de la sonde ; ignoré si une opération a déjà déclaré le fournisseur indisponible. */
    @Synchronized
    fun probed(ok: Boolean, reasonIfMissing: String) {
        if (resolved == false) return
        resolved = ok
        if (!ok) reason = reasonIfMissing
    }

    /**
     * Une opération a échoué : vrai si c'est une erreur de résolveur — le fournisseur est alors indisponible pour le reste
     * du processus ; faux pour toute autre erreur (le fournisseur répond, l'appelant garde son avertissement).
     */
    @Synchronized
    fun failed(what: String, e: Throwable): Boolean {
        if (!isResolverFailure(e)) return false
        resolved = false
        reason = "$what: ${e.javaClass.simpleName} ${e.message.orEmpty().take(120)}"
        return true
    }

    fun decide(lastReportedAt: Long, now: Long): Decision = decide(resolved, lastReportedAt, now, intervalMs)

    companion object {
        /** Au plus un événement « indisponible » par jour et par appareil (la télémétrie était dédoublonnée par processus). */
        const val REPORT_INTERVAL_MS = 24L * 3_600_000

        /**
         * [resolved] : null = jamais sondé (sonder), vrai = le fournisseur répond (publier), faux = absent : se taire, ou se
         * taire et signaler si le dernier signalement ([lastReportedAt], 0 = jamais) date d'au moins [intervalMs].
         */
        fun decide(resolved: Boolean?, lastReportedAt: Long, now: Long, intervalMs: Long = REPORT_INTERVAL_MS): Decision = when {
            resolved == null -> Decision.PROBE
            resolved -> Decision.PUBLISH
            shouldReport(lastReportedAt, now, intervalMs) -> Decision.SKIP_REPORT
            else -> Decision.SKIP_SILENT
        }

        /** Jamais signalé, intervalle écoulé, ou horloge reculée (un horodatage futur bloquerait sinon pendant des jours). */
        fun shouldReport(lastReportedAt: Long, now: Long, intervalMs: Long = REPORT_INTERVAL_MS): Boolean =
            lastReportedAt <= 0L || now < lastReportedAt || now - lastReportedAt >= intervalMs

        /**
         * Erreur de résolveur : ContentResolver.insert / update / delete lèvent IllegalArgumentException (« Unknown URL »)
         * quand aucun fournisseur ne répond à l'autorité ; le fournisseur lui-même lève la même exception (« Unknown URI »)
         * pour une URI qu'il ne connaît pas (ancien fournisseur sans programmes d'aperçu). Dans les deux cas, réessayer
         * dans ce processus ne servirait à rien. Un refus (NullPointerException « insertion failed », SecurityException)
         * vient d'un fournisseur présent : pas une erreur de résolveur.
         */
        fun isResolverFailure(e: Throwable): Boolean = e is IllegalArgumentException
    }
}
