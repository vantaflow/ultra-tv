package com.ultratv.tv.nativeapp.data.config

import com.ultratv.tv.nativeapp.data.repo.ProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote-config import. Lets the user host a single JSON file (gist, pastebin,
 * own web server) listing every provider, and pull it from the TV in one click.
 *
 * Expected JSON schema (all top-level fields except `providers` optional):
 *
 *   {
 *     "providers": [
 *       { "kind": "XTREAM",  "name": "My Xtream",  "url": "http://host:80",
 *         "username": "user", "password": "pass" },
 *       { "kind": "M3U",     "name": "My M3U",     "url": "https://.../list.m3u" },
 *       { "kind": "STALKER", "name": "MAG portal", "url": "http://host:8080",
 *         "mac": "00:1A:79:XX:XX:XX" }
 *     ]
 *   }
 *
 * Unknown fields are ignored. Each provider is added and synced sequentially;
 * failures on one provider do not abort the rest.
 */
@Singleton
class RemoteConfigImporter @Inject constructor(
    private val ok: OkHttpClient,
    private val provider: ProviderRepository,
) {
    @Serializable
    private data class ConfigRoot(val providers: List<ProviderSpec> = emptyList())

    @Serializable
    private data class ProviderSpec(
        val kind: String,
        val name: String = "",
        val url: String = "",
        val username: String = "",
        val password: String = "",
        val mac: String = "",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * Pulls config for the device's pseudo-MAC from the configured Cloudflare
     * Worker. The Worker URL is the deployment root (e.g.
     * "https://ultratv-config.your-worker.workers.dev"); we append the API
     * path ourselves.
     */
    suspend fun importByMac(workerBase: String, mac: String, onProgress: (String) -> Unit = {}): ImportResult {
        val base = workerBase.trimEnd('/')
        return importFromUrl("$base/api/config/$mac", onProgress)
    }

    suspend fun importFromUrl(url: String, onProgress: (String) -> Unit = {}): ImportResult =
        withContext(Dispatchers.IO) {
            onProgress("Fetching config…")
            val body = ok.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code} — config URL unreachable")
                resp.body?.string().orEmpty()
            }
            val cfg = runCatching { json.decodeFromString(ConfigRoot.serializer(), body) }
                .getOrElse { error("Invalid JSON: ${it.message}") }

            var ok = 0
            val errors = mutableListOf<String>()
            cfg.providers.forEachIndexed { i, p ->
                val label = p.name.ifBlank { "Provider #${i + 1}" }
                try {
                    onProgress("[$label] adding…")
                    val id = when (p.kind.uppercase()) {
                        "XTREAM" -> provider.addXtream(p.name, p.url, p.username, p.password)
                        "M3U" -> provider.addM3u(p.name, p.url)
                        "STALKER" -> provider.addStalker(p.name, p.url, p.mac)
                        else -> { errors += "$label: unknown kind '${p.kind}'"; return@forEachIndexed }
                    }
                    onProgress("[$label] syncing…")
                    provider.syncAll(id) { onProgress("[$label] $it") }
                    ok++
                } catch (t: Throwable) {
                    errors += "$label: ${t.message}"
                }
            }
            ImportResult(imported = ok, errors = errors)
        }

    data class ImportResult(val imported: Int, val errors: List<String>)
}
