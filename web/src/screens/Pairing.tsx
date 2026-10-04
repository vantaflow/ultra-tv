// Appairage avec le compte cloud (maquette Appairage) : étapes à gauche, code en cases à droite.
import { useCallback, useEffect, useRef, useState } from "react";
import { useT } from "@/i18n";
import { bridge } from "@/net/transport";
import { usePrefs } from "@/state/prefs";
import { Icon } from "@/ui/Icon";
import { RateLimitedError, groupPairingCode, runPairing } from "@/cloud/client";
import { defaultDeviceName, saveDeviceName, storeToken, syncCloud, useCloud, type SyncSummary } from "@/cloud/service";

export function PairingView({ onDone, onCancel }: { onDone: (s: SyncSummary) => void; onCancel: () => void }) {
  const t = useT();
  const { worker, deviceName } = useCloud();
  const [code, setCode] = useState<string | null>(null);
  const [left, setLeft] = useState(0);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const ctrl = useRef<AbortController | null>(null);
  const lang = usePrefs((s) => s.lang);

  const start = useCallback(() => {
    ctrl.current?.abort();
    const c = new AbortController();
    ctrl.current = c;
    setCode(null); setErr(null); setBusy(false);
    void runPairing(worker, deviceName || defaultDeviceName(), (e) => {
      if (c.signal.aborted) return;
      if (e.type === "code") { setCode(e.code); setLeft(e.expiresInSec); }
      else if (e.type === "expired") { setCode(null); setErr(t("cloud.expired")); }
      else if (e.type === "failed") { setErr(e.error instanceof RateLimitedError ? t("cloud.rateLimited") : t("cloud.failed")); }
      else if (e.type === "paired") {
        setBusy(true);
        void (async () => {
          await storeToken(e.token, e.deviceId);
          await saveDeviceName(deviceName || defaultDeviceName());
          try { onDone(await syncCloud({ force: true })); } catch { setErr(t("cloud.failed")); setBusy(false); }
        })();
      }
    }, c.signal);
  }, [worker, deviceName, t, onDone]);

  useEffect(() => { start(); return () => ctrl.current?.abort(); }, [start]);
  useEffect(() => {
    if (!code) return;
    const iv = setInterval(() => setLeft((l) => Math.max(0, l - 1)), 1000);
    return () => clearInterval(iv);
  }, [code]);

  const host = worker.replace(/^https?:\/\//, "");
  const mm = `${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`;
  return (
    <main style={{ maxWidth: "70rem" }} lang={lang}>
      <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1.1fr) minmax(0, 1fr)", gap: "3rem", alignItems: "center" }}>
        <section style={{ display: "flex", flexDirection: "column", gap: "1.5rem" }}>
          <div className="eyebrow">{t("cloud.eyebrow")}</div>
          <h1>{t("cloud.heading")}</h1>
          <ol style={{ listStyle: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: "1rem" }}>
            {[t("cloud.step1"), t("cloud.step2"), t("cloud.step3")].map((s, i) => (
              <li key={i} style={{ display: "flex", gap: "0.875rem", alignItems: "center", fontSize: "1.0625rem" }}>
                <span className="avatar" style={{ width: 32, height: 32, borderRadius: 16, background: "var(--surface-3)", color: "var(--text)", fontSize: "0.875rem" }}>{i + 1}</span>
                <span>{s}{i === 0 && <> · <strong>{host}</strong></>}</span>
              </li>
            ))}
          </ol>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
            <button className="btn primary" onClick={() => void bridge()?.openExternal?.(worker)}><Icon name="link" size={18} />{t("cloud.openDash")}</button>
            <button className="btn" onClick={start}>{t("cloud.newCode")}</button>
            <button className="btn" onClick={onCancel}>{t("common.cancel")}</button>
          </div>
          {err && <div className="alert err" role="alert"><Icon name="alert" size={20} />{err}</div>}
        </section>
        <section aria-label={t("cloud.yourCode")} style={{ display: "flex", flexDirection: "column", gap: "1.25rem", alignItems: "center" }}>
          <div className="eyebrow">{t("cloud.yourCode")}</div>
          <div style={{ display: "flex", gap: 8, direction: "ltr" }} aria-live="polite" aria-label={code ?? ""}>
            {(code ? groupPairingCode(code) : "········").split("").map((ch, i) => ch === "-"
              ? <span key={i} style={{ fontSize: "2.5rem", fontWeight: 700, color: "var(--text-5)", alignSelf: "center" }}>-</span>
              : <span key={i} className={code ? "" : "skeleton"} style={{ width: "3.25rem", height: "4.5rem", borderRadius: "1rem", background: code ? "var(--surface-2)" : undefined, display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "var(--font-title)", fontSize: "2.5rem", fontWeight: 700 }}>{code ? ch : ""}</span>)}
          </div>
          <div className="muted" style={{ display: "flex", gap: 10, alignItems: "center", fontWeight: 600 }}>
            <span className="sync-dot" />{busy ? t("sync.background") : code ? t("cloud.waiting", { t: mm }) : t("common.loading")}
          </div>
          <div className="muted">{t("cloud.device", { n: deviceName || defaultDeviceName() })}</div>
        </section>
      </div>
    </main>
  );
}
