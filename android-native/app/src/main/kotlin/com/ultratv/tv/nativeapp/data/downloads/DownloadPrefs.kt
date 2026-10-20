package com.ultratv.tv.nativeapp.data.downloads

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Réglages des téléchargements (Réglages › Lecture) : Wi-Fi seulement, emplacement. Préférences à part de
 * UserPreferences (DataStore) : lues de façon synchrone par le travailleur et par le choix du dossier.
 */
@Singleton
class DownloadPrefs @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    data class State(val wifiOnly: Boolean, val location: String)

    private val _state = MutableStateFlow(State(sp.getBoolean(KEY_WIFI, false), sp.getString(KEY_LOCATION, LOCATION_EXTERNAL) ?: LOCATION_EXTERNAL))
    val state: StateFlow<State> = _state.asStateFlow()

    fun setWifiOnly(v: Boolean) { sp.edit().putBoolean(KEY_WIFI, v).apply(); _state.value = _state.value.copy(wifiOnly = v) }

    fun setLocation(v: String) {
        val loc = if (v == LOCATION_INTERNAL) LOCATION_INTERNAL else LOCATION_EXTERNAL
        sp.edit().putString(KEY_LOCATION, loc).apply(); _state.value = _state.value.copy(location = loc)
    }

    /**
     * Dossier des NOUVEAUX téléchargements : stockage externe propre à l'appli (aucune permission, effacé à la
     * désinstallation, invisible des autres applis) ; repli sur le stockage interne s'il est absent (carte retirée, box
     * sans stockage externe). Les fichiers déjà téléchargés restent où ils sont (chemin absolu en base).
     */
    fun directory(): File = resolveDirectory(_state.value.location, ctx.getExternalFilesDir(null), ctx.filesDir)

    /** Externe disponible sur cet appareil ? (sinon le réglage est sans effet et l'interne est utilisé). */
    fun externalAvailable(): Boolean = ctx.getExternalFilesDir(null) != null

    companion object {
        const val LOCATION_EXTERNAL = "external"
        const val LOCATION_INTERNAL = "internal"
        private const val KEY_WIFI = "wifi_only"
        private const val KEY_LOCATION = "location"
        const val DIR_NAME = "downloads"

        fun resolveDirectory(location: String, external: File?, internal: File): File {
            val base = if (location == LOCATION_INTERNAL || external == null) internal else external
            return File(base, DIR_NAME)
        }
    }
}
