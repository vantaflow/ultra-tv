// Dons (facultatifs) via ChariPay : le donateur choisit un montant en dirhams sur /donate, le Worker crée un lien de
// paiement à usage unique (API ChariPay, clé secrète CHARIPAY_API_KEY, jamais exposée) et le redirige vers la page de
// paiement hébergée par ChariPay. Aucune donnée du donateur n'est demandée ni gardée ici (carte saisie chez ChariPay).
// Sans clé configurée, la page l'indique et ne propose aucun paiement.

export const CHARIPAY_API = "https://api-psp.charipay.ma";
/** Dons depuis n'importe quel pays (carte ou PayPal), toujours proposés. */
export const KOFI_URL = "https://ko-fi.com/ultratv";
export const DONATE_PRESETS = [20, 50, 100, 200];
export const DONATE_MIN = 10;
export const DONATE_MAX = 5000;

/** Montant choisi (préréglage ou montant libre) → entier en dirhams, ou null s'il est invalide. */
export function parseDonateAmount(preset, custom) {
  const raw = String(custom ?? "").trim() || String(preset ?? "").trim();
  if (!/^\d{1,5}$/.test(raw)) return null;
  const n = Number(raw);
  return n >= DONATE_MIN && n <= DONATE_MAX ? n : null;
}

/** Page de paiement renvoyée par ChariPay : https et domaine charipay.ma seulement (sinon on ne redirige pas). */
export function isCharipayUrl(u) {
  try {
    const x = new URL(String(u));
    return x.protocol === "https:" && (x.hostname === "charipay.ma" || x.hostname.endsWith(".charipay.ma"));
  } catch { return false; }
}

/**
 * Crée un lien de paiement à usage unique pour [amount] dirhams → { url } ou { error }.
 * [origin] = origine du Worker (pages de retour), [fetchImpl] remplaçable dans les tests.
 */
export async function createDonationLink(env, amount, { origin, lang = "en", fetchImpl = fetch } = {}) {
  const key = env.CHARIPAY_API_KEY;
  if (!key) return { error: "disabled" };
  const id = `don_${crypto.randomUUID()}`;
  let res;
  try {
    res = await fetchImpl(`${CHARIPAY_API}/v1/payment-links`, {
      method: "POST",
      headers: { "content-type": "application/json", accept: "application/json", "X-CHARI-PAY-API-KEY": key, "Idempotency-Key": id },
      body: JSON.stringify({
        amount: Number(amount.toFixed(2)),
        description: "Ultra TV — don / donation",
        singleUse: true,
        paymentMethod: "CARD",
        externalId: id,
        acceptUrl: `${origin}/donate/merci?l=${lang}`,
        declineUrl: `${origin}/donate?e=declined&l=${lang}`,
        // Lien sans suite s'il n'est pas payé dans l'heure.
        expiresAt: new Date(Date.now() + 3600_000).toISOString().replace(/\.\d{3}Z$/, "Z"),
        metadata: { source: "ultra-tv-donate" },
      }),
    });
  } catch (e) {
    console.error("charipay: injoignable", e?.message);
    return { error: "upstream" };
  }
  if (res.status !== 201 && res.status !== 200) {
    console.error("charipay: refus", res.status, (await res.text().catch(() => "")).slice(0, 300));
    return { error: "upstream" };
  }
  const body = await res.json().catch(() => null);
  if (!body || !isCharipayUrl(body.payUrl)) return { error: "upstream" };
  return { url: body.payUrl };
}

