package com.ultratv.tv.nativeapp.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catégories désactivées PAR LA LANGUE (et non à la main), clé `kind:providerId:remoteId`. Réactiver une langue ne
 * rouvre que celles-là : une catégorie coupée à la main reste coupée. Stocké à part de Room (pas de migration) ;
 * toute décision manuelle (écran Catégories) retire la catégorie de cette liste.
 */
@Singleton
class LangDisabledStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("lang_disabled_categories", Context.MODE_PRIVATE)
    private val lock = Any()

    private fun key(pid: Long, kind: String, id: String) = "$kind:$pid:$id"

    fun ids(pid: Long, kind: String): Set<String> {
        val prefix = "$kind:$pid:"
        return sp.getStringSet(SET, emptySet()).orEmpty().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
    }

    fun add(pid: Long, kind: String, ids: Collection<String>) = edit { s -> ids.forEach { s += key(pid, kind, it) } }

    fun remove(pid: Long, kind: String, ids: Collection<String>) = edit { s -> ids.forEach { s -= key(pid, kind, it) } }

    private inline fun edit(block: (MutableSet<String>) -> Unit) = synchronized(lock) {
        val s = HashSet(sp.getStringSet(SET, emptySet()).orEmpty())
        block(s)
        // commit : lecture immédiate cohérente (tests, enchaînement purge → réactivation).
        sp.edit().putStringSet(SET, s).commit()
    }

    private companion object { const val SET = "keys" }
}
