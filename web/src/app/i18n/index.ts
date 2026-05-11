// Lightweight i18n. Mirrors the original app's 26-locale catalogue with English,
// French and Arabic as seed languages; additional locales can be added by dropping
// a JSON-shaped object into `bundles` below.

import { create } from "zustand";
import { settingsRepo } from "@data/db/repositories";

type Bundle = Record<string, string>;

const en: Bundle = {
  // Navigation
  "nav.home": "Home",
  "nav.live": "Live TV",
  "nav.movies": "Movies",
  "nav.series": "Series",
  "nav.guide": "Guide",
  "nav.recordings": "Recordings",
  "nav.search": "Search",
  "nav.multiview": "Multi-View",
  "nav.watchlist": "Watchlist",
  "nav.settings": "Settings",
  // Common
  "common.add": "Add",
  "common.remove": "Remove",
  "common.cancel": "Cancel",
  "common.save": "Save",
  "common.delete": "Delete",
  "common.close": "Close",
  "common.edit": "Edit",
  "common.loading": "Loading…",
  "common.search": "Search",
  "common.all": "All",
  "common.none": "None",
  "common.play": "Play",
  "common.resume": "Resume",
  "common.retry": "Retry",
  "common.next": "Next",
  "common.previous": "Previous",
  "common.empty": "Nothing here yet.",
  "common.error": "Error",
  // Home
  "home.continue": "Continue Watching",
  "home.favorites": "Favorites",
  "home.topMovies": "Top Rated — Movies",
  "home.topSeries": "Top Rated — Series",
  "home.recentMovies": "Recently added — Movies",
  "home.recentSeries": "Recently updated — Series",
  "home.live": "Live TV",
  "home.noProvider": "Add a provider in Settings to start.",
  // Live
  "live.title": "Live TV",
  "live.searchPlaceholder": "Search channel or program…",
  "live.noChannels": "No channels yet. Sync your provider in Settings.",
  "live.channelsCount": "{count} channels",
  // Movies / Series
  "movies.title": "Movies",
  "movies.filter": "Filter…",
  "movies.noMovies": "No movies. Sync your provider.",
  "series.title": "Series",
  "series.noSeries": "No series. Sync your provider.",
  "series.nowPlaying": "Now playing",
  "series.nextEpisode": "Next episode →",
  "movie.cast": "Cast",
  "movie.director": "Director",
  "movie.trailer": "Trailer",
  // Guide
  "guide.title": "Guide",
  "guide.loadEpg": "Load EPG now",
  "guide.reloadEpg": "Reload EPG",
  "guide.loadingEpg": "Loading EPG…",
  "guide.noEpg": "No EPG data loaded yet. Click \"Load EPG now\" — requires an EPG URL on the provider.",
  "guide.programsCount": "{p} programs · {c} channels",
  // Search
  "search.title": "Search",
  "search.placeholder": "Search…",
  "search.recent": "Recent",
  "search.scope.all": "All",
  "search.scope.live": "Live",
  "search.scope.movie": "Movies",
  "search.scope.series": "Series",
  // Multi-view
  "multiview.title": "Multi-View",
  "multiview.hint": "Click a tile to switch its audio",
  // Recordings
  "recordings.title": "Recordings",
  "recordings.noRecordings": "No recordings yet. Start one from the Live TV player.",
  "recordings.intro": "Recordings capture the currently-playing video stream via MediaRecorder and store the result in the browser's Origin Private File System.",
  // Settings
  "settings.tab.providers": "Providers",
  "settings.tab.categories": "Categories",
  "settings.tab.network": "Network",
  "settings.tab.parental": "Parental",
  "settings.tab.appearance": "Appearance",
  "settings.tab.backup": "Backup",
  "settings.providers.title": "Providers",
  "settings.providers.add": "+ Add provider",
  "settings.providers.empty": "No providers yet. Click \"Add provider\".",
  "settings.providers.resync": "Resync",
  "settings.providers.editing": "Editing",
  "settings.network.title": "Network proxy",
  "settings.network.intro": "Proxy that bypasses CORS and forwards User-Agent / Referer. Required for most IPTV providers in the browser.",
  "settings.network.active": "Currently active",
  "settings.network.default": "Built-in default",
  "settings.network.custom": "Custom proxy URL (override the default)",
  "settings.network.reset": "Reset",
  "settings.parental.title": "Parental controls",
  "settings.parental.pinSet": "PIN is set.",
  "settings.parental.noPin": "No PIN configured.",
  "settings.parental.setPin": "Set PIN",
  "settings.parental.clearPin": "Clear PIN",
  "settings.parental.hideLocked": "Hide locked content from browsing",
  "settings.appearance.title": "Appearance",
  "settings.appearance.language": "Language",
  "settings.appearance.theme": "Theme",
  "settings.appearance.dark": "Dark",
  "settings.appearance.light": "Light",
  "settings.appearance.sidebar": "Sidebar",
  "settings.appearance.collapse": "Collapse sidebar",
  "settings.appearance.expand": "Expand sidebar",
  "settings.backup.title": "Backup",
  "settings.backup.export": "Export backup",
  "settings.backup.import": "Import backup",
  "settings.backup.intro": "Backup contains: providers (with credentials), catalog, favorites, history, settings, parental locks, filters. Stored in IndexedDB locally.",
  "settings.categories.title": "Categories",
  "settings.categories.intro": "Choose which categories to display in each section. When a whitelist is set, the next resync only fetches items from the checked categories.",
  "settings.categories.provider": "Provider",
  "settings.categories.live": "Live TV",
  "settings.categories.movies": "Movies",
  "settings.categories.series": "Series",
  "settings.categories.apply": "Apply selection: the next manual resync will only fetch the categories you've checked above.",
  "settings.categories.resyncNow": "Resync this provider",
  // Provider editor
  "provider.type": "Type",
  "provider.name": "Name",
  "provider.serverUrl": "Server URL",
  "provider.username": "Username",
  "provider.password": "Password",
  "provider.m3uUrl": "M3U URL",
  "provider.epgUrl": "EPG URL (optional XMLTV)",
  "provider.userAgent": "User-Agent (optional)",
  "provider.referer": "HTTP Referer (optional)",
  "provider.testConnection": "Test connection",
  "provider.testing": "Testing…",
  "provider.saveAndResync": "Save & Resync",
  "provider.add": "Add provider",
  "provider.edit": "Edit",
  // Category filter
  "catfilter.label": "Categories",
  "catfilter.searchPlaceholder": "Search (RTL OK)…",
  "catfilter.noCategories": "No categories",
  "catfilter.shown": "{n} of {total} categories shown",
  "catfilter.allShown": "Showing all {total}",
};

