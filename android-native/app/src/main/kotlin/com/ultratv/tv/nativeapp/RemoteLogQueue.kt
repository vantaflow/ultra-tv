package com.ultratv.tv.nativeapp

/**
 * File en mémoire des journaux à envoyer au Worker (télémétrie par lots : une requête au lieu d'une par ligne).
 *
 * - 50 lignes au plus ([maxSize]) ; au-delà, les plus anciennes sont écartées.
 * - Envoi groupé : toutes les 5 min ([flushMs]) après la plus ancienne ligne en attente, dans les 60 s ([errorFlushMs])
 *   après une erreur, et tout de suite quand la file est pleine (voir [add]).
 * - Échantillonnage côté client des lignes d'info / debug : 1 sur [sampleInfo] (comme le Worker), pour ne même pas
 *   les envoyer. Avertissements et erreurs sont toujours gardés.
 *
 * Pure (heure et hasard injectés) et synchronisée par l'appelant ([RemoteLog]) : testée sans Android.
 */
internal class LogQueue(
    private val maxSize: Int = 50,
    private val flushMs: Long = 5 * 60_000L,
    private val errorFlushMs: Long = 60_000L,
    private val sampleInfo: Int = 10,
    private val rand: () -> Double = { Math.random() },
) {
    /** [sampled] : ligne d'info déjà échantillonnée ici (le Worker ne la tire pas une seconde fois). */
    class Line(val ts: Long, val level: String, val tag: String, val message: String, val sampled: Boolean = false)

    private val lines = ArrayDeque<Line>()
    /** Instant à partir duquel le prochain envoi est dû (plus ancienne ligne + 5 min, ou première erreur + 60 s). */
    private var dueAt: Long = Long.MAX_VALUE

    val size: Int get() = lines.size

    /** Niveaux toujours gardés. */
    fun isImportant(level: String): Boolean = level.lowercase().let { it == "warn" || it == "warning" || it == "error" || it == "fatal" }

    /** Cette ligne est-elle gardée ? Avertissements et erreurs toujours, le reste 1 fois sur [sampleInfo]. */
    fun accept(level: String): Boolean = isImportant(level) || rand() < 1.0 / sampleInfo

    /**
     * Ajoute une ligne. Renvoie vrai si la file est pleine : l'appelant envoie aussitôt. Une file qui déborde
     * (envoi impossible) écarte les plus anciennes lignes.
     */
    fun add(line: Line, now: Long): Boolean {
        lines.addLast(line)
        while (lines.size > maxSize) lines.removeFirst()
        val deadline = if (isImportant(line.level)) now + errorFlushMs else now + flushMs
        if (deadline < dueAt) dueAt = deadline
        return lines.size >= maxSize
    }

    /** Délai avant le prochain envoi, ou null si la file est vide. Jamais négatif. */
    fun nextFlushDelay(now: Long): Long? = if (lines.isEmpty()) null else maxOf(0L, dueAt - now)

    /** Retire et renvoie toutes les lignes en attente (de la plus ancienne à la plus récente). */
    fun drain(): List<Line> {
        val out = lines.toList()
        lines.clear()
        dueAt = Long.MAX_VALUE
        return out
    }

    /**
     * Envoi échoué (réseau, 429, 5xx) : les lignes reviennent en tête de file, dans la limite de [maxSize] (les plus
     * anciennes sont écartées) ; nouvel essai dans 60 s si elles contiennent une erreur, sinon dans 5 min.
     */
    fun restore(failed: List<Line>, now: Long) {
        for (l in failed.asReversed()) lines.addFirst(l)
        while (lines.size > maxSize) lines.removeFirst()
        val deadline = if (lines.any { isImportant(it.level) }) now + errorFlushMs else now + flushMs
        if (deadline < dueAt) dueAt = deadline
    }

    companion object {
        /** Une ligne ne dépasse pas ce nombre de caractères (le corps d'un lot est limité à 32 Ko côté Worker). */
        const val MAX_MESSAGE = 1000

        /**
         * Découpe [lines] en lots dont le corps JSON reste sous [maxChars] (le Worker refuse plus de 32 Ko ou de 50
         * entrées). Estimation prudente (messages comptés double : échappements, UTF-8) : message + étiquette + 120 caractères d'enveloppe par ligne.
         */
        fun chunks(lines: List<Line>, maxChars: Int = 28_000, maxLines: Int = 50): List<List<Line>> {
            val out = mutableListOf<MutableList<Line>>()
            var cur = mutableListOf<Line>()
            var size = 0
            for (l in lines) {
                val n = 2 * l.message.length + l.tag.length + 120
                if (cur.isNotEmpty() && (size + n > maxChars || cur.size >= maxLines)) { out += cur; cur = mutableListOf(); size = 0 }
                cur += l; size += n
            }
            if (cur.isNotEmpty()) out += cur
            return out
        }
    }
}
