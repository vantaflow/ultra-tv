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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
class XmltvParser @Inject constructor(private val ok: OkHttpClient) {

    private val xmltvDateFormats = listOf(
        // "yyyyMMddHHmmss Z"  e.g.  20251215140000 +0100
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
    )

    suspend fun fetchAndParse(
        p: ProviderEntity,
        channelXmltvIdToLocalId: Map<String, Long>,
    ): List<EpgEntity> = withContext(Dispatchers.IO) {
        val url = "${p.baseUrl}/xmltv.php?username=${java.net.URLEncoder.encode(p.username, "UTF-8")}" +
            "&password=${java.net.URLEncoder.encode(p.password, "UTF-8")}"
        ok.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} fetching xmltv")
            val stream = resp.body?.byteStream() ?: error("Empty xmltv body")
            parse(stream, channelXmltvIdToLocalId)
        }
    }

    fun parse(input: InputStream, channelMap: Map<String, Long>): List<EpgEntity> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val out = mutableListOf<EpgEntity>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val xmltvCh = parser.getAttributeValue(null, "channel")
                val channelId = if (xmltvCh != null) channelMap[xmltvCh] else null
                if (channelId == null) {
                    skipToEndTag(parser, "programme")
                } else {
                    val start = parseDate(parser.getAttributeValue(null, "start"))
                    val stop = parseDate(parser.getAttributeValue(null, "stop"))
                    var title: String? = null
                    var desc: String? = null
                    // Walk children until we close the programme.
                    while (true) {
                        val e = parser.next()
                        if (e == XmlPullParser.END_TAG && parser.name == "programme") break
                        if (e == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "title" -> title = readText(parser)
                                "desc" -> if (desc == null) desc = readText(parser)
                                else -> skipToEndTag(parser, parser.name)
                            }
                        }
                        if (e == XmlPullParser.END_DOCUMENT) break
                    }
                    if (title != null && start != null && stop != null && stop > start) {
                        out += EpgEntity(
                            channelId = channelId,
                            title = title,
                            description = desc,
                            startMs = start,
                            endMs = stop,
                        )
                    }
                }
            }
            event = parser.next()
        }
        return out
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

    private fun skipToEndTag(parser: XmlPullParser, tag: String) {
        var depth = 1
        while (depth > 0) {
            val e = parser.next()
            if (e == XmlPullParser.END_DOCUMENT) return
            if (e == XmlPullParser.START_TAG) depth++
            if (e == XmlPullParser.END_TAG && parser.name == tag) depth--
        }
    }

    private fun parseDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        for (fmt in xmltvDateFormats) {
            val parsed = runCatching { fmt.parse(raw) }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }
}
