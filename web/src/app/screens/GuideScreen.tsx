// EPG guide grid + program search.
// Adds a category filter (same as Live TV — the guide only renders channels that pass
// the LIVE category whitelist) and an explicit "Load EPG now" button that runs the
// XMLTV fetch on demand if the provider has an `epgUrl` configured.

import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useMemo, useState } from "react";
import { channelRepo, programRepo, providerRepo } from "@data/db/repositories";
import { fetchEpg } from "@data/sync/syncEpg";
import { useProviderStore } from "@app/stores/providers";
import { useCategoryFilters } from "@app/stores/categoryFilters";
import { CategoryFilter } from "@app/components/CategoryFilter";
import { ProxyImg } from "@app/components/ProxyImg";
import { ProgramActions } from "@app/components/ProgramActions";
import type { Channel, Program } from "@domain/model";

const HOUR = 60 * 60 * 1000;
const WINDOW_MS = 6 * HOUR;
const PX_PER_HOUR = 240;

export function GuideScreen() {
  const providerId = useProviderStore((s) => s.activeProviderId);
  const [now] = useState(() => Math.floor(Date.now() / HOUR) * HOUR);
  const [query, setQuery] = useState("");
  const [loadingEpg, setLoadingEpg] = useState(false);
  const [epgMessage, setEpgMessage] = useState<string | null>(null);
  const [selectedProgram, setSelectedProgram] = useState<Program | null>(null);

  const loadFilters = useCategoryFilters((s) => s.load);
  const allowed = useCategoryFilters((s) => providerId == null ? null : s.allowed[`${providerId}:LIVE`]);

  useEffect(() => { if (providerId != null) void loadFilters(providerId); }, [providerId, loadFilters]);

  const channels = useLiveQuery<Channel[]>(
    async () => providerId == null ? [] : channelRepo.forProvider(providerId),
    [providerId],
  );
  const programs = useLiveQuery<Program[]>(
    async () => programRepo.inWindow(now - WINDOW_MS, now + WINDOW_MS),
    [now],
  );

  const byEpgId = useMemo(() => {
    const map = new Map<string, Program[]>();
    for (const p of programs ?? []) {
      const list = map.get(p.channelId) ?? [];
      list.push(p);
      map.set(p.channelId, list);
    }
    return map;
  }, [programs]);

  const visibleChannels = useMemo(() => {
    if (!channels) return [];
    let list = channels;
    if (allowed != null) {
      const set = new Set(allowed);
      list = list.filter((c) => c.categoryId != null && set.has(c.categoryId));
    }
    const lc = query.toLowerCase();
    if (lc) {
      list = list.filter((c) => {
        if (c.name.toLowerCase().includes(lc)) return true;
        const progs = c.epgChannelId ? byEpgId.get(c.epgChannelId) : undefined;
        return progs?.some((p) => p.title.toLowerCase().includes(lc) || p.description.toLowerCase().includes(lc));
      });
    }
    return list;
  }, [channels, allowed, byEpgId, query]);

  const loadEpg = async () => {
    if (providerId == null) return;
    setLoadingEpg(true);
    setEpgMessage(null);
    try {
      const provider = await providerRepo.get(providerId);
      if (!provider) throw new Error("Provider not found");
      if (!provider.epgUrl) {
        setEpgMessage("No EPG URL set. Add one in Settings → edit this provider's epgUrl.");
        return;
      }
      const count = await fetchEpg(provider.epgUrl, providerId, provider.userAgent || null, provider.httpReferer || null);
      setEpgMessage(`Loaded ${count.toLocaleString()} programs`);
    } catch (e) {
      setEpgMessage(`Error: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoadingEpg(false);
    }
  };

  if (providerId == null) return <div className="empty">Add a provider first.</div>;
  if (!channels) return <div className="empty">Loading…</div>;
  if (channels.length === 0) return <div className="empty">No channels.</div>;

  const totalWidth = (WINDOW_MS * 2 / HOUR) * PX_PER_HOUR;

  return (
    <div>
      <div style={{ display: "flex", gap: 12, marginBottom: 12, alignItems: "center", flexWrap: "wrap" }}>
        <h2 style={{ margin: 0 }}>Guide</h2>
        <CategoryFilter providerId={providerId} contentType="LIVE" label="Channels" />
        <input placeholder="Search channel or program…" value={query} onChange={(e) => setQuery(e.target.value)} />
        <button onClick={loadEpg} disabled={loadingEpg}>
          {loadingEpg ? "Loading EPG…" : (programs && programs.length > 0 ? "Reload EPG" : "Load EPG now")}
        </button>
        {epgMessage && <span style={{ color: "var(--fg-muted)" }}>{epgMessage}</span>}
        <span style={{ color: "var(--fg-muted)", marginLeft: "auto" }}>
          {programs?.length ?? 0} programs · {visibleChannels.length} channels
        </span>
      </div>
      {(!programs || programs.length === 0) && (
        <div className="banner">No EPG data loaded yet. Click "Load EPG now" — requires an EPG URL on the provider.</div>
      )}
      <div className="guide-scroll">
        <div className="guide-timebar" style={{ width: totalWidth }}>
          {Array.from({ length: (WINDOW_MS * 2) / HOUR }, (_, i) => {
            const t = now - WINDOW_MS + i * HOUR;
            const d = new Date(t);
            return (
              <div key={i} className="guide-hour" style={{ width: PX_PER_HOUR }}>
                {d.getHours().toString().padStart(2, "0")}:00
              </div>
            );
          })}
        </div>
        {visibleChannels.slice(0, 300).map((c) => {
          const progs = c.epgChannelId ? byEpgId.get(c.epgChannelId) ?? [] : [];
          return (
            <div key={c.id} className="guide-row" style={{ width: totalWidth }}>
              <div className="guide-channel">
                <ProxyImg src={c.logoUrl} alt="" />
                <span>{c.name}</span>
              </div>
              {progs.map((p) => {
                const left = Math.max(0, (p.startTime - (now - WINDOW_MS)) / HOUR) * PX_PER_HOUR;
                const width = Math.max(40, ((p.endTime - p.startTime) / HOUR) * PX_PER_HOUR);
                const active = now >= p.startTime && now < p.endTime;
                return (
                  <div
                    key={p.id}
                    className={`guide-prog${active ? " active" : ""}`}
                    style={{ left, width, cursor: "pointer" }}
                    title={`${p.title}\n${new Date(p.startTime).toLocaleTimeString()} – ${new Date(p.endTime).toLocaleTimeString()}\n${p.description}`}
                    onClick={() => setSelectedProgram(p)}
                  >
                    {p.title}
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>
      {selectedProgram && <ProgramActions program={selectedProgram} onClose={() => setSelectedProgram(null)} />}
    </div>
  );
}
