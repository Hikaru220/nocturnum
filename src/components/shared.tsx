import { useEffect, useMemo, useState } from "react";
import { open as openDialog } from "@tauri-apps/plugin-dialog";
import { openUrl } from "@tauri-apps/plugin-opener";
import { Copy, ExternalLink, Gamepad2, Square, Wrench } from "lucide-react";
import { api, errText, type Account, type Instance, type Loader } from "../lib/api";
import { formatBytes, useApp } from "../lib/store";
import { Badge, Button, cx, LoaderIcon, Modal, ProgressBar, Spinner, Input, Select, Checkbox, Field } from "./ui";

export function Avatar({ account, size = 32 }: { account?: Pick<Account, "uuid" | "username" | "kind">; size?: number }) {
  const [bad, setBad] = useState(false);
  const letter = (account?.username ?? "?").slice(0, 1).toUpperCase();
  const useHead = account && account.kind === "microsoft" && !bad;
  return useHead ? (
    <img
      src={`https://crafatar.com/avatars/${account.uuid}?size=${size * 2}&overlay`}
      width={size}
      height={size}
      alt=""
      className="rounded-[8px] bg-surface-3"
      style={{ imageRendering: "pixelated" }}
      onError={() => setBad(true)}
    />
  ) : (
    <div
      style={{ width: size, height: size }}
      className="rounded-[8px] bg-accent/20 text-accent font-bold flex items-center justify-center"
    >
      {letter}
    </div>
  );
}

export function instanceColor(id: string): string {
  let h = 0;
  for (const c of id) h = (h * 31 + c.charCodeAt(0)) % 360;
  return `hsl(${h} 55% 45%)`;
}

export function InstanceIcon({ inst, size = 48 }: { inst: Instance; size?: number }) {
  return (
    <div
      style={{ width: size, height: size, background: `linear-gradient(135deg, ${instanceColor(inst.id)}, var(--surface-3))` }}
      className="rounded-[10px] flex items-center justify-center font-extrabold text-white/90 shrink-0"
    >
      <span style={{ fontSize: size * 0.42 }}>{inst.name.slice(0, 1).toUpperCase()}</span>
    </div>
  );
}

export function LoaderBadge({ inst }: { inst: Pick<Instance, "loader" | "mcVersion"> }) {
  const t = useApp((s) => s.t);
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-dim">
      <LoaderIcon loader={inst.loader} />
      {t(`loader.${inst.loader}` as const)} · {inst.mcVersion}
    </span>
  );
}

/** Big play button: download/launch progress is shown inside it (ТЗ 5.1.1). */
export function PlayButton({ inst, big, className }: { inst: Instance; big?: boolean; className?: string }) {
  const t = useApp((s) => s.t);
  const running = useApp((s) => s.running.includes(inst.id));
  const prog = useApp((s) => s.progress[`launch:${inst.id}`] ?? s.progress[`install:${inst.id}`]);
  const settings = useApp((s) => s.settings);
  const accounts = useApp((s) => s.accounts);
  const toast = useApp((s) => s.toast);
  const busy = !!prog && !prog.done;
  const hasAccount = !!inst.accountId || accounts.length > 0;

  if (running) {
    return (
      <Button variant="danger" big={big} className={className} icon={<Square className="size-4" />} onClick={() => api.kill(inst.id)}>
        {t("home.stop")}
      </Button>
    );
  }
  return (
    <button
      disabled={busy}
      onClick={async () => {
        if (!hasAccount && !settings?.lastAccount) return toast("error", t("home.noAccount"));
        try {
          await api.launch(inst.id);
        } catch (e) {
          toast("error", errText(e));
        }
      }}
      className={cx(
        "relative overflow-hidden inline-flex items-center justify-center gap-3 font-bold transition-all active:scale-[0.99] disabled:cursor-wait",
        "bg-accent text-[var(--on-accent)] hover:brightness-110 glow",
        big ? "h-14 px-10 text-lg rounded-[12px] min-w-64" : "h-10 px-6 text-sm rounded-[10px] min-w-32",
        className,
      )}
    >
      {busy && (
        <span
          className="absolute inset-y-0 left-0 bg-white/25 transition-[width] duration-200"
          style={{ width: `${prog!.percent}%` }}
        />
      )}
      <span className="relative flex items-center gap-2">
        {busy ? <Spinner className="size-4 text-current" /> : <Gamepad2 className={big ? "size-6" : "size-4"} />}
        {busy ? (
          <>
            {prog!.label}
            {prog!.percent > 0 && prog!.percent < 100 && ` ${Math.round(prog!.percent)}%`}
            {prog!.speed > 0 && <span className="opacity-70 font-medium text-sm">· {t("launch.speed", { speed: formatBytes(prog!.speed) })}</span>}
          </>
        ) : (
          t("common.play")
        )}
      </span>
    </button>
  );
}

