import { useEffect, useMemo, useRef, useState } from "react";
import { Globe, Home, Library, Package, Search, Settings, Shirt, Users } from "lucide-react";
import { useApp, type Route } from "../lib/store";
import { cx } from "./ui";

interface Entry {
  id: string;
  label: string;
  group: string;
  icon: React.ReactNode;
  run: () => void;
}

/** Ctrl+K quick search: instances, pages/settings and catalog (ТЗ 5.4). */
export default function Palette() {
  const { paletteOpen, setPalette, t, instances, navigate } = useApp();
  const [q, setQ] = useState("");
  const [sel, setSel] = useState(0);
  const input = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (paletteOpen) {
      setQ("");
      setSel(0);
      setTimeout(() => input.current?.focus(), 20);
    }
  }, [paletteOpen]);

  const entries = useMemo<Entry[]>(() => {
    const go = (r: Route) => () => navigate(r);
    const pages: Entry[] = [
      { id: "p-home", label: t("nav.home"), group: t("pal.pages"), icon: <Home className="size-4" />, run: go({ page: "home" }) },
      { id: "p-lib", label: t("nav.library"), group: t("pal.pages"), icon: <Library className="size-4" />, run: go({ page: "library" }) },
      { id: "p-cat", label: t("nav.catalog"), group: t("pal.pages"), icon: <Globe className="size-4" />, run: go({ page: "catalog" }) },
      { id: "p-skin", label: t("nav.skins"), group: t("pal.pages"), icon: <Shirt className="size-4" />, run: go({ page: "skins" }) },
      { id: "p-acc", label: t("nav.accounts"), group: t("pal.pages"), icon: <Users className="size-4" />, run: go({ page: "accounts" }) },
      { id: "p-set", label: t("nav.settings"), group: t("pal.pages"), icon: <Settings className="size-4" />, run: go({ page: "settings" }) },
      { id: "p-set-java", label: `${t("nav.settings")} → ${t("set.java")}`, group: t("pal.pages"), icon: <Settings className="size-4" />, run: go({ page: "settings" }) },
      { id: "p-set-rpc", label: `${t("nav.settings")} → ${t("set.discord")}`, group: t("pal.pages"), icon: <Settings className="size-4" />, run: go({ page: "settings" }) },
    ];
    const inst: Entry[] = instances.map((i) => ({
      id: `i-${i.id}`,
      label: `${i.name} · ${i.mcVersion}`,
      group: t("pal.instances"),
      icon: <Package className="size-4" />,
      run: go({ page: "instance", id: i.id }),
    }));
    const needle = q.trim().toLowerCase();
    const filtered = [...inst, ...pages].filter((e) => !needle || e.label.toLowerCase().includes(needle));
    if (needle) {
      filtered.push({
        id: "cat-q",
        label: t("pal.catalog", { q }),
        group: t("nav.catalog"),
        icon: <Search className="size-4" />,
        run: go({ page: "catalog", query: q.trim() }),
      });
    }
    return filtered.slice(0, 12);
  }, [q, instances, t]);

  if (!paletteOpen) return null;
  const run = (e?: Entry) => {
    if (!e) return;
    setPalette(false);
    e.run();
  };
  return (
    <div className="fixed inset-0 z-[65] flex items-start justify-center pt-[14vh] bg-black/50 backdrop-blur-sm" onMouseDown={() => setPalette(false)}>
      <div
        role="dialog"
        aria-label={t("nav.search")}
        className="anim-pop w-[600px] max-w-[92vw] rounded-[12px] bg-surface border border-line shadow-2xl overflow-hidden"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-3 px-4 h-14 border-b border-line">
          <Search className="size-5 text-faint" />
          <input
            ref={input}
            value={q}
            onChange={(e) => { setQ(e.target.value); setSel(0); }}
            onKeyDown={(e) => {
              if (e.key === "Escape") setPalette(false);
              else if (e.key === "ArrowDown") (e.preventDefault(), setSel((s) => Math.min(entries.length - 1, s + 1)));
              else if (e.key === "ArrowUp") (e.preventDefault(), setSel((s) => Math.max(0, s - 1)));
              else if (e.key === "Enter") run(entries[sel]);
            }}
            placeholder={t("pal.placeholder")}
            className="flex-1 bg-transparent outline-none text-base placeholder:text-faint"
          />
          <kbd className="text-xs text-faint border border-line rounded px-1.5">Esc</kbd>
        </div>
        <div className="max-h-[50vh] overflow-y-auto p-2">
          {entries.length === 0 && <div className="p-6 text-center text-dim">{t("pal.none")}</div>}
          {entries.map((e, i) => (
            <button
              key={e.id}
              onMouseEnter={() => setSel(i)}
              onClick={() => run(e)}
              className={cx("w-full h-11 px-3 rounded-[10px] flex items-center gap-3 text-left", i === sel ? "bg-accent/15" : "hover:bg-surface-2")}
            >
              <span className="text-dim">{e.icon}</span>
              <span className="flex-1 truncate">{e.label}</span>
              <span className="text-xs text-faint">{e.group}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
