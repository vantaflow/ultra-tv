package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.data.xmltv.GuideAddress
import com.ultratv.tv.nativeapp.data.xmltv.GuideCheck
import com.ultratv.tv.nativeapp.data.xmltv.GuideMode
import com.ultratv.tv.nativeapp.R

/**
 * Libellés du guide personnalisé : adresse du guide complémentaire (Réglages › Synchronisation) et « Guide de cette
 * source » (FR · EN · ES · AR). Fichier à part pour ne pas toucher à [DesignStrings].
 */

val DesignStrings.guideCustomOption get() = tx(R.string.guide_guide_custom_option)
fun DesignStrings.guideCustomLabel(host: String) = tx(R.string.guide_guide_custom_label, host)
val DesignStrings.guideDialogTitle get() = tx(R.string.guide_guide_dialog_title)
val DesignStrings.guideDialogSubtitle get() = tx(R.string.guide_guide_dialog_subtitle)
val DesignStrings.guideAddressLabel get() = tx(R.string.guide_guide_address_label)
val DesignStrings.guideVerifyAndSave get() = tx(R.string.guide_guide_verify_and_save)
val DesignStrings.guideSave get() = tx(R.string.guide_guide_save)
val DesignStrings.guideVerifying get() = tx(R.string.guide_guide_verifying)
val DesignStrings.guideSaved get() = tx(R.string.guide_guide_saved)
val DesignStrings.guideRemoved get() = tx(R.string.guide_guide_removed)
val DesignStrings.guideFromPhone get() = tx(R.string.guide_guide_from_phone)

/** Message d'erreur (ou null si l'adresse est bonne) pour un verdict de vérification. */
fun DesignStrings.guideCheckMessage(c: GuideCheck): String? = when (c) {
    GuideCheck.Ok -> null
    is GuideCheck.Invalid -> when (c.problem) {
        GuideAddress.Problem.EMPTY -> tx(R.string.guide_guide_check_message_empty)
        GuideAddress.Problem.SCHEME -> tx(R.string.guide_guide_check_message_scheme)
        GuideAddress.Problem.HOST -> tx(R.string.guide_guide_check_message_host)
        GuideAddress.Problem.EXTENSION -> tx(R.string.guide_guide_check_message_extension)
    }
    GuideCheck.Unreachable -> tx(R.string.guide_guide_check_message_unreachable)
    is GuideCheck.Http -> tx(R.string.guide_guide_check_message_http, c.code)
    GuideCheck.NotXmltv -> tx(R.string.guide_guide_check_message_not_xmltv)
}

// ---- Guide de cette source ----

val DesignStrings.sourceGuide get() = tx(R.string.guide_source_guide)
val DesignStrings.sourceGuideTitle get() = tx(R.string.guide_source_guide_title)
val DesignStrings.sourceGuideSubtitle get() = tx(R.string.guide_source_guide_subtitle)
val DesignStrings.sourceGuideModeLabel get() = tx(R.string.guide_source_guide_mode_label)
fun DesignStrings.sourceGuideMode(m: GuideMode) = when (m) {
    GuideMode.REPLACE -> tx(R.string.guide_source_guide_mode_replace)
    GuideMode.COMPLETE -> tx(R.string.guide_source_guide_mode_complete)
}
fun DesignStrings.sourceGuideModeHint(m: GuideMode) = when (m) {
    GuideMode.REPLACE -> tx(R.string.guide_source_guide_mode_hint_replace)
    GuideMode.COMPLETE -> tx(R.string.guide_source_guide_mode_hint_complete)
}
fun DesignStrings.sourceGuideValue(m: GuideMode, host: String) =
    if (m == GuideMode.REPLACE) tx(R.string.guide_source_value_replaces, host) else tx(R.string.guide_source_value_completes, host)
