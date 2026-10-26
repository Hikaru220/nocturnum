import { useEffect, useState } from "react";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { ChevronsUpDown, Globe, Home as HomeIcon, Library as LibIcon, Plus, Search, Settings as SettingsIcon, Shirt, Users } from "lucide-react";
import { api, errText } from "./lib/api";
import { useApp } from "./lib/store";
import { cx, ContextMenu, Toasts, Spinner } from "./components/ui";
import { Avatar, CreateInstanceModal, CrashModal } from "./components/shared";
import Palette from "./components/Palette";
import Wizard from "./components/Wizard";
import Home from "./pages/Home";
import Library from "./pages/Library";
import InstancePage from "./pages/InstancePage";
import Catalog from "./pages/Catalog";
import Skins from "./pages/Skins";
import Accounts from "./pages/Accounts";
import Settings from "./pages/Settings";

export default function App() {
  const { ready, route, navigate, t, setPalette, setCreate, toast, refreshInstances } = useApp();
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    useApp.getState().init().catch((e) => {
      document.body.dataset.fatal = errText(e);
    });
  }, []);

  // global shortcuts
  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setPalette(true);
      }
    };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, []);

  // drag & drop: .jar → mod, .mrpack → new instance, .png → skin (ТЗ 5.4)
  useEffect(() => {
    if (!ready) return;
    let un: (() => void) | undefined;
    getCurrentWebview()
      .onDragDropEvent(async (ev) => {
        const p = ev.payload;
        if (p.type === "enter" || p.type === "over") return setDragging(true);
        setDragging(false);
        if (p.type !== "drop") return;
        const st = useApp.getState();
        for (const path of p.paths) {
          const lower = path.toLowerCase();
          try {
            if (lower.endsWith(".mrpack")) {
              await api.importMrpack(path);
              await refreshInstances();
              toast("ok", t("lib.imported"));
            } else if (lower.endsWith(".jar")) {
              const target = st.route.page === "instance" ? st.route.id : st.settings?.lastInstance || st.instances[0]?.id;
              if (!target) {
                toast("error", t("drop.noInstance"));
                continue;
              }
              await api.addContentFile(target, "mods", path);
              toast("ok", t("drop.modAdded", { name: st.instances.find((i) => i.id === target)?.name ?? "" }));
            } else if (lower.endsWith(".png")) {
              useApp.setState({ pendingSkin: await api.readFileDataUrl(path) });
              navigate({ page: "skins" });
            }
          } catch (e) {
            toast("error", errText(e));
          }
        }
      })
      .then((f) => (un = f));
    return () => un?.();
  }, [ready]);

  if (!ready) {
    return (
      <div className="h-full flex items-center justify-center">
        <Spinner className="size-8" />
      </div>
    );
  }

  const page = (() => {
    switch (route.page) {
      case "home": return <Home />;
      case "library": return <Library />;
      case "instance": return <InstancePage key={route.id} id={route.id} initialTab={route.tab} />;
      case "catalog": return <Catalog key={`${route.query}|${route.type}|${route.instanceId}|${route.project}`} initialQuery={route.query} initialType={route.type} initialInstance={route.instanceId} initialProject={route.project} />;
      case "skins": return <Skins />;
      case "accounts": return <Accounts />;
      case "settings": return <Settings />;
    }
  })();

  return (
    <div className="h-full flex flex-col">
      <Header />
      <div className="flex-1 flex min-h-0">
        <Sidebar />
        <main className="flex-1 min-w-0 overflow-y-auto" id="main">
          {page}
        </main>
      </div>
      <CreateInstanceModal />
      <CrashModal />
      <Palette />
      <Wizard />
      <Toasts />
      {dragging && (
        <div className="fixed inset-0 z-[80] bg-accent/20 border-4 border-dashed border-accent flex items-center justify-center pointer-events-none">
          <div className="rounded-[12px] bg-surface px-8 py-5 text-lg font-bold shadow-2xl">{t("drop.hint")}</div>
        </div>
      )}
    </div>
  );
}

