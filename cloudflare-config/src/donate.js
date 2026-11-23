// Dons (facultatifs) : PayPal uniquement.
// - Toujours : lien de paiement PayPal Business (montant libre, PayPal ou carte ; aucun secret).
// - Si la variable PAYPAL_CLIENT_ID est définie (identifiant public du bouton hébergé du compte PayPal Business) :
//   bouton PayPal hébergé (HostedButtons) intégré à /donate ; montant, description et moyens de paiement se règlent
//   dans le tableau de bord PayPal, sans secret côté Worker.

/** Don direct sur PayPal (versé tout de suite, sans plateforme intermédiaire). */
/** Site vitrine (lien « Retour au site » de la page de remerciement). */
export const SITE_URL = "https://vantaflow.github.io/ultra-tv/";
/** Lien PayPal.Me (envoi direct au compte, montant libre). */
export const PAYPAL_ME_URL = "https://paypal.me/pitivi";
export const PAYPAL_URL = "https://www.paypal.com/ncp/payment/JHPS8RCN35UWA";
/** Devise des dons intégrés. */
export const DONATE_CURRENCY = "EUR";
/**
 * Dons mensuels : plans d'abonnement PayPal (produit « Soutien Ultra TV », tarification fixe en EUR, résiliables depuis le
 * compte PayPal du donateur). Identifiants publics ; le SDK des abonnements utilise l'application REST « UTV » du compte Business.
 */
export const PAYPAL_SUB_CLIENT_ID = "BAAj_4Phky4o6zEklDhdQHbghPjUbv14ymR4CsSdaLRNOrgd1jEx6zu6h_VYqCQbBVqtb_gk3HYMPHs_JQ";
export const PAYPAL_PLANS = [
  { amount: 3, id: "P-0VY777380G390052WNMCJY6A" },
  { amount: 5, id: "P-73H946429U469760TNMCJZXQ" },
  { amount: 10, id: "P-70934607BP2820041NMCJ2RI" },
];
/** Bouton hébergé PayPal (même identifiant que le lien de paiement PAYPAL_URL). */
export const PAYPAL_HOSTED_BUTTON_ID = "JHPS8RCN35UWA";

/** Identifiant client PayPal valide (lettres, chiffres, - et _), sinon null : rien n'est injecté dans la page. */
export function paypalClientId(env) {
  const id = String(env?.PAYPAL_CLIENT_ID || "").trim();
  return /^[A-Za-z0-9_-]{20,128}$/.test(id) ? id : null;
}

/** CSP de /donate quand le paiement intégré est actif : ajoute les domaines exigés par le SDK PayPal. */
export function donateCsp(n) {
  return [
    "default-src 'none'",
    `script-src 'nonce-${n}' https://www.paypal.com https://*.paypal.com https://*.paypalobjects.com`,
    // Pas de nonce ici : sa présence annulerait 'unsafe-inline', et le bouton hébergé injecte des <style> sans nonce.
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' data: https://*.paypal.com https://*.paypalobjects.com",
    "connect-src 'self' https://*.paypal.com https://*.paypalobjects.com",
    "frame-src https://*.paypal.com https://*.paypalobjects.com",
    "font-src 'self' https://*.paypalobjects.com",
    "manifest-src 'self'",
    "worker-src 'self'",
    "form-action 'self' https://*.paypal.com",
    "base-uri 'none'",
    "frame-ancestors 'none'",
  ].join("; ");
}

