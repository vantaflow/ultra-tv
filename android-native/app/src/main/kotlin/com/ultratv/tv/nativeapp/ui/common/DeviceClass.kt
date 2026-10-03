package com.ultratv.tv.nativeapp.ui.common

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.compositionLocalOf

/**
 * Classe de machine : « entrée de gamme » = ActivityManager.isLowRamDevice() ou
 * moins de 2 Go de RAM (Chromecast HD, box Amlogic S905 à 1-2 Go). Sert à retirer
 * les animations et à réduire caches et tampons.
 */
object DeviceClass {
    const val LOW_RAM_BYTES = 2L * 1024 * 1024 * 1024

    fun isLowRam(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        if (am.isLowRamDevice) return true
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        return isLowRam(info.totalMem, false)
    }

    /** Pure, testable. */
    fun isLowRam(totalMemBytes: Long, lowRamFlag: Boolean): Boolean =
        lowRamFlag || totalMemBytes in 1 until LOW_RAM_BYTES + 256L * 1024 * 1024
}

/** Vrai sur les machines entrée de gamme : pas d'animations décoratives. */
val LocalLowRam = compositionLocalOf { false }
