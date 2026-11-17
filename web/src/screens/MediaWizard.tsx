// Étapes de l'assistant « ma bibliothèque » (Jellyfin, Emby, Plex, NAS), d'après les maquettes « Ajout guidé » (variante B) :
// 1 trouver le serveur (détection sur le réseau local) → 2 se connecter (formulaire, ou code Plex) → 3 choisir les bibliothèques.
// La logique pure est dans lib/mediaWizard.ts et net/discovery.ts ; ici seulement l'affichage et l'enchaînement.
import type { ReactElement } from "react";
import { useCallback, useEffect, useRef, useState } from "react";
import qrcode from "qrcode-generator";
import { useT } from "@/i18n";
import type { Source } from "@/db/types";
import { Icon } from "@/ui/Icon";
import { discoverServers, discoveryAvailable, displayAddress, mergeFound, SERVER_COLORS, SERVER_MARKS, type FoundServer } from "@/net/discovery";
import { awaitPlexToken, PIN_TTL_MS, PlexAccount, PLEX_LINK_URL, type PlexServer } from "@/net/plex";
import type { Library } from "@/net/mediaServer";
import { bridge, currentTransport } from "@/net/transport";
import { mediaOf } from "@/sync/core";
import { countdownParts, libsToSave, plexConnKind, WIZARD_STEPS, type ConnKind } from "@/lib/mediaWizard";

/** Pastille ronde colorée d'un type de serveur (J, E, P, N). */
export function ServerLogo({ kind, size = 56 }: { kind: keyof typeof SERVER_COLORS; size?: number }) {
  return <span className="wz-logo" style={{ width: size, height: size, background: SERVER_COLORS[kind], fontSize: size * 0.42 }} aria-hidden="true">{SERVER_MARKS[kind]}</span>;
}

/** Bandeau des 3 étapes (maquette) : `n` = étape en cours, 0 à 2. */
export function WizardSteps({ n }: { n: number }) {
  const t = useT();
  const labels = [t("wiz.stepFind"), t("wiz.stepConnect"), t("wiz.stepLibs")];
  return (
    <ol className="steps" aria-label={t("wiz.stepsLabel")} data-testid="wizard-steps">
      {WIZARD_STEPS.map((s, i) => (
        <li key={s} className={i < n ? "done" : i === n ? "on" : ""} aria-current={i === n ? "step" : undefined}>
          {i > 0 && <span className="ln" aria-hidden="true" />}
          <span className="dot">{i < n ? <Icon name="check" size={14} stroke={3} /> : i + 1}</span>{labels[i]}
        </li>
      ))}
    </ol>
  );
}

const KIND_NAME = { jellyfin: "Jellyfin", emby: "Emby", plex: "Plex" } as const;

// ---------------------------------------------------------------- étape 1 : trouver le serveur

