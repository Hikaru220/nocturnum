import { useEffect, useState } from "react";
import { Check, LogIn } from "lucide-react";
import { api, errText, type Instance } from "../lib/api";
import { useApp } from "../lib/store";
import { Button, cx, Input, Select, Spinner } from "./ui";
import { DeviceCodeModal, PlayButton, useMicrosoftLogin } from "./shared";

/** First-run wizard: sign in → create instance → play (ТЗ 5, 3 steps). */
export default function Wizard() {
  const { t, settings, patchSettings, accounts, refreshInstances, toast, instances } = useApp();
  const acc = useApp((s) => s.currentAccount());
  const ms = useMicrosoftLogin();
  const [step, setStep] = useState(0);
  const [versions, setVersions] = useState<string[]>([]);
  const [mc, setMc] = useState("");
  const [name, setName] = useState("Nocturnum");
  const [created, setCreated] = useState<Instance | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.mcVersions().then((m) => {
      setVersions(m.versions.filter((v) => v.type === "release").map((v) => v.id));
      setMc(m.latest.release);
    }).catch(() => {});
  }, []);

  if (!settings || settings.firstRunDone) return null;
  const finish = () => patchSettings({ firstRunDone: true });

  const create = async () => {
    setBusy(true);
    try {
      // prefer Fabric + HUD where available, fall back to vanilla
      let loader: "fabric" | "vanilla" = "fabric";
      let lv = "";
      try {
        const lvs = await api.loaderVersions("fabric", mc);
        lv = (lvs.find((x) => x.stable) ?? lvs[0])?.version ?? "";
      } catch {
        /* ignore */
      }
      if (!lv) loader = "vanilla";
      const hud = await api.hudStatus(mc, loader);
      const inst = await api.createInstance({
        name: name.trim() || "Nocturnum",
        mcVersion: mc,
        loader,
        loaderVersion: lv,
        hudEnabled: hud.supported,
        accountId: settings.lastAccount || accounts[0]?.id || "",
      });
      await refreshInstances();
      setCreated(inst);
      setStep(2);
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setBusy(false);
    }
  };

  const steps = [t("wiz.step1"), t("wiz.step2"), t("wiz.step3")];
  const live = created ? instances.find((i) => i.id === created.id) ?? created : null;
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-bg/95 backdrop-blur-md p-6">
      <div className="anim-pop w-[560px] max-w-full rounded-[12px] bg-surface border border-line p-8 shadow-2xl">
        <img src="/icon.png" className="size-16 rounded-[14px] mb-4 glow" alt="" />
        <h1 className="text-2xl font-extrabold">{t("wiz.welcome")}</h1>
        <p className="text-dim mb-6">{t("wiz.welcomeText")}</p>
        <ol className="flex items-center gap-2 mb-8">
          {steps.map((s, i) => (
            <li key={s} className="flex items-center gap-2 flex-1">
              <span className={cx("size-7 rounded-full flex items-center justify-center text-xs font-bold shrink-0", i < step ? "bg-ok text-black" : i === step ? "bg-accent text-[var(--on-accent)]" : "bg-surface-3 text-dim")}>
                {i < step ? <Check className="size-4" /> : i + 1}
              </span>
              <span className={cx("text-sm font-semibold", i === step ? "text-fg" : "text-faint")}>{s}</span>
              {i < 2 && <span className="h-px bg-line flex-1" />}
            </li>
          ))}
        </ol>

        {step === 0 && (
          <div className="space-y-4">
            <p>{acc ? t("wiz.signedAs", { name: acc.username }) : t("wiz.step1Text")}</p>
            <div className="flex gap-3">
              {acc ? (
                <Button variant="primary" onClick={() => setStep(1)}>{t("common.next")}</Button>
              ) : (
                <>
                  <Button variant="primary" icon={<LogIn className="size-4" />} loading={ms.busy} onClick={async () => { if (await ms.start()) setStep(1); }}>
                    {t("acc.addMicrosoft")}
                  </Button>
                  <Button variant="ghost" onClick={() => setStep(1)}>{t("wiz.skip")}</Button>
                </>
              )}
            </div>
          </div>
        )}
        {step === 1 && (
          <div className="space-y-4">
            <p>{t("wiz.step2Text")}</p>
            <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={48} aria-label={t("create.name")} />
            <Select value={mc} onChange={(e) => setMc(e.target.value)} aria-label={t("create.gameVersion")}>
              {versions.length === 0 && <option>{t("common.loading")}</option>}
              {versions.map((v) => <option key={v} value={v}>{v}</option>)}
            </Select>
            <div className="flex gap-3">
              <Button variant="primary" loading={busy} disabled={!mc} onClick={create}>{t("common.create")}</Button>
              <Button variant="ghost" onClick={() => setStep(0)}>{t("common.back")}</Button>
            </div>
          </div>
        )}
        {step === 2 && (
          <div className="space-y-5">
            <p>{t("wiz.step3Text")}</p>
            {live ? <PlayButton inst={live} big className="w-full" /> : <Spinner />}
            <Button variant="ghost" onClick={finish}>{t("wiz.finish")}</Button>
          </div>
        )}
      </div>
      <DeviceCodeModal onCancel={ms.cancel} />
    </div>
  );
}
