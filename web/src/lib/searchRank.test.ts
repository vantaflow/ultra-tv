import { describe, expect, it } from "vitest";
import { initial, letterIndex, orderResults, relevance, titleKey } from "./searchRank";

const T = (title: string, year: number | null = null, rating: number | null = null) => ({ title, year, rating });
const titles = (xs: { title: string }[]) => xs.map((x) => x.title);

describe("searchRank", () => {
  it("titleKey ignore les articles, accents et ponctuation", () => {
    expect(titleKey("The Office")).toBe("office");
    expect(titleKey("L'Odyssée")).toBe("odyssee");
    expect(titleKey("Les Nuits Courtes")).toBe("nuits courtes");
    expect(titleKey("Das Boot")).toBe("boot");
    expect(titleKey("À bout de souffle")).toBe("a bout de souffle");
    expect(titleKey("Les")).toBe("les");
  });

  it("initiale du rail A–Z", () => {
    expect(initial("The Office")).toBe("O");
    expect(initial("été")).toBe("E");
    expect(initial("24")).toBe("#");
  });

  it("niveaux de pertinence", () => {
    expect(relevance("nuit blanche", "Nuit Blanche")).toBe(4);
    expect(relevance("office", "The Office")).toBe(4);
    expect(relevance("nuit", "Nuit Polaire")).toBe(3);
    expect(relevance("nuit", "Avant la Nuit")).toBe(2);
    expect(relevance("nuit", "Minuit Pile")).toBe(1);
  });

  it("tri par pertinence puis le plus récent", () => {
    const xs = [T("Minuit Pile", 2023), T("Avant la Nuit", 2014), T("Nuit Polaire", 2020), T("Nuit", 2001), T("Nuit Blanche", 2021)];
    expect(titles(orderResults(xs, "nuit", "relevance"))).toEqual(["Nuit", "Nuit Blanche", "Nuit Polaire", "Avant la Nuit", "Minuit Pile"]);
  });

  it("tri A–Z sans article, chiffres d'abord", () => {
    const xs = [T("The Office"), T("Les Nuits Courtes"), T("24"), T("Avant la Nuit"), T("Ébène"), T("Minuit Pile")];
    expect(titles(orderResults(xs, "", "title"))).toEqual(["24", "Avant la Nuit", "Ébène", "Minuit Pile", "Les Nuits Courtes", "The Office"]);
  });

  it("tri par année et par note (notes invalides en fin)", () => {
    const xs = [T("B", 2010, 7), T("A", null, 0), T("C", 2020, 11), T("D", 2015, 8.5)];
    expect(titles(orderResults(xs, "", "year"))).toEqual(["C", "D", "B", "A"]);
    expect(titles(orderResults(xs, "", "rating"))).toEqual(["D", "B", "A", "C"]);
  });

  it("index des lettres", () => {
    const idx = letterIndex([T("24"), T("Avant"), T("Avril"), T("The Office"), T("Orage")]);
    expect([...idx.entries()]).toEqual([["#", 0], ["A", 1], ["O", 3]]);
  });
});
