package com.ultratv.tv.nativeapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.room.Update
import com.ultratv.tv.nativeapp.data.security.SecretBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Dao
interface ProviderRawDao {
    @Query("SELECT * FROM provider ORDER BY id ASC")
    fun observeAll(): Flow<List<ProviderEntity>>

    // Source marquée active, SINON la première : si la source active a été supprimée sans qu'une autre ne prenne le relais
    // (retrait d'une source, révocation), aucune n'était « active » et la recherche restait sur « Recherche… ».
    @Query("SELECT * FROM provider ORDER BY active DESC, id ASC LIMIT 1")
    suspend fun firstActive(): ProviderEntity?

    @Query("SELECT id FROM provider ORDER BY active DESC, id ASC LIMIT 1")
    suspend fun firstActiveId(): Long?

    @Query("SELECT * FROM provider WHERE kind = :kind AND baseUrl = :baseUrl AND username = :username LIMIT 1")
    suspend fun findByIdentity(kind: String, baseUrl: String, username: String): ProviderEntity?

    @Query("SELECT * FROM provider WHERE id = :id")
    suspend fun byId(id: Long): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: ProviderEntity): Long

    @Query("DELETE FROM provider WHERE id = :id")
    suspend fun delete(id: Long)

    /** Lecture BRUTE pour la migration de chiffrement (voir ProviderSecretsMigrator). */
    @Query("SELECT id, password AS raw FROM provider")
    suspend fun rawPasswords(): List<RawPassword>

    @Query("UPDATE provider SET password = :raw WHERE id = :id")
    suspend fun setRawPassword(id: Long, raw: String)

    @Query("UPDATE provider SET active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE provider SET active = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Query("UPDATE provider SET lastLiveSyncAt = :t WHERE id = :id")
    suspend fun setLiveSyncAt(id: Long, t: Long)

    /** Horodatage du catalogue films + séries : change à la FIN d'une synchro (pas à chaque ligne insérée). */
    @Query("SELECT lastVodSyncAt + lastSeriesSyncAt FROM provider WHERE id = :id")
    fun observeCatalogStamp(id: Long): kotlinx.coroutines.flow.Flow<Long?>

    @Query("UPDATE provider SET lastVodSyncAt = :t WHERE id = :id")
    suspend fun setVodSyncAt(id: Long, t: Long)

    @Query("UPDATE provider SET lastSeriesSyncAt = :t WHERE id = :id")
    suspend fun setSeriesSyncAt(id: Long, t: Long)

    @Query("UPDATE provider SET categoryFilter = :v WHERE id = :id")
    suspend fun setCategoryFilter(id: Long, v: Int)

    @Query("UPDATE provider SET lastEpgSyncAt = :t WHERE id = :id")
    suspend fun setEpgSyncAt(id: Long, t: Long)

    /** Remet les horodatages à zéro : la prochaine synchro recharge tout (« Actualiser »). */
    @Query("UPDATE provider SET lastLiveSyncAt = 0, lastVodSyncAt = 0, lastSeriesSyncAt = 0, lastEpgSyncAt = 0 WHERE id = :id")
    suspend fun resetSyncAt(id: Long)
}

/**
 * Façade de [ProviderRawDao] qui chiffre le mot de passe à l'écriture et le
 * déchiffre à la lecture (AES-GCM / Keystore, voir [SecretBox]). Même API que
 * l'ancien DAO : les appelants ne voient que des mots de passe en clair en mémoire,
 * jamais sur disque. (Un TypeConverter String→String est refusé par Room.)
 */
@Singleton
class ProviderDao @Inject constructor(private val raw: ProviderRawDao) {
    private fun ProviderEntity.dec() = copy(password = SecretBox.shared.decrypt(password))
    private fun ProviderEntity.enc() = copy(password = SecretBox.shared.encrypt(password))

    // Déchiffrement HORS du thread principal : map s'exécutait dans le contexte du collecteur (viewModelScope = Main) et
    // chaque mot de passe passe par le keystore (IPC) — au démarrage, plusieurs écrans observent les sources.
    fun observeAll(): Flow<List<ProviderEntity>> = raw.observeAll().map { l -> l.map { it.dec() } }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    // Déchiffrement (Keystore, appel système parfois lent) HORS du thread principal : appelé depuis un écran, il le figeait.
    suspend fun firstActive(): ProviderEntity? = raw.firstActive()?.let { e -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { e.dec() } }
    suspend fun firstActiveId(): Long? = raw.firstActiveId()
    suspend fun findByIdentity(kind: String, baseUrl: String, username: String): ProviderEntity? =
        raw.findByIdentity(kind, baseUrl, username)?.dec()
    suspend fun byId(id: Long): ProviderEntity? = raw.byId(id)?.dec()
    suspend fun upsert(p: ProviderEntity): Long = raw.upsert(p.enc())
    suspend fun delete(id: Long) = raw.delete(id)
    suspend fun deactivateAll() = raw.deactivateAll()
    suspend fun activate(id: Long) = raw.activate(id)
    fun observeCatalogStamp(id: Long): Flow<Long?> = raw.observeCatalogStamp(id)
    suspend fun markSynced(id: Long, part: SyncPart, t: Long) = when (part) {
        SyncPart.LIVE -> raw.setLiveSyncAt(id, t)
        SyncPart.VOD -> raw.setVodSyncAt(id, t)
        SyncPart.SERIES -> raw.setSeriesSyncAt(id, t)
        SyncPart.EPG -> raw.setEpgSyncAt(id, t)
    }
    suspend fun resetSyncAt(id: Long) = raw.resetSyncAt(id)
    suspend fun setCategoryFilter(id: Long, v: Int) = raw.setCategoryFilter(id, v)
    suspend fun rawPasswords(): List<RawPassword> = raw.rawPasswords()
    suspend fun setRawPassword(id: Long, value: String) = raw.setRawPassword(id, value)
}

@Dao
interface ChannelDao {
    /** Les [limit] premières chaînes seulement (rails de l'accueil) : charger les
     *  40 000 lignes pour n'en garder que 30 saturait le CPU à chaque lot inséré. */
    @Query("""
        SELECT * FROM channel WHERE providerId = :pid AND ${Hid.T}
        ORDER BY num, id
        LIMIT :limit
    """)
    fun observeTop(pid: Long, limit: Int): Flow<List<ChannelEntity>>

    @Query("""
        SELECT * FROM channel WHERE providerId = :pid AND categoryId = :cat AND ${Hid.T}
        ORDER BY num, id
    """)
    fun observeForCategory(pid: Long, cat: String): Flow<List<ChannelEntity>>

    /** Seulement (id, epgChannelId) : évite de charger 40 000 entités complètes pour l'EPG. */
    // Chaînes des catégories désactivées exclues : leurs programmes ne sont jamais gardés.
    @Query("""SELECT id, epgChannelId FROM channel WHERE providerId = :pid AND epgChannelId IS NOT NULL AND epgChannelId != ''
        AND (categoryId IS NULL OR categoryId NOT IN (SELECT remoteId FROM category WHERE providerId = :pid AND kind = 'LIVE' AND enabled = 0))""")
    suspend fun epgMapping(pid: Long): List<EpgMapping>

    /** (remoteId → id) : la resynchronisation réécrit les chaînes en gardant leur identifiant (programmes, liens). */
    @Query("SELECT id, remoteId FROM channel WHERE providerId = :pid")
    suspend fun idsByRemote(pid: Long): List<ChannelIdRow>

    /** Plus grands numéros par catégorie, chaînes manuelles (« custom:… ») exclues : les chaînes ajoutées passent APRÈS celles du fournisseur. */
    @Query("SELECT categoryId AS categoryId, MAX(num) AS maxNum, MAX(seq) AS maxSeq FROM channel WHERE providerId = :pid AND remoteId NOT LIKE 'custom:%' GROUP BY categoryId")
    suspend fun maxNumbersByCategory(pid: Long): List<CategoryMaxRow>

    /** (id, titre, identifiant EPG) de toutes les chaînes : rattache « TF1 +1 » au programme de « TF1 ». */
    @Query("SELECT id, title, epgChannelId, country FROM channel WHERE providerId = :pid AND isSeparator = 0")
    suspend fun epgTitles(pid: Long): List<EpgTitle>

    // LIMIT : un titre vide ou très courant (« FR », « HD ») ramenait des milliers d'identifiants (OOM sur les TV à 128 Mo).
    @Query("SELECT id FROM channel WHERE providerId = :pid AND title = :title AND isSeparator = 0 LIMIT 8")
    suspend fun idsByTitle(pid: Long, title: String): List<Long>

    /** Atomic position update. Swap two channels by calling twice in a transaction. */
    @Query("UPDATE channel SET userPosition = :pos WHERE id = :id")
    suspend fun setPosition(id: Long, pos: Int)

    @Query("UPDATE channel SET userPosition = 0 WHERE providerId = :pid")
    suspend fun resetPositions(pid: Long)

    @Query("SELECT * FROM channel WHERE id = :id")
    suspend fun byId(id: Long): ChannelEntity?

    /** Lookup by (providerId, remoteId) — used by PlaybackContext to rehydrate
     *  the channel after a navigation that only carried the persisted identifiers. */
    @Query("SELECT * FROM channel WHERE providerId = :pid AND remoteId = :rid LIMIT 1")
    suspend fun byRemoteId(pid: Long, rid: String): ChannelEntity?

    /** Rubrique Sport : chaînes de ces identifiants (non séparateurs, non masquées). À appeler par paquets de 400 au plus. */
    @Query("SELECT * FROM channel WHERE id IN (:ids) AND junk = 0 AND isSeparator = 0 AND ${Hid.T}")
    suspend fun byIds(ids: List<Long>): List<ChannelEntity>

    /** Rubrique Sport : chaînes des catégories (identifiants fournisseur), index (providerId, categoryId, num). Paquets de 400 au plus. */
    @Query("SELECT * FROM channel WHERE providerId = :pid AND categoryId IN (:cats) AND junk = 0 AND isSeparator = 0 AND ${Hid.T}")
    suspend fun byCategories(pid: Long, cats: List<String>): List<ChannelEntity>

    /** Rubrique Sport : pré-filtre des noms de chaînes (LIKE construit par [com.ultratv.tv.nativeapp.data.sport.SportRepository]). */
    @RawQuery
    suspend fun channelsRaw(query: SupportSQLiteQuery): List<ChannelEntity>

    /** Propriétés de flux protégé (JSON, secret de lecture) d'une chaîne ; null = flux ordinaire. */
    @Query("SELECT props FROM channel WHERE providerId = :pid AND remoteId = :rid LIMIT 1")
    suspend fun propsOf(pid: Long, rid: String): String?


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channel WHERE providerId = :pid")
    suspend fun deleteForProvider(pid: Long)

    @Query("DELETE FROM channel WHERE providerId = :pid AND categoryId IN (:ids)")
    suspend fun deleteForCategories(pid: Long, ids: List<String>)

    /** Numéros séquentiels d'une catégorie, dans leur ordre (resserrés après un masquage, voir ChannelSeq.compact). */
    @Query("SELECT id, seq FROM channel WHERE providerId = :pid AND categoryId IS :cat AND isSeparator = 0 AND junk = 0 AND seq > 0 ORDER BY seq, id")
    suspend fun seqsInCategory(pid: Long, cat: String?): List<SeqRow>