export function FindStep({ onPick, onManual, onNas, onPlex }: {
  onPick: (s: FoundServer) => void; onManual: () => void; onNas: () => void; onPlex: () => void;
}) {
  const t = useT();
  const avail = discoveryAvailable();
  const [found, setFound] = useState<FoundServer[]>([]);
  const [scanning, setScanning] = useState(avail);
  const [rounds, setRounds] = useState(0);
  const gen = useRef(0);

  const scan = useCallback(async () => {
    const my = ++gen.current;
    setScanning(true);
    const list = await discoverServers();
    if (my !== gen.current) return;
    setFound((p) => mergeFound(p, list));
    setRounds((r) => r + 1);
    setScanning(false);
  }, []);

  useEffect(() => {
    if (avail) void scan();
    return () => { gen.current++; };
  }, [avail, scan]);

  // Rien trouvé : on relance seul deux fois (un serveur peut mettre plus d'une seconde à répondre), puis c'est à l'utilisateur.
  useEffect(() => {
    if (!avail || scanning || found.length > 0 || rounds === 0 || rounds >= 3) return;
    const id = setTimeout(() => void scan(), 800);
    return () => clearTimeout(id);
  }, [avail, scanning, found.length, rounds, scan]);

  return (
    <main style={{ maxWidth: "70rem" }}>
      <div>
        <h1 style={{ marginTop: 8 }}>{t("wiz.foundTitle")}</h1>
        <p className="lead" style={{ marginTop: 12 }}>{t("wiz.foundLead")}</p>
      </div>
      {avail ? (
        <>
          {found.length > 0 && (
            <div className="wz-cards" role="list">
              {found.map((s, i) => (
                <button key={`${s.kind}:${s.id}`} role="listitem" className={`wz-card${i === 0 ? " first" : ""}`} onClick={() => onPick(s)}>
                  <span className="wz-card-top"><ServerLogo kind={s.kind} size={64} /><span className="wz-chip">{KIND_NAME[s.kind]}</span></span>
                  <span className="wz-card-name">{s.name}</span>
                  <span className="wz-card-addr">{displayAddress(s.address)}</span>
                  <span className="wz-card-libs">{t("wiz.libsAfter")}</span>
                </button>
              ))}
            </div>
          )}
          <div className="wz-scan" role="status">
            {scanning ? (
              <><span className="wz-dot pulse" aria-hidden="true" />{t("wiz.scanning")}</>
            ) : (
              <>
                <span className="wz-dot" aria-hidden="true" />{found.length ? t("wiz.scanDone") : t("wiz.noneFound")}
                <button type="button" className="btn sm" onClick={() => void scan()}><Icon name="refresh" size={16} />{t("wiz.rescan")}</button>
              </>
            )}
          </div>
        </>
      ) : (
        <p className="muted" data-testid="discovery-unavailable">{t("wiz.desktopOnly")}</p>
      )}
      <div className="wz-actions">
        <button className="btn lg" onClick={onManual}>{t("wiz.manual")}</button>
        <button className="btn lg" onClick={onNas}>{t("wiz.nasShare")}</button>
        <button className="btn lg" onClick={onPlex}>{t("wiz.plexAccount")}</button>
      </div>
    </main>
  );
}

// ---------------------------------------------------------------- étape 2 : formulaire Jellyfin / Emby

export function MediaConnectForm({ kind, value, onChange, onKind, busy, err, onSubmit, onBack }: {
  kind: "jellyfin" | "emby"; value: Source; onChange: (s: Source) => void; onKind: (k: "jellyfin" | "emby") => void;
  busy: boolean; err: string | null; onSubmit: () => void; onBack: () => void;
}) {
  const t = useT();
  const [mode, setMode] = useState<"login" | "key">(value.username ? "login" : value.password ? "key" : "login");
  const set = (p: Partial<Source>) => onChange({ ...value, ...p });
  const pick = (m: "login" | "key") => { setMode(m); if (m === "key") set({ username: "" }); };
  return (
    <main className="wz-connect" onKeyDown={(e) => { if (e.key === "Enter" && !busy && (e.target as HTMLElement).tagName === "INPUT") onSubmit(); }}>
      <section className="wz-panel" aria-labelledby="wz-h-media">
        <div className="wz-head">
          <ServerLogo kind={kind} size={64} />
          <h1 id="wz-h-media">{t("src.serverOf", { name: KIND_NAME[kind] })}</h1>
        </div>
        <div role="radiogroup" aria-label={t("src.serverType")} className="wz-seg">
          {(["jellyfin", "emby"] as const).map((k) => (
            <button key={k} type="button" role="radio" aria-checked={kind === k} className={kind === k ? "on" : ""} onClick={() => onKind(k)}>{KIND_NAME[k]}</button>
          ))}
        </div>
        <label className="wz-field">{t("src.server")}
          <input className="input" value={value.server} placeholder="http://192.168.1.10:8096" autoCapitalize="off" spellCheck={false} onChange={(e) => set({ server: e.target.value })} />
        </label>
        <div role="radiogroup" aria-label={t("src.authMode")} className="wz-seg">
          <button type="button" role="radio" aria-checked={mode === "login"} className={mode === "login" ? "on" : ""} onClick={() => pick("login")}>{t("src.authLogin")}</button>
          <button type="button" role="radio" aria-checked={mode === "key"} className={mode === "key" ? "on" : ""} onClick={() => pick("key")}>{t("src.authKey")}</button>
        </div>
        {mode === "login" && (
          <>
            <label className="wz-field">{t("src.authLogin")}
              <input className="input" value={value.username} autoComplete="off" spellCheck={false} onChange={(e) => set({ username: e.target.value })} />
            </label>
            <label className="wz-field">{t("src.password")}
              <input className="input" type="password" value={value.password} autoComplete="off" onChange={(e) => set({ password: e.target.value })} />
            </label>
          </>
        )}
        {mode === "key" && (
          <label className="wz-field">{t("src.authKey")}
            <input className="input" type="password" value={value.password} autoComplete="off" onChange={(e) => set({ password: e.target.value })} />
          </label>
        )}
        {err && <div className="alert err" role="alert"><Icon name="alert" size={20} />{err}</div>}
        {busy && <div className="alert" role="status">{t("src.testing")}</div>}
        <p className="wz-note"><Icon name="lock" size={18} />{mode === "key" ? t("src.keyNote") : t("src.secretNote")}</p>
        <div className="wz-actions">
          <button className="btn" onClick={onBack}>{t("common.back")}</button>
          <button className="btn primary lg" disabled={busy} onClick={onSubmit}>{t("src.connectImport")}</button>
        </div>
      </section>
    </main>
  );
}

