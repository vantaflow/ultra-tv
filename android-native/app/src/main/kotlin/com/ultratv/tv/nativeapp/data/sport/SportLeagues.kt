package com.ultratv.tv.nativeapp.data.sport

import org.json.JSONArray
import org.json.JSONObject

/** Une ligue proposée par l'espace Sport. [key] : clé de la liste blanche du Worker ; [name] : nom propre (non traduit). */
data class League(val key: String, val name: String, val kind: SportKind?)

/** Catalogue des ligues (mêmes clés que `LEAGUES` du Worker) dans l'ordre par défaut. */
object LeagueCatalog {
    val all: List<League> = listOf(
        League("ligue1", "Ligue 1", SportKind.FOOTBALL),
        League("premier", "Premier League", SportKind.FOOTBALL),
        League("liga", "LaLiga", SportKind.FOOTBALL),
        League("seriea", "Serie A", SportKind.FOOTBALL),
        League("bundesliga", "Bundesliga", SportKind.FOOTBALL),
        League("ucl", "Champions League", SportKind.FOOTBALL),
        League("botola", "Botola Pro", SportKind.FOOTBALL),
        League("nba", "NBA", SportKind.BASKET),
        League("nfl", "NFL", null),
        League("nhl", "NHL", null),
        League("mlb", "MLB", null),
        League("top14", "Top 14", SportKind.RUGBY),
        League("premrugby", "Premiership Rugby", SportKind.RUGBY),
        League("f1", "Formula 1", SportKind.MOTOR),
    )
    private val byKey = all.associateBy { it.key }
    fun of(key: String): League? = byKey[key]
}

/**
 * Ordre et masquage des ligues PAR PROFIL, codés en une chaîne « ligue1,nba,-nfl » (ordre ; « - » = masquée).
 * Les ligues inconnues sont ignorées, les nouvelles ligues du catalogue s'ajoutent à la fin (visibles).
 */
data class LeaguePrefs(val order: List<String>, val hidden: Set<String>) {
    /** Ligues visibles dans l'ordre choisi. */
    fun visible(): List<League> = order.mapNotNull { if (it in hidden) null else LeagueCatalog.of(it) }

    /** Toutes les ligues dans l'ordre choisi, masquées comprises (écran de personnalisation). */
    fun everything(): List<League> = order.mapNotNull { LeagueCatalog.of(it) }

    fun isHidden(key: String) = key in hidden

    fun moved(key: String, delta: Int): LeaguePrefs {
        val i = order.indexOf(key)
        val j = i + delta
        if (i < 0 || j !in order.indices) return this
        val l = order.toMutableList()
        l[i] = l[j].also { l[j] = l[i] }
        return copy(order = l)
    }

    fun withHidden(key: String, hide: Boolean): LeaguePrefs =
        if (LeagueCatalog.of(key) == null) this else copy(hidden = if (hide) hidden + key else hidden - key)

    /** Chaîne enregistrée ; vide = réglage par défaut (rien à stocker). */
    fun encode(): String = if (this == DEFAULT) "" else order.joinToString(",") { if (it in hidden) "-$it" else it }

    companion object {
        val DEFAULT = LeaguePrefs(LeagueCatalog.all.map { it.key }, emptySet())

        fun decode(raw: String?): LeaguePrefs {
            if (raw.isNullOrBlank()) return DEFAULT
            val order = LinkedHashSet<String>()
            val hidden = HashSet<String>()
            for (t in raw.split(',')) {
                val hide = t.startsWith("-")
                val k = t.removePrefix("-").trim()
                if (LeagueCatalog.of(k) == null) continue
                order.add(k)
                if (hide) hidden.add(k)
            }
            for (l in LeagueCatalog.all) order.add(l.key)
            return LeaguePrefs(order.toList(), hidden)
        }
    }
}

/** État d'un match. */
enum class MatchState { PRE, LIVE, POST, OFF }

