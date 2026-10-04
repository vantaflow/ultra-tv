import { useEffect, useRef, type ReactNode } from "react";
import { qualityLabel } from "@/lib/channelName";
import { useT } from "@/i18n";
import { useUi } from "@/state/ui";
import { Icon, type IconName } from "./Icon";

export function Toasts() {
  const toasts = useUi((s) => s.toasts);
  return (
    <div className="toasts" role="status" aria-live="polite">
      {toasts.map((t) => <div key={t.id} className="toast">{t.text}</div>)}
    </div>
  );
}

export function QBadge({ q }: { q: number }) {
  if (q < 1) return null;
  return <span className={`qbadge q${Math.min(q, 4)}`}>{qualityLabel(q)}</span>;
}

export function Modal({ title, children, onClose, wide, foot }: { title: string; children: ReactNode; onClose: () => void; wide?: boolean; foot?: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const k = (e: KeyboardEvent) => { if (e.key === "Escape") { e.stopPropagation(); onClose(); } };
    window.addEventListener("keydown", k, true);
    ref.current?.querySelector<HTMLElement>("input,button")?.focus();
    return () => window.removeEventListener("keydown", k, true);
  }, [onClose]);
  return (
    <div className="scrim" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={`modal${wide ? " wide" : ""}`} role="dialog" aria-modal="true" aria-label={title} ref={ref}>
        <h3>{title}</h3>
        {children}
        {foot && <div className="foot">{foot}</div>}
      </div>
    </div>
  );
}

export function StateCard({ icon, title, body, actions, error }: { icon: IconName; title: string; body?: string; actions?: ReactNode; error?: boolean }) {
  return (
    <section className={`state-card${error ? " err" : ""}`} aria-label={title}>
      <span className="ico"><Icon name={icon} size={36} /></span>
      <h2>{title}</h2>
      {body && <p>{body}</p>}
      {actions && <div className="actions">{actions}</div>}
    </section>
  );
}

export function Switch({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) {
  return <button type="button" role="switch" aria-checked={on} aria-label={label} className="switch" onClick={() => onChange(!on)} />;
}

export function Seg<T extends string>({ value, options, onChange, label }: { value: T; options: { v: T; label: string }[]; onChange: (v: T) => void; label: string }) {
  return (
    <div className="seg" role="radiogroup" aria-label={label}>
      {options.map((o) => <button key={o.v} type="button" role="radio" aria-checked={value === o.v} onClick={() => onChange(o.v)}>{o.label}</button>)}
    </div>
  );
}

export const hhmm = (ms: number, locale?: string) => new Date(ms).toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" });
