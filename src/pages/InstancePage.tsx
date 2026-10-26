import { useCallback, useEffect, useRef, useState } from "react";
import { listen } from "@tauri-apps/api/event";
import {
  ArrowLeft, Check, Copy, FolderOpen, Image as ImageIcon, Layers, Package, Plus, RefreshCw, ScrollText, Settings2, Sparkles, Trash2, Wrench,
} from "lucide-react";
import { api, errText, type ContentFile, type HudStatus, type Instance, type JavaInfo, type UpdateInfo } from "../lib/api";
import { formatBytes, formatPlayTime, useApp } from "../lib/store";
import { Badge, Button, Card, Empty, Field, IconButton, Input, Modal, Select, Skeleton, Slider, Tabs, Toggle } from "../components/ui";
import { InlineProgress, InstanceIcon, LoaderBadge, PlayButton, pickFiles } from "../components/shared";

type Tab = "mods" | "resourcepacks" | "shaderpacks" | "screenshots" | "settings" | "log";

export default function InstancePage({ id, initialTab }: { id: string; initialTab?: string }) {
  const { t, instances, back, navigate } = useApp();
  const inst = instances.find((i) => i.id === id);
  const [tab, setTab] = useState<Tab>((initialTab as Tab) ?? "mods");
  useEffect(() => {
    if (initialTab) setTab(initialTab as Tab);
  }, [initialTab]);
  if (!inst) {
    return <Empty title={t("common.nothing")} action={<Button onClick={() => navigate({ page: "library" })}>{t("nav.library")}</Button>} />;
  }
  return (
    <div className="anim-page p-8 max-w-6xl mx-auto">
      <div className="flex items-center gap-4 mb-6">
        <IconButton label={t("common.back")} onClick={back}>
          <ArrowLeft className="size-5" />
        </IconButton>
        <InstanceIcon inst={inst} size={64} />
        <div className="min-w-0 flex-1">
          <h1 className="text-2xl font-extrabold truncate">{inst.name}</h1>
          <div className="flex items-center gap-3">
            <LoaderBadge inst={inst} />
            {inst.loaderVersion && <span className="text-xs text-faint">{inst.loaderVersion}</span>}
            {inst.hudEnabled && <Badge tone="accent">HUD</Badge>}
          </div>
        </div>
        <IconButton label={t("inst.openFolder")} onClick={() => api.openInstanceFolder(inst.id)}>
          <FolderOpen className="size-5" />
        </IconButton>
        <PlayButton inst={inst} big />
      </div>
      <InlineProgress id={`install:${inst.id}`} />
      <div className="mt-4">
        <Tabs<Tab>
          value={tab}
          onChange={setTab}
          tabs={[
            { id: "mods", label: t("inst.tab.mods"), icon: <Package className="size-4" /> },
            { id: "resourcepacks", label: t("inst.tab.resourcepacks"), icon: <Layers className="size-4" /> },
            { id: "shaderpacks", label: t("inst.tab.shaderpacks"), icon: <Sparkles className="size-4" /> },
            { id: "screenshots", label: t("inst.tab.screenshots"), icon: <ImageIcon className="size-4" /> },
            { id: "settings", label: t("inst.tab.settings"), icon: <Settings2 className="size-4" /> },
            { id: "log", label: t("inst.tab.log"), icon: <ScrollText className="size-4" /> },
          ]}
        />
      </div>
      <div className="pt-6">
        {(tab === "mods" || tab === "resourcepacks" || tab === "shaderpacks") && <ContentTab key={tab} inst={inst} kind={tab} />}
        {tab === "screenshots" && <Screenshots inst={inst} />}
        {tab === "settings" && <InstanceSettings inst={inst} />}
        {tab === "log" && <LogTab inst={inst} />}
      </div>
    </div>
  );
}

