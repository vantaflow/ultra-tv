package com.ultratv.tv.nativeapp.update

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Canal de mise à jour (Réglages › À propos) : « Stable » (défaut : seulement la release « latest », comportement d'avant)
 * ou « Pré-version » (pré-versions « -rcN » comprises, la plus récente des deux). Même paquet et même signature : une
 * pré-version s'installe par-dessus la version installée, et inversement.
 */
object UpdateChannel {
    private const val PREFS = "update_channel"
    private const val KEY = "prerelease"

    private val _preRelease = MutableStateFlow(false)
    val preRelease: StateFlow<Boolean> = _preRelease.asStateFlow()

    /** Lu au démarrage de l'appli (avant la première recherche de mise à jour). */
    fun init(ctx: Context) {
        _preRelease.value = runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false) }.getOrDefault(false)
    }

    fun set(ctx: Context, preRelease: Boolean) {
        _preRelease.value = preRelease
        runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, preRelease).apply() }
        // Une mise à jour trouvée sur l'autre canal n'est plus d'actualité.
        UpdateChecker.clear()
    }

    /** Version installée, d'après le numéro de version et l'étiquette de publication embarquée. */
    fun installed(): UpdateVersions.Version? =
        UpdateVersions.installed(com.ultratv.tv.nativeapp.BuildConfig.VERSION_NAME, com.ultratv.tv.nativeapp.BuildConfig.RELEASE_TAG)
}
