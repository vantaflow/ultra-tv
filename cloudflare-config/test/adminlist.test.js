import { describe, expect, it } from "vitest";
import {
  parseListParams, paginate, sortRows, topWithOthers, countBy, mergeCounts, anonQuery, eventsQuery, filterAccounts, filterDevices,
  flattenDevices, listUrl, ANON_SORTS, EVENT_SORTS, ACCOUNT_SORTS, DEVICE_SORTS, DAY_MS, fixedInOf } from "../src/admin.js";
import { dateCell, flagCode } from "../src/adminui.js";

const sp = (q) => new URLSearchParams(q);
const OPTS = { sorts: ANON_SORTS, sort: "last", dir: "desc", filters: ["platform", "version", "country"] };

describe("paramètres de liste", () => {
  it("valeurs par défaut, tri hors liste blanche ignoré, page et taille bornées", () => {
    const p = parseListParams(sp(""), OPTS);
    expect(p).toMatchObject({ page: 1, size: 50, sort: "last", dir: "desc", q: "", p: "" });
    const bad = parseListParams(sp("sort=password;DROP&dir=sideways&page=-4&size=99999&p=1y"), OPTS);
    expect(bad).toMatchObject({ page: 1, size: 200, sort: "last", dir: "desc", p: "" });
    expect(parseListParams(sp("sort=web&dir=asc&page=3&p=7d&platform=windows&q=%20abc%20"), OPTS))
      .toMatchObject({ sort: "web", dir: "asc", page: 3, p: "7d", q: "abc", f: { platform: "windows", version: "", country: "" } });
  });
  it("valeur de filtre nettoyée et limitée à 64 caractères", () => {
    expect(parseListParams(sp("q=" + "x".repeat(200)), OPTS).q).toHaveLength(64);
    expect(parseListParams(sp("version=a%00b"), OPTS).f.version).toBe("a b");
  });
});

describe("pagination", () => {
  it("bornes, pages et plage affichée", () => {
    expect(paginate(0, 1)).toMatchObject({ pages: 1, page: 1, offset: 0, from: 0, to: 0 });
    expect(paginate(120, 1, 50)).toMatchObject({ pages: 3, offset: 0, from: 1, to: 50 });
    expect(paginate(120, 3, 50)).toMatchObject({ page: 3, offset: 100, from: 101, to: 120 });
    expect(paginate(120, 99, 50).page).toBe(3);
    expect(paginate(120, 0, 50).page).toBe(1);
  });
});

describe("tri en mémoire", () => {
  const rows = [{ n: "b", v: 2 }, { n: "a", v: 10 }, { n: "", v: 0 }, { n: "c", v: 2 }];
  const getters = { n: (r) => r.n, v: (r) => r.v };
  it("croissant / décroissant, stable, valeurs vides toujours en dernier", () => {
    expect(sortRows(rows, "n", "asc", getters).map((r) => r.n)).toEqual(["a", "b", "c", ""]);
    expect(sortRows(rows, "n", "desc", getters).map((r) => r.n)).toEqual(["c", "b", "a", ""]);
    expect(sortRows(rows, "v", "desc", getters).map((r) => r.n)).toEqual(["a", "b", "c", ""]);
    expect(sortRows(rows, "inconnue", "asc", getters)).toEqual(rows);
  });
  it("tri naturel des versions", () => {
    const v = [{ v: "1.2.9" }, { v: "1.2.10" }, { v: "1.10.0" }];
    expect(sortRows(v, "v", "asc", { v: (r) => r.v }).map((r) => r.v)).toEqual(["1.2.9", "1.2.10", "1.10.0"]);
  });
});

describe("agrégats", () => {
  it("top N + « autres »", () => {
    const pairs = Array.from({ length: 13 }, (_, i) => [`P${i}`, 13 - i]);
    const top = topWithOthers(pairs, 10);
    expect(top).toHaveLength(11);
    expect(top[10]).toEqual(["autres", 3 + 2 + 1]);
    expect(topWithOthers(pairs.slice(0, 3), 10)).toHaveLength(3);
  });
  it("comptage et fusion", () => {
    expect(countBy(["a", "b", "a", "", null])).toEqual([["a", 2], ["b", 1]]);
    expect(mergeCounts([["a", 2], ["b", 1]], [["b", 4]])).toEqual([["b", 5], ["a", 2]]);
  });
});

