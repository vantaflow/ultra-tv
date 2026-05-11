// Lightweight spatial navigation for Android TV / Google TV / Fire TV remotes.
//
// What it does
// ------------
// - Intercepts the four D-pad arrow keys + OK/Enter globally.
// - Finds every focusable element on the page (links, buttons, inputs, anything
//   with tabindex >= 0) and picks the one that's geometrically nearest in the
//   pressed direction from the currently-focused element.
// - Focuses it and scrolls it into view smoothly (so horizontal shelves and
//   vertical lists "drag" with the focus).
// - On Enter / OK, dispatches a real click on the focused element.
//
// Why not @noriginmedia/norigin-spatial-navigation alone
// ------------------------------------------------------
// That library requires wrapping every focusable component with `useFocusable`
// hooks. The app already has 80+ interactive elements (NavLinks, posters,
// chips, channels…) — wrapping them all would be invasive. This implementation
// walks the DOM at runtime, no per-component changes needed.

const FOCUSABLE_SELECTOR = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled]):not([type='hidden'])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
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
  const style = window.getComputedStyle(el);
  if (style.visibility === "hidden" || style.display === "none" || style.opacity === "0") return false;
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

  // Eliminate candidates that aren't in the requested half-plane (with a small
  // overlap tolerance so adjacent rows/columns still count).
  const tolerance = 4;
  const inDir = candidates.filter((c) => {
    switch (dir) {
      case "up":    return c.y + c.h <= origin.y + tolerance;
      case "down":  return c.y >= origin.y + origin.h - tolerance;
      case "left":  return c.x + c.w <= origin.x + tolerance;
      case "right": return c.x >= origin.x + origin.w - tolerance;
    }
  });
  if (inDir.length === 0) return null;

  // Score = primary-axis distance (must move forward) + cross-axis penalty *2
  // so the "obvious" next item wins over a far-but-aligned one.
  let best: { score: number; el: HTMLElement } | null = null;
  for (const c of inDir) {
    let primary: number;
    let cross: number;
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

  // Highlight focused element so the user can see where the cursor sits.
  // CSS already provides .spatial-focused styles; we toggle the class on the
  // currently-focused element.
  const updateClass = () => {
    document.querySelectorAll(".spatial-focused").forEach((el) => el.classList.remove("spatial-focused"));
    const a = document.activeElement as HTMLElement | null;
    if (a && a !== document.body) a.classList.add("spatial-focused");
  };
  document.addEventListener("focusin", updateClass);
  document.addEventListener("focusout", updateClass);

  window.addEventListener("keydown", (e) => {
    // Let inputs and the video element keep their native behaviour.
    const t = e.target as HTMLElement | null;
    const tag = t?.tagName;
    if (tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || tag === "VIDEO") return;

    if (e.key === "Enter" || e.key === " " /* OK on many remotes */) {
      const a = document.activeElement as HTMLElement | null;
      if (a && a !== document.body && (a.tagName === "A" || a.tagName === "BUTTON" || a.getAttribute("role") === "button")) {
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
  }, true);

  // First paint: focus something so the very first arrow press actually moves.
  setTimeout(() => { ensureSomethingFocused(); }, 300);
}
