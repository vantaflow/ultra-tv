package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.CatCountRow
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import com.ultratv.tv.nativeapp.data.db.UltraDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** Façon de numéroter les chaînes (réglage par profil). */
enum class ChannelNumberMode(val key: String) {
    /** Comportement historique : séquentiel stable de la catégorie, sinon numéro de la source. */
    AUTO("auto"),
    /** Numéro fourni par le fournisseur (`num`), tel quel. */
    SOURCE("source"),
    /** 1, 2, 3… sur toutes les catégories visibles du profil, dans l'ordre des catégories. */
    CONTINUOUS("continuous"),
    ;

    companion object {
        fun parse(key: String?): ChannelNumberMode = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** Tri des chaînes du Direct (réglage par profil). */
enum class ChannelSort(val key: String) {
    /** Ordre de la source (comportement historique). */
    SOURCE("source"),
    /** Par numéro affiché. */
    NUMBER("number"),
    /** A → Z, sans tenir compte de la casse ni des accents (clé `sortKey`). */
    AZ("az"),
    /** Dernières regardées en premier (historique LIVE du profil), les autres à la suite. */
    RECENT("recent"),
    ;

    /** Valeur passée aux requêtes SQL triées (0 = aucun tri : requêtes d'origine). */
    val sql: Int get() = when (this) { SOURCE -> 0; NUMBER -> 1; AZ -> 2; RECENT -> 3 }

    companion object {
        fun parse(key: String?): ChannelSort = entries.firstOrNull { it.key == key } ?: SOURCE
    }
}

/** Une catégorie visible du Direct pour la numérotation continue : [start] = nombre de chaînes visibles des catégories qui la précèdent. */
data class CategorySpan(val categoryId: String?, val start: Int, val count: Int)

/**
 * Numérotation d'affichage PAR PROFIL (logique pure). `channel.seq` reste GLOBAL (une chaîne masquée par un seul profil
 * garde son numéro en base) ; pour le profil courant, le numéro affiché d'une chaîne est
 * `seq − (nombre de chaînes masquées par ce profil, dans la même catégorie, dont le seq est plus petit)`.
 * Masquer 1 et 2 fait donc de la 3 la chaîne 1, sans trou, comme quand tous les profils les masquent.
 *
 * [hidden] : (source, catégorie) → seq masqués par le profil, triés et sans doublon. Minuscule (seulement les chaînes
 * masquées) ; vide pour un profil qui ne masque rien.
 */
class ChannelNumbering(
    private val hidden: Map<Key, IntArray> = emptyMap(),
    /** Mode de numérotation du profil. */
    val mode: ChannelNumberMode = ChannelNumberMode.AUTO,
    /** Mode continu : catégories visibles de chaque source, dans l'ordre d'affichage (vide sinon). */
    private val spans: Map<Long, List<CategorySpan>> = emptyMap(),
) {
    data class Key(val providerId: Long, val categoryId: String?)

    /** Vrai si les numéros sont ceux d'origine (mode automatique, aucune chaîne masquée par le profil). */
    val isIdentity: Boolean get() = hidden.isEmpty() && mode == ChannelNumberMode.AUTO

    /** Numéro affiché d'un [seq] (> 0) de la catégorie ; seq <= 0 inchangé. */
    fun shown(providerId: Long, categoryId: String?, seq: Int): Int {
        if (seq <= 0 || hidden.isEmpty()) return seq
        val arr = hidden[Key(providerId, categoryId)] ?: return seq
        return seq - countBelow(arr, seq)
    }

    /**
     * Numéro d'affichage d'une chaîne : séquentiel du profil, sinon celui de la source (`num`, jamais décalé : il vient
     * du fournisseur) ; 0 si inconnu.
     */
    fun numberOf(c: ChannelEntity): Int = when (mode) {
        ChannelNumberMode.SOURCE -> c.num.coerceAtLeast(0)
        ChannelNumberMode.CONTINUOUS -> if (c.seq > 0) continuous(c) else c.num.coerceAtLeast(0)
        ChannelNumberMode.AUTO -> if (c.seq > 0) shown(c.providerId, c.categoryId, c.seq) else c.num.coerceAtLeast(0)
    }

    private fun continuous(c: ChannelEntity): Int {
        val inCat = shown(c.providerId, c.categoryId, c.seq)
        val start = spans[c.providerId]?.firstOrNull { it.categoryId == c.categoryId }?.start
            ?: return inCat   // catégorie masquée / inconnue : numéro de la catégorie
        return start + inCat
    }

    /**
     * Mode continu : (catégorie, numéro dans la catégorie) de la chaîne qui porte [number] dans la source [providerId], ou
     * null si hors plage. Le numéro dans la catégorie se convertit en seq global avec [seqOf].
     */
    fun locateContinuous(providerId: Long, number: Int): Pair<String?, Int>? {
        if (mode != ChannelNumberMode.CONTINUOUS || number <= 0) return null
        val sp = spans[providerId]?.firstOrNull { number > it.start && number <= it.start + it.count } ?: return null
        return sp.categoryId to (number - sp.start)
    }

    /**
     * Inverse : le seq (global) de la chaîne qui affiche [number] dans la catégorie, ou null si [number] <= 0.
     * Point fixe `seq = number + masqués avant seq` : on avance sur les seq masqués triés. Le résultat n'est jamais un seq masqué.
     */
    fun seqOf(providerId: Long, categoryId: String?, number: Int): Int? {
        if (number <= 0) return null
        val arr = hidden[Key(providerId, categoryId)] ?: return number
        var s = number
        for (h in arr) { if (h <= s) s++ else break }
        return s
    }

    private fun countBelow(arr: IntArray, seq: Int): Int {   // nombre d'éléments < seq (recherche dichotomique)
        var lo = 0
        var hi = arr.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (arr[mid] < seq) lo = mid + 1 else hi = mid }
        return lo
    }

    companion object {
        val NONE = ChannelNumbering()

        /** Construit depuis les lignes (source, catégorie, seq) des chaînes masquées ; seq <= 0 ignorés. */
        fun of(rows: List<Triple<Long, String?, Int>>, mode: ChannelNumberMode = ChannelNumberMode.AUTO, spans: Map<Long, List<CategorySpan>> = emptyMap()): ChannelNumbering {
            val m = rows.filter { it.third > 0 }.groupBy({ Key(it.first, it.second) }, { it.third })
                .mapValues { (_, v) -> v.distinct().sorted().toIntArray() }
            return if (m.isEmpty() && mode == ChannelNumberMode.AUTO) NONE else ChannelNumbering(m, mode, if (mode == ChannelNumberMode.CONTINUOUS) spans else emptyMap())
        }

        /**
         * Catégories visibles (lignes déjà triées dans l'ordre d'affichage : source, rang, catégorie ; [CatCountRow.n] =
         * chaînes numérotées non masquées) → plages de la numérotation continue. Les catégories vides ou masquées sont ignorées.
         */
        fun spansOf(rows: List<CatCountRow>, hiddenCategories: Set<String> = emptySet()): Map<Long, List<CategorySpan>> {
            val out = HashMap<Long, MutableList<CategorySpan>>()
            val running = HashMap<Long, Int>()
            for (r in rows) {
                if (r.n <= 0 || (r.categoryId != null && "LIVE:${r.providerId}:${r.categoryId}" in hiddenCategories)) continue
                val start = running[r.providerId] ?: 0
                out.getOrPut(r.providerId) { ArrayList() }.add(CategorySpan(r.categoryId, start, r.n))
                running[r.providerId] = start + r.n
            }
            return out
        }
    }
}

/**
 * Numérotation du profil COURANT, tenue à jour d'après `channel_hidden_now` (la projection du profil) : le Flow Room se
 * relance au masquage, à l'affichage et au changement de profil (la projection est réécrite). Une seule requête, limitée aux
 * chaînes masquées : rien par ligne, quelques octets en mémoire. En mode continu, une seconde requête (nombre de chaînes par
 * catégorie, relue après une pause de 500 ms pour ne pas suivre chaque lot de synchro) donne les plages de chaque catégorie.
 */
@Singleton
class ProfileNumbering @Inject constructor(
    private val db: UltraDb,
    private val prefs: com.ultratv.tv.nativeapp.data.prefs.UserPreferencesStore,
    private val hiddenCategories: com.ultratv.tv.nativeapp.data.prefs.HiddenCategoriesStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val state: StateFlow<ChannelNumbering> by lazy {
        val mode = prefs.flow.map { ChannelNumberMode.parse(it.channelNumbering) }.distinctUntilChanged()
        val spans = mode.flatMapLatest { m ->
            if (m != ChannelNumberMode.CONTINUOUS) flowOf(emptyMap())
            else combine(db.profileViewDao().observeCategoryCounts().debounce(500), hiddenCategories.hidden) { rows, hid -> ChannelNumbering.spansOf(rows, hid) }
        }
        combine(db.profileViewDao().observeHiddenSeqs(), mode, spans) { rows, m, sp ->
            ChannelNumbering.of(rows.map { Triple(it.providerId, it.categoryId, it.seq) }, m, sp)
        }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, ChannelNumbering.NONE)
    }

    /** Numérotation actuelle (lecture ponctuelle hors Compose). */
    val current: ChannelNumbering get() = state.value
}
