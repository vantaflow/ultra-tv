package com.ultratv.tv.nativeapp.update

/**
 * Résumé des nouveautés affiché dans l'invite de mise à jour : les points des rubriques visibles par l'utilisateur
 * (Nouveau, Ajouté, Amélioré, Corrigé) des notes de la release, tirées du CHANGELOG. Les rubriques techniques
 * (Technique, Worker, Licences, Signature, Maintenance) et le lien « Full Changelog » sont ignorés.
 */
object UpdateNotes {
    private val USER_SECTIONS = listOf("nouveau", "ajouté", "amélior", "corrigé", "new", "added", "improved", "fixed")
    private const val MAX_CHARS = 110

    fun summary(notes: String, max: Int = 4): List<String> {
        val out = mutableListOf<String>()
        var inUserSection = false
        var current: StringBuilder? = null
        fun flush() { current?.let { out += clean(it.toString()) }; current = null }
        for (raw in notes.lines()) {
            val line = raw.trimEnd()
            when {
                line.startsWith("## ") && out.isNotEmpty() -> break          // version suivante : on s'arrête
                line.startsWith("#") -> { flush(); inUserSection = USER_SECTIONS.any { line.lowercase().contains(it) } }
                !inUserSection -> {}
                line.startsWith("- ") || line.startsWith("* ") -> { flush(); current = StringBuilder(line.drop(2)) }
                line.startsWith("  ") && current != null -> current!!.append(' ').append(line.trim())
                else -> flush()
            }
        }
        flush()
        return out.filter { it.isNotBlank() }.take(max)
    }

    /** Markdown retiré (gras, code, liens), coupé à la première phrase utile et à [MAX_CHARS] caractères. */
    internal fun clean(s: String): String {
        var t = s.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1").replace("**", "").replace("`", "").trim()
        val cut = t.indexOf(" : ").takeIf { it in 12..MAX_CHARS }
        if (t.length > MAX_CHARS && cut != null) t = t.substring(0, cut)
        if (t.length > MAX_CHARS) t = t.substring(0, MAX_CHARS).substringBeforeLast(' ') + "…"
        return t.trimEnd('.', ';', ',')
    }
}
