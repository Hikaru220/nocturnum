import { invoke } from "@tauri-apps/api/core";

export type Loader = "vanilla" | "fabric" | "quilt" | "neoforge" | "forge";

export interface Settings {
  language: "ru" | "en";
  theme: "dark" | "light";
  accent: string;
  uiScale: number;
  defaultRamMb: number;
  jvmArgs: string;
  customJava: string;
  windowWidth: number;
  windowHeight: number;
  fullscreen: boolean;
  discreteGpu: boolean;
  discordRpc: boolean;
  rpcHideServer: boolean;
  rpcHideInstance: boolean;
  applyPreset: boolean;
  installSodium: boolean;
  installHud: boolean;
  minimizeOnLaunch: boolean;
  downloadsThreads: number;
  firstRunDone: boolean;
  lastInstance: string;
  lastAccount: string;
  background: string;
  reduceMotion: boolean;
  msClientId: string;
  discordAppId: string;
}

export interface Instance {
  id: string;
  name: string;
  mcVersion: string;
  loader: Loader;
  loaderVersion: string;
  versionId: string;
  installed: boolean;
  ramMb: number;
  javaPath: string;
  jvmArgs: string;
  accountId: string;
  icon: string;
  hudEnabled: boolean;
  playTimeSecs: number;
  lastPlayed: number;
  createdAt: number;
  width: number;
  height: number;
  fullscreen: boolean;
  server: string;
  protectedFiles: string[];
  modpackProject: string;
  modpackVersion: string;
}

export interface Account {
  id: string;
  kind: "microsoft" | "offline";
  username: string;
  uuid: string;
  skinUrl: string;
}

export interface SystemInfo {
  totalRamMb: number;
  os: string;
  portable: boolean;
  rootDir: string;
  version: string;
  cpuThreads: number;
}

export interface ManifestVersion {
  id: string;
  type: "release" | "snapshot" | "old_beta" | "old_alpha";
  url: string;
  releaseTime: string;
}

export interface LoaderVersion {
  version: string;
  stable: boolean;
}

export interface ContentFile {
  fileName: string;
  enabled: boolean;
  size: number;
  sha1: string;
  projectId: string;
  versionId: string;
  title: string;
}

export interface JavaInfo {
  path: string;
  major: number;
  version: string;
  managed: boolean;
}

export interface HudStatus {
  supported: boolean;
  reason: string;
  versions: string[];
}

export interface Progress {
  id: string;
  label: string;
  percent: number;
  speed: number;
  done: boolean;
  error: string | null;
}

export interface CrashHint {
  kind: string;
  message: string;
  action: string;
}

export interface GameExit {
  instanceId: string;
  code: number;
  hint: CrashHint | null;
}

export interface UpdateInfo {
  fileName: string;
  projectId: string;
  title: string;
  newVersionId: string;
  newVersionNumber: string;
}

export interface SavedSkin {
  name: string;
  slim: boolean;
  dataUrl: string;
}