export const DONATE_DICT = {
  fr: {
    kofi: "Donner via Ko-fi", kofiNote: "Carte bancaire ou PayPal, depuis n'importe quel pays.", orMad: "Ou par carte marocaine, en dirhams :",
    title: "Soutenir Ultra TV", sub: "Ultra TV est gratuit, sans publicité et le restera. Un don aide à payer les serveurs et le temps passé à l'améliorer.",
    pick: "Montant (dirhams)", other: "Autre montant", give: "Faire un don", secure: "Paiement par carte sur la page sécurisée de ChariPay. Aucune donnée bancaire ne passe par Ultra TV.",
    declined: "Le paiement n'a pas abouti. Vous pouvez réessayer.", upstream: "Le service de paiement ne répond pas. Réessayez dans un moment.",
    invalid: `Montant invalide (de ${DONATE_MIN} à ${DONATE_MAX} dirhams).`, thanks: "Merci pour votre soutien !", thanksSub: "Votre don a bien été reçu. Il aide directement Ultra TV à continuer.",
    back: "Retour", github: "Code source (GitHub)",
  },
  en: {
    kofi: "Donate on Ko-fi", kofiNote: "Card or PayPal, from any country.", orMad: "Or with a Moroccan card, in dirhams:",
    title: "Support Ultra TV", sub: "Ultra TV is free and ad-free, and will stay that way. A donation helps pay for the servers and the time spent improving it.",
    pick: "Amount (Moroccan dirhams, MAD)", other: "Other amount", give: "Donate", secure: "Card payment on ChariPay's secure page. No card details ever go through Ultra TV.",
    declined: "The payment did not go through. You can try again.", upstream: "The payment service is not responding. Please try again in a moment.",
    invalid: `Invalid amount (${DONATE_MIN} to ${DONATE_MAX} MAD).`, thanks: "Thank you for your support!", thanksSub: "Your donation was received. It directly helps Ultra TV keep going.",
    back: "Back", github: "Source code (GitHub)",
  },
  es: {
    kofi: "Donar en Ko-fi", kofiNote: "Tarjeta o PayPal, desde cualquier país.", orMad: "O con tarjeta marroquí, en dírhams:",
    title: "Apoyar Ultra TV", sub: "Ultra TV es gratis, sin anuncios, y lo seguirá siendo. Una donación ayuda a pagar los servidores y el tiempo dedicado a mejorarlo.",
    pick: "Importe (dírhams marroquíes, MAD)", other: "Otro importe", give: "Donar", secure: "Pago con tarjeta en la página segura de ChariPay. Ningún dato bancario pasa por Ultra TV.",
    declined: "El pago no se completó. Puedes intentarlo de nuevo.", upstream: "El servicio de pago no responde. Inténtalo de nuevo en un momento.",
    invalid: `Importe no válido (de ${DONATE_MIN} a ${DONATE_MAX} MAD).`, thanks: "¡Gracias por tu apoyo!", thanksSub: "Hemos recibido tu donación. Ayuda directamente a que Ultra TV continúe.",
    back: "Volver", github: "Código fuente (GitHub)",
  },
  ar: {
    kofi: "تبرّع عبر Ko-fi", kofiNote: "بطاقة بنكية أو PayPal، من أي بلد.", orMad: "أو ببطاقة مغربية، بالدرهم:",
    title: "ادعم Ultra TV", sub: "Ultra TV مجاني وبلا إعلانات وسيبقى كذلك. يساعد تبرعك في دفع تكاليف الخوادم والوقت المخصص لتحسينه.",
    pick: "المبلغ (بالدرهم المغربي)", other: "مبلغ آخر", give: "تبرّع", secure: "الدفع بالبطاقة على صفحة ChariPay الآمنة. لا تمر أي بيانات بنكية عبر Ultra TV.",
    declined: "لم تتم عملية الدفع. يمكنك المحاولة مرة أخرى.", upstream: "خدمة الدفع لا تستجيب. حاول مرة أخرى بعد قليل.",
    invalid: `مبلغ غير صالح (من ${DONATE_MIN} إلى ${DONATE_MAX} درهم).`, thanks: "شكرًا لدعمك!", thanksSub: "وصلنا تبرعك، وهو يساعد Ultra TV مباشرة على الاستمرار.",
    back: "رجوع", github: "الشيفرة المصدرية (GitHub)",
  },
  pl: {
    kofi: "Wesprzyj przez Ko-fi", kofiNote: "Karta lub PayPal, z dowolnego kraju.", orMad: "Lub kartą marokańską, w dirhamach:",
    title: "Wesprzyj Ultra TV", sub: "Ultra TV jest darmowe, bez reklam i takie pozostanie. Darowizna pomaga opłacić serwery i czas poświęcony na rozwój.",
    pick: "Kwota (dirham marokański, MAD)", other: "Inna kwota", give: "Przekaż darowiznę", secure: "Płatność kartą na bezpiecznej stronie ChariPay. Żadne dane karty nie przechodzą przez Ultra TV.",
    declined: "Płatność nie powiodła się. Możesz spróbować ponownie.", upstream: "Usługa płatności nie odpowiada. Spróbuj ponownie za chwilę.",
    invalid: `Nieprawidłowa kwota (od ${DONATE_MIN} do ${DONATE_MAX} MAD).`, thanks: "Dziękujemy za wsparcie!", thanksSub: "Twoja darowizna dotarła. Bezpośrednio pomaga rozwijać Ultra TV.",
    back: "Wróć", github: "Kod źródłowy (GitHub)",
  },
};
