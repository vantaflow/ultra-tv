/**
 * Ultra TV — MAC-based remote config Worker.
 *
 * Routes:
 *   GET  /api/config/:mac        → JSON config for that MAC (no auth)
 *   POST /api/config/:mac        → write JSON config (Basic admin auth)
 *   DELETE /api/config/:mac      → remove config (Basic admin auth)
 *   GET  /                       → dashboard HTML (Basic admin auth)
 *   GET  /api/list               → list of MACs (Basic admin auth)
 *
 * Auth: HTTP Basic with username "admin" and password = env.ADMIN_PASSWORD.
 * KV:   `CONFIG` namespace stores the JSON document under the MAC string key
 *       (lowercased, colons preserved e.g. "aa:bb:cc:dd:ee:ff").
 *
 * App side: each TV computes a stable pseudo-MAC from Android device ID and
 * fetches /api/config/:mac. No PII is collected — the MAC is derived and
 * device-local.
 */

const corsHeaders = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET,POST,DELETE,OPTIONS",
  "access-control-allow-headers": "authorization,content-type",
};

function json(body, init = {}) {
  return new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: { "content-type": "application/json; charset=utf-8", ...corsHeaders, ...(init.headers || {}) },
  });
}

function html(body, init = {}) {
  return new Response(body, {
    status: init.status ?? 200,
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store", ...corsHeaders },
  });
}

function unauthorized() {
  return new Response("Auth required", {
    status: 401,
    headers: { "www-authenticate": 'Basic realm="ultratv-config"', ...corsHeaders },
  });
}

function checkAuth(req, env) {
  const h = req.headers.get("authorization") || "";
  if (!h.startsWith("Basic ")) return false;
  const decoded = atob(h.slice(6));
  const [u, p] = decoded.split(":", 2);
  return u === "admin" && p === env.ADMIN_PASSWORD;
}

function normaliseMac(raw) {
  const s = (raw || "").toLowerCase().trim();
  // Accept "aa:bb:cc:dd:ee:ff" or "aabbccddeeff" or "aa-bb-cc-dd-ee-ff".
  const hex = s.replace(/[^a-f0-9]/g, "");
  if (hex.length !== 12) return null;
  return hex.match(/.{2}/g).join(":");
}

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });

    // Public endpoint — app polls this with its derived MAC. No auth: the MAC
    // *is* the auth (knowing it = you own the device).
    const macMatch = url.pathname.match(/^\/api\/config\/([^/]+)\/?$/);
    if (macMatch && req.method === "GET") {
      const mac = normaliseMac(macMatch[1]);
      if (!mac) return json({ error: "invalid mac" }, { status: 400 });
      const raw = await env.CONFIG.get(mac);
      if (!raw) return json({ providers: [], known: false });
      return new Response(raw, { headers: { "content-type": "application/json; charset=utf-8", ...corsHeaders } });
    }

    // Everything else is admin-only.
    if (!checkAuth(req, env)) return unauthorized();

    if (macMatch && req.method === "POST") {
      const mac = normaliseMac(macMatch[1]);
      if (!mac) return json({ error: "invalid mac" }, { status: 400 });
      const body = await req.text();
      try { JSON.parse(body); } catch { return json({ error: "invalid json" }, { status: 400 }); }
      await env.CONFIG.put(mac, body);
      return json({ ok: true, mac });
    }
    if (macMatch && req.method === "DELETE") {
      const mac = normaliseMac(macMatch[1]);
      if (!mac) return json({ error: "invalid mac" }, { status: 400 });
      await env.CONFIG.delete(mac);
      return json({ ok: true, mac });
    }

    if (url.pathname === "/api/list" && req.method === "GET") {
      const list = await env.CONFIG.list();
      return json({ macs: list.keys.map((k) => k.name) });
    }

    if (url.pathname === "/" || url.pathname === "/dashboard") {
      return html(DASHBOARD_HTML);
    }

    return new Response("Not found", { status: 404, headers: corsHeaders });
  },
};

