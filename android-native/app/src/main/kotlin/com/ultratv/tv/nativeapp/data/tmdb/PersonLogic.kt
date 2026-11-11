package com.ultratv.tv.nativeapp.data.tmdb

import com.ultratv.tv.nativeapp.data.repo.FuzzySearch
import org.json.JSONObject

/** Une œuvre de la filmographie TMDB d'une personne (acteur ou équipe). */
data class PersonCredit(val isTv: Boolean, val title: String, val originalTitle: String?, val year: Int?)

/**
 * Pages acteurs / réalisateurs : lecture des réponses TMDB et rapprochement avec la playlist. Logique pure, testée à part.
 * Un titre de la playlist correspond quand son nom normalisé est celui de l'œuvre (titre ou titre original) et que
 * l'année, si les deux sont connues, est à ±1 an.
 */
/** Fiche TMDB d'une personne. [department] : « Acting », « Directing »… (métier principal, en anglais). */
data class PersonInfo(val name: String, val department: String?, val birthday: String?, val place: String?, val bio: String?, val photo: String?)

object PersonLogic {
    /** Fiche d'une personne d'après `person/{id}` ; null si la réponse est illisible. [bio] vide = biographie absente dans cette langue. */
    fun info(json: String): PersonInfo? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        fun str(k: String) = o.optString(k).trim().takeIf { it.isNotEmpty() && it != "null" }
        val name = str("name") ?: return null
        return PersonInfo(name, str("known_for_department"), str("birthday"), str("place_of_birth"), str("biography"), str("profile_path")?.takeIf { it.startsWith("/") })
    }

    /** Date de naissance « 30.07.1970 » d'après « 1970-07-30 » ; une date partielle (« 1970-07 », « 1970 ») reste partielle ; null si illisible. */
    fun birthdayLabel(iso: String?): String? {
        val p = iso?.trim()?.split('-') ?: return null
        val y = p.getOrNull(0)?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: return null
        val m = p.getOrNull(1)?.takeIf { it.length == 2 && it.toIntOrNull() in 1..12 }
        val d = p.getOrNull(2)?.takeIf { it.length == 2 && it.toIntOrNull() in 1..31 }
        return when {
            m == null -> y
            d == null -> "$m.$y"
            else -> "$d.$m.$y"
        }
    }

    /** Identifiant de la personne dont le nom correspond le mieux à [name] (premier résultat à nom identique, sinon le premier). */
    fun searchBest(json: String, name: String): Int? {
        val arr = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return null
        val want = key(name)
        var first: Int? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optInt("id", 0).takeIf { it > 0 } ?: continue
            if (first == null) first = id
            if (key(o.optString("name")) == want) return id
        }
        return first
    }

    /** Filmographie (rôles et postes confondus), sans doublon, la plus récente d'abord. */
    fun credits(json: String): List<PersonCredit> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, PersonCredit>()
        for (part in listOf("cast", "crew")) {
            val arr = root.optJSONArray(part) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val tv = o.optString("media_type") == "tv"
                val title = o.optString(if (tv) "name" else "title").trim()
                if (title.isEmpty()) continue
                val original = o.optString(if (tv) "original_name" else "original_title").trim().takeIf { it.isNotEmpty() && it != title }
                val year = o.optString(if (tv) "first_air_date" else "release_date").take(4).toIntOrNull()
                out.putIfAbsent("${if (tv) "t" else "m"}|${key(title)}|${year ?: ""}", PersonCredit(tv, title, original, year))
            }
        }
        return out.values.sortedByDescending { it.year ?: 0 }
    }

    /** Clé de comparaison des titres : minuscules, sans accents ni ponctuation. */
    fun key(s: String): String = FuzzySearch.tokens(s).joinToString(" ")

    /** Le titre de la playlist ([title], [year]) est-il cette œuvre ? */
    fun matches(c: PersonCredit, title: String, year: Int?): Boolean {
        val k = key(title)
        if (k.isEmpty() || (k != key(c.title) && (c.originalTitle == null || k != key(c.originalTitle)))) return false
        return year == null || c.year == null || kotlin.math.abs(year - c.year) <= 1
    }
}
