package com.ultratv.tv.nativeapp.data.sport

/**
 * Discipline reconnue d'un programme (onglets de filtre de l'écran Sport). Détection par mots entiers dans le titre et la
 * description du guide de la source : jamais un service externe. Un programme sans discipline reconnue n'appartient à
 * aucun onglet (il reste visible sous « Tout »).
 */
enum class SportKind(private val words: Set<String>, private val phrases: List<String> = emptyList()) {
    FOOTBALL(
        setOf("football", "foot", "soccer", "futbol", "fussball", "calcio", "bundesliga", "uefa", "ekstraklasa"),
        listOf("ligue 1", "ligue 2", "liga", "serie a", "premier league", "ligue des champions", "champions league", "coupe du monde", "europa league", "pilka nozna", "كرة القدم"),
    ),
    BASKET(setOf("basket", "basketball", "nba", "baloncesto", "koszykowka", "euroleague"), listOf("كرة السلة")),
    TENNIS(setOf("tennis", "tenis", "atp", "wta", "roland", "wimbledon"), listOf("roland garros", "us open", "australian open")),
    COMBAT(setOf("boxe", "boxing", "boxeo", "ufc", "mma", "judo", "kickboxing"), listOf("arts martiaux", "kick boxing")),
    MOTOR(setOf("f1", "motogp", "nascar", "rallye", "rally", "formule", "formula", "automobilisme", "motocross"), listOf("moto gp", "grand prix")),
    RUGBY(setOf("rugby"), listOf("top 14", "six nations"));

    companion object {
        /** Discipline du texte (titre, compétition…) ou null. Premier onglet qui correspond, dans l'ordre de déclaration. */
        fun of(vararg texts: String?): SportKind? {
            val folded = SportDetector.fold(texts.filterNotNull().joinToString(" "))
            if (folded.isEmpty()) return null
            val tokens = folded.split(' ').toSet()
            val padded = " $folded "
            return SportKind.entries.firstOrNull { k -> k.words.any { it in tokens } || k.phrases.any { padded.contains(" " + SportDetector.fold(it) + " ") } }
        }

        /** Disciplines présentes dans [entries], dans l'ordre de déclaration (onglets proposés). */
        fun present(list: List<SportEntry>): List<SportKind> {
            val found = HashSet<SportKind>()
            for (e in list) of(e.prog.title, e.prog.description)?.let { found += it }
            return SportKind.entries.filter { it in found }
        }
    }
}

/** Titre de programme décomposé pour une carte : compétition éventuelle (« Ligue 1 : PSG - OM »), puis deux côtés ou le titre entier. */
data class MatchLine(val competition: String?, val home: String?, val away: String?, val title: String)

private val MATCH_SEPARATOR = Regex("\\s+(?:vs\\.?|v|contre|[-–—])\\s+", RegexOption.IGNORE_CASE)

/** Décompose le titre d'un programme de sport ; sans séparateur reconnu, [MatchLine.home] et [MatchLine.away] sont nuls. */
fun matchLineOf(rawTitle: String): MatchLine {
    val title = rawTitle.trim()
    var competition: String? = null
    var rest = title
    val colon = title.indexOf(':')
    if (colon in 3..30) {
        val tail = title.substring(colon + 1).trim()
        if (MATCH_SEPARATOR.containsMatchIn(tail)) { competition = title.substring(0, colon).trim(); rest = tail }
    }
    val m = MATCH_SEPARATOR.find(rest)
    if (m != null) {
        val home = rest.substring(0, m.range.first).trim()
        val away = rest.substring(m.range.last + 1).trim()
        if (home.isNotEmpty() && away.isNotEmpty()) return MatchLine(competition, home, away, title)
    }
    return MatchLine(null, null, null, title)
}
