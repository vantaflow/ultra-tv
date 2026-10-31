import { describe, expect, it } from "vitest";
import { call, newAccount } from "./helpers.js";
import { activeAnnouncements, announcementState, parseAnnouncementForm } from "../src/announcements.js";

const H = 3600_000;
const form = (o) => new Map(Object.entries(o));

describe("alertes : règles", () => {
  it("formulaire : message obligatoire, niveau et cible bornés, début par défaut maintenant", () => {
    expect(parseAnnouncementForm(form({ message: " " }), 1000).error).toBe("ann_empty");
    const v = parseAnnouncementForm(form({ message: "Coupure", level: "pirate", audience: "fridge" }), 1000).value;
    expect(v).toMatchObject({ message: "Coupure", level: "info", audience: "all", startAt: 1000, endAt: null });
    expect(v.id).toMatch(/^[0-9a-f]{16}$/);
  });
  it("formulaire : millisecondes du navigateur prioritaires, sinon date sans fuseau lue en UTC ; fin après début et à venir", () => {
    const now = Date.parse("2026-11-01T10:00:00Z");
    const v = parseAnnouncementForm(form({ message: "m", startMs: String(now + H), end: "2026-11-01T14:00" }), now).value;
    expect(v.startAt).toBe(now + H);
    expect(v.endAt).toBe(Date.parse("2026-11-01T14:00:00Z"));
    expect(parseAnnouncementForm(form({ message: "m", startMs: String(now + 2 * H), endMs: String(now + H) }), now).error).toBe("ann_dates");
    expect(parseAnnouncementForm(form({ message: "m", startMs: String(now - 2 * H), endMs: String(now - H) }), now).error).toBe("ann_past");
  });
  it("diffusion : seulement dans la fenêtre, urgentes d'abord, cible respectée, champs internes jamais exposés", () => {
    const now = 10 * H;
    const list = [
      { id: "a", message: "info", level: "info", startAt: 9 * H, endAt: 11 * H, by: "admin", createdAt: 1 },
      { id: "b", message: "urgent", level: "urgent", startAt: 8 * H, endAt: null, audience: "tv" },
      { id: "c", message: "futur", level: "urgent", startAt: 12 * H },
      { id: "d", message: "fini", level: "urgent", startAt: 1 * H, endAt: 2 * H },
    ];
    expect(activeAnnouncements(list, now).map((a) => a.id)).toEqual(["b", "a"]);
    expect(activeAnnouncements(list, now, "mobile").map((a) => a.id)).toEqual(["a"]);
    expect(Object.keys(activeAnnouncements(list, now)[1])).not.toContain("by");
    expect(announcementState(list[2], now)).toBe("scheduled");
    expect(announcementState(list[3], now)).toBe("ended");
  });
});

// Un seul compte administrateur par fichier (ADMIN_LOGINS = "khalilbenaz" ; le stockage est partagé entre ces tests).
let adminP;
const adminAccount = () => (adminP ||= newAccount("khalilbenaz"));

describe("alertes : routes", () => {
  it("admin : programmer, diffuser, arrêter, supprimer", async () => {
    const admin = await adminAccount();
    const add = await call("/admin/announcements", { method: "POST", ip: admin.ip, cookie: admin.cookie, form: { csrf: admin.csrf, title: "Maintenance", message: "Coupure à 2 h", level: "warning" } });
    expect(add.headers.get("location")).toBe("/?m=ann_added#administration");
    const api = await call("/api/announcements");
    expect(api.headers.get("cache-control")).toContain("max-age=60");
    const { items } = await api.json();
    expect(items).toHaveLength(1);
    expect(items[0]).toMatchObject({ title: "Maintenance", message: "Coupure à 2 h", level: "warning" });
    const page = await (await call("/", { cookie: admin.cookie, ip: admin.ip })).text();
    expect(page).toContain('id="alertes"');
    expect(page).toContain("Coupure à 2 h");
    const end = await call(`/admin/announcements/${items[0].id}/end`, { method: "POST", ip: admin.ip, cookie: admin.cookie, form: { csrf: admin.csrf } });
    expect(end.headers.get("location")).toBe("/?m=ann_ended#administration");
    expect((await (await call("/api/announcements")).json()).items).toHaveLength(0);
    const del = await call(`/admin/announcements/${items[0].id}/delete`, { method: "POST", ip: admin.ip, cookie: admin.cookie, form: { csrf: admin.csrf } });
    expect(del.headers.get("location")).toBe("/?m=ann_deleted#administration");
    expect(await (await call("/", { cookie: admin.cookie, ip: admin.ip })).text()).not.toContain("Coupure à 2 h");
  });
  it("compte ordinaire : 404 et rien d'enregistré ; sans jeton CSRF : refusé", async () => {
    const a = await newAccount();
    const r = await call("/admin/announcements", { method: "POST", ip: a.ip, cookie: a.cookie, form: { csrf: a.csrf, message: "pirate" } });
    expect(r.status).toBe(404);
    expect(await (await call("/", { cookie: a.cookie, ip: a.ip })).text()).not.toContain('id="alertes"');
    const admin = await adminAccount();
    const noCsrf = await call("/admin/announcements", { method: "POST", ip: admin.ip, cookie: admin.cookie, form: { message: "x" } });
    expect(noCsrf.status).toBe(403);
    expect((await (await call("/api/announcements")).json()).items).toHaveLength(0);
  });
  it("le texte est échappé dans le tableau de bord", async () => {
    const admin = await adminAccount();
    await call("/admin/announcements", { method: "POST", ip: admin.ip, cookie: admin.cookie, form: { csrf: admin.csrf, message: "<script>alert(1)</script>" } });
    const page = await (await call("/", { cookie: admin.cookie, ip: admin.ip })).text();
    expect(page).not.toContain("<script>alert(1)</script>");
    expect(page).toContain("&lt;script&gt;");
  });
});
