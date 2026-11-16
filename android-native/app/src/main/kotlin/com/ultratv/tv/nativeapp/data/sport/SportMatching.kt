package com.ultratv.tv.nativeapp.data.sport

/** Un événement décrit par un titre de programme : compétition reconnue (clé stable) et clé d'identité. */
data class ParsedEvent(val competition: String?, val key: String, val isMatch: Boolean)

/**
 * Rapprochement des programmes du guide qui diffusent le MÊME événement sportif (« PSG - OM », « Paris SG vs Marseille »,
 * « Ligue 1 : PSG / OM »…) sur plusieurs chaînes. Fonctions pures, sans base ni réseau. Prudent par construction : en cas de
 * doute (titre sans équipes identifiables, compétitions différentes, horaires éloignés) les programmes restent séparés.
 */
object SportMatching {
    /** Tolérance d'horaire pour une rencontre « A - B » (les fournisseurs décalent de quelques minutes). */
    const val MATCH_TOLERANCE_MS = 15L * 60_000L
    /** Tolérance pour un événement sans équipes (titre identique exigé) : plus stricte, la confiance est plus basse. */
    const val OTHER_TOLERANCE_MS = 5L * 60_000L

    private val BRACKETS = Regex("""\([^)]*\)|\[[^\]]*]""")
    private val LIVE_PREFIX = Regex("""^\s*(?:live|direct|en direct|en vivo|en directo|na zywo|na żywo|مباشر)\s*[:|•\-–]\s*""", RegexOption.IGNORE_CASE)
    private val TRAILING = Regex("""\s*(?:[-–|]\s*)?(?:\d{1,2}\s*[:h]\s*\d{2}|hd|fhd|4k|live|en direct|en vivo|direct)\s*$""", RegexOption.IGNORE_CASE)
    private val SEP = Regex("""\s+(?:vs\.?|v|versus|contre|x|ضد|[-–—/×])\s+""", RegexOption.IGNORE_CASE)
    private val QUALITY_WORDS = setOf("hd", "fhd", "4k", "live", "direct", "vivo", "directo")

    /** Mots sans valeur d'identité dans un nom d'équipe (statuts de clubs, articles). */
    private val NOISE = setOf("fc", "cf", "ac", "sc", "cd", "ca", "afc", "sv", "fk", "bk", "rc", "ud", "cs", "us", "as", "club", "de", "du", "the", "al", "el")
    private val SYNONYM = mapOf("utd" to "united", "atl" to "atletico", "st" to "saint")

    // Alias de clubs : variantes courantes (français, anglais, espagnol, arabe translittéré) → forme canonique.
    private val ALIAS_SRC: Map<String, List<String>> = mapOf(
        "psg" to listOf("psg", "paris", "paris sg", "paris saint germain", "paris s g", "باريس سان جيرمان"),
        "marseille" to listOf("om", "o marseille", "olympique marseille", "olympique de marseille", "marseille"),
        "lyon" to listOf("ol", "lyon", "lyonnais", "olympique lyonnais", "olympique lyon"),
        "man united" to listOf("man united", "man utd", "manchester united", "manchester utd", "mufc", "مانشستر يونايتد"),
        "man city" to listOf("man city", "manchester city", "mcfc", "مانشستر سيتي"),
        "real madrid" to listOf("real madrid", "real", "r madrid", "ريال مدريد"),
        "barcelona" to listOf("barcelona", "barcelone", "barca", "fc barcelona", "برشلونة"),
        "atletico madrid" to listOf("atletico madrid", "atletico", "atl madrid", "atm", "اتلتيكو مدريد"),
        "bayern" to listOf("bayern", "bayern munich", "bayern munchen", "fc bayern", "بايرن ميونخ"),
        "inter" to listOf("inter", "inter milan", "internazionale", "inter milano", "انتر ميلان"),
        "juventus" to listOf("juve", "juventus", "يوفنتوس"),
        "tottenham" to listOf("spurs", "tottenham", "tottenham hotspur"),
        "wolves" to listOf("wolves", "wolverhampton", "wolverhampton wanderers"),
        "newcastle" to listOf("newcastle", "newcastle united", "nufc"),
        "dortmund" to listOf("bvb", "dortmund", "borussia dortmund"),
        "gladbach" to listOf("gladbach", "monchengladbach", "borussia monchengladbach"),
        "leverkusen" to listOf("leverkusen", "bayer leverkusen", "bayer 04 leverkusen"),
        "sporting" to listOf("sporting", "sporting cp", "sporting lisbon", "sporting lisbonne"),
        "benfica" to listOf("benfica", "sl benfica"),
        "ahly" to listOf("ahly", "al ahly", "الأهلي"),
        "zamalek" to listOf("zamalek", "الزمالك"),
        "wydad" to listOf("wydad", "wydad casablanca", "wydad athletic", "الوداد"),
        "raja" to listOf("raja", "raja casablanca", "raja club athletic", "الرجاء"),
        "hilal" to listOf("hilal", "al hilal", "الهلال"),
        "nassr" to listOf("nassr", "nasr", "al nassr", "النصر"),
        "esperance" to listOf("esperance", "esperance tunis", "esperance sportive tunis", "الترجي"),
    )

