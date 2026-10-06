package com.ultratv.tv.nativeapp.data.profile

import java.security.MessageDigest

/** Palette d'avatars (ARGB), alignée sur la maquette « Qui regarde ? ». */
object ProfileColors {
    val palette: List<Int> = listOf(
        0xFF3B82F6.toInt(), 0xFFEC4899.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt(),
        0xFF8B5CF6.toInt(), 0xFFEF4444.toInt(), 0xFF06B6D4.toInt(), 0xFF84CC16.toInt(),
    )
    fun forIndex(i: Int): Int = palette[Math.floorMod(i, palette.size)]
}

/** Heuristique « catégorie adulte » : la même que celle qui verrouille les catégories à l'import. */
object AdultHeuristic {
    // « ero » seul attrapait « Hero », « Heroes », « Zero »… : mot entier (ero, erotic, érotique, erotik…).
    val regex = Regex("xxx|adult|18\\+|porn|\\b[eé]ro(tic|tica|tique|tik|tico)?\\b|adulte|للكبار", RegexOption.IGNORE_CASE)
    fun isAdult(categoryName: String): Boolean = regex.containsMatchIn(categoryName)
}

/** Règles du profil Enfants (pures, testables). */
object KidsRules {
    /** Le contrôle parental est forcé : l'utilisateur ne peut pas l'éteindre. */
    fun forcesParentalControl(p: ProfileEntity?): Boolean = p?.isKids == true

    /** Clés des catégories à masquer pour un profil Enfants (adultes selon l'heuristique). */
    fun adultCategoryKeys(rows: List<CategoryNameRow>): Set<String> =
        rows.filter { AdultHeuristic.isAdult(it.name) }
            .map { "${it.kind}:${it.providerId}:${it.remoteId}" }
            .toSet()

    /** Les réglages techniques (lecteur, tampon, synchro, sources) exigent le PIN parental pour un profil Enfants. */
    fun technicalSettingsNeedPin(p: ProfileEntity?): Boolean = p?.isKids == true

    /** Accès aux réglages techniques : libre hors profil Enfants ; sinon un PIN parental DOIT exister ET avoir été saisi. */
    fun canOpenTechnicalSettings(p: ProfileEntity?, parentalPinSet: Boolean, pinVerified: Boolean): Boolean =
        !technicalSettingsNeedPin(p) || (parentalPinSet && pinVerified)
}

object ProfileRules {
    const val MAX_PROFILES = 8
    const val MAX_NAME = 16

    fun cleanName(raw: String): String = raw.trim().take(MAX_NAME)
    fun initialOf(name: String): String = cleanName(name).firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    fun hashPin(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun pinMatches(p: ProfileEntity, typed: String): Boolean = p.pinHash == null || p.pinHash == hashPin(typed)

    /** Suppression : jamais le dernier profil. */
    fun canDelete(profileCount: Int): Boolean = profileCount > 1

    fun canAdd(profileCount: Int): Boolean = profileCount < MAX_PROFILES
}

/** Quand afficher « Qui regarde ? » au démarrage. */
enum class StartupMode { ALWAYS_ASK, LAST_PROFILE;
    companion object { fun parse(s: String?) = if (s == "LAST_PROFILE") LAST_PROFILE else ALWAYS_ASK }
}

object StartupRules {
    /** Écran de sélection affiché s'il y a plus d'un profil et que le mode est « Toujours demander ». */
    fun shouldAsk(profileCount: Int, mode: StartupMode): Boolean = profileCount > 1 && mode == StartupMode.ALWAYS_ASK
}
