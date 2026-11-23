package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nouvelles catégories masquées d'office par une synchro (voir CatalogPlan.mergeDetailed), clé `kind:providerId:remoteId`.
 * Alimente la ligne discrète « N nouvelles catégories masquées » de Réglages › Catégories ; vidée quand l'utilisateur ouvre
 * la gestion des catégories depuis cette rubrique (il les a vues), ou pour une source supprimée. Stocké hors de Room
 * (pas de migration), comme [LangDisabledStore].
 */
@Singleton
class NewCategoriesStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("new_masked_categories", Context.MODE_PRIVATE)
    private val lock = Any()
    private val _keys = MutableStateFlow(sp.getStringSet(SET, emptySet()).orEmpty().toSet())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    fun add(pid: Long, kind: String, ids: Collection<String>) { if (ids.isNotEmpty()) edit { s -> ids.forEach { s += key(pid, kind, it) } } }

    /** Catégories vues : la ligne disparaît. */
    fun clear() = edit { it.clear() }

    fun forgetProvider(pid: Long) = edit { s -> s.removeAll { k -> k.split(':').getOrNull(1) == pid.toString() } }

    private inline fun edit(block: (MutableSet<String>) -> Unit) = synchronized(lock) {
        val s = HashSet(sp.getStringSet(SET, emptySet()).orEmpty())
        block(s)
        sp.edit().putStringSet(SET, s).commit()
        _keys.value = s.toSet()
    }

    companion object {
        private const val SET = "keys"
        fun key(pid: Long, kind: String, id: String) = "$kind:$pid:$id"
    }
}
