import { useState } from "react";
import { useT } from "@/i18n";
import type { Source } from "@/db/types";
import { Icon } from "@/ui/Icon";

export type SourceKind = "xtream" | "m3u-link" | "m3u-file";

export function SourceForm({ kind, value, onChange, fileInfo, onPickFile }: {
  kind: SourceKind; value: Source; onChange: (s: Source) => void; fileInfo?: string; onPickFile?: (f: File) => void;
}) {
  const t = useT();
  const [adv, setAdv] = useState(false);
  const set = (p: Partial<Source>) => onChange({ ...value, ...p });
  return (
    <div className="form-card">
      <div className="field full">
        <label htmlFor="f-name">{t("src.name")}</label>
        <input id="f-name" className="input" value={value.name} placeholder={t("src.namePh")} onChange={(e) => set({ name: e.target.value })} />
      </div>
      {kind === "xtream" && (
        <>
          <div className="field full">
            <label htmlFor="f-server">{t("src.server")}</label>
            <input id="f-server" className="input" value={value.server} placeholder={t("src.serverPh")} autoCapitalize="off" spellCheck={false} onChange={(e) => set({ server: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="f-user">{t("src.username")}</label>
            <input id="f-user" className="input" value={value.username} autoComplete="off" spellCheck={false} onChange={(e) => set({ username: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="f-pass">{t("src.password")}</label>
            <input id="f-pass" className="input" type="password" value={value.password} autoComplete="off" onChange={(e) => set({ password: e.target.value })} />
          </div>
        </>
      )}
      {kind === "m3u-link" && (
        <div className="field full">
          <label htmlFor="f-m3u">{t("src.m3uUrl")}</label>
          <input id="f-m3u" className="input" value={value.m3uUrl} placeholder="http://…/playlist.m3u8" autoCapitalize="off" spellCheck={false} onChange={(e) => set({ m3uUrl: e.target.value })} />
        </div>
      )}
      {kind === "m3u-file" && (
        <div className="field full">
          <span className="lbl">{t("src.m3uFile")}</span>
          <label className="btn" style={{ alignSelf: "flex-start" }}>
            <Icon name="file" size={18} />{fileInfo || t("src.pickFile")}
            <input type="file" accept=".m3u,.m3u8,.txt,audio/x-mpegurl,application/vnd.apple.mpegurl" className="sr-only" onChange={(e) => { const f = e.target.files?.[0]; if (f) onPickFile?.(f); }} />
          </label>
        </div>
      )}
      <div className="full">
        <button type="button" className="btn sm" onClick={() => setAdv(!adv)} aria-expanded={adv}>{t("src.advanced")} {adv ? "▴" : "▾"}</button>
      </div>
      {adv && (
        <>
          <div className="field full">
            <label htmlFor="f-epg">{t("src.epg")}</label>
            <input id="f-epg" className="input" value={value.epgUrl} spellCheck={false} onChange={(e) => set({ epgUrl: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="f-ua">{t("src.userAgent")}</label>
            <input id="f-ua" className="input" value={value.userAgent} spellCheck={false} onChange={(e) => set({ userAgent: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="f-ref">{t("src.referer")}</label>
            <input id="f-ref" className="input" value={value.referer} spellCheck={false} onChange={(e) => set({ referer: e.target.value })} />
          </div>
        </>
      )}
    </div>
  );
}
