// DVR substitute in the browser.
//
// MediaRecorder cannot record straight off a remote HLS URL — we must pipe through MSE.
// Practical approach for the web port: capture the playing <video> element via
// captureStream() and feed that into MediaRecorder. Files are stored in the Origin Private
// File System (OPFS) so they survive reloads without filesystem permission prompts.

import type { Recording } from "@domain/model";
import { recordingRepo } from "@data/db/repositories";

export class RecordingManager {
  private active: MediaRecorder | null = null;
  private chunks: Blob[] = [];
  private current: Recording | null = null;

  isRecording(): boolean {
    return this.active != null;
  }

  async start(video: HTMLVideoElement, meta: Omit<Recording, "id" | "filePath" | "sizeBytes" | "status" | "errorMessage" | "createdAt">) {
    if (this.active) throw new Error("Already recording");
    const v = video as HTMLVideoElement & {
      captureStream?: () => MediaStream;
      mozCaptureStream?: () => MediaStream;
    };
    const stream = v.captureStream?.() ?? v.mozCaptureStream?.();
    if (!stream) throw new Error("captureStream() unsupported in this browser");

    const recorder = new MediaRecorder(stream, { mimeType: "video/webm; codecs=vp9,opus" });
    this.chunks = [];
    recorder.ondataavailable = (e) => { if (e.data.size > 0) this.chunks.push(e.data); };

    const filename = `rec-${meta.channelId}-${Date.now()}.webm`;
    this.current = {
      id: 0,
      ...meta,
      filePath: filename,
      sizeBytes: 0,
      status: "RECORDING",
      errorMessage: null,
      createdAt: Date.now(),
    };
    const id = (await recordingRepo.upsert(this.current)) as number;
    this.current = { ...this.current, id };

    recorder.start(1000);
    this.active = recorder;
  }

  async stop(): Promise<Recording | null> {
    const rec = this.active;
    const cur = this.current;
    if (!rec || !cur) return null;

    return new Promise((resolve) => {
      rec.onstop = async () => {
        const blob = new Blob(this.chunks, { type: "video/webm" });
        try {
          const root = await navigator.storage.getDirectory();
          const dir = await root.getDirectoryHandle("recordings", { create: true });
          const fh = await dir.getFileHandle(cur.filePath, { create: true });
          const w = await (fh as FileSystemFileHandle & { createWritable(): Promise<FileSystemWritableFileStream> }).createWritable();
          await w.write(blob);
          await w.close();
        } catch (e) {
          await recordingRepo.upsert({ ...cur, status: "FAILED", errorMessage: e instanceof Error ? e.message : String(e) });
          this.reset();
          resolve(null);
          return;
        }
        const finished: Recording = { ...cur, sizeBytes: blob.size, status: "COMPLETED" };
        await recordingRepo.upsert(finished);
        this.reset();
        resolve(finished);
      };
      rec.stop();
    });
  }

  private reset() {
    this.active = null;
    this.current = null;
    this.chunks = [];
  }
}

export const recordingManager = new RecordingManager();

export async function loadRecordingFile(filePath: string): Promise<string> {
  const root = await navigator.storage.getDirectory();
  const dir = await root.getDirectoryHandle("recordings");
  const fh = await dir.getFileHandle(filePath);
  const file = await fh.getFile();
  return URL.createObjectURL(file);
}
