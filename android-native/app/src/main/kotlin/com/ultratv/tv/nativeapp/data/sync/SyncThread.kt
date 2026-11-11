package com.ultratv.tv.nativeapp.data.sync

/**
 * Priorité « arrière-plan » pour le travail lourd de la synchro (analyse JSON / XML, conversion, écriture Room, copie réseau).
 * Sur une box à 4 petits cœurs, ces fils tournaient à la même priorité que le fil d'interface : l'écran saccadait
 * (« Davey! duration=1–3 s ») pendant toute la synchro. En priorité d'arrière-plan, l'ordonnanceur donne la main à
 * l'interface dès qu'elle a une image à produire. La priorité d'origine du fil (partagé, issu d'un pool) est restaurée.
 */
object SyncThread {
    inline fun <T> background(block: () -> T): T {
        val previous = runCatching { android.os.Process.getThreadPriority(android.os.Process.myTid()) }.getOrNull()
        runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND) }
        try {
            return block()
        } finally {
            if (previous != null) runCatching { android.os.Process.setThreadPriority(previous) }
        }
    }
}
