package com.ultratv.tv.nativeapp.data.repo

/**
 * Entrées « poubelle » des fournisseurs : séparateurs (« ----- », « ### », « ===== »), noms vides, noms purement
 * numériques (« 0 », « 007 », « 1 ») et événements ponctuels datés (« 01-18-2024 7:00pm »). Elles restent en base
 * mais sont masquées partout (listes, comptages, guide, recherche).
 */
object JunkFilter {
    private val datePattern = Regex("""\b\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}\b|\b\d{4}-\d{2}-\d{2}\b""")

    fun isJunk(rawName: String): Boolean {
        val t = rawName.trim()
        if (t.isEmpty()) return true
        if (t.none { it.isLetter() }) return true            // « ---- », « ### », « 0 », « 1 (2026-10-03 12:00:00) »
        if (datePattern.containsMatchIn(t) && t.count { it.isLetter() } <= 6) return true   // « 01-18-2024 7:00pm »
        return false
    }
}
