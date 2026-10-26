import {
  forwardRef,
  useEffect,
  useRef,
  useState,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
} from "react";
import { createPortal } from "react-dom";
import { Check, Loader2, X } from "lucide-react";
import { useApp } from "../lib/store";

export const cx = (...c: (string | false | null | undefined)[]) => c.filter(Boolean).join(" ");

/* ---------- Button ---------- */
type BtnVariant = "primary" | "secondary" | "ghost" | "danger";
export const Button = forwardRef<
  HTMLButtonElement,
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: BtnVariant; loading?: boolean; icon?: ReactNode; big?: boolean }
>(function Button({ variant = "secondary", loading, icon, big, className, children, disabled, ...rest }, ref) {
  const base =
    "inline-flex items-center justify-center gap-2 rounded-[10px] font-semibold transition-all duration-150 select-none disabled:opacity-50 disabled:pointer-events-none active:scale-[0.98] whitespace-nowrap";
  const size = big ? "h-14 px-8 text-base rounded-[12px]" : "h-10 px-4 text-sm";
  const v = {
    primary: "bg-accent text-[var(--on-accent)] hover:brightness-110 glow",
    secondary: "bg-surface-2 text-fg hover:bg-surface-3 border border-line",
    ghost: "bg-transparent text-dim hover:bg-surface-2 hover:text-fg",
    danger: "bg-bad/15 text-bad hover:bg-bad/25 border border-bad/30",
  }[variant];
  return (
    <button ref={ref} className={cx(base, size, v, className)} disabled={disabled || loading} {...rest}>
      {loading ? <Loader2 className="size-4 animate-spin" /> : icon}
      {children}
    </button>
  );
});

export function IconButton({
  label,
  className,
  children,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button
      aria-label={label}
      title={label}
      className={cx(
        "size-10 inline-flex items-center justify-center rounded-[10px] text-dim hover:bg-surface-2 hover:text-fg transition-colors disabled:opacity-40",
        className,
      )}
      {...rest}
    >
      {children}
    </button>
  );
}

/* ---------- Inputs ---------- */
export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement> & { icon?: ReactNode }>(
  function Input({ className, icon, ...rest }, ref) {
    return (
      <div className="relative">
        {icon && <span className="absolute left-3 top-1/2 -translate-y-1/2 text-faint pointer-events-none">{icon}</span>}
        <input
          ref={ref}
          className={cx(
            "h-10 w-full rounded-[10px] bg-surface-2 border border-line px-3 text-sm text-fg placeholder:text-faint",
            "hover:border-faint focus:border-accent focus:outline-none transition-colors",
            icon ? "pl-10" : "",
            className,
          )}
          {...rest}
        />
      </div>
    );
  },
);

export function Select({
  className,
  children,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className={cx(
        "h-10 w-full rounded-[10px] bg-surface-2 border border-line px-3 text-sm text-fg",
        "hover:border-faint focus:border-accent focus:outline-none transition-colors",
        className,
      )}
      {...rest}
    >
      {children}
    </select>
  );
}

export function Toggle({
  checked,
  onChange,
  label,
  disabled,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label?: string;
  disabled?: boolean;
}) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cx(
        "relative h-6 w-11 shrink-0 rounded-full transition-colors duration-150 disabled:opacity-50",
        checked ? "bg-accent" : "bg-surface-3",
      )}
    >
      <span
        className={cx(
          "absolute top-0.5 left-0.5 size-5 rounded-full bg-white shadow transition-transform duration-150",
          checked && "translate-x-5",
        )}
      />
    </button>
  );
}

export function Checkbox({ checked, onChange, children }: { checked: boolean; onChange: (v: boolean) => void; children?: ReactNode }) {
  return (
    <label className="flex items-center gap-3 cursor-pointer text-sm">
      <span
        role="checkbox"
        aria-checked={checked}
        tabIndex={0}
        onKeyDown={(e) => (e.key === " " || e.key === "Enter") && (e.preventDefault(), onChange(!checked))}
        onClick={() => onChange(!checked)}
        className={cx(
          "size-5 rounded-md border flex items-center justify-center transition-colors shrink-0",
          checked ? "bg-accent border-accent text-[var(--on-accent)]" : "bg-surface-2 border-line",
        )}
      >
        {checked && <Check className="size-3.5" strokeWidth={3} />}
      </span>
      {children}
    </label>
  );
}

