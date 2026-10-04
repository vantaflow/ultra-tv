package com.ultratv.tv.nativeapp.ui.player.replay

import androidx.lifecycle.ViewModel
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.replay.ReplayAvailability
import com.ultratv.tv.nativeapp.data.replay.ReplayService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Accès du lecteur au replay (« Depuis le début » sur le programme en cours). */
@HiltViewModel
class ReplayViewModel @Inject constructor(private val service: ReplayService) : ViewModel() {
    suspend fun canReplay(programme: EpgEntity?): Boolean =
        programme != null && service.availability(programme) == ReplayAvailability.AVAILABLE

    suspend fun urlFor(programme: EpgEntity): String? = service.urlFor(programme)

    /** Échec du flux de replay : renvoie la nouvelle URL à essayer (autre forme), ou null. */
    suspend fun retryUrl(programme: EpgEntity, providerId: Long): String? =
        if (service.onReplayFailed(providerId)) service.urlFor(programme) else null

    fun takePending() = service.takePending()

    fun worked(providerId: Long) = service.onReplayWorked(providerId)
}
