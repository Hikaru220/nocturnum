import { useCallback, useEffect, useRef, useState } from "react";
import { openUrl } from "@tauri-apps/plugin-opener";
import { Check, Download, ExternalLink, Search } from "lucide-react";
import { api, errText, type Json } from "../lib/api";
import { formatNumber, useApp } from "../lib/store";
import { Badge, Button, Card, cx, Empty, Field, Input, Modal, Select, Skeleton, Spinner } from "../components/ui";

const CATS: Record<string, string[]> = {
  mod: ["adventure", "decoration", "economy", "equipment", "food", "game-mechanics", "library", "magic", "management", "minigame", "mobs", "optimization", "social", "storage", "technology", "transportation", "utility", "worldgen"],
  modpack: ["adventure", "challenging", "combat", "kitchen-sink", "lightweight", "magic", "multiplayer", "optimization", "quests", "technology"],
  resourcepack: ["combat", "cursed", "decoration", "modded", "realistic", "simplistic", "themed", "tweaks", "utility", "vanilla-like"],
  shader: ["cartoon", "cursed", "fantasy", "realistic", "semi-realistic", "vanilla-like", "potato", "low", "medium", "high"],
};

export default function Catalog({ initialQuery, initialType, initialInstance, initialProject }: { initialQuery?: string; initialType?: string; initialInstance?: string; initialProject?: string }) {
  const { t, instances, settings, toast } = useApp();
  const [type, setType] = useState(initialType ?? "mod");
  const [query, setQuery] = useState(initialQuery ?? "");
  const [sort, setSort] = useState("relevance");
  const [category, setCategory] = useState("");
  const [instId, setInstId] = useState(initialInstance ?? settings?.lastInstance ?? instances[0]?.id ?? "");
  const [fit, setFit] = useState(true);
  const [hits, setHits] = useState<Json[] | null>(null);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<string | null>(initialProject ?? null);
  const seq = useRef(0);

  const inst = instances.find((i) => i.id === instId);
  const gv = type === "modpack" || !fit || !inst ? undefined : inst.mcVersion;
  const ld = type === "mod" && fit && inst && inst.loader !== "vanilla" ? inst.loader : undefined;

  const run = useCallback(
    async (offset: number) => {
      const my = ++seq.current;
      setLoading(true);
      try {
        const r = await api.mrSearch({ query, projectType: type, gameVersion: gv, loader: ld, category: category || undefined, sort, offset });
        if (my !== seq.current) return;
        setTotal(r.total_hits ?? 0);
        setHits((cur) => (offset === 0 ? r.hits : [...(cur ?? []), ...r.hits]));
      } catch (e) {
        if (my === seq.current) {
          toast("error", errText(e));
          setHits((c) => c ?? []);
        }
      } finally {
        if (my === seq.current) setLoading(false);
      }
    },
    [query, type, gv, ld, category, sort],
  );

  useEffect(() => {
    const h = setTimeout(() => run(0), query ? 300 : 0);
    return () => clearTimeout(h);
  }, [run]);

  useEffect(() => setCategory(""), [type]);

  return (
    <div className="anim-page flex h-full">
      <aside className="w-64 shrink-0 border-r border-line p-5 overflow-y-auto">
        <h1 className="text-lg font-extrabold mb-4">{t("cat.title")}</h1>
        <div className="grid grid-cols-2 gap-2 mb-4">
          {(["mod", "modpack", "resourcepack", "shader"] as const).map((k) => (
            <button
              key={k}
              onClick={() => { setType(k); setHits(null); }}
              className={cx(
                "h-9 rounded-[10px] text-xs font-semibold border transition-colors",
                type === k ? "bg-accent/15 border-accent text-fg" : "bg-surface-2 border-line text-dim hover:text-fg",
              )}
            >
              {t(`cat.type.${k}` as const)}
            </button>
          ))}
        </div>
        {type !== "modpack" && (
          <>
            <Field label={t("cat.installTo")}>
              <Select value={instId} onChange={(e) => setInstId(e.target.value)}>
                {instances.length === 0 && <option value="">{t("cat.chooseInstance")}</option>}
                {instances.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.name} ({i.mcVersion})
                  </option>
                ))}
              </Select>
            </Field>
            <label className="flex items-center gap-2 text-sm cursor-pointer pb-2">
              <input type="checkbox" checked={fit} onChange={(e) => { setFit(e.target.checked); setHits(null); }} className="accent-[var(--accent)] size-4" />
              {t("cat.fitFilter")}
            </label>
          </>
        )}
        <Field label={t("cat.category")}>
          <Select value={category} onChange={(e) => { setCategory(e.target.value); setHits(null); }}>
            <option value="">{t("cat.any")}</option>
            {CATS[type].map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </Select>
        </Field>
        <Field label={t("cat.sort")}>
          <Select value={sort} onChange={(e) => { setSort(e.target.value); setHits(null); }}>
            {(["relevance", "downloads", "updated", "newest"] as const).map((s) => (
              <option key={s} value={s}>
                {t(`cat.sort.${s}` as const)}
              </option>
            ))}
          </Select>
        </Field>
        <p className="text-xs text-faint mt-4">{t("cat.source")}</p>
      </aside>

      <section className="flex-1 min-w-0 flex flex-col">
        <div className="p-5 border-b border-line">
          <Input
            autoFocus
            icon={<Search className="size-4" />}
            value={query}
            placeholder={t("cat.search")}
            onChange={(e) => { setQuery(e.target.value); setHits(null); }}
          />
        </div>
        <div className="flex-1 overflow-y-auto p-5 space-y-3">
          {hits === null ? (
            [0, 1, 2, 3, 4, 5].map((i) => <Skeleton key={i} className="h-28" />)
          ) : hits.length === 0 ? (
            <Empty title={t("cat.noResults")} />
          ) : (
            <>
              {hits.map((h) => (
                <ProjectCard key={h.project_id} hit={h} type={type} onOpen={() => setDetail(h.project_id)} instId={instId} />
              ))}
              {hits.length < total && (
                <div className="flex justify-center pt-2">
                  <Button loading={loading} onClick={() => run(hits.length)}>
                    {t("cat.more")}
                  </Button>
                </div>
              )}
            </>
          )}
        </div>
      </section>

      <ProjectModal id={detail} type={type} instId={instId} onClose={() => setDetail(null)} />
    </div>
  );
}

