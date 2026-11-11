package com.ultratv.tv.nativeapp.ui.player.osd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.DesignStrings
import com.ultratv.tv.nativeapp.i18n.I18n
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.i18n.Strings
import com.ultratv.tv.nativeapp.ui.player.PlayerExtraStrings
import com.ultratv.tv.nativeapp.ui.player.engine.AspectMode

/**
 * Textes propres à l'OSD du Direct (fichiers strings.xml de chaque langue, clés `osd_...`). Les autres libellés de la barre
 * (Pistes, Sous-titres, Enregistrer, Veille, favoris, image dans l'image, raccourcis…) réutilisent les clés existantes :
 * voir [labels]. Holder à part de [PlayerExtraStrings] : il ne bouge pas la photo de référence des tests de traduction.
 */
class LiveOsdStrings(val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)

    val recentChannels get() = tx(R.string.osd_recent_channels)
    val pictureFormat get() = tx(R.string.osd_picture_format)
    val history get() = tx(R.string.osd_history)
    val historyEmpty get() = tx(R.string.osd_history_empty)
    /** « reste 34 min » : [duration] est déjà formatée (« 34 min », « 1 h 05 »). */
    fun remaining(duration: String) = tx(R.string.osd_remaining, duration)
    /** Aide du simple bandeau (après un zap) : comment atteindre les commandes. */
    val bannerHint get() = tx(R.string.osd_banner_hint)
    /** Liste des chaînes du lecteur : première entrée, aide des touches, message de Gauche sans chaîne précédente. */
    val drawerAll get() = tx(R.string.osd_drawer_all)
    val drawerHint get() = tx(R.string.osd_drawer_hint)
    val noPreviousChannel get() = tx(R.string.osd_no_previous)

    // Réutilisés tels quels (une seule source de vérité par texte).
    val pip get() = tx(R.string.mob_pip)
    val myList get() = tx(R.string.mob_my_list)
    val play get() = tx(R.string.mob_a11y_play)
    val pause get() = tx(R.string.mob_a11y_pause)

    /** Libellés de la barre, assemblés depuis les holders existants. */
    fun labels(D: DesignStrings, X: PlayerExtraStrings, S: Strings) = OsdLabels(
        play = play, pause = pause, audio = D.pTracks, subs = X.subsPill, aspect = pictureFormat,
        aspectNames = aspectNames(D), channels = D.pChannels, history = history,
        addFavorite = D.addFavorite, removeFavorite = D.removeFavorite, record = D.pRecord, pip = pip,
        sleep = X.sleepPill, player = D.pPlayer, myList = myList, movies = S.navMovies, series = S.navSeries,
        recordings = S.navRecordings, settings = D.settingsTitle,
    )

    fun aspectNames(D: DesignStrings): Map<AspectMode, String> = mapOf(
        AspectMode.FIT to D.aspectFit, AspectMode.FILL to D.aspectFill, AspectMode.ZOOM to D.aspectZoom,
        AspectMode.R16_9 to "16:9", AspectMode.R4_3 to "4:3",
    )
}

@Composable
fun liveOsdStrings(): LiveOsdStrings {
    val lang = LocalDs.current.lang
    return remember(lang) { LiveOsdStrings(lang) }
}
