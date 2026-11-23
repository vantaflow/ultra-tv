package com.ultratv.tv.nativeapp.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.ultratv.tv.nativeapp.data.prefs.UserPrefs

/**
 * Niveau GLOBAL des réglages (en tête de la page Réglages), par APPAREIL (préférence de l'appareil, pas du profil) :
 * - [SIMPLE] masque dans toutes les rubriques les réglages techniques (moteur de lecture, tampon, DNS, fuseau horaire…) ;
 * - [EXPERT] montre tout.
 * Un réglage technique dont la valeur diffère du défaut reste TOUJOURS visible (on ne cache jamais ce que l'utilisateur a
 * changé) et rien n'est supprimé : repasser en Expert montre tout. Remplace l'interrupteur « Afficher les réglages
 * avancés » de Réglages › Lecture (1.3.73), dont la valeur enregistrée est reprise une fois (voir [SettingsLevels.resolve]).
 */
enum class SettingsLevel(val key: String) {
    SIMPLE("simple"), EXPERT("expert");

    companion object {
        /** null pour une valeur absente ou inconnue (la règle de départ s'applique alors). */
        fun parse(raw: String?): SettingsLevel? = entries.firstOrNull { it.key == raw }
    }
}

/** Niveau lu par les volets des Réglages ; hors des Réglages (défaut) rien n'est masqué. */
val LocalSettingsLevel = staticCompositionLocalOf { SettingsLevel.EXPERT }

object SettingsLevels {
    /**
     * Niveau effectif.
     * - Un niveau CHOISI dans le sélecteur ([chosen]) ou déjà fixé par la reprise ([migrated]) est gardé tel quel.
     * - Sinon, une valeur enregistrée n'est gardée que si elle ne peut pas venir de l'écriture automatique des
     *   pré-versions : 1.3.73-rc8 écrivait au premier démarrage show_advanced = « assistant passé », puis rc9 / 1.3.73
     *   figeaient settings_level d'après cette valeur. Or l'assistant n'est jamais proposé quand des sources existent
     *   déjà : un utilisateur existant avait « assistant passé » faux, d'où show_advanced = faux puis « simple » écrits
     *   sans qu'il ait rien choisi (box de 1.3.72 mise à jour). Une valeur égale à ce que l'écriture automatique aurait
     *   produit est donc ignorée.
     * - Enfin : l'ancien interrupteur S1 s'il diffère de sa valeur automatique (« afficher » = Expert, « masquer » =
     *   Simple), sinon Expert pour un utilisateur existant (assistant passé OU sources déjà présentes), Simple pour une
     *   installation neuve.
     */
    fun resolve(
        stored: String?,
        legacyShowAdvanced: Boolean?,
        hasSeenOnboarding: Boolean,
        hasProviders: Boolean = false,
        chosen: Boolean = false,
        migrated: Boolean = false,
    ): SettingsLevel {
        val parsed = SettingsLevel.parse(stored)
        if (parsed != null && (chosen || migrated)) return parsed
        // Écriture automatique de rc8 : show_advanced = hasSeenOnboarding ; une autre valeur a été choisie à la main.
        val legacyChoice = legacyShowAdvanced?.takeIf { it != hasSeenOnboarding }?.let { if (it) SettingsLevel.EXPERT else SettingsLevel.SIMPLE }
        // Écriture automatique de rc9 / 1.3.73 : l'ancienne règle (S1 enregistré tel quel, sinon assistant passé).
        val autoWritten = when (legacyShowAdvanced) {
            true -> SettingsLevel.EXPERT
            false -> SettingsLevel.SIMPLE
            null -> if (hasSeenOnboarding) SettingsLevel.EXPERT else SettingsLevel.SIMPLE
        }
        if (parsed != null && parsed != autoWritten) return parsed
        return legacyChoice ?: if (hasSeenOnboarding || hasProviders) SettingsLevel.EXPERT else SettingsLevel.SIMPLE
    }

    /** Un réglage technique est affiché en Expert, ou en Simple si sa valeur diffère du défaut. */
    fun show(level: SettingsLevel, customized: Boolean = false): Boolean = level == SettingsLevel.EXPERT || customized

