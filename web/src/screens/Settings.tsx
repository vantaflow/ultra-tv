import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { db } from "@/db/db";
import { deleteSource, saveSource } from "@/db/sources";
import { categoriesOf } from "@/db/queries";
import type { CategoryRow, Kind, Source } from "@/db/types";
import { LANGS, useT, type Lang } from "@/i18n";
import { normText } from "@/lib/text";
import { bridge, defaultProxy, getProxySetting, isElectron, setProxySetting } from "@/net/transport";
import { ACCENTS, PROFILE_COLORS, usePrefs, type Theme } from "@/state/prefs";
import { useActiveSource, useSources } from "@/state/sources";
import { useSync } from "@/state/sync";
import { useUi } from "@/state/ui";
import { syncSourceEpg, testSource } from "@/sync/client";
import { setCategoriesEnabled } from "@/db/categoryActions";
import { Modal, Seg, Switch } from "@/ui/common";
import { Icon, type IconName } from "@/ui/Icon";
import { VList, arrayRows } from "@/ui/Virtual";
import { errorKey } from "./Onboarding";
import { convertToXtream } from "@/lib/xtreamUrl";
import { cloudAvailable } from "@/cloud/client";
import { notePrefsChanged, saveDeviceName, saveWorker, setPrefsSync, shareSource, syncCloud, unpair, unshareSource, useCloud } from "@/cloud/service";
import { SourceForm, type SourceKind } from "./SourceForm";

type Section = "sources" | "cloud" | "display" | "playback" | "sync" | "categories" | "language" | "profiles" | "about";
const SECTIONS: { id: Section; icon: IconName; key: "set.sources" }[] = [
  { id: "sources", icon: "source", key: "set.sources" },
  { id: "cloud", icon: "globe", key: "set.sources" },
  { id: "display", icon: "settings", key: "set.sources" },
  { id: "playback", icon: "play", key: "set.sources" },
  { id: "sync", icon: "refresh", key: "set.sources" },
  { id: "categories", icon: "list", key: "set.sources" },
  { id: "language", icon: "globe", key: "set.sources" },
  { id: "profiles", icon: "user", key: "set.sources" },
  { id: "about", icon: "info", key: "set.sources" },
];

export function Settings() {
  const t = useT();
  const nav = useNavigate();
  const { section } = useParams();
  const cur = (SECTIONS.find((s) => s.id === section)?.id ?? "sources") as Section;
  const label = (id: Section) => t(({ sources: "set.sources", cloud: "set.cloud", display: "set.display", playback: "set.playback", sync: "set.sync", categories: "set.categories", language: "set.language", profiles: "set.profiles", about: "set.about" } as const)[id]);
  return (
    <div className="settings">
      <nav className="nav" aria-label={t("set.title")}>
        <h1>{t("set.title")}</h1>
        {SECTIONS.map((s) => (
          <button key={s.id} className={`nav-item${cur === s.id ? " on" : ""}`} onClick={() => nav(`/settings/${s.id}`)}>
            <Icon name={s.icon} size={22} />{label(s.id)}
          </button>
        ))}
      </nav>
      <div className="pane">
        {cur === "sources" && <SourcesPane />}
        {cur === "cloud" && <CloudPane />}
        {cur === "display" && <DisplayPane />}
        {cur === "playback" && <PlaybackPane />}
        {cur === "sync" && <SyncPane />}
        {cur === "categories" && <CategoriesPane />}
        {cur === "language" && <LanguagePane />}
        {cur === "profiles" && <ProfilesPane />}
        {cur === "about" && <AboutPane />}
      </div>
    </div>
  );
}

function Pref({ label, desc, children }: { label: string; desc?: string; children: React.ReactNode }) {
  return <div className="pref"><div><div>{label}</div>{desc && <div className="d">{desc}</div>}</div>{children}</div>;
}