    // Compétitions reconnues : clé stable <- phrases repliées (la première liste qui correspond gagne ; du plus précis au plus large).
    private val COMP_SRC: List<Pair<String, List<String>>> = listOf(
        "caf-cl" to listOf("caf champions league", "ligue des champions caf", "دوري ابطال افريقيا"),
        "ucl" to listOf("champions league", "ligue des champions", "ucl", "ldc", "liga de campeones", "دوري ابطال"),
        "uel" to listOf("europa league", "ligue europa", "uel"),
        "uecl" to listOf("conference league"),
        "ligue1" to listOf("ligue 1"),
        "ligue2" to listOf("ligue 2"),
        "epl" to listOf("premier league", "epl"),
        "ligaportugal" to listOf("liga portugal"),
        "laliga" to listOf("la liga", "laliga", "primera division", "liga"),
        "seriea" to listOf("serie a"),
        "bundesliga" to listOf("bundesliga"),
        "botola" to listOf("botola"),
        "worldcup" to listOf("coupe du monde", "world cup", "copa del mundo", "كاس العالم"),
        "afcon" to listOf("afcon", "africa cup", "coupe d afrique", "can"),
        "facup" to listOf("fa cup"),
        "coupedefrance" to listOf("coupe de france"),
        "copadelrey" to listOf("copa del rey"),
        "carabao" to listOf("carabao cup", "efl cup"),
        "nba" to listOf("nba"), "nfl" to listOf("nfl"), "nhl" to listOf("nhl"), "ufc" to listOf("ufc"),
        "f1" to listOf("formula 1", "formule 1", "formula one", "f1"),
        "motogp" to listOf("motogp", "moto gp"),
    )

    private fun words(folded: String): List<String> = SportDetector.tokens(folded)

    private fun teamNorm(raw: String): String {
        val w = words(SportDetector.fold(raw)).map { SYNONYM[it] ?: it }.toMutableList()
        if (w.size >= 2 && w[0] == "man") w[0] = "manchester"
        val kept = w.filter { it !in NOISE && it.isNotEmpty() }
        return (kept.ifEmpty { w }).joinToString(" ")
    }

    private val aliases: Map<String, String> by lazy {
        buildMap { for ((canon, variants) in ALIAS_SRC) for (v in variants) put(teamNorm(v), canon) }
    }

    /** Forme canonique d'un nom d'équipe (alias courants, statuts de clubs et accents ignorés) ; vide si rien d'exploitable. */
    fun canonTeam(raw: String): String = teamNorm(raw).let { aliases[it] ?: it }

    private val competitions: List<Pair<String, List<String>>> by lazy {
        COMP_SRC.map { (k, ps) -> k to ps.map { " ${SportDetector.fold(it)} " } }
    }

    /** Clé de la compétition annoncée par [text] (« Ligue 1 Uber Eats » -> « ligue1 »), ou null. */
    fun competitionOf(text: String): String? {
        val padded = " ${words(SportDetector.fold(text)).joinToString(" ")} "
        return competitions.firstOrNull { (_, ps) -> ps.any { padded.contains(it) } }?.first
    }

    /** Analyse un titre de programme : compétition, équipes (clé indépendante de l'ordre) ou titre replié sans bruit. */
    fun parse(title: String): ParsedEvent {
        var s = BRACKETS.replace(title, " ").trim()
        s = LIVE_PREFIX.replace(s, "")
        var prev: String
        do { prev = s; s = TRAILING.replace(s, "").trim() } while (s != prev)
        var comp: String? = null
        repeat(2) {
            val i = s.indexOfFirst { it == ':' || it == '|' }
            if (i in 2..40) {
                val head = s.substring(0, i)
                val tail = s.substring(i + 1).trim()
                if (!SEP.containsMatchIn(head) && SEP.containsMatchIn(tail)) { comp = comp ?: competitionOf(head); s = tail }
            }
        }
        var parts = SEP.split(s).map { it.trim() }
        if (parts.size == 3 && competitionOf(parts[0]) != null) { comp = comp ?: competitionOf(parts[0]); parts = parts.drop(1) }
        if (parts.size == 2) {
            val a = canonTeam(parts[0]); val b = canonTeam(parts[1])
            if (a.isNotEmpty() && b.isNotEmpty() && a != b) return ParsedEvent(comp, listOf(a, b).sorted().joinToString("|"), true)
        }
        val plain = words(SportDetector.fold(s)).filter { it !in QUALITY_WORDS }.joinToString(" ")
        return ParsedEvent(comp, "t:$plain", false)
    }