/** Match réduit tel que le renvoie le Worker. Scores nuls = pas de score. */
data class LeagueMatch(
    val id: String, val startMs: Long?, val name: String?,
    val home: String?, val away: String?, val homeBadge: String?, val awayBadge: String?,
    val homeScore: Int?, val awayScore: Int?, val state: MatchState, val progress: String?, val round: Int?,
) {
    /** Libellé « A – B », ou le nom de l'épreuve (F1) quand il n'y a pas d'équipes. */
    val title: String get() = if (home != null && away != null) "$home – $away" else (name ?: home ?: away ?: "")
    val hasScore: Boolean get() = homeScore != null && awayScore != null
}

data class StandingRow(
    val rank: Int?, val team: String, val badge: String?, val group: String?, val played: Int?,
    val win: Int?, val draw: Int?, val loss: Int?, val goalDiff: Int?, val points: Int?, val form: String?,
)

data class TimelineItem(val kind: String, val detail: String?, val home: Boolean, val minute: Int?, val player: String?, val assist: String?)
data class StatItem(val label: String, val home: Int, val away: Int)

data class MatchSheet(
    val match: LeagueMatch, val venue: String?, val timeline: List<TimelineItem>, val stats: List<StatItem>,
)

/** Lecture des réponses du Worker (/api/sports/league et /api/sports/match). Rend null si le corps est illisible. */
object LeagueParser {
    private fun JSONObject.s(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }
    private fun JSONObject.i(k: String): Int? = if (has(k) && !isNull(k)) (opt(k) as? Number)?.toInt() else null
    private fun https(u: String?) = u?.takeIf { it.startsWith("https://") }

    fun match(o: JSONObject): LeagueMatch? {
        val id = o.s("i") ?: return null
        val state = when (o.s("st")) { "live" -> MatchState.LIVE; "post" -> MatchState.POST; "off" -> MatchState.OFF; else -> MatchState.PRE }
        return LeagueMatch(
            id, if (o.has("t") && !o.isNull("t")) o.optLong("t") else null, o.s("n"), o.s("h"), o.s("a"),
            https(o.s("hb")), https(o.s("ab")), o.i("hs"), o.i("as"), state, o.s("p"), o.i("r"),
        )
    }

    private fun matches(a: JSONArray?): List<LeagueMatch> =
        (0 until (a?.length() ?: 0)).mapNotNull { a!!.optJSONObject(it)?.let(::match) }

    fun events(json: String?): List<LeagueMatch>? = runCatching { matches(JSONObject(json ?: return null).optJSONArray("events")) }.getOrNull()

    fun table(json: String?): List<StandingRow>? = runCatching {
        val a = JSONObject(json ?: return null).optJSONArray("rows") ?: return@runCatching emptyList()
        (0 until a.length()).mapNotNull { n ->
            val o = a.optJSONObject(n) ?: return@mapNotNull null
            StandingRow(o.i("rk"), o.s("team") ?: return@mapNotNull null, https(o.s("badge")), o.s("g"), o.i("p"), o.i("w"), o.i("d"), o.i("l"), o.i("gd"), o.i("pts"), o.s("form"))
        }
    }.getOrNull()

    fun sheet(json: String?): MatchSheet? = runCatching {
        val o = JSONObject(json ?: return null)
        val ev = o.optJSONObject("event") ?: return null
        val m = match(ev) ?: return null
        val tl = o.optJSONArray("timeline")
        val st = o.optJSONArray("stats")
        MatchSheet(
            m, ev.s("v"),
            (0 until (tl?.length() ?: 0)).mapNotNull { n -> tl!!.optJSONObject(n)?.let { TimelineItem(it.s("k") ?: return@let null, it.s("d"), it.optBoolean("h"), it.i("m"), it.s("pl"), it.s("as")) } },
            (0 until (st?.length() ?: 0)).mapNotNull { n -> st!!.optJSONObject(n)?.let { StatItem(it.s("k") ?: return@let null, it.i("h") ?: return@let null, it.i("a") ?: return@let null) } },
        )
    }.getOrNull()
}

