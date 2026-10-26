import { create } from "zustand";
import { listen } from "@tauri-apps/api/event";
import {
  api,
  errText,
  type Account,
  type GameExit,
  type Instance,
  type Progress,
  type Settings,
  type SystemInfo,
} from "./api";
import { translate, type Key } from "../i18n";

export type Route =
  | { page: "home" }
  | { page: "library" }
  | { page: "instance"; id: string; tab?: string }
  | { page: "catalog"; query?: string; type?: string; instanceId?: string; project?: string }
  | { page: "skins" }
  | { page: "accounts" }
  | { page: "settings" };

export interface Toast {
  id: number;
  kind: "info" | "ok" | "error";
  text: string;
}

interface State {
  ready: boolean;
  settings: Settings | null;
  system: SystemInfo | null;
  instances: Instance[];
  accounts: Account[];
  running: string[];
  progress: Record<string, Progress>;
  crash: GameExit | null;
  toasts: Toast[];
  route: Route;
  history: Route[];
  paletteOpen: boolean;
  createOpen: boolean;
  deviceCode: { userCode: string; verificationUri: string; expiresIn: number } | null;
  pendingSkin: string | null;

  init: () => Promise<void>;
  t: (key: Key, vars?: Record<string, string | number>) => string;
  navigate: (r: Route) => void;
  back: () => void;
  refreshInstances: () => Promise<void>;
  refreshAccounts: () => Promise<void>;
  patchSettings: (p: Partial<Settings>) => Promise<void>;
  toast: (kind: Toast["kind"], text: string) => void;
  dismissToast: (id: number) => void;
  setPalette: (v: boolean) => void;
  setCreate: (v: boolean) => void;
  dismissCrash: () => void;
  currentAccount: () => Account | undefined;
}

let toastId = 1;
let saveTimer: ReturnType<typeof setTimeout> | undefined;

export function applyTheme(s: Settings) {
  const root = document.documentElement;
  root.dataset.theme = s.theme;
  root.style.setProperty("--accent", s.accent);
  const m = /^#?([0-9a-f]{6})$/i.exec(s.accent);
  if (m) {
    const n = parseInt(m[1], 16);
    root.style.setProperty("--accent-rgb", `${(n >> 16) & 255} ${(n >> 8) & 255} ${n & 255}`);
    // pick readable text colour on accent (WCAG contrast)
    const lum = (((n >> 16) & 255) * 299 + ((n >> 8) & 255) * 587 + (n & 255) * 114) / 1000;
    root.style.setProperty("--on-accent", lum > 150 ? "#0d0d18" : "#ffffff");
  }
  root.style.fontSize = `${(14 * s.uiScale) / 100}px`;
  root.dataset.reduceMotion = String(s.reduceMotion);
}

export const useApp = create<State>((set, get) => ({
  ready: false,
  settings: null,
  system: null,
  instances: [],
  accounts: [],
  running: [],
  progress: {},
  crash: null,
  toasts: [],
  route: { page: "home" },
  history: [],
  paletteOpen: false,
  createOpen: false,
  deviceCode: null,
  pendingSkin: null,

  t: (key, vars) => translate(get().settings?.language ?? "ru", key, vars),

  init: async () => {
    const [settings, system, instances, accounts, running] = await Promise.all([
      api.getSettings(),
      api.systemInfo(),
      api.listInstances(),
      api.listAccounts(),
      api.running(),
    ]);
    applyTheme(settings);
    set({ settings, system, instances, accounts, running, ready: true });

    await listen<Progress>("progress", (e) => {
      const p = e.payload;
      set((s) => ({ progress: { ...s.progress, [p.id]: p } }));
      if (p.done) {
        setTimeout(() => {
          set((s) => {
            const cur = s.progress[p.id];
            if (cur && cur.done) {
              const { [p.id]: _, ...rest } = s.progress;
              return { progress: rest };
            }
            return {};
          });
        }, p.error ? 15000 : 1500);
        if (p.error) get().toast("error", p.error);
      }
    });
    await listen<string>("game-started", (e) => set((s) => ({ running: [...new Set([...s.running, e.payload])] })));
    await listen<GameExit>("game-exit", (e) => {
      set((s) => ({ running: s.running.filter((r) => r !== e.payload.instanceId) }));
      get().refreshInstances();
      if (e.payload.code !== 0) set({ crash: e.payload });
    });
    await listen<{ userCode: string; verificationUri: string; expiresIn: number }>("auth-code", (e) =>
      set({ deviceCode: e.payload }),
    );
    await listen<string>("tray-launch", (e) => {
      api.launch(e.payload).catch((err) => get().toast("error", errText(err)));
    });

    const startup = await api.takeStartupLaunch();
    if (startup) api.launch(startup).catch((err) => get().toast("error", errText(err)));
  },

  navigate: (r) => set((s) => ({ route: r, history: [...s.history.slice(-20), s.route] })),
  back: () =>
    set((s) => {
      const h = [...s.history];
      const prev = h.pop();
      return prev ? { route: prev, history: h } : { route: { page: "home" } };
    }),

  refreshInstances: async () => {
    set({ instances: await api.listInstances() });
    api.rebuildTray().catch(() => {});
  },
  refreshAccounts: async () => set({ accounts: await api.listAccounts() }),

  patchSettings: async (p) => {
    const cur = get().settings;
    if (!cur) return;
    const next = { ...cur, ...p };
    applyTheme(next);
    set({ settings: next });
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
      api.saveSettings(next).catch((e) => get().toast("error", errText(e)));
    }, 250);
  },

  toast: (kind, text) => {
    const id = toastId++;
    set((s) => ({ toasts: [...s.toasts.slice(-3), { id, kind, text }] }));
    setTimeout(() => get().dismissToast(id), kind === "error" ? 9000 : 3500);
  },
  dismissToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
  setPalette: (v) => set({ paletteOpen: v }),
  setCreate: (v) => set({ createOpen: v }),
  dismissCrash: () => set({ crash: null }),
  currentAccount: () => {
    const { accounts, settings } = get();
    return accounts.find((a) => a.id === settings?.lastAccount) ?? accounts[0];
  },
}));

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} Б`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} КБ`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} МБ`;
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} ГБ`;
}

export function formatPlayTime(secs: number): string {
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  return h > 0 ? `${h} ч ${m} мин` : `${m} мин`;
}

export function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`;
  return String(n);
}
