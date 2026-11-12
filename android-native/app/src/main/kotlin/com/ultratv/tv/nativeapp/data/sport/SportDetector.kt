package com.ultratv.tv.nativeapp.data.sport

import java.text.Normalizer
import java.util.Locale

/**
 * Détection du sport, pure (aucune dépendance Android) : programmes et chaînes de LA source de l'utilisateur, jamais un
 * service externe. Insensible à la casse et aux accents, mot entier (« Transport » n'est pas du sport), listes de mots
 * en fr / en / es / ar / pl. Les listes sont repliées UNE fois à l'initialisation ([fold]) avec la même fonction que le
 * texte testé : un mot accentué ou arabe s'écrit naturellement dans les listes.
 */
object SportDetector {
    private val NON_LETTER = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Minuscules, décomposition de compatibilité (lettres « fantaisie » des noms de chaînes), sans accents ni voyelles
     * arabes, quelques lettres sans décomposition (ł, ß, variantes de l'alef), tout le reste en espaces simples.
     */
    fun fold(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFKD).lowercase(Locale.ROOT)
        val sb = StringBuilder(n.length)
        for (ch in n) {
            when {
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
                ch == 'ـ' -> Unit                                   // tatweel
                ch == 'ł' -> sb.append('l')                         // ł
                ch == 'ß' -> sb.append("ss")                        // ß
                ch == 'ø' -> sb.append('o')                         // ø
                ch == 'أ' || ch == 'إ' || ch == 'آ' -> sb.append('ا')   // alef avec hamza / madda
                ch == 'ة' -> sb.append('ه')                    // ta marbouta
                ch == 'ى' -> sb.append('ي')                    // alef maqsoura
                else -> sb.append(ch)
            }
        }
        return NON_LETTER.replace(sb, " ").trim()
    }

    /** Mots d'un texte replié ; l'article arabe « ال » est retiré (« الدوري » = « دوري »). */
    internal fun tokens(folded: String): List<String> =
        if (folded.isEmpty()) emptyList()
        else folded.split(' ').map { stripArticle(it) }

    private fun stripArticle(t: String): String = if (t.length > 4 && t.startsWith("ال")) t.substring(2) else t

    private class Words(words: Collection<String>, prefixes: Collection<String> = emptyList(), phrases: Collection<String> = emptyList()) {
        val exact: Set<String> = words.map { stripArticle(fold(it)) }.toSet()
        val starts: List<String> = prefixes.map { fold(it) }
        val phr: List<String> = phrases.map { " " + fold(it) + " " }

        fun matches(folded: String, toks: List<String> = tokens(folded)): Boolean {
            if (folded.isEmpty()) return false
            for (t in toks) {
                if (t in exact) return true
                for (p in starts) if (t.startsWith(p)) return true
            }
            if (phr.isEmpty()) return false
            val padded = " $folded "
            for (p in phr) if (padded.contains(p)) return true
            return false
        }
    }

    // ── Disciplines ──
    private val DISCIPLINES = Words(
        words = listOf(
            "football", "foot", "soccer", "futbol", "fussball", "calcio", "basket", "nba", "nfl", "nhl", "mlb", "tennis", "tenis",
            "rugby", "f1", "motogp", "boxe", "boxing", "boxeo", "ufc", "mma", "cycling", "volley", "golf", "cricket", "baseball",
            "hockey", "nascar", "wta", "atp", "motocross", "snooker", "darts", "mecz", "meczu", "mecze", "pilka", "ekstraklasa",
            "koszykowka", "siatkowka", "zuzel", "baloncesto", "ciclismo", "balonmano", "automovilismo", "motociclismo", "automobilisme",
            "مباراة", "مباريات", "دوري", "كأس",
        ),
        prefixes = listOf("footbal", "basketbal", "handbal", "volleybal", "cyclis", "athleti", "gymnast", "fussbal", "natation", "swimming", "formul"),
        phrases = listOf(
            "moto gp", "moto 3", "moto2", "kick boxing", "arts martiaux", "pilka nozna", "pilka reczna", "skoki narciarskie", "tennis de table",
            "كرة القدم", "كرة السلة", "كرة اليد", "كرة الطائرة", "كرة المضرب",
        ),
    )

