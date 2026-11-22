package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.net.EmptyCatalogException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Garde anti-effacement de la synchro : une liste reçue vide, ou effondrée, alors que la base contient un catalogue
 * n'écrase rien. Un compte expiré renvoie souvent `[]` en HTTP 200 ; sans cette garde, chaînes, films et séries
 * disparaissaient (écran vide, favoris et reprises invisibles).
 *
 * Seuil de la baisse brutale : refus quand la source avait au moins [RATIO_MIN_BEFORE] éléments et qu'il en reste
 * moins de 1/[MIN_SHARE_DIVISOR] (2 %). 10 000 → 12 est refusé ; 10 000 → 2 000 (un bouquet retiré) passe. Sous
 * 1 000 éléments, seule la liste entièrement vide est refusée : une petite playlist peut vraiment fondre.
 * Une catégorie seule qui se vide reste acceptée (le total ne tombe pas à zéro).
 *
 * Changement VOLONTAIRE et durable (même adresse qui passe de 3 000 à 40 chaînes) : une liste réduite mais NON vide qui
 * revient identique (à 10 % près) sur [DURABLE_REFUSALS] synchros étalées sur au moins 24 h est acceptée (voir
 * [SyncGuardMemory]). Une liste entièrement vide n'est jamais acceptée ainsi : un compte expiré qui répond `[]` pendant
 * des jours ne doit jamais effacer le catalogue.
 */
object CatalogGuard {
    const val RATIO_MIN_BEFORE = 1_000
    const val MIN_SHARE_DIVISOR = 50
    const val DURABLE_REFUSALS = 3
    const val DURABLE_SPAN_MS = 24 * 3_600_000L

    /** Faut-il refuser [received] éléments reçus alors que la base en a [before] ? */
    fun refuses(before: Int, received: Int): Boolean =
        before > 0 && (received <= 0 || (before >= RATIO_MIN_BEFORE && received.toLong() * MIN_SHARE_DIVISOR < before))

    /** Deux tailles de liste « identiques » d'une synchro à l'autre (à 10 % près, au moins 2 éléments d'écart permis). */
    fun sameSize(a: Int, b: Int): Boolean = kotlin.math.abs(a - b) <= maxOf(2, maxOf(a, b) / 10)

    /** Le même petit résultat est revenu [refusals] fois sur [spanMs] : choix durable du fournisseur ou de l'utilisateur. */
    fun durable(refusals: Int, spanMs: Long): Boolean = refusals >= DURABLE_REFUSALS && spanMs >= DURABLE_SPAN_MS

    /** Lève [EmptyCatalogException] (et le journalise, sans identifiant) si [refuses]. */
    fun check(part: String, before: Int, received: Int) {
        if (!refuses(before, received)) return
        com.ultratv.tv.nativeapp.RemoteLog.warn("SyncGuard", "liste refusée partie=$part avant=$before reçu=$received : catalogue gardé")
        throw EmptyCatalogException(part, before, received)
    }

    /**
     * Catégories revenues VIDES en mode « une requête par catégorie » : les vider seulement si elles restent minoritaires.
     * Un panneau qui limite le débit répond `[]` à presque toutes les catégories (au lieu d'une erreur 429) : une seule
     * catégorie qui répond ne doit pas faire vider toutes les autres.
     */
    fun mayEmptyCategories(empty: Int, responded: Int): Boolean = empty in 1..responded
}

/**
 * Mémoire des refus de la garde, par source et par partie : taille reçue, premier refus, nombre de refus. Sert à accepter
 * un changement durable (voir [CatalogGuard]). Aucune donnée personnelle : des nombres et une date.
 */
@Singleton
class SyncGuardMemory internal constructor(
    private val read: (String) -> String?,
    private val write: (String, String?) -> Unit,
    internal var clock: () -> Long = System::currentTimeMillis,
) {
    @Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext ctx: android.content.Context) : this(
        { k -> ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(k, null) },
        { k, v -> ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().apply { if (v == null) remove(k) else putString(k, v) }.apply() },
    )

    /**
     * Note un refus de [received] éléments pour [key] ; vrai si ce même résultat (non vide) revient depuis assez longtemps
     * pour être un changement durable — la mémoire est alors effacée et la liste doit être acceptée.
     */
    fun refusedIsDurable(key: String, received: Int): Boolean {
        if (received <= 0) return false
        val now = clock()
        val prev = read(key)?.split('|')?.takeIf { it.size == 3 }?.let { Triple(it[0].toIntOrNull(), it[1].toLongOrNull(), it[2].toIntOrNull()) }
        val same = prev?.first != null && prev.second != null && prev.third != null && CatalogGuard.sameSize(prev.first!!, received) && now >= prev.second!!
        val first = if (same) prev!!.second!! else now
        val n = if (same) prev!!.third!! + 1 else 1
        if (CatalogGuard.durable(n, now - first)) { write(key, null); return true }
        write(key, "$received|$first|$n")
        return false
    }

    /** Liste acceptée : les refus précédents ne comptent plus. */
    fun clear(key: String) { if (read(key) != null) write(key, null) }

    companion object {
        private const val PREFS = "sync_guard"

        fun inMemory(clock: () -> Long = System::currentTimeMillis): SyncGuardMemory {
            val m = java.util.concurrent.ConcurrentHashMap<String, String>()
            return SyncGuardMemory({ m[it] }, { k, v -> if (v == null) m.remove(k) else m[k] = v }, clock)
        }
    }
}