// ---------------------------------------------------------------- étape 2 : compte Plex (code PIN)

/** QR code (modules sombres sur fond clair) de l'adresse plex.tv/link, généré ici sans réseau. */
export function QrSvg({ text, size = 200 }: { text: string; size?: number }) {
  const t = useT();
  const q = qrcode(0, "M");
  q.addData(text);
  q.make();
  const n = q.getModuleCount();
  const cells: ReactElement[] = [];
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (q.isDark(y, x)) cells.push(<rect key={`${y}:${x}`} x={x} y={y} width={1.02} height={1.02} />);
  return (
    <svg className="wz-qr" width={size} height={size} viewBox={`-2 -2 ${n + 4} ${n + 4}`} role="img" aria-label={t("wiz.plexStep1", { url: PLEX_LINK_URL.replace("https://", "") })} shapeRendering="crispEdges">
      <rect x={-2} y={-2} width={n + 4} height={n + 4} fill="#F1F5F9" />
      <g fill="#0A0F1E">{cells}</g>
    </svg>
  );
}

type PlexPhase =
  | { k: "waiting"; code: string; since: number }
  | { k: "choosing"; servers: PlexServer[] }
  | { k: "connecting" }
  | { k: "error"; msg: "expired" | "failed" | "none" | "unreachable" };

const PLEX_ERR = { expired: "src.plexExpired", failed: "src.plexFailed", none: "src.plexNoServer", unreachable: "src.plexUnreachable" } as const;
const CONN_KEY: Record<ConnKind, "wiz.connLocal" | "wiz.connRemote" | "wiz.connRelay"> = { local: "wiz.connLocal", remote: "wiz.connRemote", relay: "wiz.connRelay" };

