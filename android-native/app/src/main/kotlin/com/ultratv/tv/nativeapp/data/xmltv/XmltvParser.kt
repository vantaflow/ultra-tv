package com.ultratv.tv.nativeapp.data.xmltv

import android.util.Xml
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streaming XMLTV parser. Reads `<tv>` containing `<channel id="…">` /
 * `<programme channel="…" start="…" stop="…"><title>…</title>…</programme>`
 * and yields [EpgEntity] rows ready for insertion.
 *
 * We use a pull parser (no DOM) because xmltv feeds for big providers can be
 * 50 MB+ and DOM parsing would OOM on a TV box.
 *
 * Programmes are emitted only when the [channelXmltvIdToLocalId] map contains
 * a matching xmltv `channel="..."` — unmapped channels are skipped silently.
 */
@Singleton
class XmltvParser @Inject constructor(okBase: OkHttpClient) {

    // Flux de 80 Mo : pas de délai d'appel global, 10 s de connexion, 30 s d'inactivité.
    private val ok: OkHttpClient = okBase.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Ouvre le flux XMLTV et passe à [block] une séquence PARESSEUSE de programmes
     * (filtrés par [window] et par chaînes présentes). Rien n'est accumulé : le
     * appelant insère par lots au fil de l'eau, la mémoire reste plate pour 80 Mo de XML.
     */
    suspend fun <R> withProgrammes(
        p: ProviderEntity,
        channelXmltvIdToLocalId: Map<String, Long>,
        window: LongRange = defaultWindow(),
        block: suspend (Sequence<EpgEntity>) -> R,
    ): R = withContext(Dispatchers.IO) {
        val url = "${p.baseUrl}/xmltv.php?username=${java.net.URLEncoder.encode(p.username, "UTF-8")}" +
            "&password=${java.net.URLEncoder.encode(p.password, "UTF-8")}"
        ok.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw com.ultratv.tv.nativeapp.data.net.HttpStatusException(resp.code)
            val stream = resp.body?.byteStream()?.buffered() ?: error("Empty xmltv body")
            block(programmes(stream, channelXmltvIdToLocalId, window))
        }
    }

    /** Variante en liste, pour les tests et les petits flux. */
    fun parse(
        input: InputStream,
        channelMap: Map<String, Long>,
        window: LongRange = Long.MIN_VALUE..Long.MAX_VALUE,
    ): List<EpgEntity> = programmes(input, channelMap, window).toList()

    /**
     * @param window ne garde que les programmes qui chevauchent [window] (millisecondes).
     */
    fun programmes(
        input: InputStream,
        channelMap: Map<String, Long>,
        window: LongRange = Long.MIN_VALUE..Long.MAX_VALUE,
    ): Sequence<EpgEntity> = sequence {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val xmltvCh = parser.getAttributeValue(null, "channel")
                val channelId = if (xmltvCh != null) channelMap[xmltvCh] else null
                val start = parseDate(parser.getAttributeValue(null, "start"))
                val stop = parseDate(parser.getAttributeValue(null, "stop"))
                val inWindow = start != null && stop != null && stop > start &&
                    stop >= window.first && start <= window.last
                if (channelId == null || !inWindow) {
                    skipToEndTag(parser, "programme")
                } else {
                    var title: String? = null
                    var desc: String? = null
                    while (true) {
                        val e = parser.next()
                        if (e == XmlPullParser.END_TAG && parser.name == "programme") break
                        if (e == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "title" -> title = readText(parser)
                                "desc" -> if (desc == null) desc = readText(parser).take(MAX_DESC)
                                else -> skipToEndTag(parser, parser.name)
                            }
                        }
                        if (e == XmlPullParser.END_DOCUMENT) break
                    }
                    if (title != null) {
                        yield(EpgEntity(channelId = channelId, title = title, description = desc, startMs = start!!, endMs = stop!!))
                    }
                }
            }
            event = parser.next()
        }
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        while (true) {
            val e = parser.next()
            if (e == XmlPullParser.TEXT) sb.append(parser.text)
            if (e == XmlPullParser.END_TAG) break
            if (e == XmlPullParser.END_DOCUMENT) break
        }
        return sb.toString().trim()
    }

    /**
     * Saute l'élément courant (son START_TAG vient d'être lu) jusqu'à sa balise fermante.
     * Avant : seules les fermetures portant le même nom décrémentaient la profondeur, donc
     * sauter un `<programme>` (avec ses `<title>`...) avalait aussi les programmes suivants.
     */
    private fun skipToEndTag(parser: XmlPullParser, @Suppress("UNUSED_PARAMETER") tag: String) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.END_DOCUMENT -> return
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
            }
        }
    }

    private fun parseDate(raw: String?): Long? = parseXmltvDate(raw)

    companion object {
        private const val MAX_DESC = 400

        /** Fenêtre utile : de −2 h à +24 h. Un flux de 80 Mo couvre des jours pour des dizaines de
         *  milliers de chaînes ; l'écran n'en montre qu'un sous-ensemble horaire. */
        fun defaultWindow(nowMs: Long = System.currentTimeMillis()): LongRange =
            (nowMs - 2 * 3_600_000L)..(nowMs + 24 * 3_600_000L)
    }
}

/**
 * Date XMLTV (`yyyyMMddHHmmss` suivi d'un décalage optionnel `+0100`) en millisecondes UTC.
 * Remplace les `SimpleDateFormat` partagés du singleton, qui ne sont pas thread-safe.
 */
internal fun parseXmltvDate(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    val s = raw.trim()
    val digits = s.takeWhile { it.isDigit() }
    if (digits.length < 12) return null
    val padded = digits.padEnd(14, '0')
    return runCatching {
        val local = java.time.LocalDateTime.of(
            padded.substring(0, 4).toInt(), padded.substring(4, 6).toInt(), padded.substring(6, 8).toInt(),
            padded.substring(8, 10).toInt(), padded.substring(10, 12).toInt(), padded.substring(12, 14).toInt(),
        )
        val tz = s.substring(digits.length).trim()
        val offset = if (tz.length >= 5 && (tz[0] == '+' || tz[0] == '-')) {
            val sign = if (tz[0] == '-') -1 else 1
            val h = tz.substring(1, 3).toInt()
            val m = tz.substring(3, 5).toInt()
            java.time.ZoneOffset.ofTotalSeconds(sign * (h * 3600 + m * 60))
        } else java.time.ZoneOffset.UTC
        local.toInstant(offset).toEpochMilli()
    }.getOrNull()
}
