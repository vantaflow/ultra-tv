package com.ultratv.tv.nativeapp.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ultratv.tv.nativeapp.i18n.AppLang
import com.ultratv.tv.nativeapp.i18n.LocalDs
import com.ultratv.tv.nativeapp.R
import com.ultratv.tv.nativeapp.i18n.I18n

/**
 * Libellés des fonctions du lot B2 (zapping, veille, replay, timeshift, sous-titres), à part de
 * [com.ultratv.tv.nativeapp.i18n.DesignStrings] pour ne pas se marcher dessus avec les autres chantiers.
 */
class PlayerExtraStrings(val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)
    private fun tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)

    // Zapping
    val recent get() = tx(R.string.pex_recent)
    val zapHint get() = tx(R.string.pex_zap_hint)
    val noChannelNumber get() = tx(R.string.pex_no_channel_number)

    // Passer l'intro (TheIntroDB)
    val skipIntro get() = tx(R.string.pex_skip_intro)
    val skipRecap get() = tx(R.string.pex_skip_recap)
    val skipCredits get() = tx(R.string.pex_skip_credits)
    val nextEpisode get() = tx(R.string.pex_next_episode)

    // Replay
    val fromStart get() = tx(R.string.pex_from_start)
    val backToLive get() = tx(R.string.pex_back_to_live)

    // Sous-titres
    val subsPill get() = tx(R.string.pex_subs_pill)
    val subtitlesTitle get() = tx(R.string.pex_subtitles_title)
    val previewText get() = tx(R.string.pex_preview_text)
    val off get() = tx(R.string.pex_off)
    val track get() = tx(R.string.pex_track)
    val size get() = tx(R.string.pex_size)
    val color get() = tx(R.string.pex_color)
    val background get() = tx(R.string.pex_background)
    val outline get() = tx(R.string.pex_outline)
    val position get() = tx(R.string.pex_position)
    val delay get() = tx(R.string.pex_delay)
    val delayVlcOnly get() = tx(R.string.pex_delay_vlc_only)
    val audioLangs get() = tx(R.string.pex_audio_langs)
    val textLangs get() = tx(R.string.pex_text_langs)
    val langOrderHint get() = tx(R.string.pex_lang_order_hint)
    val searchOnline get() = tx(R.string.pex_search_online)
    val searchNeedsProxy get() = tx(R.string.pex_search_needs_proxy)
    val liveNoSubtitles get() = tx(R.string.pex_live_no_subtitles)
    val searchMoviesOnly get() = tx(R.string.pex_search_movies_only)
    val subtitlesNotConfigured get() = tx(R.string.pex_subtitles_not_configured)
    val searching get() = tx(R.string.pex_searching)
    val noSubtitleFound get() = tx(R.string.pex_no_subtitle_found)
    val subtitleAdded get() = tx(R.string.pex_subtitle_added)
    val back get() = tx(R.string.pex_back)
    fun sizeName(v: com.ultratv.tv.nativeapp.data.subtitles.SubSize) = when (v) {
        com.ultratv.tv.nativeapp.data.subtitles.SubSize.SMALL -> tx(R.string.pex_size_name_small)
        com.ultratv.tv.nativeapp.data.subtitles.SubSize.MEDIUM -> tx(R.string.pex_size_name_medium)
        com.ultratv.tv.nativeapp.data.subtitles.SubSize.LARGE -> tx(R.string.pex_size_name_large)
        com.ultratv.tv.nativeapp.data.subtitles.SubSize.XLARGE -> tx(R.string.pex_size_name_xlarge)
    }
    fun colorName(v: com.ultratv.tv.nativeapp.data.subtitles.SubColor) = when (v) {
        com.ultratv.tv.nativeapp.data.subtitles.SubColor.WHITE -> tx(R.string.pex_color_name_white)
        com.ultratv.tv.nativeapp.data.subtitles.SubColor.YELLOW -> tx(R.string.pex_color_name_yellow)
        com.ultratv.tv.nativeapp.data.subtitles.SubColor.CYAN -> tx(R.string.pex_color_name_cyan)
        com.ultratv.tv.nativeapp.data.subtitles.SubColor.GREEN -> tx(R.string.pex_color_name_green)
    }
    fun backgroundName(v: com.ultratv.tv.nativeapp.data.subtitles.SubBackground) = when (v) {
        com.ultratv.tv.nativeapp.data.subtitles.SubBackground.NONE -> tx(R.string.pex_background_name_none)
        com.ultratv.tv.nativeapp.data.subtitles.SubBackground.SEMI -> tx(R.string.pex_background_name_semi)
        com.ultratv.tv.nativeapp.data.subtitles.SubBackground.OPAQUE -> tx(R.string.pex_background_name_opaque)
    }
    fun outlineName(v: com.ultratv.tv.nativeapp.data.subtitles.SubOutline) = when (v) {
        com.ultratv.tv.nativeapp.data.subtitles.SubOutline.NONE -> tx(R.string.pex_outline_name_none)
        com.ultratv.tv.nativeapp.data.subtitles.SubOutline.THIN -> tx(R.string.pex_outline_name_thin)
        com.ultratv.tv.nativeapp.data.subtitles.SubOutline.THICK -> tx(R.string.pex_outline_name_thick)
    }
    fun positionName(v: com.ultratv.tv.nativeapp.data.subtitles.SubPosition) = when (v) {
        com.ultratv.tv.nativeapp.data.subtitles.SubPosition.BOTTOM -> tx(R.string.pex_position_name_bottom)
        com.ultratv.tv.nativeapp.data.subtitles.SubPosition.RAISED -> tx(R.string.pex_position_name_raised)
        com.ultratv.tv.nativeapp.data.subtitles.SubPosition.HIGH -> tx(R.string.pex_position_name_high)
    }

    // Pause du direct
    val delayed get() = tx(R.string.pex_delayed)
    val directMark get() = tx(R.string.pex_direct_mark)
    val tsHls get() = tx(R.string.pex_ts_hls)
    val tsNoSpace get() = tx(R.string.pex_ts_no_space)
    val tsFailed get() = tx(R.string.pex_ts_failed)

    // Fiche programme, « Ensuite », pistes
    val infoNow get() = tx(R.string.pex_info_now)
    val infoNoGuide get() = tx(R.string.pex_info_no_guide)
    val thenWord get() = tx(R.string.pex_then_word)
    val audioPill get() = tx(R.string.pex_audio_pill)
    val settingsPill get() = tx(R.string.pex_settings_pill)

    // Veille
    val sleepPill get() = tx(R.string.pex_sleep_pill)
    /** Bandeau du direct (après un zap) : les commandes ne sont pas encore actives, on dit comment les atteindre. */
    val bannerHint get() = tx(R.string.pex_banner_hint)
    val sleepTitle get() = tx(R.string.pex_sleep_title)
    val sleepEndOfProgramme get() = tx(R.string.pex_sleep_end_of_programme)
    val sleepOff get() = tx(R.string.pex_sleep_off)
    val stillThere get() = tx(R.string.pex_still_there)
    val imHere get() = tx(R.string.pex_im_here)
    val sleepNow get() = tx(R.string.pex_sleep_now)

    // Surcouche film / épisode
    val statusPlaying get() = tx(R.string.pex_status_playing)
    val statusPaused get() = tx(R.string.pex_status_paused)
    val statusLoading get() = tx(R.string.pex_status_loading)
    fun endsAt(time: String) = tx(R.string.pex_ends_at, time)
    val previousEpisode get() = tx(R.string.pex_previous_episode)
    val close get() = tx(R.string.pex_close)
    fun sleepStopsIn(s: Int) = tx(R.string.pex_sleep_stops_in, s)
}

@Composable
fun playerExtras(): PlayerExtraStrings {
    val lang = LocalDs.current.lang
    return remember(lang) { PlayerExtraStrings(lang) }
}
