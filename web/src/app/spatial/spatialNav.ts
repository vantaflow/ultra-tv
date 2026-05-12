// Spatial navigation for Android TV / Google TV / Fire TV remotes.
//
// Two entry points:
//   1. `window.__ultratv_remote(action)` — called *directly* from the native
//      MainActivity when a D-pad key is pressed. Most reliable on TV WebViews.
//   2. `window` keydown listener — for browsers, Electron, and as a fallback
//      when the synthetic KeyboardEvent fires (MainActivity dispatches both).
//
// What it does on each action:
//   - up/down/left/right: walks the DOM, finds the geometrically nearest
//     focusable element in that direction, focuses it and scrolls it into view.
//   - enter: clicks the currently focused element.
//   - back: dispatches a CustomEvent('androidback') so screens can handle it.
//
// A tiny on-screen debug HUD (toggle with Shift+R or hold OK for ~1 s) shows
// the last action received — useful when diagnosing a remote that's not firing.

declare global {
  interface Window {
    __ultratv_remote?: (action: "up" | "down" | "left" | "right" | "enter" | "back" | "menu") => void;
    __ultratv_remote_debug?: boolean;
  }
}

const FOCUSABLE_SELECTOR = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled]):not([type='hidden'])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
  "[role='button']",
  "video[controls]",
].join(",");

type Direction = "up" | "down" | "left" | "right";

interface Box { x: number; y: number; w: number; h: number; cx: number; cy: number; el: HTMLElement }

function rect(el: HTMLElement): Box {
  const r = el.getBoundingClientRect();
  return { x: r.left, y: r.top, w: r.width, h: r.height, cx: r.left + r.width / 2, cy: r.top + r.height / 2, el };
}

function isVisible(el: HTMLElement): boolean {
  if (el.hidden) return false;
  const r = el.getBoundingClientRect();
  if (r.width === 0 || r.height === 0) return false;
  if (r.bottom < -1000 || r.top > window.innerHeight + 2000) return false;
  const style = window.getComputedStyle(el);
  if (style.visibility === "hidden" || style.display === "none" || Number(style.opacity) === 0) return false;
  return true;
}

function focusables(): HTMLElement[] {
  const list: HTMLElement[] = [];
  document.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR).forEach((el) => {
    if (isVisible(el)) list.push(el);
  });
  return list;
}

function pickNext(from: HTMLElement, dir: Direction): HTMLElement | null {
  const origin = rect(from);
  const candidates = focusables().filter((el) => el !== from).map(rect);
  const tol = 6;

  const inDir = candidates.filter((c) => {
    switch (dir) {
      case "up":    return c.cy + c.h / 2 < origin.cy - tol;
      case "down":  return c.cy - c.h / 2 > origin.cy + tol;
      case "left":  return c.cx + c.w / 2 < origin.cx - tol;
      case "right": return c.cx - c.w / 2 > origin.cx + tol;
    }
  });
  if (inDir.length === 0) return null;

  let best: { score: number; el: HTMLElement } | null = null;
  for (const c of inDir) {
    let primary: number, cross: number;
    switch (dir) {
      case "up":    primary = origin.cy - c.cy; cross = Math.abs(c.cx - origin.cx); break;
      case "down":  primary = c.cy - origin.cy; cross = Math.abs(c.cx - origin.cx); break;
      case "left":  primary = origin.cx - c.cx; cross = Math.abs(c.cy - origin.cy); break;
      case "right": primary = c.cx - origin.cx; cross = Math.abs(c.cy - origin.cy); break;
    }
    if (primary <= 0) continue;
    const score = primary + cross * 2;
    if (!best || score < best.score) best = { score, el: c.el };
  }
  return best?.el ?? null;
}

function ensureSomethingFocused(): HTMLElement | null {
  const active = document.activeElement as HTMLElement | null;
  if (active && active !== document.body && isVisible(active)) return active;
  // Prefer the first focusable inside <main> (skip the sidebar) so route loads
  // land on content, not on the nav.
  const main = document.querySelector("main") as HTMLElement | null;
  const all = focusables();
  const inMain = main ? all.filter((el) => main.contains(el)) : [];
  const target = inMain[0] ?? all[0];
  if (target) { target.focus(); return target; }
  return null;
}

function scrollIntoView(el: HTMLElement) {
  el.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "nearest" });
}

function highlightFocus() {
  document.querySelectorAll(".spatial-focused").forEach((el) => el.classList.remove("spatial-focused"));
  const a = document.activeElement as HTMLElement | null;
  if (a && a !== document.body) a.classList.add("spatial-focused");
}

function move(dir: Direction): boolean {
  const current = ensureSomethingFocused();
  if (!current) return false;
  const next = pickNext(current, dir);
  if (!next) return false;
  next.focus({ preventScroll: true });
  scrollIntoView(next);
  highlightFocus();
  return true;
}