export function Slider({
  value,
  min,
  max,
  step = 1,
  onChange,
  format,
}: {
  value: number;
  min: number;
  max: number;
  step?: number;
  onChange: (v: number) => void;
  format?: (v: number) => string;
}) {
  const pct = ((value - min) / (max - min)) * 100;
  return (
    <div className="flex items-center gap-4">
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="flex-1"
        style={{ background: `linear-gradient(to right, var(--accent) ${pct}%, var(--surface-3) ${pct}%)` }}
      />
      <span className="w-20 text-right text-sm font-semibold tabular-nums">{format ? format(value) : value}</span>
    </div>
  );
}

export function Field({
  label,
  hint,
  children,
  row,
}: {
  label: string;
  hint?: string;
  children: ReactNode;
  row?: boolean;
}) {
  return (
    <div className={cx("py-3", row && "flex items-center justify-between gap-6")}>
      <div className={cx(row ? "min-w-0" : "mb-2")}>
        <div className="text-sm font-semibold">{label}</div>
        {hint && <div className="text-xs text-faint mt-0.5">{hint}</div>}
      </div>
      <div className={cx(row && "shrink-0")}>{children}</div>
    </div>
  );
}

/* ---------- Surfaces ---------- */
export function Card({ className, children, ...rest }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cx("rounded-[12px] bg-surface border border-line", className)} {...rest}>
      {children}
    </div>
  );
}

export function Badge({ children, tone = "neutral" }: { children: ReactNode; tone?: "neutral" | "accent" | "ok" | "warn" | "bad" }) {
  const c = {
    neutral: "bg-surface-3 text-dim",
    accent: "bg-accent/15 text-accent",
    ok: "bg-ok/15 text-ok",
    warn: "bg-warn/15 text-warn",
    bad: "bg-bad/15 text-bad",
  }[tone];
  return <span className={cx("inline-flex items-center rounded-md px-2 h-5 text-xs font-semibold", c)}>{children}</span>;
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cx("skeleton", className)} />;
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cx("animate-spin text-accent", className ?? "size-5")} />;
}

export function Empty({ icon, title, hint, action }: { icon?: ReactNode; title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center text-center py-16 gap-3 text-dim">
      {icon && <div className="text-faint">{icon}</div>}
      <div className="text-base font-semibold text-fg">{title}</div>
      {hint && <div className="max-w-sm">{hint}</div>}
      {action}
    </div>
  );
}

export function ProgressBar({ value, className }: { value: number; className?: string }) {
  return (
    <div className={cx("h-1.5 w-full rounded-full bg-surface-3 overflow-hidden", className)}>
      <div className="h-full bg-accent transition-[width] duration-200" style={{ width: `${Math.max(0, Math.min(100, value))}%` }} />
    </div>
  );
}

/* ---------- Modal ---------- */
export function Modal({
  open,
  onClose,
  title,
  children,
  width = 520,
  footer,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  width?: number;
  footer?: ReactNode;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const t = useApp((s) => s.t);
  useEffect(() => {
    if (!open) return;
    const prev = document.activeElement as HTMLElement | null;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopPropagation();
        onClose();
      }
      if (e.key === "Tab" && ref.current) {
        const f = ref.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea, [tabindex]:not([tabindex="-1"])',
        );
        if (!f.length) return;
        const first = f[0],
          last = f[f.length - 1];
        if (e.shiftKey && document.activeElement === first) (e.preventDefault(), last.focus());
        else if (!e.shiftKey && document.activeElement === last) (e.preventDefault(), first.focus());
      }
    };
    window.addEventListener("keydown", onKey, true);
    setTimeout(() => ref.current?.querySelector<HTMLElement>("input, select, button:not([data-close])")?.focus(), 30);
    return () => {
      window.removeEventListener("keydown", onKey, true);
      prev?.focus?.();
    };
  }, [open, onClose]);
  if (!open) return null;
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-6 bg-black/60 backdrop-blur-sm" onMouseDown={onClose}>
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        style={{ width }}
        className="anim-pop max-w-full max-h-[90vh] flex flex-col rounded-[12px] bg-surface border border-line shadow-2xl"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 h-16 border-b border-line shrink-0">
          <h2 className="text-lg font-bold">{title}</h2>
          <IconButton label={t("common.close")} data-close onClick={onClose}>
            <X className="size-5" />
          </IconButton>
        </div>
        <div className="p-6 overflow-y-auto">{children}</div>
        {footer && <div className="px-6 py-4 border-t border-line flex justify-end gap-3 shrink-0">{footer}</div>}
      </div>
    </div>,
    document.body,
  );
}