    // ── Compétitions ──
    private val COMPETITIONS = Words(
        words = listOf("bundesliga", "laliga", "eredivisie", "botola", "wimbledon", "afcon", "ucl", "uefa", "fifa", "olympics"),
        phrases = listOf(
            "premier league", "champions league", "europa league", "conference league", "la liga", "liga mx", "liga portugal", "serie a",
            "ligue 1", "ligue 2", "ligue des champions", "ligue europa", "coupe du monde", "world cup", "copa america", "copa libertadores",
            "copa del rey", "fa cup", "coupe de france", "carabao cup", "grand prix", "formula 1", "formule 1", "formula one", "tour de france",
            "roland garros", "us open", "australian open", "grand slam", "super bowl", "six nations", "top 14", "champions cup",
            "coupe d afrique", "africa cup", "jeux olympiques", "diamond league", "liga mistrzow", "puchar polski", "liga de campeones",
            "كأس العالم", "دوري أبطال", "الدوري الإسباني", "الدوري الإنجليزي", "الدوري الإيطالي",
        ),
    )

    // ── Mots génériques « sport » ──
    private val GENERIC = Words(
        words = listOf("sport", "sports", "spor", "deporte", "deportes", "deportivo", "deportiva", "sportif", "sportive", "sportifs", "sportowy", "sportowe", "رياضة", "رياضي", "رياضية", "رياضات"),
        prefixes = listOf("sport", "deport"),
    )

    /** Tournures qui écartent un programme (téléachat). Le mot entier suffit pour « transport », « support »… */
    private val NEGATIVE = Words(
        words = listOf("teleshopping", "teleachat", "televente"),
        phrases = listOf("tele achat", "tele shopping", "tele boutique", "home shopping"),
    )

    /** Mots d'équipes : clubs et sigles courants, assez distinctifs pour valider un « A - B » hors chaîne de sport. */
    private val TEAM_WORDS = Words(
        words = listOf(
            "fc", "cf", "ac", "cd", "ca", "rc", "ud", "afc", "sv", "fk", "bk", "psg", "om", "ol", "asse", "ogcn", "losc", "rcl", "oss",
            "athletic", "atletico", "sporting", "olympique", "olympiakos", "racing", "dynamo", "dinamo", "rovers", "wanderers", "rangers",
            "celtic", "juventus", "milan", "napoli", "lazio", "barcelona", "barcelone", "barca", "madrid", "marseille", "lyon", "monaco",
            "lille", "rennes", "nantes", "strasbourg", "bayern", "munich", "dortmund", "leverkusen", "schalke", "chelsea", "arsenal",
            "liverpool", "tottenham", "everton", "manchester", "newcastle", "wydad", "raja", "hassania", "berkane", "ahly", "zamalek",
            "esperance", "sevilla", "valencia", "betis", "villarreal", "porto", "benfica", "ajax", "galatasaray", "fenerbahce", "besiktas",
            "lakers", "celtics", "warriors", "knicks", "lech", "legia", "wisla", "gornik", "cracovia", "slask", "lechia",
            "الأهلي", "الزمالك", "الهلال", "النصر", "الوداد", "الرجاء", "ريال", "برشلونة",
        ),
        prefixes = listOf("united", "juventus", "atletic"),
    )

    /** Chaîne dont le NOM ou la catégorie annonce du sport (en plus des mots génériques). */
    private val CHANNEL = Words(
        words = listOf(
            "dazn", "tsn", "ssc", "foot", "nba", "nfl", "nhl", "mlb", "ufc", "wwe", "golf", "tennis", "f1", "gol", "mutv", "arryadia", "alkass",
            "botola", "laliga", "bundesliga", "سبورت", "سبورتس", "الرياضية", "الرياضة",
        ),
        prefixes = listOf("beinsport", "espn", "eurosport", "supersport", "dsport", "motorsport", "racing", "fight"),
        phrases = listOf("bein sport", "bein sports", "ligue 1", "premier league", "champions league", "serie a", "la liga"),
    )

    /** « beIN » seul est du sport, sauf ses chaînes de cinéma, séries, enfants et cuisine. */
    private val BEIN_NOT_SPORT = Words(listOf("movies", "drama", "series", "serie", "junior", "gourmet", "cinema", "box", "premium"))

