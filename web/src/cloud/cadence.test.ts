import { describe, expect, it } from "vitest";
import { createCadence, createPushGate, fingerprint } from "./cadence";

const MIN = 60_000;

describe("cadence adaptative", () => {
  it("2 min pendant 10 min après un changement, puis 10 min", () => {
    const c = createCadence({ start: 0 });
    c.noteChange(100 * MIN);
    expect(c.intervalMs(100 * MIN)).toBe(2 * MIN);
    expect(c.intervalMs(109 * MIN)).toBe(2 * MIN);
    expect(c.intervalMs(110 * MIN)).toBe(10 * MIN);
    expect(c.intervalMs(500 * MIN)).toBe(10 * MIN);
  });
  it("un nouveau changement (local ou reçu) ramène à 2 min", () => {
    const c = createCadence({ start: 0 });
    expect(c.intervalMs(300 * MIN)).toBe(10 * MIN);
    c.noteChange(300 * MIN);
    expect(c.intervalMs(301 * MIN)).toBe(2 * MIN);
  });
  it("le démarrage compte comme un changement", () => {
    const c = createCadence({ start: 1_000_000 });
    expect(c.intervalMs(1_000_000 + 5 * MIN)).toBe(2 * MIN);
    expect(c.intervalMs(1_000_000 + 11 * MIN)).toBe(10 * MIN);
  });
  it("synchro périodique due seulement quand le délai est écoulé", () => {
    const c = createCadence({ start: 0 });
    c.noteChange(100 * MIN);
    expect(c.periodicDue(101 * MIN, 100 * MIN)).toBe(false);
    expect(c.periodicDue(102 * MIN, 100 * MIN)).toBe(true);
    expect(c.periodicDue(115 * MIN, 112 * MIN)).toBe(false);
    expect(c.periodicDue(122 * MIN, 112 * MIN)).toBe(true);
  });
  it("retour sur la fenêtre : au plus une synchro par minute", () => {
    const c = createCadence({ start: 0 });
    expect(c.foregroundDue(100 * MIN, 0)).toBe(true);
    expect(c.foregroundDue(100 * MIN + 59_000, 100 * MIN)).toBe(false);
    expect(c.foregroundDue(101 * MIN, 100 * MIN)).toBe(true);
  });
});

describe("empreintes : ne pousser que ce qui a changé", () => {
  it("empreinte stable et distincte", () => {
    expect(fingerprint("abc")).toBe(fingerprint("abc"));
    expect(fingerprint("abc")).not.toBe(fingerprint("abd"));
  });
  it("contenu inchangé : rien à pousser avant le rafraîchissement de 10 min", () => {
    const g = createPushGate();
    const p = fingerprint('{"fav":[1,2]}');
    expect(g.shouldExchange("state:a", p, 0)).toBe(true);
    g.markSent("state:a", p, 0);
    expect(g.shouldExchange("state:a", p, 2 * MIN)).toBe(false);
    expect(g.shouldExchange("state:a", p, 9 * MIN)).toBe(false);
    expect(g.shouldExchange("state:a", p, 10 * MIN)).toBe(true);
  });
  it("contenu modifié : renvoyé aussitôt ; clés indépendantes ; reset", () => {
    const g = createPushGate();
    g.markSent("profiles", fingerprint("a"), 0);
    expect(g.shouldExchange("profiles", fingerprint("b"), 1000)).toBe(true);
    expect(g.shouldExchange("state:b", fingerprint("a"), 1000)).toBe(true);
    expect(g.shouldExchange("profiles", fingerprint("a"), 1000)).toBe(false);
    g.reset();
    expect(g.shouldExchange("profiles", fingerprint("a"), 1000)).toBe(true);
  });
});
