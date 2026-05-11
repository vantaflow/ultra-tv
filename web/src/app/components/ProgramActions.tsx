// Action menu for an EPG program. Lets the user:
//   - Schedule a reminder (Notifications API)
//   - Watch a past program via Xtream catch-up (if the channel supports it)

import { useState } from "react";
import { scheduleReminder } from "@data/manager/ReminderScheduler";
import { channelRepo, providerRepo } from "@data/db/repositories";
import { xtream } from "@data/providers/xtream";
import type { Program, StreamInfo } from "@domain/model";
import { streamTypeFromUrl } from "@domain/model";
import { PlayerWithControls } from "@app/components/PlayerWithControls";

interface Props {
  program: Program;
  onClose: () => void;
}

export function ProgramActions({ program, onClose }: Props) {
  const [status, setStatus] = useState<string | null>(null);
  const [stream, setStream] = useState<StreamInfo | null>(null);
  const inPast = program.endTime < Date.now();
  const inFuture = program.startTime > Date.now();
  const isLive = !inPast && !inFuture;

  const remind = async () => {
    // We need a channelId (number) — pick the first channel matching the EPG id
    const channels = await channelRepo.byEpgId(program.channelId);
    const ch = channels[0];
    if (!ch) { setStatus("No channel matches this EPG entry"); return; }
    await scheduleReminder({
      providerId: ch.providerId,
      channelId: ch.id,
      programTitle: program.title,
      startTime: program.startTime,
      endTime: program.endTime,
    });
    setStatus("Reminder scheduled — you'll be notified 2 minutes before it starts.");
  };

  const watchCatchUp = async () => {
    const channels = await channelRepo.byEpgId(program.channelId);
    const ch = channels[0];
    if (!ch) { setStatus("No channel matches this EPG entry"); return; }
    if (!ch.catchUpSupported) { setStatus("Channel does not advertise catch-up support"); return; }
    const provider = await providerRepo.get(ch.providerId);
    if (!provider) return;
    const durMin = Math.max(1, Math.round((program.endTime - program.startTime) / 60000));
    const url = xtream.catchUpUrl(
      {
        serverUrl: provider.serverUrl,
        username: provider.username,
        password: provider.password,
        userAgent: provider.userAgent || null,
        referer: provider.httpReferer || null,
      },
      ch.streamId,
      program.startTime,
      durMin,
    );
    setStream({
      url,
      title: program.title,
      headers: {},
      userAgent: provider.userAgent || null,
      streamType: streamTypeFromUrl(url),
      containerExtension: null,
      catchUpUrl: url,
      expirationTime: null,
      drmInfo: null,
    });
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal modal-wide" onClick={(e) => e.stopPropagation()}>
        <h3>{program.title}</h3>
        <div style={{ color: "var(--fg-muted)", marginBottom: 8 }}>
          {new Date(program.startTime).toLocaleString()} — {new Date(program.endTime).toLocaleString()}
        </div>
        {program.description && <p style={{ maxWidth: 720 }}>{program.description}</p>}
        {status && <div className="banner">{status}</div>}

        {stream ? (
          <div style={{ height: 480 }}>
            <PlayerWithControls stream={stream} />
          </div>
        ) : (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 8 }}>
            {isLive && <span className="banner" style={{ marginRight: "auto" }}>This program is on air right now.</span>}
            {inPast && <button onClick={watchCatchUp}>▶ Watch (catch-up)</button>}
            {inFuture && <button onClick={remind}>🔔 Remind me</button>}
            <button onClick={onClose}>Close</button>
          </div>
        )}
      </div>
    </div>
  );
}
