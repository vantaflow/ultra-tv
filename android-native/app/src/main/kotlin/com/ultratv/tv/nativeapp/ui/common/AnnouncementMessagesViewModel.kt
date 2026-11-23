package com.ultratv.tv.nativeapp.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ultratv.tv.nativeapp.data.announce.AnnouncementFeed
import com.ultratv.tv.nativeapp.data.announce.AnnouncementRepository
import com.ultratv.tv.nativeapp.data.announce.FetchOutcome
import com.ultratv.tv.nativeapp.data.announce.ReceivedAnnouncement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RefreshError { OFFLINE, UNREACHABLE }

/** État du bouton « Rafraîchir » : appel en cours, dernier échec, dernière vérification réussie (auto ou manuelle). */
data class MessagesRefreshUi(val refreshing: Boolean = false, val error: RefreshError? = null, val lastCheckAt: Long? = null)

/**
 * Réglages › Messages : historique des alertes reçues et bouton « Rafraîchir » qui lit tout de suite les alertes au
 * Worker. La nouvelle alerte éventuelle est affichée par [AnnouncementHost] (même flux, mêmes règles) ; la vérification
 * automatique des 15 minutes n'est pas touchée.
 */
@HiltViewModel
class AnnouncementMessagesViewModel(
    private val feed: AnnouncementFeed,
    val history: StateFlow<List<ReceivedAnnouncement>>,
) : ViewModel() {
    @Inject constructor(feed: AnnouncementFeed, repo: AnnouncementRepository) : this(feed, repo.historyFlow)

    private val state = MutableStateFlow(MessagesRefreshUi(lastCheckAt = feed.lastCheckAt.value))
    val ui: StateFlow<MessagesRefreshUi> = state.asStateFlow()

    init {
        viewModelScope.launch { feed.lastCheckAt.collect { at -> state.update { it.copy(lastCheckAt = at) } } }
    }

    /** Sans effet pendant une actualisation (pas de double appel). */
    fun refreshNow(audience: String) {
        if (state.value.refreshing) return
        state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val error = when (feed.check(audience)) {
                is FetchOutcome.Ok -> null
                FetchOutcome.Offline -> RefreshError.OFFLINE
                FetchOutcome.Unreachable -> RefreshError.UNREACHABLE
            }
            state.update { it.copy(refreshing = false, error = error, lastCheckAt = feed.lastCheckAt.value) }
        }
    }
}
