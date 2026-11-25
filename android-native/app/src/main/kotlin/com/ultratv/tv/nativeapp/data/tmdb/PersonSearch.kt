package com.ultratv.tv.nativeapp.data.tmdb

import com.ultratv.tv.nativeapp.data.repo.FuzzySearch
import com.ultratv.tv.nativeapp.data.repo.TitleCleaner
import org.json.JSONObject

/**
 * Recherche d'acteurs / réalisateurs par leur nom : correspondance insensible à la casse et aux accents (même pliage que la
 * recherche de titres), index des noms cités par les distributions locales, et fusion avec les noms trouvés chez TMDB.
 * Logique pure, testée à part ; l'accès aux bases et au Worker est dans [com.ultratv.tv.nativeapp.data.repo.PeopleSearch].
 */
object PersonSearch {
    /** Où une personne est citée : source, genre de titre (« M » film, « S » série) et catégorie du titre. */
    data class Occ(val pid: Long, val kind: Char, val category: String?)

    /** Une personne de l'index : nom d'affichage, mots pliés et nombre de titres par lieu de citation. */
    class Entry(val name: String, val key: String, val tokens: List<String>, val occ: Map<Occ, Int>)

    class Index(val entries: List<Entry>)

    /** Longueur minimale de la saisie (lettres) avant de chercher une personne : moins, presque tout nom correspondrait. */
    const val MIN_QUERY = 3

    private val SPLIT = Regex("[,;|/\\n]+")

    /** Noms d'une cellule de distribution (« Tom Hanks, Tim Allen ») : séparés, nettoyés, sans valeurs vides ni parasites. */
    fun names(cell: String?): List<String> {
        val raw = TitleCleaner.presentable(cell) ?: return emptyList()
        return raw.split(SPLIT).map { it.trim().trim('.', '-', ' ') }
            .filter { n -> n.length in 3..60 && n.any(Char::isLetter) && n.count { it == ' ' } <= 5 && TitleCleaner.presentable(n) != null }
    }

    /** Index des noms d'après des lignes de distribution (chaque ligne : d'où elle vient et son texte). */
    fun build(rows: List<Pair<Occ, String>>): Index {
        class Acc(val name: String, val tokens: List<String>) { val occ = HashMap<Occ, Int>() }
        val acc = LinkedHashMap<String, Acc>()
        for ((occ, cell) in rows) {
            for (n in names(cell).distinct()) {
                val tokens = FuzzySearch.tokens(n)
                if (tokens.isEmpty()) continue
                val a = acc.getOrPut(tokens.joinToString(" ")) { Acc(n, tokens) }
                a.occ.merge(occ, 1, Int::plus)
            }
        }
        return Index(acc.map { (k, a) -> Entry(a.name, k, a.tokens, a.occ) })
    }

    /** Le nom [name] correspond-il à la saisie : chaque mot saisi est le début d'un mot du nom (casse et accents ignorés) ? */
    fun matches(name: String, query: String): Boolean = score(FuzzySearch.tokens(name), FuzzySearch.tokens(query)) > 0

    /** 0 = ne correspond pas ; 3 = nom identique ; 2 = commence par la saisie ; 1 = les mots saisis débutent des mots du nom. */
    internal fun score(nameTokens: List<String>, queryTokens: List<String>): Int {
        if (queryTokens.isEmpty() || nameTokens.isEmpty() || queryTokens.sumOf { it.length } < MIN_QUERY) return 0
        if (nameTokens == queryTokens) return 3
        val free = nameTokens.toMutableList()
        for (qt in queryTokens) {
            val i = free.indexOfFirst { it.startsWith(qt) }
            if (i < 0) return 0
            free.removeAt(i)
        }
        return if (nameTokens.size >= queryTokens.size && queryTokens.indices.all { nameTokens[it].startsWith(queryTokens[it]) }) 2 else 1
    }

    /** Une personne trouvée, la qualité de la correspondance (voir [score]) et son nombre de titres visibles. */
    data class Hit(val name: String, val score: Int, val titles: Int)

    /**
     * Personnes de l'index qui correspondent à la saisie, les mieux classées d'abord : correspondance, puis nombre de titres
     * visibles, puis ordre alphabétique. [visible] écarte les citations d'un titre masqué (profil Enfants, catégorie
     * verrouillée) ; une personne sans aucune citation visible n'est pas rendue.
     */
    fun search(index: Index, query: String, limit: Int, visible: (Occ) -> Boolean = { true }): List<Hit> {
        val qt = FuzzySearch.tokens(query)
        if (qt.isEmpty()) return emptyList()
        val out = ArrayList<Hit>()
        for (e in index.entries) {
            val s = score(e.tokens, qt)
            if (s == 0) continue
            val titles = e.occ.entries.sumOf { (o, n) -> if (visible(o)) n else 0 }
            if (titles > 0) out += Hit(e.name, s, titles)
        }
        return out.sortedWith(compareByDescending<Hit> { it.score }.thenByDescending { it.titles }.thenBy { it.name.lowercase() }).take(limit)
    }

    /** Fusion des sources : les noms locaux d'abord (présents dans la playlist), puis ceux de TMDB, sans doublon (casse et accents ignorés). */
    fun merge(local: List<String>, remote: List<String>, limit: Int): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (n in local + remote) {
            val k = PersonLogic.key(n)
            if (k.isNotEmpty() && seen.add(k)) out += n
            if (out.size >= limit) break
        }
        return out
    }

    /** Personnes d'une réponse TMDB `search/person` : (identifiant, nom), dans l'ordre de TMDB. */
    fun results(json: String): List<Pair<Int, String>> {
        val arr = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return emptyList()
        val out = ArrayList<Pair<Int, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("id", 0).takeIf { it > 0 } ?: continue
            val name = o.optString("name").trim().takeIf { it.isNotEmpty() && it != "null" } ?: continue
            out += id to name
        }
        return out
    }
}
