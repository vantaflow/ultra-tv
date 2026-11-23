package com.ultratv.tv.nativeapp.data.parental

import java.util.concurrent.ConcurrentHashMap

/**
 * Film ou épisode d'une catégorie verrouillée lancé par lien profond (écran d'accueil du système : chaîne d'accueil ou
 * « Continuer à regarder » publiés avant le verrouillage, télécommande du téléphone, reprise sur un autre appareil) : le
 * lecteur demande le code parental AVANT toute lecture. Le lien enregistre l'adresse à protéger ([require]) ; le lecteur
 * la lit à son ouverture ([pendingTitle]) et ne lance rien tant que le code n'est pas accepté ([grant]). Mauvais code :
 * rien ne change (toujours en attente) ; annulation ([refuse]) : aucune lecture, retour.
 *
 * Le direct passe par [ParentalGate.admit] (chaîne verrouillée ou de catégorie verrouillée), les fiches par leur panneau
 * verrouillé (DetailLock.PIN).
 */
object DeepLinkLocks {
    private val required = ConcurrentHashMap<String, String>()

    /** Règle pure : le titre de la catégorie [categoryId] exige-t-il le code ? */
    fun isLocked(categoryId: String?, lockedCategoryIds: Set<String>): Boolean = CategoryLocks.isCategoryLocked(categoryId, lockedCategoryIds)

    /** L'adresse [url] ne se lance qu'après le code ; [title] est affiché dans la fenêtre de code. */
    fun require(url: String, title: String) { required[url] = title }

    /** Titre à afficher si [url] attend le code, sinon null (lecture libre). */
    fun pendingTitle(url: String): String? = required[url]

    /**
     * Code saisi pour [url] : vrai (et l'attente levée) seulement si [verify] l'accepte ; faux sinon, l'attente reste et
     * rien n'est lancé.
     */
    suspend fun tryPin(url: String, pin: String, verify: suspend (String) -> Boolean): Boolean {
        if (!required.containsKey(url)) return true
        if (!verify(pin)) return false
        required.remove(url)
        return true
    }

    /** Code accepté par la fenêtre de code : la lecture peut commencer. */
    fun grant(url: String) { required.remove(url) }

    /** Fenêtre annulée : aucune lecture ; un nouveau lien redemandera le code. */
    fun refuse(url: String) { required.remove(url) }

    fun clear() = required.clear()
}
