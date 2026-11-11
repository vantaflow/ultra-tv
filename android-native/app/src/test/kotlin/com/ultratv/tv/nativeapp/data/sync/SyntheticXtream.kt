package com.ultratv.tv.nativeapp.data.sync

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.buffer
import okio.source
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.Random

/**
 * Faux serveur Xtream EN MÉMOIRE (aucun socket, aucun réseau) : des réponses synthétiques réalistes, générées dans des
 * fichiers temporaires puis servies par un intercepteur OkHttp. Sert au banc de mesure ([SyncBenchTest]) et à son test
 * fonctionnel réduit. Aucune donnée réelle : noms, URL et identifiants sont inventés (hôte `.invalid`).
 */
class SyntheticXtream(
    val dir: File,
    val live: Int, val movies: Int, val series: Int,
    val guideChannels: Int, val guideDays: Int, val perDay: Int,
) {
    /** Débit (octets/s) et latence d'un TÉLÉCHARGEMENT par connexion, comme un serveur IPTV distant (0 = instantané). */
    @Volatile var bytesPerSecond: Long = 0
    @Volatile var latencyMs: Long = 0

    private val liveCats = 140; private val vodCats = 260; private val seriesCats = 110
    private val langs = listOf("FR", "AR", "EN", "ES", "DE", "IT", "TR", "PT", "NL", "RU")
    private val files = HashMap<String, File>()
    val requests = java.util.concurrent.CopyOnWriteArrayList<String>()

    fun generate() {
        dir.mkdirs()
        val rnd = Random(42)
        fun cats(n: Int, kind: String) = buildString {
            append('[')
            for (i in 1..n) {
                if (i > 1) append(',')
                val l = langs[i % langs.size]
                append("""{"category_id":"$i","category_name":"$l | $kind ${i}","parent_id":0}""")
            }
            append(']')
        }
        write("get_live_categories", cats(liveCats, "CHAINES"))
        write("get_vod_categories", cats(vodCats, "FILMS"))
        write("get_series_categories", cats(seriesCats, "SERIES"))
        write("get_live_streams", buildString {
            append('[')
            for (i in 1..live) {
                if (i > 1) append(',')
                val l = langs[i % langs.size]
                val q = listOf("", " HD", " FHD", " 4K", " SD")[i % 5]
                append("""{"num":$i,"name":"$l | Chaine Numero $i$q","stream_type":"live","stream_id":${100000 + i},"stream_icon":"http://logos.invalid/l/$i.png","epg_channel_id":"chaine$i.$l","added":"1700000000","category_id":"${1 + i % liveCats}","custom_sid":"","tv_archive":${i % 2},"direct_source":"","tv_archive_duration":${(i % 2) * 3},"thumbnail":""}""")
            }
            append(']')
        })
        write("get_vod_streams", buildString {
            append('[')
            for (i in 1..movies) {
                if (i > 1) append(',')
                val l = langs[i % langs.size]
                append("""{"num":$i,"name":"$l | Film Imaginaire Numero $i (${1980 + i % 45})","title":"Film $i","year":"${1980 + i % 45}","stream_type":"movie","stream_id":${200000 + i},"stream_icon":"http://img.invalid/p/$i.jpg","rating":"${(rnd.nextInt(90) + 10) / 10.0}","rating_5based":3.5,"added":"${1600000000 + i * 997L}","category_id":"${1 + i % vodCats}","container_extension":"${if (i % 3 == 0) "mp4" else "mkv"}","custom_sid":"","direct_source":""}""")
            }
            append(']')
        })
        val plot = "Une histoire inventée pour le banc de mesure, assez longue pour ressembler à un vrai synopsis de fournisseur : des personnages, un conflit, un dénouement inattendu et quelques mots de plus pour atteindre la taille habituelle. "
        write("get_series", buildString {
            append('[')
            for (i in 1..series) {
                if (i > 1) append(',')
                val l = langs[i % langs.size]
                append("""{"num":$i,"name":"$l | Serie Imaginaire $i (${1990 + i % 35})","series_id":${300000 + i},"cover":"http://img.invalid/s/$i.jpg","plot":"$plot","cast":"Acteur Un, Actrice Deux, Acteur Trois","director":"Réalisateur $i","genre":"Drame, Thriller","releaseDate":"${1990 + i % 35}-01-01","last_modified":"${1650000000 + i * 811L}","rating":"7.1","rating_5based":3.5,"backdrop_path":["http://img.invalid/b/$i.jpg"],"youtube_trailer":"","episode_run_time":"45","category_id":"${1 + i % seriesCats}"}""")
            }
            append(']')
        })
        write("user_info", """{"user_info":{"username":"test","auth":1,"status":"Active","exp_date":"1900000000","is_trial":"0","active_cons":"0","created_at":"1600000000","max_connections":"2"},"server_info":{"url":"serveur-fictif.invalid","timestamp_now":${System.currentTimeMillis() / 1000},"time_now":"2026-01-01 00:00:00"}}""")
        writeXmltv()
    }

    private fun write(key: String, content: String) { File(dir, "$key.json").also { it.writeText(content) }.let { files[key] = it } }

    /** Guide de [guideChannels] chaînes × [guideDays] jours, [perDay] programmes par jour, à partir d'hier (comme un vrai flux de 7 jours). */
    private fun writeXmltv() {
        val f = File(dir, "xmltv.xml"); files["xmltv"] = f
        val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        val dayMs = 86_400_000L
        val t0 = (System.currentTimeMillis() / dayMs - 1) * dayMs
        val slot = dayMs / perDay
        val desc = "Résumé inventé du programme pour le banc de mesure, de la taille d'une description de guide ordinaire, avec accents et une phrase supplémentaire pour la vraisemblance."
        f.bufferedWriter().use { w ->
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv generator-info-name=\"synthetic\">\n")
            for (c in 1..guideChannels) w.write("<channel id=\"chaine$c.${langs[c % langs.size]}\"><display-name>Chaine Numero $c</display-name></channel>\n")
            for (c in 1..guideChannels) {
                val id = "chaine$c.${langs[c % langs.size]}"
                for (d in 0 until guideDays) for (k in 0 until perDay) {
                    val s = t0 + d * dayMs + k * slot
                    w.write("<programme start=\"${fmt.format(java.util.Date(s))} +0000\" stop=\"${fmt.format(java.util.Date(s + slot))} +0000\" channel=\"$id\"><title lang=\"fr\">Programme $c-$d-$k</title><desc lang=\"fr\">$desc</desc></programme>\n")
                }
            }
            w.write("</tv>\n")
        }
    }

    fun sizeMb(key: String): Double = (files[key]?.length() ?: 0) / 1_048_576.0

    /** Intercepteur à brancher sur l'OkHttpClient du XtreamClient et de XmltvParser. */
    val interceptor = Interceptor { chain ->
        val req = chain.request()
        val url = req.url
        val key = when {
            url.encodedPath.endsWith("xmltv.php") -> "xmltv"
            else -> url.queryParameter("action") ?: "user_info"
        }
        requests += key
        // Filtre par catégorie : le faux serveur ne le sait pas (comme les panneaux qui l'ignorent) → repli global.
        val f = files[key] ?: return@Interceptor Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(404).message("nf")
            .body(ResponseBody.create("text/plain".toMediaType(), "")).build()
        if (latencyMs > 0) Thread.sleep(latencyMs)
        val raw: InputStream = f.inputStream()
        val body = (if (bytesPerSecond > 0) Throttled(raw, bytesPerSecond) else raw).source().buffer()
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("ok")
            .body(body.asResponseBody(if (key == "xmltv") "application/xml".toMediaType() else "application/json".toMediaType(), f.length())).build()
    }

    private fun okio.BufferedSource.asResponseBody(type: okhttp3.MediaType, len: Long): ResponseBody =
        object : ResponseBody() {
            override fun contentType() = type
            override fun contentLength() = len
            override fun source() = this@asResponseBody
        }

    /** Limite le débit d'une connexion (sommeil proportionnel aux octets lus). */
    private class Throttled(input: InputStream, private val bps: Long) : FilterInputStream(input) {
        private var sent = 0L
        private val t0 = System.nanoTime()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, minOf(len, 64 * 1024))
            if (n > 0) {
                sent += n
                val due = sent * 1_000_000_000L / bps - (System.nanoTime() - t0)
                if (due > 1_000_000) Thread.sleep(due / 1_000_000, (due % 1_000_000).toInt())
            }
            return n
        }
    }
}
