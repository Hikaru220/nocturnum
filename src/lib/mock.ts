// Dev-only browser mock of the Tauri backend, so the UI can be developed and screenshotted
// with plain `npm run dev` (no Rust build). Never included in production builds.

/* eslint-disable @typescript-eslint/no-explicit-any */
export function installMock() {
  const w = window as any;
  if (w.__TAURI_INTERNALS__) return;
  const callbacks = new Map<number, (d: any) => void>();
  let cbId = 1;
  const settings = {
    language: "ru", theme: "dark", accent: "#8B7CFF", uiScale: 100, defaultRamMb: 4096, jvmArgs: "", customJava: "",
    windowWidth: 854, windowHeight: 480, fullscreen: false, discreteGpu: false, discordRpc: true, rpcHideServer: false,
    rpcHideInstance: false, applyPreset: true, installSodium: true, installHud: true, minimizeOnLaunch: true,
    downloadsThreads: 12, firstRunDone: true, lastInstance: "survival", lastAccount: "a1", background: "", reduceMotion: false,
    msClientId: "", discordAppId: "", perfTier: "auto", installOptimization: true, highPriority: true,
  };
  const mk = (id: string, name: string, mc: string, loader: string, extra: any = {}) => ({
    id, name, mcVersion: mc, loader, loaderVersion: "0.20.1", versionId: "", installed: true, ramMb: 0, javaPath: "", jvmArgs: "",
    accountId: "a1", icon: "", hudEnabled: loader === "fabric", playTimeSecs: 7300, lastPlayed: 1, createdAt: 1, width: 0, height: 0,
    fullscreen: false, server: "", protectedFiles: [], modpackProject: "", modpackVersion: "", ...extra,
  });
  let instances = [mk("survival", "Выживание", "1.21.11", "fabric"), mk("pvp", "PvP сборка", "1.21.11", "fabric", { playTimeSecs: 90000 }), mk("old", "Классика 1.12.2", "1.12.2", "forge", { hudEnabled: false })];
  const accounts = [
    { id: "a1", kind: "microsoft", username: "Nocturn", uuid: "069a79f444e94726a5befca90e38aaf5", skinUrl: "" },
    { id: "a2", kind: "offline", username: "Guest", uuid: "00000000-0000-0000-0000-000000000000", skinUrl: "" },
  ];
  const hits = Array.from({ length: 8 }, (_, i) => ({
    project_id: `p${i}`, title: ["Sodium", "Lithium", "Iris Shaders", "Fabric API", "Mod Menu", "Xaero's Minimap", "JEI", "Fresh Animations"][i],
    author: "author", description: "Оптимизация и улучшение игры — быстрее, плавнее, красивее. Подходит для любой сборки.",
    downloads: 1_200_000 - i * 90000, display_categories: ["optimization", "utility"], icon_url: null,
  }));
  const handlers: Record<string, (a: any) => any> = {
    get_settings: () => settings,
    save_settings: () => null,
    system_info: () => ({ totalRamMb: 16384, os: "windows", portable: false, root_dir: "C:\\Users\\you\\AppData\\Roaming\\Nocturnum", rootDir: "C:\\Users\\you\\AppData\\Roaming\\Nocturnum", version: "0.1.0", cpuThreads: 12 }),
    hardware_info: () => ({ gpu: "NVIDIA GeForce RTX 3060", cpuThreads: 12, ramMb: 16384, tier: "high", integratedGpu: false }),
    list_instances: () => instances,
    list_accounts: () => accounts,
    running_instances: () => [],
    take_startup_launch: () => null,
    rebuild_tray: () => null,
    mc_versions: () => ({ latest: { release: "26.3", snapshot: "26.4-snapshot-5" }, versions: ["26.3", "26.2", "1.21.11", "1.21.10", "1.20.1", "1.16.5", "1.12.2", "1.8.9"].map((id) => ({ id, type: "release", url: "", releaseTime: "" })) }),
    loader_versions: () => [{ version: "0.20.1", stable: true }, { version: "0.20.0", stable: true }],
    hud_status: (a) => (a.loader === "fabric" ? { supported: true, reason: "", versions: ["1.21.11"] } : { supported: false, reason: "HUD-мод работает только с Fabric.", versions: ["1.21.11"] }),
    create_instance: (a) => { const i = mk(a.req.name.toLowerCase(), a.req.name, a.req.mcVersion, a.req.loader); instances = [i, ...instances]; return i; },
    update_instance: (a) => { instances = instances.map((i) => (i.id === a.instance.id ? a.instance : i)); return null; },
    list_content: (a) => (a.kind === "mods" ? [
      { fileName: "sodium-fabric-0.6.jar", enabled: true, size: 1_300_000, sha1: "", projectId: "AANobbMI", versionId: "", title: "Sodium" },
      { fileName: "lithium-fabric-0.14.jar.disabled", enabled: false, size: 800_000, sha1: "", projectId: "gvQqBUqZ", versionId: "", title: "Lithium" },
      { fileName: "nocturnum-hud-1.21.11.jar", enabled: true, size: 2_100_000, sha1: "", projectId: "", versionId: "", title: "" },
    ] : []),
    list_screenshots: () => [],
    game_log: () => ["[12:00:01] [main/INFO]: Loading Minecraft 1.21.11 with Fabric Loader 0.20.1", "[12:00:03] [Render thread/WARN]: Something harmless", "[12:00:05] [Render thread/ERROR]: Example error line"],
    detect_java: () => [{ path: "C:\\Program Files\\Java\\jdk-21\\bin\\java.exe", major: 21, version: "21.0.10", managed: false }],
    mr_search: () => ({ hits, total_hits: 100 }),
    mr_project: (a) => ({ id: a.id, title: "Sodium", description: "Оптимизация рендера", downloads: 40000000, icon_url: null, gallery: [], loaders: ["fabric", "quilt"], project_type: "mod", slug: "sodium" }),
    mr_versions: () => [{ id: "v1", name: "Sodium 0.6.0", version_number: "0.6.0", game_versions: ["1.21.11"], loaders: ["fabric"], version_type: "release" }],
    skin_library: () => [],
    check_updates: () => [],
    mr_check_updates: () => [],
  };
  w.__TAURI_INTERNALS__ = {
    metadata: { currentWindow: { label: "main" }, currentWebview: { label: "main", windowLabel: "main" }, windows: [], webviews: [] },
    transformCallback: (cb: (d: any) => void) => { const id = cbId++; callbacks.set(id, cb); return id; },
    unregisterCallback: (id: number) => callbacks.delete(id),
    convertFileSrc: (s: string) => s,
    invoke: async (cmd: string, args: any) => {
      if (cmd.startsWith("plugin:event|")) return cmd.endsWith("listen") ? cbId++ : null;
      if (cmd.startsWith("plugin:")) return null;
      const h = handlers[cmd];
      if (!h) { console.warn("[mock] unhandled", cmd, args); return null; }
      return h(args ?? {});
    },
  };
  w.__NOCTURNUM_MOCK__ = true;
}
