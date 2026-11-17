package com.ultratv.tv.nativeapp.data.sport

import com.ultratv.tv.nativeapp.data.db.ChannelEntity

/** Zone d'un classement (liseré de couleur de la maquette). */
enum class Zone { NONE, QUALIFIED, EUROPA, RELEGATION }

/** Zones d'un classement par ligue (football européen) : Ligue des champions, Ligue Europa, relégation. Fonctions pures. */
object StandingZones {
    private data class Spec(val qualified: Int, val europa: Int, val relegated: Int)

    // Places qualificatives (Ligue des champions), puis Europa, et nombre de derniers relégués.
    private val SPECS = mapOf(
        "ligue1" to Spec(3, 1, 3), "premier" to Spec(4, 1, 3), "liga" to Spec(4, 1, 3),
        "seriea" to Spec(4, 1, 3), "bundesliga" to Spec(4, 1, 2), "botola" to Spec(0, 0, 2),
    )

    fun of(leagueKey: String?, rank: Int?, size: Int, grouped: Boolean = false): Zone {
        val s = SPECS[leagueKey] ?: return Zone.NONE
        if (rank == null || grouped || size < 8) return Zone.NONE
        return when {
            rank <= s.qualified -> Zone.QUALIFIED
            rank <= s.qualified + s.europa -> Zone.EUROPA
            s.relegated > 0 && rank > size - s.relegated -> Zone.RELEGATION
            else -> Zone.NONE
        }
    }

    /** La ligue a-t-elle des places européennes (légende « Ligue des champions / Europa ») ? */
    fun hasEurope(leagueKey: String?): Boolean = (SPECS[leagueKey]?.qualified ?: 0) > 0
    fun hasZones(leagueKey: String?): Boolean = SPECS.containsKey(leagueKey)
}

/** Forme récente d'une équipe : les 5 derniers résultats, V/N/D normalisés en W/D/L. */
object FormLogic {
    fun parse(form: String?): List<Char> = form.orEmpty().uppercase().mapNotNull {
        when (it) { 'W', 'V', 'G' -> 'W'; 'D', 'N' -> 'D'; 'L', 'P' -> 'L'; else -> null }
    }.takeLast(5)
}

/** Carte « En direct » : un match (avec ou sans score) et les chaînes de l'utilisateur qui le diffusent. */
data class LiveCardModel(
    val key: String,
    val league: String,
    val minute: String?,
    val home: String?,
    val away: String?,
    val title: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val match: LeagueMatch?,
    /** Chaînes, meilleure qualité d'abord (la première lance la lecture). */
    val channels: List<SportEntry>,
    /** Avancement 0..1 si connu (programme du guide, sinon minute de jeu). */
    val progress: Float?,
)

object SportHubLogic {
    /** Clé de compétition du guide (SportMatching.competitionOf) pour chaque ligue du catalogue. */
    private val COMPETITION = mapOf(
        "ligue1" to "ligue1", "premier" to "epl", "liga" to "laliga", "seriea" to "seriea", "bundesliga" to "bundesliga",
        "ucl" to "ucl", "botola" to "botola", "nba" to "nba", "nfl" to "nfl", "nhl" to "nhl", "f1" to "f1",
    )

    fun entryKey(e: SportEntry) = "${e.channel.id}-${e.prog.startMs}"

    /**
     * Match « du guide » d'une carte sans score relié (compétition non couverte par la source de scores, titre non
     * reconnu) : la feuille s'ouvre quand même, avec les équipes du titre et le choix de la chaîne.
     */
    fun guideMatch(c: LiveCardModel): LeagueMatch = LeagueMatch(
        id = c.key, startMs = c.channels.firstOrNull()?.prog?.startMs, name = c.title, home = c.home, away = c.away,
        homeBadge = null, awayBadge = null, homeScore = null, awayScore = null, state = MatchState.LIVE, progress = null, round = null,
    )

    /** Qualité d'une chaîne pour les puces : « 4K », « HD » (HD et FHD), « SD », ou vide si inconnue. */
    fun qualityOf(c: ChannelEntity): String = when (c.quality) { 4 -> "4K"; 3, 2 -> "HD"; 1 -> "SD"; else -> "" }