function useInstall(type: string, instId: string) {
  const { t, toast, refreshInstances, navigate, instances } = useApp();
  const [busy, setBusy] = useState<string | null>(null);
  const [done, setDone] = useState<Set<string>>(new Set());
  const install = async (projectId: string, versionId: string | null = null) => {
    setBusy(projectId);
    try {
      if (type === "modpack") {
        const i = await api.installModpack(projectId, versionId);
        await refreshInstances();
        toast("ok", t("notif.modpackInstalled"));
        navigate({ page: "instance", id: i.id });
      } else {
        if (!instId) return toast("error", t("cat.chooseInstance"));
        const names = await api.mrInstall(instId, projectId, versionId, type);
        setDone((d) => new Set(d).add(projectId));
        toast("ok", t("cat.installedOk", { names: names.join(", ") }));
        void instances;
      }
    } catch (e) {
      toast("error", errText(e));
    } finally {
      setBusy(null);
    }
  };
  return { busy, done, install };
}

function ProjectCard({ hit, type, instId, onOpen }: { hit: Json; type: string; instId: string; onOpen: () => void }) {
  const { t } = useApp();
  const { busy, done, install } = useInstall(type, instId);
  const progress = useApp((s) => s.progress[`mod:${hit.project_id}`] ?? s.progress[`modpack:${hit.project_id}`]);
  return (
    <Card className="p-4 flex gap-4 hover:border-accent/50 transition-colors cursor-pointer" onClick={onOpen} tabIndex={0} onKeyDown={(e) => e.key === "Enter" && onOpen()}>
      {hit.icon_url ? (
        <img src={hit.icon_url} alt="" className="size-20 rounded-[10px] bg-surface-2 object-cover shrink-0" loading="lazy" />
      ) : (
        <div className="size-20 rounded-[10px] bg-surface-2 shrink-0" />
      )}
      <div className="min-w-0 flex-1">
        <div className="flex items-baseline gap-2">
          <h3 className="font-bold text-base truncate">{hit.title}</h3>
          <span className="text-xs text-faint truncate">{hit.author}</span>
        </div>
        <p className="text-dim line-clamp-2 mt-0.5">{hit.description}</p>
        <div className="flex flex-wrap items-center gap-2 mt-2">
          <Badge>{t("cat.downloads", { n: formatNumber(hit.downloads ?? 0) })}</Badge>
          {(hit.display_categories ?? []).slice(0, 3).map((c: string) => (
            <Badge key={c} tone="neutral">
              {c}
            </Badge>
          ))}
        </div>
      </div>
      <div className="shrink-0 self-center" onClick={(e) => e.stopPropagation()}>
        <Button
          variant={done.has(hit.project_id) ? "secondary" : "primary"}
          loading={busy === hit.project_id}
          icon={done.has(hit.project_id) ? <Check className="size-4" /> : <Download className="size-4" />}
          onClick={() => install(hit.project_id)}
        >
          {done.has(hit.project_id) ? t("common.installed") : t("common.install")}
          {busy === hit.project_id && progress && !progress.done && progress.percent > 0 ? ` ${Math.round(progress.percent)}%` : ""}
        </Button>
      </div>
    </Card>
  );
}