/** Mise en page des matchs d'une ligue : fusion direct + calendrier, sections direct / à venir / résultats. */
object LeagueLogic {
    const val MAX_FIXTURES = 20
    const val MAX_RESULTS = 20

    /** Les matchs du direct remplacent ceux du calendrier ayant le même identifiant (scores plus frais). */
    fun merge(schedule: List<LeagueMatch>, live: List<LeagueMatch>): List<LeagueMatch> {
        val liveIds = live.map { it.id }.toSet()
        return (schedule.filter { it.id !in liveIds } + live).sortedBy { it.startMs ?: Long.MAX_VALUE }
    }

    fun live(all: List<LeagueMatch>) = all.filter { it.state == MatchState.LIVE }
    fun fixtures(all: List<LeagueMatch>, now: Long) =
        all.filter { it.state == MatchState.PRE || (it.state == MatchState.OFF && (it.startMs ?: 0) > now) }.take(MAX_FIXTURES)

    /** Résultats : le plus récent d'abord. */
    fun results(all: List<LeagueMatch>) = all.filter { it.state == MatchState.POST }.sortedByDescending { it.startMs ?: 0L }.take(MAX_RESULTS)

    /** Y a-t-il lieu d'interroger le direct (un match en cours, ou qui commence dans 10 min / a commencé il y a moins de 4 h) ? */
    fun mayBeLive(all: List<LeagueMatch>, now: Long): Boolean =
        all.any { m ->
            val t = m.startMs
            m.state == MatchState.LIVE || (m.state == MatchState.PRE && t != null && t <= now + 10 * 60_000L && t > now - 4 * 3_600_000L)
        }
}

/** Mode sans spoiler : quand les scores, classements et détails sont-ils montrés ? Fonctions pures. */
object SpoilerLogic {
    /** Score affichable : toujours si le mode est coupé ; sinon seulement pour un match que l'utilisateur a choisi de révéler. */
    fun showScore(noSpoiler: Boolean, matchId: String, revealed: Set<String>): Boolean = !noSpoiler || matchId in revealed

    /** Le match a-t-il un score à cacher (rien à cacher avant le coup d'envoi) ? */
    fun hasSpoiler(m: LeagueMatch): Boolean = m.hasScore && (m.state == MatchState.LIVE || m.state == MatchState.POST)

    /** Le classement dévoile des résultats : masqué tant qu'on ne l'a pas affiché (par ligue). */
    fun showTable(noSpoiler: Boolean, leagueKey: String, revealedTables: Set<String>): Boolean = !noSpoiler || leagueKey in revealedTables
}

/** Rapprochement d'un match de ligue avec les programmes du guide de l'utilisateur (« Aussi sur »). */
object LeagueChannels {
    /** Tolérance d'horaire entre la source de scores et le guide (les deux se décalent de quelques minutes). */
    const val TOLERANCE_MS = 30L * 60_000L

    /** Programmes du guide qui diffusent [m] : mêmes deux équipes (alias et accents ignorés), horaire proche. Meilleure chaîne d'abord. */
    fun channelsFor(m: LeagueMatch, entries: List<SportEntry>): List<SportEntry> {
        val h = m.home?.let { SportMatching.canonTeam(it) }?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val a = m.away?.let { SportMatching.canonTeam(it) }?.takeIf { it.isNotEmpty() } ?: return emptyList()
        val start = m.startMs ?: return emptyList()
        val key = listOf(h, a).sorted().joinToString("|")
        // Horaire d'abord (comparaison d'entiers) : l'analyse du titre (expressions régulières) ne porte que sur
        // les quelques programmes proches. Analyser tout le guide pour chaque match figeait l'écran Sport (ANR).
        val hits = entries.filter { e ->
            kotlin.math.abs(e.prog.startMs - start) <= TOLERANCE_MS &&
                SportMatching.parse(e.prog.title).let { p -> p.isMatch && p.key == key }
        }
        return SportMatching.orderChannels(hits)
    }
}
