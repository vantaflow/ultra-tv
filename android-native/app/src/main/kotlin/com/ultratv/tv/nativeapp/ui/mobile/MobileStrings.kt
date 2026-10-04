package com.ultratv.tv.nativeapp.ui.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.ultratv.tv.nativeapp.i18n.AppLang

/**
 * Libellés ajoutés par l'interface tactile (téléphone / tablette), FR · EN · ES · AR.
 * Fichier dédié : les tables globales (Strings, DesignStrings) ne sont pas touchées, ce qui évite
 * tout conflit avec les autres chantiers de libellés.
 */
data class MobileStrings(
    val tabMore: String,
    val moreTitle: String,
    val navProfiles: String,
    val seeAll: String,
    val a11ySearch: String,
    val a11yProfile: String,
    val a11yBack: String,
    val a11yClose: String,
    val a11yCast: String,
    val a11ySettings: String,
    val a11yMenu: String,
    val a11yPlay: String,
    val a11yPause: String,
    val a11ySeekBack: String,
    val a11ySeekForward: String,
    val brightness: String,
    val volume: String,
    val pip: String,
    val channels: String,
    val tracks: String,
    val fromStart: String,
    val sleep: String,
    val languagesLabel: String,
    val nextChannel: String,
    val previousChannel: String,
    val aspectChanged: String,
    val tapToWatchFull: String,
    val myList: String,
    val inMyList: String,
    val episodes: String,
    val season: String,
    val moreInfo: String,
    val lessInfo: String,
    val categoriesLabel: String,
    val liveNow: String,
    val pullToRefresh: String,
    val refreshing: String,
    val clearSearch: String,
    val searchHint: String,
    val details: String,
    val noPreview: String,
    val rotateHint: String,
    val castUnavailable: String,
    val pipUnavailable: String,
    val longPressHint: String,
) {
    companion object {
        val FR = MobileStrings(
            tabMore = "Plus", moreTitle = "Plus", navProfiles = "Profils", seeAll = "Tout voir",
            a11ySearch = "Rechercher", a11yProfile = "Profil", a11yBack = "Retour", a11yClose = "Fermer", a11yCast = "Diffuser",
            a11ySettings = "Réglages du lecteur", a11yMenu = "Menu", a11yPlay = "Lecture", a11yPause = "Pause",
            a11ySeekBack = "Reculer de 10 secondes", a11ySeekForward = "Avancer de 10 secondes",
            brightness = "Luminosité", volume = "Volume", pip = "Image dans l’image", channels = "Chaînes", tracks = "Pistes",
            fromStart = "Depuis le début", sleep = "Veille", languagesLabel = "Langues", nextChannel = "Chaîne suivante", previousChannel = "Chaîne précédente",
            aspectChanged = "Format d’image", tapToWatchFull = "Toucher pour le plein écran", myList = "Ma liste", inMyList = "Dans ma liste",
            episodes = "Épisodes", season = "Saison", moreInfo = "Plus", lessInfo = "Moins", categoriesLabel = "Catégories", liveNow = "En direct",
            pullToRefresh = "Tirer pour actualiser", refreshing = "Actualisation…", clearSearch = "Effacer", searchHint = "Chaînes, films, séries",
            details = "Détails", noPreview = "Aucun aperçu", rotateHint = "Tournez l’appareil pour le plein écran",
            castUnavailable = "Aucun appareil de diffusion détecté", pipUnavailable = "Image dans l’image indisponible", longPressHint = "Appui long : options",
        )
        val EN = MobileStrings(
            tabMore = "More", moreTitle = "More", navProfiles = "Profiles", seeAll = "See all",
            a11ySearch = "Search", a11yProfile = "Profile", a11yBack = "Back", a11yClose = "Close", a11yCast = "Cast",
            a11ySettings = "Player settings", a11yMenu = "Menu", a11yPlay = "Play", a11yPause = "Pause",
            a11ySeekBack = "Back 10 seconds", a11ySeekForward = "Forward 10 seconds",
            brightness = "Brightness", volume = "Volume", pip = "Picture-in-picture", channels = "Channels", tracks = "Tracks",
            fromStart = "From the start", sleep = "Sleep", languagesLabel = "Languages", nextChannel = "Next channel", previousChannel = "Previous channel",
            aspectChanged = "Aspect ratio", tapToWatchFull = "Tap for full screen", myList = "My list", inMyList = "In my list",
            episodes = "Episodes", season = "Season", moreInfo = "More", lessInfo = "Less", categoriesLabel = "Categories", liveNow = "Live",
            pullToRefresh = "Pull to refresh", refreshing = "Refreshing…", clearSearch = "Clear", searchHint = "Channels, movies, series",
            details = "Details", noPreview = "No preview", rotateHint = "Rotate the device for full screen",
            castUnavailable = "No cast device found", pipUnavailable = "Picture-in-picture unavailable", longPressHint = "Long press: options",
        )
        val ES = MobileStrings(
            tabMore = "Más", moreTitle = "Más", navProfiles = "Perfiles", seeAll = "Ver todo",
            a11ySearch = "Buscar", a11yProfile = "Perfil", a11yBack = "Atrás", a11yClose = "Cerrar", a11yCast = "Transmitir",
            a11ySettings = "Ajustes del reproductor", a11yMenu = "Menú", a11yPlay = "Reproducir", a11yPause = "Pausa",
            a11ySeekBack = "Retroceder 10 segundos", a11ySeekForward = "Avanzar 10 segundos",
            brightness = "Brillo", volume = "Volumen", pip = "Imagen en imagen", channels = "Canales", tracks = "Pistas",
            fromStart = "Desde el principio", sleep = "Reposo", languagesLabel = "Idiomas", nextChannel = "Canal siguiente", previousChannel = "Canal anterior",
            aspectChanged = "Formato de imagen", tapToWatchFull = "Toca para pantalla completa", myList = "Mi lista", inMyList = "En mi lista",
            episodes = "Episodios", season = "Temporada", moreInfo = "Más", lessInfo = "Menos", categoriesLabel = "Categorías", liveNow = "En directo",
            pullToRefresh = "Desliza para actualizar", refreshing = "Actualizando…", clearSearch = "Borrar", searchHint = "Canales, películas, series",
            details = "Detalles", noPreview = "Sin vista previa", rotateHint = "Gira el dispositivo para pantalla completa",
            castUnavailable = "No se encontró ningún dispositivo", pipUnavailable = "Imagen en imagen no disponible", longPressHint = "Pulsación larga: opciones",
        )
        val AR = MobileStrings(
            tabMore = "المزيد", moreTitle = "المزيد", navProfiles = "الملفات الشخصية", seeAll = "عرض الكل",
            a11ySearch = "بحث", a11yProfile = "الملف الشخصي", a11yBack = "رجوع", a11yClose = "إغلاق", a11yCast = "بث",
            a11ySettings = "إعدادات المشغل", a11yMenu = "القائمة", a11yPlay = "تشغيل", a11yPause = "إيقاف مؤقت",
            a11ySeekBack = "الرجوع 10 ثوانٍ", a11ySeekForward = "التقدم 10 ثوانٍ",
            brightness = "السطوع", volume = "الصوت", pip = "صورة داخل صورة", channels = "القنوات", tracks = "المسارات",
            fromStart = "من البداية", sleep = "السكون", languagesLabel = "اللغات", nextChannel = "القناة التالية", previousChannel = "القناة السابقة",
            aspectChanged = "نسبة الصورة", tapToWatchFull = "المس لملء الشاشة", myList = "قائمتي", inMyList = "في قائمتي",
            episodes = "الحلقات", season = "الموسم", moreInfo = "المزيد", lessInfo = "أقل", categoriesLabel = "الفئات", liveNow = "مباشر",
            pullToRefresh = "اسحب للتحديث", refreshing = "جارٍ التحديث…", clearSearch = "مسح", searchHint = "قنوات، أفلام، مسلسلات",
            details = "التفاصيل", noPreview = "لا معاينة", rotateHint = "أدر الجهاز لملء الشاشة",
            castUnavailable = "لم يُعثر على جهاز بث", pipUnavailable = "صورة داخل صورة غير متاحة", longPressHint = "اضغط مطولاً: خيارات",
        )

        fun forLang(lang: AppLang, systemLanguage: String): MobileStrings {
            val resolved = if (lang == AppLang.System) AppLang.entries.firstOrNull { it.code == systemLanguage } ?: AppLang.English else lang
            return when (resolved) {
                AppLang.French -> FR
                AppLang.Spanish -> ES
                AppLang.Arabic -> AR
                else -> EN
            }
        }
    }
}

val LocalMobileStrings = compositionLocalOf { MobileStrings.EN }

@Composable
fun mobileStringsFor(lang: AppLang): MobileStrings {
    val sys = LocalConfiguration.current.locales.get(0)?.language ?: "en"
    return remember(lang, sys) { MobileStrings.forLang(lang, sys) }
}
