import { describe, expect, it } from "vitest";
import { decodeXml, parseXmltvTime, scanXmltv } from "./epg";

describe("xmltv", () => {
  it("décalage horaire", () => {
    expect(parseXmltvTime("20260704203000 +0200")).toBe(Date.UTC(2026, 6, 4, 18, 30));
    expect(parseXmltvTime("20260704203000")).toBe(Date.UTC(2026, 6, 4, 20, 30));
    expect(parseXmltvTime("n'importe quoi")).toBe(0);
  });
  it("entités", () => expect(decodeXml("Tom &amp; Jerry &#233; &lt;3 <![CDATA[a&b]]>")).toBe("Tom & Jerry é <3 a&b"));
  it("extrait seulement les chaînes voulues dans la fenêtre, quel que soit le découpage", () => {
    const doc = `<tv><channel id="a"><display-name>${"x".repeat(100000)}</display-name></channel>
<programme start="20260704200000 +0000" stop="20260704220000 +0000" channel="a"><title lang="fr">Match &amp; débat</title><desc>Résumé</desc></programme>
<programme start="20260704220000 +0000" stop="20260704230000 +0000" channel="zz"><title>Autre chaîne</title></programme>
<programme start="20250101000000 +0000" stop="20250101010000 +0000" channel="a"><title>Trop vieux</title></programme>
<programme start="20260704230000 +0000" stop="20260705000000 +0000" channel="a"><title>Suite</title></programme></tv>`;
    for (const size of [50, 777, 1 << 20]) {
      const got: unknown[] = [];
      const s = scanXmltv(new Set(["a"]), Date.UTC(2026, 6, 4, 19), Date.UTC(2026, 6, 6), (r) => got.push(...r));
      for (let i = 0; i < doc.length; i += size) s.push(doc.slice(i, i + size));
      s.end();
      expect(got).toEqual([
        { epg: "a", start: Date.UTC(2026, 6, 4, 20), end: Date.UTC(2026, 6, 4, 22), title: "Match & débat", desc: "Résumé" },
        { epg: "a", start: Date.UTC(2026, 6, 4, 23), end: Date.UTC(2026, 6, 5, 0), title: "Suite", desc: "" },
      ]);
    }
  });

  it("compare les identifiants sans casse et écrit celui du catalogue (régression : guide vide pour TF1.fr / tf1.fr)", () => {
    const got: { epg: string }[] = [];
    const s = scanXmltv(new Set(["tf1.fr"]), 0, Date.UTC(2100, 0, 1), (r) => got.push(...r));
    s.push('<programme start="20260704200000 +0000" stop="20260704220000 +0000" channel="TF1.fr"><title>JT</title></programme>');
    s.end();
    expect(got.map((r) => r.epg)).toEqual(["tf1.fr"]);
  });
  it("une ligne par variante de casse présente dans le catalogue", () => {
    const got: { epg: string }[] = [];
    const s = scanXmltv(new Set(["tf1.fr", "TF1.fr"]), 0, Date.UTC(2100, 0, 1), (r) => got.push(...r));
    s.push('<programme start="20260704200000 +0000" stop="20260704220000 +0000" channel="TF1.fr"><title>JT</title></programme>');
    s.end();
    expect(got.map((r) => r.epg).sort()).toEqual(["TF1.fr", "tf1.fr"]);
  });
});
