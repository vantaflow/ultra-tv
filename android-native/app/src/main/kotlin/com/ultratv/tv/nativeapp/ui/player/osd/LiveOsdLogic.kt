package com.ultratv.tv.nativeapp.ui.player.osd

import com.ultratv.tv.nativeapp.nav.Routes
import com.ultratv.tv.nativeapp.ui.player.engine.AspectMode

/**
 * Logique pure de l'OSD du Direct (aucune dépendance Android ni Compose) : décision des touches, étage focalisé,
 * Retour, liste ordonnée des boutons de la barre. Testée sur la JVM (LiveOsdLogicTest).
 */

/** Étage de l'OSD qui porte le focus : la barre de contrôles (bas) ou la rangée des chaînes récentes (milieu). */
enum class OsdStage { CONTROLS, RECENTS }

/** Touches du Direct que l'OSD distingue (le reste est [OTHER]). */
enum class OsdKey { OK, UP, DOWN, LEFT, RIGHT, MENU, INFO, CHANNEL_UP, CHANNEL_DOWN, GUIDE, LAST_CHANNEL, OTHER }

/** Ce que le lecteur doit faire d'une touche du Direct. */
sealed interface OsdDecision {
    /** Ouvre l'OSD complet, focus sur la barre. */
    data object OpenOsd : OsdDecision
    /** Zappe (suivante si [forward]) ; [closeOsd] : l'OSD est refermé (retour au simple bandeau). */
    data class Zap(val forward: Boolean, val closeOsd: Boolean) : OsdDecision
    /** Saut dans le tampon du différé (secondes, négatif = arrière). */
    data class TimeshiftJump(val seconds: Int) : OsdDecision
    data object OpenChannels : OsdDecision
    data object RecallPrevious : OsdDecision
    /** Touche absorbée (relance seulement la minuterie de masquage). */
    data object Consume : OsdDecision
    /** Touche laissée au focus de Compose (navigation D-pad dans l'OSD ouvert). */
    data object Pass : OsdDecision
}

object LiveOsdLogic {
    /** Saut du différé, en secondes. */
    const val TIMESHIFT_JUMP_SEC = 30
    /** Nombre maximal de chaînes récentes affichées. */
    const val MAX_RECENTS = 10

    /**
     * Décision d'une touche pendant le Direct (aucun panneau, tiroir, fiche ni code parental ouvert ; la saisie de
     * numéro, les touches média Lecture/Pause/Avance/Retour et « Passer l'intro » sont traitées avant).
     *
     * @param osdOpen OSD complet affiché (les commandes ont la main)
     * @param controlsHidden surcouche entièrement masquée (ni OSD ni bandeau)
     * @param tsActive pause du direct (différé) active
     * @param errorShown panneau d'erreur affiché : prioritaire sur toute touche du Direct
     */
    fun decide(key: OsdKey, osdOpen: Boolean, controlsHidden: Boolean, tsActive: Boolean, errorShown: Boolean = false, zapVertical: Boolean = false): OsdDecision {
        // Panneau d'erreur affiché (échec, « Le flux ne démarre pas »…) : ses boutons reçoivent TOUTES les touches.
        if (errorShown) return OsdDecision.Pass
        // Valables dans tous les états.
        when (key) {
            OsdKey.LAST_CHANNEL -> return OsdDecision.RecallPrevious
            OsdKey.GUIDE -> return OsdDecision.OpenChannels
            OsdKey.CHANNEL_UP -> return OsdDecision.Zap(forward = false, closeOsd = true)
            OsdKey.CHANNEL_DOWN -> return OsdDecision.Zap(forward = true, closeOsd = true)
            else -> Unit
        }
        // OSD ouvert : le focus de Compose se déplace entre les étages et les boutons.
        if (osdOpen) return OsdDecision.Pass
        // OSD fermé (masqué ou simple bandeau après un zap).
        // Réglage « Zapper avec Haut / Bas » : Haut = chaîne précédente, Bas = suivante (sens de la liste) ; Gauche / Droite
        // affichent alors l'OSD (en différé masqué : ±30 s, inchangé). En différé, Haut / Bas ouvrent l'OSD comme avant.
        if (zapVertical) return when (key) {
            OsdKey.OK -> OsdDecision.OpenChannels
            OsdKey.UP, OsdKey.DOWN -> if (tsActive) OsdDecision.OpenOsd else OsdDecision.Zap(forward = key == OsdKey.DOWN, closeOsd = false)
            OsdKey.LEFT, OsdKey.RIGHT ->
                if (tsActive && controlsHidden) OsdDecision.TimeshiftJump(if (key == OsdKey.LEFT) -TIMESHIFT_JUMP_SEC else TIMESHIFT_JUMP_SEC)
                else OsdDecision.OpenOsd
            OsdKey.MENU, OsdKey.INFO -> OsdDecision.OpenOsd
            else -> OsdDecision.Consume
        }
        return when (key) {
            // OK : liste des chaînes ; Haut et Bas : OSD complet ; Gauche / Droite : chaîne précédente / suivante
            // (en différé masqué : ±30 s). Demandé par les utilisateurs.
            OsdKey.OK -> OsdDecision.OpenChannels
            OsdKey.UP, OsdKey.DOWN, OsdKey.MENU, OsdKey.INFO -> OsdDecision.OpenOsd
            OsdKey.LEFT, OsdKey.RIGHT ->
                if (tsActive && controlsHidden)
                    OsdDecision.TimeshiftJump(if (key == OsdKey.LEFT) -TIMESHIFT_JUMP_SEC else TIMESHIFT_JUMP_SEC)
                else if (tsActive) OsdDecision.OpenOsd
                else OsdDecision.Zap(forward = key == OsdKey.RIGHT, closeOsd = false)
            else -> OsdDecision.Consume
        }
    }

