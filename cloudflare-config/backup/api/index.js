// Fonction Vercel du serveur de secours (voir ../src/handler.js et docs/plan-secours-vercel.md).
// Le bundle dist/worker.mjs est produit par `npm run bundle` (node build.mjs) avant `vercel deploy`.
let cached;
async function load() {
  if (!cached) {
    try { cached = (await import("../dist/worker.mjs")).handler; }
    catch (e) { console.error("bundle du secours absent:", e?.message); cached = () => new Response(JSON.stringify({ error: "backup_not_built" }), { status: 503, headers: { "content-type": "application/json", "cache-control": "no-store" } }); }
  }
  return cached;
}
const run = async (request) => (await load())(request, process.env);

export const GET = run, POST = run, PUT = run, PATCH = run, DELETE = run, HEAD = run, OPTIONS = run;
