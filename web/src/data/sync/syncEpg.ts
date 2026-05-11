// EPG ingestion. Pulls an XMLTV document (raw XML or gzipped via DecompressionStream)
// and persists Programs scoped to the given provider.

import { parseXmltv } from "@data/parsers/xmltv";
import { programRepo } from "@data/db/repositories";
import { proxiedFetch } from "@data/net/proxy";

export async function fetchEpg(url: string, providerId: number, userAgent: string | null = null, referer: string | null = null): Promise<number> {
  const res = await proxiedFetch(url, { userAgent, referer });
  if (!res.ok) throw new Error(`EPG fetch failed: ${res.status}`);

  let text: string;
  const ct = res.headers.get("content-type") ?? "";
  const isGz = url.endsWith(".gz") || ct.includes("gzip");
  if (isGz && "DecompressionStream" in globalThis) {
    const ds = new DecompressionStream("gzip");
    const decompressed = res.body?.pipeThrough(ds);
    text = await new Response(decompressed).text();
  } else {
    text = await res.text();
  }

  const parsed = parseXmltv(text, providerId);
  const programs = parsed.programs.map((p) => ({ ...p, providerId }));

  await programRepo.clearProvider(providerId);
  await programRepo.bulkAdd(programs);
  return programs.length;
}
