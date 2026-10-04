package com.ultratv.tv.nativeapp.data.recording

import com.ultratv.tv.nativeapp.data.db.EpgEntity

/** Règles pures de l'enregistrement programmé (testables sans Android). */
object RecordingPlan {
    /** Débit moyen supposé d'un flux direct (~5 Mb/s) pour estimer la place nécessaire. */
    const val BYTES_PER_MINUTE = 40L * 1024 * 1024
    /** Espace libre à toujours conserver pour le système. */
    const val FREE_MARGIN_BYTES = 500L * 1024 * 1024
    const val PAD_BEFORE_MS = 60_000L
    const val PAD_AFTER_MS = 2 * 60_000L

    fun estimateBytes(durationMs: Long): Long = durationMs.coerceAtLeast(0) / 60_000L * BYTES_PER_MINUTE

    fun hasRoom(freeBytes: Long, durationMs: Long): Boolean = freeBytes >= estimateBytes(durationMs) + FREE_MARGIN_BYTES

    fun normalizedTitle(t: String): String = t.trim().lowercase().replace(Regex("\\s+"), " ")

    /** « Série entière » : mêmes titre et chaîne (la liste fournie est celle de la chaîne), diffusions à venir. */
    fun seriesMatches(title: String, channelProgrammes: List<EpgEntity>, nowMs: Long): List<EpgEntity> {
        val key = normalizedTitle(title)
        return channelProgrammes
            .filter { it.startMs > nowMs && normalizedTitle(it.title) == key }
            .sortedBy { it.startMs }
    }

    /** Fenêtre réellement enregistrée : un peu avant et après pour absorber les écarts de guide. */
    fun paddedWindow(startMs: Long, endMs: Long): Pair<Long, Long> = (startMs - PAD_BEFORE_MS) to (endMs + PAD_AFTER_MS)
}

enum class ConnectionWarning { NONE, PLAYBACK_TAKES_CONNECTION, RECORDING_TAKES_CONNECTION }

/**
 * Un enregistrement en cours occupe une connexion du compte. Avec une seule connexion
 * (max_connections = 1, valeur retenue tant qu'elle n'est pas lue de la source), lecture et
 * enregistrement ne peuvent pas coexister : on prévient l'utilisateur dans les deux sens.
 */
object ConnectionPolicy {
    const val DEFAULT_MAX_CONNECTIONS = 1

    fun onPlayRequested(recordingRunning: Boolean, maxConnections: Int = DEFAULT_MAX_CONNECTIONS): ConnectionWarning =
        if (recordingRunning && maxConnections <= 1) ConnectionWarning.PLAYBACK_TAKES_CONNECTION else ConnectionWarning.NONE

    fun onRecordingStart(playing: Boolean, maxConnections: Int = DEFAULT_MAX_CONNECTIONS): ConnectionWarning =
        if (playing && maxConnections <= 1) ConnectionWarning.RECORDING_TAKES_CONNECTION else ConnectionWarning.NONE
}
