package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Textes du mode simple : écran des grandes tuiles, liste de chaînes à gros caractères, réglage (EN / FR / ES / AR). */
class SimpleStrings(private val lang: AppLang) {
    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)
    private fun tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)

    // ── Écran des tuiles ──
    val title get() = tx(R.string.simple_title)
    val tileLive get() = tx(R.string.simple_tile_live)
    val tileResume get() = tx(R.string.simple_tile_resume)
    val tileMovies get() = tx(R.string.simple_tile_movies)
    val tileSeries get() = tx(R.string.simple_tile_series)
    val tileFavorites get() = tx(R.string.simple_tile_favorites)
    val tileSearch get() = tx(R.string.simple_tile_search)
    val tileLiveHint get() = tx(R.string.simple_tile_live_hint)
    val tileResumeHint get() = tx(R.string.simple_tile_resume_hint)
    val tileResumeNothing get() = tx(R.string.simple_tile_resume_nothing)
    val tileMoviesHint get() = tx(R.string.simple_tile_movies_hint)
    val tileSeriesHint get() = tx(R.string.simple_tile_series_hint)
    val tileFavoritesHint get() = tx(R.string.simple_tile_favorites_hint)
    val tileSearchHint get() = tx(R.string.simple_tile_search_hint)
    val settings get() = tx(R.string.simple_settings)
    val switchProfile get() = tx(R.string.simple_switch_profile)
    val noSource get() = tx(R.string.simple_no_source)
    val loading get() = tx(R.string.simple_loading)

    // ── Liste des chaînes ──
    val channelsTitle get() = tx(R.string.simple_channels_title)
    val channelsFavoritesFirst get() = tx(R.string.simple_channels_favorites_first)
    val channelsEmpty get() = tx(R.string.simple_channels_empty)
    val favoriteMark get() = tx(R.string.simple_favorite_mark)
    val allChannels get() = tx(R.string.simple_all_channels)
    val keyOpen get() = tx(R.string.simple_key_open)
    val keyBack get() = tx(R.string.simple_key_back)
    val keyWatch get() = tx(R.string.simple_key_watch)
    val keyChange get() = tx(R.string.simple_key_change)
    val nowLabel get() = tx(R.string.simple_now)
    val nextLabel get() = tx(R.string.simple_next)
    val tabAll get() = tx(R.string.simple_tab_all)
    val tabNew get() = tx(R.string.simple_tab_new)
    val tabTop get() = tx(R.string.simple_tab_top)
    val tabAz get() = tx(R.string.simple_tab_az)
    val nextEpisode get() = tx(R.string.simple_next_episode)
    val season get() = tx(R.string.simple_season)
    val watched get() = tx(R.string.simple_watched)
    val myChannels get() = tx(R.string.simple_my_channels)
    val myTitles get() = tx(R.string.simple_my_titles)
    val say get() = tx(R.string.simple_say)
    val found get() = tx(R.string.simple_found)
    val orType get() = tx(R.string.simple_or_type)
    val holdFav get() = tx(R.string.simple_hold_fav)
    val holdRemove get() = tx(R.string.simple_hold_remove)

    // ── Réglages ──
    val settingName get() = tx(R.string.simple_setting_name)
    val settingHint get() = tx(R.string.simple_setting_hint)
    val settingHelp get() = tx(R.string.simple_setting_help)
    val exitTitle get() = tx(R.string.simple_exit_title)
    val profileOn get() = tx(R.string.simple_profile_on)
    val profileOff get() = tx(R.string.simple_profile_off)
    val on get() = tx(R.string.simple_on)
    val off get() = tx(R.string.simple_off)
}
