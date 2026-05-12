/**
 * Ultra TV — MAC-based remote config Worker.
 *
 * Auth: HTML form login → HMAC-signed session cookie (24h). The cookie carries
 * an HMAC of (expiry) keyed by ADMIN_PASSWORD so we don't need a session store.
 *
 * Public:
 *   GET  /api/config/:mac        → app polls its own config (no auth — the MAC
 *                                  itself is the secret)
 *
 * Authenticated (cookie):
 *   GET  /                       → dashboard (or redirect to /login)
 *   GET  /login                  → login form
 *   POST /login                  → check password, set cookie, redirect to /
 *   GET  /logout                 → clear cookie
 *   GET  /api/list               → list of known MACs (JSON)
 *   POST /api/config/:mac        → save a provider list (JSON body)
 *   POST /api/provider/:mac      → add a single provider (multipart form)
 *   POST /api/provider/:mac/:idx/delete → remove provider at index
 *   POST /api/config/:mac/delete → drop the entire MAC config
 *
 * KV `CONFIG`: stores the JSON config under the lowercased colon-separated MAC.
 */

const COOKIE_NAME = "uconf_sess";
const SESSION_HOURS = 24;

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
    headers: { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" },
  });
}

function redirect(to, init = {}) {
  return new Response("", { status: init.status ?? 302, headers: { location: to, ...(init.headers || {}) } });
}

function normaliseMac(raw) {
  // URL.pathname keeps `%3A` encoded — if we strip non-hex without decoding
  // first, the `3a` from `%3A` survives and the MAC ends up 22 chars instead
  // of 12. decodeURIComponent first, then filter.
  let s = (raw || "").trim();
  try { s = decodeURIComponent(s); } catch (_) { /* leave as-is */ }
  s = s.toLowerCase();
  const hex = s.replace(/[^a-f0-9]/g, "");
  if (hex.length !== 12) return null;
  return hex.match(/.{2}/g).join(":");
}

// ---- Session (signed cookie) -----------------------------------------------

async function hmac(secret, msg) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(msg));
  return btoa(String.fromCharCode(...new Uint8Array(sig))).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function makeSession(env) {
  const exp = Date.now() + SESSION_HOURS * 3600 * 1000;
  const payload = String(exp);
  const sig = await hmac(env.ADMIN_PASSWORD, payload);
  return `${payload}.${sig}`;
}

async function verifySession(value, env) {
  if (!value || !value.includes(".")) return false;
  const [exp, sig] = value.split(".", 2);
  if (Date.now() > parseInt(exp, 10)) return false;
  const want = await hmac(env.ADMIN_PASSWORD, exp);
  return want === sig;
}

function readCookie(req, name) {
  const raw = req.headers.get("cookie") || "";
  for (const part of raw.split(";")) {
    const [k, v] = part.trim().split("=", 2);
    if (k === name) return decodeURIComponent(v || "");
  }
  return null;
}

async function isAuthed(req, env) {
  const c = readCookie(req, COOKIE_NAME);
  return c && (await verifySession(c, env));
}

// ---- KV helpers ------------------------------------------------------------

async function readConfig(env, mac) {
  const raw = await env.CONFIG.get(mac);
  if (!raw) return { providers: [] };
  try { return JSON.parse(raw); } catch { return { providers: [] }; }
}

async function writeConfig(env, mac, cfg) {
  await env.CONFIG.put(mac, JSON.stringify(cfg));
}

