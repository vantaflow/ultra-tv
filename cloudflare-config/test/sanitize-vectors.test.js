// Vecteurs de masquage du Worker (sanitizeLog). Prévus pour être partagés avec l'appli Android (LogSanitizer, test JVM
// à écrire : aucun test Android ne lit encore ce fichier).
//
// Format de test/fixtures/sanitize-vectors.json (stable : toute évolution de règle commence par ce fichier) :
//   [ { "name": "…", "input": "…", "mustNotContain": ["…"], "mustContain": ["…"] }, … ]
// - name : libellé lisible et unique ; le préfixe F1…F9 renvoie aux fuites de l'audit, « intact : » marque un texte
//   qui ne doit pas être abîmé.
// - input : texte brut tel qu'un appareil peut l'envoyer (message, pile, ligne JSON…), sauts de ligne compris.
// - mustNotContain : aucune de ces chaînes ne doit rester dans la sortie, comparaison SANS tenir compte de la casse.
// - mustContain : chacune de ces chaînes doit rester dans la sortie, comparaison exacte (casse comprise).
// Le nettoyage est appelé avec une longueur maximale d'au moins 32 768 caractères (aucune coupe dans ces cas), avec ET
// sans clé d'empreinte d'hôte : un vecteur ne doit donc jamais exiger une empreinte précise (`<host#…>`), que l'appli
// ne peut pas produire. Préfixes : F1…F9 (audit), I1…I3 (relecture), « règle » (un vecteur par règle : chaque règle
// retirée doit faire échouer au moins un vecteur), « rare », « intact ».
// Tous les identifiants sont FICTIFS (ZZUSERZZ, ZZPASSZZ, ZZTOKENZZ, fournisseur.invalid, 00:1A:79:AB:CD:EF,
// 192.168.77.88, example.invalid) : n'y mettez jamais de donnée réelle.
import { describe, it, expect } from "vitest";
import vectors from "./fixtures/sanitize-vectors.json";
import { sanitizeLog, reduceUrl, hostFingerprint, sha256, hmacSha256 } from "../src/sanitize.js";

