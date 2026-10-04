import { DurableObject } from "cloudflare:workers";
import { timingSafeEqual } from "./crypto.js";

/**
 * Guard — un Durable Object par clé (IP, compte, code d'appairage).
 *
 * Pourquoi un DO et pas KV : KV est éventuellement cohérent et sans opération
 * atomique. Un compteur d'échecs ou un code à usage unique stocké dans KV se
 * contourne en parallélisant les requêtes. Un DO sérialise les accès.
 *
 * Chaque instance ne garde qu'un état minuscule et le supprime par alarme.
 */
export class Guard extends DurableObject {
  async #expireAt(ms) {
    const cur = await this.ctx.storage.getAlarm();
    if (cur === null || cur < ms) await this.ctx.storage.setAlarm(ms);
  }

  async alarm() {
    await this.ctx.storage.deleteAll();
  }

  // ---- unicité (création de compte) -----------------------------------------
  /** Vrai pour le premier appelant uniquement ; permanent jusqu'à release(). */
  async claim() {
    if (await this.ctx.storage.get("claimed")) return false;
    await this.ctx.storage.put("claimed", true);
    return true;
  }

  async release() {
    await this.ctx.storage.delete("claimed");
  }

  // ---- verrou d'écriture d'un compte (sérialise les mutations) ---------------
  /** Prend le verrou si libre (ou expiré) ; l'appelant réessaie sinon. Le jeton protège contre une libération tardive. */
  async lockAcquire(ttlMs) {
    const now = Date.now();
    const cur = await this.ctx.storage.get("m");
    if (cur && cur.until > now) return { ok: false };
    const token = crypto.randomUUID();
    await this.ctx.storage.put("m", { token, until: now + ttlMs });
    await this.#expireAt(now + ttlMs + 1000);
    return { ok: true, token };
  }

  async lockRelease(token) {
    const cur = await this.ctx.storage.get("m");
    if (cur && cur.token === token) await this.ctx.storage.delete("m");
  }

  // ---- fenêtre fixe --------------------------------------------------------
  async hit(limit, windowSec) {
    const now = Date.now();
    let w = await this.ctx.storage.get("w");
    if (!w || w.until <= now) w = { n: 0, until: now + windowSec * 1000 };
    w.n++;
    await this.ctx.storage.put("w", w);
    await this.#expireAt(w.until + 1000);
    const retryAfter = Math.max(1, Math.ceil((w.until - now) / 1000));
    return { ok: w.n <= limit, remaining: Math.max(0, limit - w.n), retryAfter };
  }

  // ---- verrouillage progressif --------------------------------------------
  async lockState() {
    const l = (await this.ctx.storage.get("l")) || { fails: 0, until: 0 };
    const now = Date.now();
    return { locked: l.until > now, retryAfter: Math.max(0, Math.ceil((l.until - now) / 1000)), fails: l.fails };
  }

  /** `threshold` échecs → baseSec, puis le délai double à chaque échec, plafonné. */
  async lockFail({ threshold, baseSec, capSec }) {
    const l = (await this.ctx.storage.get("l")) || { fails: 0, until: 0 };
    l.fails++;
    const now = Date.now();
    let wait = 0;
    if (l.fails >= threshold) {
      wait = Math.min(baseSec * 2 ** (l.fails - threshold), capSec);
      l.until = now + wait * 1000;
    }
    await this.ctx.storage.put("l", l);
    // L'historique d'échecs survit 24 h sans nouvel échec (ou jusqu'à la fin du verrou).
    await this.#expireAt(Math.max(l.until, now) + 24 * 3600 * 1000);
    return { locked: wait > 0, retryAfter: wait, fails: l.fails };
  }

  async lockClear() {
    await this.ctx.storage.delete("l");
  }

  // ---- appairage -----------------------------------------------------------
  async pairInit({ secretHash, label, ttlSec }) {
    if (await this.ctx.storage.get("p")) return { ok: false };
    const until = Date.now() + ttlSec * 1000;
    await this.ctx.storage.put("p", { secretHash, label, until, state: "pending" });
    await this.#expireAt(until + 5 * 60 * 1000);
    return { ok: true };
  }

  async pairConfirm({ login, deviceId, token }) {
    const p = await this.ctx.storage.get("p");
    if (!p || p.state !== "pending" || p.until <= Date.now()) return { ok: false };
    await this.ctx.storage.put("p", { ...p, state: "ready", login, deviceId, token });
    return { ok: true, label: p.label };
  }

  /** Le jeton n'est livré qu'une fois : l'entrée est supprimée à la remise. */
  async pairPoll(secretHash) {
    const p = await this.ctx.storage.get("p");
    if (!p || !timingSafeEqual(p.secretHash, secretHash)) return { status: "unknown" };
    if (p.state === "ready") {
      await this.ctx.storage.delete("p");
      return { status: "ready", token: p.token, deviceId: p.deviceId };
    }
    if (p.until <= Date.now()) {
      await this.ctx.storage.delete("p");
      return { status: "unknown" };
    }
    return { status: "pending" };
  }
}
