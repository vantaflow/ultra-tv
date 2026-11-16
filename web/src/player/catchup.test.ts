import { describe, expect, it } from "vitest";
import { availability, earliestMs, fillTemplate, m3uTemplate, parseCatchupType, xtreamStamp, xtreamUrl } from "./catchup";

// Hôtes et identifiants fictifs. Début = 2023-11-14 22:14:00 UTC.
const start = 1_700_000_040_000, end = start + 45 * 60_000, now = 1_700_100_000_000;
const live = "http://example.test:8080/live/usr/pwd/42.ts";

describe("catchup : Xtream", () => {
  it("forme chemin, à l'heure UTC", () =>
    expect(xtreamUrl(live, start, end, "path", "UTC")).toBe("http://example.test:8080/timeshift/usr/pwd/45/2023-11-14:22-14/42.ts"));
  it("forme php", () =>
    expect(xtreamUrl(live, start, end, "php", "UTC")).toBe("http://example.test:8080/streaming/timeshift.php?username=usr&password=pwd&stream=42&start=2023-11-14:22-14&duration=45"));
  it("heure écrite dans le fuseau du fournisseur", () => expect(xtreamStamp(start, "Europe/Paris")).toBe("2023-11-14:23-14"));
  it("URL non Xtream : null", () => expect(xtreamUrl("http://example.test/a.m3u8", start, end, "path", "UTC")).toBeNull());
});

describe("catchup : M3U", () => {
  it("types", () => {
    expect(parseCatchupType(" Shift ")).toBe("shift");
    expect(parseCatchupType("xc")).toBe("xc");
    expect(parseCatchupType("?")).toBeNull();
  });
  it("gabarits", () => {
    expect(m3uTemplate("default", "http://c.example.test/{utc}", "http://s.example.test/1.ts")).toBe("http://c.example.test/{utc}");
    expect(m3uTemplate("append", "?u={utc}", "http://s.example.test/1.ts")).toBe("http://s.example.test/1.ts?u={utc}");
    expect(m3uTemplate("shift", null, "http://s.example.test/1.ts")).toBe("http://s.example.test/1.ts?utc={utc}&lutc={lutc}");
    expect(m3uTemplate("flussonic", null, "http://s.example.test/ch/mpegts")).toBe("http://s.example.test/ch/timeshift_abs-{utc}.ts");
    expect(m3uTemplate("xc", "x", "http://s.example.test/1.ts")).toBeNull();
    expect(m3uTemplate(null, "?u={utc}", "http://s.example.test/1.ts")).toBe("http://s.example.test/1.ts?u={utc}");
  });
  it("jetons", () =>
    expect(fillTemplate("{utc}-{utcend}-{lutc}-{offset}-{duration}-{duration:60}-{Y}{m}{d}T{H}{M}{S}", start, end, now))
      .toBe("1700000040-1700002740-1700100000-99960-2700-45-20231114T221400"));
});

describe("catchup : bornes de jours d'archive", () => {
  const day = 86_400_000;
  it("fenêtre", () => {
    expect(earliestMs(2, now)).toBe(now - 2 * day);
    expect(availability(2, now - day, now)).toBe("available");
    expect(availability(2, now - 3 * day, now)).toBe("outOfWindow");
    expect(availability(0, now - 1000, now)).toBe("notArchived");
    expect(availability(2, now + 1, now)).toBe("notStarted");
  });
});
