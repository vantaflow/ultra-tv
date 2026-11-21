package com.ultratv.tv.nativeapp.ui.home

/** Retour à l'accueil par le menu latéral : l'accueil s'ouvre comme à neuf (voir [HomeFocusRestore]). */
internal object HomeFreshStart {
    @Volatile private var pending = false
    fun mark() { pending = true }
    fun consume(): Boolean { val v = pending; pending = false; return v }
}