// ---------------- Sources ----------------
function SourcesPane() {
  const t = useT();
  const nav = useNavigate();
  const list = useSources((s) => s.list);
  const prefs = usePrefs();
  const sync = useSync();
  const [edit, setEdit] = useState<Source | null>(null);
  const [share, setShare] = useState<Source | null>(null);
  const cloud = useCloud();
  const active = useActiveSource();
  return (
    <>
      <h2>{t("set.sources")}</h2>
      <p className="lead">{t("set.sourcesLead")}</p>
      {list.map((s) => {
        const isActive = active?.id === s.id;
        return (
          <div key={s.id} className={`src-card${isActive ? " active" : ""}`}>
            <span className="ico"><Icon name={s.type === "xtream" ? "source" : "list"} size={26} /></span>
            <span className="grow">
              <b className="ellipsis" style={{ display: "block" }}>{s.name}</b>
              <div className="meta">{t("set.sourceMeta", { type: s.type === "xtream" ? "Xtream Codes" : "M3U", n: s.counts.live.toLocaleString(), m: s.counts.movie.toLocaleString(), s: s.counts.series.toLocaleString() })}{isActive ? ` · ${t("set.active")}` : ""}</div>
              {(s.cloudId || cloud.paired) && <div className="meta" style={{ fontWeight: 700 }}>{cloudBadge(s, t)}</div>}
            </span>
            {!isActive && <button className="btn sm" onClick={() => prefs.set({ activeSourceId: s.id! })}>{t("set.makeActive")}</button>}
            {cloud.paired && cloud.pushAvailable !== false && s.cloudOrigin !== "cloud" && !(s.type === "m3u" && s.m3uUrl.startsWith("file:")) && (
              <button className="btn sm" onClick={() => setShare(s)}><Icon name="globe" size={14} />{t("cloud.share")}</button>
            )}
            {s.cloudOrigin === "local" && <button className="btn sm" onClick={async () => { if (await unshareSource(s)) useUi.getState().toast(t("cloud.unshare")); }}>{t("cloud.unshare")}</button>}
            <button className="btn sm" onClick={() => setEdit(s)}>{t("common.edit")}</button>
            <button className="btn sm danger" disabled={sync.running} onClick={async () => { if (confirm(t("set.confirmDelete", { n: s.name }))) await deleteSource(s.id!); }} aria-label={t("common.delete")}><Icon name="trash" size={16} /></button>
          </div>
        );
      })}
      <button className="add-card" onClick={() => nav("/welcome?add=1")}><Icon name="plus" size={24} stroke={2.5} />{t("set.addSource")}</button>
      {edit && <EditSource source={edit} onClose={() => setEdit(null)} />}
      {share && <ShareModal source={share} onClose={() => setShare(null)} />}
    </>
  );
}

function cloudBadge(s: Source, t: ReturnType<typeof useT>): string {
  if (!s.cloudId) return t("cloud.badgeLocal");
  if (s.cloudOrigin === "cloud" && s.cloudOriginName && s.cloudShared === undefined) return t("cloud.badgeFrom", { n: s.cloudOriginName });
  if (s.cloudShared === "all") return t("cloud.badgeAll");
  if (typeof s.cloudShared === "number") return t("cloud.badgeShared", { n: s.cloudShared });
  return s.cloudOriginName ? t("cloud.badgeFrom", { n: s.cloudOriginName }) : t("cloud.badgeCloud");
}

