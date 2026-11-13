package com.ultratv.tv.nativeapp.data.memory

import com.ultratv.tv.nativeapp.i18n.AppLang
import java.io.File
import java.util.Locale

/**
 * Logique pure (sans Android) de la rubrique « Mémoire et stockage » : tailles de dossiers, limite Auto du cache
 * d'images, purge des fichiers temporaires, mise en forme des tailles. Testée en JVM (MemoryLogicTest).
 */
object StorageMath {
    /** Taille cumulée d'un fichier ou d'un dossier (récursif) ; 0 s'il n'existe pas ; ignore ce qui disparaît en cours de route. */
    fun sizeOf(f: File?): Long {
        if (f == null || !f.exists()) return 0L
        if (f.isFile) return f.length()
        var total = 0L
        val stack = ArrayDeque<File>().apply { add(f) }
        while (stack.isNotEmpty()) {
            val d = stack.removeLast()
            val children = runCatching { d.listFiles() }.getOrNull() ?: continue
            for (c in children) if (c.isDirectory) stack.add(c) else total += runCatching { c.length() }.getOrDefault(0L)
        }
        return total
    }

    /** Supprime le CONTENU d'un dossier (le dossier reste) et renvoie les octets libérés. */
    fun clearDirectory(dir: File?): Long {
        if (dir == null || !dir.isDirectory) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { freed += deleteTree(it) }
        return freed
    }

    /** Supprime un fichier ou un dossier entier ; renvoie les octets réellement libérés. */
    fun deleteTree(f: File): Long {
        if (!f.exists()) return 0L
        var freed = 0L
        if (f.isDirectory) f.listFiles()?.forEach { freed += deleteTree(it) }
        val len = if (f.isFile) f.length() else 0L
        if (runCatching { f.delete() }.getOrDefault(false)) freed += len
        return freed
    }
}

/** Fichiers temporaires de la synchro (flux JSON/XML mis sur disque) et sous-titres téléchargés. */
object TempFiles {
    /** Nom d'un fichier temporaire de synchro : `xtream-123.json`, `stalker-9.json`, `xmltv-5.xml` (createTempFile). */
    private val SYNC_NAME = Regex("^(xtream|stalker|xmltv)-.*\\.(json|xml|tmp)$")
    const val SUBTITLES_DIR = "subtitles"

    /** Un fichier de synchro plus récent que ceci est peut-être encore utilisé : on n'y touche pas. */
    const val MIN_AGE_MS = 15 * 60_000L

    fun isSyncTemp(name: String): Boolean = SYNC_NAME.matches(name)

    private fun syncTempFiles(cacheDir: File): List<File> =
        cacheDir.listFiles()?.filter { it.isFile && isSyncTemp(it.name) }.orEmpty()

    /** Octets occupés par les fichiers temporaires (tous âges : c'est ce que l'écran affiche). */
    fun size(cacheDir: File): Long = syncTempFiles(cacheDir).sumOf { it.length() } + StorageMath.sizeOf(File(cacheDir, SUBTITLES_DIR))

    /**
     * Purge : fichiers de synchro de plus de [minAgeMs] (0 = tous, à n'utiliser qu'hors synchro) et sous-titres.
     * Renvoie les octets libérés.
     */
    fun purge(cacheDir: File, now: Long, minAgeMs: Long = MIN_AGE_MS): Long {
        var freed = 0L
        for (f in syncTempFiles(cacheDir)) {
            if (now - f.lastModified() >= minAgeMs) {
                val len = f.length()
                if (runCatching { f.delete() }.getOrDefault(false)) freed += len
            }
        }
        freed += StorageMath.clearDirectory(File(cacheDir, SUBTITLES_DIR))
        return freed
    }
}

/** Tampons de pause du direct restés sur le disque (appli tuée, plantage) : lesquels supprimer. */
object TimeshiftOrphans {
    /** Un tampon modifié plus récemment que ceci est peut-être encore écrit : on n'y touche pas. */
    const val MIN_AGE_MS = 60_000L

    data class Entry(val name: String, val lastModified: Long)

    /** Noms à supprimer : tout fichier hors [inUse] dont la dernière écriture remonte à au moins [minAgeMs]. */
    fun toDelete(files: List<Entry>, inUse: Set<String>, now: Long, minAgeMs: Long = MIN_AGE_MS): List<String> =
        files.filter { it.name !in inUse && now - it.lastModified >= minAgeMs }.map { it.name }
}

/** Limite du cache d'images (disque, Coil). `null` = Auto selon l'espace libre. */
object ImageCacheLimit {
    const val AUTO = "auto"
    const val MB = 1024L * 1024L
    /** Choix proposés (en Mo) ; « auto » en plus. */
    val CHOICES_MB = listOf(100, 250, 500)

