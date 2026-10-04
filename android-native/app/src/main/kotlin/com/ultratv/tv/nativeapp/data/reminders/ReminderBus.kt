package com.ultratv.tv.nativeapp.data.reminders

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Rappel arrivé à échéance : la bannière in-app (« Commence dans 1 min · Regarder ») l'écoute. */
data class ReminderEvent(
    val providerId: Long,
    val channelRemoteId: String,
    val channelName: String,
    val programmeTitle: String,
    val startMs: Long,
)

object ReminderBus {
    private val _events = MutableSharedFlow<ReminderEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<ReminderEvent> = _events.asSharedFlow()
    fun emit(e: ReminderEvent) { _events.tryEmit(e) }
}
