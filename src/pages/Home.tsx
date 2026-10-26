import { Plus, Sparkles } from "lucide-react";
import { useApp, formatPlayTime } from "../lib/store";
import { Button, Card, Empty } from "../components/ui";
import { InlineProgress, InstanceIcon, LoaderBadge, PlayButton } from "../components/shared";

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