const fr: Bundle = {
  // Navigation
  "nav.home": "Accueil",
  "nav.live": "TV en direct",
  "nav.movies": "Films",
  "nav.series": "Séries",
  "nav.guide": "Programme",
  "nav.recordings": "Enregistrements",
  "nav.search": "Recherche",
  "nav.multiview": "Multi-vue",
  "nav.watchlist": "À voir",
  "nav.settings": "Paramètres",
  // Common
  "common.add": "Ajouter",
  "common.remove": "Supprimer",
  "common.cancel": "Annuler",
  "common.save": "Enregistrer",
  "common.delete": "Supprimer",
  "common.close": "Fermer",
  "common.edit": "Modifier",
  "common.loading": "Chargement…",
  "common.search": "Rechercher",
  "common.all": "Tout",
  "common.none": "Aucun",
  "common.play": "Lire",
  "common.resume": "Reprendre",
  "common.retry": "Réessayer",
  "common.next": "Suivant",
  "common.previous": "Précédent",
  "common.empty": "Rien ici pour l'instant.",
  "common.error": "Erreur",
  // Home
  "home.continue": "Reprendre la lecture",
  "home.favorites": "Favoris",
  "home.topMovies": "Films les mieux notés",
  "home.topSeries": "Séries les mieux notées",
  "home.recentMovies": "Ajoutés récemment — Films",
  "home.recentSeries": "Mises à jour récentes — Séries",
  "home.live": "TV en direct",
  "home.noProvider": "Ajoute un provider dans les Paramètres pour commencer.",
  // Live
  "live.title": "TV en direct",
  "live.searchPlaceholder": "Chercher chaîne ou programme…",
  "live.noChannels": "Aucune chaîne. Synchronise ton provider dans les Paramètres.",
  "live.channelsCount": "{count} chaînes",
  // Movies / Series
  "movies.title": "Films",
  "movies.filter": "Filtrer…",
  "movies.noMovies": "Aucun film. Synchronise ton provider.",
  "series.title": "Séries",
  "series.noSeries": "Aucune série. Synchronise ton provider.",
  "series.nowPlaying": "Lecture en cours",
  "series.nextEpisode": "Épisode suivant →",
  "movie.cast": "Distribution",
  "movie.director": "Réalisateur",
  "movie.trailer": "Bande-annonce",
  // Guide
  "guide.title": "Programme",
  "guide.loadEpg": "Charger le guide EPG",
  "guide.reloadEpg": "Recharger l'EPG",
  "guide.loadingEpg": "Chargement EPG…",
  "guide.noEpg": "Aucune donnée EPG. Clique « Charger le guide EPG » — nécessite une URL EPG sur le provider.",
  "guide.programsCount": "{p} programmes · {c} chaînes",
  // Search
  "search.title": "Recherche",
  "search.placeholder": "Rechercher…",
  "search.recent": "Récent",
  "search.scope.all": "Tout",
  "search.scope.live": "Direct",
  "search.scope.movie": "Films",
  "search.scope.series": "Séries",
  // Multi-view
  "multiview.title": "Multi-vue",
  "multiview.hint": "Clique sur une tuile pour activer son son",
  // Recordings
  "recordings.title": "Enregistrements",
  "recordings.noRecordings": "Aucun enregistrement. Démarre-en un depuis le lecteur TV.",
  "recordings.intro": "Les enregistrements capturent le flux en cours via MediaRecorder et sont stockés dans l'OPFS du navigateur.",
  // Settings
  "settings.tab.providers": "Providers",
  "settings.tab.categories": "Catégories",
  "settings.tab.network": "Réseau",
  "settings.tab.parental": "Parental",
  "settings.tab.appearance": "Apparence",
  "settings.tab.backup": "Sauvegarde",
  "settings.providers.title": "Providers",
  "settings.providers.add": "+ Ajouter un provider",
  "settings.providers.empty": "Aucun provider. Clique « Ajouter un provider ».",
  "settings.providers.resync": "Resynchroniser",
  "settings.providers.editing": "Modification de",
  "settings.network.title": "Proxy réseau",
  "settings.network.intro": "Proxy qui contourne CORS et transmet User-Agent / Referer. Requis pour la plupart des providers IPTV en navigateur.",
  "settings.network.active": "Actif actuellement",
  "settings.network.default": "Défaut intégré",
  "settings.network.custom": "URL de proxy personnalisée (écrase le défaut)",
  "settings.network.reset": "Réinitialiser",
  "settings.parental.title": "Contrôles parentaux",
  "settings.parental.pinSet": "Code PIN défini.",
  "settings.parental.noPin": "Aucun code PIN configuré.",
  "settings.parental.setPin": "Définir le PIN",
  "settings.parental.clearPin": "Supprimer le PIN",
  "settings.parental.hideLocked": "Masquer le contenu verrouillé",
  "settings.appearance.title": "Apparence",
  "settings.appearance.language": "Langue",
  "settings.appearance.theme": "Thème",
  "settings.appearance.dark": "Sombre",
  "settings.appearance.light": "Clair",
  "settings.appearance.sidebar": "Barre latérale",
  "settings.appearance.collapse": "Réduire la barre",
  "settings.appearance.expand": "Étendre la barre",
  "settings.backup.title": "Sauvegarde",
  "settings.backup.export": "Exporter la sauvegarde",
  "settings.backup.import": "Importer une sauvegarde",
  "settings.backup.intro": "La sauvegarde contient : providers (avec credentials), catalogue, favoris, historique, paramètres, verrous parentaux, filtres. Stockée localement dans IndexedDB.",
  "settings.categories.title": "Catégories",
  "settings.categories.intro": "Choisis quelles catégories afficher dans chaque section. Quand une liste blanche est définie, la prochaine resynchronisation ne récupère que les catégories cochées.",
  "settings.categories.provider": "Provider",
  "settings.categories.live": "TV en direct",
  "settings.categories.movies": "Films",
  "settings.categories.series": "Séries",
  "settings.categories.apply": "Appliquer la sélection : la prochaine resynchronisation ne récupère que les catégories cochées.",
  "settings.categories.resyncNow": "Resynchroniser ce provider",
  // Provider editor
  "provider.type": "Type",
  "provider.name": "Nom",
  "provider.serverUrl": "URL serveur",
  "provider.username": "Identifiant",
  "provider.password": "Mot de passe",
  "provider.m3uUrl": "URL M3U",
  "provider.epgUrl": "URL EPG (XMLTV, optionnel)",
  "provider.userAgent": "User-Agent (optionnel)",
  "provider.referer": "Referer HTTP (optionnel)",
  "provider.testConnection": "Tester la connexion",
  "provider.testing": "Test…",
  "provider.saveAndResync": "Enregistrer & resynchroniser",
  "provider.add": "Ajouter un provider",
  "provider.edit": "Modifier",
  // Category filter
  "catfilter.label": "Catégories",
  "catfilter.searchPlaceholder": "Rechercher (RTL OK)…",
  "catfilter.noCategories": "Aucune catégorie",
  "catfilter.shown": "{n} sur {total} catégories affichées",
  "catfilter.allShown": "Affiche les {total}",
};

