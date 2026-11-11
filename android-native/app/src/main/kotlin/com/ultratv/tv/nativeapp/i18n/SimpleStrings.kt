package com.ultratv.tv.nativeapp.i18n

/** Textes du mode simple : écran des grandes tuiles, liste de chaînes à gros caractères, réglage (EN / FR / ES / AR). */
class SimpleStrings(private val lang: AppLang) {
    private fun t(en: String, fr: String, es: String = en, ar: String = en) = when (lang) {
        AppLang.French -> fr
        AppLang.Spanish -> es
        AppLang.Arabic -> ar
        else -> en
    }

    // ── Écran des tuiles ──
    val title get() = t("What would you like to do?", "Que voulez-vous faire ?", "¿Qué quiere hacer?", "ماذا تريد أن تفعل؟")
    val tileLive get() = t("Live TV", "Télé en direct", "TV en directo", "التلفاز المباشر")
    val tileResume get() = t("Resume", "Reprendre", "Continuar", "متابعة المشاهدة")
    val tileMovies get() = t("Movies", "Films", "Películas", "الأفلام")
    val tileSeries get() = t("TV shows", "Séries", "Series", "المسلسلات")
    val tileFavorites get() = t("My favorites", "Mes favoris", "Mis favoritos", "مفضلتي")
    val tileSearch get() = t("Search", "Rechercher", "Buscar", "بحث")
    val tileLiveHint get() = t("Last channel or channel list", "Dernière chaîne ou liste des chaînes", "Último canal o lista de canales", "آخر قناة أو قائمة القنوات")
    val tileResumeHint get() = t("Continue where you stopped", "Continuer là où vous vous êtes arrêté", "Seguir donde lo dejó", "تابع من حيث توقفت")
    val tileResumeNothing get() = t("Nothing to resume yet: choose a film", "Rien à reprendre pour l'instant : choisissez un film", "Nada que continuar todavía: elija una película", "لا شيء لمتابعته بعد: اختر فيلمًا")
    val tileMoviesHint get() = t("Choose a film", "Choisir un film", "Elegir una película", "اختر فيلمًا")
    val tileSeriesHint get() = t("Choose a series", "Choisir une série", "Elegir una serie", "اختر مسلسلًا")
    val tileFavoritesHint get() = t("Your saved channels and titles", "Vos chaînes et titres enregistrés", "Sus canales y títulos guardados", "قنواتك وعناوينك المحفوظة")
    val tileSearchHint get() = t("Find a channel, film or series", "Trouver une chaîne, un film ou une série", "Encontrar un canal, película o serie", "ابحث عن قناة أو فيلم أو مسلسل")
    val settings get() = t("Settings", "Réglages", "Ajustes", "الإعدادات")
    val switchProfile get() = t("Change profile", "Changer de profil", "Cambiar de perfil", "تغيير الملف الشخصي")
    val noSource get() = t("No source yet. Ask the person who set up the app, or open Settings.", "Aucune source pour l'instant. Demandez à la personne qui a installé l'appli, ou ouvrez les Réglages.", "Aún no hay fuente. Pregunte a quien instaló la aplicación, o abra los Ajustes.", "لا يوجد مصدر بعد. اسأل من ثبّت التطبيق، أو افتح الإعدادات.")
    val loading get() = t("Loading…", "Chargement…", "Cargando…", "جارٍ التحميل…")

    // ── Liste des chaînes ──
    val channelsTitle get() = t("Choose a channel", "Choisissez une chaîne", "Elija un canal", "اختر قناة")
    val channelsFavoritesFirst get() = t("Your favorites come first", "Vos favoris sont en premier", "Sus favoritos van primero", "مفضلتك أولًا")
    val channelsEmpty get() = t("No channel to show yet.", "Aucune chaîne à afficher pour l'instant.", "Aún no hay canales que mostrar.", "لا توجد قنوات للعرض بعد.")
    val favoriteMark get() = t("Favorite", "Favori", "Favorito", "مفضلة")
    val allChannels get() = t("All the channels", "Toutes les chaînes", "Todos los canales", "كل القنوات")

    // ── Réglages ──
    val settingName get() = t("Simple mode", "Mode simple", "Modo sencillo", "الوضع المبسّط")
    val settingHint get() = t("Big tiles instead of the home screen", "Grandes tuiles à la place de l'accueil", "Grandes mosaicos en lugar del inicio", "بلاطات كبيرة بدل الشاشة الرئيسية")
    val settingHelp get() = t(
        "Big, easy tiles for those who prefer simplicity. This profile only. To leave simple mode, come back to Settings.",
        "De grandes tuiles faciles à choisir, pour ceux qui préfèrent la simplicité. Ce profil seulement. Pour quitter le mode simple, revenez dans les Réglages.",
        "Grandes mosaicos fáciles de elegir, para quien prefiere la sencillez. Solo este perfil. Para salir del modo sencillo, vuelva a los Ajustes.",
        "بلاطات كبيرة وسهلة الاختيار لمن يفضّل البساطة. لهذا الملف الشخصي فقط. للخروج من الوضع المبسّط، عد إلى الإعدادات.",
    )
    val exitTitle get() = t("Leave simple mode", "Quitter le mode simple", "Salir del modo sencillo", "الخروج من الوضع المبسّط")
    val profileOn get() = t("Simple mode: on", "Mode simple : activé", "Modo sencillo: activado", "الوضع المبسّط: مفعّل")
    val profileOff get() = t("Simple mode: off", "Mode simple : désactivé", "Modo sencillo: desactivado", "الوضع المبسّط: معطّل")
    val on get() = t("On", "Activé", "Activado", "مفعّل")
    val off get() = t("Off", "Désactivé", "Desactivado", "معطّل")
}