// ---- Worker entry ----------------------------------------------------------

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });

    // ---- Public app-facing endpoint ----
    const macGet = url.pathname.match(/^\/api\/config\/([^/]+)\/?$/);
    if (macGet && req.method === "GET") {
      const mac = normaliseMac(macGet[1]);
      if (!mac) return json({ error: "invalid mac" }, { status: 400 });
      const cfg = await readConfig(env, mac);
      return new Response(JSON.stringify({ providers: cfg.providers || [], known: !!cfg.providers?.length }), {
        headers: { "content-type": "application/json; charset=utf-8", ...corsHeaders },
      });
    }

    // ---- Auth routes ----
    if (url.pathname === "/login" && req.method === "GET") {
      const err = url.searchParams.get("e");
      return html(loginPage(err));
    }
    if (url.pathname === "/login" && req.method === "POST") {
      const form = await req.formData();
      const pw = (form.get("password") || "").toString();
      if (pw !== env.ADMIN_PASSWORD) return redirect("/login?e=1");
      const sess = await makeSession(env);
      const cookie = `${COOKIE_NAME}=${encodeURIComponent(sess)}; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=${SESSION_HOURS * 3600}`;
      return new Response("", { status: 302, headers: { location: "/", "set-cookie": cookie } });
    }
    if (url.pathname === "/logout") {
      return new Response("", {
        status: 302,
        headers: { location: "/login", "set-cookie": `${COOKIE_NAME}=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax` },
      });
    }

    // ---- Authenticated routes ----
    if (!(await isAuthed(req, env))) {
      // For browser pages → redirect to login. For API → 401 JSON.
      if (url.pathname.startsWith("/api/")) return json({ error: "auth required" }, { status: 401 });
      return redirect("/login");
    }

    if (url.pathname === "/api/list" && req.method === "GET") {
      const list = await env.CONFIG.list();
      return json({ macs: list.keys.map((k) => k.name) });
    }

    // Add a provider for a MAC (form submit).
    const addProv = url.pathname.match(/^\/api\/provider\/([^/]+)\/?$/);
    if (addProv && req.method === "POST") {
      const mac = normaliseMac(addProv[1]);
      if (!mac) return badRequest("Invalid MAC");
      const f = await req.formData();
      const kind = (f.get("kind") || "").toString().toUpperCase();
      const name = (f.get("name") || "").toString();
      const provider = { kind, name };
      if (kind === "XTREAM") {
        provider.url = (f.get("url") || "").toString();
        provider.username = (f.get("username") || "").toString();
        provider.password = (f.get("password") || "").toString();
      } else if (kind === "M3U") {
        provider.url = (f.get("url") || "").toString();
      } else if (kind === "STALKER") {
        provider.url = (f.get("url") || "").toString();
        provider.mac = (f.get("mac") || "").toString();
      } else {
        return badRequest("Unknown kind: " + kind);
      }
      const cfg = await readConfig(env, mac);
      cfg.providers = cfg.providers || [];
      cfg.providers.push(provider);
      await writeConfig(env, mac, cfg);
      return redirect(`/?mac=${encodeURIComponent(mac)}`);
    }

    // Delete a provider at index.
    const delProv = url.pathname.match(/^\/api\/provider\/([^/]+)\/(\d+)\/delete\/?$/);
    if (delProv && req.method === "POST") {
      const mac = normaliseMac(delProv[1]);
      const idx = parseInt(delProv[2], 10);
      if (!mac) return badRequest("Invalid MAC");
      const cfg = await readConfig(env, mac);
      cfg.providers = (cfg.providers || []).filter((_, i) => i !== idx);
      await writeConfig(env, mac, cfg);
      return redirect(`/?mac=${encodeURIComponent(mac)}`);
    }

    // Delete the whole MAC entry.
    const delMac = url.pathname.match(/^\/api\/config\/([^/]+)\/delete\/?$/);
    if (delMac && req.method === "POST") {
      const mac = normaliseMac(delMac[1]);
      if (!mac) return badRequest("Invalid MAC");
      await env.CONFIG.delete(mac);
      return redirect("/");
    }

    // Direct JSON save (advanced) — kept as a fallback for power users.
    const saveCfg = url.pathname.match(/^\/api\/config\/([^/]+)\/?$/);
    if (saveCfg && req.method === "POST") {
      const mac = normaliseMac(saveCfg[1]);
      if (!mac) return badRequest("Invalid MAC");
      const body = await req.text();
      try { JSON.parse(body); } catch { return badRequest("Invalid JSON"); }
      await env.CONFIG.put(mac, body);
      return json({ ok: true, mac });
    }

    if (url.pathname === "/" || url.pathname === "/dashboard") {
      const selectedMac = normaliseMac(url.searchParams.get("mac") || "");
      const list = await env.CONFIG.list();
      const macs = list.keys.map((k) => k.name);
      const cfg = selectedMac ? await readConfig(env, selectedMac) : null;
      return html(dashboardPage({ macs, selectedMac, cfg }));
    }

    return new Response("Not found", { status: 404 });
  },
};

function badRequest(msg) {
  return new Response(msg, { status: 400 });
}

// ---- HTML pages ------------------------------------------------------------

