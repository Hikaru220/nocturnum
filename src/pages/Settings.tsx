import { useEffect, useState } from "react";
import { open as openDialog } from "@tauri-apps/plugin-dialog";
import { openUrl } from "@tauri-apps/plugin-opener";
import { Check, Copy, ExternalLink, FolderOpen, RefreshCw } from "lucide-react";
import { api, errText, type JavaInfo } from "../lib/api";
import { useApp } from "../lib/store";
import { Button, Card, cx, Field, Input, Select, Slider, Toggle, Tabs } from "../components/ui";

const ACCENTS = ["#8B7CFF", "#5BA8FF", "#2DD4BF", "#4ADE80", "#FBBF24", "#FB923C", "#FF6B81", "#F472B6"];
type Sec = "general" | "appearance" | "game" | "java" | "storage" | "discord" | "about";

export default function Settings() {
  const { t, settings: s, system, patchSettings: patch, toast } = useApp();
  const [sec, setSec] = useState<Sec>("general");
  const [javas, setJavas] = useState<JavaInfo[] | null>(null);
  const [javaMsg, setJavaMsg] = useState("");
  const [moveData, setMoveData] = useState(true);
  const [log, setLog] = useState("");
  const [checking, setChecking] = useState("");
  const [discordMsg, setDiscordMsg] = useState<{ ok: boolean; message: string } | null>(null);
  const [msMsg, setMsMsg] = useState<{ ok: boolean; message: string } | null>(null);
  const [hw, setHw] = useState<{ gpu: string; ramMb: number; tier: string } | null>(null);
  useEffect(() => {
    api.hardwareInfo().then(setHw).catch(() => {});
  }, []);
  useEffect(() => {
    if (sec === "java" && javas === null) api.detectJava().then(setJavas).catch(() => setJavas([]));
    if (sec === "about") api.launcherLog().then(setLog).catch(() => {});
  }, [sec]);
  if (!s || !system) return null;

  const maxRam = Math.max(2048, Math.floor((system.totalRamMb * 0.85) / 512) * 512);
  const row = (label: string, v: boolean, on: (v: boolean) => void, hint?: string) => (
    <Field label={label} hint={hint} row>
      <Toggle checked={v} onChange={on} label={label} />
    </Field>
  );

  const checkJava = async () => {
    try {
      const j = await api.validateJava(s.customJava);
      setJavaMsg(t("set.javaOk", { version: j.version }));
    } catch (e) {
      setJavaMsg(errText(e));
    }
  };

  const changeRoot = async () => {
    const dir = await openDialog({ directory: true });
    if (!dir || Array.isArray(dir)) return;
    try {
      await api.setRootDir(dir, moveData);
      toast("ok", t("set.rootNote"));
    } catch (e) {
      toast("error", errText(e));
    }
  };

  return (
    <div className="anim-page p-8 max-w-3xl mx-auto">
      <h1 className="text-2xl font-extrabold mb-4">{t("set.title")}</h1>
      <Tabs<Sec>
        value={sec}
        onChange={setSec}
        tabs={[
          { id: "general", label: t("set.general") },
          { id: "appearance", label: t("set.appearance") },
          { id: "game", label: t("set.game") },
          { id: "java", label: t("set.java") },
          { id: "storage", label: t("set.storage") },
          { id: "discord", label: t("set.discord") },
          { id: "about", label: t("set.about") },
        ]}
      />
      <div className="pt-4 divide-y divide-line/60">
        {sec === "general" && (
          <>
            <Field label={t("set.language")} row>
              <Select value={s.language} onChange={(e) => patch({ language: e.target.value as "ru" | "en" })} className="!w-44">
                <option value="ru">Русский</option>
                <option value="en">English</option>
              </Select>
            </Field>
            {row(t("set.preset"), s.applyPreset, (v) => patch({ applyPreset: v }), t("set.presetHint"))}
            {row(t("set.sodium"), s.installSodium, (v) => patch({ installSodium: v }), t("set.sodiumHint"))}
            {row(t("set.hud"), s.installHud, (v) => patch({ installHud: v }))}
            {row(t("set.opt"), s.installOptimization, (v) => patch({ installOptimization: v }), t("set.optHint"))}
            {row(t("set.priority"), s.highPriority, (v) => patch({ highPriority: v }))}
            <Field label={t("set.tier")} hint={hw ? t("set.detected", { gpu: hw.gpu || "—", gb: (hw.ramMb / 1024).toFixed(0), tier: t((`set.tier.${hw.tier}`) as never) }) : t("set.tierHint")} row>
              <Select value={s.perfTier} onChange={(e) => patch({ perfTier: e.target.value as never })} className="!w-44">
                {(["auto", "low", "mid", "high"] as const).map((k) => (
                  <option key={k} value={k}>
                    {t(`set.tier.${k}` as never)}
                  </option>
                ))}
              </Select>
            </Field>
            {row(t("set.minimize"), s.minimizeOnLaunch, (v) => patch({ minimizeOnLaunch: v }))}
          </>
        )}

        {sec === "appearance" && (
          <>
            <Field label={t("set.theme")} row>
              <Select value={s.theme} onChange={(e) => patch({ theme: e.target.value as "dark" | "light" })} className="!w-44">
                <option value="dark">{t("set.theme.dark")}</option>
                <option value="light">{t("set.theme.light")}</option>
              </Select>
            </Field>
            <Field label={t("set.accent")}>
              <div className="flex flex-wrap gap-3">
                {ACCENTS.map((c) => (
                  <button
                    key={c}
                    aria-label={c}
                    onClick={() => patch({ accent: c })}
                    style={{ background: c }}
                    className={cx("size-10 rounded-[10px] flex items-center justify-center transition-transform hover:scale-105", s.accent.toLowerCase() === c.toLowerCase() && "ring-2 ring-offset-2 ring-offset-bg ring-fg")}
                  >
                    {s.accent.toLowerCase() === c.toLowerCase() && <Check className="size-5 text-black/70" />}
                  </button>
                ))}
              </div>
            </Field>
            <Field label={t("set.scale")}>
              <Slider value={s.uiScale} min={90} max={150} step={5} onChange={(v) => patch({ uiScale: v })} format={(v) => `${v}%`} />
            </Field>
            {row(t("set.reduceMotion"), s.reduceMotion, (v) => patch({ reduceMotion: v }), t("set.reduceMotionHint"))}
            <Field label={t("set.background")}>
              <Input value={s.background} placeholder="https://… / C:\…\image.png" onChange={(e) => patch({ background: e.target.value })} />
            </Field>
          </>
        )}

        {sec === "game" && (
          <>
            <Field label={t("set.windowSize")} row>
              <div className="flex items-center gap-2 w-56">
                <Input type="number" min={400} value={s.windowWidth} onChange={(e) => patch({ windowWidth: Number(e.target.value) || 854 })} />
                <span className="text-faint">×</span>
                <Input type="number" min={300} value={s.windowHeight} onChange={(e) => patch({ windowHeight: Number(e.target.value) || 480 })} />
              </div>
            </Field>
            {row(t("set.fullscreen"), s.fullscreen, (v) => patch({ fullscreen: v }))}
            {row(t("set.gpu"), s.discreteGpu, (v) => patch({ discreteGpu: v }))}
            <Field label={t("set.threads")}>
              <Slider value={s.downloadsThreads} min={4} max={16} onChange={(v) => patch({ downloadsThreads: v })} />
            </Field>
          </>
        )}

        {sec === "java" && (
          <>
            <Field label={t("set.ram")} hint={`${t("set.ramPc", { gb: (system.totalRamMb / 1024).toFixed(1) })} · ${t("set.ramLight")} · ${t("set.ramHeavy")}`}>
              <Slider value={Math.min(s.defaultRamMb, maxRam)} min={1024} max={maxRam} step={512} onChange={(v) => patch({ defaultRamMb: v })} format={(v) => `${(v / 1024).toFixed(1)} ГБ`} />
              <p className="text-xs text-faint mt-1">{t("set.ramMax")}</p>
            </Field>
            <Field label={t("set.javaPath")}>
              <div className="flex gap-2">
                <Input value={s.customJava} placeholder="C:\Program Files\Java\…\bin\java.exe" onChange={(e) => { patch({ customJava: e.target.value }); setJavaMsg(""); }} />
                <Button
                  icon={<FolderOpen className="size-4" />}
                  onClick={async () => {
                    const f = await openDialog({ filters: [{ name: "java", extensions: ["exe", ""] }] });
                    if (f && !Array.isArray(f)) patch({ customJava: f });
                  }}
                >
                  {t("common.browse")}
                </Button>
                <Button disabled={!s.customJava} onClick={checkJava}>{t("set.javaCheck")}</Button>
              </div>
              {javaMsg && <p className="text-sm mt-2 text-dim">{javaMsg}</p>}
            </Field>
            <Field label={t("set.jvm")}>
              <Input value={s.jvmArgs} placeholder="-XX:+UseZGC" onChange={(e) => patch({ jvmArgs: e.target.value })} />
            </Field>
            <Field label={t("set.javaFound")}>
              <div className="space-y-1.5">
                {(javas ?? []).map((j) => (
                  <div key={j.path} className="flex items-center gap-3 text-sm bg-surface-2 rounded-[10px] px-3 h-10">
                    <b>Java {j.major}</b>
                    <span className="text-faint truncate flex-1">{j.path}</span>
                    {j.managed && <span className="text-accent text-xs">Nocturnum</span>}
                  </div>
                ))}
                {javas === null && <div className="text-dim">{t("common.loading")}</div>}
                <Button icon={<RefreshCw className="size-4" />} onClick={() => { setJavas(null); api.detectJava().then(setJavas); }}>
                  {t("set.javaDetect")}
                </Button>
              </div>
            </Field>
          </>
        )}

        {sec === "storage" && (
          <>
            <Field label={t("set.root")}>
              <Card className="px-3 h-10 flex items-center text-sm text-dim selectable truncate">{system.rootDir}</Card>
              <div className="flex items-center gap-4 mt-3">
                <Button icon={<FolderOpen className="size-4" />} disabled={system.portable} onClick={changeRoot}>{t("set.rootChange")}</Button>
                <label className="flex items-center gap-2 text-sm cursor-pointer">
                  <input type="checkbox" checked={moveData} onChange={(e) => setMoveData(e.target.checked)} className="accent-[var(--accent)] size-4" />
                  {t("set.rootMove")}
                </label>
                <Button variant="ghost" onClick={() => api.openPath(system.rootDir)}>{t("common.open")}</Button>
              </div>
              <p className="text-xs text-faint mt-2">{system.portable ? t("set.portable") : `${t("set.rootNote")} ${t("set.portableHint")}`}</p>
            </Field>
          </>
        )}

        {sec === "discord" && (
          <>
            {row(t("set.rpc"), s.discordRpc, (v) => patch({ discordRpc: v }))}
            {row(t("set.rpcServer"), s.rpcHideServer, (v) => patch({ rpcHideServer: v }))}
            {row(t("set.rpcInstance"), s.rpcHideInstance, (v) => patch({ rpcHideInstance: v }))}
            <Field label={t("set.rpcAppId")}>
              <div className="flex gap-2">
                <Input value={s.discordAppId} placeholder="123456789012345678" onChange={(e) => { patch({ discordAppId: e.target.value.trim() }); setDiscordMsg(null); }} />
                <Button loading={checking === "d"} onClick={async () => { setChecking("d"); try { setDiscordMsg(await api.checkDiscord(s.discordAppId)); } finally { setChecking(""); } }}>{t("set.check")}</Button>
              </div>
              {discordMsg && <p className={`text-sm mt-2 ${discordMsg.ok ? "text-ok" : "text-bad"}`}>{discordMsg.message}</p>}
              <button className="text-xs text-accent mt-2 hover:underline" onClick={() => openUrl("https://github.com/Hikaru220/nocturnum/blob/main/docs/MICROSOFT_AUTH.md#discord-rich-presence")}>{t("set.discordSetup")}</button>
            </Field>
            <Field label={t("set.msClientId")} hint={t("set.msClientHint")}>
              <div className="flex gap-2">
                <Input value={s.msClientId} placeholder="00000000-0000-0000-0000-000000000000" onChange={(e) => { patch({ msClientId: e.target.value.trim() }); setMsMsg(null); }} />
                <Button loading={checking === "m"} onClick={async () => { setChecking("m"); try { setMsMsg(await api.checkMsClient(s.msClientId)); } finally { setChecking(""); } }}>{t("set.check")}</Button>
              </div>
              {msMsg && <p className={`text-sm mt-2 ${msMsg.ok ? "text-ok" : "text-bad"}`}>{msMsg.message}</p>}
              <button className="text-xs text-accent mt-2 hover:underline" onClick={() => openUrl("https://github.com/Hikaru220/nocturnum/blob/main/docs/MICROSOFT_AUTH.md")}>{t("set.msSetup")}</button>
            </Field>
          </>
        )}

        {sec === "about" && (
          <div className="py-4 space-y-4">
            <div className="flex items-center gap-4">
              <img src="/icon.png" className="size-16 rounded-[14px]" alt="" />
              <div>
                <div className="text-xl font-extrabold">Nocturnum Launcher</div>
                <div className="text-dim">{t("set.version", { v: system.version })} · {t("set.license")}</div>
              </div>
            </div>
            <Button icon={<ExternalLink className="size-4" />} onClick={() => openUrl("https://github.com/Hikaru220/nocturnum")}>{t("set.github")}</Button>
            <div>
              <div className="flex items-center justify-between mb-2">
                <div className="font-semibold">{t("set.launcherLog")}</div>
                <Button icon={<Copy className="size-4" />} onClick={() => navigator.clipboard.writeText(log).then(() => toast("ok", t("common.copied")))}>{t("inst.copyLog")}</Button>
              </div>
              <pre className="selectable h-56 overflow-auto rounded-[12px] bg-bg border border-line p-3 text-xs whitespace-pre-wrap text-dim">{log || "—"}</pre>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
