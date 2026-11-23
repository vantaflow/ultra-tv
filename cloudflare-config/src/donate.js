// Dons (facultatifs) : PayPal uniquement.
// - Toujours : lien direct paypal.me (aucun secret).
// - Si la variable PAYPAL_CLIENT_ID est définie (application REST du compte PayPal Business, identifiant public) :
//   boutons PayPal / carte intégrés à /donate (SDK JS de PayPal), montant au choix, sans secret côté Worker.

/** Don direct sur PayPal (versé tout de suite, sans plateforme intermédiaire). */
export const PAYPAL_URL = "https://paypal.me/kbenazzouz";
/** Devise des dons intégrés. */
export const DONATE_CURRENCY = "EUR";
/** Montants proposés (euros). */
export const DONATE_AMOUNTS = [3, 5, 10, 20];

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
    `style-src 'nonce-${n}' 'unsafe-inline'`,
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
    title: "Soutenir Ultra TV", sub: "Ultra TV est gratuit, sans publicité et le restera. Un don aide à payer les serveurs et le temps passé à l'améliorer.",
    paypal: "Faire un don avec PayPal", paypalNote: "Don direct, compte PayPal ou carte bancaire. Aucune donnée bancaire ne passe par Ultra TV.",
    amount: "Montant", other: "Autre montant (€)", thanks: "Merci beaucoup pour votre don !", error: "Le paiement n'a pas abouti. Vous pouvez réessayer ou passer par le lien ci-dessous.",
    direct: "Ou par le lien PayPal direct",
  },
  en: {
    title: "Support Ultra TV", sub: "Ultra TV is free and ad-free, and will stay that way. A donation helps pay for the servers and the time spent improving it.",
    paypal: "Donate with PayPal", paypalNote: "Direct donation, PayPal account or card. No card details go through Ultra TV.",
    amount: "Amount", other: "Other amount (€)", thanks: "Thank you so much for your donation!", error: "The payment did not go through. You can try again or use the link below.",
    direct: "Or use the direct PayPal link",
  },
  es: {
    title: "Apoyar Ultra TV", sub: "Ultra TV es gratis, sin anuncios, y lo seguirá siendo. Una donación ayuda a pagar los servidores y el tiempo dedicado a mejorarlo.",
    paypal: "Donar con PayPal", paypalNote: "Donación directa, cuenta PayPal o tarjeta. Ningún dato bancario pasa por Ultra TV.",
    amount: "Importe", other: "Otro importe (€)", thanks: "¡Muchas gracias por tu donación!", error: "El pago no se completó. Puedes volver a intentarlo o usar el enlace de abajo.",
    direct: "O con el enlace directo de PayPal",
  },
  ar: {
    title: "ادعم Ultra TV", sub: "Ultra TV مجاني وبلا إعلانات وسيبقى كذلك. يساعد تبرعك في دفع تكاليف الخوادم والوقت المخصص لتحسينه.",
    paypal: "تبرّع عبر PayPal", paypalNote: "تبرّع مباشر، بحساب PayPal أو بطاقة بنكية. لا تمر أي بيانات بنكية عبر Ultra TV.",
    amount: "المبلغ", other: "مبلغ آخر (€)", thanks: "شكرًا جزيلًا على تبرعك!", error: "لم تكتمل عملية الدفع. يمكنك المحاولة مجددًا أو استخدام الرابط أدناه.",
    direct: "أو عبر رابط PayPal المباشر",
  },
  pl: {
    title: "Wesprzyj Ultra TV", sub: "Ultra TV jest darmowe, bez reklam i takie pozostanie. Darowizna pomaga opłacić serwery i czas poświęcony na rozwój.",
    paypal: "Wesprzyj przez PayPal", paypalNote: "Bezpośrednia darowizna, konto PayPal lub karta. Żadne dane karty nie przechodzą przez Ultra TV.",
    amount: "Kwota", other: "Inna kwota (€)", thanks: "Bardzo dziękujemy za darowiznę!", error: "Płatność się nie powiodła. Spróbuj ponownie lub użyj linku poniżej.",
    direct: "Lub przez bezpośredni link PayPal",
  },
};
