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
// Le nettoyage est appelé avec une longueur maximale d'au moins 32 768 caractères (aucune coupe dans ces cas). Un
// vecteur n'exige jamais une empreinte précise (`<host#…>`). Préfixes : F1…F9 (audit), I1…I3 (relecture), « règle » (un vecteur par règle : chaque règle
// retirée doit faire échouer au moins un vecteur), « rare », « intact ».
// Tous les identifiants sont FICTIFS (ZZUSERZZ, ZZPASSZZ, ZZTOKENZZ, fournisseur.invalid, 00:1A:79:AB:CD:EF,
// 192.168.77.88, example.invalid) : n'y mettez jamais de donnée réelle.
import { describe, it, expect } from "vitest";
import vectors from "./fixtures/sanitize-vectors.json";
import { sanitizeLog, reduceUrl, hostFingerprint } from "../src/sanitize.js";

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
    const out = sanitizeLog(v.input, 32768);
    const low = out.toLowerCase();
    for (const s of v.mustNotContain) expect(low, `« ${s} » est resté dans : ${out}`).not.toContain(s.toLowerCase());
    for (const s of v.mustContain) expect(out, `« ${s} » a disparu de : ${out}`).toContain(s);
  });

  it("idempotent : nettoyer deux fois ne change rien (réécriture des anciennes lignes relançable)", () => {
    for (const v of vectors) {
      const once = sanitizeLog(v.input, 32768);
      expect(sanitizeLog(once, 32768), v.name).toBe(once);
    }
  });
});

describe("sanitizeLog : réduction des adresses", () => {
  it("garde schéma, empreinte stable de l'hôte, type de ressource et extension", () => {
    const fp = hostFingerprint("fournisseur.invalid");
    expect(fp).toMatch(/^[0-9a-f]{8}$/);
    expect(sanitizeLog("http://fournisseur.invalid:8080/live/a/b/1.ts")).toBe(`http://<host#${fp}>/live/*.ts`);
    expect(sanitizeLog("http://FOURNISSEUR.invalid/a/b/1.ts")).toBe(`http://<host#${fp}>/*.ts`);
    expect(sanitizeLog("http://fournisseur.invalid/get.php?username=a&password=b")).toBe(`http://<host#${fp}>/get.php?*`);
    expect(sanitizeLog("http://192.168.77.88:8096")).toMatch(/^http:\/\/<lan#[0-9a-f]{8}>$/);
  });
  it("deux adresses du même fournisseur donnent la même empreinte, deux fournisseurs deux empreintes", () => {
    const a = sanitizeLog("http://f1.invalid/x/y/1.ts"), b = sanitizeLog("https://f1.invalid:443/z"), c = sanitizeLog("http://f2.invalid/x");
    const fp = (s) => s.match(/<host#([0-9a-f]{8})>/)[1];
    expect(fp(a)).toBe(fp(b));
    expect(fp(a)).not.toBe(fp(c));
  });
  it("empreinte FNV-1a 32 bits connue (référence pour l'implémentation Kotlin)", () => {
    expect(hostFingerprint("")).toBe("811c9dc5");
    expect(hostFingerprint("a")).toBe("e40c292c");
    expect(hostFingerprint("Fournisseur.Invalid")).toBe(hostFingerprint("fournisseur.invalid"));
  });
  it("reduceUrl : adresse sans chemin, avec fragment, encodée", () => {
    expect(reduceUrl("https", "h.invalid")).toMatch(/^https:\/\/<host#[0-9a-f]{8}>$/);
    expect(reduceUrl("https", "h.invalid/list.m3u#secret")).toMatch(/\/\*\.m3u$/);
    expect(reduceUrl("http", "h.invalid%2Flive%2Fu%2Fp%2F1.ts", { encoded: true })).toMatch(/\/live\/\*\.ts$/);
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
  // TOUTE la requête (nettoyage compris). Mesures en millisecondes entières (horloge de workerd), médiane de 5 passes
  // après échauffement : elles dépendent de la machine.
  // - Pile réaliste de 30 Ko : doit tenir sous 10 ms (mesuré : 2 à 4 ms en node sur un Mac récent).
  // - Entrées hostiles de 30 à 64 Ko : seuil INDICATIF de 40 ms (pires formes mesurées : 7 à 11 ms). Il garde la
  //   linéarité (un retour arrière catastrophique coûterait des secondes) ; au-delà de 10 ms en production, seule la
  //   requête de l'attaquant échoue (erreur 1102), les autres requêtes ne sont pas touchées.
  const median = (f) => {
    for (let i = 0; i < 2; i++) f();
    const runs = [];
    for (let i = 0; i < 5; i++) { const t = performance.now(); f(); runs.push(performance.now() - t); }
    return runs.sort((a, b) => a - b)[2];
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
    expect(median(() => sanitizeLog(h, 32 * 1024))).toBeLessThan(40);
  });

  it("pile réaliste de 30 Ko : nettoyée en moins de 10 ms (limite CPU réelle), sans fuite", () => {
    const frame = "\tat okhttp3.internal.connection.RealCall.execute(RealCall.kt:154)\n";
    const stack = `java.io.IOException: unexpected end of stream on http://fournisseur.invalid:8080/ZZUSERZZ/ZZPASSZZ/1.ts\n${frame.repeat(400)}`
      + `JSON input: {"username":"ZZUSERZZ","password":"ZZPASSZZ"}\n${frame.repeat(60)}`;
    expect(stack.length).toBeGreaterThan(30000);
    expect(median(() => sanitizeLog(stack, 32 * 1024))).toBeLessThan(10);
    const out = sanitizeLog(stack, 32 * 1024);
    expect(out).not.toContain("ZZUSERZZ");
    expect(out).not.toContain("ZZPASSZZ");
    expect(out).toContain("RealCall.kt:154");
  });
});
