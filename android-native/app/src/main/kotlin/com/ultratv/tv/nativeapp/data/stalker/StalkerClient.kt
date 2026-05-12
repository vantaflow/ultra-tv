package com.ultratv.tv.nativeapp.data.stalker

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stalker Portal (MAG-style) client. Stalker uses MAC-address authentication and
 * a per-session token. Real flow:
 *
 *   1. handshake → returns a JWT-style token tied to the MAC
 *   2. get_profile  → side-effect that some portals require before subsequent calls
 *   3. get_genres    → live categories
 *   4. get_all_channels → live channels (URLs are opaque `cmd` strings)
 *   5. create_link  → resolves a `cmd` into a playable URL at play time
 *
 * The `cmd` URLs returned by Stalker are not directly playable. They look like
 * `ffrt http://1.2.3.4/iptv/123/...` — Stalker expects the client to call
 * `create_link` to get the actual stream URL with a per-session token.
 * We resolve lazily right before playback (see [resolvePlayUrl]).
 *
 * Provider fields we use:
 *   baseUrl  — portal root, e.g. http://provider.com:8080 (no /c/ etc)
 *   username — MAC address (XX:XX:XX:XX:XX:XX)
 *   password — unused, kept for schema parity
 */
@Singleton
class StalkerClient @Inject constructor(private val ok: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val ua =
        "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) " +
                "MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

    data class Session(val token: String, val portalRoot: String)

    suspend fun handshake(p: ProviderEntity): Session = withContext(Dispatchers.IO) {
        val portal = portalRoot(p.baseUrl)
        val url = "$portal/portal.php?type=stb&action=handshake&JsHttpRequest=1-xml"
        val body = call(url, p)
        val token = (json.parseToJsonElement(body) as? JsonObject)
            ?.get("js")?.jsonObject?.get("token")?.jsonPrimitive?.contentOrNull
            ?: error("Stalker handshake failed (no token). Check URL and MAC.")
        // Some portals require a profile fetch right after handshake.
        runCatching { call("$portal/portal.php?type=stb&action=get_profile&token=$token", p, token) }
        Session(token = token, portalRoot = portal)
    }

    suspend fun fetchLiveCategories(p: ProviderEntity, s: Session): List<CategoryEntity> = runCatching {
        val body = call("${s.portalRoot}/portal.php?type=itv&action=get_genres", p, s.token)
        val arr = (json.parseToJsonElement(body) as? JsonObject)?.get("js") as? JsonArray ?: return@runCatching emptyList()
        arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val rid = o["id"]?.str() ?: return@mapNotNull null
            val name = o["title"]?.str() ?: return@mapNotNull null
            CategoryEntity(providerId = p.id, kind = "LIVE", remoteId = rid, name = name)
        }
    }.getOrDefault(emptyList())

    suspend fun fetchLiveChannels(p: ProviderEntity, s: Session): List<ChannelEntity> = runCatching {
        // page=0 returns all items on most portals. If the portal paginates we'd
        // need to loop on total_items / max_page_items — punted to v2.
        val body = call(
            "${s.portalRoot}/portal.php?type=itv&action=get_all_channels&JsHttpRequest=1-xml",
            p, s.token,
        )
        val arr = (json.parseToJsonElement(body) as? JsonObject)
            ?.get("js")?.jsonObject?.get("data") as? JsonArray ?: return@runCatching emptyList()
        arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val rid = o["id"]?.str() ?: return@mapNotNull null
            val name = o["name"]?.str() ?: return@mapNotNull null
            val logo = o["logo"]?.str()?.takeIf { it.isNotBlank() }
            // We store the raw `cmd` here — it's NOT a playable URL. resolvePlayUrl()
            // converts it to a playable one at play time.
            val cmd = o["cmd"]?.str() ?: return@mapNotNull null
            val genre = o["tv_genre_id"]?.str()
            ChannelEntity(
                providerId = p.id,
                remoteId = rid,
                name = name,
                logo = logo,
                categoryId = genre,
                streamUrl = "stalker://$cmd",   // sentinel — see resolvePlayUrl()
            )
        }
    }.getOrDefault(emptyList())

    /** Turns a stored `stalker://<cmd>` URL into a playable URL via create_link. */
    suspend fun resolvePlayUrl(p: ProviderEntity, channelStreamUrl: String): String =
        withContext(Dispatchers.IO) {
            val cmd = channelStreamUrl.removePrefix("stalker://")
            val s = handshake(p)
            val encoded = java.net.URLEncoder.encode(cmd, "UTF-8")
            val body = call(
                "${s.portalRoot}/portal.php?type=itv&action=create_link&cmd=$encoded&JsHttpRequest=1-xml",
                p, s.token,
            )
            val raw = (json.parseToJsonElement(body) as? JsonObject)
                ?.get("js")?.jsonObject?.get("cmd")?.jsonPrimitive?.contentOrNull
                ?: error("Stalker create_link returned no URL")
            // create_link typically returns "ffrt http://1.2.3.4/iptv/..." — strip the prefix.
            raw.substringAfter(' ', raw)
        }

    // ---- Helpers ----

    private fun portalRoot(rawBase: String): String {
        val s = rawBase.trimEnd('/')
        // Common portal paths: "/c", "/stalker_portal/c". We always hit portal.php
        // at the same level, so strip these conveniences.
        return when {
            s.endsWith("/stalker_portal/c") -> s.removeSuffix("/c")
            s.endsWith("/c") -> s.removeSuffix("/c")
            else -> s
        }
    }

    private suspend fun call(url: String, p: ProviderEntity, token: String? = null): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .header("X-User-Agent", "Model: MAG250; Link: WiFi")
                .header("Accept", "*/*")
                .header("Cookie", "mac=${p.username.urlEnc()}; stb_lang=en; timezone=Europe/London")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
            ok.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Stalker HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
        }

    private fun kotlinx.serialization.json.JsonElement.str(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private fun String.urlEnc(): String = java.net.URLEncoder.encode(this, "UTF-8")
}
