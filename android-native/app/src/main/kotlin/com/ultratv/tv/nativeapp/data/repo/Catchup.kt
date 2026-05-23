package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Catchup / time-shift URL builder. Two flavours are supported:
 *
 * 1. **M3U catchup-source template** — providers ship a URL with placeholders
 *    like `${start}`, `${end}`, `${duration}`, `${utc}`, `${timestamp}` or
 *    strftime tokens `{Y}/{m}/{d}/{H}/{M}/{S}`. We substitute against the
 *    programme's startMs / endMs.
 *
 * 2. **Xtream timeshift** — providers expose
 *    `…/streaming/timeshift.php?username=&password=&stream=N&start=Y-m-d:H-M&duration=MIN`
 *    when `tv_archive == 1`. We synthesise this from the channel's streamUrl
 *    by sniffing the Xtream output_format suffix.
 *
 * Returns the playable catchup URL, or null when the channel doesn't support
 * catch-up or the programme is outside the available window.
 */
object Catchup {

    fun buildUrl(channel: ChannelEntity, prog: EpgEntity, nowMs: Long = System.currentTimeMillis()): String? {
        // Programme must be in the past and inside the catchup window.
        if (prog.endMs >= nowMs) return null
        if (channel.catchupDays > 0) {
            val windowMs = channel.catchupDays * 24L * 60 * 60 * 1000
            if (nowMs - prog.startMs > windowMs) return null
        }
        val template = channel.catchupSource
        if (!template.isNullOrBlank()) return fillTemplate(template, prog)

        // Xtream synth fallback — works when the channel was imported via the
        // Xtream API and the URL still carries `/live/<user>/<pass>/<sid>.ts`.
        return synthesizeXtreamTimeshift(channel.streamUrl, prog)
    }

    private fun fillTemplate(template: String, prog: EpgEntity): String {
        val startSec = prog.startMs / 1000
        val endSec = prog.endMs / 1000
        val durMin = ((prog.endMs - prog.startMs) / 60_000).toInt().coerceAtLeast(1)
        val sdf = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val startStr = sdf.format(java.util.Date(prog.startMs))

        var out = template
        // Bash-style placeholders.
        out = out.replace("\${start}", startSec.toString())
        out = out.replace("\${end}", endSec.toString())
        out = out.replace("\${duration}", durMin.toString())
        out = out.replace("\${timestamp}", startSec.toString())
        out = out.replace("\${utc}", startSec.toString())
        out = out.replace("\${offset}", "0")

        // strftime-style tokens — provider docs reference these.
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = prog.startMs }
        fun two(n: Int) = "%02d".format(n)
        out = out.replace("{Y}", cal.get(java.util.Calendar.YEAR).toString())
        out = out.replace("{m}", two(cal.get(java.util.Calendar.MONTH) + 1))
        out = out.replace("{d}", two(cal.get(java.util.Calendar.DAY_OF_MONTH)))
        out = out.replace("{H}", two(cal.get(java.util.Calendar.HOUR_OF_DAY)))
        out = out.replace("{M}", two(cal.get(java.util.Calendar.MINUTE)))
        out = out.replace("{S}", two(cal.get(java.util.Calendar.SECOND)))
        return out
    }

    /**
     * Sniffs an Xtream live URL of the form
     *   http(s)://host[:port]/<user>/<pass>/<sid>(.ts|.m3u8)?
     * and builds the timeshift endpoint that Xtream Codes exposes for
     * channels with `tv_archive == 1`.
     */
    private fun synthesizeXtreamTimeshift(streamUrl: String, prog: EpgEntity): String? {
        val m = XTREAM_LIVE.matchEntire(streamUrl) ?: return null
        val (base, user, pass, sid, _) = m.destructured
        val sdf = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        val start = sdf.format(java.util.Date(prog.startMs))
        val durMin = ((prog.endMs - prog.startMs) / 60_000).toInt().coerceAtLeast(1)
        return "$base/streaming/timeshift.php" +
            "?username=$user&password=$pass&stream=$sid&start=$start&duration=$durMin"
    }

    private val XTREAM_LIVE = Regex(
        "^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/([^./]+)(\\..+)?$",
    )
}
