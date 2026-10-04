package com.ultratv.tv.nativeapp.data.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * Logique PURE de la synchro cloud multi-appareils (aucune dépendance Android hors org.json) : analyse de la réponse
 * du Worker et fusion local / cloud. Testée à part (CloudSyncLogicTest).
 *
 * Règles de fusion :
 *  - une source cloud inconnue localement est AJOUTÉE ;
 *  - une source déjà liée dont l'adresse, les identifiants ou la MAC ont changé dans le cloud est MISE À JOUR : le cloud gagne
 *    pour les identifiants, même si le local a été modifié ;
 *  - le local garde ses préférences : source par défaut, langues, catégories, favoris... ne sont jamais touchés, et le NOM
 *    local n'est écrasé que si l'utilisateur ne l'a pas renommé ;
 *  - une source liée qui n'est plus dans la réponse (supprimée du cloud ou plus affectée à cet appareil) est RETIRÉE,
 *    avec confirmation si des données locales en dépendent (favoris, enregistrements) ;
 *  - une source locale jamais envoyée (sans lien) n'est JAMAIS touchée.
 */

data class CloudDevice(val id: String, val name: String, val label: String)

data class CloudProvider(
    val id: String,
    val kind: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val mac: String,
    /** `null` = tous les appareils du compte ; sinon la liste explicite. */
    val assign: List<String>?,
    val originName: String,
    val updatedAt: Long,
    /** Réglages d'affichage partagés (langues, catégories désactivées) ; null si jamais publiés. */
    val prefs: CloudPrefs? = null,
) {
    /** Nombre d'appareils qui reçoivent cette source. */
    fun sharedWith(deviceCount: Int): Int = assign?.size ?: deviceCount
}

data class CloudConfig(
    val version: Long,
    val selfId: String?,
    val devices: List<CloudDevice>,
    val providers: List<CloudProvider>,
)

/** Une source locale telle que vue par la fusion. */
data class LocalProvider(
    val localId: Long,
    val kind: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val mac: String,
    /** Identifiant cloud si la source a été reçue du cloud ou envoyée vers lui. */
    val cloudId: String?,
)

/** Dernier nom reçu du cloud pour une source liée : permet de savoir si l'utilisateur l'a renommée. */
data class AppliedName(val name: String)

sealed interface SyncAction {
    data class Add(val cloud: CloudProvider) : SyncAction
    /** Adopte une source locale identique (même serveur et même compte) au lieu de la dupliquer. */
    data class Link(val localId: Long, val cloud: CloudProvider) : SyncAction
    data class Update(val localId: Long, val cloud: CloudProvider, val renameLocal: Boolean) : SyncAction
    data class Remove(val localId: Long, val name: String, val dependents: Int) : SyncAction {
        val needsConfirm: Boolean get() = dependents > 0
    }
}

val SUPPORTED_KINDS = setOf("XTREAM", "M3U")

object CloudSyncLogic {

    /** Identité d'une source : même type, même serveur (sans « / » final), même compte. */
    fun identity(kind: String, url: String, username: String): String =
        "${kind.uppercase()}|${url.trim().trimEnd('/').lowercase()}|$username"

    fun parseConfig(body: String): CloudConfig {
        val o = JSONObject(body)
        val selfFromDevices = o.optJSONArray("devices").toList { d -> d.optString("id").takeIf { d.optBoolean("isCurrent") } }.filterNotNull().firstOrNull()
        val devices = o.optJSONArray("devices").toList { d -> CloudDevice(d.optString("id"), d.optString("name"), d.optString("model").ifBlank { d.optString("label") }) }
        val providers = o.optJSONArray("providers").toList { p ->
            val a = p.opt("sharedWith")
            CloudProvider(
                id = p.optString("id"), kind = p.optString("kind").uppercase(), name = p.optString("name"), url = p.optString("url"),
                username = p.optString("username"), password = p.optString("password"), mac = p.optString("mac"),
                assign = if (a is JSONArray) (0 until a.length()).map { a.optString(it) } else null,
                originName = p.optString("originName"), updatedAt = p.optLong("updatedAt"),
                prefs = DisplayPrefs.parse(p.optJSONObject("prefs")),
            )
        }.filter { it.id.isNotBlank() }
        return CloudConfig(o.optLong("version"), o.optString("self").ifBlank { null } ?: selfFromDevices, devices, providers)
    }

