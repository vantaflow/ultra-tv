package com.ultratv.tv.nativeapp.data.stalker

import com.ultratv.tv.nativeapp.data.db.ProviderDao
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adresse stockée → adresse jouable, juste AVANT d'ouvrir le flux. Les adresses ordinaires (Xtream, M3U) sont
 * renvoyées telles quelles ; une adresse `stalker://…` demande au portail un lien de lecture (`create_link`), qui
 * n'est valable que peu de temps et ne peut donc pas être calculé à la synchro.
 */
interface StreamUrlResolver {
    suspend fun resolve(url: String): String

    /** Comme [resolve], avec les en-têtes que le lecteur doit envoyer (portail Stalker) ; lève en cas d'échec. */
    suspend fun open(url: String): StreamOpen = StreamOpen(resolve(url))

    /** Signal de présence (watchdog) pendant la lecture d'une adresse `stalker://` ; sans effet pour toute autre. */
    suspend fun presence(url: String) {}
}

/** Flux prêt à ouvrir : adresse, en-têtes de requête et User-Agent imposés (null = celui du lecteur). */
data class StreamOpen(val url: String, val headers: Map<String, String> = emptyMap(), val userAgent: String? = null, val stalker: Boolean = false)

@Singleton
class DefaultStreamUrlResolver @Inject constructor(
    private val providers: ProviderDao,
    private val stalker: StalkerClient,
) : StreamUrlResolver {
    override suspend fun resolve(url: String): String {
        val link = StalkerLink.parse(url) ?: return url
        val p = providers.byId(link.providerId) ?: return url
        if (p.kind != "STALKER") return url
        return stalker.createLink(p, link.type, link.cmd, link.series, link.chId)
    }

    override suspend fun presence(url: String) {
        val link = StalkerLink.parse(url) ?: return
        val p = providers.byId(link.providerId)?.takeIf { it.kind == "STALKER" } ?: return
        stalker.watchdog(p)
    }

    override suspend fun open(url: String): StreamOpen {
        val link = StalkerLink.parse(url) ?: return StreamOpen(url)
        val p = providers.byId(link.providerId)?.takeIf { it.kind == "STALKER" } ?: return StreamOpen(url)
        val play = stalker.openLink(p, link.type, link.cmd, link.series, link.chId)
        return StreamOpen(play.url, play.headers, play.userAgent, stalker = true)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StreamUrlModule {
    @Binds abstract fun resolver(impl: DefaultStreamUrlResolver): StreamUrlResolver
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface StreamUrlEntryPoint {
    fun streamUrlResolver(): StreamUrlResolver
}

/**
 * Accès en UN appel depuis un écran Compose (lecteur) sans changer son ViewModel. Ne lève jamais : en cas d'échec
 * l'adresse d'origine est rendue (le lecteur affiche alors son erreur habituelle et propose « Réessayer »).
 */
object StreamUrls {
    /** Présence d'un boîtier MAG (watchdog) ; ne lève jamais (hors annulation). */
    suspend fun presence(context: android.content.Context, url: String) {
        if (!StalkerLink.isStalker(url)) return
        runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, StreamUrlEntryPoint::class.java).streamUrlResolver().presence(url)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
    }

    /** Ouverture d'un flux pour le lecteur : lien frais + en-têtes (Stalker). Lève en cas d'échec — l'appelant journalise et classe. */
    suspend fun open(context: android.content.Context, url: String): StreamOpen {
        if (!StalkerLink.isStalker(url)) return StreamOpen(url)
        return EntryPointAccessors.fromApplication(context.applicationContext, StreamUrlEntryPoint::class.java).streamUrlResolver().open(url)
    }

    suspend fun resolve(context: android.content.Context, url: String): String {
        if (!StalkerLink.isStalker(url)) return url
        return runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, StreamUrlEntryPoint::class.java).streamUrlResolver().resolve(url)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(url)
    }
}

/** En-têtes et User-Agent d'un flux ouvert, appliqués à une requête OkHttp (téléchargement, enregistrement, relais de différé). */
fun StreamOpen.applyTo(b: okhttp3.Request.Builder): okhttp3.Request.Builder {
    headers.forEach { (k, v) -> b.header(k, v) }
    userAgent?.let { b.header("User-Agent", it) }
    return b
}

/**
 * Client dont toutes les requêtes vers l'hôte du flux portent les en-têtes de [o] (HLS : playlist et segments). Un autre
 * hôte (CDN, redirection) ne reçoit jamais le cookie ni le jeton. Sans en-tête à ajouter, le client est rendu tel quel.
 */
fun okhttp3.OkHttpClient.withStreamHeaders(o: StreamOpen): okhttp3.OkHttpClient {
    if (o.headers.isEmpty() && o.userAgent == null) return this
    val home = o.url.toHttpUrlOrNull() ?: return this
    return newBuilder().addInterceptor { chain ->
        val req = chain.request()
        chain.proceed(if (req.url.host == home.host && req.url.port == home.port) o.applyTo(req.newBuilder()).build() else req)
    }.build()
}
