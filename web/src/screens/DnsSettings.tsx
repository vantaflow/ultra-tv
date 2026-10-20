// Réglages › Lecture › Réseau : DNS personnalisé (application de bureau uniquement, masqué dans un navigateur).
import { useEffect, useState, type ReactNode } from "react";
import { useT } from "@/i18n";
import {
  DOH_PRESETS, PROVIDER_NAMES, chromiumScope, dnsBridge, dnsIssues, normalizeDns, pushDns, sourceHost, testOutcome,
  type DnsMode, type DnsSettings as Dns, type DohPreset,
} from "@/net/dns";
import { usePrefs } from "@/state/prefs";
import { useActiveSource } from "@/state/sources";
import { Seg } from "@/ui/common";

type PrefRow = (p: { label: string; desc?: string; children: ReactNode }) => ReactNode;

export function DnsSettings({ Pref }: { Pref: PrefRow }) {
  const t = useT();
  const stored = normalizeDns(usePrefs((s) => s.dns));
  const setPrefs = usePrefs((s) => s.set);
  const source = useActiveSource();
  const [draft, setDraft] = useState<Dns>(stored);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const key = JSON.stringify(stored);
  useEffect(() => { setDraft(normalizeDns(JSON.parse(key))); }, [key]);
  const b = dnsBridge();
  if (!b) return null;

  const issues = dnsIssues(draft);
  /** Enregistre (préférences locales) puis applique dans le processus principal. */
  const commit = (next: Dns) => {
    setDraft(next);
    setMsg(null);
    setPrefs({ dns: next });
    void pushDns(next);
  };
  const host = sourceHost(source);
  const modeLabel = (m: DnsMode) => m === "system" ? t("dns.system") : m === "doh" ? t("dns.doh") : m === "custom" ? t("dns.custom") : PROVIDER_NAMES[m];
  const presetLabel = (p: DohPreset) => p === "custom" ? t("dns.custom") : PROVIDER_NAMES[p];
  const scope = chromiumScope(draft);

  return (
    <>
      <div className="eyebrow">{t("dns.group")}</div>
      <Pref label={t("dns.server")} desc={draft.mode === "system" ? undefined : t("dns.scope")}>
        <Seg<DnsMode> label={t("dns.server")} value={draft.mode} onChange={(mode) => commit({ ...draft, mode })}
          options={(["system", "google", "cloudflare", "quad9", "doh", "custom"] as const).map((v) => ({ v, label: modeLabel(v) }))} />
      </Pref>
      {draft.mode === "doh" && (
        <Pref label={t("dns.dohProvider")}>
          <Seg<DohPreset> label={t("dns.dohProvider")} value={draft.dohPreset} onChange={(dohPreset) => commit({ ...draft, dohPreset })}
            options={DOH_PRESETS.map((v) => ({ v, label: presetLabel(v) }))} />
        </Pref>
      )}
      {draft.mode === "doh" && draft.dohPreset === "custom" && (
        <Pref label={t("dns.dohUrl")} desc={issues.dohUrl && draft.dohUrl ? t("dns.invalidDohUrl") : t("dns.dohUrlD")}>
          <input className="input" style={{ width: "22rem" }} value={draft.dohUrl} spellCheck={false} placeholder="https://" aria-label={t("dns.dohUrl")}
            aria-invalid={issues.dohUrl && !!draft.dohUrl} onChange={(e) => setDraft({ ...draft, dohUrl: e.target.value })} onBlur={() => commit({ ...draft, dohUrl: draft.dohUrl.trim() })} />
        </Pref>
      )}
      {draft.mode === "custom" && (
        <>
          <Pref label={t("dns.primary")} desc={issues.primary && draft.primary ? t("dns.invalidIp") : t("dns.serversD")}>
            <input className="input mono" style={{ width: "16rem" }} value={draft.primary} spellCheck={false} aria-label={t("dns.primary")}
              aria-invalid={issues.primary && !!draft.primary} onChange={(e) => setDraft({ ...draft, primary: e.target.value })} onBlur={() => commit({ ...draft, primary: draft.primary.trim() })} />
          </Pref>
          <Pref label={t("dns.secondary")} desc={issues.secondary ? t("dns.invalidIp") : undefined}>
            <input className="input mono" style={{ width: "16rem" }} value={draft.secondary} spellCheck={false} aria-label={t("dns.secondary")}
              aria-invalid={issues.secondary} onChange={(e) => setDraft({ ...draft, secondary: e.target.value })} onBlur={() => commit({ ...draft, secondary: draft.secondary.trim() })} />
          </Pref>
        </>
      )}
      {scope !== "full" && <p className="muted" style={{ fontSize: "0.8125rem", margin: 0 }}>{scope === "proxy-only" ? t("dns.customNote") : t("dns.dohNote")}</p>}
      <Pref label={t("dns.test")} desc={msg ?? (host ? t("dns.testD") : t("dns.noHost"))}>
        <button className="btn" disabled={busy || !host} onClick={async () => {
          if (!host) return;
          setBusy(true); setMsg(t("dns.testing"));
          try {
            const o = testOutcome(await b.testDns(host));
            setMsg(t(o.key, o.vars));
          } catch { setMsg(t("dns.testFailed")); } finally { setBusy(false); }
        }}>{busy ? t("dns.testing") : t("dns.test")}</button>
      </Pref>
    </>
  );
}
