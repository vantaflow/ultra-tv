package com.ultratv.tv.nativeapp.data.stalker

import com.ultratv.tv.nativeapp.data.db.ProviderDao
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adresse stockée → adresse jouable, juste AVANT d'ouvrir le flux. Les adresses ordinaires (Xtream, M3U) sont
 * renvoyées telles quelles ; une adresse `stalker://…` demande au portail un lien de lecture (`create_link`), qui
 * n'est valable que peu de temps et ne peut donc pas être calculé à la synchro.
 */
interface StreamUrlResolver {
    suspend fun resolve(url: String): String
}

@Singleton
class DefaultStreamUrlResolver @Inject constructor(
    private val providers: ProviderDao,
    private val stalker: StalkerClient,
) : StreamUrlResolver {
    override suspend fun resolve(url: String): String {
        val link = StalkerLink.parse(url) ?: return url
        val p = providers.byId(link.providerId) ?: return url
        if (p.kind != "STALKER") return url
        return stalker.createLink(p, link.type, link.cmd, link.series)
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
    suspend fun resolve(context: android.content.Context, url: String): String {
        if (!StalkerLink.isStalker(url)) return url
        return runCatching {
            EntryPointAccessors.fromApplication(context.applicationContext, StreamUrlEntryPoint::class.java).streamUrlResolver().resolve(url)
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrDefault(url)
    }
}