    private val VERSUS = Regex("^(.{2,60}?)\\s+(?:vs\\.?|versus|v/s)\\s+(.{2,60})$", RegexOption.IGNORE_CASE)
    private val DASH = Regex("^(.{2,40}?)\\s+[-–—]\\s+(.{2,40}?)\\s*(?:\\(.*\\)|\\[.*\\])?\\s*$")
    private val LIVE_PREFIX = Regex("^\\s*(?:live|direct|en direct|en vivo|en directo|na zywo|na żywo|مباشر)\\s*[:|•\\-–]\\s*", RegexOption.IGNORE_CASE)

    private fun isTeamLike(side: String): Boolean = TEAM_WORDS.matches(fold(side))

    private fun matchesAnyKeyword(f: String, toks: List<String>) =
        GENERIC.matches(f, toks) || DISCIPLINES.matches(f, toks) || COMPETITIONS.matches(f, toks)

    /**
     * Ce programme est-il du sport ? [onSportChannel] : il passe sur une chaîne de sport (tout y est du sport, sauf
     * téléachat). Sinon : un mot de sport ou de compétition dans le titre, « A vs B » avec une équipe reconnue (ou
     * précédé de « Live : »), « A - B » où les deux côtés sont des équipes, « Live : / Direct : » suivi de sport.
     * [description] (facultative, 120 premiers caractères) : seuls les noms de COMPÉTITIONS y comptent, pour ne pas
     * transformer un film qui parle de tennis en match.
     */
    fun isSportProgramme(title: String, description: String? = null, onSportChannel: Boolean = false): Boolean {
        if (title.isBlank()) return false
        val f = fold(title)
        if (f.isEmpty()) return false
        val toks = tokens(f)
        if (NEGATIVE.matches(f, toks)) return false
        if (onSportChannel) return true
        if (matchesAnyKeyword(f, toks)) return true
        val live = LIVE_PREFIX.find(title)
        val rest = if (live != null) title.substring(live.range.last + 1).trim() else title.trim()
        val vs = VERSUS.find(rest)
        if (vs != null && (live != null || isTeamLike(vs.groupValues[1]) || isTeamLike(vs.groupValues[2]))) return true
        val dash = DASH.find(rest)
        if (dash != null) {
            val a = isTeamLike(dash.groupValues[1])
            val b = isTeamLike(dash.groupValues[2])
            if ((a && b) || (live != null && (a || b))) return true
        }
        if (description != null && COMPETITIONS.matches(fold(description.take(120)))) return true
        return false
    }

    /** Chaîne de sport d'après son nom et/ou le nom de sa catégorie. */
    fun isSportChannel(channelName: String?, categoryName: String?): Boolean =
        isSportName(channelName) || isSportName(categoryName)

    private fun isSportName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val f = fold(name)
        if (f.isEmpty()) return false
        val toks = tokens(f)
        if (NEGATIVE.matches(f, toks)) return false
        if (GENERIC.matches(f, toks) || CHANNEL.matches(f, toks)) return true
        return "bein" in toks && !BEIN_NOT_SPORT.matches(f, toks)
    }

    /** Motifs LIKE (ASCII insensible à la casse) pour PRÉ-FILTRER les titres en SQL : le filtre exact reste [isSportProgramme]. */
    val TITLE_LIKE: List<String> = listOf(
        "%sport%", "%football%", "%soccer%", "% vs %", "% vs. %", "live :%", "live:%", "direct :%", "direct:%", "%ligue 1%", "%league%",
        "%liga%", "%bundesliga%", "%serie a%", "%nba%", "%nfl%", "%nhl%", "%ufc%", "%mma%", "%tennis%", "%tenis%", "%rugby%", "%basket%",
        "%handball%", "%cyclisme%", "%formul%", "%motogp%", "%boxe%", "%boxing%", "%f_tbol%", "%mecz%", "%coupe du monde%", "%grand prix%",
        "%botola%", "%derby%", "%كرة%", "%مباراة%", "%دوري%", "%رياض%",
    )

    /** Motifs LIKE sur le nom d'une chaîne (pré-filtre ; le filtre exact reste [isSportChannel]). */
    val CHANNEL_LIKE: List<String> = listOf(
        "%sport%", "%bein%", "%dazn%", "%espn%", "%ssc%", "%tsn%", "%foot%", "%nba%", "%nfl%", "%ufc%", "%golf%", "%tennis%", "%racing%",
        "%motor%", "%deport%", "%arryadia%", "%alkass%", "%رياض%", "%سبورت%",
    )
}
