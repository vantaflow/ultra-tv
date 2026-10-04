package com.ultratv.tv.nativeapp.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Textes des écrans « Claude Design », EN / FR / ES / AR côte à côte (une ligne = une chaîne,
 * impossible d'oublier une langue). Complète [Strings]. Aucune donnée factice : ce ne sont que
 * des libellés d'interface.
 */
class DesignStrings(val lang: AppLang) {
    private fun t(en: String, fr: String, es: String = en, ar: String = en): String = when (lang) {
        AppLang.French -> fr
        AppLang.Spanish -> es
        AppLang.Arabic -> ar
        else -> en
    }

    // Rail
    val railProfile get() = t("Profile", "Profil", "Perfil", "الملف الشخصي")
    val railSwitchProfile get() = t("Switch profile", "Changer de profil", "Cambiar de perfil", "تبديل الملف")

    // Commun
    val live get() = t("LIVE", "EN DIRECT", "EN DIRECTO", "مباشر")
    val watch get() = t("Watch", "Regarder", "Ver", "مشاهدة")
    val tvGuide get() = t("TV Guide", "Guide TV", "Guía TV", "دليل التلفاز")
    val until get() = t("until %s", "jusqu’à %s", "hasta %s", "حتى %s")
    val channelsCount get() = t("%d channels", "%d chaînes", "%d canales", "%d قناة")
    val minLeft get() = t("%d min left", "Reste %d min", "Quedan %d min", "متبقي %d د")
    val hourMinLeft get() = t("%1\$d h %2\$02d left", "Reste %1\$d h %2\$02d", "Quedan %1\$d h %2\$02d", "متبقي %1\$d س %2\$02d")

    // Accueil
    val featured get() = t("Featured", "À la une", "Destacado", "الأبرز")
    val continueWatching get() = t("Continue watching", "Reprendre la lecture", "Seguir viendo", "متابعة المشاهدة")
    val favoriteChannels get() = t("Favorite channels", "Chaînes favorites", "Canales favoritos", "القنوات المفضلة")
    val homeEmpty get() = t("Your catalog is loading…", "Votre catalogue se charge…", "Cargando tu catálogo…", "جارٍ تحميل الكتالوج…")
    val homeNoSource get() = t("Add a source to get started", "Ajoutez une source pour commencer", "Añade una fuente para empezar", "أضف مصدرًا للبدء")
    val syncCloud get() = t("Sync from the cloud", "Synchroniser depuis le cloud", "Sincronizar desde la nube", "المزامنة من السحابة")
    val syncCloudHint get() = t("Shows a short code to enter on the dashboard", "Affiche un code à saisir sur le tableau de bord", "Muestra un código para introducir en el panel", "يعرض رمزًا لإدخاله في لوحة التحكم")

    // Première synchronisation (Chargement.dc.html)
    val firstSync get() = t("FIRST SYNC", "PREMIÈRE SYNCHRONISATION", "PRIMERA SINCRONIZACIÓN", "المزامنة الأولى")
    val preparing get() = t("Preparing your catalog", "Préparation de votre catalogue", "Preparando tu catálogo", "جارٍ تجهيز الكتالوج")
    val preparingBody get() = t("Live channels arrive first. Movies, series and the TV guide continue in the background.", "Les chaînes en direct arrivent en premier. Films, séries et guide TV continuent en arrière-plan.", "Los canales en directo llegan primero. Películas, series y la guía TV siguen en segundo plano.", "تصل القنوات المباشرة أولًا. تستمر الأفلام والمسلسلات والدليل في الخلفية.")
    val etaAbout get() = t("about %s left", "environ %s restantes", "unos %s restantes", "حوالي %s متبقية")
    val etaLessThanMinute get() = t("under a minute", "moins d’une minute", "menos de un minuto", "أقل من دقيقة")
    val etaMinutes get() = t("%d min", "%d min", "%d min", "%d د")
    val watchLive get() = t("Watch live TV", "Regarder le direct", "Ver el directo", "مشاهدة المباشر")
    val availableWhenReady get() = t("Available as soon as the channels are ready", "Disponible dès que les chaînes sont prêtes", "Disponible en cuanto los canales estén listos", "متاح بمجرد جاهزية القنوات")
    val stepLive get() = t("Live channels", "Chaînes en direct", "Canales en directo", "القنوات المباشرة")
    val stepMovies get() = t("Movies", "Films", "Películas", "الأفلام")
    val stepSeries get() = t("Series", "Séries", "Series", "المسلسلات")
    val stepGuide get() = t("TV guide", "Guide TV", "Guía TV", "دليل التلفاز")
    val stateWaiting get() = t("Waiting", "En attente", "En espera", "في الانتظار")
    val stateDone get() = t("Done", "Terminé", "Listo", "اكتمل")
    val stateFailed get() = t("Failed", "Échec", "Error", "فشل")
    val countChannels get() = t("%s channels", "%s chaînes", "%s canales", "%s قناة")
    val countMovies get() = t("%s movies", "%s films", "%s películas", "%s فيلم")
    val countSeries get() = t("%s series", "%s séries", "%s series", "%s مسلسل")
    val countProgrammes get() = t("%s programmes", "%s programmes", "%s programas", "%s برنامج")
    val nextOpenInstant get() = t("Next launch will be instant: the catalog is kept on the device and updated in the background.", "La prochaine ouverture sera instantanée : le catalogue est gardé sur l’appareil et mis à jour en arrière-plan.", "La próxima apertura será instantánea: el catálogo se guarda en el dispositivo y se actualiza en segundo plano.", "الفتح التالي فوري: يُحفظ الكتالوج على الجهاز ويُحدَّث في الخلفية.")
    val hintOk get() = t("OK", "OK")
    val hintWatch get() = t("Watch", "Regarder", "Ver", "مشاهدة")
    val retry get() = t("Retry", "Réessayer", "Reintentar", "إعادة المحاولة")
    val fixSource get() = t("Fix the source", "Corriger la source", "Corregir la fuente", "تصحيح المصدر")
    // Direct
    val directTitle get() = t("Live", "Direct", "Directo", "مباشر")
    val catFavorites get() = t("Favorites", "Favoris", "Favoritos", "المفضلة")
    val catAll get() = t("All", "Tout", "Todo", "الكل")
    val categoryHeader get() = t("%1\$s · %2\$s channels", "%1\$s · %2\$s chaînes", "%1\$s · %2\$s canales", "%1\$s · %2\$s قناة")
    val noChannels get() = t("No channels in this category", "Aucune chaîne dans cette catégorie", "No hay canales en esta categoría", "لا توجد قنوات في هذه الفئة")
    val noFavorites get() = t("No favorites yet — hold OK on a channel to add one", "Pas encore de favoris — maintenez OK sur une chaîne pour en ajouter", "Aún no hay favoritos: mantén OK en un canal para añadir uno", "لا مفضلات بعد — اضغط مطولًا على OK لإضافة قناة")
    val upNext get() = t("UP NEXT", "À SUIVRE", "A CONTINUACIÓN", "التالي")
    val noProgramInfo get() = t("No programme information", "Aucune information de programme", "Sin información de programa", "لا توجد معلومات عن البرنامج")
    val addFavorite get() = t("Add to favorites", "Ajouter aux favoris", "Añadir a favoritos", "إضافة إلى المفضلة")
    val removeFavorite get() = t("Remove from favorites", "Retirer des favoris", "Quitar de favoritos", "إزالة من المفضلة")
    val lockChannel get() = t("Lock channel", "Verrouiller la chaîne", "Bloquear canal", "قفل القناة")
    val unlockChannel get() = t("Unlock channel", "Déverrouiller la chaîne", "Desbloquear canal", "فتح القناة")
    val close get() = t("Close", "Fermer", "Cerrar", "إغلاق")
    val hourShort get() = t("%d h", "%d h", "%d h", "%d س")
    val minShort get() = t("%d min", "%d min", "%d min", "%d د")
    val loading get() = t("Loading…", "Chargement…", "Cargando…", "جارٍ التحميل…")
    // Guide
    val today get() = t("Today", "Aujourd’hui", "Hoy", "اليوم")
    val tomorrow get() = t("Tomorrow", "Demain", "Mañana", "غدًا")
    val guideNoData get() = t("The programme guide is still loading. It will appear here as soon as it is ready.", "Le guide des programmes se charge encore. Il apparaîtra ici dès qu’il sera prêt.", "La guía de programas aún se está cargando.", "لا يزال دليل البرامج قيد التحميل.")
    val remind get() = t("Remind me", "Me le rappeler", "Recordármelo", "ذكّرني")
    // Films / Séries
    val moviesTitle get() = t("Movies", "Films", "Películas", "الأفلام")
    val seriesTitle get() = t("Series", "Séries", "Series", "المسلسلات")
    val allChip get() = t("All", "Tous", "Todos", "الكل")
    val noMovies get() = t("No movies yet — your catalog is still loading.", "Aucun film pour l’instant — votre catalogue se charge encore.", "Aún no hay películas: el catálogo se está cargando.", "لا توجد أفلام بعد — لا يزال الكتالوج قيد التحميل.")
    val noSeries get() = t("No series yet — your catalog is still loading.", "Aucune série pour l’instant — votre catalogue se charge encore.", "Aún no hay series: el catálogo se está cargando.", "لا توجد مسلسلات بعد — لا يزال الكتالوج قيد التحميل.")
    // Lecteur
    val pTracks get() = t("Tracks", "Pistes", "Pistas", "المسارات")
    val pDisplay get() = t("Display", "Affichage", "Pantalla", "العرض")
    val pRecord get() = t("Record", "Enregistrer", "Grabar", "تسجيل")
    val pChannels get() = t("Channels", "Chaînes", "Canales", "القنوات")
    val pPlayer get() = t("Player", "Lecteur", "Reproductor", "المشغل")
    val errNoResponse get() = t("This channel is not responding", "Cette chaîne ne répond pas", "Este canal no responde", "هذه القناة لا تستجيب")
    val errRefused get() = t("The provider refused the connection", "Le fournisseur a refusé la connexion", "El proveedor rechazó la conexión", "رفض المزود الاتصال")
    val errRefusedHint get() = t("Another device or stream may already be using your single connection.", "Un autre appareil ou flux utilise peut-être déjà votre connexion unique.", "Otro dispositivo o flujo puede estar usando tu única conexión.", "ربما يستخدم جهاز أو بث آخر اتصالك الوحيد.")
    val errNetwork get() = t("Network problem", "Problème de réseau", "Problema de red", "مشكلة في الشبكة")
    val errFormat get() = t("This format cannot be played", "Ce format ne peut pas être lu", "No se puede reproducir este formato", "لا يمكن تشغيل هذا التنسيق")
    val errNotFound get() = t("This channel no longer exists", "Cette chaîne n’existe plus", "Este canal ya no existe", "لم تعد هذه القناة موجودة")
    val nextChannel get() = t("Next channel", "Chaîne suivante", "Canal siguiente", "القناة التالية")
    val noticeVlc get() = t("Playing with VLC", "Lecture avec VLC", "Reproduciendo con VLC", "التشغيل عبر VLC")
    val noticeExo get() = t("Playing with ExoPlayer", "Lecture avec ExoPlayer", "Reproduciendo con ExoPlayer", "التشغيل عبر ExoPlayer")
    val noticeSoftware get() = t("Software decoding", "Décodage logiciel", "Decodificación por software", "فك الترميز البرمجي")
    val noticeRetry get() = t("Reconnecting…", "Reconnexion…", "Reconectando…", "إعادة الاتصال…")
    val engine get() = t("Player engine", "Moteur de lecture", "Motor de reproducción", "محرك التشغيل")
    val decoding get() = t("Decoding", "Décodage", "Decodificación", "فك الترميز")
    val bufferMemory get() = t("Buffer memory", "Mémoire tampon", "Memoria de búfer", "ذاكرة التخزين المؤقت")
    val auto get() = t("Auto", "Auto", "Auto", "تلقائي")
    val hardware get() = t("Hardware", "Matériel", "Hardware", "عتاد")
    val software get() = t("Software", "Logiciel", "Software", "برمجي")
    val bufLow get() = t("Low latency", "Faible latence", "Baja latencia", "زمن انتقال منخفض")
    val bufBalanced get() = t("Balanced", "Équilibré", "Equilibrado", "متوازن")
    val bufStable get() = t("Stable", "Stable", "Estable", "مستقر")
    val bufCustom get() = t("Custom", "Personnalisé", "Personalizado", "مخصص")
    val aspectFit get() = t("Fit", "Ajusté", "Ajustar", "ملاءمة")
    val aspectFill get() = t("Fill", "Rempli", "Rellenar", "تعبئة")
    val aspectZoom get() = t("Zoom", "Zoom", "Zoom", "تكبير")
    val speed get() = t("Speed", "Vitesse", "Velocidad", "السرعة")
    val sleepTimer get() = t("Sleep timer", "Minuterie", "Temporizador", "مؤقت النوم")
    val statsLabel get() = t("Statistics", "Statistiques", "Estadísticas", "الإحصاءات")
    val audio get() = t("Audio", "Audio", "Audio", "الصوت")
    val subtitles get() = t("Subtitles", "Sous-titres", "Subtítulos", "الترجمة")
    val off get() = t("Off", "Désactivés", "Desactivados", "إيقاف")
    val bufferClamped get() = t("Buffer reduced to fit this device's memory", "Tampon réduit pour tenir dans la mémoire de cet appareil", "Búfer reducido para la memoria de este dispositivo", "تم تقليل المخزن المؤقت ليناسب ذاكرة الجهاز")
    val engineExo get() = t("ExoPlayer", "ExoPlayer", "ExoPlayer", "ExoPlayer")
    val engineVlc get() = t("VLC", "VLC", "VLC", "VLC")
    val sectionsCount get() = t("%d sections", "%d sections", "%d secciones", "%d أقسام")
    val syncing get() = t("Syncing", "Synchronisation", "Sincronizando", "جارٍ المزامنة")
}

val LocalDs = compositionLocalOf { DesignStrings(AppLang.English) }

@Composable
fun designStringsFor(lang: AppLang): DesignStrings {
    val resolved = if (lang == AppLang.System) {
        val sys = LocalConfiguration.current.locales.get(0)?.language ?: "en"
        AppLang.entries.firstOrNull { it.code == sys } ?: AppLang.English
    } else lang
    return remember(resolved) { DesignStrings(resolved) }
}