    @Query("UPDATE channel SET seq = :seq WHERE id = :id")
    suspend fun setSeq(id: Long, seq: Int)

    /** Chaînes masquées par l'utilisateur : retirées de la base (voir HiddenChannelsStore). */
    @Query("DELETE FROM channel WHERE providerId = :pid AND remoteId IN (:ids)")
    suspend fun deleteByRemoteIds(pid: Long, ids: List<String>)

    /** Toutes les chaînes d'une catégorie (écran « Chaînes de la catégorie »), dans l'ordre du fournisseur. */
    @Query("SELECT * FROM channel WHERE providerId = :pid AND categoryId = :cat ORDER BY num, id")
    suspend fun listInCategory(pid: Long, cat: String): List<ChannelEntity>

    @Query("DELETE FROM channel WHERE providerId = :pid AND categoryId = :cat")
    suspend fun deleteForCategory(pid: Long, cat: String)

    @Query("SELECT COUNT(*) FROM channel WHERE providerId = :pid")
    suspend fun count(pid: Long): Int

    /** Chaînes venues du fournisseur (sans les chaînes ajoutées à la main) : référence de la garde anti-effacement. */
    @Query("SELECT COUNT(*) FROM channel WHERE providerId = :pid AND remoteId NOT LIKE 'custom:%'")
    suspend fun countProvided(pid: Long): Int

    /** Chaînes d'une catégorie (une catégorie absente de la liste du serveur n'est retirée que si plus rien n'y renvoie). */
    @Query("SELECT COUNT(*) FROM channel WHERE providerId = :pid AND categoryId = :cat")
    suspend fun countInCategory(pid: Long, cat: String): Int

    /** Clés `source:identifiant` des chaînes rangées dans une catégorie du Direct verrouillée par code (voir ParentalLocks). */
    @Query("SELECT ch.providerId || ':' || ch.remoteId FROM channel ch JOIN category c ON c.providerId = ch.providerId AND c.kind = 'LIVE' AND c.remoteId = ch.categoryId WHERE c.locked = 1")
    fun observeCategoryLockedKeys(): kotlinx.coroutines.flow.Flow<List<String>>

    /** Catégories de chaînes par identifiant distant (rangée « Chaînes récentes » de l'accueil, voir HomeGuard). */
    @Query("SELECT id AS id, remoteId AS remoteId, categoryId AS categoryId FROM channel WHERE providerId = :pid AND remoteId IN (:rids)")
    suspend fun categoriesByRemote(pid: Long, rids: List<String>): List<TitleCategory>

    @Query("SELECT COUNT(*) FROM channel WHERE providerId = :pid")
    fun observeCount(pid: Long): Flow<Int>

    /** Chaînes affichées du Direct (sans décorations ni séparateurs) : total de « Toutes les chaînes » du lecteur. */
    @Query("SELECT COUNT(*) FROM channel WHERE providerId = :pid AND junk = 0 AND isSeparator = 0")
    suspend fun countLiveAll(pid: Long): Int

    /** Chaînes affichées masquées par le profil courant : retranchées des compteurs (qui restent servis par l'index couvrant). */
    @Query("SELECT COUNT(*) FROM channel_hidden_now h JOIN channel c ON c.providerId = h.providerId AND c.remoteId = h.remoteId WHERE h.providerId = :pid AND c.junk = 0 AND c.isSeparator = 0")
    suspend fun countLiveHidden(pid: Long): Int


    @Query("""
        SELECT COUNT(*) FROM channel c
        JOIN favorite f ON f.profileId = :profileId AND f.providerId = c.providerId AND f.kind = 'LIVE' AND f.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.isSeparator = 0 AND ${Hid.C}
    """)
    suspend fun countFavorites(pid: Long, profileId: Long): Int

    /** Rail d'accueil sans favoris : des chaînes avec logo, par ordre alphabétique (hors décorations). */
    /** Mode simple : les premières chaînes de la liste « Tout » (sans décorations ni séparateurs), dans son ordre. */
    @Query("SELECT * FROM channel WHERE providerId = :pid AND junk = 0 AND isSeparator = 0 AND ${Hid.T} ORDER BY catRank, num, id LIMIT :limit")
    fun observeFirstClean(pid: Long, limit: Int): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channel WHERE providerId = :pid AND junk = 0 AND isSeparator = 0 AND logo IS NOT NULL AND logo != '' AND sortKey >= 'a' AND ${Hid.T} ORDER BY num, id LIMIT :limit")
    fun observeTopWithLogo(pid: Long, limit: Int): Flow<List<ChannelEntity>>

    @Query("""
        SELECT c.* FROM channel c
        JOIN favorite f ON f.profileId = :profileId AND f.providerId = c.providerId AND f.kind = 'LIVE' AND f.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.isSeparator = 0 AND ${Hid.C} ORDER BY c.num, c.id LIMIT :limit
    """)
    fun observeFavoritesTop(pid: Long, limit: Int, profileId: Long): Flow<List<ChannelEntity>>

