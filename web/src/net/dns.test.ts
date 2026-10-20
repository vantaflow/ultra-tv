import { afterEach, describe, expect, it, vi } from "vitest";
import { DEFAULT_DNS, chromiumScope, dnsBridge, dnsIssues, dnsUsable, isIp, isValidDohUrl, normalizeDns, pushDns, sourceHost, testOutcome } from "./dns";
import { translate } from "@/i18n";

describe("DNS : modèle et validation", () => {
  it("normalizeDns tolère les valeurs absentes ou inconnues", () => {
    expect(normalizeDns(undefined)).toEqual(DEFAULT_DNS);
    expect(normalizeDns({ mode: "QUAD9", primary: " 1.2.3.4 " })).toMatchObject({ mode: "quad9", primary: "1.2.3.4" });
    expect(normalizeDns({ mode: "evil", dohPreset: "x" })).toMatchObject({ mode: "system", dohPreset: "cloudflare" });
  });

  it("isIp : IPv4 / IPv6, rejette noms et ports", () => {
    for (const ok of ["8.8.8.8", "149.112.112.112", "2606:4700:4700::1111", "::1"]) expect(isIp(ok), ok).toBe(true);
    for (const ko of ["", "256.1.1.1", "1.2.3", "dns.google", "1.1.1.1:53", "abc:xyz"]) expect(isIp(ko), ko).toBe(false);
  });

  it("isValidDohUrl : https uniquement, sans identifiants", () => {
    expect(isValidDohUrl("https://dns.example/dns-query")).toBe(true);
    expect(isValidDohUrl("http://dns.example/dns-query")).toBe(false);
    expect(isValidDohUrl("https://u:p@dns.example/")).toBe(false);
    expect(isValidDohUrl("dns.example")).toBe(false);
  });

  it("dnsIssues / dnsUsable : seuls les champs du mode courant comptent", () => {
    expect(dnsUsable(DEFAULT_DNS)).toBe(false);
    expect(dnsUsable({ ...DEFAULT_DNS, mode: "google" })).toBe(true);
    expect(dnsIssues({ ...DEFAULT_DNS, mode: "custom", primary: "1.1.1.1", secondary: "" })).toEqual({ dohUrl: false, primary: false, secondary: false });
    expect(dnsIssues({ ...DEFAULT_DNS, mode: "custom", primary: "x", secondary: "y" })).toEqual({ dohUrl: false, primary: true, secondary: true });
    expect(dnsUsable({ ...DEFAULT_DNS, mode: "doh", dohPreset: "custom", dohUrl: "http://x" })).toBe(false);
    expect(dnsUsable({ ...DEFAULT_DNS, mode: "doh", dohPreset: "custom", dohUrl: "https://x.example/q" })).toBe(true);
    // Une URL invalide n'est pas signalée hors du mode DoH personnalisé.
    expect(dnsIssues({ ...DEFAULT_DNS, mode: "doh", dohPreset: "google", dohUrl: "nope" }).dohUrl).toBe(false);
  });

  it("chromiumScope : préréglages => point DoH, IP inconnues => proxy seulement", () => {
    expect(chromiumScope(DEFAULT_DNS)).toBe("full");
    expect(chromiumScope({ ...DEFAULT_DNS, mode: "cloudflare" })).toBe("doh-endpoint");
    expect(chromiumScope({ ...DEFAULT_DNS, mode: "doh" })).toBe("full");
    expect(chromiumScope({ ...DEFAULT_DNS, mode: "custom", primary: "192.168.1.1" })).toBe("proxy-only");
    expect(chromiumScope({ ...DEFAULT_DNS, mode: "custom", primary: "192.168.1.1", secondary: "9.9.9.9" })).toBe("doh-endpoint");
  });
});

describe("DNS : hôte du fournisseur testé", () => {
  const base = { server: "", m3uUrl: "", stalkerApi: undefined };
  it("extrait le nom d'hôte seul (jamais l'URL avec identifiants)", () => {
    expect(sourceHost({ ...base, type: "xtream", server: "http://panel.example:8080" })).toBe("panel.example");
    expect(sourceHost({ ...base, type: "xtream", server: "panel.example:8080" })).toBe("panel.example");
    expect(sourceHost({ ...base, type: "m3u", m3uUrl: "http://iptv.example/get.php?username=u&password=p" })).toBe("iptv.example");
    expect(sourceHost({ ...base, type: "stalker", server: "http://mag.example/c/", stalkerApi: "http://api.example/portal.php" })).toBe("api.example");
  });
  it("aucun hôte : fichier local, source absente, URL invalide", () => {
    expect(sourceHost({ ...base, type: "m3u", m3uUrl: "file:/x.m3u" })).toBeNull();
    expect(sourceHost(null)).toBeNull();
    expect(sourceHost({ ...base, type: "xtream", server: "" })).toBeNull();
  });
});

describe("DNS : résultat du test", () => {
  it("serveur personnalisé, système, repli et échec", () => {
    expect(testOutcome({ ok: true, address: "1.2.3.4", ms: 12.4, via: "custom" })).toEqual({ key: "dns.testOk", vars: { ip: "1.2.3.4", ms: 12 } });
    expect(testOutcome({ ok: true, address: "1.2.3.4", ms: 5, via: "system" }).key).toBe("dns.testSystem");
    expect(testOutcome({ ok: true, address: "1.2.3.4", ms: 5, via: "system", reason: "ETIMEDOUT" })).toEqual({ key: "dns.testFallback", vars: { ip: "1.2.3.4", reason: "ETIMEDOUT" } });
    expect(testOutcome({ ok: false, reason: "ENOTFOUND" }).key).toBe("dns.testFailed");
    expect(testOutcome(undefined).key).toBe("dns.testFailed");
  });
  it("les messages sont traduits avec leurs variables", () => {
    const o = testOutcome({ ok: true, address: "1.2.3.4", ms: 7, via: "system", reason: "ETIMEDOUT" });
    expect(translate("fr", o.key, o.vars)).toBe("Serveur injoignable (ETIMEDOUT) · DNS système : 1.2.3.4");
  });
});

describe("DNS : pont Electron", () => {
  afterEach(() => { vi.unstubAllGlobals(); });
  it("absent dans un navigateur : section masquée, envoi sans effet", async () => {
    vi.stubGlobal("window", {});
    expect(dnsBridge()).toBeNull();
    await expect(pushDns(DEFAULT_DNS)).resolves.toBeUndefined();
  });
  it("Electron : le réglage est envoyé au processus principal ; une erreur IPC est absorbée", async () => {
    const setDns = vi.fn().mockResolvedValue({ appliesToChromium: true });
    vi.stubGlobal("window", { ultratv: { isElectron: true, setDns, testDns: vi.fn() } });
    await pushDns({ ...DEFAULT_DNS, mode: "google" });
    expect(setDns).toHaveBeenCalledWith(expect.objectContaining({ mode: "google" }));
    setDns.mockRejectedValueOnce(new Error("ipc"));
    await expect(pushDns(DEFAULT_DNS)).resolves.toBeUndefined();
  });
});