function ShareModal({ source, onClose }: { source: Source; onClose: () => void }) {
  const t = useT();
  const { devices, deviceId } = useCloud();
  const list = Array.isArray(devices) ? devices : [];
  const [all, setAll] = useState(true);
  const [sel, setSel] = useState<Set<string>>(new Set(list.map((d) => d.id)));
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    setBusy(true);
    const r = await shareSource(source, all || list.length === 0 ? "all" : [...sel]);
    setBusy(false);
    if (r.ok) { useUi.getState().toast(t("cloud.shareDone")); onClose(); }
    else useUi.getState().toast(r.reason === "limit" ? t("cloud.shareLimit") : r.reason === "unsupported" ? t("cloud.shareFile") : t("cloud.shareFail"));
    if (!r.ok && r.reason === "unavailable") onClose();
  };
  return (
    <Modal title={t("cloud.shareTitle", { n: source.name })} onClose={onClose} foot={
      <>
        <button className="btn" onClick={onClose}>{t("common.cancel")}</button>
        <button className="btn primary" disabled={busy || (!all && sel.size === 0)} onClick={() => void submit()}>{t("cloud.share")}</button>
      </>}>
      <div className="seg" role="radiogroup" style={{ alignSelf: "flex-start" }}>
        <button role="radio" aria-checked={all} onClick={() => setAll(true)}>{t("cloud.shareAll")}</button>
        <button role="radio" aria-checked={!all} disabled={list.length === 0} onClick={() => setAll(false)}>{t("cloud.shareChoose")}</button>
      </div>
      {!all && list.map((d) => (
        <label key={d.id} className="pref" style={{ cursor: "pointer" }}>
          <span>{d.name}{d.isCurrent || d.id === deviceId ? ` (${t("cloud.thisDevice")})` : ""}</span>
          <input type="checkbox" checked={sel.has(d.id)} onChange={(e) => setSel((c) => { const n = new Set(c); if (e.target.checked) n.add(d.id); else n.delete(d.id); return n; })} />
        </label>
      ))}
      <p className="muted" style={{ fontSize: "0.8125rem" }}>{t("cloud.shareNote")}</p>
    </Modal>
  );
}

function CloudPane() {
  const t = useT();
  const nav = useNavigate();
  const c = useCloud();
  const lang = usePrefs((s) => s.lang);
  const [name, setName] = useState(c.deviceName);
  const [worker, setWorker] = useState(c.worker);
  const [msg, setMsg] = useState<string | null>(null);
  useEffect(() => { setName(c.deviceName); setWorker(c.worker); }, [c.deviceName, c.worker]);
  if (!cloudAvailable()) return <><h2>{t("set.cloud")}</h2><p className="lead">{t("cloud.lead")}</p></>;
  return (
    <>
      <h2>{t("set.cloud")}</h2>
      <p className="lead">{t("cloud.lead")}</p>
      {c.error === "token-rejected" && <div className="alert err" role="alert"><Icon name="alert" size={20} />{t("cloud.tokenRejected")}</div>}
      {!c.paired ? (
        <div><button className="btn primary" onClick={() => nav("/welcome?add=1&cloud=1")}><Icon name="globe" size={18} />{t("cloud.pairNow")}</button></div>
      ) : (
        <>
          <Pref label={t("cloud.deviceName")}>
            <input className="input" style={{ width: "18rem" }} value={name} aria-label={t("cloud.deviceName")} onChange={(e) => setName(e.target.value)} onBlur={() => void saveDeviceName(name)} />
          </Pref>
          <Pref label={t("cloud.worker")}>
            <input className="input" style={{ width: "26rem" }} value={worker} aria-label={t("cloud.worker")} spellCheck={false}
              onChange={(e) => setWorker(e.target.value)} onBlur={async () => { if (!(await saveWorker(worker))) setWorker(c.worker); }} />
          </Pref>
          <Pref label={t("cloud.prefsSync")} desc={t("cloud.prefsSyncDesc")}>
            <Switch on={c.prefsSync} label={t("cloud.prefsSync")} onChange={(v) => void setPrefsSync(v)} />
          </Pref>
          <Pref label={t("cloud.lastSync", { t: c.lastSyncAt ? new Date(c.lastSyncAt).toLocaleString(lang) : t("set.never") })}>
            <button className="btn primary" disabled={c.syncing} onClick={async () => {
              setMsg(null);
              try {
                const r = await syncCloud({ force: true });
                setMsg(r.unchanged ? t("cloud.upToDate") : t("cloud.syncDone", { a: r.added, u: r.updated, r: r.removed }));
              } catch { setMsg(t("cloud.failed")); }
            }}><Icon name="refresh" size={18} />{c.syncing ? t("set.syncing") : t("cloud.syncNow")}</button>
          </Pref>
          {msg && <div className="alert" role="status">{msg}</div>}
          <div className="eyebrow">{Array.isArray(c.devices) ? t("cloud.devices") : t("cloud.devicesCount", { n: c.devices })}</div>
          {Array.isArray(c.devices) && c.devices.map((d) => (
            <div key={d.id} className="pref">
              <span><span>{d.name}</span>{(d.model || d.lastSeen !== undefined) && <span className="d" style={{ display: "block" }}>{[d.model, new Date(d.lastSeen || 0).getTime() > 0 ? new Date(d.lastSeen!).toLocaleString(lang) : "—"].filter(Boolean).join(" · ")}</span>}</span>
              {(d.isCurrent || d.id === c.deviceId) && <span className="cbadge">{t("cloud.thisDevice")}</span>}
            </div>
          ))}
          <div><button className="btn danger" onClick={async () => { if (confirm(t("cloud.unpairConfirm"))) await unpair(); }}>{t("cloud.unpair")}</button></div>
        </>
      )}
    </>
  );
}

