package com.ultratv.tv.nativeapp.ui.player.osd

import com.ultratv.tv.nativeapp.data.db.EpgEntity

/** Rôle d'un programme dans le ruban de la soirée. */
enum class RibbonKind { PAST, NOW, NEXT }

/**
 * Un segment du ruban de la soirée : [weight] proportionnel à la durée (largeur relative), [label] = heure de début
 * (« 20:00 · EN COURS » pour le programme en cours), [fraction] = avancement du programme en cours (0 sinon).
 */
data class OsdRibbonSlot(val kind: RibbonKind, val title: String, val label: String, val weight: Float, val fraction: Float)

/** Calculs purs du ruban de la soirée (précédent / en cours / suivants) à partir du guide ; testés sur la JVM. */
object OsdRibbon {
    /** Nombre maximal de programmes suivants. */
    const val MAX_NEXT = 3
    /** Durées bornées avant pondération : un jingle de 2 min reste lisible, un film de 4 h ne mange pas tout le ruban. */
    private const val MIN_MIN = 20f
    private const val MAX_MIN = 120f
    private const val UNIT_MIN = 30f

    fun weightOf(startMs: Long, endMs: Long): Float {
        val minutes = ((endMs - startMs) / 60_000f).coerceIn(MIN_MIN, MAX_MIN)
        return minutes / UNIT_MIN
    }

    /**
     * Segments : au plus un programme précédent, le programme en cours (celui qui contient [nowMs]), puis jusqu'à [MAX_NEXT]
     * suivants. Liste vide sans programme en cours (pas de guide ou trou de guide) : le ruban est alors masqué.
     */
    fun slots(items: List<EpgEntity>, nowMs: Long, hm: (Long) -> String, onAirWord: String): List<OsdRibbonSlot> {
        val sorted = items.filter { it.title.isNotBlank() && it.endMs > it.startMs }.distinctBy { it.startMs }.sortedBy { it.startMs }
        val cur = sorted.firstOrNull { it.startMs <= nowMs && nowMs < it.endMs } ?: return emptyList()
        val prev = sorted.lastOrNull { it.endMs <= cur.startMs }
        val next = sorted.filter { it.startMs >= cur.endMs }.take(MAX_NEXT)
        val out = ArrayList<OsdRibbonSlot>()
        if (prev != null) out += OsdRibbonSlot(RibbonKind.PAST, prev.title, hm(prev.startMs), weightOf(prev.startMs, prev.endMs), 0f)
        out += OsdRibbonSlot(
            RibbonKind.NOW, cur.title, "${hm(cur.startMs)} · $onAirWord", weightOf(cur.startMs, cur.endMs),
            ((nowMs - cur.startMs).toFloat() / (cur.endMs - cur.startMs)).coerceIn(0f, 1f),
        )
        for (n in next) out += OsdRibbonSlot(RibbonKind.NEXT, n.title, hm(n.startMs), weightOf(n.startMs, n.endMs), 0f)
        return out
    }
}

/** Carte « ENSUITE » : plage horaire, titre et résumé du programme suivant. */
data class OsdNextInfo(val range: String, val title: String, val summary: String?)

/** Boutons de la carte « ENSUITE ». */
enum class OsdNextAction { REMIND, RECORD }

/** Petits calculs de texte de l'OSD (purs). */
object OsdText {
    /** Pastille HDR : « hdr10 » → « HDR10 », « hlg » → « HLG », « dv » → « Dolby Vision » ; null = SDR / inconnu. */
    fun hdrLabel(kind: String?): String? = when (kind?.lowercase()) { "hdr10" -> "HDR10"; "hlg" -> "HLG"; "dv" -> "Dolby Vision"; else -> null }

    /** Nombre de chaînes récentes montrées sur la rangée (disposition de la maquette). */
    const val RECENTS_SHOWN = 5

    /** Initiales d'un nom de chaîne : deux premiers mots (« France Info » → « FI »), sinon deux premiers caractères. */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).map { w -> w.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "·"
            words.size == 1 -> words[0].take(2)
            else -> "${words[0].first()}${words[1].first()}"
        }.uppercase()
    }

    /** Ligne « MAINTENANT · 20:00 – 21:30 · RESTE 54 MIN » (les parties absentes sont omises). */
    fun nowLine(nowWord: String, timeRange: String?, remaining: String?): String =
        listOfNotNull(nowWord.takeIf { it.isNotBlank() }, timeRange?.takeIf { it.isNotBlank() }, remaining?.takeIf { it.isNotBlank() })
            .joinToString(" · ").uppercase()

    /** Qualité de la chaîne (4 = 4K … 1 = SD) ; null si inconnue. */
    fun qualityLabel(q: Int): String? = when (q) { 4 -> "4K"; 3 -> "FHD"; 2 -> "HD"; 1 -> "SD"; else -> null }
}
