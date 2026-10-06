package com.ultratv.tv.nativeapp.data.m3u

import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class M3uResult(val channels: List<ChannelEntity>, val categories: List<CategoryEntity>)

/**
 * Streaming-friendly M3U parser. Reads the standard extended-M3U format:
 *
 *   #EXTM3U
 *   #EXTINF:-1 tvg-id="…" tvg-name="…" tvg-logo="…" group-title="News",My Channel
 *   http://…/stream.ts
 *
 * Each `#EXTINF` line is paired with the next non-comment URL. We only keep
 * channels for Phase 4 (movies/series in M3U lack a standard schema).
 */
@Singleton
class M3uParser @Inject constructor(okBase: OkHttpClient) {
    private val ok: OkHttpClient = okBase.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .build()


    suspend fun fetch(url: String, providerId: Long): M3uResult = withContext(Dispatchers.IO) {
        // Lecture en flux : on ne matérialise ni le corps (jusqu'à plusieurs dizaines
        // de Mo) ni la liste de toutes ses lignes, ce qui faisait grimper le tas
        // d'une box à 1-2 Go jusqu'au GC en boucle.
        ok.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw com.ultratv.tv.nativeapp.data.net.HttpStatusException(resp.code)
            val body = resp.body ?: return@use M3uResult(emptyList(), emptyList())
            body.charStream().buffered().use { parse(it, providerId) }
        }
    }

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String, providerId: Long): M3uResult =
        text.reader().buffered().use { parse(it, providerId) }

    /** Parse ligne à ligne : un `#EXTINF` est associé à la prochaine ligne non-commentaire. */
    fun parse(reader: java.io.BufferedReader, providerId: Long): M3uResult {
        val channels = ArrayList<ChannelEntity>()
        val groupsSeen = LinkedHashMap<String, CategoryEntity>()
        var seq = 0

        var pending: String? = null   // dernière ligne #EXTINF en attente d'URL
        val usedIds = HashSet<String>()
        while (true) {
            val raw = reader.readLine() ?: break
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) { pending = line; continue }
            if (line.startsWith("#")) continue
            val info = pending ?: continue
            pending = null

            val attrs = attrRegex.findAll(info).associate { it.groupValues[1] to it.groupValues[2] }
            val displayName = info.substringAfterLast(',', "").trim().ifBlank { attrs["tvg-name"] ?: "Channel" }
            val group = attrs["group-title"]?.takeIf { it.isNotBlank() }
            if (group != null && group !in groupsSeen) {
                groupsSeen[group] = CategoryEntity(
                    providerId = providerId, kind = "LIVE",
                    remoteId = "g:$group", name = group,
                )
            }
            val tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
            // Un tvg-id est souvent RÉPÉTÉ (variantes HD/SD, même chaîne dans plusieurs groupes) : l'identifiant est unique
            // en base, donc les suivantes écrasaient la première. La première garde le tvg-id (favoris existants), les
            // autres reçoivent un suffixe ; toutes gardent tvg-id pour le guide.
            val rid = when {
                tvgId == null -> "m3u-${seq++}"
                usedIds.add(tvgId) -> tvgId
                else -> "$tvgId#${seq++}"
            }
            channels += ChannelEntity(
                providerId = providerId,
                remoteId = rid,
                name = displayName,
                logo = attrs["tvg-logo"],
                categoryId = group?.let { "g:$it" },
                streamUrl = line,
                epgChannelId = tvgId,
                // Rattrapage : `catchup-source` + `catchup-days` (voir Catchup.kt).
                catchupSource = attrs["catchup-source"]?.takeIf { it.isNotBlank() },
                catchupDays = attrs["catchup-days"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            )
        }
        return M3uResult(channels, groupsSeen.values.toList())
    }
}
