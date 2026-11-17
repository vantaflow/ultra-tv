// Connexion Plex par code PIN (plex.tv/link) dans le formulaire de source : affiche le code, attend sa validation, liste les
// serveurs du compte, choisit la meilleure connexion et renseigne la source (adresse, identifiant de machine, jeton du serveur).
// Le jeton ne quitte jamais cet écran en clair : il part dans la source, chiffrée à l'enregistrement.
import { useEffect, useRef, useState } from "react";
import { useT } from "@/i18n";
import type { Source } from "@/db/types";
import { awaitPlexToken, PlexAccount, PLEX_LINK_URL, type PlexServer } from "@/net/plex";
import { bridge, currentTransport } from "@/net/transport";

type Phase =
  | { k: "idle" }
  | { k: "waiting"; code: string }
  | { k: "choosing"; servers: PlexServer[] }
  | { k: "connecting" }
  | { k: "error"; msg: "expired" | "failed" | "none" | "unreachable" };

const ERR_KEY = { expired: "src.plexExpired", failed: "src.plexFailed", none: "src.plexNoServer", unreachable: "src.plexUnreachable" } as const;

export function PlexLinkField({ value, onChange }: { value: Source; onChange: (s: Source) => void }) {
  const t = useT();
  const [phase, setPhase] = useState<Phase>({ k: "idle" });
  const abort = useRef<AbortController | null>(null);
  // Le formulaire peut être modifié pendant l'attente : toujours la valeur courante au moment de renseigner la source.
  const latest = useRef(value);
  latest.current = value;
  useEffect(() => () => abort.current?.abort(), []);

  const choose = async (acc: PlexAccount, s: PlexServer) => {
    setPhase({ k: "connecting" });
    try {
      const conn = await acc.bestConnection(s);
      if (!conn) { setPhase({ k: "error", msg: "unreachable" }); return; }
      onChange({ ...latest.current, server: conn.uri, username: s.machineId, password: s.accessToken, name: latest.current.name.trim() ? latest.current.name : s.name });
      setPhase({ k: "idle" });
    } catch (e) {
      if ((e as Error).name !== "AbortError") setPhase({ k: "error", msg: "unreachable" });
    }
  };

  const start = async () => {
    abort.current?.abort();
    const ctrl = new AbortController();
    abort.current = ctrl;
    try {
      const acc = new PlexAccount(await currentTransport(), undefined, ctrl.signal);
      const res = await awaitPlexToken({ create: () => acc.createPin(), poll: (id) => acc.pollPin(id), onCode: (code) => setPhase({ k: "waiting", code }), signal: ctrl.signal });
      if ("expired" in res) { setPhase({ k: "error", msg: "expired" }); return; }
      setPhase({ k: "connecting" });
      const servers = await acc.servers(res.token);
      if (!servers.length) setPhase({ k: "error", msg: "none" });
      else if (servers.length === 1) await choose(acc, servers[0]!);
      else setPhase({ k: "choosing", servers });
      chooser.current = (s) => void choose(acc, s);
    } catch (e) {
      if ((e as Error).name !== "AbortError") setPhase({ k: "error", msg: "failed" });
    }
  };
  const chooser = useRef<(s: PlexServer) => void>(() => undefined);

  const open = () => { const b = bridge(); if (b?.openExternal) void b.openExternal(PLEX_LINK_URL); else window.open(PLEX_LINK_URL, "_blank", "noopener"); };
  const linked = !!value.password && !!value.server;
  const host = (() => { try { return new URL(value.server).host; } catch { return value.server; } })();

  return (
    <div className="field full" style={{ gap: 10 }}>
      {phase.k === "waiting" && (
        <>
          <span>{t("src.plexOpen")}</span>
          <b style={{ fontSize: 40, letterSpacing: 6 }} data-testid="plex-code">{phase.code}</b>
          <span className="muted">{t("src.plexWaiting")}</span>
          <div style={{ display: "flex", gap: 8 }}>
            <button type="button" className="btn sm" onClick={open}>{PLEX_LINK_URL.replace("https://", "")}</button>
            <button type="button" className="btn sm" onClick={() => { abort.current?.abort(); setPhase({ k: "idle" }); }}>{t("common.cancel")}</button>
          </div>
        </>
      )}
      {phase.k === "choosing" && (
        <>
          <span>{t("src.plexPick")}</span>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {phase.servers.map((s) => <button key={s.machineId} type="button" className="btn" onClick={() => chooser.current(s)}>{s.name}</button>)}
          </div>
        </>
      )}
      {phase.k === "connecting" && <span className="muted">{t("src.plexConnecting")}</span>}
      {phase.k === "error" && <span role="alert" style={{ color: "var(--danger, #e5484d)" }}>{t(ERR_KEY[phase.msg])}</span>}
      {(phase.k === "idle" || phase.k === "error") && (
        <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
          <button type="button" className="btn" onClick={() => void start()}>{linked ? t("src.plexRelink") : t("src.plexLink")}</button>
          {linked && <span className="muted">{t("src.plexLinked", { name: host })}</span>}
        </div>
      )}
      <span className="muted">{t("src.plexHint")}</span>
    </div>
  );
}
