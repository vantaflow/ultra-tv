// Lightweight spatial navigation for Android TV / Google TV / Fire TV remotes.
//
// How it works
// ------------
// - Intercepts the four arrow keys + Enter globally (D-pad on Android TV is
//   forwarded by MainActivity.java → dispatchKeyEvent → JS KeyboardEvent).
// - Walks the live DOM to find every focusable element (links, buttons,
//   inputs, anything with tabindex >= 0, [role=button]).
// - Picks the geometrically nearest one in the requested direction from the
//   currently-focused element.
// - Focuses it and scrolls it into view smoothly so horizontal shelves and
//   vertical lists "drag" with the cursor.
// - On Enter / OK, dispatches a real click on the focused element.
//
// Why not @noriginmedia/norigin-spatial-navigation alone
// ------------------------------------------------------
// That library requires wrapping every focusable component with `useFocusable`
// hooks. The app already has 100+ interactive elements — this implementation
// walks the DOM at runtime, no per-component changes needed.

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
  if (r.bottom < 0 || r.top > window.innerHeight + 2000) return false; // off-screen far away
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

  const tolerance = 6;
  const inDir = candidates.filter((c) => {
    switch (dir) {
      case "up":    return c.cy + c.h / 2 < origin.cy - tolerance;
      case "down":  return c.cy - c.h / 2 > origin.cy + tolerance;
      case "left":  return c.cx + c.w / 2 < origin.cx - tolerance;
      case "right": return c.cx - c.w / 2 > origin.cx + tolerance;
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
  const first = focusables()[0];
  if (first) { first.focus(); return first; }
  return null;
}

function scrollIntoView(el: HTMLElement) {
  el.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "nearest" });
}

const DIR_KEYS: Record<string, Direction> = {
  ArrowUp: "up", ArrowDown: "down", ArrowLeft: "left", ArrowRight: "right",
};

let installed = false;

export function installSpatialNav() {
  if (installed) return;
  installed = true;

  const updateClass = () => {
    document.querySelectorAll(".spatial-focused").forEach((el) => el.classList.remove("spatial-focused"));
    const a = document.activeElement as HTMLElement | null;
    if (a && a !== document.body) a.classList.add("spatial-focused");
  };
  document.addEventListener("focusin", updateClass);
  document.addEventListener("focusout", updateClass);

  const handler = (e: KeyboardEvent) => {
    const t = e.target as HTMLElement | null;
    const tag = t?.tagName;

    // Inputs and the video element keep their native arrow-key behaviour
    // (text caret, volume, seek). Everything else routes through spatial nav.
    const inEditable = tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || (t as HTMLElement | null)?.isContentEditable === true;
    const inVideo = tag === "VIDEO";
    if (inEditable || inVideo) return;

    if (e.key === "Enter") {
      const a = document.activeElement as HTMLElement | null;
      if (a && a !== document.body) {
        e.preventDefault();
        a.click();
        return;
      }
    }

    const dir = DIR_KEYS[e.key];
    if (!dir) return;
    const current = ensureSomethingFocused();
    if (!current) return;
    const next = pickNext(current, dir);
    if (next) {
      e.preventDefault();
      next.focus({ preventScroll: true });
      scrollIntoView(next);
    }
  };

  // Capture phase so we beat any other listener that might stopPropagation.
  window.addEventListener("keydown", handler, true);

  // Whenever the user navigates (React Router updates the DOM), re-establish
  // an initial focus so the first arrow press actually moves.
  const reFocus = () => setTimeout(ensureSomethingFocused, 200);
  reFocus();
  window.addEventListener("popstate", reFocus);
  // React Router pushState — patch once
  const origPush = history.pushState;
  history.pushState = function (...args) {
    const r = origPush.apply(this, args as never);
    reFocus();
    return r;
  };
}
