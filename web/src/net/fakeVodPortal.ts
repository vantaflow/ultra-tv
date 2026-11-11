// Faux portail Stalker en mémoire pour les tests (fetch simulé) : films et séries paginés par 14, module `type=series`,
// catégories en erreur, jeton qui expire en route, portail sans `total_items`. Aucun réseau.

export const FAKE_API = "http://portal.test/stalker_portal/server/load.php";
export const FAKE_PORTAL = "http://portal.test/stalker_portal/c/";
export const FAKE_MAC = "00:1A:79:AB:CD:EF";
const PER_PAGE = 14;

/** Catégories : identifiant → [titre, nombre d'éléments]. */
type CatMap = Record<string, [string, number]>;

export class FakeVodPortal {
  /** Catégories du module VOD (aucune par défaut : portail Ministra récent). */
  vodCategories: CatMap = {};
  /** Catégories du module `type=series` (30 + 5 + 4 séries par défaut). */
  seriesCategories: CatMap = { "1": ["FR Séries", 30], "2": ["FR Drames", 5], "3": ["FR Docs", 4] };
  /** Catégories dont toute liste répond 500. */
  failingCategories = new Set<string>();
  /** Statut HTTP imposé à `vod/get_categories` (ex. 503). */
  vodCategoriesStatus: number | null = null;
  /** `vod/create_link` ne donne rien (liste vide) : le lecteur doit essayer `series/create_link`. */
  vodCreateLinkFails = false;
  vodCreateLinkStatus: number | null = null;
  /** Sans `total_items` ni `max_page_items` : le client lit jusqu'à une page vide. */
  omitTotal = false;
  /** Le jeton expire après ce nombre de requêtes de liste servies. */
  expireAfterListRequests: number | null = null;
  /** Une série sur N de la VOD est une série (`is_series`) ; 0 = aucune. */
  vodSeriesEvery = 0;
  /** Saisons du module séries : 0 = épisodes listés directement sous la série. */
  seasons = 2;

  handshakes = 0;
  urls: URL[] = [];
  private valid: string | null = null;
  private lists = 0;

  fetch = async (input: string | URL | Request, init?: RequestInit): Promise<Response> => {
    const u = new URL(typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url);
    this.urls.push(u);
    if (u.origin + u.pathname !== FAKE_API) return new Response("not found", { status: 404 });
    const q = u.searchParams;
    const type = q.get("type"), action = q.get("action");
    const json = (js: unknown) => new Response(JSON.stringify({ js }));
    if (type === "stb" && action === "handshake") { this.valid = `T${++this.handshakes}`; return json({ token: this.valid }); }
    // Jeton absent, périmé ou remplacé : refus classique des portails Stalker.
    if (!this.valid || new Headers(init?.headers).get("Authorization") !== `Bearer ${this.valid}`) {
      return new Response("Authorization failed.");
    }
    return this.route(type, action, q, json);
  };

  private page(all: (i: number) => unknown, count: number, q: URLSearchParams): unknown {
    const p = Number(q.get("p") ?? 1);
    const from = (p - 1) * PER_PAGE;
    const data = Array.from({ length: Math.max(0, Math.min(PER_PAGE, count - from)) }, (_, i) => all(from + i));
    return this.omitTotal ? { data } : { total_items: count, max_page_items: PER_PAGE, data };
  }

  private route(type: string | null, action: string | null, q: URLSearchParams, json: (js: unknown) => Response): Response {
    const key = `${type}/${action}`;
    const cats = (m: CatMap) => json(Object.entries(m).map(([id, [title]]) => ({ id, title })));
    switch (key) {
      case "stb/get_profile": return json({ status: 0 });
      case "itv/get_genres": return json([{ id: "*", title: "All" }, { id: "5", title: "FR | News" }]);
      case "itv/get_all_channels": return json({ data: [{ id: "1", name: "FR: Chaîne 1", cmd: "ffmpeg http://localhost/ch/1", tv_genre_id: "5" }] });
      case "vod/get_categories":
        return this.vodCategoriesStatus ? new Response("", { status: this.vodCategoriesStatus }) : cats(this.vodCategories);
      case "series/get_categories": return cats(this.seriesCategories);
      case "vod/get_ordered_list": case "series/get_ordered_list": return this.list(type!, q, json);
      case "vod/create_link": case "series/create_link": {
        if (type === "vod" && this.vodCreateLinkStatus) return new Response("", { status: this.vodCreateLinkStatus });
        if (type === "vod" && this.vodCreateLinkFails) return json([]);
        return json({ cmd: `ffmpeg http://flux.invalid/${type}/${encodeURIComponent(q.get("cmd") ?? "")}.mkv` });
      }
      default: return json([]);
    }
  }

  private list(type: string, q: URLSearchParams, json: (js: unknown) => Response): Response {
    const cat = q.get("category");
    const movie = q.get("movie_id");
    if (cat !== null && this.failingCategories.has(cat)) return new Response("boom", { status: 500 });
    this.lists++;
    if (this.expireAfterListRequests !== null && this.lists === this.expireAfterListRequests) this.valid = null;
    if (movie) return json(this.episodes(movie, q));
    if (cat === null) return json({ data: [] });
    if (type === "series") {
      const [, count] = this.seriesCategories[cat] ?? ["", 0];
      return json(this.page((i) => ({ id: `${cat}-${i}`, name: `FR Série ${cat}.${i}`, cmd: "", series: [] }), count, q));
    }
    const [, count] = this.vodCategories[cat] ?? ["", 0];
    return json(this.page((i) => {
      const id = `${cat}${String(i).padStart(6, "0")}`;
      const isSeries = this.vodSeriesEvery > 0 && i % this.vodSeriesEvery === 0;
      return isSeries ? { id, name: `FR Série ${i}`, cmd: `s/${id}`, series: [1, 2, 3] } : { id, name: `FR Film ${i} (2020)`, cmd: `m/${id}` };
    }, count, q));
  }

  /** Module séries : `season_id=0` → saisons (ou épisodes directs sans saison) ; sinon épisodes de la saison. */
  private episodes(seriesId: string, q: URLSearchParams): unknown {
    const season = q.get("season_id");
    const eps = (s: number) => Array.from({ length: 3 }, (_, i) => ({ id: `${seriesId}:${s}:${i + 1}`, name: `Épisode ${i + 1}`, series_number: i + 1, cmd: `ep/${seriesId}/${s}/${i + 1}`, is_episode: 1 }));
    if (this.seasons === 0) return { total_items: 3, max_page_items: PER_PAGE, data: eps(1) };
    if (season === "0") {
      const data = Array.from({ length: this.seasons }, (_, i) => ({ id: `${seriesId}:${i + 1}`, name: `Saison ${i + 1}`, season_number: i + 1, is_season: 1 }));
      return { total_items: data.length, max_page_items: PER_PAGE, data };
    }
    const s = Number(String(season).split(":").pop());
    return { total_items: 3, max_page_items: PER_PAGE, data: eps(s) };
  }
}