    /**
     * Rubrique affichée : toujours si elle porte au moins un réglage non technique ; une rubrique ENTIÈREMENT technique est
     * masquée en Simple, sauf si l'un de ses réglages a été modifié.
     */
    fun rubVisible(level: SettingsLevel, technicalOnly: Boolean, anyCustomized: Boolean): Boolean =
        !technicalOnly || show(level, anyCustomized)
}

/** Raccourci des volets : réglage technique affiché au niveau courant (ou parce que [customized]). */
@Composable
fun showTechnical(customized: Boolean = false): Boolean = SettingsLevels.show(LocalSettingsLevel.current, customized)

/**
 * Réglages techniques de UserPrefs (toutes rubriques) : masqués en Simple sauf valeur différente du défaut.
 * Lecture : moteur, décodage, tampon, qualité, fréquence, tunnel, surface ; Synchronisation : contenus, Wi-Fi seul, guide
 * XMLTV supplémentaire ; Affichage : fuseau horaire, format de l'heure, logos locaux ; Langues : MULTI / langue inconnue ;
 * Sources : adresse du Worker.
 */
enum class AdvancedRow {
    ENGINE, DECODER, BUFFER, MAX_HEIGHT, VIDEO_QUALITY, PREFERRED_QUALITY, AUTO_FPS, TUNNELED, VIDEO_SURFACE,
    SYNC_LIVE, SYNC_EPG, SYNC_VOD, SYNC_SERIES, SYNC_UNMETERED, EXTRA_GUIDE,
    TIME_ZONE, TIME_FORMAT, LOCAL_LOGOS,
    INCLUDE_MULTI, INCLUDE_UNKNOWN,
    WORKER_URL,
}

object AdvancedSettings {
    private val defaults = UserPrefs()

    /** Valeur différente du défaut. */
    fun customized(p: UserPrefs, row: AdvancedRow): Boolean = when (row) {
        AdvancedRow.ENGINE -> p.playerEngine != defaults.playerEngine
        AdvancedRow.DECODER -> p.decoderMode != defaults.decoderMode
        AdvancedRow.BUFFER -> p.bufferPreset != defaults.bufferPreset
        AdvancedRow.MAX_HEIGHT -> p.maxHeight != defaults.maxHeight
        AdvancedRow.VIDEO_QUALITY -> p.videoQuality != defaults.videoQuality
        AdvancedRow.PREFERRED_QUALITY -> p.preferredQuality != defaults.preferredQuality
        AdvancedRow.AUTO_FPS -> p.autoFrameRate != defaults.autoFrameRate
        AdvancedRow.TUNNELED -> p.tunneledPlayback != defaults.tunneledPlayback
        AdvancedRow.VIDEO_SURFACE -> p.videoSurface != defaults.videoSurface
        AdvancedRow.SYNC_LIVE -> p.syncLive != defaults.syncLive
        AdvancedRow.SYNC_EPG -> p.syncEpg != defaults.syncEpg
        AdvancedRow.SYNC_VOD -> p.syncVod != defaults.syncVod
        AdvancedRow.SYNC_SERIES -> p.syncSeries != defaults.syncSeries
        AdvancedRow.SYNC_UNMETERED -> p.syncUnmeteredOnly != defaults.syncUnmeteredOnly
        AdvancedRow.EXTRA_GUIDE -> p.extraEpg != defaults.extraEpg
        AdvancedRow.TIME_ZONE -> p.timeZone != defaults.timeZone
        AdvancedRow.TIME_FORMAT -> p.timeFormat != defaults.timeFormat
        AdvancedRow.LOCAL_LOGOS -> p.localLogosFolderUri != defaults.localLogosFolderUri
        AdvancedRow.INCLUDE_MULTI -> p.includeMulti != defaults.includeMulti
        AdvancedRow.INCLUDE_UNKNOWN -> p.includeUnknownLang != defaults.includeUnknownLang
        AdvancedRow.WORKER_URL -> p.workerBaseUrl != defaults.workerBaseUrl
    }

    fun visible(p: UserPrefs, row: AdvancedRow): Boolean =
        SettingsLevels.show(SettingsLevel.parse(p.settingsLevel) ?: SettingsLevel.EXPERT, customized(p, row))
}