    /** Favoris du profil de PLUSIEURS sources (écran Favoris « Toutes les sources ») : même requête, bornée par [limit] au total. */
    @Query("""
        SELECT c.* FROM channel c
        JOIN favorite f ON f.profileId = :profileId AND f.providerId = c.providerId AND f.kind = 'LIVE' AND f.remoteId = c.remoteId
        WHERE c.providerId IN (:pids) AND c.isSeparator = 0 AND ${Hid.C} ORDER BY c.providerId, c.num, c.id LIMIT :limit
    """)
    fun observeFavoritesTopIn(pids: List<Long>, limit: Int, profileId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT lang AS lang, COUNT(*) AS n FROM channel WHERE providerId = :pid AND junk = 0 AND isSeparator = 0 GROUP BY lang")
    fun observeLangCounts(pid: Long): Flow<List<LangCount>>

    // « Tout » : SANS les séparateurs de sections — mélangés à toutes les catégories ils n'ont pas de sens, et sans
    // vrai numéro de playlist ils remontaient tous en tête (38 en-têtes vides avant la première chaîne).
    // Ordre : celui des catégories (Paramètres › Catégories, même tri que la liste de gauche), puis la playlist dans
    // chaque catégorie. Avant : numéro de chaîne seul, sans rapport avec l'ordre choisi.
    // Ordre servi par l'index grâce à catRank : voir allAfter / allBefore (pagination par clé, LiveAllPagingSource).

    @Query("SELECT * FROM channel WHERE providerId = :pid AND categoryId = :cat AND junk = 0 AND ${Hid.T} AND (:useLang = 0 OR lang IN (:langs)) ORDER BY num, id")
    fun pagedForCategory(pid: Long, cat: String, useLang: Int, langs: List<String>): androidx.paging.PagingSource<Int, ChannelEntity>


    // ── Repli tant que catRank n'est pas rempli (base migrée depuis < 18, remplissage en tâche de fond : LiveOrderBackfill) ──
    // Même ordre que catRank, calculé par jointure : lent (tri complet) mais juste. À retirer quand plus aucune base
    // antérieure à la version 18 n'est en circulation.
    @Query("SELECT ch.* FROM channel ch LEFT JOIN category k ON k.providerId = ch.providerId AND k.kind = 'LIVE' AND k.remoteId = ch.categoryId WHERE ch.providerId = :pid AND ch.junk = 0 AND ch.isSeparator = 0 AND ${Hid.CH} AND (:useLang = 0 OR ch.lang IN (:langs)) ORDER BY CASE WHEN COALESCE(k.position, 0) = 0 THEN 1 ELSE 0 END, k.position, k.id, ch.num, ch.id")
    fun pagedAllLegacy(pid: Long, useLang: Int, langs: List<String>): androidx.paging.PagingSource<Int, ChannelEntity>

    @Query("SELECT ch.* FROM channel ch LEFT JOIN category k ON k.providerId = ch.providerId AND k.kind = 'LIVE' AND k.remoteId = ch.categoryId WHERE ch.providerId = :pid AND ch.junk = 0 AND ch.isSeparator = 0 AND ${Hid.CH} AND (ch.categoryId IS NULL OR ch.categoryId NOT IN (:hidden)) AND (:useLang = 0 OR ch.lang IN (:langs)) ORDER BY CASE WHEN COALESCE(k.position, 0) = 0 THEN 1 ELSE 0 END, k.position, k.id, ch.num, ch.id")
    fun pagedAllExcludingLegacy(pid: Long, hidden: List<String>, useLang: Int, langs: List<String>): androidx.paging.PagingSource<Int, ChannelEntity>

    @Query("SELECT ch.* FROM channel ch LEFT JOIN category k ON k.providerId = ch.providerId AND k.kind = 'LIVE' AND k.remoteId = ch.categoryId WHERE ch.providerId = :pid AND ch.junk = 0 AND ch.isSeparator = 0 AND ${Hid.CH} ORDER BY CASE WHEN COALESCE(k.position, 0) = 0 THEN 1 ELSE 0 END, k.position, k.id, ch.num, ch.id LIMIT :limit OFFSET :offset")
    suspend fun windowAllLegacy(pid: Long, limit: Int, offset: Int): List<ChannelEntity>

    @Query("SELECT ch.id FROM channel ch LEFT JOIN category k ON k.providerId = ch.providerId AND k.kind = 'LIVE' AND k.remoteId = ch.categoryId WHERE ch.providerId = :pid AND ch.junk = 0 AND ch.isSeparator = 0 AND ${Hid.CH} ORDER BY CASE WHEN COALESCE(k.position, 0) = 0 THEN 1 ELSE 0 END, k.position, k.id, ch.num, ch.id")
    suspend fun orderedAllIdsLegacy(pid: Long): List<Long>

    /**
     * Rang (0 = première) de la chaîne [cid] dans l'ordre de [orderedAllIdsLegacy], calculé par COMPTAGE des lignes qui la
     * précèdent au lieu de charger les ~55 000 identifiants pour un indexOf. Mêmes critères et même ordre
     * (les NULL de la jointure passent avant tout, d'où COALESCE(…, -1)).
     */
    @Query("""
        SELECT COUNT(*) FROM channel ch
        LEFT JOIN category k ON k.providerId = ch.providerId AND k.kind = 'LIVE' AND k.remoteId = ch.categoryId,
        (SELECT CASE WHEN COALESCE(tk.position, 0) = 0 THEN 1 ELSE 0 END AS o, COALESCE(tk.position, -1) AS p,
                COALESCE(tk.id, -1) AS kid, t.num AS n, t.id AS i
           FROM channel t
           LEFT JOIN category tk ON tk.providerId = t.providerId AND tk.kind = 'LIVE' AND tk.remoteId = t.categoryId
          WHERE t.id = :cid LIMIT 1) tg
        WHERE ch.providerId = :pid AND ch.junk = 0 AND ch.isSeparator = 0 AND ${Hid.CH}
          AND (CASE WHEN COALESCE(k.position, 0) = 0 THEN 1 ELSE 0 END, COALESCE(k.position, -1), COALESCE(k.id, -1), ch.num, ch.id)
              < (tg.o, tg.p, tg.kid, tg.n, tg.i)
    """)
    suspend fun rankAllLegacy(pid: Long, cid: Long): Int

    /** Sources dont des chaînes affichées n'ont pas encore de rang de catégorie (0) : à remplir en tâche de fond (recherche d'index, pas de parcours). */
    @Query("SELECT p.id FROM provider p WHERE EXISTS(SELECT 1 FROM channel WHERE providerId = p.id AND junk = 0 AND isSeparator = 0 AND catRank = 0)")
    suspend fun unrankedProviders(): List<Long>

    /** Remplissage PAR LOT (au plus [limit] lignes, chacune sa transaction) : renvoie le nombre de lignes réécrites. */
    @Query("UPDATE channel SET catRank = :rank WHERE id IN (SELECT id FROM channel WHERE providerId = :pid AND categoryId = :cat AND catRank != :rank LIMIT :limit)")
    suspend fun setCatRankBatch(pid: Long, cat: String, rank: Int, limit: Int): Int

    @Query("UPDATE channel SET catRank = :rank WHERE id IN (SELECT id FROM channel WHERE providerId = :pid AND catRank != :rank AND (categoryId IS NULL OR categoryId NOT IN (SELECT remoteId FROM category WHERE providerId = :pid AND kind = 'LIVE')) LIMIT :limit)")
    suspend fun setCatRankOrphansBatch(pid: Long, rank: Int, limit: Int): Int


    /** Idem, groupe « Tout » du Direct : mêmes catégories masquées exclues, même ordre (rang de catégorie, numéro). */
    @Query("""
        SELECT * FROM channel c WHERE c.providerId = :pid AND c.junk = 0 AND c.isSeparator = 0 AND ${Hid.C}
        AND (:useHidden = 0 OR c.categoryId IS NULL OR c.categoryId NOT IN (:hidden))
        AND EXISTS (SELECT 1 FROM epg e WHERE e.channelId = c.id AND e.endMs >= :from AND e.startMs <= :to)
        ORDER BY c.catRank, c.num, c.id
    """)
    fun pagedWithEpgAll(pid: Long, from: Long, to: Long, useHidden: Int, hidden: List<String>): androidx.paging.PagingSource<Int, ChannelEntity>

    /** Idem, une catégorie du fournisseur. */
    @Query("""
        SELECT * FROM channel c WHERE c.providerId = :pid AND c.categoryId = :cat AND c.junk = 0 AND c.isSeparator = 0 AND ${Hid.C}
        AND EXISTS (SELECT 1 FROM epg e WHERE e.channelId = c.id AND e.endMs >= :from AND e.startMs <= :to)
        ORDER BY c.num, c.id
    """)
    fun pagedWithEpgForCategory(pid: Long, cat: String, from: Long, to: Long): androidx.paging.PagingSource<Int, ChannelEntity>

    /** Idem, favoris du profil. */
    @Query("""
        SELECT c.* FROM channel c
        JOIN favorite f ON f.profileId = :profileId AND f.providerId = c.providerId AND f.kind = 'LIVE' AND f.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.isSeparator = 0 AND ${Hid.C}
        AND EXISTS (SELECT 1 FROM epg e WHERE e.channelId = c.id AND e.endMs >= :from AND e.startMs <= :to)
        ORDER BY c.num, c.id
    """)
    fun pagedWithEpgFavorites(pid: Long, from: Long, to: Long, profileId: Long): androidx.paging.PagingSource<Int, ChannelEntity>

    // ── « Tout » par CLÉ (catRank, num, id) : liste paginée du Direct et zapping ──
    // Pagination par clé au lieu de LIMIT/OFFSET : chaque page démarre par une recherche dans l'index
    // (providerId, junk, isSeparator, catRank, num[, rowid]) au lieu de parcourir les N lignes qui précèdent, et une ligne
    // insérée par une synchro entre deux pages ne peut jamais apparaître deux fois (clés en double = plantage de la liste).
    // Filtres facultatifs : langue de la vue (useLang) et catégories masquées du profil (useHidden).

    /** Page suivante : les [limit] chaînes strictement APRÈS (catRank, num, id). */
    @Query("SELECT * FROM channel WHERE providerId = :pid AND junk = 0 AND isSeparator = 0 AND ${Hid.T} AND (catRank, num, id) > (:catRank, :num, :id) AND (:useHidden = 0 OR categoryId IS NULL OR categoryId NOT IN (:hidden)) AND (:useLang = 0 OR lang IN (:langs)) ORDER BY catRank, num, id LIMIT :limit")
    suspend fun allAfter(pid: Long, catRank: Int, num: Int, id: Long, limit: Int, useHidden: Int, hidden: List<String>, useLang: Int, langs: List<String>): List<ChannelEntity>

    /** Page précédente : les [limit] chaînes strictement AVANT (catRank, num, id), de la plus proche à la plus lointaine. */
    @Query("SELECT * FROM channel WHERE providerId = :pid AND junk = 0 AND isSeparator = 0 AND ${Hid.T} AND (catRank, num, id) < (:catRank, :num, :id) AND (:useHidden = 0 OR categoryId IS NULL OR categoryId NOT IN (:hidden)) AND (:useLang = 0 OR lang IN (:langs)) ORDER BY catRank DESC, num DESC, id DESC LIMIT :limit")
    suspend fun allBefore(pid: Long, catRank: Int, num: Int, id: Long, limit: Int, useHidden: Int, hidden: List<String>, useLang: Int, langs: List<String>): List<ChannelEntity>

    /** Vrai tant que des chaînes affichées de la source n'ont pas de rang (base migrée, remplissage en cours) : repli sur l'ordre joint. */
    @Query("SELECT EXISTS(SELECT 1 FROM channel WHERE providerId = :pid AND junk = 0 AND isSeparator = 0 AND catRank = 0)")
    fun observeHasUnranked(pid: Long): Flow<Boolean>

    /** Rang de catégorie des chaînes d'une catégorie (voir LiveOrder) ; seules les lignes qui changent sont réécrites (déclencheurs FTS). */
    @Query("UPDATE channel SET catRank = :rank WHERE providerId = :pid AND categoryId = :cat AND catRank != :rank")
    suspend fun setCatRank(pid: Long, cat: String, rank: Int)

    /** Rang des chaînes SANS catégorie du Direct (categoryId nul ou inconnu de la table category). */
    @Query("UPDATE channel SET catRank = :rank WHERE providerId = :pid AND catRank != :rank AND (categoryId IS NULL OR categoryId NOT IN (SELECT remoteId FROM category WHERE providerId = :pid AND kind = 'LIVE'))")
    suspend fun setCatRankOrphans(pid: Long, rank: Int)

    @Query("SELECT * FROM channel WHERE providerId = :pid AND categoryId = :cat AND junk = 0 AND isSeparator = 0 AND ${Hid.T} ORDER BY num, id LIMIT :limit OFFSET :offset")
    suspend fun windowCategory(pid: Long, cat: String, limit: Int, offset: Int): List<ChannelEntity>


    @Query("SELECT COUNT(*) FROM channel WHERE providerId = :pid AND categoryId = :cat AND junk = 0 AND isSeparator = 0 AND ${Hid.T} AND (num < :num OR (num = :num AND id < :id))")
    suspend fun rankCategory(pid: Long, cat: String, num: Int, id: Long): Int

    @Query("""
        SELECT c.* FROM channel c
        JOIN favorite f ON f.profileId = :profileId AND f.providerId = c.providerId AND f.kind = 'LIVE' AND f.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.isSeparator = 0 AND ${Hid.C} ORDER BY c.num, c.id
    """)
    suspend fun favoritesList(pid: Long, profileId: Long): List<ChannelEntity>

    // ── Ordre PERSONNEL du profil (table channel_order, voir ChannelOrderEntity) ──
    // Variantes des requêtes ci-dessus, utilisées SEULEMENT pour les listes qui ont un ordre personnel (sinon les requêtes
    // d'origine, servies par l'index, restent en service). Tri : place choisie (les chaînes sans place à la suite), puis
    // l'ordre naturel (numéro, identifiant). 2147483647 = « sans place » : doit rester égal à ChannelOrder.NO_POSITION.

    /** Une catégorie du fournisseur dans l'ordre du profil (liste paginée du Direct). */
    @Query("""
        SELECT c.* FROM channel c
        LEFT JOIN channel_order o ON o.profileId = :profileId AND o.providerId = c.providerId AND o.scope = :cat AND o.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.categoryId = :cat AND c.junk = 0 AND ${Hid.C} AND (:useLang = 0 OR c.lang IN (:langs))
        ORDER BY COALESCE(o.position, 2147483647), c.num, c.id
    """)
    fun pagedForCategoryOrdered(pid: Long, cat: String, useLang: Int, langs: List<String>, profileId: Long): androidx.paging.PagingSource<Int, ChannelEntity>

    /** Favoris dans l'ordre du profil (liste paginée du Direct). */
    @Query("""
        SELECT c.* FROM channel c
        JOIN favorite f ON f.profileId = :profileId AND f.providerId = c.providerId AND f.kind = 'LIVE' AND f.remoteId = c.remoteId
        LEFT JOIN channel_order o ON o.profileId = :profileId AND o.providerId = c.providerId AND o.scope = '__fav__' AND o.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.isSeparator = 0 AND ${Hid.C} AND (:useLang = 0 OR c.lang IN (:langs))
        ORDER BY COALESCE(o.position, 2147483647), c.num, c.id
    """)
    fun pagedFavoritesOrdered(pid: Long, useLang: Int, langs: List<String>, profileId: Long): androidx.paging.PagingSource<Int, ChannelEntity>

    /** Même fenêtre que [windowCategory], dans l'ordre du profil. */
    @Query("""
        SELECT c.* FROM channel c
        LEFT JOIN channel_order o ON o.profileId = :profileId AND o.providerId = c.providerId AND o.scope = :cat AND o.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.categoryId = :cat AND c.junk = 0 AND c.isSeparator = 0 AND ${Hid.C}
        ORDER BY COALESCE(o.position, 2147483647), c.num, c.id LIMIT :limit OFFSET :offset
    """)
    suspend fun windowCategoryOrdered(pid: Long, cat: String, profileId: Long, limit: Int, offset: Int): List<ChannelEntity>

    /** Même rang que [rankCategory], dans l'ordre du profil ([pos] = place de la chaîne, 2147483647 si elle n'en a pas). */
    @Query("""
        SELECT COUNT(*) FROM channel c
        LEFT JOIN channel_order o ON o.profileId = :profileId AND o.providerId = c.providerId AND o.scope = :cat AND o.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.categoryId = :cat AND c.junk = 0 AND c.isSeparator = 0 AND ${Hid.C}
        AND (COALESCE(o.position, 2147483647) < :pos
            OR (COALESCE(o.position, 2147483647) = :pos AND (c.num < :num OR (c.num = :num AND c.id < :id))))
    """)
    suspend fun rankCategoryOrdered(pid: Long, cat: String, profileId: Long, pos: Int, num: Int, id: Long): Int

    /** Même liste que [favoritesList], dans l'ordre du profil. */
    @Query("""
        SELECT c.* FROM channel c
        JOIN favorite f ON f.profileId = :profileId AND f.providerId = c.providerId AND f.kind = 'LIVE' AND f.remoteId = c.remoteId
        LEFT JOIN channel_order o ON o.profileId = :profileId AND o.providerId = c.providerId AND o.scope = '__fav__' AND o.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.isSeparator = 0 AND ${Hid.C}
        ORDER BY COALESCE(o.position, 2147483647), c.num, c.id
    """)
    suspend fun favoritesListOrdered(pid: Long, profileId: Long): List<ChannelEntity>

    /** Identifiants fournisseur d'une catégorie, dans l'ordre affiché (séparateurs compris, décorations exclues) : base du déplacement. */
    @Query("""
        SELECT c.remoteId FROM channel c
        LEFT JOIN channel_order o ON o.profileId = :profileId AND o.providerId = c.providerId AND o.scope = :cat AND o.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.categoryId = :cat AND c.junk = 0 AND ${Hid.C}
        ORDER BY COALESCE(o.position, 2147483647), c.num, c.id
    """)
    suspend fun orderedKeysForCategory(pid: Long, cat: String, profileId: Long): List<String>

    /** Identifiants fournisseur des favoris, dans l'ordre affiché : base du déplacement. */
    @Query("""
        SELECT c.remoteId FROM channel c
        JOIN favorite f ON f.profileId = :profileId AND f.providerId = c.providerId AND f.kind = 'LIVE' AND f.remoteId = c.remoteId
        LEFT JOIN channel_order o ON o.profileId = :profileId AND o.providerId = c.providerId AND o.scope = '__fav__' AND o.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.isSeparator = 0 AND ${Hid.C}
        ORDER BY COALESCE(o.position, 2147483647), c.num, c.id
    """)
    suspend fun orderedKeysForFavorites(pid: Long, profileId: Long): List<String>

    /** Autres qualités d'une même chaîne (même titre nettoyé et même langue), de la meilleure à la moins bonne. */
    @Query("SELECT * FROM channel WHERE providerId = :pid AND title = :title AND lang = :lang AND junk = 0 AND isSeparator = 0 AND ${Hid.T} ORDER BY quality DESC, num LIMIT 12")
    suspend fun variantsOf(pid: Long, title: String, lang: String): List<ChannelEntity>

    /** Chaînes précises d'une source (écran « Chaînes verrouillées » : seulement celles qu'on a verrouillées). */
    @Query("SELECT * FROM channel WHERE providerId = :pid AND remoteId IN (:ids) ORDER BY sortKey")
    suspend fun byRemoteIds(pid: Long, ids: List<String>): List<ChannelEntity>

    @Query("""
        SELECT c.* FROM channel c
        JOIN favorite f ON f.profileId = :profileId AND f.providerId = c.providerId AND f.kind = 'LIVE' AND f.remoteId = c.remoteId
        WHERE c.providerId = :pid AND c.isSeparator = 0 AND ${Hid.C} AND (:useLang = 0 OR c.lang IN (:langs)) ORDER BY c.num, c.id
    """)
    fun pagedFavorites(pid: Long, useLang: Int, langs: List<String>, profileId: Long): androidx.paging.PagingSource<Int, ChannelEntity>

    // ── Tri choisi par le profil (ChannelSort ≠ source) : variantes triées, sans séparateurs (un en-tête de section n'a plus de sens trié) ──

    @Query("SELECT c.* FROM channel c ${ChSort.JOIN} WHERE c.providerId = :pid AND c.categoryId = :cat AND c.junk = 0 AND c.isSeparator = 0 AND ${Hid.C} AND (:useLang = 0 OR c.lang IN (:langs)) ORDER BY ${ChSort.ORDER}")
    fun pagedForCategorySorted(pid: Long, cat: String, useLang: Int, langs: List<String>, profileId: Long, sort: Int, srcNum: Int): androidx.paging.PagingSource<Int, ChannelEntity>

    @Query("""
        SELECT c.* FROM channel c
        JOIN favorite f ON f.profileId = :profileId AND f.providerId = c.providerId AND f.kind = 'LIVE' AND f.remoteId = c.remoteId
        ${ChSort.JOIN}
        WHERE c.providerId = :pid AND c.isSeparator = 0 AND ${Hid.C} AND (:useLang = 0 OR c.lang IN (:langs)) ORDER BY ${ChSort.ORDER}
    """)
    fun pagedFavoritesSorted(pid: Long, useLang: Int, langs: List<String>, profileId: Long, sort: Int, srcNum: Int): androidx.paging.PagingSource<Int, ChannelEntity>

    /** « Tout » trié : catégories dans leur ordre (rang), tri choisi DANS chaque catégorie. */
    @Query("SELECT c.* FROM channel c ${ChSort.JOIN} WHERE c.providerId = :pid AND c.junk = 0 AND c.isSeparator = 0 AND ${Hid.C} AND (:useHidden = 0 OR c.categoryId IS NULL OR c.categoryId NOT IN (:hidden)) AND (:useLang = 0 OR c.lang IN (:langs)) ORDER BY c.catRank, ${ChSort.ORDER}")
    fun pagedAllSorted(pid: Long, useHidden: Int, hidden: List<String>, useLang: Int, langs: List<String>, profileId: Long, sort: Int, srcNum: Int): androidx.paging.PagingSource<Int, ChannelEntity>

    /** Une catégorie triée (file de zapping, tiroir du lecteur, Guide). */
    @Query("SELECT c.* FROM channel c ${ChSort.JOIN} WHERE c.providerId = :pid AND c.categoryId = :cat AND c.junk = 0 AND c.isSeparator = 0 AND ${Hid.C} ORDER BY ${ChSort.ORDER}")
    suspend fun categorySorted(pid: Long, cat: String, profileId: Long, sort: Int, srcNum: Int): List<ChannelEntity>

    /** Favoris triés (file de zapping, tiroir du lecteur, Guide). */
    @Query("""
        SELECT c.* FROM channel c
        JOIN favorite f ON f.profileId = :profileId AND f.providerId = c.providerId AND f.kind = 'LIVE' AND f.remoteId = c.remoteId
        ${ChSort.JOIN}
        WHERE c.providerId = :pid AND c.isSeparator = 0 AND ${Hid.C} ORDER BY ${ChSort.ORDER}
    """)
    suspend fun favoritesSorted(pid: Long, profileId: Long, sort: Int, srcNum: Int): List<ChannelEntity>

    /** Identifiants de « Tout » triés (même ordre que [pagedAllSorted], sans filtre) : la fenêtre de zapping se prend autour de la chaîne courante. */
    @Query("SELECT c.id FROM channel c ${ChSort.JOIN} WHERE c.providerId = :pid AND c.junk = 0 AND c.isSeparator = 0 AND ${Hid.C} ORDER BY c.catRank, ${ChSort.ORDER}")
    suspend fun allSortedIds(pid: Long, profileId: Long, sort: Int, srcNum: Int): List<Long>

    /** Chaîne portant ce numéro de la SOURCE (mode « numéros de la source »), la première dans l'ordre des catégories. */
    @Query("SELECT * FROM channel WHERE providerId = :pid AND num = :num AND junk = 0 AND isSeparator = 0 AND ${Hid.T} ORDER BY catRank, id LIMIT 1")
    suspend fun bySourceNumber(pid: Long, num: Int): ChannelEntity?

    /** Chaîne de seq global [seq] dans la catégorie (saisie d'un numéro en numérotation continue). */
    @Query("SELECT * FROM channel WHERE providerId = :pid AND categoryId IS :cat AND seq = :seq AND junk = 0 AND isSeparator = 0 AND ${Hid.T} LIMIT 1")
    suspend fun byCategorySeq(pid: Long, cat: String?, seq: Int): ChannelEntity?

    /** Compteurs par catégorie (colonne de gauche du Direct), servis par l'index couvrant. */
    @Query("SELECT categoryId AS categoryId, SUM(CASE WHEN isSeparator = 0 THEN 1 ELSE 0 END) AS n, SUM(isSeparator) AS sections FROM channel WHERE providerId = :pid AND junk = 0 GROUP BY categoryId")
    fun observeCategoryCountsAll(pid: Long): Flow<List<CategoryCount>>

    /** Chaînes masquées par le profil courant, par catégorie : à retrancher de [observeCategoryCountsAll]. */
    @Query("SELECT c.categoryId AS categoryId, SUM(CASE WHEN c.isSeparator = 0 THEN 1 ELSE 0 END) AS n, SUM(c.isSeparator) AS sections FROM channel_hidden_now h JOIN channel c ON c.providerId = h.providerId AND c.remoteId = h.remoteId WHERE h.providerId = :pid AND c.junk = 0 GROUP BY c.categoryId")
    fun observeHiddenCounts(pid: Long): Flow<List<CategoryCount>>


    // Sous-requête et non jointure : avec la jointure, SQLite parcourait toutes les lignes de la source et évaluait le MATCH
    // pour chacune (55 s pour « spider » sur une box 2 Go, ~500 fois plus lent). Ici l'index plein texte est lu une fois.
    @Query("""
        SELECT c.* FROM channel c WHERE c.id IN (SELECT docid FROM channel_fts WHERE channel_fts MATCH :match)
        AND c.providerId = :pid AND c.junk = 0 AND c.isSeparator = 0 AND ${Hid.C} LIMIT :limit
    """)
    suspend fun searchFts(pid: Long, match: String, limit: Int): List<ChannelEntity>

    /** Recherche en arabe (#247) : candidats par motif LIKE sur le nom brut (voir ArabicFold), filtrés ensuite en mémoire. */
    @Query("""
        SELECT c.* FROM channel c WHERE c.providerId = :pid AND c.junk = 0 AND c.isSeparator = 0 AND ${Hid.C}
        AND c.name LIKE :pattern ESCAPE '\' LIMIT :limit
    """)
    suspend fun nameLike(pid: Long, pattern: String, limit: Int): List<ChannelEntity>

    /** Plus longue archive (jours de rattrapage) des chaînes de la source ; 0 si aucune (#248). */
    @Query("SELECT COALESCE(MAX(catchupDays), 0) FROM channel WHERE providerId = :pid")
    suspend fun maxArchiveDays(pid: Long): Int

    /** Chaînes avec archive et leur durée (jours) : programmes passés gardés pour elles seules (#248). */
    @Query("SELECT id, catchupDays FROM channel WHERE providerId = :pid AND catchupDays > 0")
    suspend fun archiveDays(pid: Long): List<ChannelArchive>

    /** Rubrique Replay : chaînes de la source avec archive, hors chaînes masquées du profil, dans l'ordre de « Tout » du Direct. */
    @Query("SELECT * FROM channel WHERE providerId = :pid AND catchupDays > 0 AND junk = 0 AND isSeparator = 0 AND ${Hid.T} ORDER BY catRank, num, id")
    suspend fun archivedChannels(pid: Long): List<ChannelEntity>

    /** La source a-t-elle au moins une chaîne avec archive (entrée « Replay » du menu) ? */
    @Query("SELECT EXISTS(SELECT 1 FROM channel WHERE providerId = :pid AND catchupDays > 0 AND junk = 0 AND isSeparator = 0)")
    suspend fun hasArchive(pid: Long): Boolean
}

/** Durée d'archive d'une chaîne (jours de rattrapage). */
data class ChannelArchive(val id: Long, val catchupDays: Int)

/** Une ligne de distribution (noms séparés par des virgules) et la catégorie du titre. */
data class CastRow(val categoryId: String?, val names: String)

@Dao
interface MovieDao {
    /** Favoris : seulement les films demandés (index unique providerId+remoteId), jamais tout le catalogue. */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND remoteId IN (:remoteIds) ORDER BY id")
    fun observeByRemoteIds(pid: Long, remoteIds: List<String>): Flow<List<MovieEntity>>