function EditSource({ source, onClose }: { source: Source; onClose: () => void }) {
  const t = useT();
  const [s, setS] = useState(source);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const kind: SourceKind = s.type === "xtream" ? "xtream" : s.m3uUrl.startsWith("file:") ? "m3u-file" : "m3u-link";
  return (
    <Modal wide title={t("common.edit")} onClose={onClose} foot={
      <>
        <button className="btn" onClick={onClose}>{t("common.cancel")}</button>
        <button className="btn primary" disabled={busy} onClick={async () => {
          setBusy(true); setErr(null);
          try {
            const out = kind === "m3u-link" ? convertToXtream(s) ?? s : s;
            if (kind !== "m3u-file") await testSource(out);
            await saveSource(out);
            if (out !== s) void useSync.getState().start(out, { preserveFlags: true, silent: true });
            onClose();
          } catch (e) { setErr(errorKey(e, t, s.type)); } finally { setBusy(false); }
        }}>{busy ? t("src.testing") : t("common.save")}</button>
      </>}>
      <SourceForm kind={kind} value={s} onChange={setS} fileInfo={s.m3uUrl.replace(/^file:/, "")} />
      {err && <div className="alert err" role="alert"><Icon name="alert" size={20} />{err}</div>}
    </Modal>
  );
}

// ---------------- Affichage ----------------
function DisplayPane() {
  const t = useT();
  const p = usePrefs();
  return (
    <>
      <h2>{t("set.display")}</h2>
      <Pref label={t("set.theme")}>
        <Seg<Theme> label={t("set.theme")} value={p.theme} onChange={(theme) => p.set({ theme })} options={[{ v: "auto", label: t("set.themeAuto") }, { v: "dark", label: t("set.themeDark") }, { v: "light", label: t("set.themeLight") }]} />
      </Pref>
      <Pref label={t("set.accent")}>
        <div style={{ display: "flex", gap: 10 }} role="radiogroup" aria-label={t("set.accent")}>
          {ACCENTS.map((c) => <button key={c} className="swatch" role="radio" aria-checked={p.accent === c} aria-label={c} style={{ background: c }} onClick={() => p.set({ accent: c })} />)}
        </div>
      </Pref>
      <Pref label={t("set.uiLanguage")}>
        <Seg<Lang> label={t("set.uiLanguage")} value={p.lang} onChange={(lang) => p.set({ lang })} options={LANGS.map((l) => ({ v: l.code, label: l.label }))} />
      </Pref>
    </>
  );
}

