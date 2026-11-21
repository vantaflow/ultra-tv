package com.ultratv.tv.nativeapp.data.profile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Source de vérité des profils et du profil courant. */
@Singleton
class ProfileRepository(
    private val dao: ProfileDao,
    private val state: ProfileStateStore,
    scope: CoroutineScope,
    /** Journal pour la synchro entre appareils (absent dans les tests). */
    private val log: ProfileSyncLog? = null,
    /** Réglages de Direct par profil à recalculer au changement de profil (absent dans la plupart des tests). */
    private val hook: ProfileSwitchHook? = null,
) {
    @Inject constructor(dao: ProfileDao, state: ProfileStateStore, log: ProfileSyncLog, hook: com.ultratv.tv.nativeapp.data.repo.ProfileSwitcher) :
        this(dao, state, CoroutineScope(SupervisorJob() + Dispatchers.Default + com.ultratv.tv.nativeapp.BackgroundTasks.handler("profiles")), log, hook)

    /** Appelé aussi pour un profil créé par la synchro entre appareils (voir [applySharedProfiles]). */
    internal suspend fun profileCreated(id: Long) { runCatching { hook?.onProfileCreated(id) } }

    val profiles: StateFlow<List<ProfileEntity>> =
        dao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Profil courant : le dernier choisi, sinon le premier. Null tant que la base n'a pas répondu. */
    val current: StateFlow<ProfileEntity?> =
        combine(profiles, state.currentId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val currentId: Flow<Long> = current.filterNotNull().map { it.id }.distinctUntilChanged()

    /** Identifiant synchrone du profil courant (profil Principal tant que la base n'a pas répondu). */
    val currentIdNow: Long get() = current.value?.id ?: DEFAULT_PROFILE_ID

    /** Préférences surchargées du profil courant. */
    val currentPrefs: Flow<ProfilePrefs> = currentId.flatMapLatest { id ->
        dao.observePrefs(id).map { rows -> ProfilePrefs.fromMap(rows.associate { it.key to it.value }) }
    }

    /** Catégories à masquer pour le profil courant : choix de l'utilisateur + adultes si profil Enfants. */
    val hiddenCategoryKeys: Flow<Set<String>> = currentId.flatMapLatest { id ->
        combine(dao.observeHidden(id), current, dao.observeCategoryNames()) { chosen, p, cats ->
            if (KidsRules.forcesParentalControl(p)) chosen.toSet() + KidsRules.adultCategoryKeys(cats) else chosen.toSet()
        }
    }

    /** Sources visibles du profil courant (null = toutes). */
    val currentSources: Flow<List<String>?> = currentPrefs.map { it.sources }.distinctUntilChanged()

    /** Recherche dans toutes les sources visibles du profil courant. */
    val currentSearchAll: Flow<Boolean> = currentPrefs.map { it.searchAllSources == true }.distinctUntilChanged()

    /** Écran Favoris du profil courant : favoris de toutes les sources visibles réunis (choix mémorisé par profil). */
    val currentFavAll: Flow<Boolean> = currentPrefs.map { it.favAllSources == true }.distinctUntilChanged()

    /** Espace Sport du profil courant : ligues (ordre et masquage, codage de LeaguePrefs) et mode sans spoiler. */
    val currentSportLeagues: Flow<String?> = currentPrefs.map { it.sportLeagues }.distinctUntilChanged()
    val currentSportNoSpoiler: Flow<Boolean> = currentPrefs.map { it.sportNoSpoiler == true }.distinctUntilChanged()

    val startupMode: Flow<StartupMode> = state.startupMode

    private val chosenThisSession = MutableStateFlow(false)
    private val forced = MutableStateFlow(false)

    /**
     * Vrai tant que « Qui regarde ? » doit être montré : au démarrage (plusieurs profils, mode « Toujours demander »)
     * ou sur demande explicite (« Changer de profil »). Null tant que la base n'a pas répondu.
     */
    val needsSelection: Flow<Boolean?> = combine(profiles, state.startupMode, chosenThisSession, forced) { l, m, chosen, f ->
        if (l.isEmpty()) null else f || (!chosen && StartupRules.shouldAsk(l.size, m))
    }

    suspend fun setStartupMode(m: StartupMode) = state.setStartupMode(m)

    /** Choisit le profil et marque la sélection de la session comme faite. */
    suspend fun select(id: Long) {
        // Projections du profil (chaînes masquées, ordre des catégories) AVANT le changement : les listes ne montrent jamais
        // un instant les réglages de l'autre profil.
        runCatching { hook?.onSwitch(id) }
        state.setCurrentId(id)
        chosenThisSession.value = true
        forced.value = false
    }

    /** Change de profil en cours de session (« Changer de profil » depuis le rail). */
    fun requestSwitch() { forced.value = true }

    /** Retour sur « Qui regarde ? » : on reste sur le profil courant au lieu de quitter l'application. */
    fun cancelSwitch() { chosenThisSession.value = true; forced.value = false }

    suspend fun create(name: String, color: Int, isKids: Boolean = false, pin: String? = null): Long? {
        if (!ProfileRules.canAdd(dao.count())) return null
        val n = ProfileRules.cleanName(name).ifBlank { return null }
        log?.touch(n)
        return dao.insert(
            ProfileEntity(
                name = n, color = color, initial = ProfileRules.initialOf(n), isKids = isKids,
                pinHash = pin?.takeIf { it.isNotBlank() }?.let(ProfileRules::hashPin),
            ),
        ).also { profileCreated(it) }
    }

    suspend fun rename(id: Long, name: String) {
        val n = ProfileRules.cleanName(name).ifBlank { return }
        val p = dao.byId(id) ?: return
        if (ProfileSyncLogic.key(p.name) != ProfileSyncLogic.key(n)) { log?.tombstone(p.name); log?.touch(n, from = p.name) }
        else log?.touch(n)
        dao.update(p.copy(name = n, initial = ProfileRules.initialOf(n)))
    }

    suspend fun setColor(id: Long, color: Int) { dao.byId(id)?.let { log?.touch(it.name); dao.update(it.copy(color = color)) } }
    suspend fun setKids(id: Long, kids: Boolean) { dao.byId(id)?.let { log?.touch(it.name); dao.update(it.copy(isKids = kids)) } }

    /** PIN du profil ; null ou vide = retire la protection. */
    suspend fun setPin(id: Long, pin: String?) {
        dao.byId(id)?.let { dao.update(it.copy(pinHash = pin?.takeIf { s -> s.isNotBlank() }?.let(ProfileRules::hashPin))) }
    }

    suspend fun verifyPin(id: Long, typed: String): Boolean = dao.byId(id)?.let { ProfileRules.pinMatches(it, typed) } ?: false

    /** Supprime un profil et ses données. Refuse (false) pour le dernier profil. */
    suspend fun delete(id: Long): Boolean {
        if (!ProfileRules.canDelete(dao.count())) return false
        dao.byId(id)?.let { log?.tombstone(it.name) }
        dao.deleteFavoritesOf(id); dao.deleteHistoryOf(id); dao.deletePrefsOf(id); dao.deleteHiddenOf(id); dao.deleteChannelOrderOf(id)
        dao.deleteHiddenChannelsOf(id); dao.deleteCategoryOrderOf(id)
        dao.deleteProfile(id)
        val remaining = dao.observeAllOnce()
        if (current.value?.id == id) remaining.firstOrNull()?.let { runCatching { hook?.onSwitch(it.id) }; state.setCurrentId(it.id) }
        // Moins de profils : des chaînes peuvent être devenues masquées par TOUS les autres.
        runCatching { hook?.onProfilesChanged() }
        return true
    }

    // ── Préférences et catégories masquées du profil courant ──
    suspend fun putPref(key: String, value: String) {
        dao.putPref(ProfilePrefEntity(currentIdNow, key, value))
        // Langue du profil : partagée entre appareils (le profil est marqué modifié pour que ce choix gagne ailleurs).
        if (key == ProfilePrefs.UI_LANGUAGE) current.value?.let { log?.touch(it.name) }
    }
    suspend fun clearPref(key: String) = dao.removePref(currentIdNow, key)

    /** Profils en mode simple (pour la liste de Réglages › Profils). */
    val simpleModeProfileIds: Flow<Set<Long>> = dao.observeSimpleModeProfiles().map { it.toSet() }
    /** Mode simple d'un profil précis (Réglages › Profils), pas seulement du courant. */
    suspend fun setSimpleModeFor(profileId: Long, on: Boolean) =
        dao.putPref(ProfilePrefEntity(profileId, ProfilePrefs.SIMPLE_MODE, on.toString()))

    /** Règles de temps d'écran (texte codé) par profil. */
    val screenTimeById: Flow<Map<Long, String>> = dao.observeScreenTimeRows().map { rows -> rows.associate { it.profileId to it.value } }

    /** Règles de temps d'écran d'un profil précis (Réglages › Profils) ; texte vide = règles retirées. Le profil est marqué modifié : le choix gagne sur les autres appareils. */
    suspend fun setScreenTimeFor(profileId: Long, encoded: String) {
        if (encoded.isBlank()) dao.removePref(profileId, ProfilePrefs.SCREEN_TIME)
        else dao.putPref(ProfilePrefEntity(profileId, ProfilePrefs.SCREEN_TIME, encoded))
        dao.byId(profileId)?.let { log?.touch(it.name) }
    }

    /** Sources visibles d'un profil précis ; null ou vide = toutes. Le profil est marqué modifié : le choix gagne sur les autres appareils. */
    suspend fun setSourcesFor(profileId: Long, names: List<String>?) {
        val clean = names?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()?.takeIf { it.isNotEmpty() }
        dao.byId(profileId)?.let { log?.touch(it.name) }
        if (clean == null) dao.removePref(profileId, ProfilePrefs.SOURCES)
        else dao.putPref(ProfilePrefEntity(profileId, ProfilePrefs.SOURCES, ProfileSourcesRules.encode(clean)))
    }

    suspend fun setSearchAll(on: Boolean) = putPref(ProfilePrefs.SEARCH_ALL, on.toString())

    suspend fun setFavAll(on: Boolean) = putPref(ProfilePrefs.FAV_ALL, on.toString())

    suspend fun setSportLeagues(encoded: String?) {
        if (encoded.isNullOrEmpty()) dao.removePref(currentIdNow, ProfilePrefs.SPORT_LEAGUES) else putPref(ProfilePrefs.SPORT_LEAGUES, encoded)
    }
    suspend fun setSportNoSpoiler(on: Boolean) = putPref(ProfilePrefs.SPORT_NO_SPOILER, on.toString())

    suspend fun sourcesOf(profileId: Long): List<String>? =
        dao.observePrefs(profileId).first().firstOrNull { it.key == ProfilePrefs.SOURCES }?.value?.let(ProfileSourcesRules::decode)

    suspend fun setCategoryHidden(key: String, hidden: Boolean) {
        if (hidden) dao.hide(ProfileHiddenCategoryEntity(currentIdNow, key)) else dao.unhide(currentIdNow, key)
    }
    suspend fun setCategoryHiddenFor(profileId: Long, key: String, hidden: Boolean) {
        if (hidden) dao.hide(ProfileHiddenCategoryEntity(profileId, key)) else dao.unhide(profileId, key)
    }
    suspend fun clearHiddenCategories() = dao.clearHidden(currentIdNow)
}

private suspend fun ProfileDao.observeAllOnce(): List<ProfileEntity> = observeAll().first()

/**
 * Point d'entrée des autres modules pour tout ce qui est PAR PROFIL.
 * `profileScope.flow { pid -> dao.observeX(pid) }` se ré-abonne tout seul au changement de profil.
 */
@Singleton
class ProfileScope @Inject constructor(private val repo: ProfileRepository) {
    val id: Flow<Long> get() = repo.currentId
    val idNow: Long get() = repo.currentIdNow
    val prefs: Flow<ProfilePrefs> get() = repo.currentPrefs
    fun <T> flow(block: (profileId: Long) -> Flow<T>): Flow<T> = repo.currentId.flatMapLatest(block)
}

/** Accès pour la synchro (création / mise à jour reçues d'un autre appareil, sans réécrire le journal à la date du jour). */
suspend fun ProfileRepository.applySharedProfiles(dao: ProfileDao, log: ProfileSyncLog, actions: List<ProfileSyncLogic.Action>) {
    for (a in actions) when (a) {
        is ProfileSyncLogic.Action.Create -> if (ProfileRules.canAdd(dao.count())) {
            val id = dao.insert(ProfileEntity(name = a.p.n, color = a.p.c, initial = ProfileRules.initialOf(a.p.n), isKids = a.p.k))
            a.p.l?.let { dao.putPref(ProfilePrefEntity(id, ProfilePrefs.UI_LANGUAGE, it)) }
            a.p.s?.let { dao.putPref(ProfilePrefEntity(id, ProfilePrefs.SOURCES, ProfileSourcesRules.encode(it))) }
            applySharedScreenTime(dao, id, a.p.t)
            log.set(a.p.n, a.p.at, true)
            profileCreated(id)
        }
        is ProfileSyncLogic.Action.Update -> dao.byId(a.id)?.let {
            dao.update(it.copy(color = a.p.c, isKids = a.p.k))
            a.p.l?.let { l -> dao.putPref(ProfilePrefEntity(a.id, ProfilePrefs.UI_LANGUAGE, l)) }
            applySharedSources(dao, a.id, a.p.s)
            applySharedScreenTime(dao, a.id, a.p.t)
            log.set(a.p.n, a.p.at, true)
        }
        is ProfileSyncLogic.Action.Delete -> if (delete(a.id)) log.set(a.p.n, a.p.at, false)
        is ProfileSyncLogic.Action.Rename -> dao.byId(a.id)?.let { old ->
            dao.update(old.copy(name = a.p.n, initial = ProfileRules.initialOf(a.p.n), color = a.p.c, isKids = a.p.k))
            a.p.l?.let { l -> dao.putPref(ProfilePrefEntity(a.id, ProfilePrefs.UI_LANGUAGE, l)) }
            applySharedSources(dao, a.id, a.p.s)
            applySharedScreenTime(dao, a.id, a.p.t)
            log.set(old.name, a.p.at, false); log.set(a.p.n, a.p.at, true, a.p.from)
        }
    }
}

/** Sources visibles reçues : absentes (null) = toutes, le choix local est donc retiré. */
private suspend fun applySharedSources(dao: ProfileDao, profileId: Long, names: List<String>?) {
    if (names.isNullOrEmpty()) dao.removePref(profileId, ProfilePrefs.SOURCES)
    else dao.putPref(ProfilePrefEntity(profileId, ProfilePrefs.SOURCES, ProfileSourcesRules.encode(names)))
}

/** Règles de temps d'écran reçues : normalisées (jamais de texte brut du réseau tel quel) ; null = champ absent, rien à changer. */
private suspend fun applySharedScreenTime(dao: ProfileDao, profileId: Long, t: String?) {
    if (t == null) return
    val clean = com.ultratv.tv.nativeapp.data.screentime.ScreenTime.encode(com.ultratv.tv.nativeapp.data.screentime.ScreenTime.decode(t))
    if (clean.isEmpty()) dao.removePref(profileId, ProfilePrefs.SCREEN_TIME) else dao.putPref(ProfilePrefEntity(profileId, ProfilePrefs.SCREEN_TIME, clean))
}
