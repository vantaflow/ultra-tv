import { useEffect, useState } from "react";
import { Account } from "@/screens/Account";
import { HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { dirOf, translate, useT } from "@/i18n";
import { catalogNeedsRebuild, claimRebuild } from "@/sync/rebuild";
import { initTransport, bridge, isElectron } from "@/net/transport";
import { normalizeDns, pushDns } from "@/net/dns";
import { PlayerHost } from "@/player/PlayerHost";
import { ErrorBoundary } from "@/ui/ErrorBoundary";
import { Rail } from "@/ui/Rail";
import { Toasts } from "@/ui/common";
import { SyncPill } from "@/ui/SyncPill";
import { resolveTheme, resolveThemeAttr, usePrefs } from "@/state/prefs";
import { accentVars } from "@/lib/themes";
import { startSourcesWatcher, useActiveSource, useSources } from "@/state/sources";
import { isStaleSyncing, needsFirstSync } from "@/lib/syncPolicy";
import { useSync } from "@/state/sync";
import { useUi } from "@/state/ui";
import { Home } from "@/screens/Home";
import { Live } from "@/screens/Live";
import { Guide } from "@/screens/Guide";
import { VodScreen } from "@/screens/Vod";
import { MovieDetail, SeriesDetail } from "@/screens/Detail";
import { Person } from "@/screens/Person";
import { Search } from "@/screens/Search";
import { Favorites } from "@/screens/Favorites";
import { Settings } from "@/screens/Settings";
import { Sport } from "@/screens/Sport";
import { Recordings } from "@/screens/Recordings";
import { ReminderBanners } from "@/ui/ReminderBanners";
import { pushCloseLabels, startScheduler } from "@/state/schedule";
import { Onboarding } from "@/screens/Onboarding";
import { Profiles } from "@/screens/Profiles";
import { loadCloud, startCloudSchedule } from "@/cloud/service";
import { startAnonPing } from "@/cloud/anon";
import { cloudAvailable } from "@/cloud/client";
import { guideColRem } from "@/lib/guideCol";
import { useTraktPrewarm, useTraktResumeImport, useTraktSync } from "@/trakt/hooks";

function Effects() {
  const { theme, accent, lang, syncOn, animations, channelLogos, guideCol } = usePrefs();
  const nav = useNavigate();
  const loc = useLocation();
  const { ready, list } = useSources();
  const active = useActiveSource();
  useTraktSync(active?.id);
  useTraktPrewarm(active);
  useTraktResumeImport(active);

  useEffect(() => {
    const apply = () => {
      const r = document.documentElement;
      const mode = resolveThemeAttr(theme);
      r.dataset.theme = mode;
      // Accent par défaut (rouge) : on laisse les jetons du thème ; sinon couleur choisie + texte lisible (≥ 4,5:1) dessus et en texte.
      const av = accentVars(accent, mode);
      if (!av) { for (const k of ["--accent", "--accent-text", "--on-accent"]) r.style.removeProperty(k); }
      else { r.style.setProperty("--accent", av.accent); r.style.setProperty("--accent-text", av.accentText); r.style.setProperty("--on-accent", av.onAccent); }
      r.lang = lang;
      r.dir = dirOf(lang);
      // Animations : « off » coupe transitions et défilement de « À la une », « subtle » les raccourcit.
      r.dataset.anim = animations === "auto" ? (matchMedia("(prefers-reduced-motion: reduce)").matches ? "off" : "normal") : animations;
      r.dataset.logos = channelLogos ? "on" : "off";
      r.style.setProperty("--g-col", `${guideColRem(guideCol)}rem`);
      void bridge()?.setTitleBarTheme?.(resolveTheme(theme) === "dark");
    };
    apply();
    const mq = matchMedia("(prefers-color-scheme: light)");
    mq.addEventListener("change", apply);
    return () => mq.removeEventListener("change", apply);
  }, [theme, accent, lang, animations, channelLogos, guideCol]);

  useEffect(() => {
    document.documentElement.classList.toggle("mac", bridge()?.platform === "darwin");
    startSourcesWatcher();
    let stopCloud: (() => void) | undefined;
    let stopAnon: (() => void) | undefined;
    void loadCloud().then(() => { if (cloudAvailable()) { stopCloud = startCloudSchedule(); stopAnon = startAnonPing(); } });
    void initTransport();
    // Rappels et enregistrements : reprogrammés au lancement (persistés dans Dexie).
    const stopSchedule = startScheduler();
    // DNS personnalisé : le réglage local fait foi, le processus principal est réaligné au démarrage.
    if (isElectron()) void pushDns(normalizeDns(usePrefs.getState().dns));
    const on = () => useUi.getState().setOnline(true);
    const off = () => useUi.getState().setOnline(false);
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    const unsub = bridge()?.onUpdateStatus?.((s) => useUi.getState().setUpdate(s));
    return () => {
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
      if (typeof unsub === "function") unsub();
      stopCloud?.();
      stopAnon?.();
      stopSchedule();
    };
  }, []);

  // Confirmation de fermeture pendant un enregistrement : textes dans la langue de l'interface.
  useEffect(() => { pushCloseLabels(); }, [lang]);

  // Raccourcis globaux : « / » ou Ctrl/⌘ + K ouvre la recherche.
  useEffect(() => {
    const k = (e: KeyboardEvent) => {
      const el = e.target as HTMLElement | null;
      const typing = !!el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA" || el.isContentEditable);
      if ((e.key === "k" || e.key === "K") && (e.metaKey || e.ctrlKey)) { e.preventDefault(); nav("/search"); }
      else if (e.key === "/" && !typing && !e.metaKey && !e.ctrlKey) { e.preventDefault(); nav("/search"); }
    };
    window.addEventListener("keydown", k);
    return () => window.removeEventListener("keydown", k);
  }, [nav]);

  // Première ouverture : aucune source -> accueil de configuration.
  useEffect(() => {
    if (ready && list.length === 0 && !loc.pathname.startsWith("/welcome")) nav("/welcome", { replace: true });
  }, [ready, list.length, loc.pathname, nav]);

  // Récupération : source déjà synchronisée mais catalogue local vide (catégories perdues après une mise à jour).
  // Une seule tentative par démarrage et par source : une source réellement vide ne provoque aucune boucle.
  useEffect(() => {
    if (!ready || !active || active.id == null || useSync.getState().running) return;
    const src = active;
    let cancelled = false;
    void (async () => {
      if (!(await catalogNeedsRebuild(src)) || cancelled || !claimRebuild(src.id!)) return;
      const ok = await useSync.getState().start(src, { preserveFlags: true, silent: true });
      if (ok) useUi.getState().toast(translate(usePrefs.getState().lang, "sync.rebuilt"));
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, active?.id]);

  // Mise à jour silencieuse au lancement : l'ancien catalogue reste visible jusqu'à la bascule de génération.
  useEffect(() => {
    if (!ready || !active || syncOn !== "launch" || active.state !== "ready") return;
    if (Date.now() - active.lastSyncAt < 12 * 3600_000) return;
    void useSync.getState().start(active, { preserveFlags: true, silent: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, active?.id]);

  // Synchro interrompue (application fermée en cours de route) : au lancement rien ne tourne, un état « syncing » est donc périmé.
  // Sans reprise, le direct (écrit en premier) serait utilisable mais Films/Séries resteraient vides pour toujours.
  useEffect(() => {
    if (!ready || !active || !isStaleSyncing(active) || useSync.getState().running) return;
    void useSync.getState().start(active, { preserveFlags: true, silent: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, active?.id]);

  // Source jamais synchronisée hors assistant (ex. M3U get.php convertie en Xtream au démarrage) : première synchro sans action.
  useEffect(() => {
    if (!ready || !active || !needsFirstSync(active) || loc.pathname.startsWith("/welcome")) return;
    // Garde les catégories masquées/affichées à la main s'il existe déjà un catalogue (identifiants du fournisseur).
    void useSync.getState().start(active, { preserveFlags: true, silent: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, active?.id, active?.state, loc.pathname]);

  return null;
}

/** Mise à jour téléchargée : proposée tout de suite (sinon installée à la fermeture de l'application). */
function UpdateReady() {
  const t = useT();
  const u = useUi((s) => s.update);
  const [hidden, setHidden] = useState<string | null>(null);
  const b = bridge();
  if (u?.state !== "downloaded" || !b?.installUpdate || hidden === (u.version ?? "")) return null;
  return (
    <div className="update-ready" role="status">
      <div><strong>{t("state.update", { v: u.version ?? "" })}</strong><div className="sub">{t("state.updateLater")}</div></div>
      <button className="btn primary sm" onClick={() => void b.installUpdate!()}>{t("state.install")}</button>
      <button className="btn sm" onClick={() => setHidden(u.version ?? "")}>{t("state.later")}</button>
    </div>
  );
}

/** Installation par paquet (.deb / .rpm) : pas d'installation automatique, on propose le téléchargement du nouveau paquet. */
function UpdateManual() {
  const t = useT();
  const u = useUi((s) => s.update);
  const [hidden, setHidden] = useState<string | null>(null);
  const b = bridge();
  const url = u?.url ?? "";
  if (u?.state !== "manual" || !b?.openExternal || !/^https:\/\//.test(url) || hidden === (u.version ?? "")) return null;
  return (
    <div className="update-ready" role="status">
      <div><strong>{t("state.updateManual", { v: u.version ?? "" })}</strong><div className="sub">{t("state.updateManualBody")}</div></div>
      <button className="btn primary sm" onClick={() => void b.openExternal!(url)}>{t("state.download")}</button>
      <button className="btn sm" onClick={() => setHidden(u.version ?? "")}>{t("state.later")}</button>
    </div>
  );
}

function Shell() {
  const loc = useLocation();
  const { ready } = useSources();
  const bare = loc.pathname.startsWith("/welcome") || loc.pathname.startsWith("/profiles");
  if (!ready) return <div className="app no-rail" />;
  return (
    <div className={`app${bare ? " no-rail" : ""}`}>
      {!bare && <Rail />}
      <main className="content">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/live" element={<Live />} />
          <Route path="/guide" element={<Guide />} />
          <Route path="/sport" element={<Sport />} />
          <Route path="/recordings" element={<Recordings />} />
          <Route path="/movies" element={<VodScreen kind="movie" />} />
          <Route path="/movie/:id" element={<MovieDetail />} />
          <Route path="/series" element={<VodScreen kind="series" />} />
          <Route path="/serie/:id" element={<SeriesDetail />} />
          <Route path="/person/:name" element={<Person />} />
          <Route path="/search" element={<Search />} />
          <Route path="/favorites" element={<Favorites />} />
          <Route path="/account" element={<Account />} />
          <Route path="/settings/:section?" element={<Settings />} />
          <Route path="/welcome/*" element={<Onboarding />} />
          <Route path="/profiles" element={<Profiles />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      {!bare && <SyncPill />}
      <Toasts />
      <ReminderBanners />
      <UpdateReady />
      <UpdateManual />
    </div>
  );
}

export function App() {
  return (
    <ErrorBoundary>
      <HashRouter>
        <Effects />
        <Shell />
        <PlayerHost />
      </HashRouter>
    </ErrorBoundary>
  );
}