// ---------------- Lecture ----------------
function PlaybackPane() {
  const t = useT();
  const p = usePrefs();
  const [proxy, setProxy] = useState("");
  useEffect(() => { void getProxySetting().then((v) => setProxy(v ?? "")); }, []);
  return (
    <>
      <h2>{t("set.playback")}</h2>
      <Pref label={t("set.autoPreview")} desc={t("set.autoPreviewD")}><Switch on={p.autoPreview} onChange={(v) => p.set({ autoPreview: v })} label={t("set.autoPreview")} /></Pref>
      <Pref label={t("set.autoplayNext")}><Switch on={p.autoplayNext} onChange={(v) => p.set({ autoplayNext: v })} label={t("set.autoplayNext")} /></Pref>
      <Pref label={t("set.preferMp4")} desc={t("set.preferMp4D")}><Switch on={p.preferMp4} onChange={(v) => p.set({ preferMp4: v })} label={t("set.preferMp4")} /></Pref>
      <Pref label={t("set.liveFormat")} desc={t("set.liveFormatD")}>
        <Seg<"m3u8" | "ts"> label={t("set.liveFormat")} value={p.liveFormat} onChange={(liveFormat) => p.set({ liveFormat })} options={[{ v: "m3u8", label: "HLS" }, { v: "ts", label: "MPEG-TS" }]} />
      </Pref>
      {!isElectron() && (
        <Pref label={t("set.proxy")} desc={t("set.proxyD")}>
          <input className="input" style={{ width: "22rem" }} value={proxy} placeholder={defaultProxy() ?? "https://…"} onChange={(e) => setProxy(e.target.value)} onBlur={() => void setProxySetting(proxy.trim() || null)} aria-label={t("set.proxy")} />
        </Pref>
      )}
    </>
  );
}

// ---------------- Synchronisation ----------------
function SyncPane() {
  const t = useT();
  const p = usePrefs();
  const nav = useNavigate();
  const source = useActiveSource();
  const sync = useSync();
  const cats = useLiveQuery(async () => (source?.cid ? [...(await categoriesOf(source.cid, "live")), ...(await categoriesOf(source.cid, "movie")), ...(await categoriesOf(source.cid, "series"))] : []), [source?.cid]) ?? [];
  const a = cats.filter((c) => c.enabled).length;
  const [busyEpg, setBusyEpg] = useState(false);
  if (!source) return null;
  return (
    <>
      <h2>{t("set.sync")}</h2>
      <p className="lead">{t("set.lastSync", { t: source.lastSyncAt ? new Date(source.lastSyncAt).toLocaleString(p.lang) : t("set.never") })}</p>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
        <button className="btn primary" disabled={sync.running} onClick={() => void sync.start(source, { preserveFlags: true })}><Icon name="refresh" size={18} />{sync.running ? t("set.syncing") : t("set.syncNow")}</button>
        <button className="btn" disabled={busyEpg || sync.running} onClick={async () => { setBusyEpg(true); try { await syncSourceEpg(source); useUi.getState().toast(t("toast.synced")); } catch { /* ignoré */ } setBusyEpg(false); }}>{t("set.epgNow")}</button>
        {sync.running && <button className="btn" onClick={sync.cancel}>{t("common.cancel")}</button>}
      </div>
      {sync.running && sync.progress && <div className="bigbar"><i style={{ width: `${Math.round(sync.progress.ratio * 100)}%` }} /></div>}
      {sync.error && <div className="alert err" role="alert"><Icon name="alert" size={20} />{sync.error}</div>}
      <Pref label={t("set.syncWhen")}>
        <Seg<"launch" | "manual"> label={t("set.syncWhen")} value={p.syncOn} onChange={(syncOn) => p.set({ syncOn })} options={[{ v: "launch", label: t("set.syncLaunch") }, { v: "manual", label: t("set.syncManual") }]} />
      </Pref>
      <Pref label={t("set.manageCats")} desc={t("set.catsSummary", { a, d: cats.length - a })}>
        <button className="btn" onClick={() => nav("/settings/categories")}>{t("set.manageCats")}<Icon name="chevron" size={16} /></button>
      </Pref>
    </>
  );
}