export interface NewInstanceReq {
  name: string;
  mcVersion: string;
  loader: Loader;
  loaderVersion: string;
  hudEnabled: boolean;
  accountId: string;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Json = any;

export const api = {
  getSettings: () => invoke<Settings>("get_settings"),
  saveSettings: (settings: Settings) => invoke<void>("save_settings", { settings }),
  systemInfo: () => invoke<SystemInfo>("system_info"),
  setRootDir: (path: string, moveData: boolean) => invoke<void>("set_root_dir", { path, moveData }),
  launcherLog: () => invoke<string>("get_launcher_log"),
  openPath: (path: string) => invoke<void>("open_path", { path }),
  readFileDataUrl: (path: string) => invoke<string>("read_file_data_url", { path }),
  writePng: (path: string, dataUrl: string) => invoke<void>("write_png", { path, dataUrl }),

  mcVersions: () => invoke<{ latest: { release: string; snapshot: string }; versions: ManifestVersion[] }>("mc_versions"),
  loaderVersions: (loader: string, mc: string) => invoke<LoaderVersion[]>("loader_versions", { loader, mc }),
  hudStatus: (mc: string, loader: string) => invoke<HudStatus>("hud_status", { mc, loader }),

  listInstances: () => invoke<Instance[]>("list_instances"),
  createInstance: (req: NewInstanceReq) => invoke<Instance>("create_instance", { req }),
  updateInstance: (instance: Instance) => invoke<void>("update_instance", { instance }),
  deleteInstance: (id: string) => invoke<void>("delete_instance", { id }),
  duplicateInstance: (id: string) => invoke<Instance>("duplicate_instance", { id }),
  openInstanceFolder: (id: string) => invoke<void>("open_instance_folder", { id }),
  applyPresetNow: (id: string) => invoke<void>("apply_preset_now", { id }),
  createShortcut: (id: string) => invoke<void>("create_shortcut", { id }),
  listContent: (id: string, kind: string) => invoke<ContentFile[]>("list_content", { id, kind }),
  toggleContent: (id: string, kind: string, file: string, enable: boolean) =>
    invoke<void>("toggle_content", { id, kind, file, enable }),
  removeContent: (id: string, kind: string, file: string) => invoke<void>("remove_content", { id, kind, file }),
  addContentFile: (id: string, kind: string, path: string) => invoke<string>("add_content_file", { id, kind, path }),
  listScreenshots: (id: string) => invoke<string[]>("list_screenshots", { id }),
  readOptions: (id: string) => invoke<string>("read_options", { id }),

  installInstance: (id: string) => invoke<void>("install_instance", { id }),
  repairInstance: (id: string) => invoke<void>("repair_instance", { id }),
  launch: (id: string) => invoke<void>("launch_instance", { id }),
  kill: (id: string) => invoke<void>("kill_instance", { id }),
  running: () => invoke<string[]>("running_instances"),
  gameLog: (id: string) => invoke<string[]>("game_log", { id }),
  applyCrashFix: (id: string, action: string) => invoke<void>("apply_crash_fix", { id, action }),

  listAccounts: () => invoke<Account[]>("list_accounts"),
  loginMicrosoft: () => invoke<Account>("login_microsoft"),
  cancelLogin: () => invoke<void>("cancel_login"),
  addOffline: (name: string) => invoke<Account>("add_offline_account", { name }),
  removeAccount: (id: string) => invoke<void>("remove_account", { id }),

  detectJava: () => invoke<JavaInfo[]>("detect_java"),
  validateJava: (path: string) => invoke<JavaInfo>("validate_java", { path }),

  mrSearch: (p: {
    query: string;
    projectType: string;
    gameVersion?: string;
    loader?: string;
    category?: string;
    sort: string;
    offset: number;
  }) => invoke<Json>("mr_search", p),
  mrProject: (id: string) => invoke<Json>("mr_project", { id }),
  mrVersions: (id: string, loaders: string[], gameVersions: string[]) =>
    invoke<Json[]>("mr_versions", { id, loaders, gameVersions }),
  mrInstall: (instanceId: string, projectId: string, versionId: string | null, projectType: string) =>
    invoke<string[]>("mr_install", { instanceId, projectId, versionId, projectType }),
  mrCheckUpdates: (instanceId: string) => invoke<UpdateInfo[]>("mr_check_updates", { instanceId }),
  mrUpdateAll: (instanceId: string) => invoke<number>("mr_update_all", { instanceId }),

  importMrpack: (path: string) => invoke<Instance>("import_mrpack", { path }),
  installModpack: (projectId: string, versionId: string | null) =>
    invoke<Instance>("install_modpack", { projectId, versionId }),
  updateModpack: (id: string) => invoke<boolean>("update_modpack", { id }),
  exportMrpack: (id: string, dest: string) => invoke<void>("export_mrpack", { id, dest }),
  importCurseforge: (path: string) => invoke<{ instance: Instance; missing: number }>("import_curseforge", { path }),

  skinByName: (name: string) => invoke<{ dataUrl: string; slim: boolean }>("skin_by_name", { name }),
  skinOfAccount: (id: string) => invoke<{ dataUrl: string; slim: boolean }>("skin_of_account", { id }),
  skinLibrary: () => invoke<SavedSkin[]>("skin_library"),
  skinSave: (name: string, dataUrl: string, slim: boolean) => invoke<void>("skin_save", { name, dataUrl, slim }),
  skinDelete: (name: string) => invoke<void>("skin_delete", { name }),
  skinUpload: (accountId: string, dataUrl: string, slim: boolean) =>
    invoke<void>("skin_upload", { accountId, dataUrl, slim }),

  takeStartupLaunch: () => invoke<string | null>("take_startup_launch"),
  rebuildTray: () => invoke<void>("rebuild_tray"),
  windowHide: () => invoke<void>("window_hide"),
  windowShow: () => invoke<void>("window_show"),
};

export function errText(e: unknown): string {
  if (typeof e === "string") return e;
  if (e && typeof e === "object" && "message" in e) return String((e as { message: unknown }).message);
  return String(e);
}
