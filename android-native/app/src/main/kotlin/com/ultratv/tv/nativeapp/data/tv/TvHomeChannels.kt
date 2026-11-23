package com.ultratv.tv.nativeapp.data.tv

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.nav.DeepLink
import com.ultratv.tv.nativeapp.ui.home.HomeGuard
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Chaînes de l'écran d'accueil Android TV / Google TV que l'utilisateur active une par une (Réglages › Affichage, TV
 * seulement). Les trois chaînes historiques (« Chaînes récentes », « Favoris », « Nouveautés ») restent activées par
 * défaut, les autres sont désactivées : rien ne change pour une installation existante tant que le réglage n'est pas touché.
 */
enum class TvHomeChannel(val key: String, val defaultOn: Boolean) {
    RECENT("recent", true),
    FAVORITES("favorites", true),
    NEWS("news", true),
    NEW_MOVIES("new_movies", false),
    NEW_SERIES("new_series", false),
    TOP10("top10", false),
    TOP_RATED_MOVIES("top_movies", false),
    MY_LIST("my_list", false),
    /** Seulement si la rubrique Sport est activée (Réglages › Affichage). */
    SPORT_LIVE("sport", false),
    ;

    companion object {
        fun ofKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

/** Catégorie de chaînes du Direct publiée comme chaîne d'accueil (au plus [TvChannelsChoice.MAX_CATEGORIES]). */
data class TvCategoryRef(val providerId: Long, val categoryId: String) {
    /** Clé stable de la chaîne publiée (préférences, identifiants internes). */
    val key: String get() = "cat:$providerId:${enc(categoryId)}"

    companion object {
        internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
        internal fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")

        fun parseKey(token: String): TvCategoryRef? {
            if (!token.startsWith("cat:")) return null
            val rest = token.removePrefix("cat:")
            val pid = rest.substringBefore(':', "").toLongOrNull() ?: return null
            val rid = runCatching { dec(rest.substringAfter(':', "")) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
            return TvCategoryRef(pid, rid)
        }
    }
}

/**
 * Choix de l'utilisateur, enregistré en une chaîne (préférence `tv_home_channels`) : clés séparées par des virgules,
 * catégories sous la forme `cat:<source>:<identifiant encodé>`. Préférence absente (null) = choix par défaut ; chaîne vide =
 * tout désactivé.
 */
data class TvChannelsChoice(val enabled: Set<TvHomeChannel>, val categories: List<TvCategoryRef> = emptyList()) {
    fun isOn(c: TvHomeChannel) = c in enabled

    /** Activée ET publiable : « Sport en direct » exige la rubrique Sport. */
    fun isEffective(c: TvHomeChannel, sportSection: Boolean) = isOn(c) && (c != TvHomeChannel.SPORT_LIVE || sportSection)

    fun toggled(c: TvHomeChannel): TvChannelsChoice = copy(enabled = if (c in enabled) enabled - c else enabled + c)

    /** Ajoute une catégorie (sans doublon) ; refusée (choix inchangé) au-delà de [MAX_CATEGORIES]. */
    fun withCategory(ref: TvCategoryRef): TvChannelsChoice =
        if (ref in categories || categories.size >= MAX_CATEGORIES) this else copy(categories = categories + ref)

    fun withoutCategory(ref: TvCategoryRef): TvChannelsChoice = copy(categories = categories - ref)

    /** Catégories de la source active seulement (une catégorie d'une autre source n'est pas publiée). */
    fun categoriesFor(providerId: Long?): List<TvCategoryRef> = if (providerId == null) emptyList() else categories.filter { it.providerId == providerId }

    fun encode(): String = (TvHomeChannel.entries.filter { it in enabled }.map { it.key } + categories.map { it.key }).joinToString(",")

    /** Nombre de chaînes activées (affiché dans Réglages). */
    fun count(sportSection: Boolean): Int = TvHomeChannel.entries.count { isEffective(it, sportSection) } + categories.size

    companion object {
        const val MAX_CATEGORIES = 3
        val DEFAULT = TvChannelsChoice(TvHomeChannel.entries.filter { it.defaultOn }.toSet())

        fun parse(raw: String?): TvChannelsChoice {
            if (raw == null) return DEFAULT
            val tokens = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return TvChannelsChoice(
                enabled = tokens.mapNotNull { TvHomeChannel.ofKey(it) }.toSet(),
                categories = tokens.mapNotNull { TvCategoryRef.parseKey(it) }.distinct().take(MAX_CATEGORIES),
            )
        }

        /** Chaînes passées de désactivées à activées (invite système « Afficher sur l'accueil » à présenter). */
        fun newlyEnabled(before: TvChannelsChoice, after: TvChannelsChoice): Set<String> =
            (after.enabled.map { it.key } + after.categories.map { it.key }).toSet() -
                (before.enabled.map { it.key } + before.categories.map { it.key }).toSet()
    }
}

/**
 * Programme d'une chaîne d'accueil, indépendant d'Android (testable). [internalId] stable : la chaîne est mise à jour par
 * différence, rien n'est supprimé puis recréé pour rien.
 */
data class TvProgramItem(
    val internalId: String,
    val title: String,
    val image: String,
    /** TvContractCompat.PreviewPrograms.ASPECT_RATIO_*. */
    val aspectRatio: Int,
    /** TvContractCompat.PreviewPrograms.TYPE_*. */
    val type: Int,
    val link: String,
    val live: Boolean = false,
    val description: String? = null,
    /** Image à composer par l'appli (logo de chaîne en 16:9, fond en 2:3) ; [image] reste la source (signature). */
    val compose: TvArtRequest? = null,
)

/** Règles pures des chaînes d'accueil activables (sélection, filtres, différences). */
object TvChannelPlan {
    const val MAX = 20
    const val TOP10_SIZE = 10
    /** Lignes lues par requête (marge pour les éléments filtrés ou sans image). */
    const val FETCH = 60
    /** Au moins autant de tendances Trakt pour un Top 10 « du jour » (sinon les films les mieux notés), comme l'accueil. */
    const val MIN_TRENDING = 4
    /** Délai minimal depuis le démarrage de l'appareil avant le premier travail des chaînes activables. */
    const val BOOT_SETTLE_MS = 120_000L
    /** Rafraîchissement de « Sport en direct » (les matchs commencent et finissent). */
    const val SPORT_REFRESH_MS = 10L * 60_000

    private const val RATIO_16_9 = androidx.tvprovider.media.tv.TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9
    private const val TYPE_MOVIE = androidx.tvprovider.media.tv.TvContractCompat.PreviewPrograms.TYPE_MOVIE
    private const val TYPE_SERIES = androidx.tvprovider.media.tv.TvContractCompat.PreviewPrograms.TYPE_TV_SERIES
    private const val TYPE_CHANNEL = androidx.tvprovider.media.tv.TvContractCompat.PreviewPrograms.TYPE_CHANNEL

    /** Attente avant le premier travail : rien de lourd juste après le démarrage de la box. */
    fun bootWaitMs(elapsedSinceBootMs: Long, settleMs: Long = BOOT_SETTLE_MS): Long = (settleMs - elapsedSinceBootMs).coerceIn(0L, settleMs)

    /** Titre de film ou de série candidat, avec sa catégorie (filtre du profil). */
    data class Vod(
        val kind: NewsChannelPlan.Kind, val providerId: Long, val remoteId: String, val title: String,
        val poster: String?, val landscape: String?, val categoryId: String?,
    )

    private fun catKind(k: NewsChannelPlan.Kind) = if (k == NewsChannelPlan.Kind.MOVIE) "MOVIE" else "SERIES"

    /** Ce que l'accueil de l'appli montrerait au profil actif (voir [HomeGuard]) : même règle pour la chaîne publiée. */
    fun allowed(v: Vod, guard: HomeGuard) = guard.allows(catKind(v.kind), v.providerId, v.categoryId, v.title)

    /**
     * Titres publiables, dans l'ordre donné : autorisés par [guard], avec titre et image, un seul par titre (4K / Bluray…),
     * au plus [max].
     */
    fun selectVod(items: List<Vod>, guard: HomeGuard, max: Int = MAX): List<Vod> =
        items.asSequence()
            .filter { it.title.isNotBlank() && (!it.poster.isNullOrBlank() || !it.landscape.isNullOrBlank()) && allowed(it, guard) }
            .distinctBy { it.kind to it.remoteId }
            .distinctBy { it.kind to it.title.trim().lowercase() }
            .take(max).toList()

    /** Films et séries entrelacés (Ma liste) : un film, une série… ; un type épuisé laisse la place à l'autre. */
    fun interleave(a: List<Vod>, b: List<Vod>): List<Vod> {
        val out = ArrayList<Vod>(a.size + b.size)
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            if (i < a.size) out += a[i++]
            if (j < b.size) out += b[j++]
        }
        return out
    }

    /** Top 10 du jour : tendances Trakt (déjà rapprochées de la source) si assez nombreuses, sinon les mieux notés. */
    fun top10(trending: List<Vod>, topRated: List<Vod>, guard: HomeGuard): List<Vod> {
        val t = selectVod(trending, guard, TOP10_SIZE)
        return if (t.size >= MIN_TRENDING) t else selectVod(topRated, guard, TOP10_SIZE)
    }

    fun vodProgram(prefix: String, v: Vod): TvProgramItem {
        val art = NewsChannelPlan.art(v.poster, v.landscape, v.title)
        return TvProgramItem(
            internalId = "$prefix:${v.kind.name}:${v.providerId}:${v.remoteId}",
            title = v.title, image = art.uri, aspectRatio = art.aspectRatio, compose = art.compose,
            type = if (v.kind == NewsChannelPlan.Kind.MOVIE) TYPE_MOVIE else TYPE_SERIES,
            link = if (v.kind == NewsChannelPlan.Kind.MOVIE) DeepLink.movieDetail(v.providerId, v.remoteId) else DeepLink.series(v.providerId, v.remoteId),
        )
    }

    /** Chaînes du direct publiables : ni séparateur, ni verrouillée, ni d'une catégorie bloquée ([guard]) ; une par nom. */
    fun selectChannels(channels: List<ChannelEntity>, guard: HomeGuard, max: Int = MAX): List<ChannelEntity> =
        channels.asSequence()
            .filter { !it.isSeparator && it.title.isNotBlank() && guard.allowsChannel(it) }
            .distinctBy { it.providerId to it.remoteId }
            .distinctBy { it.title.trim().uppercase() }
            .take(max).toList()

    fun channelProgram(prefix: String, c: ChannelEntity, fallbackImage: String, title: String = c.title, description: String? = null) = TvProgramItem(
        internalId = "$prefix:LIVE:${c.providerId}:${c.remoteId}",
        title = title, image = c.logo?.takeIf { it.isNotBlank() } ?: fallbackImage, aspectRatio = RATIO_16_9,
        type = TYPE_CHANNEL, link = DeepLink.live(c.providerId, c.remoteId), live = true, description = description,
        // Logo entier centré dans une vignette 16:9 composée (le lanceur l'agrandissait et le rognait) ; nom sans logo.
        compose = TvArtRequest.ChannelLogo(c.logo?.takeIf { it.isNotBlank() }, c.title),
    )

    /** Opérations à appliquer : [existing] = identifiant interne -> ligne publiée. */
    data class Diff(val insert: List<TvProgramItem>, val update: List<Pair<Long, TvProgramItem>>, val deleteRowIds: List<Long>)

    fun diff(existing: Map<String, Long>, wanted: List<TvProgramItem>): Diff {
        val keys = wanted.map { it.internalId }.toSet()
        return Diff(
            insert = wanted.filter { it.internalId !in existing },
            update = wanted.mapNotNull { i -> existing[i.internalId]?.let { it to i } },
            deleteRowIds = existing.filterKeys { it !in keys }.values.toList(),
        )
    }

    fun signature(items: List<TvProgramItem>): String = "art${TvArtPlan.VERSION}|" + items.joinToString("|") { "${it.internalId}:${it.title}:${it.image}:${it.description.orEmpty()}" }.hashCode().toString()

    /** Clés publiées à supprimer : plus voulues (désactivées, catégorie retirée, autre source). */
    fun stale(published: Set<String>, wanted: Set<String>): Set<String> = published - wanted
}

/**
 * Contrôle parental des chaînes publiées : AUCUN élément verrouillé (chaîne verrouillée, ou catégorie verrouillée) dans
 * « Continuer à regarder ». Choix de l'utilisateur (comme Favoris, Ma liste, Chaînes récentes) : filtre
 * [HomeGuard.forHistory] pour le direct comme pour les films et épisodes — verrou parental et adulte en profil Enfants,
 * jamais le masquage ni la désactivation de catégorie ; catégorie relue dans le catalogue.
 */
object HistoryGuardPlan {
    fun categoryKind(h: WatchHistoryEntity) = when (h.kind) { "LIVE" -> "LIVE"; "MOVIE" -> "MOVIE"; else -> "SERIES" }

    /** Identifiant du titre dont la catégorie compte : la série d'un épisode. */
    fun titleRemoteId(h: WatchHistoryEntity) = if (h.kind == "EPISODE") h.parentRemoteId ?: h.remoteId else h.remoteId

    fun allows(h: WatchHistoryEntity, categoryId: String?, liveGuard: HomeGuard, vodGuard: HomeGuard): Boolean {
        val g = if (h.kind == "LIVE") liveGuard else vodGuard
        return (h.kind != "LIVE" || "${h.providerId}:${h.remoteId}" !in g.lockedChannels) &&
            g.allows(categoryKind(h), h.providerId, categoryId, h.title)
    }
}
