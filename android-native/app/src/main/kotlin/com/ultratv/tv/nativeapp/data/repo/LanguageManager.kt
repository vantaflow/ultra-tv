package com.ultratv.tv.nativeapp.data.repo

import androidx.room.withTransaction
import com.ultratv.tv.nativeapp.data.db.CategoryDao
import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.MovieDao
import com.ultratv.tv.nativeapp.data.db.ProviderDao
import com.ultratv.tv.nativeapp.data.db.SeriesDao
import com.ultratv.tv.nativeapp.data.db.UltraDb
import com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore
import com.ultratv.tv.nativeapp.data.sync.SyncCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Changement des langues de contenu (Réglages › Langues, réglages reçus d'un autre appareil). Les langues masquées ne sont
 * pas chargées (voir [LangSelection]) ; quand la sélection change :
 *  - une langue MASQUÉE : ses catégories sont désactivées et ses titres purgés de la base (et de l'index plein texte,
 *    par les déclencheurs), SANS rien retélécharger ;
 *  - une langue RÉACTIVÉE : ses catégories sont réactivées et la source est resynchronisée en entier (les titres
 *    n'étaient plus en base). Les sources M3U importées d'un fichier ne sont jamais touchées : on ne peut pas les relire.
 */
@Singleton
class LanguageManager @Inject constructor(
    private val db: UltraDb,
    private val providers: ProviderDao,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val prefs: UserPreferencesStore,
    private val sync: SyncCoordinator,
) {
    /** [purged] : des catégories ou des titres ont été retirés ; [resync] : sources à resynchroniser en entier. */
    data class Outcome(val purged: Boolean = false, val resync: List<Long> = emptyList()) {
        val changed: Boolean get() = purged || resync.isNotEmpty()
    }

    private val mutex = Mutex()

    suspend fun current(): LangSelection = prefs.flow.first().let { LangSelection.of(it.languages, it.includeMulti, it.includeUnknownLang) }

    /**
     * Enregistre les nouveaux réglages (les paramètres nuls restent inchangés), purge ce qui devient masqué et lance la
     * resynchronisation de ce qui redevient visible. Un changement à la fois (la purge ne doit pas croiser la suivante).
     */
    suspend fun change(languages: Set<String>? = null, includeMulti: Boolean? = null, includeUnknown: Boolean? = null): Outcome = mutex.withLock {
        val old = current()
        languages?.let { prefs.setLanguages(it.map { c -> c.lowercase() }.sorted().joinToString(",")) }
        includeMulti?.let { prefs.setIncludeMulti(it) }
        includeUnknown?.let { prefs.setIncludeUnknownLang(it) }
        val out = apply(old, current())
        out.resync.forEach { sync.requestResync(it) }
        out
    }

    /** Applique à la base le passage de [old] à [new] (sans toucher aux réglages) ; la resynchro est laissée à l'appelant. */
    suspend fun apply(old: LangSelection, new: LangSelection): Outcome {
        val hide = LangSelection.hides(old, new)
        val show = LangSelection.reveals(old, new)
        if (!hide && !show) return Outcome()
        var purged = false
        val resync = ArrayList<Long>()
        for (p in providers.observeAll().first().filter { it.kind == "XTREAM" || it.kind == "STALKER" || it.kind == "M3U" }) {
            var hasCatalog = false
            db.withTransaction {
                for (kind in listOf("LIVE", "MOVIE", "SERIES")) {
                    val cats = categoryDao.forProviderKind(p.id, kind)
                    if (cats.isNotEmpty()) hasCatalog = true
                    val off = cats.filter { it.enabled && old.allows(it.lang) && !new.allows(it.lang) }.map { it.remoteId }
                    val on = cats.filter { !it.enabled && !old.allows(it.lang) && new.allows(it.lang) }.map { it.remoteId }
                    off.chunked(500).forEach { chunk ->
                        categoryDao.setEnabled(p.id, kind, chunk, false)
                        when (kind) { "LIVE" -> channelDao.deleteForCategories(p.id, chunk); "MOVIE" -> movieDao.deleteForCategories(p.id, chunk); else -> seriesDao.deleteForCategories(p.id, chunk) }
                    }
                    if (off.isNotEmpty()) purged = true
                    on.chunked(500).forEach { categoryDao.setEnabled(p.id, kind, it, true) }
                    if (!hide) continue
                    // Titres d'une langue masquée restés dans des catégories visibles (« FR | » dans une catégorie sans langue…),
                    // sauf ceux des catégories gardées à la main malgré leur langue masquée.
                    val offSet = off.toHashSet()
                    val keep = cats.filter { it.enabled && it.remoteId !in offSet && !new.allows(it.lang) }.map { it.remoteId }.ifEmpty { listOf("") }.take(900)
                    val present = when (kind) { "LIVE" -> channelDao.distinctLangs(p.id); "MOVIE" -> movieDao.distinctLangs(p.id); else -> seriesDao.distinctLangs(p.id) }
                    val gone = present.filter { !new.allows(it) }
                    if (gone.isEmpty()) continue
                    val n = when (kind) { "LIVE" -> channelDao.deleteByLangs(p.id, gone, keep); "MOVIE" -> movieDao.deleteByLangs(p.id, gone, keep); else -> seriesDao.deleteByLangs(p.id, gone, keep) }
                    if (n > 0) purged = true
                }
            }
            if (show && hasCatalog) resync += p.id
        }
        return Outcome(purged, resync)
    }
}
