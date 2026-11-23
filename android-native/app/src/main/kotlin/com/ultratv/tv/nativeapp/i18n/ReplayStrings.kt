package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés de la rubrique Replay (chaînes à archive, jours, programmes passés). Fichier à part : aucun conflit avec [DesignStrings]. */
val DesignStrings.replayNav get() = tx(R.string.replay_nav)
val DesignStrings.replayAllChannels get() = tx(R.string.replay_all_channels)
val DesignStrings.replayCategory get() = tx(R.string.replay_category)
val DesignStrings.replayToday get() = tx(R.string.replay_today)
val DesignStrings.replayYesterday get() = tx(R.string.replay_yesterday)
fun DesignStrings.replayArchiveDays(days: Int) = tx(R.string.replay_archive_days, days)
fun DesignStrings.replayMinutes(minutes: Int) = tx(R.string.replay_minutes, minutes)
val DesignStrings.replayUnavailable get() = tx(R.string.replay_unavailable)
val DesignStrings.replayOnNow get() = tx(R.string.replay_on_now)
val DesignStrings.replayPickChannel get() = tx(R.string.replay_pick_channel)
val DesignStrings.replayNoProgrammes get() = tx(R.string.replay_no_programmes)
val DesignStrings.replayEmptyTitle get() = tx(R.string.replay_empty_title)
val DesignStrings.replayEmptyBody get() = tx(R.string.replay_empty_body)
val DesignStrings.replayHiddenBody get() = tx(R.string.replay_hidden_body)
val DesignStrings.replayNoSource get() = tx(R.string.replay_no_source)