function Sidebar() {
  const { route, navigate, t, setCreate } = useApp();
  const items = [
    { page: "home", icon: HomeIcon, label: t("nav.home") },
    { page: "library", icon: LibIcon, label: t("nav.library") },
    { page: "catalog", icon: Globe, label: t("nav.catalog") },
    { page: "skins", icon: Shirt, label: t("nav.skins") },
    { page: "accounts", icon: Users, label: t("nav.accounts") },
  ] as const;
  const active = route.page === "instance" ? "library" : route.page;
  return (
    <nav className="w-60 shrink-0 border-r border-line bg-surface/60 p-3 flex flex-col gap-1" aria-label="Main">
      {items.map((it) => (
        <button
          key={it.page}
          onClick={() => navigate({ page: it.page } as never)}
          aria-current={active === it.page ? "page" : undefined}
          className={cx(
            "h-11 px-3 rounded-[10px] flex items-center gap-3 font-semibold transition-colors text-left",
            active === it.page ? "bg-accent/15 text-fg" : "text-dim hover:bg-surface-2 hover:text-fg",
          )}
        >
          <it.icon className={cx("size-5", active === it.page && "text-accent")} />
          {it.label}
        </button>
      ))}
      <button
        onClick={() => setCreate(true)}
        className="h-11 px-3 rounded-[10px] flex items-center gap-3 font-semibold text-accent hover:bg-accent/10 transition-colors mt-2"
      >
        <Plus className="size-5" />
        {t("lib.new")}
      </button>
      <div className="flex-1" />
      <button
        onClick={() => navigate({ page: "settings" })}
        aria-current={active === "settings" ? "page" : undefined}
        className={cx(
          "h-11 px-3 rounded-[10px] flex items-center gap-3 font-semibold transition-colors text-left",
          active === "settings" ? "bg-accent/15 text-fg" : "text-dim hover:bg-surface-2 hover:text-fg",
        )}
      >
        <SettingsIcon className={cx("size-5", active === "settings" && "text-accent")} />
        {t("nav.settings")}
      </button>
    </nav>
  );
}

function Header() {
  const { t, accounts, patchSettings, setPalette, navigate } = useApp();
  const cur = useApp((s) => s.currentAccount());
  const [menu, setMenu] = useState<{ x: number; y: number } | null>(null);
  return (
    <header className="h-16 shrink-0 border-b border-line flex items-center gap-4 px-5 bg-surface/60">
      <img src="/mark.svg" className="size-8" alt="" />
      <span className="font-extrabold tracking-[0.12em] text-sm">NOCTURNUM</span>
      <button
        onClick={() => setPalette(true)}
        className="mx-auto h-10 w-[420px] max-w-[40vw] rounded-[10px] bg-surface-2 border border-line px-3 flex items-center gap-2 text-faint hover:border-faint transition-colors"
      >
        <Search className="size-4" />
        <span className="flex-1 text-left text-sm">{t("nav.search")}</span>
        <kbd className="text-xs border border-line rounded px-1.5">Ctrl K</kbd>
      </button>
      <button
        onClick={(e) => {
          const r = e.currentTarget.getBoundingClientRect();
          if (accounts.length === 0) return navigate({ page: "accounts" });
          setMenu({ x: r.right - 224, y: r.bottom + 6 });
        }}
        className="h-11 pl-2 pr-3 rounded-[10px] flex items-center gap-3 hover:bg-surface-2 transition-colors"
        aria-label={t("nav.accounts")}
      >
        <Avatar account={cur} size={30} />
        <span className="text-sm font-semibold max-w-32 truncate">{cur?.username ?? t("acc.addMicrosoft")}</span>
        <ChevronsUpDown className="size-4 text-faint" />
      </button>
      {menu && (
        <ContextMenu
          x={menu.x}
          y={menu.y}
          onClose={() => setMenu(null)}
          items={[
            ...accounts.map((a) => ({ label: `${a.username}${a.id === cur?.id ? " ✓" : ""}`, onClick: () => patchSettings({ lastAccount: a.id }) })),
            { label: t("nav.accounts"), onClick: () => navigate({ page: "accounts" }) },
          ]}
        />
      )}
    </header>
  );
}
