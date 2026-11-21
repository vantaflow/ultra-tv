package com.ultratv.tv.nativeapp.ui.home

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.data.tmdb.TmdbKind
import com.ultratv.tv.nativeapp.ui.catalog.PosterItem

/** Données fixes partagées par les tests de structure et de parcours de l'accueil TV. */
internal object HomeTvFixtures {
    val heroWithBackdrop = HeroItem(
        kind = HeroItem.Kind.MOVIE, id = 1, title = "Orbite basse", year = 2024, genre = "Science-fiction", rating = 7.8,
        backdrop = "https://exemple.invalid/fond.jpg", poster = null, plot = "Six heures pour ramener tout l'équipage sur Terre.", runtimeMin = 126,
    )

    fun posterUi(prefix: String, i: Int) = PosterCardUi(
        key = "$prefix$i", item = PosterItem(id = i + if (prefix == "t") 100L else 0L, title = "Titre $prefix$i", poster = null, year = 2024, rating = 7.5),
        isShow = false, kind = TmdbKind.MOVIE,
    )

    fun resume(i: Int) = ResumeCardUi(
        key = "r$i", entry = WatchHistoryEntity(providerId = 1, kind = "MOVIE", remoteId = "$i", title = "Film $i", poster = null, streamUrl = "u"),
        title = "Film $i", progress = 0.4f, minutesLeft = 30,
    )

    private fun ch(i: Int) = ChannelEntity(id = i.toLong(), providerId = 1, remoteId = "$i", name = "Nova $i", logo = null, categoryId = null, streamUrl = "u", num = i)

    fun rows(layout: HomeLayout) = HomeRows(
        // Trois chaînes : deux avec programme en cours, une sans (la carte montre alors seulement la chaîne).
        channels = listOf(ChannelCardUi(ch(1), "Esprits criminels", "20:00 – 21:00", 0.6f), ChannelCardUi(ch(2), "Le grand journal", "20:30 – 21:30", 0.3f), ChannelCardUi(ch(3), null)),
        resume = listOf(resume(1), resume(2)), recentChannels = listOf(resume(3)),
        newEpisodes = listOf(NewEpisodeCardUi(21, "Les Gardiens du phare", null, 2023, 8.1, 2, 7, 0L, aired = true)),
        shortcuts = shortcutsOf(HomeCounts(channels = 1240, movies = 30435, series = 4028, favorites = 12), emptyList(), null, null),
        // Top 10 (dix affiches) et « Derniers films » (six affiches) : de quoi tester les rangées d'affiches.
        top10 = (1..10).map { posterUi("t", it) }, latestMovies = (1..6).map { posterUi("m", it) },
        layout = layout,
    )

    /** Accueil long : une dizaine de rangées, de quoi faire défiler l'image « À la une » hors de la composition. */
    fun longRows(layout: HomeLayout) = rows(layout).copy(
        latestSeries = (1..6).map { posterUi("s", it) }, topMovies = (1..6).map { posterUi("f", it) },
        topSeries = (1..6).map { posterUi("g", it) }, trending = (1..6).map { posterUi("v", it) }, popular = (1..6).map { posterUi("p", it) },
    )
}