    /** Le focus revient à la racine (qui lit les touches du Direct) quand l'OSD est fermé, sauf si le panneau d'erreur le détient. */
    fun shouldRestoreRootFocus(isLive: Boolean, osdOpen: Boolean, errorShown: Boolean): Boolean = isLive && !osdOpen && !errorShown

    /** Effet de Retour sur l'OSD (le reste, quitter le lecteur compris, relève du BackHandler du lecteur). */
    enum class BackEffect { FOCUS_CONTROLS, CLOSE_OSD, NOT_OSD }

    /**
     * Retour : focus dans les récents → il revient sur la barre ; focus dans la barre → l'OSD se ferme ;
     * OSD fermé → [BackEffect.NOT_OSD] (comportement habituel, qui finit par quitter le lecteur).
     * @param playing le flux joue (comme avant : sans image, Retour quitte directement)
     */
    fun backEffect(osdOpen: Boolean, stage: OsdStage, playing: Boolean): BackEffect = when {
        !osdOpen || !playing -> BackEffect.NOT_OSD
        stage == OsdStage.RECENTS -> BackEffect.FOCUS_CONTROLS
        else -> BackEffect.CLOSE_OSD
    }

    /** Format d'image suivant : FIT → FILL → ZOOM → 16:9 → 4:3 → FIT. */
    fun nextAspect(current: AspectMode): AspectMode {
        val all = AspectMode.entries
        return all[(all.indexOf(current) + 1) % all.size]
    }
}

/** Identifiants des boutons de la barre, dans l'ordre d'affichage. */
enum class OsdActionId {
    PLAY_PAUSE, JUMP_BACK, JUMP_FORWARD, AUDIO, SUBS, ASPECT, CHANNELS, HISTORY, FAVORITE, RECORD, PIP, REPLAY, SLEEP, PLAYER,
    MY_LIST, MOVIES, SERIES, RECORDINGS, SETTINGS,
}

/** Un bouton de la barre : libellé, tracé d'icône (grille 24), état actif visible, complément de libellé (format courant). */
data class OsdAction(
    val id: OsdActionId, val label: String, val icon: String,
    val active: Boolean = false, val detail: String? = null, val fillIcon: Boolean = false,
    /** Séparateur visuel avant ce bouton (début des raccourcis). */
    val separatorBefore: Boolean = false,
)

/** Libellés (déjà traduits) des boutons ; construits à partir des holders de textes (voir LiveOsdStrings.labels). */
data class OsdLabels(
    val play: String, val pause: String, val audio: String, val subs: String, val aspect: String,
    val aspectNames: Map<AspectMode, String>, val channels: String, val history: String, val addFavorite: String, val removeFavorite: String,
    val record: String, val pip: String, val sleep: String, val player: String,
    val myList: String, val movies: String, val series: String, val recordings: String, val settings: String,
)

