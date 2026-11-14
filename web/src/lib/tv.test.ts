// Plateforme TV : détection, transposition des touches et polyfills.
import { describe, expect, it } from "vitest";
import { ensureTvCompat, isTyping, registerTizenKeys, TIZEN_KEYS, tvKey, tvKindOf } from "./tv";

describe("tvKindOf", () => {
  it("reconnaît Tizen (Samsung) et webOS (LG, Web0S avec un zéro)", () => {
    expect(tvKindOf("Mozilla/5.0 (SMART-TV; Linux; Tizen 5.5) AppleWebKit/537.36 (KHTML, like Gecko) Version/2.5 TV Safari/537.36")).toBe("tizen");
    expect(tvKindOf("Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.79 Safari/537.36 WebAppManager")).toBe("webos");
  });

  it("reconnaît les anciennes chaînes d'agent (Maple, SmartTV générique)", () => {
    expect(tvKindOf("Mozilla/5.0 (SmartHub; SMART-TV; Maples)")).toBe("tizen");
    expect(tvKindOf("Mozilla/5.0 (SMART-TV; Linux) ...")).toBe("tizen");
  });

  it("ignore navigateur et bureau (Electron, mobile)", () => {
    expect(tvKindOf("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/141.0.0.0 Safari/537.36")).toBeNull();
    expect(tvKindOf("Mozilla/5.0 (Linux; Android 14; BR25) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")).toBeNull();
    expect(tvKindOf("")).toBeNull();
  });
});

describe("tvKey", () => {
  it("touche Retour : Tizen 10009, webOS 461, doublon webOS en Espace arrière", () => {
    expect(tvKey({ keyCode: 10009 }, false)).toBe("Escape");
    expect(tvKey({ keyCode: 461 }, false)).toBe("Escape");
    expect(tvKey({ keyCode: 8 }, false)).toBe("Escape");
    // Pendant une saisie (clavier virtuel), le retour arrière reste un retour arrière.
    expect(tvKey({ keyCode: 8 }, true)).toBeNull();
  });

  it("Retour livré via `key` sur certains moteurs", () => {
    expect(tvKey({ key: "XF86Back" }, false)).toBe("Escape");
    expect(tvKey({ key: "GoBack" }, false)).toBe("Escape");
    expect(tvKey({ key: "BrowserBack" }, false)).toBe("Escape");
  });

  it("touches média et chaînes : même transposition sur les deux plateformes", () => {
    expect(tvKey({ keyCode: 412 }, false)).toBe("ArrowLeft"); // rembobinage
    expect(tvKey({ keyCode: 417 }, false)).toBe("ArrowRight"); // avance
    expect(tvKey({ keyCode: 415 }, false)).toBe(" "); // lecture
    expect(tvKey({ keyCode: 10252 }, false)).toBe(" "); // lecture / pause
    expect(tvKey({ keyCode: 413 }, false)).toBe("Escape"); // stop
    expect(tvKey({ keyCode: 427 }, false)).toBe("ArrowUp"); // chaîne +
    expect(tvKey({ keyCode: 428 }, false)).toBe("ArrowDown"); // chaîne −
  });

  it("Exit Tizen → sortie d'application", () => {
    expect(tvKey({ keyCode: 10182 }, false)).toBe("exit");
  });

  it("touches inconnues : rien (les flèches et Entrée arrivent déjà en clavier standard)", () => {
    expect(tvKey({ keyCode: 13 }, false)).toBeNull();
    expect(tvKey({ keyCode: 38 }, false)).toBeNull();
    expect(tvKey({ keyCode: 403 }, false)).toBeNull(); // touche rouge
    expect(tvKey({}, false)).toBeNull();
  });
});

describe("isTyping", () => {
  const tgt = (x: unknown) => x as unknown as EventTarget;
  it("reconnaît les champs de saisie par tagName (compatible environnement sans DOM)", () => {
    expect(isTyping(tgt({ tagName: "input" }))).toBe(true);
    expect(isTyping(tgt({ tagName: "TEXTAREA" }))).toBe(true);
    expect(isTyping(tgt({ tagName: "div" }))).toBe(false);
    expect(isTyping(tgt({ tagName: "div", isContentEditable: true }))).toBe(true);
    expect(isTyping(tgt({}))).toBe(false);
    expect(isTyping(null)).toBe(false);
  });
});

describe("ensureTvCompat", () => {
  it("crypto.randomUUID disponible après l'appel", () => {
    ensureTvCompat();
    const id = globalThis.crypto.randomUUID();
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });
});

describe("registerTizenKeys", () => {
  it("enregistre les touches média auprès de Tizen et ignore celles absentes du modèle", () => {
    const seen: string[] = [];
    const g = { tizen: { tvinputdevice: { registerKey: (k: string) => { if (k === "Exit") throw new Error("absente"); seen.push(k); } } } };
    expect(registerTizenKeys(g)).toBe(TIZEN_KEYS.length - 1);
    expect(seen).toContain("MediaPlayPause");
    expect(seen).toContain("ChannelUp");
  });

  it("ne fait rien hors Tizen", () => {
    expect(registerTizenKeys({})).toBe(0);
  });

  it("Pause seule (19) bascule lecture/pause", () => {
    expect(tvKey({ keyCode: 19 }, false)).toBe(" ");
  });
});