const baseStyles = `
:root { color-scheme: dark; --bg:#0b1020; --bg2:#131a30; --bg3:#1b2240; --fg:#e6e9f2; --muted:#8a93ac; --accent:#6ea8ff; --danger:#ff6b6b; --border:rgba(255,255,255,0.08); }
* { box-sizing: border-box; }
body { margin:0; background:var(--bg); color:var(--fg); font:14px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif; padding:24px; }
a { color: var(--accent); text-decoration: none; }
h1 { margin:0 0 4px 0; font-size:22px; }
.sub { color:var(--muted); margin-bottom:24px; }
.panel { background:var(--bg2); border:1px solid var(--border); border-radius:12px; padding:16px; }
label { display:block; color:var(--muted); font-size:12px; margin:10px 0 4px; }
input, select, textarea { width:100%; background:var(--bg); color:var(--fg); border:1px solid var(--border); border-radius:8px; padding:9px 11px; font:inherit; }
button { background:var(--accent); color:#0b1020; border:0; border-radius:8px; padding:9px 14px; font:inherit; font-weight:600; cursor:pointer; }
button.secondary { background:transparent; color:var(--fg); border:1px solid var(--border); font-weight:400; }
button.danger { background:transparent; color:var(--danger); border:1px solid var(--danger); }
.row { display:flex; gap:8px; flex-wrap:wrap; align-items:center; margin-top:10px; }
ul.macs { list-style:none; padding:0; margin:0; max-height:60vh; overflow:auto; }
ul.macs li { padding:9px 10px; border-radius:8px; font-family: ui-monospace, monospace; }
ul.macs li a { display:block; color:var(--fg); }
ul.macs li.active, ul.macs li:hover { background: rgba(110,168,255,.18); }
ul.macs li.active a, ul.macs li:hover a { color: var(--accent); }
.providers { display:flex; flex-direction:column; gap:8px; margin-top:12px; }
.provider { display:flex; gap:10px; align-items:center; padding:10px 12px; background:var(--bg3); border-radius:10px; }
.provider .kind { background:var(--accent); color:#0b1020; padding:2px 8px; border-radius:6px; font-size:11px; font-weight:700; }
.provider .name { font-weight:600; }
.provider .muted { color:var(--muted); font-size:12px; }
.provider form { margin-left:auto; }
.tabs { display:flex; gap:6px; border-bottom:1px solid var(--border); margin:12px 0; }
.tabs button { background:transparent; color:var(--muted); padding:8px 14px; border-radius:0; font-weight:500; }
.tabs button.on { color:var(--accent); border-bottom:2px solid var(--accent); }
.field-help { color:var(--muted); font-size:11px; margin-top:4px; }
.toolbar { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
`;

function loginPage(err) {
  return `<!doctype html><html><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/><title>Ultra TV — login</title><style>${baseStyles}
form { max-width: 380px; margin: 80px auto; padding: 24px; background: var(--bg2); border-radius: 14px; border: 1px solid var(--border); }
</style></head><body>
<form method="post" action="/login">
  <h1>Ultra TV config</h1>
  <div class="sub">Admin sign-in</div>
  ${err ? `<div style="color:var(--danger); margin-bottom:8px;">Wrong password.</div>` : ""}
  <label>Password</label>
  <input type="password" name="password" autocomplete="current-password" autofocus />
  <div class="row" style="margin-top:16px;"><button type="submit">Sign in</button></div>
</form>
</body></html>`;
}

