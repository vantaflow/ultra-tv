package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.ui.player.engine.PlayErrorKind
import java.util.Locale

/**
 * Diagnostic du flux en cours (logique pure, testée) : classement Bon / Moyen / Mauvais, cause probable, rapport copiable
 * sans identifiants. Tout vient de mesures déjà faites par le lecteur : aucune requête réseau supplémentaire.
 */
object StreamDiagnostic {
    enum class Grade { GOOD, FAIR, POOR, UNKNOWN }
    enum class Cause { NONE, SLOW_NETWORK, PROVIDER_LIMITED, VARIANT_CAPPED, REFUSED, NOT_FOUND, FORMAT, DEVICE, UNKNOWN }

    /** Variante vidéo d'un flux adaptatif (HLS) : hauteur en pixels et débit annoncé en kb/s. */
    data class Variant(val height: Int, val kbps: Int)
    data class ErrorEvent(val kind: PlayErrorKind, val http: Int? = null)

    data class Input(
        val engine: String,
        /** Un repli vers un autre moteur / décodeur a eu lieu pour ce flux. */
        val fellBack: Boolean = false,
        /** Délai entre l'ouverture du flux et la première image, ms ; null = pas encore d'image. */
        val firstFrameMs: Long? = null,
        val rebufferCount: Int = 0,
        val rebufferTotalMs: Long = 0,
        val droppedFrames: Int? = null,
        val renderedFrames: Long = -1,
        val errors: List<ErrorEvent> = emptyList(),
        val playing: Boolean = false,
        val resolution: String? = null,
        val currentKbps: Int? = null,
        /** Débit réellement reçu (estimation du moteur), kb/s. */
        val bandwidthKbps: Int? = null,
        val variants: List<Variant> = emptyList(),
    )

    /** Seuils (documentés pour les tests). */
    const val SLOW_FIRST_FRAME_MS = 5_000L
    const val FAIR_REBUFFERS = 2
    const val POOR_REBUFFERS = 5
    const val POOR_REBUFFER_MS = 15_000L
    const val DROPPED_RATIO = 0.05
    /** Marge de débit : sous 1,3 × le débit de la variante, le réseau est jugé juste. */
    const val BANDWIDTH_MARGIN = 1.3

    private fun droppedRatio(i: Input): Double {
        val d = i.droppedFrames ?: return 0.0
        val total = i.renderedFrames + d
        return if (i.renderedFrames <= 0 || total <= 0) 0.0 else d.toDouble() / total
    }

    fun grade(i: Input): Grade {
        if (!i.playing && i.errors.isNotEmpty()) return Grade.POOR
        if (!i.playing && i.firstFrameMs == null) return Grade.UNKNOWN
        if (i.rebufferCount >= POOR_REBUFFERS || i.rebufferTotalMs >= POOR_REBUFFER_MS) return Grade.POOR
        val fair = i.rebufferCount >= FAIR_REBUFFERS || (i.firstFrameMs ?: 0) >= SLOW_FIRST_FRAME_MS ||
            i.errors.isNotEmpty() || i.fellBack || droppedRatio(i) >= DROPPED_RATIO
        return if (fair) Grade.FAIR else Grade.GOOD
    }

    fun cause(i: Input): Cause {
        val err = i.errors.lastOrNull()
        if (err != null) when (err.kind) {
            PlayErrorKind.REFUSED, PlayErrorKind.PORTAL_REFUSED -> return Cause.REFUSED
            PlayErrorKind.NOT_FOUND, PlayErrorKind.NO_LINK -> return Cause.NOT_FOUND
            PlayErrorKind.FORMAT, PlayErrorKind.DECODER, PlayErrorKind.NO_PICTURE,
            PlayErrorKind.DRM_UNSUPPORTED, PlayErrorKind.DRM_INVALID, PlayErrorKind.DRM_LICENSE -> return Cause.FORMAT
            else -> {}
        }
        val g = grade(i)
        if (g == Grade.GOOD) return Cause.NONE
        if (g == Grade.UNKNOWN) return Cause.UNKNOWN
        val bw = i.bandwidthKbps
        val cur = i.currentKbps
        val top = i.variants.maxByOrNull { it.height }
        // Réseau lent : le débit reçu suffit à peine (ou pas) à la variante lue, ou à la plus basse proposée.
        if (bw != null) {
            val need = cur ?: i.variants.minOfOrNull { it.kbps }
            if (need != null && bw < need * BANDWIDTH_MARGIN) return Cause.SLOW_NETWORK
        }
        // Variante bridée : le réseau suffirait à la plus haute, mais une plus basse est lue.
        if (bw != null && top != null && cur != null && cur < top.kbps && bw >= top.kbps * BANDWIDTH_MARGIN) return Cause.VARIANT_CAPPED
        if (droppedRatio(i) >= DROPPED_RATIO && i.rebufferCount < FAIR_REBUFFERS) return Cause.DEVICE
        // Réseau correct mais le flux saccade ou démarre lentement : le fournisseur limite.
        if (bw != null && (i.rebufferCount >= FAIR_REBUFFERS || (i.firstFrameMs ?: 0) >= SLOW_FIRST_FRAME_MS ||
                err?.kind == PlayErrorKind.NETWORK || err?.kind == PlayErrorKind.START_TIMEOUT)) return Cause.PROVIDER_LIMITED
        return Cause.UNKNOWN
    }

