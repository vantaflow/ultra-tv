package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.ui.player.zap.ZapLogic
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

    /** Chaîne suivante / précédente de la catégorie en cours, en SAUTANT les séparateurs. */
    fun next(): ChannelEntity? = move(true)

    fun previous(): ChannelEntity? = move(false)

    private fun move(forward: Boolean): ChannelEntity? {
        val s = _state.value ?: return null
        val i = ZapLogic.step(s.channels, s.index, forward) ?: return null
        _state.value = s.copy(index = i)
        return s.channels[i]
    }

    /** Saute à la chaîne de numéro [number] dans la catégorie en cours ; null si aucune. */
    fun jumpToNumber(number: Int): ChannelEntity? {
        val s = _state.value ?: return null
        val i = ZapLogic.byNumber(s.channels, number) ?: return null
        _state.value = s.copy(index = i)
        return s.channels[i]
    }

    /** Chaîne cherchée par numéro, sans changer l'index (aperçu pendant la saisie). */
    fun peekNumber(number: Int): ChannelEntity? {
        val s = _state.value ?: return null
        return ZapLogic.byNumber(s.channels, number)?.let { s.channels[it] }
    }

    fun clear() { _state.value = null }
}
