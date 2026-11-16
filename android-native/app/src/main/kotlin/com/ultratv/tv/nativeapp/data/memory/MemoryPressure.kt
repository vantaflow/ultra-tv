package com.ultratv.tv.nativeapp.data.memory

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pression mémoire : logique pure (sans Android, testée en JVM) et registre des caches à vider.
 *
 * Le système appelle `onTrimMemory(niveau)` / `onLowMemory()` quand la mémoire manque (fréquent sur les téléviseurs, dont
 * le tas Java tient parfois en 128 Mo). Chaque cache en mémoire s'inscrit ici avec la gravité à partir de laquelle il se
 * vide ; l'application relaie les appels système vers [trim].
 */
object MemoryPressure {

    enum class Severity { NONE, LIGHT, MODERATE, CRITICAL }

    // Valeurs de ComponentCallbacks2.TRIM_MEMORY_* (constantes publiques de l'API, recopiées pour rester testable en JVM).
    private const val RUNNING_MODERATE = 5
    private const val RUNNING_LOW = 10
    private const val RUNNING_CRITICAL = 15
    private const val UI_HIDDEN = 20
    private const val BACKGROUND = 40
    private const val COMPLETE = 80

    /**
     * Gravité d'un niveau `onTrimMemory`. Sur appareil à mémoire limitée ([lowRam]) les niveaux « légers » comptent comme
     * « modérés » : on rend les caches plus tôt plutôt que de risquer l'OutOfMemoryError.
     */
    fun severity(level: Int, lowRam: Boolean): Severity = when {
        level >= COMPLETE || level in RUNNING_CRITICAL until UI_HIDDEN -> Severity.CRITICAL
        level in RUNNING_LOW until RUNNING_CRITICAL || level >= BACKGROUND -> Severity.MODERATE
        level >= RUNNING_MODERATE -> if (lowRam) Severity.MODERATE else Severity.LIGHT
        else -> Severity.NONE
    }

    private class Entry(val min: Severity, val action: () -> Unit)

    private val entries = CopyOnWriteArrayList<Entry>()

    /** Inscrit [action] : exécutée (sur le fil appelant de [trim]) dès que la gravité atteint [min]. Fermer pour se désinscrire. */
    fun register(min: Severity = Severity.MODERATE, action: () -> Unit): AutoCloseable {
        val e = Entry(min, action)
        entries += e
        return AutoCloseable { entries -= e }
    }

    /** Lance toutes les actions inscrites pour [severity] ; une action en échec n'empêche pas les suivantes. Renvoie leur nombre. */
    fun trim(severity: Severity): Int {
        if (severity == Severity.NONE) return 0
        var n = 0
        for (e in entries) if (severity >= e.min) { runCatching(e.action); n++ }
        return n
    }

    /** `onLowMemory()` : le système est au plus bas, tout ce qui se reconstruit est rendu. */
    fun onLowMemory(): Int = trim(Severity.CRITICAL)
}

/**
 * Taille du cache MÉMOIRE des images, selon l'appareil. Coil dimensionne par défaut sur la classe de tas ; avec
 * `largeHeap` il prendrait la grande classe (plusieurs centaines de Mo), ce qui ne protège plus rien sur une TV.
 */
object ImageMemoryPolicy {
    private const val MB = 1024L * 1024
    const val MIN_BYTES = 4 * MB
    const val MAX_BYTES = 160 * MB

    /** [memoryClassMb] : `ActivityManager.memoryClass` (tas normal, pas la grande classe). */
    fun maxBytes(memoryClassMb: Int, lowRamDevice: Boolean, economy: Boolean): Long {
        val fraction = when {
            economy -> 0.08
            lowRamDevice || memoryClassMb <= 128 -> 0.12
            memoryClassMb <= 192 -> 0.18
            else -> 0.25
        }
        return (memoryClassMb.coerceAtLeast(1) * MB * fraction).toLong().coerceIn(MIN_BYTES, MAX_BYTES)
    }
}

/** Mémoire jointe aux rapports de plantage : tas, mémoire libre, et repérage des OutOfMemoryError. */
object CrashMemory {
    data class Snapshot(
        val maxHeapMb: Long, val usedHeapMb: Long, val memoryClassMb: Int, val largeMemoryClassMb: Int,
        val lowRamDevice: Boolean, val availMb: Long, val totalMb: Long, val systemLow: Boolean,
    )

    /** Vrai si [t] ou l'une de ses causes est un OutOfMemoryError (profondeur limitée : une cause circulaire est possible). */
    fun isOutOfMemory(t: Throwable?): Boolean {
        var cur = t
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur is OutOfMemoryError) return true
            cur = cur.cause?.takeIf { it !== cur }
            depth++
        }
        return false
    }

    /** Étiquette ajoutée en tête de la trace : visible d'un coup d'œil dans le tableau de bord. */
    fun tag(oom: Boolean): String = if (oom) "[OOM] " else ""

    /** Ligne ajoutée à la fin de la trace. */
    fun line(s: Snapshot): String =
        "[memoire] tas max=${s.maxHeapMb} Mo, utilise=${s.usedHeapMb} Mo, classe=${s.memoryClassMb} Mo (grande=${s.largeMemoryClassMb} Mo), " +
            "ram libre=${s.availMb}/${s.totalMb} Mo, systeme faible=${if (s.systemLow) "oui" else "non"}, lowRamDevice=${if (s.lowRamDevice) "oui" else "non"}"
}