    @Query("SELECT COUNT(*) FROM movie WHERE providerId = :pid")
    fun observeCount(pid: Long): Flow<Int>

    /** Nombre de films d'une catégorie (« Mes bibliothèques » : dossiers d'un partage NAS). */
    @Query("SELECT COUNT(*) FROM movie WHERE providerId = :pid AND categoryId = :cat")
    suspend fun countInCategory(pid: Long, cat: String): Int

    /** Catégories de films par identifiant local / distant (rangées de l'accueil, voir HomeGuard). */
    @Query("SELECT id AS id, remoteId AS remoteId, categoryId AS categoryId FROM movie WHERE id IN (:ids)")
    suspend fun categoriesById(ids: List<Long>): List<TitleCategory>

    @Query("SELECT id AS id, remoteId AS remoteId, categoryId AS categoryId FROM movie WHERE providerId = :pid AND remoteId IN (:rids)")
    suspend fun categoriesByRemote(pid: Long, rids: List<String>): List<TitleCategory>

    /** Derniers ajoutés d'une bibliothèque (rangée courte de « Mes bibliothèques »). */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND categoryId = :cat ORDER BY addedKey DESC LIMIT :limit")
    suspend fun latestInCategory(pid: Long, cat: String, limit: Int): List<MovieEntity>

