import { openUrl } from "@tauri-apps/plugin-opener";
import { useEffect, useState } from "react";
import { Plus, Sparkles, Server } from "lucide-react";
import { api, errText } from "../lib/api";
import { useApp, formatPlayTime } from "../lib/store";
import { Button, Card, Empty } from "../components/ui";
import { InlineProgress, InstanceIcon, LoaderBadge, PlayButton } from "../components/shared";

/** SunShine banner (единое ТЗ 7.1): name, online, ping; refreshed once a minute only while the window is visible and no game runs. */
function SunShineBanner() {
  const { t, instances, settings, toast, accounts, refreshInstances } = useApp();
  const running = useApp((s) => s.running.length > 0);
  const [st, setSt] = useState<{ online: number; max: number; pingMs: number; address: string } | null>(null);
  useEffect(() => {
    if (running) return;
    let alive = true;
    const poll = () => {
      if (document.visibilityState === "visible") api.sunshineStatus().then((r) => alive && setSt(r)).catch(() => {});
    };
    poll();
    const id = setInterval(poll, 60000);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, [running]);
  const play = async () => {
    try {
      // one click: reuse the 26.2 Fabric build or create it (with the HUD mod), then join the server
      let inst = instances.find((i) => i.mcVersion === "26.2" && i.loader === "fabric");
      if (!inst) {
        const lvs = await api.loaderVersions("fabric", "26.2");
        const lv = (lvs.find((x) => x.stable) ?? lvs[0])?.version ?? "";
        const hud = await api.hudStatus("26.2", "fabric");
        inst = await api.createInstance({
          name: "SunShine",
          mcVersion: "26.2",
          loader: "fabric",
          loaderVersion: lv,
          hudEnabled: hud.supported,
          accountId: settings?.lastAccount || accounts[0]?.id || "",
        });
        await refreshInstances();
      }
      await api.updateInstance({ ...inst, server: st?.address ?? "play.sunshine-mc.ru:30040" });
      await api.launch(inst.id);
    } catch (e) {
      toast("error", errText(e));
    }
  };
  return (
    <section className="rounded-[12px] border border-line bg-surface p-5 flex flex-wrap items-center justify-between gap-4">
      <div className="flex items-center gap-4">
        <div className="size-12 rounded-[12px] bg-accent/20 text-accent flex items-center justify-center"><Server className="size-6" /></div>
        <div>
          <div className="font-extrabold text-lg">SunShine</div>
          <div className="text-sm text-dim">{st ? t("home.sunshineOnline", { online: st.online, max: st.max, ping: st.pingMs }) : t("home.sunshineOffline")}</div>
        </div>
      </div>
      <div className="flex gap-3">
        <Button onClick={() => openUrl("https://discord.gg/tp8sdmmUXv")}>Discord</Button>
        <Button variant="primary" disabled={running} onClick={play}>{t("common.play")}</Button>
      </div>
    </section>
  );
}

export default function Home() {
  const { t, instances, settings, navigate, setCreate } = useApp();
  const last = instances.find((i) => i.id === settings?.lastInstance) ?? instances[0];
  const bg = settings?.background;

  return (
    <div className="anim-page p-8 max-w-6xl mx-auto space-y-8">
      <section
        className="relative overflow-hidden rounded-[12px] border border-line min-h-72 flex items-end"
        style={{
          background: bg
            ? `linear-gradient(to top, var(--bg), transparent 70%), url("${bg}") center/cover`
            : "radial-gradient(1200px 400px at 85% -10%, rgb(var(--accent-rgb) / 0.35), transparent 60%), radial-gradient(800px 300px at 0% 120%, rgb(var(--accent-rgb) / 0.2), transparent 60%), var(--surface)",
        }}
      >
        {bg && <div className="absolute inset-0 backdrop-blur-[2px] bg-black/30" />}
        <div className="relative p-8 w-full flex flex-wrap items-end justify-between gap-6">
          {last ? (
            <>
              <div className="flex items-center gap-5 min-w-0">
                <InstanceIcon inst={last} size={80} />
                <div className="min-w-0">
                  <div className="text-xs uppercase tracking-widest text-dim mb-1">{t("home.lastInstance")}</div>
                  <h1 className="text-3xl font-extrabold truncate">{last.name}</h1>
                  <div className="mt-1 flex items-center gap-3">
                    <LoaderBadge inst={last} />
                    <span className="text-xs text-faint">{t("inst.playTime", { time: last.playTimeSecs ? formatPlayTime(last.playTimeSecs) : t("inst.neverPlayed") })}</span>
                  </div>
                  <div className="w-72 max-w-full">
                    <InlineProgress id={`install:${last.id}`} />
                  </div>
                </div>
              </div>
              <PlayButton inst={last} big />
            </>
          ) : (
            <Empty
              icon={<Sparkles className="size-10" />}
              title={t("home.noInstances")}
              hint={t("home.noInstancesHint")}
              action={
                <Button variant="primary" big icon={<Plus className="size-5" />} onClick={() => setCreate(true)}>
                  {t("home.createFirst")}
                </Button>
              }
            />
          )}
        </div>
      </section>

      <SunShineBanner />

      {instances.length > 1 && (
        <section>
          <h2 className="text-lg font-bold mb-3">{t("home.recent")}</h2>
          <div className="grid grid-cols-[repeat(auto-fill,minmax(260px,1fr))] gap-4">
            {instances.slice(0, 6).map((i) => (
              <Card
                key={i.id}
                className="p-4 flex items-center gap-4 hover:border-accent/60 transition-colors cursor-pointer"
                onClick={() => navigate({ page: "instance", id: i.id })}
              >
                <InstanceIcon inst={i} />
                <div className="min-w-0 flex-1">
                  <div className="font-bold truncate">{i.name}</div>
                  <LoaderBadge inst={i} />
                </div>
                <div onClick={(e) => e.stopPropagation()}>
                  <PlayButton inst={i} />
                </div>
              </Card>
            ))}
          </div>
        </section>
      )}

      <section>
        <h2 className="text-lg font-bold mb-3">{t("home.news")}</h2>
        <div className="grid md:grid-cols-3 gap-4">
          {([1, 2, 3] as const).map((n) => (
            <Card key={n} className="p-5">
              <div className="font-bold mb-1.5">{t(`news.${n}.title` as const)}</div>
              <p className="text-dim">{t(`news.${n}.text` as const)}</p>
            </Card>
          ))}
        </div>
      </section>
    </div>
  );
}
