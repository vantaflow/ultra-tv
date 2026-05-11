// Add/Edit provider form. Lets the user test credentials before persisting and exposes
// every field the data model supports (server URL, auth, M3U URL, EPG URL, UA, Referer).

import { useEffect, useState } from "react";
import { testProvider, type ProviderTestResult } from "@data/sync/testProvider";
import { useProviderStore } from "@app/stores/providers";
import type { Provider, ProviderType } from "@domain/model";

interface Props {
  initial?: Provider;
  onClose: () => void;
}

const UA_PRESETS = [
  "VLC/3.0.20 LibVLC/3.0.20",
  "TiviMate/4.6.0 (Android)",
  "Lavf/58.76.100",
  "Mozilla/5.0 (Linux; Android 10; SmartTV) AppleWebKit/537.36",
];

export function ProviderEditor({ initial, onClose }: Props) {
  const isEdit = !!initial;
  const addProvider = useProviderStore((s) => s.addProvider);
  const updateProvider = useProviderStore((s) => s.updateProvider);
  const syncProvider = useProviderStore((s) => s.syncProvider);

  const [type, setType] = useState<ProviderType>(initial?.type ?? "XTREAM_CODES");
  const [name, setName] = useState(initial?.name ?? "");
  const [serverUrl, setServerUrl] = useState(initial?.serverUrl ?? "");
  const [username, setUsername] = useState(initial?.username ?? "");
  const [password, setPassword] = useState(initial?.password ?? "");
  const [m3uUrl, setM3uUrl] = useState(initial?.m3uUrl ?? "");
  const [epgUrl, setEpgUrl] = useState(initial?.epgUrl ?? "");
  const [userAgent, setUserAgent] = useState(initial?.userAgent ?? "");
  const [httpReferer, setHttpReferer] = useState(initial?.httpReferer ?? "");
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<ProviderTestResult | null>(null);
  const [saving, setSaving] = useState(false);

  // Reset test result whenever inputs change
  useEffect(() => { setTestResult(null); }, [type, serverUrl, username, password, m3uUrl, userAgent, httpReferer]);

  const onTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const result = await testProvider({ type, serverUrl, username, password, m3uUrl, userAgent, httpReferer });
      setTestResult(result);
    } finally {
      setTesting(false);
    }
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    try {
      if (isEdit && initial) {
        await updateProvider(initial.id, {
          name: name || initial.name,
          type,
          serverUrl,
          username,
          password,
          m3uUrl,
          epgUrl,
          userAgent,
          httpReferer,
        });
        // Re-sync only if the user explicitly hits "Save & Resync"
        // (handled by callers when desired)
      } else {
        await addProvider({ name: name || "Provider", type, serverUrl, username, password, m3uUrl, epgUrl, userAgent, httpReferer });
      }
      onClose();
    } finally {
      setSaving(false);
    }
  };

  const onSaveAndResync = async () => {
    if (!isEdit || !initial) return;
    setSaving(true);
    try {
      await updateProvider(initial.id, { type, serverUrl, username, password, m3uUrl, epgUrl, userAgent, httpReferer, name: name || initial.name });
      await syncProvider(initial.id);
      onClose();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="modal modal-wide" onClick={(e) => e.stopPropagation()} onSubmit={onSubmit}>
        <h3>{isEdit ? `Edit "${initial?.name}"` : "Add provider"}</h3>

        <div className="form-row">
          <label>Type</label>
          <select value={type} onChange={(e) => setType(e.target.value as ProviderType)}>
            <option value="XTREAM_CODES">Xtream Codes</option>
            <option value="M3U">M3U URL</option>
            <option value="STALKER_PORTAL">Stalker Portal</option>
          </select>
        </div>
        <div className="form-row">
          <label>Name</label>
          <input value={name} onChange={(e) => setName(e.target.value)} required placeholder="My provider" />
        </div>

        {type === "XTREAM_CODES" && (
          <>
            <div className="form-row"><label>Server URL</label><input value={serverUrl} onChange={(e) => setServerUrl(e.target.value)} placeholder="http://example.com:8080" required /></div>
            <div className="form-row"><label>Username</label><input value={username} onChange={(e) => setUsername(e.target.value)} required autoComplete="off" /></div>
            <div className="form-row"><label>Password</label><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required autoComplete="off" /></div>
          </>
        )}
        {type === "M3U" && (
          <div className="form-row"><label>M3U URL</label><input value={m3uUrl} onChange={(e) => setM3uUrl(e.target.value)} required placeholder="http://example.com/get.php?…" /></div>
        )}
        {type === "STALKER_PORTAL" && (
          <>
            <div className="form-row"><label>Portal URL</label><input value={serverUrl} onChange={(e) => setServerUrl(e.target.value)} required placeholder="http://portal.example.com" /></div>
            <div className="form-row"><label>MAC Address</label><input value={username} onChange={(e) => setUsername(e.target.value)} required placeholder="00:1A:79:XX:XX:XX" /></div>
          </>
        )}

        <div className="form-row">
          <label>EPG URL (optional XMLTV)</label>
          <input value={epgUrl} onChange={(e) => setEpgUrl(e.target.value)} placeholder="https://iptv-org.../epg.xml.gz" />
        </div>
        <div className="form-row">
          <label>User-Agent (optional)</label>
          <input list="ua-presets-editor" value={userAgent} onChange={(e) => setUserAgent(e.target.value)} placeholder="VLC/3.0.20 LibVLC/3.0.20" />
          <datalist id="ua-presets-editor">{UA_PRESETS.map((u) => <option key={u} value={u} />)}</datalist>
        </div>
        <div className="form-row">
          <label>HTTP Referer (optional)</label>
          <input value={httpReferer} onChange={(e) => setHttpReferer(e.target.value)} placeholder="https://example.com/" />
        </div>

        <div style={{ display: "flex", gap: 8, alignItems: "center", marginTop: 8 }}>
          <button type="button" onClick={onTest} disabled={testing}>
            {testing ? "Testing…" : "Test connection"}
          </button>
          {testResult && (
            <span style={{ color: testResult.ok ? "var(--accent)" : "var(--danger)" }}>
              {testResult.ok ? "✓ " : "✗ "}{testResult.message}
            </span>
          )}
        </div>

        {testResult?.details && (
          <div className="banner" style={{ fontSize: 13, marginTop: 8 }}>
            {Object.entries(testResult.details).map(([k, v]) => (
              <div key={k}><strong>{k}:</strong> {v}</div>
            ))}
          </div>
        )}

        <div style={{ display: "flex", gap: 8, marginTop: 16, justifyContent: "flex-end", flexWrap: "wrap" }}>
          <button type="button" onClick={onClose}>Cancel</button>
          {isEdit && <button type="button" onClick={onSaveAndResync} disabled={saving}>Save & Resync</button>}
          <button type="submit" disabled={saving}>{isEdit ? "Save" : (saving ? "Adding…" : "Add provider")}</button>
        </div>
      </form>
    </div>
  );
}
