package com.ultratv.tv.nativeapp.ui.profile

import com.ultratv.tv.nativeapp.i18n.AppLang

/** Textes de l'écran « Qui regarde ? » et de Réglages › Profils (EN / FR / ES / AR). */
class ProfileStrings(private val lang: AppLang) {
    private fun t(en: String, fr: String, es: String = en, ar: String = en) = when (lang) {
        AppLang.French -> fr
        AppLang.Spanish -> es
        AppLang.Arabic -> ar
        else -> en
    }
    val whoWatching get() = t("Who's watching?", "Qui regarde ?", "¿Quién está viendo?", "من يشاهد؟")
    val add get() = t("Add", "Ajouter", "Añadir", "إضافة")
    val manage get() = t("Manage profiles", "Gérer les profils", "Gestionar perfiles", "إدارة الملفات الشخصية")
    val done get() = t("Done", "Terminé", "Listo", "تم")
    val protectedHint get() = t("PIN protected", "Protégé par PIN", "Protegido con PIN", "محمي برمز PIN")
    val kidsHint get() = t("Kids", "Enfants", "Niños", "الأطفال")
    val lastHint get() = t("Last used", "Dernier utilisé", "Último usado", "آخر استخدام")
    val pinTitle get() = t("Protected profile", "Profil protégé", "Perfil protegido", "ملف محمي")
    fun pinSubtitle(name: String) = t("Enter the PIN for $name", "Saisissez le code de $name", "Introduce el PIN de $name", "أدخل رمز PIN الخاص بـ $name")
    val wrongPin get() = t("Wrong PIN", "Code incorrect", "PIN incorrecto", "رمز PIN غير صحيح")
    val settingsTitle get() = t("Profiles", "Profils", "Perfiles", "الملفات الشخصية")
    val settingsSub get() = t(
        "Favorites, history and display are per profile. Sources and technical settings are shared.",
        "Favoris, historique et affichage sont propres à chaque profil. Sources et réglages techniques sont partagés.",
        "Favoritos, historial y pantalla son propios de cada perfil. Las fuentes y los ajustes técnicos se comparten.",
        "المفضلة والسجل والعرض خاصة بكل ملف شخصي. المصادر والإعدادات التقنية مشتركة.",
    )
    val newProfile get() = t("New profile", "Nouveau profil", "Nuevo perfil", "ملف شخصي جديد")
    val rename get() = t("Rename", "Renommer", "Renombrar", "إعادة التسمية")
    val color get() = t("Avatar color", "Couleur de l'avatar", "Color del avatar", "لون الصورة الرمزية")
    val kidsProfile get() = t("Kids profile", "Profil Enfants", "Perfil infantil", "ملف الأطفال")
    val kidsHelp get() = t(
        "Adult categories hidden, parental control forced, technical settings locked",
        "Catégories adultes masquées, contrôle parental forcé, réglages techniques verrouillés",
        "Categorías para adultos ocultas, control parental forzado, ajustes técnicos bloqueados",
        "إخفاء فئات البالغين، فرض الرقابة الأبوية، قفل الإعدادات التقنية",
    )
    val pin get() = t("Profile PIN", "Code du profil", "PIN del perfil", "رمز PIN للملف")
    val pinNone get() = t("None", "Aucun", "Ninguno", "بدون")
    val pinSet get() = t("Enabled", "Activé", "Activado", "مفعّل")
    val pinRemove get() = t("Remove PIN", "Retirer le code", "Quitar el PIN", "إزالة الرمز")
    val delete get() = t("Delete profile", "Supprimer le profil", "Eliminar perfil", "حذف الملف الشخصي")
    val deleteLast get() = t("The last profile cannot be deleted", "Le dernier profil ne peut pas être supprimé", "No se puede eliminar el último perfil", "لا يمكن حذف آخر ملف شخصي")
    fun deleteConfirm(name: String) = t(
        "Delete \"$name\" and its favorites and history?",
        "Supprimer « $name » ainsi que ses favoris et son historique ?",
        "¿Eliminar «$name» junto con sus favoritos e historial?",
        "هل تريد حذف «$name» مع مفضلته وسجله؟",
    )
    val confirm get() = t("Delete", "Supprimer", "Eliminar", "حذف")
    val cancel get() = t("Cancel", "Annuler", "Cancelar", "إلغاء")
    val startup get() = t("At startup", "Au démarrage", "Al iniciar", "عند التشغيل")
    val alwaysAsk get() = t("Always ask", "Toujours demander", "Preguntar siempre", "اسأل دائمًا")
    val lastProfile get() = t("Last profile", "Dernier profil", "Último perfil", "آخر ملف")
    val switchProfile get() = t("Switch profile", "Changer de profil", "Cambiar de perfil", "تبديل الملف")
    val nameLabel get() = t("Name", "Nom", "Nombre", "الاسم")
    val save get() = t("Save", "Enregistrer", "Guardar", "حفظ")
    val kidsLocked get() = t("Locked for the Kids profile", "Verrouillé pour le profil Enfants", "Bloqueado para el perfil infantil", "مقفل لملف الأطفال")
    val kidsNeedPin get() = t(
        "Set a parental PIN from another profile to unlock these settings.",
        "Définissez un code parental depuis un autre profil pour déverrouiller ces réglages.",
        "Define un PIN parental desde otro perfil para desbloquear estos ajustes.",
        "عيّن رمزًا أبويًا من ملف شخصي آخر لفتح هذه الإعدادات.",
    )
    val colorNames get() = when (lang) {
        AppLang.French -> listOf("Bleu", "Rose", "Vert", "Ambre", "Violet", "Rouge", "Cyan", "Lime")
        AppLang.Spanish -> listOf("Azul", "Rosa", "Verde", "Ámbar", "Morado", "Rojo", "Cian", "Lima")
        AppLang.Arabic -> listOf("أزرق", "وردي", "أخضر", "عنبري", "بنفسجي", "أحمر", "سماوي", "ليموني")
        else -> listOf("Blue", "Pink", "Green", "Amber", "Purple", "Red", "Cyan", "Lime")
    }
}
