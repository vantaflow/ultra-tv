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
) {
    @Inject constructor(dao: ProfileDao, state: ProfileStateStore) :
        this(dao, state, CoroutineScope(SupervisorJob() + Dispatchers.Default))

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
        return dao.insert(
            ProfileEntity(
                name = n, color = color, initial = ProfileRules.initialOf(n), isKids = isKids,
                pinHash = pin?.takeIf { it.isNotBlank() }?.let(ProfileRules::hashPin),
            ),
        )
    }

    suspend fun rename(id: Long, name: String) {
        val n = ProfileRules.cleanName(name).ifBlank { return }
        val p = dao.byId(id) ?: return
        dao.update(p.copy(name = n, initial = ProfileRules.initialOf(n)))
    }

    suspend fun setColor(id: Long, color: Int) { dao.byId(id)?.let { dao.update(it.copy(color = color)) } }
    suspend fun setKids(id: Long, kids: Boolean) { dao.byId(id)?.let { dao.update(it.copy(isKids = kids)) } }

    /** PIN du profil ; null ou vide = retire la protection. */
    suspend fun setPin(id: Long, pin: String?) {
        dao.byId(id)?.let { dao.update(it.copy(pinHash = pin?.takeIf { s -> s.isNotBlank() }?.let(ProfileRules::hashPin))) }
    }

    suspend fun verifyPin(id: Long, typed: String): Boolean = dao.byId(id)?.let { ProfileRules.pinMatches(it, typed) } ?: false

    /** Supprime un profil et ses données. Refuse (false) pour le dernier profil. */
    suspend fun delete(id: Long): Boolean {
        if (!ProfileRules.canDelete(dao.count())) return false
        dao.deleteFavoritesOf(id); dao.deleteHistoryOf(id); dao.deletePrefsOf(id); dao.deleteHiddenOf(id)
        dao.deleteProfile(id)
        val remaining = dao.observeAllOnce()
        if (current.value?.id == id) remaining.firstOrNull()?.let { state.setCurrentId(it.id) }
        return true
    }

    // ── Préférences et catégories masquées du profil courant ──
    suspend fun putPref(key: String, value: String) = dao.putPref(ProfilePrefEntity(currentIdNow, key, value))
    suspend fun clearPref(key: String) = dao.removePref(currentIdNow, key)

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
