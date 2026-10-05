import { describe, expect, it } from "vitest";
import { ArrayObjectSplitter, asArray, parseTolerant, streamObjects, TruncatedError } from "./json";

describe("parseTolerant", () => {
  it("accepte un JSON valide", () => expect(parseTolerant('[{"a":1}]')).toEqual([{ a: 1 }]));
  it("tolère les caractères de contrôle dans les chaînes", () => {
    const bad = '[{"category_name":"Sport\u0007\u0001 FR\tHD"}]';
    expect(() => JSON.parse(bad)).toThrow();
    expect(parseTolerant<{ category_name: string }[]>(bad)[0]!.category_name).toBe("Sport   FR HD");
  });
  it("ignore le BOM", () => expect(parseTolerant("﻿[1]")).toEqual([1]));
});

describe("asArray", () => {
  it("false et null donnent un tableau vide", () => {
    expect(asArray(false)).toEqual([]);
    expect(asArray(null)).toEqual([]);
  });
  it("objet indexé -> valeurs", () => expect(asArray({ "1": { a: 1 }, "2": { a: 2 } })).toEqual([{ a: 1 }, { a: 2 }]));
});

describe("ArrayObjectSplitter", () => {
  const json = JSON.stringify([{ a: "x}{y", b: { c: [1, 2, { d: '"q"' }] } }, { a: "second \\ \"esc\"" }, { a: 3 }]);
  it("découpe quel que soit le fractionnement", () => {
    for (const size of [1, 2, 3, 7, 13, 1000]) {
      const sp = new ArrayObjectSplitter();
      const got: string[] = [];
      for (let i = 0; i < json.length; i += size) got.push(...sp.feed(json.slice(i, i + size)));
      expect(got.map((g) => JSON.parse(g))).toEqual(JSON.parse(json));
    }
  });
});

describe("streamObjects", () => {
  it("lit un corps en plusieurs blocs, par lots, avec contrôle de contrôle", async () => {
    const items = Array.from({ length: 2500 }, (_, i) => ({ id: i, name: `Titre é ${i}\u0003` }));
    const text = JSON.stringify(items).replace(/\\u0003/g, "\u0003");
    const bytes = new TextEncoder().encode(text);
    const body = new ReadableStream<Uint8Array>({
      start(c) { for (let i = 0; i < bytes.length; i += 4096) c.enqueue(bytes.slice(i, i + 4096)); c.close(); },
    });
    const sizes: number[] = [];
    const n = await streamObjects<{ id: number }>(new Response(body), (b) => { sizes.push(b.length); }, { batchSize: 1000 });
    expect(n).toBe(2500);
    expect(sizes).toEqual([1000, 1000, 500]);
  });
  it("renvoie 0 pour un corps non tableau", async () => {
    expect(await streamObjects(new Response("false"), () => undefined)).toBe(0);
  });
});

describe("réponse coupée", () => {
  it("signale une liste interrompue au lieu de garder le début en silence", async () => {
    const got: number[] = [];
    const body = '[{"id":1},{"id":2},{"id":3';
    await expect(streamObjects<{ id: number }>(new Response(body), (b) => { got.push(...b.map((x) => x.id)); })).rejects.toBeInstanceOf(TruncatedError);
    expect(got).toEqual([1, 2]);
  });
  it("liste complète, objet indexé ou « false » : pas d'erreur", async () => {
    expect(await streamObjects(new Response('[{"a":1}]'), () => undefined)).toBe(1);
    expect(await streamObjects(new Response('{"x":{"a":1}}'), () => undefined)).toBe(1);
    expect(await streamObjects(new Response("false"), () => undefined)).toBe(0);
  });
});
