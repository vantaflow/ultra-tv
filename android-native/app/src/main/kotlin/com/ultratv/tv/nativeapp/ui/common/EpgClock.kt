package com.ultratv.tv.nativeapp.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared "what should the clock say for this EPG timestamp" helper.
 *
 * Stores the user's `epgTimeOffsetMin` preference at the process level so any
 * Compose layer can format programme start/end times without having to thread
 * the offset through every view-model. The pref is mirrored from DataStore by
 * [UltraTvApp] on every change.
 */
object EpgClock {
    @Volatile var offsetMinutes: Int = 0

    private val fmt = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    fun apply(ms: Long): Long = ms + offsetMinutes * 60_000L

    fun hm(ms: Long): String = fmt.get()!!.format(Date(apply(ms)))
}