    /** Noms en clair pour le rapport copié (anglais : destiné au support). */
    fun gradeName(g: Grade) = when (g) { Grade.GOOD -> "Good"; Grade.FAIR -> "Fair"; Grade.POOR -> "Poor"; Grade.UNKNOWN -> "Inconclusive" }
    fun causeName(c: Cause) = when (c) {
        Cause.NONE -> "none"; Cause.SLOW_NETWORK -> "slow network"; Cause.PROVIDER_LIMITED -> "provider limited"
        Cause.VARIANT_CAPPED -> "capped variant"; Cause.REFUSED -> "access refused"; Cause.NOT_FOUND -> "stream not found"
        Cause.FORMAT -> "unsupported format or decoder"; Cause.DEVICE -> "device cannot keep up"; Cause.UNKNOWN -> "unknown"
    }

    // ---------- Masquage des identifiants ----------

    private val URL_RE = Regex("""(?i)\b([a-z][a-z0-9+.-]*)://[^\s"'<>]+""")
    private val SECRET_PARAM_RE = Regex("""(?i)\b(user(?:name)?|pass(?:word)?|pwd|token|key|auth|mac|sn|device_id|portal_key)\s*[=:]\s*[^\s&,;"']+""")
    private val MAC_RE = Regex("""(?i)\b[0-9a-f]{2}(?:[:-][0-9a-f]{2}){5}\b""")

    /** Une adresse de flux : seul le schéma reste (hôte, chemin — où Xtream met user/mot de passe — et requête sont masqués). */
    fun redactUrl(url: String): String = URL_RE.replace(url) { "${it.groupValues[1].lowercase()}://***" }

    /** Texte libre : adresses, paramètres sensibles (user, password, token…) et adresses MAC masqués. */
    fun redactText(text: String): String =
        SECRET_PARAM_RE.replace(MAC_RE.replace(redactUrl(text), "**:**:**:**:**:**")) { "${it.groupValues[1]}=***" }

    // ---------- Rapport ----------

    private fun mbps(k: Int?) = k?.takeIf { it > 0 }?.let { String.format(Locale.US, "%.1f Mb/s", it / 1000.0) } ?: "n/a"

    /** Rapport copiable (anglais, sans identifiants) : à coller dans une demande de support. */
    fun report(i: Input, appVersion: String? = null): String {
        val lines = buildList {
            add("Ultra TV stream diagnostic" + (appVersion?.let { " ($it)" } ?: ""))
            add("Result: ${gradeName(grade(i))}; probable cause: ${causeName(cause(i))}")
            add("Engine: ${i.engine}" + if (i.fellBack) " (fell back from another engine/decoder)" else "")
            add("Time to first frame: ${i.firstFrameMs?.let { "$it ms" } ?: "n/a"}")
            add("Received throughput: ${mbps(i.bandwidthKbps)}")
            add("Playing: ${i.resolution ?: "n/a"} at ${mbps(i.currentKbps)}")
            if (i.variants.isNotEmpty()) add("Variants: " + i.variants.sortedBy { it.height }.joinToString(", ") { "${it.height}p/${mbps(it.kbps)}" })
            add("Rebuffers: ${i.rebufferCount} (${String.format(Locale.US, "%.1f", i.rebufferTotalMs / 1000.0)} s total)")
            add("Dropped frames: ${i.droppedFrames ?: "n/a"}")
            add("Errors: " + if (i.errors.isEmpty()) "none" else i.errors.joinToString(", ") { it.kind.name + (it.http?.let { h -> " (HTTP $h)" } ?: "") })
        }
        return redactText(lines.joinToString("\n"))
    }
}
