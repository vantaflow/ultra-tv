// Dons (facultatifs) : simples liens vers PayPal, Ko-fi et Liberapay, sans secret ni formulaire côté Worker.

/** Don direct sur PayPal (versé tout de suite, sans plateforme intermédiaire) : proposé en premier. */
export const PAYPAL_URL = "https://paypal.me/kbenazzouz";

/** Dons depuis n'importe quel pays (carte ou PayPal), toujours proposés. */
export const KOFI_URL = "https://ko-fi.com/ultratv";
/** Dons récurrents (chaque semaine, mois ou année). */
export const LIBERAPAY_URL = "https://liberapay.com/ultratv/";

export const DONATE_DICT = {
  fr: {
    paypal: "Faire un don avec PayPal", paypalNote: "Don direct, compte PayPal ou carte bancaire.", kofiAlt: "Ou via Ko-fi (carte ou PayPal).",
    liberapay: "Don régulier via Liberapay", kofi: "Donner via Ko-fi", kofiNote: "Carte bancaire ou PayPal, depuis n'importe quel pays.",
    title: "Soutenir Ultra TV", sub: "Ultra TV est gratuit, sans publicité et le restera. Un don aide à payer les serveurs et le temps passé à l'améliorer.",
  },
  en: {
    paypal: "Donate with PayPal", paypalNote: "Direct donation, PayPal account or card.", kofiAlt: "Or via Ko-fi (card or PayPal).",
    liberapay: "Recurring donation on Liberapay", kofi: "Donate on Ko-fi", kofiNote: "Card or PayPal, from any country.",
    title: "Support Ultra TV", sub: "Ultra TV is free and ad-free, and will stay that way. A donation helps pay for the servers and the time spent improving it.",
  },
  es: {
    paypal: "Donar con PayPal", paypalNote: "Donación directa, cuenta PayPal o tarjeta.", kofiAlt: "O a través de Ko-fi (tarjeta o PayPal).",
    liberapay: "Donación periódica en Liberapay", kofi: "Donar en Ko-fi", kofiNote: "Tarjeta o PayPal, desde cualquier país.",
    title: "Apoyar Ultra TV", sub: "Ultra TV es gratis, sin anuncios, y lo seguirá siendo. Una donación ayuda a pagar los servidores y el tiempo dedicado a mejorarlo.",
  },
  ar: {
    paypal: "تبرّع عبر PayPal", paypalNote: "تبرّع مباشر، بحساب PayPal أو بطاقة بنكية.", kofiAlt: "أو عبر Ko-fi (بطاقة أو PayPal).",
    liberapay: "تبرّع دوري عبر Liberapay", kofi: "تبرّع عبر Ko-fi", kofiNote: "بطاقة بنكية أو PayPal، من أي بلد.",
    title: "ادعم Ultra TV", sub: "Ultra TV مجاني وبلا إعلانات وسيبقى كذلك. يساعد تبرعك في دفع تكاليف الخوادم والوقت المخصص لتحسينه.",
  },
  pl: {
    paypal: "Wesprzyj przez PayPal", paypalNote: "Bezpośrednia darowizna, konto PayPal lub karta.", kofiAlt: "Lub przez Ko-fi (karta lub PayPal).",
    liberapay: "Stała darowizna przez Liberapay", kofi: "Wesprzyj przez Ko-fi", kofiNote: "Karta lub PayPal, z dowolnego kraju.",
    title: "Wesprzyj Ultra TV", sub: "Ultra TV jest darmowe, bez reklam i takie pozostanie. Darowizna pomaga opłacić serwery i czas poświęcony na rozwój.",
  },
};
