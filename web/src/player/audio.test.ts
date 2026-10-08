import { describe, expect, it } from "vitest";
import { AudioWatcher, nextAudioFallback, revertToSilent, type AudioSample } from "./audio";

const s = (time: number, o: Partial<AudioSample> = {}): AudioSample => ({ time, paused: false, decodedBytes: 0, ...o });

describe("AudioWatcher", () => {
  it("octets audio décodés : ok immédiatement", () => {
    expect(new AudioWatcher().tick(s(0, { decodedBytes: 4096 }))).toBe("ok");
  });
  it("aucun octet décodé après 3 s de lecture : silent", () => {
    const w = new AudioWatcher();
    const out = [0, 1, 2, 3, 4].map((t) => w.tick(s(t)));
    expect(out).toEqual(["wait", "wait", "wait", "silent", "silent"]);
  });
  it("API absente : on n'interfère pas", () => {
    expect(new AudioWatcher().tick({ time: 5, paused: false })).toBe("ok");
  });
  it("en pause ou à l'arrêt : le temps ne compte pas", () => {
    const w = new AudioWatcher();
    for (let i = 0; i < 6; i++) expect(w.tick(s(10, { paused: true }))).toBe("wait");
  });
  it("un saut de position n'est pas du temps joué", () => {
    const w = new AudioWatcher();
    w.tick(s(0)); w.tick(s(1)); // 1 s
    expect(w.tick(s(600))).toBe("wait"); // seek
    expect(w.tick(s(601))).toBe("wait"); // 2 s
    expect(w.tick(s(602))).toBe("silent"); // 3 s cumulées malgré le saut
  });
  it("mozHasAudio vrai : ok ; piste audio absente (0) : silent", () => {
    expect(new AudioWatcher().tick({ time: 0, paused: false, mozHasAudio: true })).toBe("ok");
    const w = new AudioWatcher();
    const r = [0, 1, 2, 3].map((t) => w.tick({ time: t, paused: false, audioTrackCount: 0 }));
    expect(r[3]).toBe("silent");
  });
  it("sonde abandonnée au bout de 20 ticks sans progression", () => {
    const w = new AudioWatcher();
    let v = "wait";
    for (let i = 0; i < 20; i++) v = w.tick(s(5, { paused: true }));
    expect(v).toBe("ok");
  });
});

describe("repli audio", () => {
  it("candidat suivant, ou -1 en fin de liste", () => {
    expect(nextAudioFallback(3, 0)).toBe(1);
    expect(nextAudioFallback(3, 2)).toBe(-1);
  });
  it("retour au candidat sans son si les replis échouent", () => {
    expect(revertToSilent(0, 3)).toBe(0);
    expect(revertToSilent(null, 3)).toBe(-1);
    expect(revertToSilent(5, 3)).toBe(-1);
  });
});