/* ---------- mods / packs ---------- */
function ContentTab({ inst, kind }: { inst: Instance; kind: "mods" | "resourcepacks" | "shaderpacks" }) {
  const { t, navigate, toast } = useApp();
  const [files, setFiles] = useState<ContentFile[] | null>(null);
  const [updates, setUpdates] = useState<UpdateInfo[] | null>(null);
  const [checking, setChecking] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [del, setDel] = useState<ContentFile | null>(null);

  const reload = useCallback(() => api.listContent(inst.id, kind).then(setFiles).catch((e) => toast("error", errText(e))), [inst.id, kind]);
  useEffect(() => {
    reload();
    const un = listen("content-changed", reload);
    return () => {
      un.then((f) => f());
    };
  }, [reload]);
  // Refresh when background installs finish
  const progress = useApp((s) => s.progress);
  const busyKey = Object.values(progress).filter((p) => p.done && !p.error).length;
  useEffect(() => {
    reload();
  }, [busyKey]);

  const projectType = kind === "mods" ? "mod" : kind === "resourcepacks" ? "resourcepack" : "shader";
  const exts = kind === "mods" ? ["jar"] : ["zip"];

  const addFile = async () => {
    const picked = await pickFiles([{ name: kind, extensions: exts }], true);
    try {
      for (const p of picked) await api.addContentFile(inst.id, kind, p);
      await reload();
    } catch (e) {
      toast("error", errText(e));
    }
  };

  const check = async () => {
    setChecking(true);
    try {
      const u = await api.mrCheckUpdates(inst.id);
      setUpdates(u);
      toast(u.length ? "info" : "ok", u.length ? t("inst.updatesFound", { n: u.length }) : t("inst.upToDate"));
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setChecking(false);
    }
  };
  const updateAll = async () => {
    setUpdating(true);
    try {
      const n = await api.mrUpdateAll(inst.id);
      toast("ok", t("inst.updated", { n }));
      setUpdates(null);
      await reload();
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setUpdating(false);
    }
  };

  return (
    <div>
      <div className="flex flex-wrap gap-3 mb-4">
        <Button variant="primary" icon={<Plus className="size-4" />} onClick={() => navigate({ page: "catalog", type: projectType, instanceId: inst.id })}>
          {t("inst.addFromCatalog")}
        </Button>
        <Button icon={<Plus className="size-4" />} onClick={addFile}>
          {t("inst.addFile")}
        </Button>
        {kind === "mods" && (
          <>
            <Button icon={<RefreshCw className="size-4" />} loading={checking} onClick={check}>
              {t("inst.checkUpdates")}
            </Button>
            {updates && updates.length > 0 && (
              <Button variant="primary" loading={updating} onClick={updateAll}>
                {t("inst.updateAll")} ({updates.length})
              </Button>
            )}
          </>
        )}
      </div>
      {files === null ? (
        <div className="space-y-2">
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-14" />
          ))}
        </div>
      ) : files.length === 0 ? (
        <Empty title={kind === "mods" ? t("inst.modsEmpty") : t("inst.packsEmpty")} />
      ) : (
        <div className="space-y-2">
          {files.map((f) => {
            const up = updates?.find((u) => f.fileName.replace(/\.disabled$/, "") === u.fileName.replace(/\.disabled$/, ""));
            return (
              <Card key={f.fileName} className="px-4 h-14 flex items-center gap-4">
                <Toggle checked={f.enabled} label={f.fileName} onChange={(v) => api.toggleContent(inst.id, kind, f.fileName, v).then(reload).catch((e) => toast("error", errText(e)))} />
                <div className={`min-w-0 flex-1 ${f.enabled ? "" : "opacity-50"}`}>
                  <div className="font-semibold truncate">{f.title || f.fileName.replace(/\.disabled$/, "")}</div>
                  {f.title && <div className="text-xs text-faint truncate">{f.fileName.replace(/\.disabled$/, "")}</div>}
                </div>
                {up && <Badge tone="accent">→ {up.newVersionNumber}</Badge>}
                {!f.enabled && <Badge>{t("inst.disabled")}</Badge>}
                <span className="text-xs text-faint w-16 text-right">{formatBytes(f.size)}</span>
                <IconButton label={t("common.delete")} onClick={() => setDel(f)}>
                  <Trash2 className="size-4" />
                </IconButton>
              </Card>
            );
          })}
        </div>
      )}
      <Modal
        open={!!del}
        onClose={() => setDel(null)}
        title={t("common.delete")}
        width={440}
        footer={
          <>
            <Button variant="ghost" onClick={() => setDel(null)}>
              {t("common.cancel")}
            </Button>
            <Button
              variant="danger"
              onClick={async () => {
                const f = del!;
                setDel(null);
                await api.removeContent(inst.id, kind, f.fileName).catch((e) => toast("error", errText(e)));
                reload();
              }}
            >
              {t("common.delete")}
            </Button>
          </>
        }
      >
        {t("inst.removeFile", { name: del?.fileName ?? "" })}
      </Modal>
    </div>
  );
}