/** État qui décide des boutons proposés. */
data class OsdActionInputs(
    val playing: Boolean, val tsActive: Boolean, val isFavorite: Boolean, val aspect: AspectMode,
    /** Libellé du bouton « Depuis le début » / « Retour au direct » ; null = indisponible. */
    val replayLabel: String?, val replayActive: Boolean,
    val sleepActive: Boolean, val pipSupported: Boolean,
    /** Raccourcis Ma liste, Films… (faux si le lecteur n'a pas de navigation à proposer). */
    val shortcuts: Boolean = true,
)

object OsdIcons {
    const val PLAY = "M7 4v16l13-8z"
    const val PAUSE = "M7 4h3.5v16H7zM13.5 4H17v16h-3.5z"
    const val AUDIO = "M4 6h16M4 12h10M4 18h6"
    const val SUBS = "M3 6h18v12H3zM7 11h3M12 11h5M7 15h6"
    const val ASPECT = "M4 8V4h4M20 8V4h-4M4 16v4h4M20 16v4h-4"
    const val CHANNELS = "M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"
    const val HISTORY = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 7v5l3 2"
    const val HEART = "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"
    const val RECORD = "M12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12z"
    const val PIP = "M3 5h18v14H3zM12 12h7v5h-7z"
    const val REPLAY = "M3 12a9 9 0 1 0 3-6.7M3 4v5h5"
    const val SLEEP = "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"
    const val PLAYER = "M3 5h18v12H3zM8 21h8M12 17v4"
    const val MY_LIST = "M5 4h14v17l-7-4-7 4z"
    const val MOVIES = "M4 4h16v16H4zM8 4v16M16 4v16M4 9h4M4 15h4M16 9h4M16 15h4"
    const val SERIES = "M3 8h18v12H3zM8 4h8"
    const val RECORDINGS = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"
    const val SETTINGS = "M4 6h16M4 12h16M4 18h16M8 4v4M16 10v4M10 16v4"
}

/** Construction PURE de la barre : ordre figé par la demande, boutons conditionnels (différé, replay, PiP). */
object OsdActions {
    fun build(i: OsdActionInputs, L: OsdLabels): List<OsdAction> = buildList {
        add(OsdAction(OsdActionId.PLAY_PAUSE, if (i.playing) L.pause else L.play, if (i.playing) OsdIcons.PAUSE else OsdIcons.PLAY, fillIcon = true))
        if (i.tsActive) {
            add(OsdAction(OsdActionId.JUMP_BACK, "−${LiveOsdLogic.TIMESHIFT_JUMP_SEC}", ""))
            add(OsdAction(OsdActionId.JUMP_FORWARD, "+${LiveOsdLogic.TIMESHIFT_JUMP_SEC}", ""))
        }
        add(OsdAction(OsdActionId.AUDIO, L.audio, OsdIcons.AUDIO))
        add(OsdAction(OsdActionId.SUBS, L.subs, OsdIcons.SUBS))
        add(OsdAction(OsdActionId.ASPECT, L.aspect, OsdIcons.ASPECT, detail = L.aspectNames[i.aspect]))
        add(OsdAction(OsdActionId.CHANNELS, L.channels, OsdIcons.CHANNELS))
        add(OsdAction(OsdActionId.HISTORY, L.history, OsdIcons.HISTORY))
        add(OsdAction(OsdActionId.FAVORITE, if (i.isFavorite) L.removeFavorite else L.addFavorite, OsdIcons.HEART, active = i.isFavorite, fillIcon = i.isFavorite))
        add(OsdAction(OsdActionId.RECORD, L.record, OsdIcons.RECORD))
        if (i.pipSupported) add(OsdAction(OsdActionId.PIP, L.pip, OsdIcons.PIP))
        if (i.replayLabel != null) add(OsdAction(OsdActionId.REPLAY, i.replayLabel, OsdIcons.REPLAY, active = i.replayActive))
        add(OsdAction(OsdActionId.SLEEP, L.sleep, OsdIcons.SLEEP, active = i.sleepActive))
        add(OsdAction(OsdActionId.PLAYER, L.player, OsdIcons.PLAYER))
        if (i.shortcuts) {
            add(OsdAction(OsdActionId.MY_LIST, L.myList, OsdIcons.MY_LIST, separatorBefore = true))
            add(OsdAction(OsdActionId.MOVIES, L.movies, OsdIcons.MOVIES))
            add(OsdAction(OsdActionId.SERIES, L.series, OsdIcons.SERIES))
            add(OsdAction(OsdActionId.RECORDINGS, L.recordings, OsdIcons.RECORDINGS))
            add(OsdAction(OsdActionId.SETTINGS, L.settings, OsdIcons.SETTINGS))
        }
    }

