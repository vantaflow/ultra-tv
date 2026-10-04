// Assistant d'ajout d'une source : type -> identifiants (vérifiés) -> langues -> première synchro.
import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { db } from "@/db/db";
import { emptySource, getSource, saveSource } from "@/db/sources";
import type { Source } from "@/db/types";
import { useT, type TFn } from "@/i18n";
import { HttpError, isElectron } from "@/net/transport";
import { usePrefs } from "@/state/prefs";
import { useSync } from "@/state/sync";
import { detectSourceLanguages, testSource } from "@/sync/client";
import { OTHER_LANG, type DetectedLanguage } from "@/sync/core";
import { AppMark, Icon, type IconName } from "@/ui/Icon";
import { SourceForm, type SourceKind as FormKind } from "./SourceForm";
import { PairingView } from "./Pairing";
import { cloudAvailable } from "@/cloud/client";
import { listSources } from "@/db/sources";
import { convertToXtream, missingRequired, nonStandardHttpStatus } from "@/lib/xtreamUrl";

type SourceKind = FormKind | "cloud";

type Step = "type" | "form" | "langs" | "sync" | "cloud";

export function errorKey(e: unknown, t: TFn, type?: Source["type"]): string {
  const err = e as Error;
  if (err instanceof HttpError) return type === "m3u" && nonStandardHttpStatus(err.message) != null ? t("src.err.m3uBlocked") : t("src.err.blocked", { c: err.status });
  if (err.message === "auth") return t("src.err.auth");
  if (err.message === "expired") return t("src.err.expired");
  if (err.message === "not-m3u") return t("src.err.notm3u");
  return isElectron() ? t("src.err.network") : t("src.err.cors");
}

function Brand() {
  const t = useT();
  return <div className="brand"><AppMark size={40} className="ic" /><span>ULTRA <span>TV</span></span><span className="sr-only">{t("app.name")}</span></div>;
}

function Steps({ n }: { n: number }) {
  const t = useT();
  const labels = [t("set.sources"), t("common.languages"), t("set.sync")];
  return (
    <ol className="steps" aria-label="Progression">
      {labels.map((l, i) => (
        <li key={i} className={i < n ? "done" : i === n ? "on" : ""}>
          {i > 0 && <span className="ln" aria-hidden="true" />}
          <span className="dot">{i < n ? <Icon name="check" size={14} stroke={3} /> : i + 1}</span>{l}
        </li>
      ))}
    </ol>
  );
}

function langLabel(code: string, ui: string, t: TFn): string {
  if (code === OTHER_LANG) return t("lang.other");
  let out = code;
  try {
    out = code === "AR"
      ? new Intl.DisplayNames([ui], { type: "language" }).of("ar") ?? code
      : new Intl.DisplayNames([ui], { type: "region" }).of(code === "UK" ? "GB" : code) ?? code;
  } catch { /* code brut */ }
  return out.charAt(0).toLocaleUpperCase(ui) + out.slice(1);
}

