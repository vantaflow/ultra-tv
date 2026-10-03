import { env, exports } from "cloudflare:workers";

let ipCounter = 0;
/** IP unique par test : les compteurs de débit sont par IP, les tests ne se polluent pas. */
export function freshIp() {
  ipCounter++;
  return `203.0.${(ipCounter >> 8) & 255}.${ipCounter & 255}`;
}

export const ORIGIN = "https://config.test";

export async function call(path, { method = "GET", headers = {}, form, json, body, cookie, ip, origin = ORIGIN } = {}) {
  const h = new Headers(headers);
  if (ip) h.set("cf-connecting-ip", ip);
  if (cookie) h.set("cookie", cookie);
  if (origin && method !== "GET") h.set("origin", origin);
  let payload = body;
  if (form) {
    h.set("content-type", "application/x-www-form-urlencoded");
    payload = new URLSearchParams(form).toString();
  } else if (json !== undefined) {
    h.set("content-type", "application/json");
    payload = JSON.stringify(json);
  }
  return exports.default.fetch(new Request(ORIGIN + path, { method, headers: h, body: payload, redirect: "manual" }));
}

export function cookieFrom(res) {
  const raw = res.headers.get("set-cookie") || "";
  const m = raw.match(/(__Host-utv_sess)=([^;]*)/);
  return m && m[2] ? `${m[1]}=${m[2]}` : null;
}

export async function csrfOf(cookie, ip) {
  const res = await call("/", { cookie, ip });
  const html = await res.text();
  const m = html.match(/name="csrf" value="([^"]+)"/);
  return m && m[1];
}

export const strongPw = "correct-horse-battery";

/** Crée un compte et renvoie {login, cookie, csrf, ip}. */
export async function newAccount(login = `user-${crypto.randomUUID().slice(0, 8)}`) {
  const ip = freshIp();
  const res = await call("/signup", { method: "POST", ip, form: { login, password: strongPw, confirm: strongPw } });
  const cookie = cookieFrom(res);
  if (!cookie) throw new Error(`signup a échoué (${res.status})`);
  return { login, cookie, csrf: await csrfOf(cookie, ip), ip };
}

/** Parcours d'appairage complet : renvoie le jeton d'appareil. */
export async function pairDevice(acct, name = "Salon") {
  const ip = freshIp();
  const start = await (await call("/api/pair/start", { method: "POST", ip, json: { label: "aa:bb:cc:dd:ee:ff" } })).json();
  const confirm = await call("/pair", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, code: start.code, name } });
  if (confirm.status !== 302) throw new Error(`confirm ${confirm.status}`);
  const poll = await (await call("/api/pair/poll", { method: "POST", ip, json: { code: start.code, pollSecret: start.pollSecret } })).json();
  return { token: poll.token, deviceId: poll.deviceId, ip };
}

export async function addProvider(acct, fields) {
  return call("/providers", { method: "POST", ip: acct.ip, cookie: acct.cookie, form: { csrf: acct.csrf, ...fields } });
}

export async function allKv() {
  const out = {};
  let cursor;
  do {
    const r = await env.CONFIG.list({ cursor });
    for (const k of r.keys) out[k.name] = await env.CONFIG.get(k.name);
    cursor = r.list_complete ? undefined : r.cursor;
  } while (cursor);
  return out;
}

export const bearer = (t) => ({ authorization: `Bearer ${t}` });
export const basic = (pw) => ({ authorization: "Basic " + btoa("ops:" + pw) });
export const OPS = "test-ops-token-0123456789abcdef0123456789";
export const ADMIN = "test-admin-token-0123456789abcdef0123456789";
