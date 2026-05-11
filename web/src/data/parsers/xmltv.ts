// Minimal XMLTV parser used for EPG ingestion.
// Streams a DOMParser pass over the document; sufficient for guides up to a few MB.
// For very large guides we'll move to a SAX parser (sax-wasm) later.

import type { Program } from "@domain/model";

const XMLTV_TIME_RE = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-]\d{4}))?$/;

function parseXmltvDate(raw: string | null): number {
  if (!raw) return 0;
  const m = raw.match(XMLTV_TIME_RE);
  if (!m) return 0;
  const [, y, mo, d, h, mi, s, tz] = m;
  let iso = `${y}-${mo}-${d}T${h}:${mi}:${s}`;
  if (tz) iso += `${tz.slice(0, 3)}:${tz.slice(3)}`;
  else iso += "Z";
  const t = Date.parse(iso);
  return Number.isFinite(t) ? t : 0;
}

export interface XmltvChannelMeta {
  id: string;
  displayName: string;
  icon: string | null;
}

export interface XmltvResult {
  channels: XmltvChannelMeta[];
  programs: Program[];
}

export function parseXmltv(xml: string, providerId = 0): XmltvResult {
  const doc = new DOMParser().parseFromString(xml, "application/xml");
  if (doc.querySelector("parsererror")) {
    throw new Error("Invalid XMLTV document");
  }

  const channels: XmltvChannelMeta[] = [];
  for (const el of Array.from(doc.getElementsByTagName("channel"))) {
    const id = el.getAttribute("id") ?? "";
    if (!id) continue;
    channels.push({
      id,
      displayName: el.querySelector("display-name")?.textContent?.trim() ?? id,
      icon: el.querySelector("icon")?.getAttribute("src") ?? null,
    });
  }

  const programs: Program[] = [];
  let nextId = 1;
  for (const el of Array.from(doc.getElementsByTagName("programme"))) {
    const channelId = el.getAttribute("channel") ?? "";
    if (!channelId) continue;
    programs.push({
      id: nextId++,
      channelId,
      title: el.querySelector("title")?.textContent?.trim() ?? "",
      description: el.querySelector("desc")?.textContent?.trim() ?? "",
      startTime: parseXmltvDate(el.getAttribute("start")),
      endTime: parseXmltvDate(el.getAttribute("stop")),
      lang: el.querySelector("title")?.getAttribute("lang") ?? "",
      rating: el.querySelector("rating value")?.textContent?.trim() ?? null,
      imageUrl: el.querySelector("icon")?.getAttribute("src") ?? null,
      genre: el.querySelector("category")?.textContent?.trim() ?? null,
      category: el.querySelector("category")?.textContent?.trim() ?? null,
      hasArchive: false,
      isNowPlaying: false,
      providerId,
    });
  }

  return { channels, programs };
}