function activate() {
  const a = document.activeElement as HTMLElement | null;
  if (a && a !== document.body) {
    a.click();
    return true;
  }
  return false;
}

function fireBack() {
  const ev = new CustomEvent("androidback", { cancelable: true });
  window.dispatchEvent(ev);
  if (!ev.defaultPrevented && history.length > 1) history.back();
}

// Debug HUD — shows the last remote action received in the bottom-right corner.
let hud: HTMLDivElement | null = null;
function showHud(text: string) {
  if (!window.__ultratv_remote_debug) return;
  if (!hud) {
    hud = document.createElement("div");
    hud.style.cssText = "position:fixed;bottom:8px;right:8px;background:rgba(0,0,0,.8);color:#6ea8ff;padding:6px 10px;border-radius:6px;font:12px/1.2 ui-monospace,monospace;z-index:99999;pointer-events:none;";
    document.body.appendChild(hud);
  }
  hud.textContent = `remote: ${text}`;
  hud.style.opacity = "1";
  setTimeout(() => { if (hud) hud.style.opacity = "0.3"; }, 600);
}

let installed = false;

export function installSpatialNav() {
  if (installed) return;
  installed = true;
  // Note: called both at module-load (see bottom of file) AND from React's
  // SpatialFocusBootstrap. Idempotent.

  // Temporary: enable the debug HUD on Android so we can see whether key
  // events are reaching JS at all. Toggle off with Shift+R once nav works.
  const ua = (navigator.userAgent || "").toLowerCase();
  if (ua.includes("android")) {
    window.__ultratv_remote_debug = true;
  }

  // Expose the native bridge entry point.
  window.__ultratv_remote = (action) => {
    showHud(action);
    switch (action) {
      case "up": case "down": case "left": case "right": move(action); break;
      case "enter": activate(); break;
      case "back": fireBack(); break;
      case "menu": window.dispatchEvent(new CustomEvent("androidmenu")); break;
    }
  };

  // Browser / Electron path + fallback when MainActivity fires synthetic events.
  const onKey = (e: KeyboardEvent) => {
    const t = e.target as HTMLElement | null;
    const tag = t?.tagName;
    const inEditable = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || (t as HTMLElement | null)?.isContentEditable === true;
    const inVideo = tag === "VIDEO";
    if (inEditable || inVideo) return;

    let handled = false;
    if (e.key === "ArrowUp")    handled = move("up");
    else if (e.key === "ArrowDown")  handled = move("down");
    else if (e.key === "ArrowLeft")  handled = move("left");
    else if (e.key === "ArrowRight") handled = move("right");
    else if (e.key === "Enter")      handled = activate();
    else if (e.key === "R" && e.shiftKey) { window.__ultratv_remote_debug = !window.__ultratv_remote_debug; showHud("debug " + (window.__ultratv_remote_debug ? "on" : "off")); }
    if (handled) e.preventDefault();
  };
  window.addEventListener("keydown", onKey, true);

  document.addEventListener("focusin", highlightFocus);
  document.addEventListener("focusout", highlightFocus);

  // Initial focus + after each navigation. Two delays: a quick first try, and
  // a second one after React has likely committed the new route.
  const reFocus = () => {
    setTimeout(() => { ensureSomethingFocused(); highlightFocus(); }, 80);
    setTimeout(() => { ensureSomethingFocused(); highlightFocus(); }, 350);
  };
  reFocus();
  window.addEventListener("popstate", reFocus);
  const origPush = history.pushState;
  history.pushState = function (...args) {
    const r = origPush.apply(this, args as never);
    reFocus();
    return r;
  };
  const origReplace = history.replaceState;
  history.replaceState = function (...args) {
    const r = origReplace.apply(this, args as never);
    reFocus();
    return r;
  };

  // When new content is injected (lazy-loaded screens, async lists), make sure
  // *some* element is focused. Throttled to once per animation frame so heavy
  // renders don't thrash.
  let pending = false;
  const mo = new MutationObserver(() => {
    if (pending) return;
    pending = true;
    requestAnimationFrame(() => {
      pending = false;
      const a = document.activeElement as HTMLElement | null;
      if (!a || a === document.body || !isVisible(a)) ensureSomethingFocused();
    });
  });
  mo.observe(document.body, { childList: true, subtree: true });
}

// Module-load install: guarantees window.__ultratv_remote exists before the
// MainActivity's first keypress reaches us, even if React hasn't hydrated yet.
// The DOM listeners are deferred until DOMContentLoaded if body isn't ready.
if (typeof window !== "undefined") {
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => installSpatialNav(), { once: true });
  } else {
    installSpatialNav();
  }
}