const ar: Bundle = {
  "nav.home": "الرئيسية",
  "nav.live": "البث المباشر",
  "nav.movies": "أفلام",
  "nav.series": "مسلسلات",
  "nav.guide": "الدليل",
  "nav.recordings": "التسجيلات",
  "nav.search": "بحث",
  "nav.multiview": "عرض متعدد",
  "nav.settings": "الإعدادات",
  "common.add": "إضافة",
  "common.cancel": "إلغاء",
  "common.save": "حفظ",
  "common.delete": "حذف",
  "common.close": "إغلاق",
  "common.edit": "تعديل",
  "common.loading": "جارٍ التحميل…",
  "common.search": "بحث",
  "common.play": "تشغيل",
  "common.resume": "متابعة",
  "common.retry": "إعادة المحاولة",
  "home.continue": "متابعة المشاهدة",
  "home.favorites": "المفضلة",
  "home.noProvider": "أضف موفّرًا من الإعدادات للبدء.",
  "settings.tab.providers": "الموفّرون",
  "settings.tab.categories": "الفئات",
  "settings.tab.network": "الشبكة",
  "settings.tab.parental": "الرقابة الأبوية",
  "settings.tab.appearance": "المظهر",
  "settings.tab.backup": "النسخ الاحتياطي",
};

