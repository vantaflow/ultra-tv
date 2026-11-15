// Essai de l'adaptateur sur la vraie base Turso (clés temporaires « __smoke: », effacées à la fin).
import { connect } from "@tursodatabase/serverless";
import { TursoD1, TursoKV } from "../src/turso.js";
const db = new TursoD1(connect({ url: process.env.TURSO_URL, authToken: process.env.TURSO_AUTH_TOKEN }));
const kv = new TursoKV(db);
const t0 = Date.now();
await kv.put("__smoke:a", "1", { expirationTtl: 120, metadata: { x: 1 } });
await kv.put("__smoke:b", JSON.stringify({ ok: true }));
const a = await kv.get("__smoke:a"), b = await kv.get("__smoke:b", "json"), m = await kv.getWithMetadata("__smoke:a");
const l = await kv.list({ prefix: "__smoke:" });
const r = await db.batch([db.prepare("INSERT INTO kv_docs (k, v, exp) VALUES (?, ?, NULL)").bind("__smoke:d", "v"), db.prepare("SELECT count(*) AS n FROM kv_docs WHERE k = ?").bind("__smoke:d")]);
const first = await db.prepare("SELECT v FROM kv_docs WHERE k = ?").bind("__smoke:d").first("v");
await kv.delete("__smoke:a"); await kv.delete("__smoke:b"); await db.prepare("DELETE FROM kv_docs WHERE k = ?").bind("__smoke:d").run();
const gone = (await kv.list({ prefix: "__smoke:" })).keys.length;
console.log({ a, b, meta: m.metadata, list: l.keys.map((k) => k.name), batchCount: r[1].results[0].n, first, gone, ms: Date.now() - t0 });