    /** Minute de jeu « 67' » d'un match en direct, ou null si la source n'en donne pas. */
    fun minuteOf(m: LeagueMatch): String? = m.progress?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.last().isDigit()) "$it'" else it }

    private fun minuteFraction(m: LeagueMatch, kind: SportKind?): Float? {
        if (kind != SportKind.FOOTBALL) return null
        val n = m.progress?.filter { it.isDigit() }?.toIntOrNull() ?: return null
        return (n / 90f).coerceIn(0f, 1f)
    }

    private fun progressOf(m: LeagueMatch?, channels: List<SportEntry>, kind: SportKind?, nowMs: Long): Float? {
        channels.firstOrNull()?.let { return SportLogic.progress(it.prog.startMs, it.prog.endMs, nowMs) }
        return m?.let { minuteFraction(it, kind) }
    }

    /**
     * Cartes « En direct ». Une ligue choisie : ses matchs en direct (avec score), reliés aux chaînes du guide. « Tout » :
     * les directs du guide, enrichis du score et de la minute quand ils correspondent à un match de la ligue chargée.
     */
    fun liveCards(
        leagueKey: String?, leagueName: String?, kind: SportKind?, matches: List<LeagueMatch>,
        guideLive: List<SportEntry>, entries: List<SportEntry>, alsoOn: Map<Pair<Long, Long>, List<SportEntry>>,
        nowMs: Long, liveByLeague: Map<String, List<LeagueMatch>> = emptyMap(), programmeLabel: (SportEntry) -> String,
    ): List<LiveCardModel> {
        val liveMatches = LeagueLogic.live(matches)
        if (leagueKey != null) {
            return liveMatches.map { m ->
                val ch = LeagueChannels.channelsFor(m, entries)
                LiveCardModel(
                    "m${m.id}", leagueName.orEmpty(), minuteOf(m), m.home, m.away, m.title, m.homeScore, m.awayScore, m, ch,
                    progressOf(m, ch, kind, nowMs),
                )
            }
        }
        // « Tout » : le guide mélange toutes les compétitions, donc on rattache les scores des directs de TOUTES les ligues
        // (pas seulement celle chargée pour le classement), chaque match gardant le nom de sa ligue.
        val byEntry = HashMap<String, Pair<LeagueMatch, String>>()
        fun attach(m: LeagueMatch, name: String) {
            if (m.state != MatchState.LIVE) return
            for (e in LeagueChannels.channelsFor(m, entries)) byEntry.putIfAbsent(entryKey(e), m to name)
        }
        for (m in liveMatches) attach(m, leagueName.orEmpty())
        for ((k, list) in liveByLeague) { val n = LeagueCatalog.of(k)?.name.orEmpty(); for (m in list) attach(m, n) }
        return guideLive.map { e ->
            val hit = byEntry[entryKey(e)]
            val m = hit?.first
            val lgName = hit?.second.orEmpty()
            val line = matchLineOf(e.prog.title)
            val others = alsoOn[SportMatching.entryKey(e)].orEmpty()
            val ch = SportMatching.orderChannels(listOf(e) + others)
            LiveCardModel(
                "g${entryKey(e)}", if (m != null) lgName else line.competition ?: programmeLabel(e),
                m?.let(::minuteOf), m?.home ?: line.home, m?.away ?: line.away, line.title, m?.homeScore, m?.awayScore, m, ch,
                SportLogic.progress(e.prog.startMs, e.prog.endMs, nowMs),
            )
        }
    }

    /**
     * Programmes à venir pour la ligue choisie : ceux du guide qui diffusent un de ses matchs à venir ou en cours, ou dont le
     * titre annonce sa compétition. « Tout » (null) : tous les programmes reconnus, magazines et autres sports compris.
     */
    fun filterUpcoming(leagueKey: String?, matches: List<LeagueMatch>, entries: List<SportEntry>, days: List<SportDay>): List<SportDay> {
        if (leagueKey == null) return days
        val covered = HashSet<String>()
        for (m in matches) if (m.state != MatchState.POST) for (e in LeagueChannels.channelsFor(m, entries)) covered.add(entryKey(e))
        val comp = COMPETITION[leagueKey]
        return days.map { d ->
            SportDay(d.dayStartMs, d.entries.filter { e ->
                entryKey(e) in covered || (comp != null && SportMatching.parse(e.prog.title).competition == comp)
            })
        }.filter { it.entries.isNotEmpty() }
    }

    /** Dernière journée jouée, pour le sous-titre du classement (« J14 »). */
    fun roundOf(rows: List<StandingRow>): Int? = rows.mapNotNull { it.played }.maxOrNull()?.takeIf { it > 0 }

    /** Part du premier sur le total, 0..100 (barres de statistiques) ; 50 si rien. */
    fun sharePercent(home: Int, away: Int): Int {
        val t = home + away
        return if (t <= 0) 50 else Math.round(home * 100f / t)
    }
}
