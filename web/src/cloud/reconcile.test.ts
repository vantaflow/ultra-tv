import { describe, expect, it } from "vitest";
import { emptySource } from "@/db/sources";
import type { Source } from "@/db/types";
import type { CloudProvider } from "./client";
import { reconcile, toSourceFields } from "./reconcile";

const rp = (o: Partial<CloudProvider> = {}): CloudProvider => ({ id: "a0000001", kind: "XTREAM", name: "Abo", url: "http://h:80", username: "u", password: "p", ...o });
const ls = (o: Partial<Source> = {}): Source => ({ ...emptySource(), id: 1, name: "Abo", type: "xtream", server: "http://h:80", username: "u", password: "p", cloudId: "a0000001", cloudOrigin: "cloud", ...o });

describe("toSourceFields", () => {
  it("Xtream, M3U ; Stalker ignoré", () => {
    expect(toSourceFields(rp())).toMatchObject({ type: "xtream", server: "http://h:80", username: "u" });
    expect(toSourceFields(rp({ kind: "m3u", url: "http://x/a.m3u" }))).toMatchObject({ type: "m3u", m3uUrl: "http://x/a.m3u" });
    expect(toSourceFields(rp({ kind: "STALKER" }))).toBeNull();
  });
});

describe("reconcile", () => {
  it("ajoute un fournisseur inconnu", () => {
    const p = reconcile([], [rp()], emptySource);
    expect(p.add).toHaveLength(1);
    expect(p.add[0]).toMatchObject({ cloudId: "a0000001", cloudOrigin: "cloud", server: "http://h:80" });
  });
  it("ignore Stalker et le compte", () => {
    const p = reconcile([], [rp({ kind: "STALKER", id: "a0000002" })], emptySource);
    expect(p.add).toHaveLength(0);
    expect(p.skipped).toBe(1);
  });
  it("modifie : le nom seul ne relance pas la synchro, les identifiants oui", () => {
    const a = reconcile([ls()], [rp({ name: "Nouveau nom" })], emptySource);
    expect(a.update).toEqual([{ id: 1, patch: { name: "Nouveau nom" }, resync: false }]);
    const b = reconcile([ls()], [rp({ password: "autre" })], emptySource);
    expect(b.update[0]).toMatchObject({ patch: { password: "autre" }, resync: true });
  });
  it("retire une source cloud disparue du compte, détache une source locale partagée", () => {
    const p = reconcile([ls({ id: 1 }), ls({ id: 2, cloudId: "a0000009", cloudOrigin: "local" }), ls({ id: 3, cloudId: undefined, cloudOrigin: undefined })], [], emptySource);
    expect(p.remove).toEqual([1]);
    expect(p.detach).toEqual([2]);
  });
  it("adopte une source locale identique au lieu de la dupliquer", () => {
    const p = reconcile([ls({ id: 5, cloudId: undefined, cloudOrigin: undefined })], [rp()], emptySource);
    expect(p.add).toHaveLength(0);
    expect(p.update).toEqual([{ id: 5, patch: { cloudId: "a0000001", cloudOrigin: "local", cloudShared: undefined }, resync: false }]);
  });
  it("une source locale partagée garde ses identifiants locaux ; le partage suit le cloud", () => {
    const local = ls({ cloudOrigin: "local", password: "secret-local" });
    const p = reconcile([local], [rp({ password: "autre", sharedWith: ["d1", "d2"] })], emptySource);
    expect(p.update).toEqual([{ id: 1, patch: { cloudShared: 2 }, resync: false }]);
  });
  it("partagée avec tous", () => {
    const p = reconcile([], [rp({ sharedWith: "all" })], emptySource);
    expect(p.add[0]!.cloudShared).toBe("all");
  });
});