    /** Route de navigation des raccourcis ; null pour les autres boutons. */
    fun routeOf(id: OsdActionId): String? = when (id) {
        OsdActionId.MY_LIST -> Routes.FAVORITES
        OsdActionId.MOVIES -> Routes.MOVIES
        OsdActionId.SERIES -> Routes.SERIES
        OsdActionId.RECORDINGS -> Routes.RECORDINGS
        OsdActionId.SETTINGS -> Routes.SETTINGS
        else -> null
    }
}

/** Construction PURE des informations de l'étage haut à partir du guide (programme en cours, suivant, différé). */
object OsdModel {
    /**
     * @param prog programme en cours ; null (pas de guide) = pas d'info de programme : le nom de la chaîne sert de titre
     * @param tsWindow fenêtre du différé (début, fin, fraction 0..1, repère « DIRECT ») : remplace la progression du programme
     * @param remaining « reste 34 min » à partir de « 34 min »
     */
    fun programme(
        prog: com.ultratv.tv.nativeapp.data.db.EpgEntity?, next: com.ultratv.tv.nativeapp.data.db.EpgEntity?, nowMs: Long, thenWord: String,
        tsWindow: OsdProgress?, remaining: (String) -> String, hm: (Long) -> String, range: (Long, Long) -> String,
    ): OsdProgrammeInfo? {
        if (prog == null) return null
        val span = prog.endMs - prog.startMs
        val progress = tsWindow ?: if (span > 0) OsdProgress(
            startLabel = hm(prog.startMs), endLabel = hm(prog.endMs),
            fraction = ((nowMs - prog.startMs).toFloat() / span).coerceIn(0f, 1f),
            remainingLabel = com.ultratv.tv.nativeapp.ui.player.ProgrammeInfo.durationLabel(prog.endMs - nowMs)?.let(remaining),
        ) else null
        return OsdProgrammeInfo(
            title = prog.title,
            description = prog.description?.trim()?.takeIf { it.isNotEmpty() },
            timeRange = if (tsWindow == null && span > 0) range(prog.startMs, prog.endMs) else null,
            progress = progress,
            thenLine = com.ultratv.tv.nativeapp.ui.player.ProgrammeInfo.thenLine(thenWord, next, hm),
        )
    }

    /**
     * Chaînes récentes affichées : sans la chaîne en cours, au plus [LiveOsdLogic.MAX_RECENTS], la plus récente d'abord.
     *
     * Dédoublonnage : par clé (source:identifiant), mais aussi par nom affiché normalisé. Une même chaîne est
     * souvent servie sous plusieurs identifiants (variantes HD/FHD/backup, autre catégorie, autre source) qui ont
     * donc des clés différentes mais le même nom nettoyé : sans cela « TF1 1 » apparaissait deux fois, et la chaîne
     * en cours revenait sous un autre identifiant. On garde la première occurrence (la plus récente).
     *
     * @param currentName nom affiché de la chaîne en cours (exclut aussi ses variantes)
     */
    fun recents(all: List<OsdRecent>, currentKey: String?, currentName: String? = null): List<OsdRecent> {
        val seenKeys = HashSet<String>()
        val seenNames = HashSet<String>()
        val curName = currentName?.let(::nameKey)?.takeIf { it.isNotEmpty() }
        val out = ArrayList<OsdRecent>()
        for (r in all) {
            if (r.key == currentKey) continue
            val n = nameKey(r.name)
            if (curName != null && n == curName) continue
            if (!seenKeys.add(r.key)) continue
            // Nom vide : pas d'identité d'affichage fiable, seule la clé compte.
            if (n.isNotEmpty() && !seenNames.add(n)) continue
            out += r
            if (out.size >= LiveOsdLogic.MAX_RECENTS) break
        }
        return out
    }

    private fun nameKey(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")
}
