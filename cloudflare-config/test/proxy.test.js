import { describe, it, expect, vi, afterEach } from "vitest";
import proxy, { validateTarget, isPrivateHost } from "../../web/cloudflare/worker.js";

const env = { ALLOWED_HOSTS: "iptv.example.com, CDN.Example.com", ALLOWED_ORIGINS: "https://app.example.com" };
const get = (target, init = {}, e = env) =>
  proxy.fetch(new Request("https://proxy.test/?target=" + encodeURIComponent(target), init), e);

afterEach(() => vi.restoreAllMocks());

describe("proxy web : liste blanche et SSRF", () => {
  it("validateTarget_sansAllowedHosts_403", () => {
    expect(validateTarget("https://iptv.example.com/x", {}).status).toBe(403);
    expect(validateTarget("https://iptv.example.com/x", { ALLOWED_HOSTS: " , " }).status).toBe(403);
  });
  it("validateTarget_hoteNonListe_403", () => {
    expect(validateTarget("https://evil.example.org/", env).status).toBe(403);
  });
  it("validateTarget_hoteListe_insensibleALaCasse", () => {
    expect(validateTarget("https://cdn.example.com/a", env).url).toBeTruthy();
  });
  it.each(["file:///etc/passwd", "ftp://iptv.example.com/", "gopher://iptv.example.com/", "javascript:alert(1)"])(
    "validateTarget_%s_refuse", (t) => expect(validateTarget(t, env).status).toBe(400));
  it("validateTarget_identifiantsDansLUrl_refuses", () => {
    expect(validateTarget("https://user:pw@iptv.example.com/", env).status).toBe(400);
  });
  it.each(["127.0.0.1", "10.0.0.5", "192.168.1.1", "172.16.0.1", "169.254.169.254", "100.64.0.1", "0.0.0.0",
    "localhost", "foo.internal", "[::1]", "[fd00::1]", "[fe80::1]", "[::ffff:127.0.0.1]", "2130706433", "0x7f000001"])(
    "isPrivateHost_%s_vrai", (h) => expect(isPrivateHost(h)).toBe(true));
  it.each(["iptv.example.com", "8.8.8.8", "93.184.216.34", "172.32.0.1", "[2606:4700::1111]"])(
    "isPrivateHost_%s_faux", (h) => expect(isPrivateHost(h)).toBe(false));
  it("validateTarget_ipPriveeMemeDansLaListeBlanche_403", () => {
    expect(validateTarget("http://169.254.169.254/latest/meta-data", { ALLOWED_HOSTS: "169.254.169.254" }).status).toBe(403);
    expect(validateTarget("http://127.0.0.1:8080/", { ALLOWED_HOSTS: "127.0.0.1" }).status).toBe(403);
  });
});

describe("proxy web : requêtes", () => {
  it("methodePost_405", async () => {
    const r = await proxy.fetch(new Request("https://proxy.test/?target=https://iptv.example.com/", { method: "POST", body: "x" }), env);
    expect(r.status).toBe(405);
  });
  it("sansCible_400", async () => {
    expect((await proxy.fetch(new Request("https://proxy.test/"), env)).status).toBe(400);
  });
  it("cors_originNonListee_pasDEnTeteAllowOrigin", async () => {
    const r = await get("https://evil.example.org/", { headers: { origin: "https://evil.example" } });
    expect(r.headers.get("access-control-allow-origin")).toBeNull();
  });
  it("cors_originListee_refleteLOrigineExacte_jamaisEtoile", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("ok"));
    const r = await get("https://iptv.example.com/a", { headers: { origin: "https://app.example.com" } });
    expect(r.headers.get("access-control-allow-origin")).toBe("https://app.example.com");
  });
  it("cors_sansAllowedOrigins_aucunEnTete", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("ok"));
    const r = await get("https://iptv.example.com/a", { headers: { origin: "https://app.example.com" } }, { ALLOWED_HOSTS: "iptv.example.com" });
    expect(r.headers.get("access-control-allow-origin")).toBeNull();
  });
  it("redirection_versHoteInterdit_refusee_etNeSuitPas", async () => {
    const spy = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 302, headers: { location: "http://169.254.169.254/latest/meta-data" } }));
    const r = await get("https://iptv.example.com/a");
    expect(r.status).toBe(403);
    expect(spy).toHaveBeenCalledTimes(1);
  });
  it("redirection_versHoteAutorise_suivie", async () => {
    const spy = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(null, { status: 301, headers: { location: "https://cdn.example.com/b" } }))
      .mockResolvedValueOnce(new Response("final", { status: 200 }));
    const r = await get("https://iptv.example.com/a");
    expect(r.status).toBe(200);
    expect(await r.text()).toBe("final");
    expect(spy.mock.calls[1][0]).toBe("https://cdn.example.com/b");
    expect(spy.mock.calls[0][1].redirect).toBe("manual");
  });
  it("redirection_boucle_stoppeeApres3Sauts", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(async () => new Response(null, { status: 302, headers: { location: "https://iptv.example.com/loop" } }));
    expect((await get("https://iptv.example.com/a")).status).toBe(508);
  });
  it("reponse_retireSetCookieEtLocation", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("x", { headers: { "set-cookie": "a=b", "content-type": "text/plain" } }));
    const r = await get("https://iptv.example.com/a");
    expect(r.headers.get("set-cookie")).toBeNull();
  });
});
