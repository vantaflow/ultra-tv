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
        /** Partie en cours + compteur : l'interface compose elle-même un libellé localisé. */
        val part: com.ultratv.tv.nativeapp.data.db.SyncPart? = null,
        val count: Int? = null,
    )

    private val _status = MutableStateFlow<Status?>(null)
    val status: StateFlow<Status?> = _status

    fun set(s: Status) { _status.value = s }
    fun clear() { _status.value = null }

    /** Dernier échec de synchro d'une source (null = tout va bien). Alimente la bannière d'erreur. */
    data class Failure(val providerId: Long, val provider: String, val kind: com.ultratv.tv.nativeapp.data.net.SyncErrorKind)

    private val _failure = MutableStateFlow<Failure?>(null)
    val failure: StateFlow<Failure?> = _failure
    fun fail(f: Failure) { _failure.value = f }
    fun clearFailure(providerId: Long? = null) {
        val cur = _failure.value ?: return
        if (providerId == null || cur.providerId == providerId) _failure.value = null
    }

    /** Synchro réussie mais incomplète : [skipped] catégories sautées (en erreur, trop lentes ou vides), contenu précédent gardé. */
    data class Partial(val providerId: Long, val provider: String, val skipped: Int)

    private val _partial = MutableStateFlow<Partial?>(null)
    val partial: StateFlow<Partial?> = _partial
    fun setPartial(p: Partial) { _partial.value = p }
    fun clearPartial(providerId: Long? = null) {
        val cur = _partial.value ?: return
        if (providerId == null || cur.providerId == providerId) _partial.value = null
    }

    /** État du GUIDE seul (chargement avec progression, échec avec raison) : lu par le guide et la première synchro. */
    private val _epg = MutableStateFlow<com.ultratv.tv.nativeapp.data.xmltv.EpgState>(com.ultratv.tv.nativeapp.data.xmltv.EpgState.Idle)
    val epg: StateFlow<com.ultratv.tv.nativeapp.data.xmltv.EpgState> = _epg
    fun setEpg(s: com.ultratv.tv.nativeapp.data.xmltv.EpgState) { _epg.value = s }
}
