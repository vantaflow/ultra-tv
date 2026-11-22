package com.ultratv.tv.nativeapp.ui.settings

import com.ultratv.tv.nativeapp.data.prefs.UserPrefs

/**
 * Réglages avancés (Réglages › Lecture) : masqués par défaut pour une installation neuve, affichés d'office pour un
 * utilisateur existant, et un réglage avancé dont la valeur diffère du défaut reste toujours visible (on ne cache jamais
 * ce que l'utilisateur a changé). Rien n'est supprimé : l'interrupteur « Afficher les réglages avancés » les montre tous.
 */
enum class AdvancedRow { ENGINE, DECODER, BUFFER, MAX_HEIGHT, VIDEO_QUALITY, PREFERRED_QUALITY, AUTO_FPS, TUNNELED, VIDEO_SURFACE }

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
    }

    fun visible(p: UserPrefs, row: AdvancedRow): Boolean = p.showAdvanced || customized(p, row)

    /** Première lecture après la mise à jour : affichés pour qui utilisait déjà l'appli (assistant déjà passé). */
    fun initialShow(hasSeenOnboarding: Boolean): Boolean = hasSeenOnboarding
}