    /** Films « à la une » (rotation) : les mieux notés qui ont une affiche. */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND poster IS NOT NULL AND poster != '' AND rating IS NOT NULL AND rating <= 10 ORDER BY rating DESC LIMIT :limit")
    fun observeHeroes(pid: Long, limit: Int): Flow<List<MovieEntity>>

    /** Film « à la une » : le mieux noté qui a une affiche. */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND poster IS NOT NULL AND poster != '' AND rating IS NOT NULL AND rating <= 10 ORDER BY rating DESC LIMIT 1")
    fun observeHero(pid: Long): Flow<MovieEntity?>

    @Query("SELECT * FROM movie WHERE providerId = :pid ORDER BY id LIMIT :limit")
    fun observeTop(pid: Long, limit: Int): Flow<List<MovieEntity>>

    /** Derniers ajoutés : l'identifiant Xtream (stream_id) croît à chaque ajout du fournisseur. */
    @Query("SELECT * FROM movie WHERE providerId = :pid ORDER BY addedKey DESC LIMIT :limit")
    fun observeLatest(pid: Long, limit: Int): Flow<List<MovieEntity>>

    /** Instantané des derniers ajoutés (chaîne « Nouveautés » de Google TV : relue à la FIN d'une synchro, pas observée). */
    @Query("SELECT * FROM movie WHERE providerId = :pid ORDER BY addedKey DESC LIMIT :limit")
    suspend fun latest(pid: Long, limit: Int): List<MovieEntity>

    /** « Films les mieux notés » (accueil) : note valide et affiche présente. */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND poster IS NOT NULL AND poster != '' AND rating > 0 AND rating <= 10 ORDER BY rating DESC, addedKey DESC LIMIT :limit")
    fun observeTopRated(pid: Long, limit: Int): Flow<List<MovieEntity>>

    /** « Parce que vous avez regardé… » : même genre (texte de la fiche), les mieux notés d'abord. */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND id != :excludeId AND genre LIKE '%' || :genre || '%' AND poster IS NOT NULL AND poster != '' ORDER BY rating IS NULL, rating DESC, addedKey DESC LIMIT :limit")
    suspend fun byGenre(pid: Long, genre: String, excludeId: Long, limit: Int): List<MovieEntity>

    /** Page acteur / réalisateur : films dont la distribution (liste ou fiche) ou le réalisateur cite ce nom. */
    @Query("""
        SELECT m.* FROM movie m LEFT JOIN vod_info v ON v.providerId = m.providerId AND v.remoteId = m.remoteId
        WHERE m.providerId = :pid AND (m.`cast` LIKE '%' || :name || '%' OR v.`cast` LIKE '%' || :name || '%' OR v.director LIKE '%' || :name || '%')
        ORDER BY m.year IS NULL, m.year DESC LIMIT :limit
    """)
    suspend fun byPerson(pid: Long, name: String, limit: Int): List<MovieEntity>

    /** Recherche d'acteurs : distributions de la liste (colonne cast), avec la catégorie pour le filtre du profil. */
    @Query("SELECT categoryId AS categoryId, `cast` AS names FROM movie WHERE providerId = :pid AND `cast` IS NOT NULL AND `cast` != ''")
    suspend fun castRows(pid: Long): List<CastRow>

    /** Recherche d'acteurs : distributions et réalisateurs des fiches déjà ouvertes (vod_info). */
    @Query("""
        SELECT m.categoryId AS categoryId, COALESCE(v.`cast`, '') || ',' || COALESCE(v.director, '') AS names
        FROM vod_info v JOIN movie m ON m.providerId = v.providerId AND m.remoteId = v.remoteId
        WHERE v.providerId = :pid AND ((v.`cast` IS NOT NULL AND v.`cast` != '') OR (v.director IS NOT NULL AND v.director != ''))
    """)
    suspend fun infoCastRows(pid: Long): List<CastRow>

    /** Repli : même catégorie du fournisseur, les mieux notés d'abord. */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND id != :excludeId AND categoryId = :cat AND poster IS NOT NULL AND poster != '' ORDER BY rating IS NULL, rating DESC, addedKey DESC LIMIT :limit")
    suspend fun sameCategory(pid: Long, cat: String, excludeId: Long, limit: Int): List<MovieEntity>

    /** Rangée d'une catégorie (accueil Films) : les plus récents d'abord. */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND categoryId = :cat ORDER BY addedKey DESC LIMIT :limit")
    fun observeRow(pid: Long, cat: String, limit: Int): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movie WHERE providerId = :pid AND categoryId = :cat ORDER BY id")
    fun observeForCategory(pid: Long, cat: String): Flow<List<MovieEntity>>

    @Query("SELECT * FROM movie WHERE providerId = :pid AND remoteId = :rid LIMIT 1")
    suspend fun byRemoteId(pid: Long, rid: String): MovieEntity?

    @Query("SELECT * FROM movie WHERE id = :id")
    suspend fun byId(id: Long): MovieEntity?

    /** Chaînes d'accueil Android TV (Ma liste, Top 10) : lot d'identifiants, ordre non garanti. */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND remoteId IN (:rids)")
    suspend fun byRemoteIds(pid: Long, rids: List<String>): List<MovieEntity>

