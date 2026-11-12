package com.ultratv.tv.nativeapp.ui.player

import com.ultratv.tv.nativeapp.ui.player.engine.EngineStats
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Pastilles techniques du flux en cours (film / épisode). Un champ inconnu reste null : rien n'est affiché pour lui. */
data class TechBadges(val quality: String? = null, val codec: String? = null, val audio: String? = null, val hdr: String? = null) {
    val isEmpty get() = quality == null && codec == null && audio == null && hdr == null
    companion object { val EMPTY = TechBadges() }
}

/** Épisode qui suit l'épisode en cours (carte « Épisode suivant dans N s ») : numéro, titre, résumé et vignette. */
data class NextEpisodeInfo(val number: Int, val title: String, val summary: String?, val image: String?)

/** Logique pure de la surcouche film / épisode (testée à part) : pastilles, temps restant, heure de fin. */
object VodOverlayLogic {
    /** Pastilles d'après les statistiques du moteur (Media3 comme LibVLC). */
    fun badges(s: EngineStats?): TechBadges =
        if (s == null) TechBadges.EMPTY else TechBadges(quality(s.resolution), codec(s.videoCodec), audio(s.audioChannels), hdrLabel(s.hdrKind))

    /** Pastille HDR : « hdr10 » → « HDR10 », « hlg » → « HLG », « dv » → « Dolby Vision » ; null = SDR / inconnu. */
    fun hdrLabel(kind: String?): String? = com.ultratv.tv.nativeapp.ui.player.osd.OsdText.hdrLabel(kind)

    /** SD / HD / FHD / 4K d'après « 1920×1080 ». La largeur compte aussi : un film en 2,39:1 en 1920×800 reste FHD. */
    fun quality(resolution: String?): String? {
        val parts = resolution?.split('×', 'x', 'X')?.mapNotNull { it.trim().toIntOrNull() } ?: return null
        if (parts.size != 2) return null
        val (w, h) = parts
        if (w <= 0 || h <= 0) return null
        return when {
            w >= 3200 || h >= 2000 -> "4K"
            w >= 1800 || h >= 1000 -> "FHD"
            w >= 1200 || h >= 700 -> "HD"
            else -> "SD"
        }
    }

    /** Nom usuel du codec vidéo d'après le type MIME Media3 (« avc ») ou le code LibVLC (« h264 »). */
    fun codec(raw: String?): String? {
        val c = raw?.lowercase(Locale.ROOT)?.trim().orEmpty()
        if (c.isEmpty()) return null
        return when {
            "hevc" in c || "h265" in c || "hvc1" in c || "hev1" in c || "dolby-vision" in c -> "HEVC"
            "avc" in c || "h264" in c || "h.264" in c -> "H.264"
            "av01" in c || c == "av1" -> "AV1"
            "vp9" in c || "vp09" in c -> "VP9"
            "vp8" in c -> "VP8"
            "mpeg2" in c || "mp2v" in c || "mpgv" in c -> "MPEG-2"
            "mp4v" in c || "mpeg4" in c || "divx" in c || "xvid" in c -> "MPEG-4"
            "vc1" in c || "wvc1" in c || "wmv3" in c -> "VC-1"
            else -> null
        }
    }

    /** Disposition audio : 2 → 2.0, 6 → 5.1, 8 → 7.1. */
    fun audio(channels: Int?): String? = when (channels) {
        null -> null
        1 -> "1.0"; 2 -> "2.0"; 3 -> "2.1"; 4 -> "4.0"; 5 -> "5.0"; 6 -> "5.1"; 7 -> "6.1"; 8 -> "7.1"
        else -> null
    }

    private fun two(n: Long) = if (n < 10) "0$n" else "$n"

    /** Durée « 1:02:03 » ou « 2:03 » (le temps écoulé). */
    fun clock(ms: Long): String {
        val s = (ms.coerceAtLeast(0)) / 1000
        val h = s / 3600; val m = s % 3600 / 60; val sec = s % 60
        return if (h > 0) "$h:${two(m)}:${two(sec)}" else "$m:${two(sec)}"
    }

    /** Temps restant « −2:48:58 » ; vide si la durée est inconnue. */
    fun remaining(posMs: Long, durMs: Long): String =
        if (durMs <= 0) "" else "−" + clock((durMs - posMs).coerceAtLeast(0))

    /** Instant de fin de lecture au rythme [speed] ; null si la durée est inconnue. */
    fun endsAtMs(nowMs: Long, posMs: Long, durMs: Long, speed: Float = 1f): Long? {
        if (durMs <= 0) return null
        val sp = if (speed > 0f) speed else 1f
        return nowMs + ((durMs - posMs).coerceAtLeast(0) / sp).toLong()
    }

    /** Heure de fin affichée : « 13:06 » (24 h) ou « 1:06 PM » (12 h). */
    fun endClock(endMs: Long, is24h: Boolean, zone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = endMs }
        val h = cal.get(Calendar.HOUR_OF_DAY); val m = cal.get(Calendar.MINUTE).toLong()
        if (is24h) return "${two(h.toLong())}:${two(m)}"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:${two(m)} ${if (h < 12) "AM" else "PM"}"
    }
}
