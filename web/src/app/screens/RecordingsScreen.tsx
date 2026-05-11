import { useLiveQuery } from "dexie-react-hooks";
import { useRef, useState } from "react";
import { recordingRepo } from "@data/db/repositories";
import { loadRecordingFile } from "@data/manager/RecordingManager";

export function RecordingsScreen() {
  const recordings = useLiveQuery(() => recordingRepo.list(), []);
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [error, setError] = useState<string | null>(null);

  const play = async (filePath: string) => {
    try {
      const url = await loadRecordingFile(filePath);
      if (videoRef.current) {
        videoRef.current.src = url;
        await videoRef.current.play();
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div>
      <h2>Recordings</h2>
      <p style={{ color: "var(--fg-muted)" }}>
        Recordings capture the currently-playing video stream via MediaRecorder and store the
        result in the browser's Origin Private File System.
      </p>
      {error && <div className="banner error">{error}</div>}
      {!recordings || recordings.length === 0 ? (
        <div className="empty">No recordings yet. Start one from the Live TV player.</div>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th>Program</th><th>Channel</th><th>Start</th><th>Status</th><th>Size</th><th></th></tr></thead>
          <tbody>
            {recordings.map((r) => (
              <tr key={r.id}>
                <td>{r.programTitle}</td>
                <td>{r.channelName}</td>
                <td>{new Date(r.startTime).toLocaleString()}</td>
                <td>{r.status}</td>
                <td>{(r.sizeBytes / 1_000_000).toFixed(1)} MB</td>
                <td>
                  <button disabled={r.status !== "COMPLETED"} onClick={() => play(r.filePath)}>Play</button>
                  <button onClick={() => recordingRepo.delete(r.id)} style={{ marginLeft: 6 }}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <video ref={videoRef} controls style={{ width: "100%", marginTop: 16, background: "#000" }} />
    </div>
  );
}