export function InlineProgress({ id }: { id: string }) {
  const p = useApp((s) => s.progress[id]);
  if (!p || p.done) return null;
  return (
    <div className="mt-2">
      <div className="flex justify-between text-xs text-dim mb-1">
        <span>{p.label}</span>
        <span>
          {Math.round(p.percent)}%{p.speed > 0 && ` · ${formatBytes(p.speed)}/с`}
        </span>
      </div>
      <ProgressBar value={p.percent} />
    </div>
  );
}

/* ---------- Create instance ---------- */
export function CreateInstanceModal() {
  const { createOpen, setCreate, t, accounts, settings, toast, refreshInstances, navigate } = useApp();
  const [name, setName] = useState("");
  const [mc, setMc] = useState("");
  const [filter, setFilter] = useState<"release" | "snapshot" | "old">("release");
  const [loader, setLoader] = useState<Loader>("fabric");
  const [loaderVersion, setLoaderVersion] = useState("");
  const [hud, setHud] = useState(true);
  const [all, setAll] = useState<{ id: string; type: string }[]>([]);
  const [loaderVers, setLoaderVers] = useState<{ version: string; stable: boolean }[] | null>(null);
  const [hudStatus, setHudStatus] = useState<{ supported: boolean; reason: string } | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!createOpen) return;
    api
      .mcVersions()
      .then((m) => {
        setAll(m.versions);
        setMc((cur) => cur || m.latest.release);
      })
      .catch((e) => toast("error", errText(e)));
    setHud(settings?.installHud ?? true);
  }, [createOpen]);

  const versions = useMemo(
    () =>
      all.filter((v) =>
        filter === "release" ? v.type === "release" : filter === "snapshot" ? v.type === "snapshot" : v.type.startsWith("old"),
      ),
    [all, filter],
  );

  useEffect(() => {
    if (!mc || loader === "vanilla") {
      setLoaderVers([]);
      setLoaderVersion("");
      return;
    }
    let live = true;
    setLoaderVers(null);
    api
      .loaderVersions(loader, mc)
      .then((v) => {
        if (!live) return;
        setLoaderVers(v);
        setLoaderVersion((v.find((x) => x.stable) ?? v[0])?.version ?? "");
      })
      .catch(() => live && (setLoaderVers([]), setLoaderVersion("")));
    return () => {
      live = false;
    };
  }, [mc, loader]);

  useEffect(() => {
    if (mc) api.hudStatus(mc, loader).then(setHudStatus).catch(() => {});
  }, [mc, loader]);

  const loaderMissing = loader !== "vanilla" && loaderVers !== null && loaderVers.length === 0;
  const canCreate = name.trim() && mc && !loaderMissing && loaderVers !== null && !busy;

  const submit = async () => {
    setBusy(true);
    try {
      const inst = await api.createInstance({
        name: name.trim(),
        mcVersion: mc,
        loader,
        loaderVersion,
        hudEnabled: hud && !!hudStatus?.supported,
        accountId: settings?.lastAccount || accounts[0]?.id || "",
      });
      await refreshInstances();
      toast("ok", t("create.created"));
      setCreate(false);
      setName("");
      navigate({ page: "instance", id: inst.id });
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setBusy(false);
    }
  };

  const loaders: Loader[] = ["vanilla", "fabric", "quilt", "neoforge", "forge"];
  return (
    <Modal
      open={createOpen}
      onClose={() => setCreate(false)}
      title={t("create.title")}
      footer={
        <>
          <Button variant="ghost" onClick={() => setCreate(false)}>
            {t("common.cancel")}
          </Button>
          <Button variant="primary" disabled={!canCreate} loading={busy} onClick={submit}>
            {t("common.create")}
          </Button>
        </>
      }
    >
      <div className="space-y-1">
        <Field label={t("create.name")}>
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder={t("create.namePlaceholder")} maxLength={48} />
        </Field>
        <Field label={t("create.loader")}>
          <div className="grid grid-cols-5 gap-2">
            {loaders.map((l) => (
              <button
                key={l}
                onClick={() => setLoader(l)}
                className={cx(
                  "h-10 rounded-[10px] text-sm font-semibold border transition-colors",
                  loader === l ? "bg-accent/15 border-accent text-fg" : "bg-surface-2 border-line text-dim hover:text-fg",
                )}
              >
                {t(`loader.${l}` as const)}
              </button>
            ))}
          </div>
        </Field>
        <Field label={t("create.gameVersion")}>
          <div className="flex gap-2 mb-2">
            {(["release", "snapshot", "old"] as const).map((f) => (
              <button
                key={f}
                onClick={() => setFilter(f)}
                className={cx(
                  "h-8 px-3 rounded-full text-xs font-semibold",
                  filter === f ? "bg-accent text-[var(--on-accent)]" : "bg-surface-2 text-dim hover:text-fg",
                )}
              >
                {t(f === "release" ? "create.releases" : f === "snapshot" ? "create.snapshots" : "create.old")}
              </button>
            ))}
          </div>
          <Select value={mc} onChange={(e) => setMc(e.target.value)}>
            {all.length === 0 && <option>{t("common.loading")}</option>}
            {versions.map((v) => (
              <option key={v.id} value={v.id}>
                {v.id}
              </option>
            ))}
            {mc && !versions.some((v) => v.id === mc) && <option value={mc}>{mc}</option>}
          </Select>
        </Field>
        {loader !== "vanilla" && (
          <Field label={t("create.loaderVersion")}>
            {loaderVers === null ? (
              <div className="h-10 flex items-center gap-2 text-dim">
                <Spinner className="size-4" /> {t("common.loading")}
              </div>
            ) : loaderMissing ? (
              <div className="text-sm text-bad">{t("create.noLoaderVersions", { loader: t(`loader.${loader}` as const) })}</div>
            ) : (
              <Select value={loaderVersion} onChange={(e) => setLoaderVersion(e.target.value)}>
                {loaderVers.map((v, i) => (
                  <option key={v.version} value={v.version}>
                    {v.version}
                    {i === 0 && v.stable ? ` — ${t("create.auto")}` : ""}
                  </option>
                ))}
              </Select>
            )}
          </Field>
        )}
        <div className="pt-2">
          <Checkbox checked={hud && !!hudStatus?.supported} onChange={setHud}>
            <span>
              <span className="font-semibold">{t("create.hud")}</span>
              <span className="block text-xs text-faint">
                {hudStatus && !hudStatus.supported ? t("create.hudUnsupported", { reason: hudStatus.reason }) : t("create.hudHint")}
              </span>
            </span>
          </Checkbox>
        </div>
      </div>
    </Modal>
  );
}

