package com.ultratv.tv.nativeapp.data.memory

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Tampon de pause du direct : dossier et indicateur « en cours d'utilisation » (le vider pendant la lecture la casserait). */
object TimeshiftRegistry {
    const val DIR_NAME = "timeshift"
    @Volatile var active: Boolean = false
}

/**
 * Réglages de « Mémoire et stockage », à part des préférences générales (SharedPreferences : lus de façon synchrone
 * au démarrage par la fabrique d'images de Coil).
 */
@Singleton
class MemoryPrefs @Inject constructor(@ApplicationContext ctx: Context) {
    data class Values(
        val imageCache: String = ImageCacheLimit.AUTO,
        val autoClean: Boolean = true,
        val economy: String = EconomyMode.AUTO,
    )

    private val sp = ctx.getSharedPreferences("memory_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Values> = _state.asStateFlow()

    private fun read() = Values(
        imageCache = sp.getString(KEY_CACHE, ImageCacheLimit.AUTO).let { v -> if (v?.toIntOrNull() in ImageCacheLimit.CHOICES_MB) v!! else ImageCacheLimit.AUTO },
        autoClean = sp.getBoolean(KEY_AUTO_CLEAN, true),
        economy = EconomyMode.normalize(sp.getString(KEY_ECONOMY, EconomyMode.AUTO)),
    )

    fun setImageCache(v: String) { sp.edit().putString(KEY_CACHE, v).apply(); _state.value = read() }
    fun setAutoClean(v: Boolean) { sp.edit().putBoolean(KEY_AUTO_CLEAN, v).apply(); _state.value = read() }
    fun setEconomy(v: String) { sp.edit().putString(KEY_ECONOMY, EconomyMode.normalize(v)).apply(); _state.value = read() }

    private companion object {
        const val KEY_CACHE = "image_cache"
        const val KEY_AUTO_CLEAN = "auto_clean"
        const val KEY_ECONOMY = "economy"
    }
}

/** RAM de l'appareil (appel système léger, sans I/O disque). */
fun Context.ramState(): RamState {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    val mi = android.app.ActivityManager.MemoryInfo().also(am::getMemoryInfo)
    return RamState(mi.totalMem, mi.availMem, am.isLowRamDevice)
}