/* ---------- screenshots ---------- */
function Screenshots({ inst }: { inst: Instance }) {
  const { t } = useApp();
  const [paths, setPaths] = useState<string[] | null>(null);
  const [urls, setUrls] = useState<Record<string, string>>({});
  const [big, setBig] = useState<string | null>(null);
  useEffect(() => {
    api.listScreenshots(inst.id).then(async (p) => {
      setPaths(p);
      for (const f of p.slice(0, 24)) {
        api.readFileDataUrl(f).then((u) => setUrls((m) => ({ ...m, [f]: u }))).catch(() => {});
      }
    });
  }, [inst.id]);
  if (paths === null) return <Skeleton className="h-40" />;
  if (!paths.length) return <Empty icon={<ImageIcon className="size-10" />} title={t("inst.screensEmpty")} />;
  return (
    <>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(220px,1fr))] gap-3">
        {paths.slice(0, 24).map((p) => (
          <button key={p} onClick={() => setBig(p)} className="aspect-video rounded-[10px] overflow-hidden bg-surface-2 border border-line hover:border-accent transition-colors">
            {urls[p] ? <img src={urls[p]} className="w-full h-full object-cover" alt="" /> : <Skeleton className="w-full h-full" />}
          </button>
        ))}
      </div>
      <Modal open={!!big} onClose={() => setBig(null)} title={big?.split(/[\\/]/).pop() ?? ""} width={960}>
        {big && urls[big] && <img src={urls[big]} className="w-full rounded-[10px]" alt="" />}
      </Modal>
    </>
  );
}

/* ---------- log ---------- */
function LogTab({ inst }: { inst: Instance }) {
  const { t, toast } = useApp();
  const [lines, setLines] = useState<string[]>([]);
  const box = useRef<HTMLDivElement>(null);
  const stick = useRef(true);
  useEffect(() => {
    api.gameLog(inst.id).then(setLines);
    const un = listen<{ instanceId: string; line: string }>("game-log", (e) => {
      if (e.payload.instanceId === inst.id) setLines((l) => (l.length > 4000 ? [...l.slice(-3000), e.payload.line] : [...l, e.payload.line]));
    });
    return () => {
      un.then((f) => f());
    };
  }, [inst.id]);
  useEffect(() => {
    if (stick.current && box.current) box.current.scrollTop = box.current.scrollHeight;
  }, [lines]);
  return (
    <div>
      <div className="flex gap-3 mb-3">
        <Button icon={<Copy className="size-4" />} onClick={() => navigator.clipboard.writeText(lines.join("\n")).then(() => toast("ok", t("common.copied")))}>
          {t("inst.copyLog")}
        </Button>
        <Button variant="ghost" onClick={() => setLines([])}>
          {t("inst.clearLog")}
        </Button>
      </div>
      <div
        ref={box}
        onScroll={(e) => {
          const el = e.currentTarget;
          stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 40;
        }}
        className="selectable h-[48vh] overflow-auto rounded-[12px] bg-bg border border-line p-4 font-mono text-xs leading-5 whitespace-pre-wrap break-all"
      >
        {lines.length === 0 ? <span className="text-faint">{t("inst.logEmpty")}</span> : lines.map((l, i) => (
          <div key={i} className={/ERROR|Exception|FATAL/.test(l) ? "text-bad" : /WARN/.test(l) ? "text-warn" : "text-dim"}>
            {l}
          </div>
        ))}
      </div>
    </div>
  );
}