/* ---------- Tabs ---------- */
export function Tabs<T extends string>({
  tabs,
  value,
  onChange,
}: {
  tabs: { id: T; label: string; icon?: ReactNode }[];
  value: T;
  onChange: (v: T) => void;
}) {
  return (
    <div role="tablist" className="flex gap-1 border-b border-line overflow-x-auto">
      {tabs.map((tb) => (
        <button
          key={tb.id}
          role="tab"
          aria-selected={value === tb.id}
          onClick={() => onChange(tb.id)}
          className={cx(
            "h-11 px-4 flex items-center gap-2 text-sm font-semibold whitespace-nowrap border-b-2 -mb-px transition-colors",
            value === tb.id ? "border-accent text-fg" : "border-transparent text-dim hover:text-fg",
          )}
        >
          {tb.icon}
          {tb.label}
        </button>
      ))}
    </div>
  );
}

/* ---------- Context menu ---------- */
export interface MenuItem {
  label: string;
  icon?: ReactNode;
  danger?: boolean;
  onClick: () => void;
}

export function ContextMenu({ x, y, items, onClose }: { x: number; y: number; items: MenuItem[]; onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState({ x, y });
  useEffect(() => {
    const r = ref.current?.getBoundingClientRect();
    if (r) setPos({ x: Math.min(x, window.innerWidth - r.width - 8), y: Math.min(y, window.innerHeight - r.height - 8) });
    const close = () => onClose();
    const key = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("mousedown", close);
    window.addEventListener("keydown", key);
    window.addEventListener("blur", close);
    return () => {
      window.removeEventListener("mousedown", close);
      window.removeEventListener("keydown", key);
      window.removeEventListener("blur", close);
    };
  }, [x, y, onClose]);
  return createPortal(
    <div
      ref={ref}
      style={{ left: pos.x, top: pos.y }}
      onMouseDown={(e) => e.stopPropagation()}
      className="anim-pop fixed z-[60] min-w-56 rounded-[12px] bg-surface-2 border border-line shadow-2xl p-1.5"
    >
      {items.map((it) => (
        <button
          key={it.label}
          onClick={() => {
            onClose();
            it.onClick();
          }}
          className={cx(
            "w-full h-10 px-3 rounded-[8px] flex items-center gap-3 text-sm text-left hover:bg-surface-3",
            it.danger && "text-bad",
          )}
        >
          {it.icon}
          {it.label}
        </button>
      ))}
    </div>,
    document.body,
  );
}

/* ---------- Toasts ---------- */
export function Toasts() {
  const toasts = useApp((s) => s.toasts);
  const dismiss = useApp((s) => s.dismissToast);
  return (
    <div className="fixed bottom-5 right-5 z-[70] flex flex-col gap-2 w-96 max-w-[calc(100vw-2.5rem)]" aria-live="polite">
      {toasts.map((tt) => (
        <div
          key={tt.id}
          onClick={() => dismiss(tt.id)}
          className={cx(
            "anim-toast selectable cursor-pointer rounded-[12px] border px-4 py-3 text-sm shadow-xl bg-surface-2",
            tt.kind === "error" && "border-bad/50",
            tt.kind === "ok" && "border-ok/50",
            tt.kind === "info" && "border-line",
          )}
        >
          {tt.text}
        </div>
      ))}
    </div>
  );
}

export function LoaderIcon({ loader, className }: { loader: string; className?: string }) {
  const colors: Record<string, string> = {
    fabric: "#dbb69b",
    quilt: "#c796f9",
    neoforge: "#f6993f",
    forge: "#5d7ab8",
    vanilla: "#8bd08b",
  };
  return (
    <span
      className={cx("inline-block rounded-sm", className ?? "size-2.5")}
      style={{ background: colors[loader] ?? "var(--text-faint)" }}
      aria-hidden
    />
  );
}