/* ---------- Microsoft device-code dialog ---------- */
export function DeviceCodeModal({ onCancel }: { onCancel: () => void }) {
  const code = useApp((s) => s.deviceCode);
  const t = useApp((s) => s.t);
  const toast = useApp((s) => s.toast);
  return (
    <Modal open={!!code} onClose={onCancel} title={t("acc.deviceTitle")} width={460}>
      {code && (
        <div className="text-center space-y-4">
          <p className="text-dim">
            {t("acc.deviceStep1")} <span className="text-fg font-semibold">{code.verificationUri}</span> {t("acc.deviceStep2")}
          </p>
          <div className="selectable text-4xl font-extrabold tracking-[0.25em] text-accent py-4 rounded-[12px] bg-surface-2">
            {code.userCode}
          </div>
          <div className="flex justify-center gap-3">
            <Button
              icon={<Copy className="size-4" />}
              onClick={() => navigator.clipboard.writeText(code.userCode).then(() => toast("ok", t("common.copied")))}
            >
              {t("common.copy")}
            </Button>
            <Button variant="primary" icon={<ExternalLink className="size-4" />} onClick={() => openUrl(code.verificationUri)}>
              {t("acc.openPage")}
            </Button>
          </div>
          <div className="flex items-center justify-center gap-2 text-sm text-dim">
            <Spinner className="size-4" /> {t("acc.deviceWaiting")}
          </div>
        </div>
      )}
    </Modal>
  );
}

