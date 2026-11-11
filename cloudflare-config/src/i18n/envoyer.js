// Traductions de la page publique /envoyer (formulaire d'envoi d'une source à la TV).
// Un dictionnaire par langue, sans dépendance. Toutes les langues ont les MÊMES clés (vérifié par les tests).
// Les textes sont insérés échappés (côté serveur) ou passés au script de la page en JSON (messages dynamiques).

export const ENVOYER_LANGS = ["en", "fr", "es", "ar", "pl"];
export const ENVOYER_DEFAULT_LANG = "en";
export const ENVOYER_COOKIE = "utv-lang";
/** Nom natif de chaque langue, affiché dans le sélecteur. */
export const ENVOYER_LANG_NAMES = { en: "English", fr: "Français", es: "Español", ar: "العربية", pl: "Polski" };
export const ENVOYER_DIR = { en: "ltr", fr: "ltr", es: "ltr", ar: "rtl", pl: "ltr" };

export const ENVOYER_DICT = {
  fr: {
    title: "Ultra TV — envoyer une source",
    skip: "Aller au contenu",
    themeBtn: "Thème", themeAuto: "Thème : automatique", themeLight: "Thème : clair", themeDark: "Thème : sombre", themeChange: "changer",
    langLabel: "Langue",
    h1: "Envoyer à ma TV",
    sub: "Ajoute une source sur ta TV sans rien taper à la télécommande. Aucun compte nécessaire.",
    done: "Source envoyée. Ta TV l'ajoute et lance la synchronisation dans quelques secondes. Tu peux fermer cette page.",
    codeLabel: "Code affiché sur la TV", codeHint: "(6 caractères)",
    kindLabel: "Type de source",
    kindXtream: "Xtream Codes", kindM3u: "Lien M3U", kindStalker: "Portail Stalker (MAC)", kindGuide: "Guide XMLTV (adresse)",
    nameLabel: "Nom", optional: "(facultatif)", namePh: "Ma source",
    urlLabel: "Adresse du serveur",
    userLabel: "Identifiant", passLabel: "Mot de passe", showPw: "Afficher le mot de passe",
    macLabel: "Adresse MAC",
    send: "Envoyer",
    noscript: "JavaScript est nécessaire pour envoyer la source.",
    note: "Les identifiants ne servent qu'à ajouter la source sur ta TV : ils sont chiffrés, conservés 15 minutes au plus et supprimés dès que la TV les a reçus.",
    lblXtream: "Adresse du serveur", lblM3u: "Adresse de la playlist M3U", lblStalker: "Adresse du portail", lblGuide: "Adresse du guide XMLTV (.xml ou .xml.gz)",
    errCode: "Code inconnu ou expiré. Vérifie l'écran de ta TV.",
    errUsed: "Une source a déjà été envoyée avec ce code. Relance « Depuis le téléphone » sur la TV pour en envoyer une autre.",
    errUrl: "Adresse invalide : elle doit commencer par http:// ou https://.",
    errCreds: "Identifiant et mot de passe obligatoires.",
    errMac: "Adresse MAC invalide (format 00:1A:79:XX:XX:XX).",
    errKind: "Type de source invalide.",
    errGuide: "Adresse de guide invalide : http:// ou https://, finissant par .xml ou .xml.gz.",
    errRate: "Trop de tentatives. Réessaie dans quelques minutes.",
    errFail: "L'envoi a échoué. Réessaie.",
    errNet: "Connexion impossible. Vérifie ton réseau et réessaie.",
  },
  en: {
    title: "Ultra TV — send a source",
    skip: "Skip to content",
    themeBtn: "Theme", themeAuto: "Theme: automatic", themeLight: "Theme: light", themeDark: "Theme: dark", themeChange: "change",
    langLabel: "Language",
    h1: "Send to my TV",
    sub: "Add a source to your TV without typing anything with the remote. No account needed.",
    done: "Source sent. Your TV adds it and starts syncing in a few seconds. You can close this page.",
    codeLabel: "Code shown on the TV", codeHint: "(6 characters)",
    kindLabel: "Source type",
    kindXtream: "Xtream Codes", kindM3u: "M3U link", kindStalker: "Stalker portal (MAC)", kindGuide: "XMLTV guide (address)",
    nameLabel: "Name", optional: "(optional)", namePh: "My source",
    urlLabel: "Server address",
    userLabel: "Username", passLabel: "Password", showPw: "Show password",
    macLabel: "MAC address",
    send: "Send",
    noscript: "JavaScript is required to send the source.",
    note: "Your credentials are only used to add the source to your TV: they are encrypted, kept for 15 minutes at most and deleted as soon as the TV has received them.",
    lblXtream: "Server address", lblM3u: "M3U playlist address", lblStalker: "Portal address", lblGuide: "XMLTV guide address (.xml or .xml.gz)",
    errCode: "Unknown or expired code. Check your TV screen.",
    errUsed: "A source has already been sent with this code. Start “From your phone” again on the TV to send another one.",
    errUrl: "Invalid address: it must start with http:// or https://.",
    errCreds: "Username and password are required.",
    errMac: "Invalid MAC address (format 00:1A:79:XX:XX:XX).",
    errKind: "Invalid source type.",
    errGuide: "Invalid guide address: http:// or https://, ending in .xml or .xml.gz.",
    errRate: "Too many attempts. Try again in a few minutes.",
    errFail: "Sending failed. Try again.",
    errNet: "Cannot connect. Check your network and try again.",
  },
  es: {
    title: "Ultra TV — enviar una fuente",
    skip: "Ir al contenido",
    themeBtn: "Tema", themeAuto: "Tema: automático", themeLight: "Tema: claro", themeDark: "Tema: oscuro", themeChange: "cambiar",
    langLabel: "Idioma",
    h1: "Enviar a mi TV",
    sub: "Añade una fuente a tu TV sin escribir nada con el mando. No hace falta ninguna cuenta.",
    done: "Fuente enviada. Tu TV la añade e inicia la sincronización en unos segundos. Puedes cerrar esta página.",
    codeLabel: "Código que aparece en la TV", codeHint: "(6 caracteres)",
    kindLabel: "Tipo de fuente",
    kindXtream: "Xtream Codes", kindM3u: "Enlace M3U", kindStalker: "Portal Stalker (MAC)", kindGuide: "Guía XMLTV (dirección)",
    nameLabel: "Nombre", optional: "(opcional)", namePh: "Mi fuente",
    urlLabel: "Dirección del servidor",
    userLabel: "Usuario", passLabel: "Contraseña", showPw: "Mostrar la contraseña",
    macLabel: "Dirección MAC",
    send: "Enviar",
    noscript: "Se necesita JavaScript para enviar la fuente.",
    note: "Las credenciales solo sirven para añadir la fuente a tu TV: se cifran, se conservan 15 minutos como máximo y se eliminan en cuanto la TV las recibe.",
    lblXtream: "Dirección del servidor", lblM3u: "Dirección de la lista M3U", lblStalker: "Dirección del portal", lblGuide: "Dirección de la guía XMLTV (.xml o .xml.gz)",
    errCode: "Código desconocido o caducado. Comprueba la pantalla de tu TV.",
    errUsed: "Ya se ha enviado una fuente con este código. Vuelve a abrir «Desde el teléfono» en la TV para enviar otra.",
    errUrl: "Dirección no válida: debe empezar por http:// o https://.",
    errCreds: "El usuario y la contraseña son obligatorios.",
    errMac: "Dirección MAC no válida (formato 00:1A:79:XX:XX:XX).",
    errKind: "Tipo de fuente no válido.",
    errGuide: "Dirección de guía no válida: http:// o https://, terminada en .xml o .xml.gz.",
    errRate: "Demasiados intentos. Inténtalo de nuevo en unos minutos.",
    errFail: "El envío ha fallado. Inténtalo de nuevo.",
    errNet: "No se puede conectar. Comprueba tu red e inténtalo de nuevo.",
  },
  ar: {
    title: "Ultra TV — إرسال مصدر",
    skip: "انتقل إلى المحتوى",
    themeBtn: "السمة", themeAuto: "السمة: تلقائية", themeLight: "السمة: فاتحة", themeDark: "السمة: داكنة", themeChange: "تغيير",
    langLabel: "اللغة",
    h1: "إرسال إلى تلفازي",
    sub: "أضف مصدرًا إلى تلفازك دون كتابة أي شيء بجهاز التحكم. لا حاجة إلى حساب.",
    done: "تم إرسال المصدر. سيضيفه تلفازك ويبدأ المزامنة خلال ثوانٍ. يمكنك إغلاق هذه الصفحة.",
    codeLabel: "الرمز الظاهر على التلفاز", codeHint: "(6 أحرف)",
    kindLabel: "نوع المصدر",
    kindXtream: "Xtream Codes", kindM3u: "رابط M3U", kindStalker: "بوابة Stalker (MAC)", kindGuide: "دليل XMLTV (عنوان)",
    nameLabel: "الاسم", optional: "(اختياري)", namePh: "مصدري",
    urlLabel: "عنوان الخادم",
    userLabel: "اسم المستخدم", passLabel: "كلمة المرور", showPw: "إظهار كلمة المرور",
    macLabel: "عنوان MAC",
    send: "إرسال",
    noscript: "يلزم تفعيل JavaScript لإرسال المصدر.",
    note: "لا تُستخدم بيانات الدخول إلا لإضافة المصدر إلى تلفازك: فهي مشفّرة وتُحفظ 15 دقيقة على الأكثر وتُحذف بمجرد استلام التلفاز لها.",
    lblXtream: "عنوان الخادم", lblM3u: "عنوان قائمة M3U", lblStalker: "عنوان البوابة", lblGuide: "عنوان دليل XMLTV (‎.xml أو ‎.xml.gz)",
    errCode: "الرمز غير معروف أو منتهي الصلاحية. تحقق من شاشة التلفاز.",
    errUsed: "تم إرسال مصدر بهذا الرمز من قبل. أعد تشغيل «من الهاتف» على التلفاز لإرسال مصدر آخر.",
    errUrl: "عنوان غير صالح: يجب أن يبدأ بـ http:// أو https://.",
    errCreds: "اسم المستخدم وكلمة المرور مطلوبان.",
    errMac: "عنوان MAC غير صالح (الصيغة 00:1A:79:XX:XX:XX).",
    errKind: "نوع المصدر غير صالح.",
    errGuide: "عنوان الدليل غير صالح: http:// أو https://، وينتهي بـ ‎.xml أو ‎.xml.gz.",
    errRate: "محاولات كثيرة جدًا. أعد المحاولة بعد بضع دقائق.",
    errFail: "فشل الإرسال. أعد المحاولة.",
    errNet: "تعذّر الاتصال. تحقق من الشبكة وأعد المحاولة.",
  },
  pl: {
    title: "Ultra TV — wyślij źródło",
    skip: "Przejdź do treści",
    themeBtn: "Motyw", themeAuto: "Motyw: automatyczny", themeLight: "Motyw: jasny", themeDark: "Motyw: ciemny", themeChange: "zmień",
    langLabel: "Język",
    h1: "Wyślij na mój telewizor",
    sub: "Dodaj źródło do telewizora bez wpisywania czegokolwiek pilotem. Konto nie jest potrzebne.",
    done: "Źródło wysłane. Telewizor doda je i za kilka sekund rozpocznie synchronizację. Możesz zamknąć tę stronę.",
    codeLabel: "Kod widoczny na telewizorze", codeHint: "(6 znaków)",
    kindLabel: "Typ źródła",
    kindXtream: "Xtream Codes", kindM3u: "Link M3U", kindStalker: "Portal Stalker (MAC)", kindGuide: "Przewodnik XMLTV (adres)",
    nameLabel: "Nazwa", optional: "(opcjonalnie)", namePh: "Moje źródło",
    urlLabel: "Adres serwera",
    userLabel: "Login", passLabel: "Hasło", showPw: "Pokaż hasło",
    macLabel: "Adres MAC",
    send: "Wyślij",
    noscript: "Do wysłania źródła potrzebny jest JavaScript.",
    note: "Dane logowania służą tylko do dodania źródła na telewizorze: są szyfrowane, przechowywane najwyżej 15 minut i usuwane, gdy tylko telewizor je odbierze.",
    lblXtream: "Adres serwera", lblM3u: "Adres listy M3U", lblStalker: "Adres portalu", lblGuide: "Adres przewodnika XMLTV (.xml lub .xml.gz)",
    errCode: "Nieznany lub wygasły kod. Sprawdź ekran telewizora.",
    errUsed: "Z tym kodem wysłano już źródło. Uruchom ponownie „Z telefonu” na telewizorze, aby wysłać kolejne.",
    errUrl: "Nieprawidłowy adres: musi zaczynać się od http:// lub https://.",
    errCreds: "Login i hasło są wymagane.",
    errMac: "Nieprawidłowy adres MAC (format 00:1A:79:XX:XX:XX).",
    errKind: "Nieprawidłowy typ źródła.",
    errGuide: "Nieprawidłowy adres przewodnika: http:// lub https://, kończący się na .xml lub .xml.gz.",
    errRate: "Zbyt wiele prób. Spróbuj ponownie za kilka minut.",
    errFail: "Wysyłanie nie powiodło się. Spróbuj ponownie.",
    errNet: "Brak połączenia. Sprawdź sieć i spróbuj ponownie.",
  },
};

