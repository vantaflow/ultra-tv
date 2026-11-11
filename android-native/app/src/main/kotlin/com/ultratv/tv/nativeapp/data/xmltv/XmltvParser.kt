package com.ultratv.tv.nativeapp.data.xmltv

import android.util.Xml
import com.ultratv.tv.nativeapp.data.db.EpgEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import com.ultratv.tv.nativeapp.data.net.HttpStatusException
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

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
class XmltvParser internal constructor(
    okBase: OkHttpClient,
    internal val limits: EpgLimits,
    private val sleeper: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    @Inject constructor(okBase: OkHttpClient) : this(okBase, EpgLimits())

    // Flux de 150 Mo : pas de délai d'appel global (le chien de garde de [GuardedInput] borne la durée), connexion et inactivité limitées.
    private val ok: OkHttpClient = okBase.newBuilder()
        .connectTimeout(limits.connectMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(limits.stallMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /** Une tentative a échoué définitivement après [attempts] essais ; [cause] porte le détail (classé par NetErrors). */
    class FetchException(cause: Throwable, val attempts: Int) : IOException(cause.message, cause)

    /**
     * Ouvre [request], surveille la connexion (calage, débit, durée), décompresse le gzip EN FLUX et passe le flux à
     * [consume]. Échec réseau / 5xx / 408 / 429 / délai : jusqu'à [EpgLimits.maxAttempts] essais avec attente croissante.
     * [consume] est donc rejoué depuis le début : ses écritures doivent être idempotentes (la table epg remplace sur
     * (chaîne, début)). Jamais de nouvel essai sur 401/403/404 ni sur un XML invalide.
     */
    private suspend fun <R> fetch(request: Request, onBytes: (Long, Long) -> Unit, consume: suspend (InputStream) -> R): R {
        var attempt = 1
        while (true) {
            try {
                return fetchOnce(request, onBytes, consume)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (attempt >= limits.maxAttempts || !EpgErrors.retryable(t)) throw FetchException(t, attempt)
                sleeper(limits.backoff(attempt))
                attempt++
            }
        }
    }

    private suspend fun <R> fetchOnce(request: Request, onBytes: (Long, Long) -> Unit, consume: suspend (InputStream) -> R): R {
        val call = ok.newCall(request)
        val resp = call.execute()
        resp.use {
            if (!resp.isSuccessful) throw HttpStatusException(resp.code)
            val body = resp.body ?: error("Empty xmltv body")
            val len = body.contentLength()
            val guard = GuardedInput(
                body.byteStream(), limits,
                onAbort = { call.cancel(); runCatching { resp.close() } },
                onBytes = { onBytes(it, len) }, expectedBytes = len,
            )
            return guard.use { consume(maybeGunzip(BufferedInputStream(it, 64 * 1024))) }
        }
    }

    /**
     * Ouvre le flux XMLTV et passe à [block] une séquence PARESSEUSE de programmes
     * (filtrés par [window] et par chaînes présentes). Rien n'est accumulé : le
     * appelant insère par lots au fil de l'eau, la mémoire reste plate pour 150 Mo de XML.
     * [onBytes] : (octets lus, taille annoncée ou -1). [block] peut être rejoué (nouvelle tentative) : idempotent.
     */
    suspend fun <R> withProgrammes(
        p: ProviderEntity,
        channelXmltvIdToLocalId: Map<String, Long>,
        window: LongRange = defaultWindow(),
        onBytes: (Long, Long) -> Unit = { _, _ -> },
        block: suspend (Sequence<EpgEntity>) -> R,
    ): R = withContext(Dispatchers.IO) {
        // Flux pré-téléchargé pendant l'écriture du catalogue (voir [prefetch]) : lu depuis le fichier, sans attendre le réseau.
        val pre = prefetched.remove(p.id)?.let { runCatching { it.await() }.getOrNull() }
        if (pre != null) {
            try {
                val size = pre.length()
                return@withContext pre.inputStream().use { fin ->
                    val counting = object : java.io.FilterInputStream(fin) {
                        var n = 0L
                        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) { n += it; onBytes(n, size) } }
                    }
                    block(programmes(maybeGunzip(BufferedInputStream(counting, 64 * 1024)), channelXmltvIdToLocalId, window))
                }
            } catch (e: IOException) {
                // Fichier abîmé : retéléchargement ci-dessous.
            } catch (e: org.xmlpull.v1.XmlPullParserException) {
            } finally { pre.delete() }
        }
        fetch(Request.Builder().url(feedUrl(p)).build(), onBytes) { stream -> block(programmes(stream, channelXmltvIdToLocalId, window)) }
    }

    private fun feedUrl(p: ProviderEntity) = "${p.baseUrl}/xmltv.php?username=${java.net.URLEncoder.encode(p.username, "UTF-8")}" +
        "&password=${java.net.URLEncoder.encode(p.password, "UTF-8")}"

    private val prefetched = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.Deferred<java.io.File>>()

    /**
     * Télécharge le flux XMLTV de la source dans un fichier temporaire, en arrière-plan : [withProgrammes] le lit ensuite
     * depuis le disque. Pendant la première synchro, les 150 Mo du guide arrivent donc pendant que le direct, les films
     * et les séries s'écrivent. Même surveillance et mêmes reprises que [withProgrammes] (jamais d'attente infinie).
     * Un échec n'est jamais fatal : [withProgrammes] retélécharge alors lui-même.
     */
    fun prefetch(scope: kotlinx.coroutines.CoroutineScope, p: ProviderEntity) {
        prefetched.computeIfAbsent(p.id) {
            scope.async(Dispatchers.IO) {
                val f = java.io.File.createTempFile("xmltv-", ".xml")
                try {
                    com.ultratv.tv.nativeapp.data.sync.SyncThread.background {
                        fetchRaw(Request.Builder().url(feedUrl(p)).build()) { body -> f.outputStream().use { out -> body.copyTo(out, 64 * 1024) } }
                    }
                    f
                } catch (t: Throwable) { f.delete(); throw t }
            }
        }
    }

    /** Comme [fetch] mais sans décompression : le fichier garde les octets du serveur (le gzip est lu au moment de l'analyse). */
    private suspend fun fetchRaw(request: Request, consume: (InputStream) -> Unit) {
        var attempt = 1
        while (true) {
            try {
                val call = ok.newCall(request)
                call.execute().use { resp ->
                    if (!resp.isSuccessful) throw HttpStatusException(resp.code)
                    val body = resp.body ?: error("Empty xmltv body")
                    GuardedInput(body.byteStream(), limits, onAbort = { call.cancel(); runCatching { resp.close() } }, expectedBytes = body.contentLength()).use(consume)
                }
                return
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (attempt >= limits.maxAttempts || !EpgErrors.retryable(t)) throw FetchException(t, attempt)
                sleeper(limits.backoff(attempt))
                attempt++
            }
        }
    }

    /** Annule le pré-téléchargement non consommé de la source et supprime son fichier. */
    fun discardPrefetch(p: ProviderEntity) {
        val d = prefetched.remove(p.id) ?: return
        d.cancel()
        d.invokeOnCompletion { if (it == null) runCatching { d.getCompleted().delete() } }
    }

    /**
     * Guide à une URL (guide complémentaire, `url-tvg` d'une playlist M3U ; gzip détecté automatiquement). Les `<channel>`
     * précèdent les `<programme>` : chaque chaîne du guide est résolue (identifiant, noms affichés) vers 0..n chaînes
     * locales, puis ses programmes sont émis pour chacune (décalés de N heures pour « X +N »). Mêmes délais et reprises
     * que [withProgrammes].
     */
    suspend fun <R> withExternal(
        url: String,
        resolve: (feedId: String, names: List<String>) -> List<Pair<Long, Int>>,
        window: LongRange = defaultWindow(),
        onBytes: (Long, Long) -> Unit = { _, _ -> },
        block: suspend (Sequence<EpgEntity>) -> R,
    ): R = withContext(Dispatchers.IO) {
        fetch(Request.Builder().url(url).header("User-Agent", "UltraTV").build(), onBytes) { input -> block(externalProgrammes(input, resolve, window)) }
    }

    fun externalProgrammes(
        input: InputStream,
        resolve: (feedId: String, names: List<String>) -> List<Pair<Long, Int>>,
        window: LongRange = Long.MIN_VALUE..Long.MAX_VALUE,
    ): Sequence<EpgEntity> = sequence {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val map = HashMap<String, List<Pair<Long, Int>>>()
        // Guide sans balises <channel> (rare, mais vu) : les identifiants des programmes sont résolus directement.
        var sawChannel = false
        val unresolved = HashSet<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "channel") {
                sawChannel = true
                val id = parser.getAttributeValue(null, "id")
                val names = ArrayList<String>(2)
                while (true) {
                    val e = parser.next()
                    if (e == XmlPullParser.END_TAG && parser.name == "channel") break
                    if (e == XmlPullParser.END_DOCUMENT) break
                    if (e == XmlPullParser.START_TAG) { if (parser.name == "display-name") names += readText(parser) else skipToEndTag(parser, parser.name) }
                }
                if (id != null) resolve(id, names).takeIf { it.isNotEmpty() }?.let { map[id] = it }
            } else if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val chAttr = parser.getAttributeValue(null, "channel")
                var targets = chAttr?.let { map[it] }
                if (targets == null && chAttr != null && !sawChannel && unresolved.add(chAttr)) {
                    val r = resolve(chAttr, emptyList())
                    if (r.isNotEmpty()) { map[chAttr] = r; unresolved.remove(chAttr); targets = r }
                }
                val start = parseDate(parser.getAttributeValue(null, "start"))
                val stop = parseDate(parser.getAttributeValue(null, "stop"))
                if (targets == null || start == null || stop == null || stop <= start) skipToEndTag(parser, "programme")
                else {
                    var title: String? = null
                    var desc: String? = null
                    while (true) {
                        val e = parser.next()
                        if (e == XmlPullParser.END_TAG && parser.name == "programme") break
                        if (e == XmlPullParser.END_DOCUMENT) break
                        if (e == XmlPullParser.START_TAG) when (parser.name) {
                            "title" -> if (title == null) title = readText(parser)
                            "desc" -> if (desc == null) desc = readText(parser).take(MAX_DESC)
                            else -> skipToEndTag(parser, parser.name)
                        }
                    }
                    if (title != null) for ((cid, h) in targets!!) {
                        val s = start + h * 3_600_000L; val t = stop + h * 3_600_000L
                        if (t >= window.first && s <= window.last) yield(EpgEntity(channelId = cid, title = title, description = desc, startMs = s, endMs = t))
                    }
                }
            }
            event = parser.next()
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