/** Starts Microsoft sign-in and handles result. */
export function useMicrosoftLogin() {
  const { refreshAccounts, patchSettings, toast, t } = useApp();
  const [busy, setBusy] = useState(false);
  const start = async (): Promise<Account | null> => {
    setBusy(true);
    try {
      const acc = await api.loginMicrosoft();
      await refreshAccounts();
      await patchSettings({ lastAccount: acc.id });
      toast("ok", t("acc.added"));
      return acc;
    } catch (e) {
      toast("error", errText(e));
      return null;
    } finally {
      setBusy(false);
      useApp.setState({ deviceCode: null });
    }
  };
  const cancel = () => {
    api.cancelLogin();
  };
  return { busy, start, cancel };
}

/* ---------- Crash helper dialog (ТЗ 3.8) ---------- */
export function CrashModal() {
  const { crash, dismissCrash, t, navigate, refreshInstances, toast, instances } = useApp();
  if (!crash) return null;
  const inst = instances.find((i) => i.id === crash.instanceId);
  const hint = crash.hint;
  const act = async () => {
    if (!hint) return;
    try {
      if (hint.action.startsWith("ram:")) {
        await api.applyCrashFix(crash.instanceId, hint.action);
        await refreshInstances();
        toast("ok", t("launch.fixApplied"));
      } else if (hint.action === "open-mods") {
        navigate({ page: "instance", id: crash.instanceId, tab: "mods" });
      } else if (hint.action === "repair" || hint.action === "java:auto") {
        await api.repairInstance(crash.instanceId);
      } else if (hint.action === "open-settings") {
        navigate({ page: "settings" });
      }
    } catch (e) {
      toast("error", errText(e));
    }
    dismissCrash();
  };
  const label =
    hint?.action.startsWith("ram:") ? t("launch.fixRam") : hint?.action === "open-mods" ? t("launch.fixMods") : hint?.action ? t("launch.fixRepair") : "";
  return (
    <Modal
      open
      onClose={dismissCrash}
      title={t("launch.crashed")}
      footer={
        <>
          <Button variant="ghost" onClick={dismissCrash}>
            {t("common.close")}
          </Button>
          {inst && (
            <Button
              onClick={() => {
                dismissCrash();
                navigate({ page: "instance", id: inst.id, tab: "log" });
              }}
            >
              {t("inst.tab.log")}
            </Button>
          )}
          {hint?.action && (
            <Button variant="primary" icon={<Wrench className="size-4" />} onClick={act}>
              {label}
            </Button>
          )}
        </>
      }
    >
      <div className="space-y-3">
        <Badge tone="bad">code {crash.code}</Badge>
        <p className="text-base">{hint?.message}</p>
      </div>
    </Modal>
  );
}

export async function pickFiles(filters: { name: string; extensions: string[] }[], multiple = false) {
  const r = await openDialog({ multiple, filters });
  if (!r) return [];
  return Array.isArray(r) ? r : [r];
}
