package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Libellés de l'écran Réglages du mode simple. Fichier à part : [DesignStrings] et [SimpleStrings] restent intacts. */
val DesignStrings.simpleBackFull get() = tx(R.string.simple_back_full)
val DesignStrings.simpleAllSettings get() = tx(R.string.simple_all_settings)
val DesignStrings.simpleFullHint get() = tx(R.string.simple_full_hint)
val DesignStrings.simpleSize get() = tx(R.string.simple_size)
fun DesignStrings.simpleSizeName(s: com.ultratv.tv.nativeapp.ui.simple.SimpleSize) = tx(when (s) {
    com.ultratv.tv.nativeapp.ui.simple.SimpleSize.SMALL -> R.string.simple_size_small
    com.ultratv.tv.nativeapp.ui.simple.SimpleSize.NORMAL -> R.string.simple_size_normal
    com.ultratv.tv.nativeapp.ui.simple.SimpleSize.LARGE -> R.string.simple_size_large
})