    /** Deux programmes (titre + début) décrivent-ils le même événement ? */
    fun sameEvent(a: ParsedEvent, aStartMs: Long, b: ParsedEvent, bStartMs: Long): Boolean {
        if (a.key != b.key || a.key == "t:") return false
        if (a.competition != null && b.competition != null && a.competition != b.competition) return false
        val tol = if (a.isMatch) MATCH_TOLERANCE_MS else OTHER_TOLERANCE_MS
        return kotlin.math.abs(aStartMs - bStartMs) <= tol
    }

    /** Regroupe les entrées par événement (chaque groupe : début croissant) ; les groupes d'une seule entrée sont gardés. */
    fun groups(entries: List<SportEntry>): List<List<SportEntry>> {
        val byKey = LinkedHashMap<String, MutableList<Pair<ParsedEvent, SportEntry>>>()
        for (e in entries) { val p = parse(e.prog.title); byKey.getOrPut(p.key) { mutableListOf() }.add(p to e) }
        val out = ArrayList<List<SportEntry>>()
        for (list in byKey.values) {
            val clusters = ArrayList<MutableList<Pair<ParsedEvent, SportEntry>>>()
            for (item in list.sortedBy { it.second.prog.startMs }) {
                val home = clusters.firstOrNull { c -> sameEvent(c[0].first, c[0].second.prog.startMs, item.first, item.second.prog.startMs) }
                if (home != null) home.add(item) else clusters.add(mutableListOf(item))
            }
            clusters.mapTo(out) { c -> c.map { it.second } }
        }
        return out
    }

    /** Ordre d'affichage des chaînes d'un événement : meilleure qualité, chaînes de sport, numéro (connu d'abord), titre. */
    private val channelOrder = compareByDescending<SportEntry> { it.channel.quality }
        .thenByDescending { it.sportChannel }
        .thenBy { it.channel.num == 0 }.thenBy { it.channel.num }
        .thenBy { it.channel.title }.thenBy { it.channel.id }

    /** Les autres chaînes du groupe [g] pour [self] : une entrée par chaîne, sans la sienne, triées. */
    private fun others(g: List<SportEntry>, self: SportEntry): List<SportEntry> =
        g.filter { it.channel.id != self.channel.id }.sortedWith(channelOrder).distinctBy { it.channel.id }

    /** Clé d'une entrée dans [alsoOnMap] : chaîne + début du programme. */
    fun entryKey(e: SportEntry): Pair<Long, Long> = e.channel.id to e.prog.startMs

    /** Pour chaque entrée dont l'événement passe aussi ailleurs : les autres chaînes (« Aussi sur… »). Absente = aucune autre. */
    fun alsoOnMap(entries: List<SportEntry>): Map<Pair<Long, Long>, List<SportEntry>> {
        val m = HashMap<Pair<Long, Long>, List<SportEntry>>()
        for (g in groups(entries)) {
            if (g.size < 2) continue
            for (e in g) { val o = others(g, e); if (o.isNotEmpty()) m[entryKey(e)] = o }
        }
        return m
    }

    /** Les autres chaînes qui diffusent le même événement que [entry] parmi [entries]. */
    fun alsoOn(entry: SportEntry, entries: List<SportEntry>): List<SportEntry> {
        val p = parse(entry.prog.title)
        val same = entries.filter { it === entry || sameEvent(p, entry.prog.startMs, parse(it.prog.title), it.prog.startMs) }
        return others(same, entry)
    }

    /** Une seule entrée par événement (la première de [entries]), ordre conservé : remplace le dédoublonnage par titre exact. */
    fun collapse(entries: List<SportEntry>): List<SportEntry> {
        val kept = HashMap<String, MutableList<Pair<ParsedEvent, SportEntry>>>()
        val out = ArrayList<SportEntry>()
        for (e in entries) {
            val p = parse(e.prog.title)
            val same = kept.getOrPut(p.key) { mutableListOf() }
            if (same.none { (kp, ke) -> sameEvent(kp, ke.prog.startMs, p, e.prog.startMs) }) { same.add(p to e); out.add(e) }
        }
        return out
    }

    /** Étiquette « qualité · langue » d'une chaîne d'après ses badges (qualité 1 SD..4 4K ; langue détectée, sinon pays) ; vide si inconnu. */
    fun badgesOf(c: com.ultratv.tv.nativeapp.data.db.ChannelEntity): String {
        val q = when (c.quality) { 4 -> "4K"; 3 -> "FHD"; 2 -> "HD"; 1 -> "SD"; else -> "" }
        val lang = c.lang.trim().ifEmpty { c.country.orEmpty() }.uppercase()
        return listOf(q, lang).filter { it.isNotEmpty() }.joinToString(" · ")
    }
}