function ProjectModal({ id, type, instId, onClose }: { id: string | null; type: string; instId: string; onClose: () => void }) {
  const { t, instances } = useApp();
  const [p, setP] = useState<Json | null>(null);
  const [versions, setVersions] = useState<Json[] | null>(null);
  const [shot, setShot] = useState(0);
  const { busy, done, install } = useInstall(type, instId);
  const inst = instances.find((i) => i.id === instId);

  useEffect(() => {
    setP(null);
    setVersions(null);
    setShot(0);
    if (!id) return;
    api.mrProject(id).then(setP).catch(() => {});
    const loaders = type === "mod" && inst && inst.loader !== "vanilla" ? [inst.loader, ...(inst.loader === "quilt" ? ["fabric"] : [])] : [];
    const gvs = type !== "modpack" && inst ? [inst.mcVersion] : [];
    api.mrVersions(id, loaders, gvs).then(setVersions).catch(() => setVersions([]));
  }, [id]);

  const gallery: Json[] = p?.gallery ?? [];
  return (
    <Modal open={!!id} onClose={onClose} title={p?.title ?? t("common.loading")} width={860}
      footer={
        <>
          {p && (
            <Button variant="ghost" icon={<ExternalLink className="size-4" />} onClick={() => openUrl(`https://modrinth.com/${p.project_type}/${p.slug}`)}>
              {t("cat.openModrinth")}
            </Button>
          )}
          <Button
            variant="primary"
            icon={<Download className="size-4" />}
            loading={busy === id}
            onClick={() => id && install(id)}
          >
            {type === "modpack" ? t("cat.installModpack") : done.has(id ?? "") ? t("common.installed") : t("common.install")}
          </Button>
        </>
      }
    >
      {!p ? (
        <Spinner />
      ) : (
        <div className="space-y-5">
          <div className="flex gap-4">
            {p.icon_url && <img src={p.icon_url} alt="" className="size-20 rounded-[10px]" />}
            <div>
              <p className="text-dim">{p.description}</p>
              <div className="flex gap-2 mt-2 flex-wrap">
                <Badge tone="accent">{t("cat.downloads", { n: formatNumber(p.downloads ?? 0) })}</Badge>
                {(p.loaders ?? []).slice(0, 5).map((l: string) => (
                  <Badge key={l}>{l}</Badge>
                ))}
              </div>
            </div>
          </div>
          {gallery.length > 0 && (
            <div>
              <h3 className="font-bold mb-2">{t("cat.gallery")}</h3>
              <img src={gallery[shot]?.url} alt="" className="w-full max-h-80 object-cover rounded-[10px] bg-surface-2" />
              <div className="flex gap-2 mt-2 overflow-x-auto">
                {gallery.map((g, i) => (
                  <button key={g.url} onClick={() => setShot(i)} className={cx("shrink-0 h-14 rounded-md overflow-hidden border-2", i === shot ? "border-accent" : "border-transparent")}>
                    <img src={g.url} alt="" className="h-full" loading="lazy" />
                  </button>
                ))}
              </div>
            </div>
          )}
          <div>
            <h3 className="font-bold mb-2">{t("cat.versions")}</h3>
            {versions === null ? (
              <Skeleton className="h-24" />
            ) : versions.length === 0 ? (
              <p className="text-dim">{inst ? t("cat.noCompatible", { version: inst.mcVersion, loader: inst.loader }) : t("cat.noResults")}</p>
            ) : (
              <div className="space-y-1.5 max-h-64 overflow-y-auto pr-1">
                {versions.slice(0, 30).map((v) => (
                  <div key={v.id} className="flex items-center gap-3 px-3 h-12 rounded-[10px] bg-surface-2">
                    <div className="min-w-0 flex-1">
                      <div className="font-semibold truncate">{v.name}</div>
                      <div className="text-xs text-faint truncate">
                        {v.version_number} · {(v.game_versions ?? []).slice(-3).join(", ")} · {(v.loaders ?? []).join(", ")}
                      </div>
                    </div>
                    <Badge tone={v.version_type === "release" ? "ok" : "warn"}>{v.version_type}</Badge>
                    <Button className="!h-8" loading={busy === id} onClick={() => id && install(id, v.id)}>
                      {t("common.install")}
                    </Button>
                  </div>
                ))}
              </div>
            )}
            {type === "mod" && <p className="text-xs text-faint mt-2">{t("cat.depsHint")}</p>}
          </div>
        </div>
      )}
    </Modal>
  );
}
