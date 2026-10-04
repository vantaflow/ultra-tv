package com.ultratv.tv.nativeapp.ui.profile

import com.ultratv.tv.nativeapp.i18n.AppLang

/** Textes de l'écran « Qui regarde ? » et de Réglages › Profils (FR / EN ; les autres langues retombent sur l'anglais). */
class ProfileStrings(private val lang: AppLang) {
    private fun t(en: String, fr: String) = if (lang == AppLang.French) fr else en
    val whoWatching get() = t("Who's watching?", "Qui regarde ?")
    val add get() = t("Add", "Ajouter")
    val manage get() = t("Manage profiles", "Gérer les profils")
    val done get() = t("Done", "Terminé")
    val protectedHint get() = t("PIN protected", "Protégé par PIN")
    val kidsHint get() = t("Kids", "Enfants")
    val lastHint get() = t("Last used", "Dernier utilisé")
    val pinTitle get() = t("Protected profile", "Profil protégé")
    fun pinSubtitle(name: String) = t("Enter the PIN for $name", "Saisissez le code de $name")
    val wrongPin get() = t("Wrong PIN", "Code incorrect")
    val settingsTitle get() = t("Profiles", "Profils")
    val settingsSub get() = t("Favorites, history and display are per profile. Sources and technical settings are shared.", "Favoris, historique et affichage sont propres à chaque profil. Sources et réglages techniques sont partagés.")
    val newProfile get() = t("New profile", "Nouveau profil")
    val rename get() = t("Rename", "Renommer")
    val color get() = t("Avatar color", "Couleur de l'avatar")
    val kidsProfile get() = t("Kids profile", "Profil Enfants")
    val kidsHelp get() = t("Adult categories hidden, parental control forced, technical settings locked", "Catégories adultes masquées, contrôle parental forcé, réglages techniques verrouillés")
    val pin get() = t("Profile PIN", "Code du profil")
    val pinNone get() = t("None", "Aucun")
    val pinSet get() = t("Enabled", "Activé")
    val pinRemove get() = t("Remove PIN", "Retirer le code")
    val delete get() = t("Delete profile", "Supprimer le profil")
    val deleteLast get() = t("The last profile cannot be deleted", "Le dernier profil ne peut pas être supprimé")
    fun deleteConfirm(name: String) = t("Delete \"$name\" and its favorites and history?", "Supprimer « $name » ainsi que ses favoris et son historique ?")
    val confirm get() = t("Delete", "Supprimer")
    val cancel get() = t("Cancel", "Annuler")
    val startup get() = t("At startup", "Au démarrage")
    val alwaysAsk get() = t("Always ask", "Toujours demander")
    val lastProfile get() = t("Last profile", "Dernier profil")
    val switchProfile get() = t("Switch profile", "Changer de profil")
    val nameLabel get() = t("Name", "Nom")
    val save get() = t("Save", "Enregistrer")
    val kidsLocked get() = t("Locked for the Kids profile", "Verrouillé pour le profil Enfants")
    val kidsNeedPin get() = t("Set a parental PIN from another profile to unlock these settings.", "Définissez un code parental depuis un autre profil pour déverrouiller ces réglages.")
    val colorNames get() = if (lang == AppLang.French) listOf("Bleu", "Rose", "Vert", "Ambre", "Violet", "Rouge", "Cyan", "Lime") else listOf("Blue", "Pink", "Green", "Amber", "Purple", "Red", "Cyan", "Lime")
}