const bundles: Record<string, Bundle> = { en, fr, ar };

const RTL_LANGS = new Set(["ar", "he", "fa", "ur"]);

interface I18nState {
  lang: string;
  setLang: (lang: string) => Promise<void>;
  init: () => Promise<void>;
  t: (key: string, vars?: Record<string, string | number>) => string;
}

function applyDirection(lang: string) {
  const dir = RTL_LANGS.has(lang) ? "rtl" : "ltr";
  document.documentElement.lang = lang;
  document.documentElement.dir = dir;
}

export const useI18n = create<I18nState>((set, get) => ({
  lang: "en",
  async setLang(lang) {
    const final = bundles[lang] ? lang : "en";
    await settingsRepo.set("ui.lang", final);
    applyDirection(final);
    set({ lang: final });
  },
  async init() {
    const saved = await settingsRepo.get<string>("ui.lang");
    let lang: string = "en";
    if (saved && bundles[saved]) lang = saved;
    else if (navigator.language?.startsWith("fr")) lang = "fr";
    else if (navigator.language?.startsWith("ar")) lang = "ar";
    applyDirection(lang);
    set({ lang });
  },
  t(key, vars) {
    const lang = get().lang;
    const tmpl = bundles[lang]?.[key] ?? bundles["en"]?.[key] ?? key;
    if (!vars) return tmpl;
    return tmpl.replace(/\{(\w+)\}/g, (_, k) => String(vars[k] ?? `{${k}}`));
  },
}));

export const availableLocales = Object.keys(bundles);