// ---------------- Langues et catégories ----------------
function CategoriesPane() {
  const t = useT();
  const source = useActiveSource();
  const sync = useSync();
  const [kind, setKind] = useState<Kind>("live");
  const [q, setQ] = useState("");
  const all = useLiveQuery(async () => (source?.cid ? (await db.categories.where("[sourceId+kind]").between([source.cid, ""], [source.cid, "￿"]).toArray()).sort((a, b) => a.ord - b.ord) : []), [source?.cid]) ?? [];
  const initial = useRef<Map<number, 0 | 1> | null>(null);
  if (initial.current == null && all.length) initial.current = new Map(all.map((c) => [c.id!, c.enabled]));
  const pending = initial.current ? all.filter((c) => initial.current!.get(c.id!) !== c.enabled).length : 0;
  const nq = normText(q);
  // Filtre par texte seulement (nom brut ou nom affiché) : aucune déduction de langue, fiable quelle que soit la source.
  // Cochées d'abord (ordre du fournisseur conservé dans chaque groupe), sur tous les appareils.
  const shown = useMemo(() => all.filter((c) => c.kind === kind && (!nq || catMatches(c.name, nq))).sort((a, b) => b.enabled - a.enabled), [all, kind, nq]);
  if (!source) return null;
  const setMany = async (rows: CategoryRow[], v: 0 | 1) => { if (await setCategoriesEnabled(rows, v) && source.id != null) notePrefsChanged(source.id); };
  const counts = { live: all.filter((c) => c.kind === "live").length, movie: all.filter((c) => c.kind === "movie").length, series: all.filter((c) => c.kind === "series").length };
  return (
    <>
      <h2>{t("set.categories")}</h2>
      <p className="lead">{t("set.catsNote")}</p>
      <div className="chips" role="tablist">
        {(["live", "movie", "series"] as const).map((k) => <button key={k} className="chip" role="tab" aria-selected={kind === k} onClick={() => setKind(k)}>{t(k === "live" ? "nav.live" : k === "movie" ? "nav.movies" : "nav.series")} <span className="n">{counts[k]}</span></button>)}
      </div>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
        <div className="search-box" style={{ width: "22rem", maxWidth: "100%" }}>
          <Icon name="search" size={16} />
          <input className="input" placeholder={t("set.catsFilter")} value={q} onChange={(e) => setQ(e.target.value)} aria-label={t("common.filter")} />
        </div>
        <button className="btn sm" onClick={() => void setMany(shown, 1)}>{t("set.catsEnableAll")} ({shown.length})</button>
        <button className="btn sm" onClick={() => void setMany(shown, 0)}>{t("set.catsDisableAll")} ({shown.length})</button>
      </div>
      <div style={{ height: "min(28rem, 50vh)", display: "flex", flexDirection: "column", minHeight: 0 }}>
        <VList rows={arrayRows(shown)} rowH={52} className="vlist" label={t("common.categories")}
          render={(c) => c && (
            <div className="cat-row">
              <span className="ellipsis"><b style={{ fontWeight: 600 }}>{c.label}</b> <span className="muted mono" style={{ fontSize: "0.75rem" }}>{c.count || ""}</span></span>
              <span><Switch on={!!c.enabled} label={c.label} onChange={(v) => void setMany([c], v ? 1 : 0)} /></span>
              <span />
            </div>
          )} />
      </div>
      <div style={{ display: "flex", gap: 12, alignItems: "center" }}>
        <button className="btn primary" disabled={pending === 0 || sync.running} onClick={() => { initial.current = null; void sync.start(source, { preserveFlags: true }); }}>{sync.running ? t("set.syncing") : t("set.catsApply")}</button>
        {pending > 0 && <span className="muted">{t("set.catsPending", { n: pending })}</span>}
        {sync.running && sync.progress && <span className="muted mono">{Math.round(sync.progress.ratio * 100)} %</span>}
      </div>
    </>
  );
}

// ---------------- Langue ----------------
function LanguagePane() {
  const t = useT();
  const p = usePrefs();
  return (
    <>
      <h2>{t("set.language")}</h2>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(14rem, 1fr))", gap: 10 }}>
        {LANGS.map((l) => (
          <button key={l.code} className="lang-tile" role="radio" aria-checked={p.lang === l.code} onClick={() => p.set({ lang: l.code })}>
            <span className="code">{l.code.toUpperCase()}</span><b>{l.label}</b>
          </button>
        ))}
      </div>
    </>
  );
}

