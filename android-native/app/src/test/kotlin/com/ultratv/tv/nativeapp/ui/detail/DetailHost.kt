package com.ultratv.tv.nativeapp.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import com.ultratv.tv.nativeapp.data.db.WatchHistoryEntity
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.LocalStrings
import com.ultratv.tv.nativeapp.i18n.designStringsFor
import com.ultratv.tv.nativeapp.i18n.stringsFor
import com.ultratv.tv.nativeapp.ui.common.LocalUiWidthDp
import com.ultratv.tv.nativeapp.ui.design.Ux
import com.ultratv.tv.nativeapp.ui.mobile.LocalMobileStrings
import com.ultratv.tv.nativeapp.ui.mobile.LocalTouch
import com.ultratv.tv.nativeapp.ui.mobile.LocalUiHeightDp
import com.ultratv.tv.nativeapp.ui.mobile.MobileStrings

/**
 * Hôte de test des fiches (lot 3). Les classes de test fixent la fenêtre Robolectric :
 * TV `w960dp-h540dp-xhdpi` (= 1920 × 1080 px, la densité normalisée de l'appli), toucher `w1300dp-h1300dp-xhdpi`
 * (assez grande pour toutes les tailles du lot 2 : `390×844`, `320×568`, `844×390`, `800×1280`, `1280×800`).
 *
 * - [tv] : la fenêtre entière sert (width / height sont alors ignorés : 960 × 540 dp) ; sinon le contenu est imposé à
 *   [width] × [height] dp et `LocalTouch` est vrai, comme sur un téléphone ou une tablette.
 * - [fontScale] : facteur de police du système (ou « Taille du texte » de l'appli).
 * - [keyboard] : mode clavier (télécommande, clavier matériel) ; sans lui, les éléments cliquables ne prennent pas le focus.
 *   Par défaut vrai sur TV (télécommande) et faux au toucher ; à passer à vrai pour une tablette avec clavier.
 * - Arabe : mise en page de droite à gauche.
 *
 * À appeler UNE fois par test ; penser à `@After { Ux.themeLight = false }` ([resetDetailHost]).
 */
internal fun ComposeContentTestRule.setDetail(
    tv: Boolean, width: Int, height: Int, lang: AppLang = AppLang.French, light: Boolean = false, fontScale: Float = 1f,
    keyboard: Boolean = tv, content: @Composable () -> Unit,
) {
    I18n.init(ApplicationProvider.getApplicationContext())
    Ux.themeLight = light
    setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDs provides designStringsFor(lang), LocalStrings provides stringsFor(lang),
            LocalMobileStrings provides MobileStrings.forLang(lang, lang.code),
            LocalTouch provides !tv,
            LocalUiWidthDp provides (if (tv) 960f else width.toFloat()),
            LocalUiHeightDp provides (if (tv) 540f else height.toFloat()),
            LocalDensity provides Density(base.density, fontScale),
            LocalLayoutDirection provides (if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr),
        ) {
            if (keyboard) {
                val inputMode = LocalInputModeManager.current
                SideEffect { inputMode.requestInputMode(InputMode.Keyboard) }
            }
            if (tv) content()
            else Box(Modifier.requiredSize(width.dp, height.dp), contentAlignment = Alignment.TopStart) { content() }
        }
    }
    waitForIdle()
}

internal fun resetDetailHost() { Ux.themeLight = false }

/** Données fictives neutres des fiches. */
internal object DetailFixtures {
    private val people = listOf(
        CastUi("Nora Valin", "Elsa", null), CastUi("Tom Reyes", "Malik", null), CastUi("Ana Kiel", null, null),
        CastUi("Jo Bram", null, null), CastUi("Lou Marez", null, null), CastUi("Sam Doré", "Réalisation", null),
    )

    /** Film ; [resumeAtMs] > 0 : film commencé (« Reprendre à … » + « Depuis le début »). */
    fun movie(
        title: String = "Orbite basse", resumeAtMs: Long = 0L, trailer: Boolean = true, cast: List<CastUi> = people,
        synopsis: String? = "Une ingénieure coincée dans une station orbitale en perdition a six heures pour ramener son équipage sur Terre.",
    ) = MovieDetailTvModel(
        title = title, backdrop = null, poster = null,
        chips = listOf("2024", "Science-fiction", "4K", "VF"),
        ratings = listOf(RatingCell("7,6", "IMDb"), RatingCell("2 h 06", "Durée")),
        synopsis = synopsis, skeleton = false,
        primaryLabel = if (resumeAtMs > 0) "Reprendre à 31:12" else "Regarder",
        fromStartLabel = if (resumeAtMs > 0) "Depuis le début" else null,
        trailerLabel = "Bande-annonce",
        progress = if (resumeAtMs > 0) 0.25f else 0f, progressLine = if (resumeAtMs > 0) "Reprendre à 31:12 · 94 min restantes" else null,
        cast = cast, castTitle = "Distribution", backDescription = "Retour",
    )

    fun entity(name: String = "Les Gardiens du phare") = SeriesEntity(
        providerId = 1, remoteId = "s1", name = name, poster = null, categoryId = null, year = 2023, rating = 8.1,
        plot = "Sur une île coupée du monde, trois gardiens découvrent que le phare guide autre chose que des bateaux.",
        genre = "Drame", cast = "Mara Aubin, Yann Kerel, Léa Tanguy",
    )

    fun episode(season: Int, episode: Int) = EpisodeEntity(
        seriesId = 1, remoteId = "e$season.$episode", season = season, episode = episode, title = "Titre$episode", streamUrl = "", container = null, plot = null,
    )

    /** Historique d'un épisode : [positionMs] / [durationMs]. */
    fun history(season: Int, episode: Int, positionMs: Long = 1_000_000, durationMs: Long = 2_900_000) = WatchHistoryEntity(
        providerId = 1, kind = "EPISODE", remoteId = "e$season.$episode", title = "Titre$episode", poster = null, streamUrl = "",
        positionMs = positionMs, durationMs = durationMs,
    )

    /**
     * Série de [seasons] saisons de [episodesPerSeason] épisodes (« Titre1 »…). [activeSeason] : saison affichée ;
     * [resume] : épisode à reprendre (saison, numéro), null = rien à reprendre.
     */
    fun series(seasons: Int, episodesPerSeason: Int, activeSeason: Int = 1, resume: Pair<Int, Int>? = null, trailer: Boolean = true): SeriesDetailData {
        val eps = (1..seasons).flatMap { sn -> (1..episodesPerSeason).map { n -> episode(sn, n) } }
        val target = resume?.let { (s, e) -> eps.first { it.season == s && it.episode == e } }
        return SeriesDetailData(
            series = entity(), episodes = eps, seasons = (1..seasons).toList(), activeSeason = activeSeason, target = target, resuming = target != null,
            progress = resume?.let { (s, e) -> mapOf("e$s.$e" to history(s, e)) } ?: emptyMap(), traktSeen = emptySet(), ratings = null,
            trailerUrl = if (trailer) "dQw4w9WgXcQ" else null, downloads = emptyMap(), loading = false,
        )
    }
}