/** Code de langue pris en charge d'après une balise (« es-MX » → « es »), ou null. */
export function normalizeEnvoyerLang(tag) {
  if (typeof tag !== "string") return null;
  const primary = tag.trim().toLowerCase().split(/[-_]/)[0];
  return ENVOYER_LANGS.includes(primary) ? primary : null;
}

/** Meilleure langue prise en charge d'un en-tête Accept-Language (qualités q= respectées, q=0 exclu), ou null. */
export function fromAcceptLanguage(header) {
  if (typeof header !== "string" || !header) return null;
  const items = header.split(",").map((part, i) => {
    const [tag, ...params] = part.trim().split(";");
    let q = 1;
    for (const p of params) {
      const m = p.trim().match(/^q\s*=\s*([0-9.]+)$/i);
      if (m) q = Number.parseFloat(m[1]);
    }
    return { tag: tag.trim(), q: Number.isFinite(q) ? q : 0, i };
  });
  items.sort((a, b) => b.q - a.q || a.i - b.i);
  for (const it of items) {
    if (it.q <= 0) continue;
    const l = normalizeEnvoyerLang(it.tag);
    if (l) return l;
  }
  return null;
}

/** Valeur du cookie de préférence de langue dans un en-tête Cookie, ou null. */
export function langFromCookie(cookieHeader) {
  if (typeof cookieHeader !== "string") return null;
  for (const part of cookieHeader.split(";")) {
    const eq = part.indexOf("=");
    if (eq > 0 && part.slice(0, eq).trim() === ENVOYER_COOKIE) return normalizeEnvoyerLang(part.slice(eq + 1).trim());
  }
  return null;
}

/**
 * Langue de la page : paramètre `l` de l'URL, puis cookie de préférence, puis Accept-Language, sinon anglais.
 * Un paramètre ou un cookie non pris en charge est ignoré (on passe au critère suivant).
 */
export function pickEnvoyerLang({ param = null, cookie = null, acceptLanguage = null } = {}) {
  return normalizeEnvoyerLang(param) || normalizeEnvoyerLang(cookie) || fromAcceptLanguage(acceptLanguage) || ENVOYER_DEFAULT_LANG;
}