export function Onboarding() {
  const t = useT();
  const nav = useNavigate();
  const [params] = useSearchParams();
  const adding = params.get("add") === "1";
  const prefs = usePrefs();
  const [step, setStep] = useState<Step>(params.get("cloud") === "1" && cloudAvailable() ? "cloud" : "type");
  const [kind, setKind] = useState<SourceKind>("xtream");
  const [src, setSrc] = useState<Source>(() => emptySource());
  const [fileText, setFileText] = useState<string | null>(null);
  const [fileInfo, setFileInfo] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [detected, setDetected] = useState<DetectedLanguage[]>([]);
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [savedId, setSavedId] = useState<number | null>(null);
  const sync = useSync();
  const started = useRef(false);

  const pickType = (k: SourceKind) => {
    setKind(k);
    if (k === "cloud") { setStep("cloud"); return; }
    setSrc({ ...emptySource(), type: k === "xtream" ? "xtream" : "m3u" });
    setFileText(null); setFileInfo(""); setErr(null);
    setStep("form");
  };

  const submit = async () => {
    setErr(null);
    // Adresse get.php / player_api.php collée en M3U : c'est une source Xtream Codes (get.php est souvent bloqué).
    const xt = kind === "m3u-link" ? convertToXtream(src) : null;
    const s = { ...(xt ?? src), name: src.name.trim() || (kind === "xtream" || xt ? (xt ?? src).server.replace(/^https?:\/\//, "").split(/[:/]/)[0]! : t("src.m3uLink")) };
    // On valide la source EFFECTIVE (après conversion) : une adresse get.php devient Xtream, m3uUrl est alors vide.
    if (missingRequired(kind, s)) { setErr(t("src.err.required")); return; }
    if (kind === "m3u-file" && fileText == null) { setErr(t("src.err.required")); return; }
    setBusy(true);
    try {
      if (kind === "m3u-file") await db.details.put({ key: "m3ufile:tmp", json: fileText, fetchedAt: Date.now() });
      const r = await testSource(s);
      const saved: Source = { ...s, expDate: r.expDate, maxConnections: r.maxConnections };
      const id = await saveSource(saved);
      if (kind === "m3u-file") {
        await db.details.put({ key: `m3ufile:${id}`, json: fileText, fetchedAt: Date.now() });
        await db.details.delete("m3ufile:tmp");
      }
      setSavedId(id);
      setSrc({ ...saved, id });
      prefs.set({ activeSourceId: id });
      if (s.type === "xtream") {
        const d = await detectSourceLanguages({ ...saved, id });
        setDetected(d.languages);
        const ui = prefs.lang.toUpperCase();
        // Langue de l'interface + catégories sans langue identifiable (« Autres », souvent internationales).
        const pre = d.languages.filter((l) => l.code === ui || l.code === OTHER_LANG).map((l) => l.code);
        setChosen(new Set(pre.length ? pre : d.languages.map((l) => l.code)));
        setStep("langs");
      } else setStep("sync");
    } catch (e) {
      setErr(errorKey(e, t, s.type));
    } finally {
      setBusy(false);
    }
  };

  const startSync = async (id: number, langs: string[] | null) => {
    const row = await getSource(id);
    if (!row) return;
    const s = { ...row, langs };
    await saveSource(s);
    void useSync.getState().start(s, { silent: true });
  };

  useEffect(() => {
    if (step !== "sync" || started.current || savedId == null) return;
    started.current = true;
    void startSync(savedId, kind === "xtream" && chosen.size < detected.length ? [...chosen] : null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [step, savedId]);

  const done = step === "sync" && !sync.running && !sync.error && !!sync.progress;
  useEffect(() => { if (done) { const id = setTimeout(() => nav("/", { replace: true }), 900); return () => clearTimeout(id); } }, [done, nav]);

  const catsTotal = useMemo(() => detected.filter((l) => chosen.has(l.code)).reduce((a, l) => a + l.categories, 0), [detected, chosen]);

  return (
    <div className="stage">
      <header>
        <Brand />
        {step !== "sync" && <Steps n={step === "langs" ? 1 : 0} />}
        {adding && step !== "sync" ? <button className="btn sm" onClick={() => nav("/settings/sources")}>{t("common.cancel")}</button> : <span />}
      </header>

      {step === "type" && (
        <main>
          <div>
            <div className="eyebrow">{t("src.step", { a: 1, b: 3 })}</div>
            <h1 style={{ marginTop: 8 }}>{t("src.which")}</h1>
            <p className="lead" style={{ marginTop: 12 }}>{t("src.whichLead")}</p>
          </div>
          <div className="choice-grid">
            {([...(cloudAvailable() ? [["cloud", "globe", "src.cloud", "src.cloudD"]] : []), ["xtream", "source", "src.xtream", "src.xtreamD"], ["m3u-link", "link", "src.m3uLink", "src.m3uLinkD"], ["m3u-file", "file", "src.m3uFile", "src.m3uFileD"]] as [SourceKind, IconName, "src.xtream", "src.xtreamD"][]).map(([k, ic, l, d], i) => (
              <button key={k} className={`choice${i === 0 ? " first" : ""}`} onClick={() => pickType(k)}>
                <span className="ico"><Icon name={ic} size={30} /></span>
                <span><b>{t(l)}</b><span className="s">{t(d)}</span></span>
              </button>
            ))}
          </div>
        </main>
      )}

      {step === "cloud" && (
        <PairingView
          onCancel={() => setStep("type")}
          onDone={async (r) => {
            const list = await listSources();
            const first = list.find((x) => x.cloudId);
            if (first) { prefs.set({ activeSourceId: first.id! }); setSavedId(first.id!); started.current = true; setSrc(first); setKind(first.type === "xtream" ? "xtream" : "m3u-link"); setStep("sync"); }
            else if (list.length) nav("/", { replace: true });
            else { setStep("type"); setErr(r.skipped ? t("cloud.noProviders") : t("cloud.noProviders")); }
          }}
        />
      )}
      {step === "type" && err && <div className="alert err" style={{ maxWidth: "56rem", margin: "0 auto" }} role="alert">{err}</div>}

      {step === "form" && (
        <main onKeyDown={(e) => { if (e.key === "Enter" && !busy && (e.target as HTMLElement).tagName === "INPUT") void submit(); }}>
          <div>
            <div className="eyebrow">{t("src.step", { a: 2, b: 3 })}</div>
            <h1 style={{ marginTop: 8 }}>{t(kind === "xtream" ? "src.xtream" : kind === "m3u-link" ? "src.m3uLink" : "src.m3uFile")}</h1>
          </div>
          <SourceForm kind={kind as FormKind} value={src} onChange={setSrc} fileInfo={fileInfo}
            onPickFile={async (f) => { setFileText(await f.text()); setFileInfo(t("src.fileChosen", { n: f.name, k: Math.round(f.size / 1024) })); setSrc((s) => ({ ...s, m3uUrl: `file:${f.name}`, name: s.name || f.name.replace(/\.[^.]+$/, "") })); }} />
          {err && <div className="alert err" role="alert"><Icon name="alert" size={20} />{err}</div>}
          {busy && <div className="alert" role="status">{t("src.testing")}</div>}
          <div style={{ display: "flex", gap: 12 }}>
            <button className="btn" onClick={() => setStep("type")}>{t("common.back")}</button>
            <button className="btn primary lg" disabled={busy} onClick={() => void submit()}>{t("src.connect")}</button>
          </div>
        </main>
      )}

      {step === "langs" && (
        <main>
          <div>
            <div className="eyebrow">{t("lang.eyebrow")}</div>
            <h1 style={{ marginTop: 8 }}>{t("lang.title")}</h1>
            <p className="lead" style={{ marginTop: 12 }}>{t("lang.lead")}</p>
          </div>
          <div className="lang-grid" role="group" aria-label={t("common.languages")}>
            {detected.map((l) => (
              <button key={l.code} className="lang-tile" role="checkbox" aria-checked={chosen.has(l.code)} onClick={() => setChosen((c) => { const n = new Set(c); if (n.has(l.code)) n.delete(l.code); else n.add(l.code); return n; })}>
                <span className="code">{l.code === OTHER_LANG ? "…" : l.code}</span>
                <span><b style={{ display: "block", fontSize: "0.9375rem" }}>{langLabel(l.code, prefs.lang, t)}</b><span className="n">{t("lang.cats", { n: l.categories })}</span></span>
              </button>
            ))}
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
            <button className="btn" onClick={() => setChosen(chosen.size === detected.length ? new Set() : new Set(detected.map((l) => l.code)))}>{t("lang.all")}</button>
            <span className="muted grow">{t("lang.summary", { n: chosen.size, c: catsTotal })}</span>
            <button className="btn primary lg" disabled={chosen.size === 0} onClick={() => setStep("sync")}>{t("common.continue")}</button>
          </div>
        </main>
      )}

      {step === "sync" && <FirstSync name={src.name} kindLabel={src.type === "xtream" ? "Xtream Codes" : "M3U"} onRetry={() => { started.current = false; setStep("form"); }} onOpen={() => nav("/", { replace: true })} />}
    </div>
  );
}

const WEIGHTS = { live: 0.3, movie: 0.35, series: 0.2, epg: 0.15 };
const ORDER = ["categories", "live", "movie", "series", "epg", "done"] as const;

function FirstSync({ name, kindLabel, onRetry, onOpen }: { name: string; kindLabel: string; onRetry: () => void; onOpen: () => void }) {
  const t = useT();
  const { running, progress, error, cancel } = useSync();
  const idx = progress ? ORDER.indexOf(progress.phase) : 0;
  const phaseRatio = (p: keyof typeof WEIGHTS) => { const pi = ORDER.indexOf(p); return idx > pi ? 1 : idx === pi ? progress?.ratio ?? 0 : 0; };
  const pct = Math.round((phaseRatio("live") * WEIGHTS.live + phaseRatio("movie") * WEIGHTS.movie + phaseRatio("series") * WEIGHTS.series + phaseRatio("epg") * WEIGHTS.epg) * 100);
  const c = progress?.counts ?? { live: 0, movie: 0, series: 0 };
  const liveReady = idx > ORDER.indexOf("live");
  const rows: { key: keyof typeof WEIGHTS; icon: IconName; label: string; n: number }[] = [
    { key: "live", icon: "live", label: t("sync.live"), n: c.live },
    { key: "movie", icon: "movies", label: t("sync.movies"), n: c.movie },
    { key: "series", icon: "series", label: t("sync.series"), n: c.series },
    { key: "epg", icon: "guide", label: t("sync.epg"), n: 0 },
  ];
  return (
    <main style={{ maxWidth: "64rem" }}>
      <div>
        <div className="eyebrow">{t("sync.eyebrow")}</div>
        <h1 style={{ marginTop: 8 }}>{t("sync.title")}</h1>
        <p className="lead" style={{ marginTop: 12 }}>{t("sync.lead")}</p>
        <p className="muted" style={{ marginTop: 8, display: "flex", gap: 8, alignItems: "center" }}><Icon name="source" size={18} />{name} · {kindLabel}</p>
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "minmax(0,1fr) minmax(0,1.1fr)", gap: "2rem" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
            <span style={{ fontFamily: "var(--font-title)", fontWeight: 700, fontSize: "2.25rem" }}>{t("sync.percent", { p: pct })}</span>
          </div>
          <div className="bigbar" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label="Progression globale"><i style={{ width: `${pct}%` }} /></div>
          {error && <div className="alert err" role="alert"><Icon name="alert" size={20} /><span>{t("sync.failed")} — {error}</span></div>}
          <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
            <button className="btn primary lg" disabled={!liveReady} onClick={onOpen}><Icon name="play" size={18} fill />{t("sync.watchLive")}</button>
            <span className="muted" style={{ fontSize: "0.8125rem" }}>{t("sync.watchLiveHint")}</span>
          </div>
          {error && <div><button className="btn" onClick={onRetry}>{t("common.retry")}</button></div>}
          {running && <div><button className="btn sm" onClick={cancel}>{t("common.cancel")}</button></div>}
        </div>
        <div className="step-list" aria-label="Étapes">
          {rows.map((r) => {
            const ratio = phaseRatio(r.key);
            const state = ratio >= 1 ? "done" : ratio > 0 || (idx === ORDER.indexOf(r.key)) ? "active" : "";
            return (
              <div key={r.key} className={`step-item ${state}`}>
                <span className="ic">{state === "done" ? <Icon name="check" size={20} stroke={3} /> : <Icon name={r.icon} size={20} />}</span>
                <span className="grow">
                  <span style={{ display: "flex", justifyContent: "space-between", fontWeight: 700 }}><span>{r.label}</span>
                    <span className="muted mono" style={{ fontSize: "0.8125rem", fontWeight: 600 }}>{state === "done" ? (r.n ? t("sync.done", { n: r.n.toLocaleString() }) : "✓") : state === "active" ? (r.n ? t("sync.counting", { n: r.n.toLocaleString() }) : "…") : t("sync.wait")}</span></span>
                  <span className="bigbar" style={{ height: 6, display: "block", marginTop: 8 }}><i style={{ width: `${ratio * 100}%` }} /></span>
                </span>
              </div>
            );
          })}
        </div>
      </div>
      <p className="muted" style={{ fontSize: "0.875rem" }}>{t("sync.footer")}</p>
    </main>
  );
}
