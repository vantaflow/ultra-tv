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
class M3uParser @Inject constructor(private val ok: OkHttpClient) {

    suspend fun fetch(url: String, providerId: Long): M3uResult = withContext(Dispatchers.IO) {
        val body = ok.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code} fetching M3U")
            resp.body?.string().orEmpty()
        }
        parse(body, providerId)
    }

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String, providerId: Long): M3uResult {
        val lines = text.lineSequence().map { it.trim() }.toList()
        val channels = mutableListOf<ChannelEntity>()
        val groupsSeen = LinkedHashMap<String, CategoryEntity>()

        var i = 0
        var seq = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXTINF")) {
                // Parse attributes and the trailing display name.
                val attrs = attrRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                val displayName = line.substringAfterLast(',', "").trim().ifBlank { attrs["tvg-name"] ?: "Channel" }
                val logo = attrs["tvg-logo"]
                val group = attrs["group-title"]?.takeIf { it.isNotBlank() }

                // Find the next non-comment line as the stream URL.
                var j = i + 1
                while (j < lines.size && lines[j].startsWith("#")) j++
                val url = lines.getOrNull(j)?.takeIf { it.isNotBlank() && !it.startsWith("#") }
                if (url != null) {
                    if (group != null && group !in groupsSeen) {
                        groupsSeen[group] = CategoryEntity(
                            providerId = providerId, kind = "LIVE",
                            remoteId = "g:$group", name = group,
                        )
                    }
                    val tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                    channels += ChannelEntity(
                        providerId = providerId,
                        remoteId = tvgId ?: "m3u-${seq++}",
                        name = displayName,
                        logo = logo,
                        categoryId = group?.let { "g:$it" },
                        streamUrl = url,
                        epgChannelId = tvgId,
                    )
                    i = j + 1
                    continue
                }
            }
            i++
        }
        return M3uResult(channels, groupsSeen.values.toList())
    }
}