export const DONATE_DICT = {
  fr: {
    thanksTitle: "Merci pour votre don !", thanksBody: "Votre soutien aide à garder Ultra TV gratuit, sans publicité, et à continuer de l'améliorer.", thanksMonthly: "Votre don mensuel est actif. Vous pouvez le résilier à tout moment depuis votre compte PayPal.", thanksReceipt: "PayPal vous envoie un reçu par e-mail.", thanksSite: "Retour au site Ultra TV", thanksAgain: "Faire un autre don",
    once: "Une fois", monthly: "Chaque mois", monthlyNote: "Don mensuel, résiliable à tout moment depuis votre compte PayPal.", subThanks: "Merci ! Votre don mensuel est actif.",
    title: "Soutenir Ultra TV", sub: "Ultra TV est gratuit, sans publicité et le restera. Un don aide à payer les serveurs et le temps passé à l'améliorer.",
    paypal: "Faire un don avec PayPal", paypalNote: "Don direct, compte PayPal ou carte bancaire. Aucune donnée bancaire ne passe par Ultra TV.",
    thanks: "Merci beaucoup pour votre don !", error: "Le paiement n'a pas abouti. Vous pouvez réessayer ou passer par le lien ci-dessous.",
    direct: "Payer par carte bancaire ou PayPal", me: "Ou envoyer directement sur PayPal.Me", cardHint: "Carte bancaire sans compte PayPal : bouton « Checkout » ci-dessus, ou le lien ci-dessous.",
  },
  en: {
    thanksTitle: "Thank you for your donation!", thanksBody: "Your support helps keep Ultra TV free and ad-free, and keeps it improving.", thanksMonthly: "Your monthly donation is active. You can cancel it anytime from your PayPal account.", thanksReceipt: "PayPal sends you a receipt by email.", thanksSite: "Back to the Ultra TV website", thanksAgain: "Make another donation",
    once: "One time", monthly: "Monthly", monthlyNote: "Monthly donation, cancel anytime from your PayPal account.", subThanks: "Thank you! Your monthly donation is active.",
    title: "Support Ultra TV", sub: "Ultra TV is free and ad-free, and will stay that way. A donation helps pay for the servers and the time spent improving it.",
    paypal: "Donate with PayPal", paypalNote: "Direct donation, PayPal account or card. No card details go through Ultra TV.",
    thanks: "Thank you so much for your donation!", error: "The payment did not go through. You can try again or use the link below.",
    direct: "Pay by card or PayPal", me: "Or send directly via PayPal.Me", cardHint: "Card without a PayPal account: the “Checkout” button above, or the link below.",
  },
  es: {
    thanksTitle: "¡Gracias por tu donación!", thanksBody: "Tu apoyo ayuda a que Ultra TV siga siendo gratis, sin anuncios, y a seguir mejorándolo.", thanksMonthly: "Tu donación mensual está activa. Puedes cancelarla en cualquier momento desde tu cuenta PayPal.", thanksReceipt: "PayPal te envía un recibo por correo electrónico.", thanksSite: "Volver al sitio de Ultra TV", thanksAgain: "Hacer otra donación",
    once: "Una vez", monthly: "Cada mes", monthlyNote: "Donación mensual, cancelable en cualquier momento desde tu cuenta PayPal.", subThanks: "¡Gracias! Tu donación mensual está activa.",
    title: "Apoyar Ultra TV", sub: "Ultra TV es gratis, sin anuncios, y lo seguirá siendo. Una donación ayuda a pagar los servidores y el tiempo dedicado a mejorarlo.",
    paypal: "Donar con PayPal", paypalNote: "Donación directa, cuenta PayPal o tarjeta. Ningún dato bancario pasa por Ultra TV.",
    thanks: "¡Muchas gracias por tu donación!", error: "El pago no se completó. Puedes volver a intentarlo o usar el enlace de abajo.",
    direct: "Pagar con tarjeta o PayPal", me: "O enviar directamente por PayPal.Me", cardHint: "Tarjeta sin cuenta PayPal: botón «Checkout» de arriba o el enlace de abajo.",
  },
  ar: {
    thanksTitle: "شكرًا على تبرعك!", thanksBody: "دعمك يساعد على إبقاء Ultra TV مجانيًا وبلا إعلانات، وعلى مواصلة تحسينه.", thanksMonthly: "تبرعك الشهري مفعّل. يمكنك إلغاؤه في أي وقت من حسابك على PayPal.", thanksReceipt: "يرسل لك PayPal إيصالًا عبر البريد الإلكتروني.", thanksSite: "العودة إلى موقع Ultra TV", thanksAgain: "تبرّع مرة أخرى",
    once: "مرة واحدة", monthly: "كل شهر", monthlyNote: "تبرّع شهري، يمكن إلغاؤه في أي وقت من حسابك على PayPal.", subThanks: "شكرًا! تبرعك الشهري مفعّل.",
    title: "ادعم Ultra TV", sub: "Ultra TV مجاني وبلا إعلانات وسيبقى كذلك. يساعد تبرعك في دفع تكاليف الخوادم والوقت المخصص لتحسينه.",
    paypal: "تبرّع عبر PayPal", paypalNote: "تبرّع مباشر، بحساب PayPal أو بطاقة بنكية. لا تمر أي بيانات بنكية عبر Ultra TV.",
    thanks: "شكرًا جزيلًا على تبرعك!", error: "لم تكتمل عملية الدفع. يمكنك المحاولة مجددًا أو استخدام الرابط أدناه.",
    direct: "ادفع بالبطاقة البنكية أو PayPal", me: "أو أرسل مباشرة عبر PayPal.Me", cardHint: "بطاقة بنكية دون حساب PayPal: زر «Checkout» أعلاه أو الرابط أدناه.",
  },
  pl: {
    thanksTitle: "Dziękujemy za darowiznę!", thanksBody: "Twoje wsparcie pomaga utrzymać Ultra TV za darmo i bez reklam oraz dalej je rozwijać.", thanksMonthly: "Twoja comiesięczna darowizna jest aktywna. Możesz ją anulować w dowolnej chwili na koncie PayPal.", thanksReceipt: "PayPal wyśle Ci potwierdzenie e-mailem.", thanksSite: "Wróć na stronę Ultra TV", thanksAgain: "Przekaż kolejną darowiznę",
    once: "Jednorazowo", monthly: "Co miesiąc", monthlyNote: "Comiesięczna darowizna, możesz ją anulować w dowolnej chwili na koncie PayPal.", subThanks: "Dziękujemy! Twoja comiesięczna darowizna jest aktywna.",
    title: "Wesprzyj Ultra TV", sub: "Ultra TV jest darmowe, bez reklam i takie pozostanie. Darowizna pomaga opłacić serwery i czas poświęcony na rozwój.",
    paypal: "Wesprzyj przez PayPal", paypalNote: "Bezpośrednia darowizna, konto PayPal lub karta. Żadne dane karty nie przechodzą przez Ultra TV.",
    thanks: "Bardzo dziękujemy za darowiznę!", error: "Płatność się nie powiodła. Spróbuj ponownie lub użyj linku poniżej.",
    direct: "Zapłać kartą lub przez PayPal", me: "Lub wyślij bezpośrednio przez PayPal.Me", cardHint: "Karta bez konta PayPal: przycisk „Checkout” powyżej lub link poniżej.",
  },
};
