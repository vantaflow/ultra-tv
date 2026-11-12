package com.ultratv.tv.nativeapp.data.sport

import android.content.Context
import com.ultratv.tv.nativeapp.data.extmeta.ExtMetaApi
import com.ultratv.tv.nativeapp.data.extmeta.ExtMetaParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Écussons d'équipes (TheSportsDB via le Worker) : logique pure. Seul un NOM D'ÉQUIPE tiré du titre du programme est
 * envoyé (jamais la chaîne, l'adresse du flux ni rien d'identifiant). Les équipes inconnues sont mémorisées aussi
 * (30 jours) pour ne pas redemander.
 */
object TeamBadgeLogic {
    const val TTL_MS = 30L * 24 * 3_600_000
    /** Équipes résolues au plus par passage de l'écran Sport. */
    const val MAX_NAMES = 40

    private val BRACKETS = Regex("""\([^)]*\)|\[[^\]]*\]""")
    private val NOT_ALLOWED = Regex("""[^\p{L}\p{N} .'&-]+""")
    private val SPACES = Regex("""\s+""")

    /**
     * Nom envoyé au Worker : sans parenthèses ni crochets, caractères simples seulement
     * (ceux qu'accepte le Worker), 2 à 40 caractères, pas uniquement des chiffres ; null sinon.
     */
    fun queryName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = BRACKETS.replace(raw, " ")
        s = NOT_ALLOWED.replace(s, " ")
        s = SPACES.replace(s, " ").trim()
        s = s.trim('-', '.', ' ')
        if (s.length !in 2..40 || s.all { it.isDigit() || it == ' ' }) return null
        return s
    }

    /** Clé de cache d'un nom brut (repliée : casse et accents ignorés) ; null si le nom n'est pas interrogeable. */
    fun keyOf(raw: String?): String? = queryName(raw)?.let { SportDetector.fold(it) }?.takeIf { it.isNotEmpty() }

    /** Disciplines à équipes : les joueurs de tennis, combattants et pilotes ne sont pas cherchés. */
    fun teamSport(kind: SportKind?): Boolean = kind == null || kind == SportKind.FOOTBALL || kind == SportKind.BASKET || kind == SportKind.RUGBY

    /** Noms d'équipes à résoudre pour ces programmes : côtés « A – B » des disciplines à équipes, sans doublon, au plus [max]. */
    fun namesOf(entries: List<SportEntry>, max: Int = MAX_NAMES): List<String> {
        val out = LinkedHashMap<String, String>()
        for (e in entries) {
            if (!teamSport(SportKind.of(e.prog.title, e.prog.description))) continue
            val line = matchLineOf(e.prog.title)
            for (side in listOf(line.home, line.away)) {
                val q = queryName(side) ?: continue
                val k = SportDetector.fold(q).takeIf { it.isNotEmpty() } ?: continue
                out.putIfAbsent(k, q)
                if (out.size >= max) return out.values.toList()
            }
        }
        return out.values.toList()
    }

    /** « horodatage|url » (url vide = équipe inconnue). */
    fun encode(badge: String?, atMs: Long): String = "$atMs|${badge.orEmpty()}"

    /** Entrée mémorisée : [Hit.badge] nul = inconnue ; null = absente, illisible ou périmée. */
    fun decode(raw: String?, nowMs: Long): Hit? {
        val i = raw?.indexOf('|') ?: return null
        if (i <= 0) return null
        val at = raw.substring(0, i).toLongOrNull() ?: return null
        if (nowMs - at !in 0 until TTL_MS) return null
        return Hit(raw.substring(i + 1).takeIf { it.startsWith("https://") })
    }

    class Hit(val badge: String?)
}

/** Stockage clé → valeur des écussons (SharedPreferences dans l'appli, mémoire dans les tests). */
interface TeamBadgeStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class PrefsTeamBadgeStore(ctx: Context) : TeamBadgeStore {
    private val sp = ctx.getSharedPreferences("team_badges", Context.MODE_PRIVATE)
    override fun get(key: String): String? = sp.getString(key, null)
    override fun put(key: String, value: String) {
        val e = sp.edit()
        if (sp.all.size >= 3_000) e.clear()        // borne : on repart de zéro, les écussons se redemandent au besoin
        e.putString(key, value).apply()
    }
}

/**
 * Écussons d'équipes : mémoire, puis préférences (30 jours, « inconnue » comprise), puis le Worker. Échec réseau ou Worker
 * indisponible : rien (non mémorisé). Jamais d'exception vers l'appelant.
 */
@Singleton
class TeamBadgeRepository(private val api: ExtMetaApi, private val store: TeamBadgeStore) {
    @Inject constructor(api: ExtMetaApi, @ApplicationContext ctx: Context) : this(api, PrefsTeamBadgeStore(ctx))

    private val memory = java.util.Collections.synchronizedMap(HashMap<String, TeamBadgeLogic.Hit>())

    /** Écusson déjà connu (mémoire ou préférences), sans appel réseau ; null si inconnu ou jamais demandé. */
    fun cached(name: String, now: Long = System.currentTimeMillis()): TeamBadgeLogic.Hit? {
        val k = TeamBadgeLogic.keyOf(name) ?: return null
        memory[k]?.let { return it }
        return TeamBadgeLogic.decode(store.get(k), now)?.also { memory[k] = it }
    }

    suspend fun badge(name: String, now: Long = System.currentTimeMillis()): String? {
        val q = TeamBadgeLogic.queryName(name) ?: return null
        val k = SportDetector.fold(q).takeIf { it.isNotEmpty() } ?: return null
        cached(q, now)?.let { return it.badge }
        if (!api.isEnabled()) return null
        val r = try { api.get("sports/team?name=" + java.net.URLEncoder.encode(q, "UTF-8").replace("+", "%20")) } catch (e: java.io.IOException) { null } ?: return null
        val hit = when (r.code) {
            200 -> ExtMetaParser.team(r.body)?.let { TeamBadgeLogic.Hit(it.team?.badge) } ?: return null
            404 -> TeamBadgeLogic.Hit(null)                   // nom refusé par le Worker : inutile de redemander
            else -> return null
        }
        memory[k] = hit
        store.put(k, TeamBadgeLogic.encode(hit.badge, now))
        return hit.badge
    }

    /** Écussons de [names] (clé repliée → URL), au plus [parallel] appels simultanés ; les équipes sans écusson sont absentes. */
    suspend fun badges(names: List<String>, now: Long = System.currentTimeMillis(), parallel: Int = 4): Map<String, String> = coroutineScope {
        val gate = Semaphore(parallel)
        names.map { n -> async { gate.withPermit { TeamBadgeLogic.keyOf(n)?.let { k -> badge(n, now)?.let { k to it } } } } }
            .awaitAll().filterNotNull().toMap()
    }
}
