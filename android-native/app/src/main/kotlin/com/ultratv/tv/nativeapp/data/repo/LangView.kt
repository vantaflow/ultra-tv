package com.ultratv.tv.nativeapp.data.repo

/**
 * Filtre de langue TEMPORAIRE d'une vue (Direct, Films, Séries) : ensemble de codes de la colonne `lang`
 * (« fr », « ar », « MULTI », « » = non déterminé). `null` = aucun filtre. Traduit en paramètres SQL
 * (`:useLang`, `:langs`) ; la liste n'est jamais vide (SQLite refuse `IN ()` selon les versions).
 */
data class LangView(val selected: Set<String>?) {
    val useLang: Int get() = if (selected.isNullOrEmpty()) 0 else 1
    val langs: List<String> get() = selected?.toList()?.ifEmpty { listOf("") } ?: listOf("")

    fun toggle(code: String): LangView {
        val cur = selected ?: return LangView(setOf(code))
        val next = if (code in cur) cur - code else cur + code
        return LangView(next.ifEmpty { null })
    }

    /** Applique le même filtre à une liste déjà chargée (fenêtres de zapping). */
    fun <T> filter(items: List<T>, langOf: (T) -> String): List<T> =
        if (useLang == 0) items else items.filter { langOf(it) in selected!! }

    companion object { val ALL = LangView(null) }
}