// Secret FICTIF d'empreinte d'hôte (jamais celui de la production).
const KEY = "cle-de-test-fictive-pour-les-empreintes";
const tagOf = (s) => (s.match(/<(?:host|lan)#([0-9a-f]{12})>/) || [])[1];

describe("sanitizeLog : vecteurs partagés", () => {
  it("le fichier de vecteurs a le format attendu", () => {
    expect(Array.isArray(vectors)).toBe(true);
    expect(vectors.length).toBeGreaterThan(50);
    const names = new Set();
    for (const v of vectors) {
      expect(Object.keys(v).sort()).toEqual(["input", "mustContain", "mustNotContain", "name"]);
      expect(typeof v.input).toBe("string");
      expect(names.has(v.name)).toBe(false);
      names.add(v.name);
    }
    for (const f of ["F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9"]) expect(vectors.some((v) => v.name.startsWith(f + " "))).toBe(true);
  });

  it.each(vectors.map((v) => [v.name, v]))("%s", (_name, v) => {
    for (const hostKey of [null, KEY]) {
      const out = sanitizeLog(v.input, 32768, { hostKey });
      const low = out.toLowerCase();
      for (const s of v.mustNotContain) expect(low, `« ${s} » est resté dans : ${out}`).not.toContain(s.toLowerCase());
      for (const s of v.mustContain) expect(out, `« ${s} » a disparu de : ${out}`).toContain(s);
    }
  });

  it("idempotent : nettoyer deux fois ne change rien (réécriture des anciennes lignes relançable)", () => {
    for (const v of vectors) {
      const once = sanitizeLog(v.input, 32768, { hostKey: KEY });
      expect(sanitizeLog(once, 32768, { hostKey: KEY }), v.name).toBe(once);
    }
  });
});

describe("sanitizeLog : réduction des adresses", () => {
  it("garde schéma, empreinte salée de l'hôte, type de ressource et extension", () => {
    const fp = hostFingerprint("fournisseur.invalid", KEY);
    expect(fp).toMatch(/^[0-9a-f]{12}$/);
    const log = (s) => sanitizeLog(s, 4000, { hostKey: KEY });
    expect(log("http://fournisseur.invalid:8080/live/a/b/1.ts")).toBe(`http://<host#${fp}>/live/*.ts`);
    expect(log("http://FOURNISSEUR.invalid/a/b/1.ts")).toBe(`http://<host#${fp}>/*.ts`);
    expect(log("http://fournisseur.invalid/get.php?username=a&password=b")).toBe(`http://<host#${fp}>/get.php?*`);
    expect(log("http://192.168.77.88:8096")).toMatch(/^http:\/\/<lan#[0-9a-f]{12}>$/);
  });
  it("deux adresses du même fournisseur donnent la même empreinte, deux fournisseurs deux empreintes", () => {
    const log = (s) => sanitizeLog(s, 4000, { hostKey: KEY });
    const a = log("http://f1.invalid/x/y/1.ts"), b = log("https://f1.invalid:443/z"), c = log("http://f2.invalid/x");
    expect(tagOf(a)).toBe(tagOf(b));
    expect(tagOf(a)).not.toBe(tagOf(c));
  });
  it("l'empreinte ne porte que sur l'hôte : identifiants de l'autorité et chemin d'une clé d'hôte n'y entrent pas", () => {
    const log = (s) => sanitizeLog(s, 4000, { hostKey: KEY });
    const fp = hostFingerprint("fournisseur.invalid", KEY);
    expect(tagOf(log("http://ZZUSERZZ:ZZPASSZZ@fournisseur.invalid/"))).toBe(fp);
    expect(tagOf(log("url=fournisseur.invalid/ZZUSERZZ/ZZPASSZZ/1.ts"))).toBe(fp);
    expect(tagOf(log("server=fournisseur.invalid:8080"))).toBe(fp);
    expect(log("url=fournisseur.invalid/ZZUSERZZ/ZZPASSZZ/1.ts")).toBe(`url=<host#${fp}>/*.ts`);
  });
  it("une autre clé donne une autre empreinte (salée : rien à précalculer sans le secret)", () => {
    expect(hostFingerprint("fournisseur.invalid", KEY)).not.toBe(hostFingerprint("fournisseur.invalid", KEY + "x"));
    expect(hostFingerprint("Fournisseur.Invalid", KEY)).toBe(hostFingerprint("fournisseur.invalid", KEY));
  });
  it("sans clé : <host> / <lan> sans aucune empreinte (jamais de repli non salé)", () => {
    expect(hostFingerprint("fournisseur.invalid", null)).toBeNull();
    expect(hostFingerprint("fournisseur.invalid", "")).toBeNull();
    const out = sanitizeLog("http://fournisseur.invalid/live/a/b/1.ts http://192.168.77.88:8096 url=fournisseur.invalid");
    expect(out).toBe("http://<host>/live/*.ts http://<lan> url=<host>");
    expect(out).not.toContain("#");
  });
  it("au plus 64 empreintes nouvelles par appel (borne de temps CPU), puis <host> sans empreinte", () => {
    const input = Array.from({ length: 100 }, (_, i) => `http://h${i}.borne.invalid/x`).join(" ");
    const out = sanitizeLog(input, 32768, { hostKey: KEY + "-borne" });
    expect(out.match(/<host#[0-9a-f]{12}>/g).length).toBe(64);
    expect(out.match(/<host>/g).length).toBe(36);
    expect(out).not.toContain("borne.invalid");
    // Un hôte déjà calculé garde son empreinte même au-delà de la borne.
    const again = sanitizeLog(`${input} http://h0.borne.invalid/y`, 32768, { hostKey: KEY + "-borne" });
    expect(again.endsWith(`<host#${hostFingerprint("h0.borne.invalid", KEY + "-borne")}>/*`)).toBe(true);
  });
  it("reduceUrl : adresse sans chemin, avec fragment, encodée, sans schéma", () => {
    expect(reduceUrl("https", "h.invalid", { hostKey: KEY })).toMatch(/^https:\/\/<host#[0-9a-f]{12}>$/);
    expect(reduceUrl("https", "h.invalid")).toBe("https://<host>");
    expect(reduceUrl("https", "h.invalid/list.m3u#secret")).toMatch(/\/\*\.m3u$/);
    expect(reduceUrl("http", "h.invalid%2Flive%2Fu%2Fp%2F1.ts", { encoded: true })).toMatch(/\/live\/\*\.ts$/);
    expect(reduceUrl(null, "h.invalid/u/p/1.ts")).toBe("<host>/*.ts");
  });
});

describe("empreinte : SHA-256 et HMAC en JS pur, identiques à Web Crypto", () => {
  const hex = (b) => Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  const enc = (s) => new TextEncoder().encode(s);
  it("SHA-256 sur des longueurs autour des frontières de bloc", async () => {
    for (const n of [0, 1, 55, 56, 63, 64, 65, 119, 120, 128, 1000]) {
      const b = new Uint8Array(n).map((_, i) => (i * 31 + 7) & 255);
      expect(hex(sha256(b)), `longueur ${n}`).toBe(hex(new Uint8Array(await crypto.subtle.digest("SHA-256", b))));
    }
  });
  it("HMAC-SHA-256 : RFC 4231 cas 2, clé longue, et comparaison avec crypto.subtle", async () => {
    expect(hex(hmacSha256(enc("Jefe"), enc("what do ya want for nothing?")))).toBe("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    for (const [k, m] of [[KEY, "fournisseur.invalid"], ["k".repeat(131), "192.0.2.10"]]) {
      const ck = await crypto.subtle.importKey("raw", enc(k), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
      expect(hex(hmacSha256(enc(k), enc(m)))).toBe(hex(new Uint8Array(await crypto.subtle.sign("HMAC", ck, enc(m)))));
    }
    expect(hostFingerprint("fournisseur.invalid", KEY)).toBe(hex(hmacSha256(enc(KEY), enc("fournisseur.invalid"))).slice(0, 12));
  });
});

describe("sanitizeLog : bornes et coût", () => {
  it("non-chaîne → vide, caractères de contrôle retirés, longueur bornée", () => {
    expect(sanitizeLog(undefined)).toBe("");
    expect(sanitizeLog(42)).toBe("");
    expect(sanitizeLog("a\u0000b\u001bc")).toBe("abc");
    expect(sanitizeLog("a".repeat(10000), 100).length).toBeLessThanOrEqual(100);
  });

  // Coût. La contrainte réelle est la limite de 10 ms de temps CPU par requête de l'offre gratuite de Workers, pour
  // TOUTE la requête (nettoyage compris). Mesures en millisecondes entières (horloge de workerd) : la plus courte de
  // 7 passes après échauffement, la moins sensible aux autres fichiers de test qui tournent en parallèle. Elles
  // dépendent de la machine.
  // - Pile réaliste de 30 Ko : doit tenir sous 10 ms (mesuré : 2 à 4 ms en node sur un Mac récent).
  // - Entrées hostiles de 30 à 64 Ko : seuil INDICATIF de 40 ms (pires formes mesurées : 7 à 11 ms). Il garde la
  //   linéarité (un retour arrière catastrophique coûterait des secondes) ; au-delà de 10 ms en production, seule la
  //   requête de l'attaquant échoue (erreur 1102), les autres requêtes ne sont pas touchées.
  const fastest = (f) => {
    for (let i = 0; i < 2; i++) f();
    let best = Infinity;
    for (let i = 0; i < 7; i++) { const t = performance.now(); f(); best = Math.min(best, performance.now() - t); }
    return best;
  };
  const N = 48 * 1024;
  const fill = (unit) => unit.repeat(Math.ceil(N / unit.length)).slice(0, N);
  let n = 0;
  const hostile = [
    "a".repeat(64000),
    "http://" + "a/".repeat(32000),
    "http://h/x " + "a/b ".repeat(16000),
    "http://h " + "a ".repeat(32000),
    "=".repeat(64000),
    ":".repeat(64000),
    "aa:".repeat(21000),
    "0:".repeat(32000),
    "1.".repeat(32000),
    "%3A".repeat(21000),
    "a@".repeat(32000),
    ("x".repeat(63) + "@").repeat(1000),
    "eyJ" + "a".repeat(64000),
    "password=".repeat(7000),
    '"password":"' + '\\"'.repeat(30000),
    "user ".repeat(12800),
    "Cookie: " + "a=b; ".repeat(12000),
    ("\tat com.ultratv.Foo.bar(Foo.kt:12)\n").repeat(1700),
    fill("url=a "), fill("stalker:a "), fill("http://h/x a b c d e f "), fill("a:"),
    fill("GET /"), fill("GET /a/b "), fill("a:b@"), fill("("), fill('http://h/a"/'), fill("http://h/a\\x/"),
    fill("password=x y "), fill("http://h/get.php?password=x a b c "), fill("http://h/live/a/b c d e "),
    fill("%2541"), fill("\\u002f"), fill("a/b/1.ts "), fill("login failed for a / b "), fill("é@é.é "),
    Array.from({ length: 5000 }, () => `http://h${n++}.invalid/x`).join(" ").slice(0, N),
  ];
  it.each(hostile.map((h, i) => [i, h]))("entrée hostile n°%i : coût linéaire (seuil indicatif 40 ms)", (_i, h) => {
    expect(fastest(() => sanitizeLog(h, 32 * 1024, { hostKey: KEY }))).toBeLessThan(40);
  });

  it("pile réaliste de 30 Ko : nettoyée en moins de 10 ms (limite CPU réelle), sans fuite", () => {
    const frame = "\tat okhttp3.internal.connection.RealCall.execute(RealCall.kt:154)\n";
    const stack = `java.io.IOException: unexpected end of stream on http://fournisseur.invalid:8080/ZZUSERZZ/ZZPASSZZ/1.ts\n${frame.repeat(400)}`
      + `JSON input: {"username":"ZZUSERZZ","password":"ZZPASSZZ"}\n${frame.repeat(60)}`;
    expect(stack.length).toBeGreaterThan(30000);
    expect(fastest(() => sanitizeLog(stack, 32 * 1024, { hostKey: KEY }))).toBeLessThan(10);
    const out = sanitizeLog(stack, 32 * 1024, { hostKey: KEY });
    expect(out).not.toContain("ZZUSERZZ");
    expect(out).not.toContain("ZZPASSZZ");
    expect(out).toContain("RealCall.kt:154");
  });
});