    const val AUTO_MIN_MB = 64L
    const val AUTO_MAX_MB = 512L
    const val AUTO_LOW_DEVICE_MAX_MB = 128L

    /** Valeur enregistrée → octets (Auto → [autoBytes]). Une valeur inconnue vaut Auto. */
    fun bytes(pref: String, freeBytes: Long, limitedDevice: Boolean): Long {
        val mb = pref.toIntOrNull()?.takeIf { it in CHOICES_MB }
        return if (mb != null) mb * MB else autoBytes(freeBytes, limitedDevice)
    }

    /**
     * Auto : un dixième de l'espace libre, borné à 64–512 Mo (128 Mo au plus sur appareil à mémoire limitée),
     * arrondi aux 16 Mo inférieurs. Espace libre inconnu ou négatif → le minimum.
     */
    fun autoBytes(freeBytes: Long, limitedDevice: Boolean): Long {
        val cap = if (limitedDevice) AUTO_LOW_DEVICE_MAX_MB else AUTO_MAX_MB
        val tenth = (freeBytes.coerceAtLeast(0L) / 10L) / MB
        val mb = tenth.coerceIn(AUTO_MIN_MB, cap)
        return (mb / 16L * 16L).coerceAtLeast(AUTO_MIN_MB) * MB
    }

    /** Le cache dépasse-t-il la limite (donc à purger) ? */
    fun overLimit(currentBytes: Long, limitBytes: Long): Boolean = limitBytes > 0 && currentBytes > limitBytes
}

/** Mode « économie de mémoire ». */
object EconomyMode {
    const val AUTO = "auto"
    const val ON = "on"
    const val OFF = "off"
    val ALL = listOf(AUTO, ON, OFF)

    fun normalize(v: String?): String = if (v in ALL) v!! else AUTO

    /** Économie effective : forcée par l'utilisateur, ou (Auto) selon l'appareil. */
    fun effective(mode: String, limitedDevice: Boolean): Boolean = when (normalize(mode)) {
        ON -> true
        OFF -> false
        else -> limitedDevice
    }
}

/** Appareil à mémoire limitée : drapeau système « low RAM », ou moins de 2 Go. */
object RamClass {
    const val LIMITED_BELOW_BYTES = 2L * 1024 * 1024 * 1024
    fun isLimited(totalBytes: Long, lowRamFlag: Boolean): Boolean = lowRamFlag || (totalBytes in 1 until LIMITED_BELOW_BYTES)
}

/** Décompte de l'espace utilisé par l'appli (octets). */
data class StorageUsage(
    val database: Long,
    val images: Long,
    val downloads: Long,
    val timeshift: Long,
    val temp: Long,
    val logs: Long,
    val free: Long,
) {
    val total: Long get() = database + images + downloads + timeshift + temp + logs
}

/** Mise en forme des tailles : unités et séparateur décimal selon la langue (1 Ko = 1024 octets). */
object SizeFormat {
    private fun units(lang: AppLang) = when (lang) {
        AppLang.French -> arrayOf("o", "Ko", "Mo", "Go", "To")
        AppLang.Arabic -> arrayOf("بايت", "ك.ب", "م.ب", "ج.ب", "ت.ب")
        AppLang.Spanish -> arrayOf("bytes", "KB", "MB", "GB", "TB")
        else -> arrayOf("B", "KB", "MB", "GB", "TB")
    }

    fun format(bytes: Long, lang: AppLang): String {
        val u = units(lang)
        var v = bytes.coerceAtLeast(0L).toDouble()
        var i = 0
        while (v >= 1024.0 && i < u.lastIndex) { v /= 1024.0; i++ }
        // Octets et Ko : entiers ; Mo : entier au-delà de 100 ; Go et plus : une décimale.
        val num = when {
            i == 0 || i == 1 -> String.format(Locale.ROOT, "%d", Math.round(v))
            i == 2 -> if (v >= 100) String.format(Locale.ROOT, "%d", Math.round(v)) else decimal(v, lang)
            else -> decimal(v, lang)
        }
        return "$num ${u[i]}"
    }

    private fun decimal(v: Double, lang: AppLang): String {
        val s = String.format(Locale.ROOT, "%.1f", v)
        val t = if (s.endsWith(".0")) s.dropLast(2) else s
        return when (lang) {
            AppLang.French, AppLang.Spanish, AppLang.Polish, AppLang.PortugueseBr -> t.replace('.', ',')
            AppLang.Arabic -> t.replace('.', '٫')
            else -> t
        }
    }
}