function dashboardPage({ macs, selectedMac, cfg }) {
  const macListHtml = macs.map((m) => `
    <li class="${m === selectedMac ? "active" : ""}"><a href="/?mac=${encodeURIComponent(m)}">${m}</a></li>
  `).join("");

  const providers = (cfg && cfg.providers) || [];
  const providerRows = providers.map((p, i) => `
    <div class="provider">
      <span class="kind">${escape(p.kind || "?")}</span>
      <div>
        <div class="name">${escape(p.name || "(unnamed)")}</div>
        <div class="muted">${escape(p.url || "")} ${p.username ? " · " + escape(p.username) : ""}${p.mac ? " · MAC " + escape(p.mac) : ""}</div>
      </div>
      <form method="post" action="/api/provider/${encodeURIComponent(selectedMac)}/${i}/delete" onsubmit="return confirm('Remove this provider?')">
        <button type="submit" class="danger">Remove</button>
      </form>
    </div>
  `).join("");

  const macSection = selectedMac ? `
    <div class="toolbar">
      <div><strong>Current MAC:</strong> <span style="font-family:ui-monospace,monospace; color:var(--accent)">${escape(selectedMac)}</span></div>
      <form method="post" action="/api/config/${encodeURIComponent(selectedMac)}/delete" onsubmit="return confirm('Delete ALL providers for ${escape(selectedMac)}?')">
        <button class="danger" type="submit">Delete MAC</button>
      </form>
    </div>

    <h2 style="margin:0 0 6px 0; font-size:16px;">Providers (${providers.length})</h2>
    <div class="providers">
      ${providers.length ? providerRows : `<div class="muted">No providers yet — add one below.</div>`}
    </div>

    <h2 style="margin:24px 0 8px 0; font-size:16px;">Add a provider</h2>
    <div class="tabs">
      <button type="button" class="on" onclick="showTab('xtream')">Xtream Codes</button>
      <button type="button" onclick="showTab('m3u')">M3U URL</button>
      <button type="button" onclick="showTab('stalker')">Stalker Portal</button>
    </div>

    <form method="post" action="/api/provider/${encodeURIComponent(selectedMac)}" id="form-xtream">
      <input type="hidden" name="kind" value="XTREAM" />
      <label>Name (optional)</label>
      <input name="name" placeholder="My Xtream" />
      <label>Server URL <span class="muted">(e.g. http://provider.com:8080)</span></label>
      <input name="url" required />
      <label>Username</label>
      <input name="username" required />
      <label>Password</label>
      <input name="password" type="password" required />
      <div class="row"><button type="submit">Add Xtream provider</button></div>
    </form>

    <form method="post" action="/api/provider/${encodeURIComponent(selectedMac)}" id="form-m3u" style="display:none;">
      <input type="hidden" name="kind" value="M3U" />
      <label>Name (optional)</label>
      <input name="name" placeholder="My M3U" />
      <label>Playlist URL <span class="muted">(.m3u / .m3u8)</span></label>
      <input name="url" required />
      <div class="row"><button type="submit">Add M3U provider</button></div>
    </form>

    <form method="post" action="/api/provider/${encodeURIComponent(selectedMac)}" id="form-stalker" style="display:none;">
      <input type="hidden" name="kind" value="STALKER" />
      <label>Name (optional)</label>
      <input name="name" placeholder="MAG portal" />
      <label>Portal URL <span class="muted">(e.g. http://host:8080)</span></label>
      <input name="url" required />
      <label>Device MAC <span class="muted">(00:1A:79:XX:XX:XX)</span></label>
      <input name="mac" required pattern="^[0-9a-fA-F:]{17}$" />
      <div class="row"><button type="submit">Add Stalker portal</button></div>
    </form>
  ` : `<div class="muted">Pick a MAC from the left or open a new one to start provisioning.</div>`;

  return `<!doctype html><html><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/><title>Ultra TV — config</title><style>${baseStyles}
.layout { display:grid; grid-template-columns: 320px 1fr; gap:24px; align-items:start; }
.topbar { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
</style></head><body>
<div class="topbar">
  <div><h1 style="display:inline; margin-right:12px;">Ultra TV — config dashboard</h1><span class="sub">Provision providers per device MAC.</span></div>
  <a href="/logout"><button class="secondary">Sign out</button></a>
</div>

<div class="layout">
  <div class="panel">
    <form method="get" action="/" onsubmit="this.mac.value = this.mac.value.toLowerCase()">
      <label>Open a MAC</label>
      <input name="mac" placeholder="aa:bb:cc:dd:ee:ff" required pattern="^[0-9a-fA-F:\\-]{12,17}$" />
      <div class="row"><button type="submit">Open</button></div>
    </form>
    <div class="sub" style="margin-top:18px;">Known MACs (${macs.length}):</div>
    <ul class="macs">${macListHtml || `<li class="muted">none yet</li>`}</ul>
  </div>

  <div class="panel">
    ${macSection}
  </div>
</div>

<script>
function showTab(id) {
  ['xtream','m3u','stalker'].forEach(k => document.getElementById('form-'+k).style.display = (k===id?'block':'none'));
  document.querySelectorAll('.tabs button').forEach((b,i) => b.classList.toggle('on', i === ['xtream','m3u','stalker'].indexOf(id)));
}
</script>
</body></html>`;
}

function escape(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
