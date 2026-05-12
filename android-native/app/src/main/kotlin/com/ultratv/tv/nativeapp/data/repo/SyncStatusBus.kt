package com.ultratv.tv.nativeapp.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global sync-progress feed. Whoever runs a sync (ProviderRepository) reports
 * progress here; the UI subscribes from a single overlay banner so the user
 * sees status from any screen (Home, Live, etc), not just Settings.
 *
 * `null` = nothing running.
 */
@Singleton
class SyncStatusBus @Inject constructor() {
    data class Status(
        val provider: String,
        val step: String,
        val percent: Int? = null,        // null if unknown
    )

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status

    fun set(s: Status) { _status.value = s }
    fun clear() { _status.value = null }
}