// ---------------- Profils ----------------
function ProfilesPane() {
  const t = useT();
  const p = usePrefs();
  const [name, setName] = useState("");
  return (
    <>
      <h2>{t("set.profiles")}</h2>
      <p className="lead">{t("set.profilesLead")}</p>
      {p.profiles.map((pr, i) => (
        <div key={pr.id} className="src-card">
          <span className="avatar" style={{ background: pr.color }}>{(pr.name || t("profile.main")).slice(0, 1).toUpperCase()}</span>
          <span className="grow"><b>{pr.name || t("profile.main")}</b>{p.profileId === pr.id && <div className="meta">{t("set.active")}</div>}</span>
          {p.profileId !== pr.id && <button className="btn sm" onClick={() => p.set({ profileId: pr.id })}>{t("set.makeActive")}</button>}
          {i > 0 && <button className="btn sm danger" aria-label={t("common.delete")} onClick={() => p.set({ profiles: p.profiles.filter((x) => x.id !== pr.id), profileId: p.profileId === pr.id ? p.profiles[0]!.id : p.profileId })}><Icon name="trash" size={16} /></button>}
        </div>
      ))}
      <div style={{ display: "flex", gap: 10 }}>
        <input className="input" style={{ maxWidth: "20rem" }} placeholder={t("set.profileName")} value={name} onChange={(e) => setName(e.target.value)} aria-label={t("set.profileName")} />
        <button className="btn primary" disabled={!name.trim()} onClick={() => { p.set({ profiles: [...p.profiles, { id: `p${Date.now()}`, name: name.trim(), color: PROFILE_COLORS[p.profiles.length % PROFILE_COLORS.length]! }] }); setName(""); }}>{t("set.addProfile")}</button>
      </div>
    </>
  );
}

// ---------------- À propos ----------------
function AboutPane() {
  const t = useT();
  const b = bridge();
  const update = useUi((s) => s.update);
  const [msg, setMsg] = useState<string | null>(null);
  return (
    <>
      <h2>{t("set.about")}</h2>
      <p className="lead">{t("set.about.privacy")}</p>
      <Pref label={t("set.about.version", { v: b?.version ?? "web" })} desc={b ? `${b.platform} · ${b.arch ?? ""}` : undefined}>
        {b?.checkForUpdates && (
          <button className="btn" onClick={async () => {
            const r = (await b.checkForUpdates!()) as { state?: string; version?: string } | undefined;
            const st = r?.state;
            // Une erreur n'est plus présentée comme « à jour ».
            setMsg(st === "available" || st === "downloading" || st === "downloaded" ? t("set.about.updateAvail", { v: r?.version ?? "" })
              : st === "error" ? t("set.about.updateFail") : st === "unavailable" ? t("set.about.updateUnavail") : t("set.about.updateNone"));
          }}>{t("set.about.update")}</button>
        )}
      </Pref>
      {(msg || update?.state === "downloading") && <div className="alert">{update?.state === "downloading" ? t("state.downloading", { p: Math.round(update.percent ?? 0) }) : msg}</div>}
      <div className="eyebrow">{t("set.about.shortcuts")}</div>
      <div className="hotkeys">
        <span><span className="kbd">/</span> <span className="kbd">Ctrl/⌘ K</span></span><span>{t("set.sk.search")}</span>
        <span><span className="kbd">↑</span> <span className="kbd">↓</span></span><span>{t("set.sk.nav")}</span>
        <span><span className="kbd">{t("key.enter")}</span></span><span>{t("set.sk.open")}</span>
        <span><span className="kbd">{t("key.esc")}</span></span><span>{t("set.sk.esc")}</span>
        <span><span className="kbd">{t("key.space")}</span> <span className="kbd">F</span> <span className="kbd">M</span> <span className="kbd">P</span> <span className="kbd">I</span></span><span>{t("player.shortcuts")}</span>
      </div>
    </>
  );
}

/** Filtre court (« FR », « AR », « 4K ») = mot entier : « FR » ne doit pas ramener « AFRICA ». Sinon, sous-chaîne. */
export function catMatches(name: string, nq: string): boolean {
  const n = normText(name);
  if (nq.length > 3 || /\s/.test(nq)) return n.includes(nq);
  return n.split(/[^\p{L}\p{N}+]+/u).includes(nq);
}
