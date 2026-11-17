package com.ultratv.tv.nativeapp.data.nas

import com.ultratv.tv.nativeapp.data.db.CategoryEntity
import com.ultratv.tv.nativeapp.data.db.EpisodeEntity
import com.ultratv.tv.nativeapp.data.db.MovieEntity
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.ProviderEntity
import com.ultratv.tv.nativeapp.data.db.SeriesEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catalogue d'un partage NAS (SMB) : parcours du partage, reconnaissance des films et épisodes par nom de fichier ([NasNames]),
 * adresse de lecture locale. Le résultat du dernier parcours est gardé en mémoire : les parties « films » et « séries » d'une même
 * synchro, puis l'ouverture d'une fiche série, ne relancent pas un parcours complet du partage à chaque fois.
 *
 * Affiches et fiches : le catalogue n'en met pas ; le repli TMDB de l'appli (PosterResolver pour les affiches, TmdbRepository pour
 * les fiches : titre nettoyé + année, cache 30 jours, requêtes espacées, jamais en rafale) s'applique comme à toute source sans affiche.
 */
@Singleton
class NasClient(private val smb: NasSmb, private val providerById: suspend (Long) -> ProviderEntity?) {
    @Inject constructor(smb: NasSmb, providers: ProviderDao) : this(smb, { providers.byId(it) })

    private class Cached(val key: String, val at: Long, val catalog: Nas.Catalog)

    private val cache = HashMap<Long, Cached>()
    private val mutex = Mutex()

    /** Relais local vers le partage : une seule instance, démarrée à la première lecture. */
    private val relay = NasRelay { providerId, path ->
        val p = runBlocking { providerById(providerId) }?.takeIf { Nas.isNasKind(it.kind) } ?: return@NasRelay null
        smb.openFile(p, path)
    }

    /** Vérifie une adresse et des identifiants (ajout) sans rien enregistrer ; lève une [java.io.IOException] en cas d'échec. */
    suspend fun test(a: Nas.Address, user: String, pass: String) = withContext(Dispatchers.IO) { smb.test(a, user, pass) }

    /**
     * Catalogue de la source [p]. Le dernier parcours est réutilisé s'il a moins de [maxAgeMs] ; sinon le partage est reparcouru
     * (borné : voir [Nas.Limits]).
     */
    suspend fun catalog(p: ProviderEntity, maxAgeMs: Long = 60_000L, now: Long = System.currentTimeMillis()): Nas.Catalog = mutex.withLock {
        val key = "${p.baseUrl}|${p.username}"
        cache[p.id]?.let { if (it.key == key && now - it.at <= maxAgeMs) return it.catalog }
        val addr = Nas.parseAddress(p.baseUrl) ?: throw java.io.IOException("nas: bad address")
        val files = withContext(Dispatchers.IO) {
            val ctx = currentCoroutineContext()
            Nas.scan(smb.lister(p), cancelled = { !ctx.isActive })
        }
        val cat = Nas.buildCatalog(p.id, addr.rootName, files)
        cache[p.id] = Cached(key, System.currentTimeMillis(), cat)
        cat
    }

    suspend fun categories(p: ProviderEntity, kind: String): List<CategoryEntity> =
        catalog(p).let { if (kind == "MOVIE") it.movieCategories else it.seriesCategories }

    suspend fun movies(p: ProviderEntity, categoryId: String): List<MovieEntity> = catalog(p).movies.filter { it.categoryId == categoryId }

    suspend fun series(p: ProviderEntity, categoryId: String): List<SeriesEntity> = catalog(p).series.filter { it.categoryId == categoryId }

    /** Épisodes d'une série ([seriesRemoteId]) ; le parcours en cache (30 min) est réutilisé à l'ouverture d'une fiche. */
    suspend fun episodes(p: ProviderEntity, seriesRemoteId: String, seriesRowId: Long): List<EpisodeEntity> =
        Nas.toEpisodes(p.id, seriesRowId, catalog(p, maxAgeMs = 30 * 60_000L).episodes[seriesRemoteId].orEmpty())

    /** Adresse de lecture (relais local sur 127.0.0.1) d'un lien `nas://` ; le fichier n'est ouvert qu'à la requête du lecteur. */
    fun openUrl(link: Nas.NasLink.Parsed): String = relay.urlFor(link.providerId, link.path)

    /** Libère les connexions SMB (fermeture de l'appli, source supprimée). */
    fun close() { relay.stop(); smb.closeAll() }
}