    private fun <T> JSONArray?.toList(f: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(f) }

    /**
     * Une source M3U « get.php / player_api.php » avec identifiants EST une source Xtream Codes (get.php est
     * souvent bloqué, ex. HTTP 884). L'appareil la convertit ; sans cette normalisation, la fusion voyait la
     * version cloud (M3U) « différente » de la locale (Xtream) et réécrivait l'adresse get.php avec des
     * identifiants vides à chaque synchro cloud.
     */
    fun normalized(c: CloudProvider): CloudProvider {
        if (c.kind != "M3U") return c
        val x = com.ultratv.tv.nativeapp.data.net.XtreamUrl.parse(c.url) ?: return c
        return c.copy(kind = "XTREAM", url = x.server.trimEnd('/'), username = x.username, password = x.password)
    }

    /**
     * @param dependents nombre de données locales (favoris + enregistrements) qui dépendent de chaque source locale
     * @param appliedNames dernier nom reçu du cloud, par identifiant cloud
     */
    fun plan(
        local: List<LocalProvider>,
        cloud: List<CloudProvider>,
        dependents: Map<Long, Int>,
        appliedNames: Map<String, String>,
    ): List<SyncAction> {
        val actions = mutableListOf<SyncAction>()
        val supported = cloud.map(::normalized).filter { it.kind in SUPPORTED_KINDS }
        val byCloudId = local.filter { it.cloudId != null }.associateBy { it.cloudId!! }
        val unlinkedByIdentity = local.filter { it.cloudId == null }.associateBy { identity(it.kind, it.url, it.username) }.toMutableMap()
        for (c in supported) {
            val linked = byCloudId[c.id]
            if (linked != null) {
                if (differs(linked, c)) {
                    val renamed = linked.name != (appliedNames[c.id] ?: linked.name)
                    actions += SyncAction.Update(linked.localId, c, renameLocal = !renamed && linked.name != c.name)
                } else if (linked.name != c.name && linked.name == appliedNames[c.id]) {
                    actions += SyncAction.Update(linked.localId, c, renameLocal = true)
                }
                continue
            }
            val twin = unlinkedByIdentity.remove(identity(c.kind, c.url, c.username))
            if (twin != null) {
                actions += SyncAction.Link(twin.localId, c)
                if (differs(twin, c)) actions += SyncAction.Update(twin.localId, c, renameLocal = false)
            } else actions += SyncAction.Add(c)
        }
        val keep = supported.map { it.id }.toSet()
        for (l in local) {
            val id = l.cloudId ?: continue
            if (id !in keep) actions += SyncAction.Remove(l.localId, l.name, dependents[l.localId] ?: 0)
        }
        return actions
    }

    private fun differs(l: LocalProvider, c: CloudProvider): Boolean =
        l.url.trim().trimEnd('/') != c.url.trim().trimEnd('/') || l.username != c.username || l.password != c.password ||
            (c.kind == "STALKER" && l.mac != c.mac)

    /**
     * Avertissement de connexions (jamais bloquant) : la source n'autorise qu'un flux à la fois et au moins deux
     * appareils la reçoivent.
     */
    fun connectionWarning(maxConnections: Int, sharedWith: Int): Boolean = maxConnections <= 1 && sharedWith >= 2

    /** Corps JSON envoyé au Worker pour partager / mettre à jour une source locale. */
    fun uploadBody(l: LocalProvider, shareWith: List<String>?, cloudId: String?): String {
        val o = JSONObject().put("kind", l.kind).put("name", l.name).put("url", l.url)
        if (l.kind == "XTREAM") o.put("username", l.username).put("password", l.password)
        if (cloudId != null) o.put("id", cloudId)
        o.put("shareWith", if (shareWith == null) "all" else JSONArray(shareWith))
        return o.toString()
    }

    /** Nom affiché de l'appareil : le nom donné dans le tableau de bord, à défaut l'étiquette (modèle). */
    fun deviceDisplayName(d: CloudDevice): String = d.name.ifBlank { d.label }.ifBlank { d.id.take(6) }
}