const DASHBOARD_HTML = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1" />
<title>Ultra TV — config dashboard</title>
<style>
  :root { color-scheme: dark; --bg:#0b1020; --bg2:#131a30; --fg:#e6e9f2; --muted:#8a93ac; --accent:#6ea8ff; --danger:#ff6b6b; --border:rgba(255,255,255,0.08); }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--fg); font:14px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif; padding:24px; }
  h1 { margin:0 0 4px 0; font-size:22px; }
  .sub { color:var(--muted); margin-bottom:24px; }
  .grid { display:grid; grid-template-columns: 320px 1fr; gap:24px; align-items:start; }
  .panel { background:var(--bg2); border:1px solid var(--border); border-radius:12px; padding:16px; }
  label { display:block; color:var(--muted); font-size:12px; margin:8px 0 4px; }
  input, select, textarea { width:100%; background:var(--bg); color:var(--fg); border:1px solid var(--border); border-radius:8px; padding:8px 10px; font:inherit; }
  textarea { font-family: ui-monospace, SFMono-Regular, monospace; min-height: 360px; }
  button { background:var(--accent); color:#0b1020; border:0; border-radius:8px; padding:8px 14px; font:inherit; font-weight:600; cursor:pointer; }
  button.secondary { background:transparent; color:var(--fg); border:1px solid var(--border); font-weight:400; }
  button.danger { background:transparent; color:var(--danger); border:1px solid var(--danger); }
  .row { display:flex; gap:8px; flex-wrap:wrap; align-items:center; margin-top:8px; }
  ul.macs { list-style:none; padding:0; margin:0; max-height:60vh; overflow:auto; }
  ul.macs li { padding:8px; border-radius:8px; cursor:pointer; font-family: ui-monospace, monospace; }
  ul.macs li:hover, ul.macs li.active { background: rgba(110,168,255,.18); color:var(--accent); }
  .toast { position:fixed; bottom:16px; right:16px; background:#1b2240; border:1px solid var(--accent); color:var(--fg); padding:10px 14px; border-radius:10px; opacity:0; transition:opacity .2s; pointer-events:none; }
  .toast.on { opacity:1; }
  .hint { color:var(--muted); font-size:12px; margin-top:6px; }
</style>
</head>
<body>
  <h1>Ultra TV — config dashboard</h1>
  <div class="sub">Provision provider lists per device MAC. Apps polling this Worker by MAC will auto-import the JSON below.</div>
  <div class="grid">
    <div class="panel">
      <div class="row">
        <input id="newMac" placeholder="aa:bb:cc:dd:ee:ff" />
        <button onclick="select(document.getElementById('newMac').value)">Open</button>
      </div>
      <div class="hint">Or pick an existing MAC:</div>
      <ul class="macs" id="macList"></ul>
      <div class="row"><button class="secondary" onclick="loadList()">↻ Refresh</button></div>
    </div>
    <div class="panel">
      <div><strong>MAC:</strong> <span id="curMac" style="font-family:ui-monospace,monospace; color:var(--accent)">—</span></div>
      <label>Providers JSON</label>
      <textarea id="cfg" spellcheck="false" placeholder='{
  "providers": [
    { "kind": "XTREAM", "name": "My Xtream", "url": "http://host:80", "username": "user", "password": "pass" },
    { "kind": "M3U",    "name": "My M3U",    "url": "https://my.host/list.m3u" },
    { "kind": "STALKER","name": "MAG portal","url": "http://host:8080", "mac": "00:1A:79:XX:XX:XX" }
  ]
}'></textarea>
      <div class="row">
        <button onclick="save()">💾 Save</button>
        <button class="secondary" onclick="format()">{ } Format</button>
        <button class="danger" onclick="del()">🗑 Delete</button>
      </div>
      <div class="hint">When the app calls <code>/api/config/&lt;mac&gt;</code>, this exact JSON is returned.</div>
    </div>
  </div>
  <div class="toast" id="toast"></div>
<script>
let current = null;

function toast(msg) {
  const el = document.getElementById('toast');
  el.textContent = msg; el.classList.add('on');
  clearTimeout(toast._t); toast._t = setTimeout(() => el.classList.remove('on'), 2200);
}

async function loadList() {
  const r = await fetch('/api/list');
  if (!r.ok) { toast('list failed'); return; }
  const data = await r.json();
  const ul = document.getElementById('macList');
  ul.innerHTML = '';
  data.macs.forEach(m => {
    const li = document.createElement('li');
    li.textContent = m;
    if (m === current) li.classList.add('active');
    li.onclick = () => select(m);
    ul.appendChild(li);
  });
}

async function select(rawMac) {
  const m = rawMac.toLowerCase().replace(/[^a-f0-9]/g, '');
  if (m.length !== 12) { toast('invalid MAC'); return; }
  const mac = m.match(/.{2}/g).join(':');
  current = mac;
  document.getElementById('curMac').textContent = mac;
  const r = await fetch('/api/config/' + mac);
  const data = await r.json();
  document.getElementById('cfg').value = JSON.stringify(data, null, 2);
  loadList();
}

async function save() {
  if (!current) { toast('select a MAC first'); return; }
  let body;
  try { body = JSON.stringify(JSON.parse(document.getElementById('cfg').value)); }
  catch (e) { toast('invalid JSON: ' + e.message); return; }
  const r = await fetch('/api/config/' + current, { method: 'POST', body });
  if (r.ok) { toast('saved ✓'); loadList(); }
  else { toast('save failed: ' + r.status); }
}

async function del() {
  if (!current) return;
  if (!confirm('Delete config for ' + current + '?')) return;
  const r = await fetch('/api/config/' + current, { method: 'DELETE' });
  if (r.ok) { toast('deleted'); document.getElementById('cfg').value = ''; loadList(); }
}

function format() {
  try {
    const v = JSON.parse(document.getElementById('cfg').value);
    document.getElementById('cfg').value = JSON.stringify(v, null, 2);
  } catch (e) { toast('invalid JSON'); }
}

loadList();
</script>
</body>
</html>`;