describe("requêtes SQL (D1)", () => {
  const NOW = 1_000_000_000_000;
  const params = (q) => parseListParams(sp(q), OPTS);
  it("sans filtre : pas de WHERE, tri par dernier relevé décroissant", () => {
    expect(anonQuery(params(""), NOW)).toEqual({ where: "", args: [], order: "last_seen DESC, fingerprint ASC" });
  });
  it("filtres plateforme, version, pays, période : valeurs liées, jamais concaténées", () => {
    const q = anonQuery(params("platform=android-tv&version=1.2.3&country=ma&p=7d&sort=first&dir=asc"), NOW);
    expect(q.where).toBe("WHERE platform = ? AND version = ? AND country = ? AND last_seen >= ?");
    expect(q.args).toEqual(["android-tv", "1.2.3", "MA", NOW - 7 * DAY_MS]);
    expect(q.order).toBe("first_seen ASC, fingerprint ASC");
  });
  it("plateforme inconnue = non déclarée ; recherche : jokers LIKE échappés", () => {
    expect(anonQuery(params("platform=unknown"), NOW).where).toBe("WHERE platform IS NULL");
    const q = anonQuery(params("q=50%25_x"), NOW);
    expect(q.args).toEqual(["%50\\%\\_x%", "%50\\%\\_x%", "%50\\%\\_x%", "%50\\%\\_x%"]);
    expect(q.where).toContain("ESCAPE");
  });
  it("tri : colonne hors liste blanche remplacée, injection impossible", () => {
    const q = anonQuery({ ...params(""), sort: "last_seen; DROP TABLE anon_pings", dir: "asc" }, NOW);
    expect(q.order).toBe("last_seen ASC, fingerprint ASC");
    expect(JSON.stringify(anonQuery(params("version=%27%3B+DROP"), NOW).where)).not.toContain("DROP");
  });
  it("journaux et plantages : type, niveau, recherche selon le type", () => {
    const o = { sorts: EVENT_SORTS, sort: "ts", dir: "desc", filters: ["version", "country", "level"] };
    const ev = eventsQuery("event", parseListParams(sp("level=ERROR&q=boom&p=24h"), o), NOW);
    expect(ev.where).toContain("kind = ? AND level = ? AND ts >= ? AND (message LIKE");
    expect(ev.args.slice(0, 3)).toEqual(["event", "error", NOW - DAY_MS]);
    const cr = eventsQuery("crash", parseListParams(sp("q=NullPointer"), o), NOW);
    expect(cr.where).toContain("stack LIKE");
    expect(cr.where).not.toContain("message LIKE");
    expect(cr.order).toBe("ts DESC, id DESC");
    // Plantages déjà corrigés masqués par défaut ; « fixed=show » les remontre.
    expect(cr.where).toContain("NOT ((stack LIKE ? AND version_code < ?)");
    const oc = { ...o, filters: ["version", "country", "fixed"] };
    expect(eventsQuery("crash", parseListParams(sp("fixed=show"), oc), NOW).where).not.toContain("NOT (");
  });
  it("plantages réglés : version de correction, régression visible", () => {
    const stack = "main: java.lang.IndexOutOfBoundsException: Illegal attempt to access index 0 in ItemSnapshotList of size 0";
    expect(fixedInOf(stack, 10308)).toBe("1.3.16");
    expect(fixedInOf(stack, 10316)).toBeNull();
    expect(fixedInOf("java.lang.IllegalStateException: A migration from 21 to 20 was required but not found.", 10292)).toBe("1.2.93");
    expect(fixedInOf("java.lang.NullPointerException", 10200)).toBeNull();
  });
});

describe("filtres comptes et appareils (mémoire)", () => {
  const NOW = 10 * DAY_MS;
  const accounts = [
    { login: "alice", legacy: false, country: "MA", createdAt: 1, lastLoginAt: NOW - 1000, lastSeen: NOW - 1000, devices: [{ name: "Salon", model: "SKYWORTH", version: "1.2.5", country: "MA", lastSeen: NOW - 1000, createdAt: 1, edition: "standard" }] },
    { login: "bob", legacy: false, country: "FR", createdAt: 2, lastLoginAt: 0, lastSeen: 0, devices: [] },
    { login: "aa:bb:cc:dd:ee:ff", legacy: true, country: null, createdAt: 0, lastLoginAt: 0, lastSeen: 0, devices: [{ name: "Vieux", model: "", version: "", country: null, lastSeen: 0, createdAt: 0, edition: "standard" }] },
  ];
  const f = (q, sorts = ACCOUNT_SORTS) => parseListParams(sp(q), { sorts, sort: "created", filters: ["country", "version"] });
  it("comptes : recherche, pays, version, période", () => {
    expect(filterAccounts(accounts, f("q=sky"), NOW).map((r) => r.login)).toEqual(["alice"]);
    expect(filterAccounts(accounts, f("country=fr"), NOW).map((r) => r.login)).toEqual(["bob"]);
    expect(filterAccounts(accounts, f("version=1.2.5"), NOW).map((r) => r.login)).toEqual(["alice"]);
    expect(filterAccounts(accounts, f("p=24h"), NOW).map((r) => r.login)).toEqual(["alice"]);
  });
  it("appareils : aplatis (anciens comptes MAC exclus), filtrés", () => {
    const devs = flattenDevices(accounts);
    expect(devs).toHaveLength(1);
    expect(devs[0].login).toBe("alice");
    expect(filterDevices(devs, f("q=alice", DEVICE_SORTS), NOW)).toHaveLength(1);
    expect(filterDevices(devs, f("country=FR", DEVICE_SORTS), NOW)).toHaveLength(0);
    expect(filterDevices(devs, f("p=7d", DEVICE_SORTS), NOW)).toHaveLength(1);
  });
});

describe("liens et affichage", () => {
  it("listUrl conserve les filtres, remet la page à 1 quand le tri change", () => {
    const p = parseListParams(sp("platform=windows&page=4&q=x"), OPTS);
    expect(listUrl("/admin/anon", p, { sort: "web", dir: "asc" })).toBe("/admin/anon?q=x&sort=web&dir=asc&platform=windows");
    expect(listUrl("/admin/anon", p, { page: 5 })).toContain("page=5");
    expect(listUrl("/admin/anon", parseListParams(sp(""), { ...OPTS, sort: undefined }), { sort: undefined })).toBe("/admin/anon?dir=desc");
  });
  it("date relative avec date exacte en info-bulle, drapeau + code", () => {
    const html = dateCell(Date.UTC(2026, 10, 1, 8, 30), Date.UTC(2026, 10, 1, 11, 30));
    expect(html).toContain("il y a 3 h");
    expect(html).toContain('title="2026-11-01 08:30 UTC"');
    expect(dateCell(0)).toBe("—");
    expect(flagCode("MA")).toBe("Maroc · MA");
    expect(flagCode("x")).toBe("—");
  });
});
