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
    private val cloudSource: CloudConfigSource,
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
     * Récupère la configuration de cet appareil auprès du Worker avec son jeton
     * d'appareil (obtenu par appairage). La MAC n'est plus envoyée : ce n'est
     * qu'une étiquette d'affichage, jamais une clé d'accès.
     *
     * @throws NotPairedException aucun jeton stocké ; l'UI doit lancer l'appairage
     * @throws TokenRejectedException le Worker a révoqué ce jeton (déjà effacé localement)
     */
    suspend fun importFromCloud(workerBase: String, onProgress: (String) -> Unit = {}): ImportResult {
        onProgress("Fetching config…")
        return importJson(cloudSource.fetch(workerBase), onProgress)
    }

    /** Import depuis une URL JSON quelconque fournie par l'utilisateur (gist, serveur perso). */
    suspend fun importFromUrl(url: String, onProgress: (String) -> Unit = {}): ImportResult {
        val body = withContext(Dispatchers.IO) {
            onProgress("Fetching config…")
            ok.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code} — config URL unreachable")
                resp.body?.string().orEmpty()
            }
        }
        return importJson(body, onProgress)
    }

    private suspend fun importJson(body: String, onProgress: (String) -> Unit): ImportResult =
        withContext(Dispatchers.IO) {
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
