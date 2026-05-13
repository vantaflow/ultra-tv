package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the live-TV channel list the user was browsing right before opening
 * the player, plus the currently-playing index. Lets the player implement
 * D-pad UP / DOWN zap without re-querying Room every keypress.
 *
 * Cleared when entering a non-LIVE PlaybackContext (movies / episodes
 * shouldn't zap).
 */
@Singleton
class LivePlaybackQueue @Inject constructor() {
    data class State(val channels: List<ChannelEntity>, val index: Int)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    fun set(channels: List<ChannelEntity>, current: ChannelEntity) {
        val idx = channels.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        _state.value = State(channels, idx)
    }

    fun next(): ChannelEntity? {
        val s = _state.value ?: return null
        if (s.channels.isEmpty()) return null
        val i = (s.index + 1) % s.channels.size
        _state.value = s.copy(index = i)
        return s.channels[i]
    }

    fun previous(): ChannelEntity? {
        val s = _state.value ?: return null
        if (s.channels.isEmpty()) return null
        val i = (s.index - 1 + s.channels.size) % s.channels.size
        _state.value = s.copy(index = i)
        return s.channels[i]
    }

    fun clear() { _state.value = null }
}
