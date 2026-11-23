package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ProviderEntity

/**
 * « Modifier » une source (Réglages › Sources) : règles pures, testées sans réseau ni base.
 *  - [prefill] : valeurs du formulaire d'ajout rouvert pour la source (le mot de passe est prérempli, affiché masqué) ;
 *  - [changesProvider] : la nouvelle adresse ou le nouveau compte désigne-t-il un AUTRE fournisseur (catalogue remplacé) ?
 * La source garde toujours son identifiant local : favoris, historique, catégories masquées ou verrouillées, numéros et
 * profils restent attachés à elle.
 */
object SourceEdit {
    /** Formulaire prérempli. [user] = identifiant Xtream / NAS ; Stalker : [mac], [login], [password] du portail, [identity]. */
    data class Prefill(
        val kind: String,
        val name: String,
        val url: String,
        val user: String = "",
        val password: String = "",
        val mac: String = "",
        val login: String = "",
        val identity: com.ultratv.tv.nativeapp.data.stalker.StalkerIdentity = com.ultratv.tv.nativeapp.data.stalker.StalkerIdentity(),
    ) {
        // Jamais d'identifiant ni de mot de passe dans un journal.
        override fun toString() = "Prefill($kind)"
    }

    /** Types de source qui ont un formulaire à rouvrir (Plex se relie par code PIN : pas de formulaire). */
    val EDITABLE_KINDS = setOf("XTREAM", "M3U", "M3U_LOCAL", "STALKER", "JELLYFIN", "EMBY", "NAS")

    fun prefill(p: ProviderEntity): Prefill = when (p.kind) {
        "STALKER" -> {
            val (login, pass) = com.ultratv.tv.nativeapp.data.stalker.StalkerCreds.unpack(p.password)
            Prefill(p.kind, p.name, p.baseUrl, mac = p.username, login = login, password = pass,
                identity = com.ultratv.tv.nativeapp.data.stalker.StalkerCreds.identity(p.password))
        }
        "XTREAM", "NAS" -> Prefill(p.kind, p.name, p.baseUrl, user = p.username, password = p.password)
        // Serveur multimédia : `username` = identifiant interne du serveur et `password` = jeton ; rien à montrer.
        "JELLYFIN", "EMBY" -> Prefill(p.kind, p.name, p.baseUrl)
        else -> Prefill(p.kind, p.name, p.baseUrl)
    }

    /**
     * Vrai si la source modifiée pointe vers un autre fournisseur : autre hôte, ou même hôte mais autre compte (identifiant
     * Xtream, MAC Stalker, identifiants d'une adresse de liste M3U, chemin d'une liste M3U). Changer seulement le mot de
     * passe, le schéma (http → https), le port ou le nom ne remplace rien.
     */
    fun changesProvider(old: ProviderEntity, newUrl: String, newUser: String): Boolean {
        val a = hostOf(old.baseUrl)
        val b = hostOf(newUrl)
        if (a == null || b == null) return old.baseUrl.trim().trimEnd('/') != newUrl.trim().trimEnd('/')
        if (!a.equals(b, ignoreCase = true)) return true
        return when (old.kind) {
            "XTREAM", "NAS" -> !old.username.trim().equals(newUser.trim(), ignoreCase = false)
            "STALKER" -> com.ultratv.tv.nativeapp.data.stalker.StalkerMac.normalize(old.username) != com.ultratv.tv.nativeapp.data.stalker.StalkerMac.normalize(newUser)
            "M3U" -> {
                val oldX = com.ultratv.tv.nativeapp.data.net.XtreamUrl.parse(old.baseUrl)
                val newX = com.ultratv.tv.nativeapp.data.net.XtreamUrl.parse(newUrl)
                if (oldX != null || newX != null) oldX?.username != newX?.username
                else pathOf(old.baseUrl) != pathOf(newUrl)
            }
            else -> false
        }
    }

    private fun uri(url: String) = runCatching { java.net.URI(url.trim().let { if (it.contains("://")) it else "http://$it" }) }.getOrNull()
    private fun hostOf(url: String): String? = uri(url)?.host?.takeIf { it.isNotBlank() }
    private fun pathOf(url: String): String = uri(url)?.rawPath.orEmpty().trimEnd('/')
}

/** Une autre source a déjà cette adresse et ce compte : la modification est refusée (pas de doublon). */
class SourceExistsException : IllegalStateException("source already exists")