/* ---------- instance settings ---------- */
function InstanceSettings({ inst }: { inst: Instance }) {
  const { t, accounts, system, settings, refreshInstances, toast } = useApp();
  const [draft, setDraft] = useState<Instance>(inst);
  const [javas, setJavas] = useState<JavaInfo[]>([]);
  const [hud, setHud] = useState<HudStatus | null>(null);
  const [showAdv, setShowAdv] = useState(false);
  const [busy, setBusy] = useState("");
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => setDraft(inst), [inst.id]);
  useEffect(() => {
    api.detectJava().then(setJavas).catch(() => {});
    api.hudStatus(inst.mcVersion, inst.loader).then(setHud).catch(() => {});
  }, [inst.id]);

  const patch = (p: Partial<Instance>) => {
    const next = { ...draft, ...p };
    setDraft(next);
    clearTimeout(timer.current);
    timer.current = setTimeout(async () => {
      // always merge into latest persisted copy so play time etc. isn't overwritten
      const latest = (await api.listInstances()).find((i) => i.id === inst.id);
      if (!latest) return;
      await api.updateInstance({ ...latest, ...p, ...pickChanged(next, inst) }).catch((e) => toast("error", errText(e)));
      refreshInstances();
    }, 400);
  };
  const maxRam = Math.max(2048, Math.min(32768, Math.floor(((system?.totalRamMb ?? 8192) * 0.85) / 512) * 512));
  const ram = draft.ramMb || settings?.defaultRamMb || 4096;
  const run = async (key: string, fn: () => Promise<unknown>, ok?: string) => {
    setBusy(key);
    try {
      await fn();
      if (ok) toast("ok", ok);
      await refreshInstances();
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setBusy("");
    }
  };

  return (
    <div className="grid lg:grid-cols-2 gap-x-10 max-w-5xl">
      <div>
        <Field label={t("inst.rename")}>
          <Input value={draft.name} maxLength={48} onChange={(e) => patch({ name: e.target.value })} />
        </Field>
        <Field label={t("inst.memory")} hint={t("inst.memoryHint")}>
          <Slider value={Math.min(ram, maxRam)} min={1024} max={maxRam} step={512} onChange={(v) => patch({ ramMb: v })} format={(v) => `${(v / 1024).toFixed(1)} ГБ`} />
        </Field>
        <Field label={t("inst.account")}>
          <Select value={draft.accountId} onChange={(e) => patch({ accountId: e.target.value })}>
            <option value="">{t("inst.accountDefault")}</option>
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.username} · {a.kind === "microsoft" ? t("acc.licensed") : t("acc.offline")}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("inst.server")} hint={t("inst.serverHint")}>
          <Input value={draft.server} placeholder="play.example.com" onChange={(e) => patch({ server: e.target.value.trim() })} />
        </Field>
        <Field label={t("inst.hud")} row hint={hud && !hud.supported ? hud.reason : undefined}>
          <Toggle checked={draft.hudEnabled && !!hud?.supported} disabled={!hud?.supported} onChange={(v) => patch({ hudEnabled: v })} />
        </Field>
        {draft.modpackProject && (
          <Field label={t("inst.modpack")} hint={t("inst.modpackProtected")} row>
            <Button
              loading={busy === "mp"}
              onClick={() =>
                run("mp", async () => {
                  const upd = await api.updateModpack(inst.id);
                  toast("ok", upd ? t("inst.modpackUpdated") : t("inst.modpackUpToDate"));
                })
              }
            >
              {t("inst.updateModpack")}
            </Button>
          </Field>
        )}
      </div>
      <div>
        <Field label={t("inst.repair")} hint={inst.installed ? t("inst.repairHint") : t("inst.notInstalled")} row>
          <Button icon={<Wrench className="size-4" />} loading={busy === "repair"} onClick={() => run("repair", () => api.repairInstance(inst.id), t("common.done"))}>
            {inst.installed ? t("inst.repair") : t("inst.install")}
          </Button>
        </Field>
        <Field label={t("inst.preset")} row>
          <Button icon={<Check className="size-4" />} onClick={() => run("preset", () => api.applyPresetNow(inst.id), t("inst.presetDone"))}>
            {t("common.save")}
          </Button>
        </Field>
        <div className="text-xs text-faint py-2">
          {t("inst.playTime", { time: inst.playTimeSecs ? formatPlayTime(inst.playTimeSecs) : t("inst.neverPlayed") })}
        </div>
        <button className="mt-2 text-sm font-semibold text-accent hover:underline" onClick={() => setShowAdv(!showAdv)}>
          {t("common.advanced")} {showAdv ? "▲" : "▼"}
        </button>
        {showAdv && (
          <div className="anim-page">
            <Field label={t("inst.java")}>
              <Select value={draft.javaPath} onChange={(e) => patch({ javaPath: e.target.value })}>
                <option value="">{t("inst.javaAuto")}</option>
                {javas.map((j) => (
                  <option key={j.path} value={j.path}>
                    Java {j.major} ({j.version}){j.managed ? " · Nocturnum" : ""}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label={t("inst.jvmArgs")}>
              <Input value={draft.jvmArgs} placeholder="-XX:+UseZGC" onChange={(e) => patch({ jvmArgs: e.target.value })} />
            </Field>
            <Field label={t("inst.resolution")}>
              <div className="flex items-center gap-2">
                <Input type="number" min={0} value={draft.width || ""} placeholder={String(settings?.windowWidth ?? 854)} onChange={(e) => patch({ width: Number(e.target.value) || 0 })} />
                <span className="text-faint">×</span>
                <Input type="number" min={0} value={draft.height || ""} placeholder={String(settings?.windowHeight ?? 480)} onChange={(e) => patch({ height: Number(e.target.value) || 0 })} />
              </div>
            </Field>
            <Field label={t("set.fullscreen")} row>
              <Toggle checked={draft.fullscreen} onChange={(v) => patch({ fullscreen: v })} />
            </Field>
          </div>
        )}
      </div>
    </div>
  );
}

// fields the user edits in the settings form (everything else keeps the stored value)
function pickChanged(next: Instance, base: Instance): Partial<Instance> {
  const keys: (keyof Instance)[] = ["name", "ramMb", "accountId", "server", "hudEnabled", "javaPath", "jvmArgs", "width", "height", "fullscreen"];
  const out: Partial<Instance> = {};
  for (const k of keys) if (next[k] !== base[k]) (out as Record<string, unknown>)[k] = next[k];
  return out;
}