    @Query("SELECT * FROM movie WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<MovieEntity>

    /** Rapprochement Trakt : colonnes minimales, par tranches (pagination par clé, sans OFFSET). */
    @Query("SELECT id, title, poster, year, rating FROM movie WHERE providerId = :pid AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun liteChunk(pid: Long, afterId: Long, limit: Int): List<CatalogLite>

    /** Recalcul des clés de tri A–Z (toutes sources), par tranches de clé primaire. */
    @Query("SELECT id, title, poster, year, rating FROM movie WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun titleChunk(afterId: Long, limit: Int): List<CatalogLite>

    @Query("UPDATE movie SET sortKey = :key WHERE id = :id")
    suspend fun setSortKey(id: Long, key: String)


    @Query("SELECT * FROM movie WHERE providerId = :pid AND (:useLang = 0 OR lang IN (:langs)) ORDER BY id")
    fun pagedAll(pid: Long, useLang: Int, langs: List<String>): androidx.paging.PagingSource<Int, MovieEntity>

    /*
     * Grille filtrée (puces Tri, Note, Période ; 0 = sans filtre). « Tous » : une requête PAR TRI, pour que l'ordre suive
     * un index (providerId, rating) / (providerId, addedKey) / rowid — un ORDER BY CASE … obligeait SQLite à retrier
     * toute la table (180 000 lignes) à chaque page. Dans une catégorie, déjà bornée par son index, un seul tri suffit.
     */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY id")
    fun pagedFilteredByProvider(pid: Long, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movie WHERE providerId = :pid AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY addedKey DESC, id DESC")
    fun pagedFilteredByRecent(pid: Long, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movie WHERE providerId = :pid AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY rating DESC, id DESC")
    fun pagedFilteredByRating(pid: Long, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, MovieEntity>

    /** Tri « A–Z » ([titleSortKeyOf] : sans article ni accents). Pas d'index : tri à la demande, choisi explicitement. */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY sortKey, id")
    fun pagedFilteredByTitle(pid: Long, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movie WHERE providerId = :pid AND categoryId = :cat AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY CASE WHEN :sort = 2 THEN rating END DESC, CASE WHEN :sort = 1 THEN addedKey END DESC, CASE WHEN :sort = 3 THEN sortKey END, id")
    fun pagedFilteredInCategory(pid: Long, cat: String, useLang: Int, langs: List<String>, sort: Int, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movie WHERE providerId = :pid AND categoryId = :cat AND (:useLang = 0 OR lang IN (:langs)) ORDER BY id")
    fun pagedForCategory(pid: Long, cat: String, useLang: Int, langs: List<String>): androidx.paging.PagingSource<Int, MovieEntity>


    /*
     * Titres d'un genre automatique (table title_genre) : la sous-requête EXISTS filtre les lignes dans l'ORDRE de l'index de
     * tri (rowid / (providerId, addedKey) / (providerId, rating)) — pas de tri complet, et une page s'arrête dès qu'elle est pleine.
     */
    @Query("SELECT * FROM movie m WHERE m.providerId = :pid AND EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = m.providerId AND g.kind = 'MOVIE' AND g.remoteId = m.remoteId AND g.genre = :genre) AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY id")
    fun pagedGenreByProvider(pid: Long, genre: String, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movie m WHERE m.providerId = :pid AND EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = m.providerId AND g.kind = 'MOVIE' AND g.remoteId = m.remoteId AND g.genre = :genre) AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY addedKey DESC, id DESC")
    fun pagedGenreByRecent(pid: Long, genre: String, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movie m WHERE m.providerId = :pid AND EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = m.providerId AND g.kind = 'MOVIE' AND g.remoteId = m.remoteId AND g.genre = :genre) AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY rating DESC, id DESC")
    fun pagedGenreByRating(pid: Long, genre: String, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movie m WHERE m.providerId = :pid AND EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = m.providerId AND g.kind = 'MOVIE' AND g.remoteId = m.remoteId AND g.genre = :genre) AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY sortKey, id")
    fun pagedGenreByTitle(pid: Long, genre: String, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, MovieEntity>

    /** Rangée d'un genre automatique (vue « Rangées ») : les plus récents d'abord. */
    @Query("SELECT * FROM movie m WHERE m.providerId = :pid AND EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = m.providerId AND g.kind = 'MOVIE' AND g.remoteId = m.remoteId AND g.genre = :genre) ORDER BY addedKey DESC LIMIT :limit")
    fun observeGenreRow(pid: Long, genre: String, limit: Int): Flow<List<MovieEntity>>

    /** Films proches par genre automatique (title_genre), hors film de départ : pour « Parce que vous avez regardé ». */
    @Query("SELECT * FROM movie m WHERE m.providerId = :pid AND m.id != :excludeId AND EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = m.providerId AND g.kind = 'MOVIE' AND g.remoteId = m.remoteId AND g.genre = :genre) ORDER BY rating DESC, id DESC LIMIT :limit")
    suspend fun byAutoGenre(pid: Long, genre: String, excludeId: Long, limit: Int): List<MovieEntity>

    @Query("UPDATE movie SET plot = :plot, cast = :cast, genre = :genre, duration = :duration, backdrop = :backdrop WHERE id = :id")
    suspend fun updateDetails(id: Long, plot: String?, cast: String?, genre: String?, duration: String?, backdrop: String?)

    @Query("SELECT lang AS lang, COUNT(*) AS n FROM movie WHERE providerId = :pid GROUP BY lang")
    fun observeLangCounts(pid: Long): Flow<List<LangCount>>

    @Query("SELECT categoryId AS categoryId, COUNT(*) AS n, 0 AS sections FROM movie WHERE providerId = :pid GROUP BY categoryId")
    fun observeCategoryCounts(pid: Long): Flow<List<CategoryCount>>

    @Query("""
        SELECT m.* FROM movie m WHERE m.id IN (SELECT docid FROM movie_fts WHERE movie_fts MATCH :match)
        AND m.providerId = :pid LIMIT :limit
    """)
    suspend fun searchFts(pid: Long, match: String, limit: Int): List<MovieEntity>


    /**
     * Titres qui COMMENCENT par la saisie (« :prefix » se termine par %, jokers de la saisie échappés par « \\ »).
     * Complète la recherche plein texte quand un mot courant sature son plafond : le titre exact n'y était pas forcément.
     */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND title LIKE :prefix ESCAPE '\\' LIMIT :limit")
    suspend fun titlePrefix(pid: Long, prefix: String, limit: Int): List<MovieEntity>

    /** Recherche en arabe (#247) : candidats par motif LIKE sur le nom brut (voir ArabicFold), filtrés ensuite en mémoire. */
    @Query("SELECT * FROM movie WHERE providerId = :pid AND name LIKE :pattern ESCAPE '\\' LIMIT :limit")
    suspend fun nameLike(pid: Long, pattern: String, limit: Int): List<MovieEntity>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MovieEntity>)

    @Query("DELETE FROM movie WHERE providerId = :pid")
    suspend fun deleteForProvider(pid: Long)

    @Query("DELETE FROM movie WHERE providerId = :pid AND categoryId IN (:ids)")
    suspend fun deleteForCategories(pid: Long, ids: List<String>)

    @Query("DELETE FROM movie WHERE providerId = :pid AND categoryId = :cat")
    suspend fun deleteForCategory(pid: Long, cat: String)
}

@Dao
interface SeriesDao {
    /** Favoris : seulement les séries demandées (index unique providerId+remoteId), jamais tout le catalogue. */
    @Query("SELECT * FROM series WHERE providerId = :pid AND remoteId IN (:remoteIds) ORDER BY id")
    fun observeByRemoteIds(pid: Long, remoteIds: List<String>): Flow<List<SeriesEntity>>

    /** Complète la fiche série avec ce que renvoie get_series_info (sans écraser par du vide). */
    @Query("UPDATE series SET plot = COALESCE(:plot, plot), genre = COALESCE(:genre, genre), `cast` = COALESCE(:cast, `cast`), backdrop = COALESCE(:backdrop, backdrop), year = COALESCE(:year, year), rating = COALESCE(:rating, rating) WHERE id = :id")
    suspend fun updateInfo(id: Long, plot: String?, genre: String?, cast: String?, backdrop: String?, year: Int?, rating: Double?)

    @Query("SELECT COUNT(*) FROM series WHERE providerId = :pid")
    fun observeCount(pid: Long): Flow<Int>

    /** Nombre de séries d'une catégorie (« Mes bibliothèques » : dossiers d'un partage NAS). */
    @Query("SELECT COUNT(*) FROM series WHERE providerId = :pid AND categoryId = :cat")
    suspend fun countInCategory(pid: Long, cat: String): Int

    /** Catégories de séries par identifiant local / distant (rangées de l'accueil, voir HomeGuard). */
    @Query("SELECT id AS id, remoteId AS remoteId, categoryId AS categoryId FROM series WHERE id IN (:ids)")
    suspend fun categoriesById(ids: List<Long>): List<TitleCategory>

    @Query("SELECT id AS id, remoteId AS remoteId, categoryId AS categoryId FROM series WHERE providerId = :pid AND remoteId IN (:rids)")
    suspend fun categoriesByRemote(pid: Long, rids: List<String>): List<TitleCategory>

    /** Derniers ajoutées d'une bibliothèque (rangée courte de « Mes bibliothèques »). */
    @Query("SELECT * FROM series WHERE providerId = :pid AND categoryId = :cat ORDER BY addedKey DESC LIMIT :limit")
    suspend fun latestInCategory(pid: Long, cat: String, limit: Int): List<SeriesEntity>

    /** Séries « à la une » (rotation) : les mieux notées qui ont une image paysage. */
    @Query("SELECT * FROM series WHERE providerId = :pid AND backdrop IS NOT NULL AND backdrop != '' AND rating IS NOT NULL AND rating <= 10 ORDER BY rating DESC LIMIT :limit")
    fun observeHeroes(pid: Long, limit: Int): Flow<List<SeriesEntity>>

    /** Série « à la une » : la mieux notée qui a une image paysage (backdrop_path). */
    @Query("SELECT * FROM series WHERE providerId = :pid AND backdrop IS NOT NULL AND rating IS NOT NULL AND rating <= 10 ORDER BY rating DESC LIMIT 1")
    fun observeHero(pid: Long): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE providerId = :pid ORDER BY id LIMIT :limit")
    fun observeTop(pid: Long, limit: Int): Flow<List<SeriesEntity>>

    /** Dernières ajoutées : l'identifiant Xtream (series_id) croît à chaque ajout du fournisseur. */
    @Query("SELECT * FROM series WHERE providerId = :pid ORDER BY addedKey DESC LIMIT :limit")
    fun observeLatest(pid: Long, limit: Int): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE providerId = :pid ORDER BY addedKey DESC LIMIT :limit")
    suspend fun latest(pid: Long, limit: Int): List<SeriesEntity>

    /** Page acteur : séries dont la distribution cite ce nom. */
    @Query("SELECT * FROM series WHERE providerId = :pid AND `cast` LIKE '%' || :name || '%' ORDER BY year IS NULL, year DESC LIMIT :limit")
    suspend fun byPerson(pid: Long, name: String, limit: Int): List<SeriesEntity>

    /** Recherche d'acteurs : distributions des séries, avec la catégorie pour le filtre du profil. */
    @Query("SELECT categoryId AS categoryId, `cast` AS names FROM series WHERE providerId = :pid AND `cast` IS NOT NULL AND `cast` != ''")
    suspend fun castRows(pid: Long): List<CastRow>

    /** « Séries les mieux notées » (accueil) : note valide et affiche présente. */
    @Query("SELECT * FROM series WHERE providerId = :pid AND poster IS NOT NULL AND poster != '' AND rating > 0 AND rating <= 10 ORDER BY rating DESC, addedKey DESC LIMIT :limit")
    fun observeTopRated(pid: Long, limit: Int): Flow<List<SeriesEntity>>

    /** Rangée d'une catégorie (accueil Séries) : les plus récentes d'abord. */
    @Query("SELECT * FROM series WHERE providerId = :pid AND categoryId = :cat ORDER BY addedKey DESC LIMIT :limit")
    fun observeRow(pid: Long, cat: String, limit: Int): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE providerId = :pid AND categoryId = :cat ORDER BY id")
    fun observeForCategory(pid: Long, cat: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series WHERE providerId = :pid AND remoteId = :rid LIMIT 1")
    suspend fun byRemoteId(pid: Long, rid: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun byId(id: Long): SeriesEntity?

    /** Chaînes d'accueil Android TV (Ma liste, Top 10) : lot d'identifiants, ordre non garanti. */
    @Query("SELECT * FROM series WHERE providerId = :pid AND remoteId IN (:rids)")
    suspend fun byRemoteIds(pid: Long, rids: List<String>): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<SeriesEntity>

    /** Rapprochement Trakt : colonnes minimales, par tranches (pagination par clé, sans OFFSET). */
    @Query("SELECT id, title, poster, year, rating FROM series WHERE providerId = :pid AND id > :afterId ORDER BY id LIMIT :limit")
    suspend fun liteChunk(pid: Long, afterId: Long, limit: Int): List<CatalogLite>

    /** Recalcul des clés de tri A–Z (toutes sources), par tranches de clé primaire. */
    @Query("SELECT id, title, poster, year, rating FROM series WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun titleChunk(afterId: Long, limit: Int): List<CatalogLite>

    @Query("UPDATE series SET sortKey = :key WHERE id = :id")
    suspend fun setSortKey(id: Long, key: String)


    @Query("SELECT * FROM series WHERE providerId = :pid AND (:useLang = 0 OR lang IN (:langs)) ORDER BY id")
    fun pagedAll(pid: Long, useLang: Int, langs: List<String>): androidx.paging.PagingSource<Int, SeriesEntity>

    /*
     * Grille filtrée (puces Tri, Note, Période ; 0 = sans filtre). « Tous » : une requête PAR TRI, pour que l'ordre suive
     * un index (providerId, rating) / (providerId, addedKey) / rowid — un ORDER BY CASE … obligeait SQLite à retrier
     * toute la table (180 000 lignes) à chaque page. Dans une catégorie, déjà bornée par son index, un seul tri suffit.
     */
    @Query("SELECT * FROM series WHERE providerId = :pid AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY id")
    fun pagedFilteredByProvider(pid: Long, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE providerId = :pid AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY addedKey DESC, id DESC")
    fun pagedFilteredByRecent(pid: Long, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE providerId = :pid AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY rating DESC, id DESC")
    fun pagedFilteredByRating(pid: Long, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, SeriesEntity>

    /** Tri « A–Z » ([titleSortKeyOf] : sans article ni accents). Pas d'index : tri à la demande, choisi explicitement. */
    @Query("SELECT * FROM series WHERE providerId = :pid AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY sortKey, id")
    fun pagedFilteredByTitle(pid: Long, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE providerId = :pid AND categoryId = :cat AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY CASE WHEN :sort = 2 THEN rating END DESC, CASE WHEN :sort = 1 THEN addedKey END DESC, CASE WHEN :sort = 3 THEN sortKey END, id")
    fun pagedFilteredInCategory(pid: Long, cat: String, useLang: Int, langs: List<String>, sort: Int, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE providerId = :pid AND categoryId = :cat AND (:useLang = 0 OR lang IN (:langs)) ORDER BY id")
    fun pagedForCategory(pid: Long, cat: String, useLang: Int, langs: List<String>): androidx.paging.PagingSource<Int, SeriesEntity>


    /*
     * Titres d'un genre automatique (table title_genre) : la sous-requête EXISTS filtre les lignes dans l'ORDRE de l'index de
     * tri (rowid / (providerId, addedKey) / (providerId, rating)) — pas de tri complet, et une page s'arrête dès qu'elle est pleine.
     */
    @Query("SELECT * FROM series s WHERE s.providerId = :pid AND EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = s.providerId AND g.kind = 'SERIES' AND g.remoteId = s.remoteId AND g.genre = :genre) AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY id")
    fun pagedGenreByProvider(pid: Long, genre: String, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series s WHERE s.providerId = :pid AND EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = s.providerId AND g.kind = 'SERIES' AND g.remoteId = s.remoteId AND g.genre = :genre) AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY addedKey DESC, id DESC")
    fun pagedGenreByRecent(pid: Long, genre: String, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series s WHERE s.providerId = :pid AND EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = s.providerId AND g.kind = 'SERIES' AND g.remoteId = s.remoteId AND g.genre = :genre) AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY rating DESC, id DESC")
    fun pagedGenreByRating(pid: Long, genre: String, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series s WHERE s.providerId = :pid AND EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = s.providerId AND g.kind = 'SERIES' AND g.remoteId = s.remoteId AND g.genre = :genre) AND (:useLang = 0 OR lang IN (:langs)) AND (:minRating <= 0 OR (rating >= :minRating AND rating <= 10)) AND (:yearFrom = 0 OR year >= :yearFrom) AND (:yearTo = 0 OR year <= :yearTo) ORDER BY sortKey, id")
    fun pagedGenreByTitle(pid: Long, genre: String, useLang: Int, langs: List<String>, minRating: Int, yearFrom: Int, yearTo: Int): androidx.paging.PagingSource<Int, SeriesEntity>

    /** Rangée d'un genre automatique (vue « Rangées ») : les plus récents d'abord. */
    @Query("SELECT * FROM series s WHERE s.providerId = :pid AND EXISTS (SELECT 1 FROM title_genre g WHERE g.providerId = s.providerId AND g.kind = 'SERIES' AND g.remoteId = s.remoteId AND g.genre = :genre) ORDER BY addedKey DESC LIMIT :limit")
    fun observeGenreRow(pid: Long, genre: String, limit: Int): Flow<List<SeriesEntity>>

    @Query("SELECT lang AS lang, COUNT(*) AS n FROM series WHERE providerId = :pid GROUP BY lang")
    fun observeLangCounts(pid: Long): Flow<List<LangCount>>

    @Query("SELECT categoryId AS categoryId, COUNT(*) AS n, 0 AS sections FROM series WHERE providerId = :pid GROUP BY categoryId")
    fun observeCategoryCounts(pid: Long): Flow<List<CategoryCount>>

    @Query("""
        SELECT s.* FROM series s WHERE s.id IN (SELECT docid FROM series_fts WHERE series_fts MATCH :match)
        AND s.providerId = :pid LIMIT :limit
    """)
    suspend fun searchFts(pid: Long, match: String, limit: Int): List<SeriesEntity>


    /**
     * Titres qui COMMENCENT par la saisie (« :prefix » se termine par %, jokers de la saisie échappés par « \\ »).
     * Complète la recherche plein texte quand un mot courant sature son plafond : le titre exact n'y était pas forcément.
     */
    @Query("SELECT * FROM series WHERE providerId = :pid AND title LIKE :prefix ESCAPE '\\' LIMIT :limit")
    suspend fun titlePrefix(pid: Long, prefix: String, limit: Int): List<SeriesEntity>

    /** Recherche en arabe (#247) : candidats par motif LIKE sur le nom brut (voir ArabicFold), filtrés ensuite en mémoire. */
    @Query("SELECT * FROM series WHERE providerId = :pid AND name LIKE :pattern ESCAPE '\\' LIMIT :limit")
    suspend fun nameLike(pid: Long, pattern: String, limit: Int): List<SeriesEntity>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SeriesEntity>)

    @Query("DELETE FROM series WHERE providerId = :pid")
    suspend fun deleteForProvider(pid: Long)

    @Query("DELETE FROM series WHERE providerId = :pid AND categoryId IN (:ids)")
    suspend fun deleteForCategories(pid: Long, ids: List<String>)

    @Query("DELETE FROM series WHERE providerId = :pid AND categoryId = :cat")
    suspend fun deleteForCategory(pid: Long, cat: String)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episode WHERE seriesId = :sid ORDER BY season, episode")
    fun observeForSeries(sid: Long): Flow<List<EpisodeEntity>>

    /** Épisode d'une source par son identifiant distant (liens profonds Watch Next). */
    @Query("SELECT e.* FROM episode e JOIN series s ON s.id = e.seriesId WHERE s.providerId = :pid AND e.remoteId = :rid LIMIT 1")
    suspend fun byRemoteId(pid: Long, rid: String): EpisodeEntity?

    @Query("SELECT * FROM episode WHERE id = :id")
    suspend fun byId(id: Long): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EpisodeEntity>)

    @Query("DELETE FROM episode WHERE seriesId = :sid")
    suspend fun deleteForSeries(sid: Long)

    @Query("SELECT * FROM episode WHERE seriesId = :sid ORDER BY season, episode")
    suspend fun forSeries(sid: Long): List<EpisodeEntity>

    /** Remplacement ATOMIQUE : l'écran ne voit jamais la liste vide entre la suppression et la réinsertion. */
    @androidx.room.Transaction
    suspend fun replaceForSeries(sid: Long, eps: List<EpisodeEntity>) {
        deleteForSeries(sid)
        upsertAll(eps)
    }
}

@Dao
interface CategoryDao {

    @Query("SELECT remoteId FROM category WHERE providerId = :pid AND kind = :kind")
    suspend fun remoteIds(pid: Long, kind: String): List<String>

    @Query("SELECT remoteId FROM category WHERE providerId = :pid AND kind = :kind AND locked = 1")
    suspend fun lockedIds(pid: Long, kind: String): List<String>

    @Query("SELECT remoteId FROM category WHERE providerId = :pid AND kind = :kind AND enabled = 0")
    suspend fun disabledIds(pid: Long, kind: String): List<String>

    @Query("SELECT * FROM category WHERE providerId = :pid AND kind = :kind ORDER BY CASE WHEN position = 0 THEN 1 ELSE 0 END, position, id")
    fun observeForProviderKind(pid: Long, kind: String): Flow<List<CategoryEntity>>

    @Query("UPDATE category SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    /** Verrou parental d'une catégorie (Gérer les catégories › cadenas) ; conservé par les synchros (CatalogPlan.merge). */
    @Query("UPDATE category SET locked = :locked WHERE providerId = :pid AND kind = :kind AND remoteId = :remoteId")
    suspend fun setLockedRemote(pid: Long, kind: String, remoteId: String, locked: Boolean)

    @Query("SELECT kind AS kind, enabled AS enabled, COUNT(*) AS n FROM category WHERE providerId = :pid GROUP BY kind, enabled")
    fun observeEnabledCounts(pid: Long): Flow<List<EnabledCount>>

    @Query("SELECT lang AS lang, COUNT(*) AS n FROM category WHERE providerId = :pid GROUP BY lang")
    fun observeLangCounts(pid: Long): Flow<List<LangCount>>

    @Query("SELECT * FROM category WHERE providerId = :pid AND kind = :kind")
    suspend fun forProviderKind(pid: Long, kind: String): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE providerId = :pid AND kind = :kind ORDER BY CASE WHEN position = 0 THEN 1 ELSE 0 END, position, id")
    fun observeOrdered(pid: Long, kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM category WHERE providerId = :pid AND kind = :kind")
    fun observeCount(pid: Long, kind: String): Flow<Int>

    @Query("SELECT remoteId FROM category WHERE providerId = :pid AND kind = :kind ORDER BY CASE WHEN position = 0 THEN 1 ELSE 0 END, position, id")
    suspend fun orderedIds(pid: Long, kind: String): List<String>

    @Query("UPDATE category SET enabled = :enabled WHERE providerId = :pid AND kind = :kind AND remoteId IN (:ids)")
    suspend fun setEnabled(pid: Long, kind: String, ids: List<String>, enabled: Boolean)

    @Query("UPDATE category SET position = :position WHERE providerId = :pid AND kind = :kind AND remoteId = :id")
    suspend fun setPosition(pid: Long, kind: String, id: String, position: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CategoryEntity>)

    @Query("DELETE FROM category WHERE providerId = :pid AND kind = :kind")
    suspend fun deleteForProviderKind(pid: Long, kind: String)

    @Query("DELETE FROM category WHERE providerId = :pid AND kind = :kind AND remoteId IN (:ids)")
    suspend fun deleteRemoteIds(pid: Long, kind: String, ids: List<String>)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite WHERE profileId = :profileId AND providerId = :pid AND kind = :kind")
    fun observeForKind(profileId: Long, pid: Long, kind: String): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) FROM favorite WHERE profileId = :profileId AND providerId = :pid AND kind = :kind")
    fun observeCount(profileId: Long, pid: Long, kind: String): Flow<Int>

    /** Tous les favoris du profil pour la source (compteur du raccourci « Favoris » de l'accueil). */
    @Query("SELECT COUNT(*) FROM favorite WHERE profileId = :profileId AND providerId = :pid")
    fun observeCountAll(profileId: Long, pid: Long): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE profileId = :profileId AND providerId = :pid AND kind = :kind AND remoteId = :rid)")
    fun observeIsFavorite(profileId: Long, pid: Long, kind: String, rid: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(f: FavoriteEntity)

    /** Favoris (tous profils) qui dépendent d'une source : sert à confirmer son retrait. */
    @Query("SELECT * FROM favorite WHERE providerId = :pid")
    suspend fun allForProvider(pid: Long): List<FavoriteEntity>

    @Query("SELECT * FROM favorite")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT COUNT(*) FROM favorite WHERE providerId = :pid")
    suspend fun countForProvider(pid: Long): Int

    @Query("DELETE FROM favorite WHERE profileId = :profileId AND providerId = :pid AND kind = :kind AND remoteId = :rid")
    suspend fun remove(profileId: Long, pid: Long, kind: String, rid: String)
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND providerId = :pid ORDER BY watchedAt DESC LIMIT :limit")
    fun observeRecent(profileId: Long, pid: Long, limit: Int = 30): Flow<List<WatchHistoryEntity>>

    @Query("SELECT positionMs FROM watch_history WHERE profileId = :profileId AND providerId = :pid AND kind = :kind AND remoteId = :rid LIMIT 1")
    suspend fun positionFor(profileId: Long, pid: Long, kind: String, rid: String): Long?

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND providerId = :pid AND kind = :kind ORDER BY watchedAt DESC LIMIT :limit")
    fun observeRecentByKind(profileId: Long, pid: Long, kind: String, limit: Int = 30): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND providerId = :pid AND positionMs > 0 AND (durationMs = 0 OR positionMs < durationMs - 60000) ORDER BY watchedAt DESC LIMIT :limit")
    fun observeContinueWatching(profileId: Long, pid: Long, limit: Int = 20): Flow<List<WatchHistoryEntity>>

    /** Progression des épisodes d'une série (fiche série : barre de progression, « Reprendre »). */
    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND providerId = :pid AND kind = 'EPISODE' AND parentRemoteId = :parent ORDER BY watchedAt DESC")
    fun observeEpisodesOf(profileId: Long, pid: Long, parent: String): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(h: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE profileId = :profileId AND providerId = :pid AND kind = :kind AND remoteId = :rid")
    suspend fun remove(profileId: Long, pid: Long, kind: String, rid: String)

    /** Purge l'historique de TOUS les profils pour un fournisseur supprimé. */
    @Query("SELECT * FROM watch_history WHERE providerId = :pid AND watchedAt > :since ORDER BY watchedAt DESC LIMIT :limit")
    suspend fun changedSince(pid: Long, since: Long, limit: Int = 400): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND providerId = :pid AND kind = :kind AND remoteId = :rid LIMIT 1")
    suspend fun get(profileId: Long, pid: Long, kind: String, rid: String): WatchHistoryEntity?

    @Query("SELECT MAX(watchedAt) FROM watch_history")
    fun observeLatestAt(): Flow<Long?>

    @Query("DELETE FROM watch_history WHERE providerId = :pid")
    suspend fun clearForProvider(pid: Long)

    /** Rubrique « Mémoire et stockage » : efface l'historique d'un profil (retourne le nombre de lignes). */
    @Query("DELETE FROM watch_history WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long): Int

    @Query("DELETE FROM watch_history")
    suspend fun clearAll(): Int

    /** Remet à zéro les positions de reprise d'un profil, en gardant l'historique lui-même. */
    @Query("UPDATE watch_history SET positionMs = 0 WHERE profileId = :profileId AND positionMs > 0")
    suspend fun resetPositionsForProfile(profileId: Long): Int

    @Query("UPDATE watch_history SET positionMs = 0 WHERE positionMs > 0")
    suspend fun resetPositionsAll(): Int
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recording ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recording WHERE id = :id")
    suspend fun byId(id: Long): RecordingEntity?

    @Query("SELECT COUNT(*) FROM recording WHERE providerId = :pid")
    suspend fun countForProvider(pid: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: RecordingEntity): Long

    @Query("UPDATE recording SET status = :status, downloadedBytes = :down, totalBytes = :total, errorMessage = :err, completedAt = :doneAt WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, down: Long, total: Long, err: String?, doneAt: Long?)

    @Query("DELETE FROM recording WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM recording WHERE status = 'scheduled' AND scheduledEndMs > :nowMs ORDER BY scheduledStartMs")
    suspend fun scheduledPending(nowMs: Long): List<RecordingEntity>

    @Query("UPDATE recording SET status = 'failed', errorMessage = 'missed' WHERE status = 'scheduled' AND scheduledEndMs < :nowMs")
    suspend fun expireMissed(nowMs: Long)

    @Query("SELECT COUNT(*) FROM recording WHERE status = 'scheduled' AND providerId = :pid AND remoteId = :rid AND scheduledStartMs = :startMs")
    suspend fun countScheduled(pid: Long, rid: String, startMs: Long): Int

    @Query("SELECT COUNT(*) FROM recording WHERE status = 'running'")
    fun observeRunningCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recording WHERE status = 'running'")
    suspend fun runningCount(): Int

    /** Enregistrements programmés ou en cours de la source [pid] qui chevauchent [startMs, endMs). */
    @Query("SELECT COUNT(*) FROM recording WHERE providerId = :pid AND status IN ('scheduled', 'queued', 'running') AND scheduledEndMs > :startMs AND scheduledStartMs < :endMs")
    suspend fun countOverlapping(pid: Long, startMs: Long, endMs: Long): Int

    /** Enregistrements en cours (ou lancés) de la source [pid], hors [exceptId]. */
    @Query("SELECT COUNT(*) FROM recording WHERE providerId = :pid AND status IN ('queued', 'running') AND id != :exceptId")
    suspend fun countActive(pid: Long, exceptId: Long): Int

    @Query("UPDATE recording SET status = :status, errorMessage = :err WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, err: String?)
}

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg WHERE channelId = :cid AND endMs >= :nowMs ORDER BY startMs LIMIT 20")
    fun observeUpcoming(cid: Long, nowMs: Long): Flow<List<EpgEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EpgEntity>)

    /** Programmes en cours ou à venir dont le titre correspond au motif LIKE (ESCAPE antislash), triés par début : le premier de chaque chaîne est le plus proche. */
    @Query("""
        SELECT c.*, e.title AS pTitle, e.startMs AS pStart, e.endMs AS pEnd
        FROM epg e JOIN channel c ON c.id = e.channelId
        WHERE c.providerId = :pid AND c.junk = 0 AND c.isSeparator = 0 AND ${Hid.C}
          AND e.endMs > :nowMs AND e.startMs <= :toMs AND e.title LIKE :pattern ESCAPE '\'
        ORDER BY e.startMs LIMIT :limit
    """)
    suspend fun searchPrograms(pid: Long, pattern: String, nowMs: Long, toMs: Long, limit: Int): List<com.ultratv.tv.nativeapp.data.repo.ProgramHitRow>

    /** Chaînes de la source qui ont au moins un programme encore à venir ou en cours. */
    // Index endMs : seuls les programmes en cours / à venir sont parcourus (avant : jointure sur tout le guide).
    @Query("SELECT DISTINCT e.channelId FROM epg e WHERE e.endMs >= :nowMs AND e.channelId IN (SELECT id FROM channel WHERE providerId = :pid)")
    suspend fun channelsWithProgrammes(pid: Long, nowMs: Long): List<Long>

    @Query("DELETE FROM epg WHERE channelId = :cid")
    suspend fun deleteForChannel(cid: Long)

    @Query("DELETE FROM epg WHERE channelId IN (:cids)")
    suspend fun deleteForChannels(cids: List<Long>)

    @Query("DELETE FROM epg WHERE channelId IN (SELECT id FROM channel WHERE providerId = :pid)")
    suspend fun deleteForProvider(pid: Long)

    /**
     * Fin du dernier programme du guide de la source (null si guide vide) : le guide couvre-t-il encore les heures à
     * venir ? Parcourt l'index endMs à rebours et s'arrête à la première ligne de la source (pas toute la table).
     */
    @Query("SELECT e.endMs FROM epg e WHERE e.channelId IN (SELECT id FROM channel WHERE providerId = :pid) ORDER BY e.endMs DESC LIMIT 1")
    suspend fun lastEndForProvider(pid: Long): Long?

    /**
     * Programmes terminés avant [cutoffMs] : le guide ne grossit plus sans fin. Sauf ceux d'une chaîne avec archive encore
     * dans son archive (plafond 7 jours), lisibles en rattrapage depuis les jours passés du guide (#248).
     */
    @Query("""
        DELETE FROM epg WHERE endMs < :cutoffMs AND endMs < :nowMs - ${com.ultratv.tv.nativeapp.data.repo.EpgRetention.DAY_MS} *
        COALESCE((SELECT MAX(0, MIN(c.catchupDays, ${com.ultratv.tv.nativeapp.data.repo.EpgRetention.MAX_ARCHIVE_DAYS})) FROM channel c WHERE c.id = epg.channelId), 0)
    """)
    suspend fun deletePast(cutoffMs: Long, nowMs: Long): Int

    @Query("SELECT * FROM epg WHERE channelId IN (:channelIds) AND endMs >= :nowMs AND startMs <= :windowEndMs ORDER BY startMs")
    suspend fun rangeForChannels(channelIds: List<Long>, nowMs: Long, windowEndMs: Long): List<EpgEntity>

    /**
     * Rubrique Sport : comme [rangeForChannels] mais SANS la description (souvent longue : des milliers de lignes en
     * mémoire sur une box modeste). Index (channelId, startMs). Paquets de 400 chaînes au plus.
     */
    @Query("SELECT id, channelId, title, NULL AS description, startMs, endMs FROM epg WHERE channelId IN (:channelIds) AND endMs > :nowMs AND startMs <= :toMs ORDER BY startMs")
    suspend fun sportRange(channelIds: List<Long>, nowMs: Long, toMs: Long): List<EpgEntity>

    /** Rubrique Sport : pré-filtre des titres (LIKE construit par SportRepository), sans description. */
    @RawQuery
    suspend fun epgRaw(query: SupportSQLiteQuery): List<EpgEntity>

    /** Full programme list for one channel within a time window — used by the
     *  "tonight's schedule" column on the Live screen. */
    @Query("SELECT * FROM epg WHERE channelId = :cid AND endMs >= :fromMs AND startMs <= :toMs ORDER BY startMs")
    suspend fun forChannelInRange(cid: Long, fromMs: Long, toMs: Long): List<EpgEntity>
}

data class LangCount(val lang: String, val n: Int)

/** Projection légère d'un film / d'une série (rapprochement Trakt). `title` = titre déjà nettoyé. */
data class CatalogLite(val id: Long, val title: String, val poster: String?, val year: Int?, val rating: Double?)

/** Identifiants et catégorie d'un titre ou d'une chaîne (filtre de l'accueil). */
data class TitleCategory(val id: Long, val remoteId: String, val categoryId: String?)

data class EnabledCount(val kind: String, val enabled: Boolean, val n: Int)

data class CategoryCount(val categoryId: String?, val n: Int, val sections: Int = 0)

/** Nombre de chaînes du Direct VISIBLES pour le profil courant. */
suspend fun ChannelDao.countLive(pid: Long): Int = countLiveAll(pid) - countLiveHidden(pid)

/** Compteurs par catégorie VISIBLES pour le profil courant (voir [minusHidden]). */
fun ChannelDao.observeCategoryCounts(pid: Long): Flow<List<CategoryCount>> =
    kotlinx.coroutines.flow.combine(observeCategoryCountsAll(pid), observeHiddenCounts(pid)) { all, hid -> all.minusHidden(hid) }

/** Retranche les chaînes masquées ([hidden]) des compteurs par catégorie. Pur. */
fun List<CategoryCount>.minusHidden(hidden: List<CategoryCount>): List<CategoryCount> {
    if (hidden.isEmpty()) return this
    val h = hidden.associateBy { it.categoryId }
    return map { c -> h[c.categoryId]?.let { x -> c.copy(n = (c.n - x.n).coerceAtLeast(0), sections = (c.sections - x.sections).coerceAtLeast(0)) } ?: c }
}

/** Parties du catalogue synchronisées (et horodatées) indépendamment. */
enum class SyncPart { LIVE, VOD, SERIES, EPG }

data class EpgMapping(val id: Long, val epgChannelId: String)
data class ChannelIdRow(val id: Long, val remoteId: String)

/** Plus grands `num` et `seq` des chaînes du fournisseur dans une catégorie (voir ManualChannels). */
data class CategoryMaxRow(val categoryId: String?, val maxNum: Int, val maxSeq: Int)
data class SeqRow(val id: Long, val seq: Int)
data class EpgTitle(val id: Long, val title: String, val epgChannelId: String?, val country: String? = null)

data class RawPassword(val id: Long, val raw: String)

@Dao
interface VodInfoDao {
    @Query("SELECT * FROM vod_info WHERE providerId = :pid AND remoteId = :rid")
    suspend fun get(pid: Long, rid: String): VodInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(v: VodInfoEntity)
}