export function PlexStep({ preferMachineId, onChosen, onCancel }: {
  preferMachineId?: string; onChosen: (r: { server: string; username: string; password: string; name: string }) => void; onCancel: () => void;
}) {
  const t = useT();
  const [phase, setPhase] = useState<PlexPhase | null>(null);
  const [linked, setLinked] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  const abort = useRef<AbortController | null>(null);
  const account = useRef<PlexAccount | null>(null);

  const choose = useCallback(async (s: PlexServer) => {
    setPhase({ k: "connecting" });
    try {
      const conn = await account.current!.bestConnection(s);
      if (!conn) { setPhase({ k: "error", msg: "unreachable" }); return; }
      onChosen({ server: conn.uri, username: s.machineId, password: s.accessToken, name: s.name });
    } catch (e) {
      if ((e as Error).name !== "AbortError") setPhase({ k: "error", msg: "unreachable" });
    }
  }, [onChosen]);

  const start = useCallback(async () => {
    abort.current?.abort();
    const ctrl = new AbortController();
    abort.current = ctrl;
    setLinked(false);
    try {
      const acc = new PlexAccount(await currentTransport(), undefined, ctrl.signal);
      account.current = acc;
      const res = await awaitPlexToken({ create: () => acc.createPin(), poll: (id) => acc.pollPin(id), onCode: (code) => setPhase({ k: "waiting", code, since: Date.now() }), signal: ctrl.signal });
      if ("expired" in res) { setPhase({ k: "error", msg: "expired" }); return; }
      setPhase({ k: "connecting" });
      const servers = await acc.servers(res.token);
      setLinked(true);
      if (!servers.length) { setPhase({ k: "error", msg: "none" }); return; }
      const wanted = preferMachineId ? servers.find((s) => s.machineId === preferMachineId) : undefined;
      if (wanted || servers.length === 1) await choose(wanted ?? servers[0]!);
      else setPhase({ k: "choosing", servers });
    } catch (e) {
      if ((e as Error).name !== "AbortError") setPhase({ k: "error", msg: "failed" });
    }
  }, [choose, preferMachineId]);

  useEffect(() => { void start(); return () => abort.current?.abort(); }, [start]);
  useEffect(() => {
    if (phase?.k !== "waiting") return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [phase?.k]);

  const link = PLEX_LINK_URL.replace("https://", "");
  const openLink = () => { const b = bridge(); if (b?.openExternal) void b.openExternal(PLEX_LINK_URL); else window.open(PLEX_LINK_URL, "_blank", "noopener"); };
  const cd = phase?.k === "waiting" ? countdownParts(PIN_TTL_MS - (now - phase.since)) : null;

  return (
    <main className="wz-plex" style={{ maxWidth: "76rem" }}>
      <section className="wz-panel" aria-labelledby="wz-h-pin">
        <div className="wz-head">
          <ServerLogo kind="plex" size={64} />
          <h1 id="wz-h-pin">{t("wiz.plexTitle")}</h1>
        </div>
        <ol className="wz-steps-list">
          <li>{t("wiz.plexStep1", { url: link })}</li>
          <li>{t("wiz.plexStep2")}</li>
        </ol>
        {phase?.k === "waiting" ? (
          <div className="wz-code-row">
            <div className="wz-code" aria-label={t("src.plexOpen")} data-testid="plex-code">
              {[...phase.code].map((c, i) => <span key={i}>{c}</span>)}
            </div>
            <QrSvg text={PLEX_LINK_URL} />
          </div>
        ) : phase?.k === "error" ? (
          <div className="alert err" role="alert"><Icon name="alert" size={20} />{t(PLEX_ERR[phase.msg])}</div>
        ) : (
          <div className="alert" role="status">{t("src.plexConnecting")}</div>
        )}
        {cd && <div className="wz-scan" role="status"><span className="wz-dot pulse" aria-hidden="true" />{cd.unit === "min" ? t("wiz.plexExpiresMin", { n: cd.n }) : t("wiz.plexExpiresSec", { n: cd.n })}</div>}
        <div className="wz-actions" style={{ marginTop: "auto" }}>
          <button className="btn" onClick={() => void start()}>{t("wiz.plexNew")}</button>
          <button className="btn" onClick={openLink}>{link}</button>
          <button className="btn" onClick={onCancel}>{t("common.cancel")}</button>
        </div>
      </section>
      <section className="wz-panel" aria-labelledby="wz-h-srv">
        <h2 id="wz-h-srv">{t("wiz.plexPickTitle")}</h2>
        {phase?.k === "choosing" ? (
          <>
            <p className="muted">{t("wiz.plexPickLead")}</p>
            {phase.servers.map((s, i) => (
              <button key={s.machineId} className={`wz-server${i === 0 ? " first" : ""}`} onClick={() => void choose(s)}>
                <span className="grow"><b>{s.name}</b><span className="s">{s.owned ? t("wiz.plexOwned") : t("wiz.plexShared")}</span></span>
                <span className={`wz-badge ${plexConnKind(s)}`}>{t(CONN_KEY[plexConnKind(s)])}</span>
              </button>
            ))}
          </>
        ) : (
          <p className="muted">{linked ? t("wiz.plexPickLead") : t("src.plexHint")}</p>
        )}
        <p className="muted" style={{ marginTop: "auto", fontSize: "0.8125rem" }}>{t("wiz.plexConnHint")}</p>
      </section>
    </main>
  );
}

// ---------------------------------------------------------------- étape 3 : choisir les bibliothèques

export function LibsStep({ source, onDone }: { source: Source; onDone: (libs: string[] | null) => void }) {
  const t = useT();
  const [libs, setLibs] = useState<Library[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [checked, setChecked] = useState<Set<string>>(new Set());

  useEffect(() => {
    let live = true;
    void (async () => {
      try {
        const list = await mediaOf(await currentTransport(), source).libraries();
        if (!live) return;
        setLibs(list);
        setChecked(new Set(list.map((l) => l.id)));   // toutes cochées par défaut
      } catch {
        if (live) { setFailed(true); setLibs([]); }
      }
    })();
    return () => { live = false; };
  }, [source]);

  const toggle = (id: string) => setChecked((c) => { const n = new Set(c); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  const all = libs != null && libs.length > 0 && checked.size === libs.length;
  const group = (type: Library["type"], title: string) => {
    const rows = (libs ?? []).filter((l) => l.type === type);
    if (!rows.length) return null;
    return (
      <div className="wz-libgroup" role="group" aria-label={title}>
        <h2>{title}</h2>
        {rows.map((l) => (
          <label key={l.id} className="wz-lib"><input type="checkbox" checked={checked.has(l.id)} onChange={() => toggle(l.id)} /><span>{l.name}</span></label>
        ))}
      </div>
    );
  };

  return (
    <main style={{ maxWidth: "56rem" }}>
      <div>
        <h1 style={{ marginTop: 8 }}>{t("wiz.libsTitle")}</h1>
        <p className="lead" style={{ marginTop: 12 }}>{t("wiz.libsLead")}</p>
      </div>
      {libs == null && <div className="alert" role="status">{t("wiz.libsLoading")}</div>}
      {failed && <div className="alert err" role="alert"><Icon name="alert" size={20} />{t("wiz.libsFailed")}</div>}
      {libs != null && !failed && libs.length === 0 && <div className="alert" role="status">{t("wiz.libsEmpty")}</div>}
      {libs != null && libs.length > 0 && (
        <div className="wz-libs">
          {group("movies", t("nav.movies"))}
          {group("tvshows", t("nav.series"))}
        </div>
      )}
      <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        {libs != null && libs.length > 0 && (
          <>
            <button className="btn" onClick={() => setChecked(all ? new Set() : new Set(libs.map((l) => l.id)))}>{t("wiz.libsAll")}</button>
            <span className="muted grow">{t("wiz.libsCount", { n: checked.size, total: libs.length })}</span>
          </>
        )}
        {(libs == null || libs.length === 0) && <span className="grow" />}
        <button className="btn primary lg" disabled={libs == null || (libs.length > 0 && checked.size === 0)} onClick={() => onDone(libs && libs.length ? libsToSave(libs, checked) : null)}>{t("wiz.libsImport")}</button>
      </div>
    </main>
  );
}
