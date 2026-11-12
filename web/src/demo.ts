// Données fictives pour les rendus de contrôle (web/scripts/screens.mjs). Chargé UNIQUEMENT quand le build est
// fait avec VITE_DEMO=1 : jamais dans l'application distribuée. Aucun flux, aucun identifiant réel.
import { db } from "@/db/db";
import { normText, wordKeys } from "@/lib/text";
import type { CategoryRow, ChannelRow, MovieRow, ProgramRow, SeriesRow, Source } from "@/db/types";
import { usePrefs } from "@/state/prefs";

const CATS = ["Actualités", "Sport", "Cinéma", "Jeunesse", "Documentaires", "Musique"];
const CHANNELS = ["Le Fil Info", "Chaîne Info", "Info 24", "Actu Plus", "Monde TV", "Journal TV", "Eco News", "Ciné Classic", "Sport Max", "Kids Zone", "Nature HD", "Radio Vision"];
const SHOWS = ["Journal de 20h", "Le grand débat", "Édition spéciale", "Reportage", "Magazine", "Les titres", "Bourse", "L'idiot", "Ligue 1 · Direct", "Dessins animés", "Planète bleue", "Concert"];
const MOVIES = ["Orbite basse", "Les Cerfs-volants", "Marée haute", "Le Dernier Train", "Cuisine de nuit", "Le Phare", "Sable rouge", "Nuit blanche", "Le Verger", "Terre brûlée", "Au bord du monde", "Lueur"];
const SERIES = ["Les Gardiens du phare", "Brume", "Capitale", "Les Héritiers", "Station 9", "Vagues"];

export async function seedDemo(): Promise<void> {
  if ((await db.sources.count()) > 0) return;
  const cid = 1;
  const now = Date.now();
  const id = await db.sources.add({
    name: "Démo", type: "xtream", server: "http://demo.invalid", username: "demo", password: "demo", m3uUrl: "", epgUrl: "", userAgent: "", referer: "",
    cid, langs: null, createdAt: now, lastSyncAt: now, counts: { live: 120, movie: 3040, series: 402 }, expDate: null, maxConnections: 1, state: "ready",
  } as Source);
  const cats: CategoryRow[] = CATS.map((label, i) => ({ sourceId: cid, kind: "live", extId: `c${i}`, name: label, label, badge: null, count: 20 + i * 7, enabled: 1, adult: 0, ord: i }));
  const vcats: CategoryRow[] = ["Action", "Comédie", "Familial", "Science-fiction"].map((label, i) => ({ sourceId: cid, kind: "movie", extId: `m${i}`, name: label, label, badge: null, count: 300, enabled: 1, adult: 0, ord: i }));
  const scats: CategoryRow[] = ["Drame", "Policier"].map((label, i) => ({ sourceId: cid, kind: "series", extId: `s${i}`, name: label, label, badge: null, count: 200, enabled: 1, adult: 0, ord: i }));
  await db.categories.bulkAdd([...cats, ...vcats, ...scats]);
  const chans: ChannelRow[] = CHANNELS.map((display, i) => ({
    sourceId: cid, catExt: `c${i % CATS.length}`, ord: i, streamId: 100 + i, num: i + 1, name: display, display, norm: normText(display), words: wordKeys(cid, normText(display)),
    country: "FR", q: i % 3 === 0 ? 4 : 3, flags: 0, sep: 0, logo: null, epg: `e${i}`, archive: i % 2 as 0 | 1,
  }));
  await db.channels.bulkAdd(chans);
  // Guide : programmes de 45 à 90 min autour de maintenant, pour chaque chaîne.
  const h = 3600_000;
  const base = Math.floor((now - 4 * h) / (30 * 60_000)) * 30 * 60_000;
  const progs: ProgramRow[] = [];
  CHANNELS.forEach((_, i) => {
    let t = base - (i % 3) * 20 * 60_000;
    for (let k = 0; t < now + 8 * h; k++) {
      const len = (45 + ((i + k) % 3) * 20) * 60_000;
      progs.push({ sourceId: cid, epg: `e${i}`, start: t, end: t + len, title: SHOWS[(i + k) % SHOWS.length]!, desc: "Un résumé court du programme, affiché dans l'aperçu du guide et sur la surcouche du Direct." });
      t += len;
    }
  });
  await db.programs.bulkAdd(progs);
  const movies: MovieRow[] = MOVIES.map((title, i) => ({
    sourceId: cid, catExt: `m${i % 4}`, ord: i, streamId: 1000 + i, name: title, title, norm: normText(title), words: wordKeys(cid, normText(title)), year: 2015 + (i % 10),
    poster: "http://demo.invalid/p.jpg", rating: 5 + (i % 5), ext: "mp4", added: now - i * 86400_000,
  }));
  await db.movies.bulkAdd(movies);
  const series: SeriesRow[] = SERIES.map((title, i) => ({
    sourceId: cid, catExt: `s${i % 2}`, ord: i, seriesId: 2000 + i, name: title, title, norm: normText(title), words: wordKeys(cid, normText(title)), year: 2018 + i,
    poster: "http://demo.invalid/p.jpg", backdrop: null, plot: "Sur une île battue par les vents, une gardienne découvre que le phare cache un secret.", rating: 6 + (i % 4), added: now - i * 86400_000,
  }));
  await db.series.bulkAdd(series);
  // Fiches détaillées déjà en cache (aucun appel réseau) : distribution, saisons et épisodes.
  const cast = "Mara Aubin, Yann Kerel, Léa Tanguy, Paul Guen, Sofia Ruiz, Noé Bastin";
  await db.details.bulkPut([
    { key: `vod:${id}:1000`, fetchedAt: now, json: { info: { plot: "Une ingénieure coincée dans une station en perdition a six heures pour ramener son équipage sur Terre.", cast, director: "Éli Marchand", genre: "Science-fiction", duration_secs: 7560 } } },
    { key: `series:${id}:2000`, fetchedAt: now, json: {
      info: { plot: "Sur une île battue par les vents, une gardienne découvre que le phare cache un secret.", cast, genre: "Drame" },
      seasons: [{ season_number: 1 }, { season_number: 2 }, { season_number: 3 }],
      episodes: Object.fromEntries([1, 2, 3].map((s) => [String(s), [["Le signal", "49"], ["Brouillard", "51"], ["La lanterne", "54"], ["Marée basse", "50"], ["Le registre", "53"]].map(([title, min], k) => ({
        id: s * 100 + k, title, episode_num: k + 1, container_extension: "mp4", info: { duration_secs: Number(min) * 60, plot: "Une lumière répond à celle du phare, et la tempête isole l'île pendant trois jours." },
      }))])),
    } },
  ]);
  await db.history.bulkAdd([
    { key: "main:1:movie:1001", profile: "main", sourceId: id, kind: "movie", refId: 1001, title: MOVIES[1]!, image: null, pos: 1500, dur: 6000, updatedAt: now },
    { key: "main:1:movie:1002", profile: "main", sourceId: id, kind: "movie", refId: 1002, title: MOVIES[2]!, image: null, pos: 4800, dur: 6000, updatedAt: now - 1000 },
    { key: "main:1:series:2000:1", profile: "main", sourceId: id, kind: "series", refId: 5, title: SERIES[0]!, image: null, pos: 1200, dur: 3000, updatedAt: now - 2000, seriesId: 2000, season: 2, episode: 4, epId: 5 },
  ]);
  // Évite l'assistant de première ouverture et fixe la langue du rendu.
  usePrefs.getState().set({ activeSourceId: id, lang: new URLSearchParams(location.search).get("lang") === "en" ? "en" : "fr" });
}
