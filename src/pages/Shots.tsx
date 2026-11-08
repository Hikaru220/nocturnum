import { useEffect, useRef, useState } from "react";
import { Camera, Copy, FolderOpen, Trash2 } from "lucide-react";
import { api, errText, type GalleryShot } from "../lib/api";
import { useApp } from "../lib/store";
import { Empty, IconButton, Modal, Skeleton } from "../components/ui";

// thumbnails are made one at a time, and never while a game is running (the launcher stays out of its way)
const queue: (() => Promise<void>)[] = [];
let busy = false;
function enqueue(job: () => Promise<void>) {
  queue.push(job);
  if (busy) return;
  busy = true;
  (async () => {
    while (queue.length) await queue.shift()!().catch(() => {});
    busy = false;
  })();
}

function Thumb({ shot, onOpen, onCopy, onDelete, onFolder }: { shot: GalleryShot; onOpen: () => void; onCopy: () => void; onDelete: () => void; onFolder: () => void }) {
  const t = useApp((s) => s.t);
  const [src, setSrc] = useState<string | null>(null);
  const [visible, setVisible] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver((e) => e.some((x) => x.isIntersecting) && setVisible(true), { rootMargin: "200px" });
    io.observe(el);
    return () => io.disconnect();
  }, []);
  useEffect(() => {
    if (!visible || src) return;
    let alive = true;
    enqueue(async () => {
      if (!alive) return;
      const d = await api.screenshotThumb(shot.path);
      if (alive) setSrc(d);
    });
    return () => {
      alive = false;
    };
  }, [visible, src, shot.path]);
  return (
    <div ref={ref} className="group relative rounded-[12px] overflow-hidden border border-line bg-surface-2 aspect-video">
      {src ? (
        <button onClick={onOpen} className="size-full block">
          <img src={src} alt={shot.fileName} className="size-full object-cover" loading="lazy" />
        </button>
      ) : (
        <Skeleton className="size-full" />
      )}
      <div className="absolute inset-x-0 bottom-0 p-2 bg-gradient-to-t from-black/70 to-transparent text-xs text-white flex items-end justify-between gap-2 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
        <div className="min-w-0">
          <div className="truncate font-semibold">{shot.instanceName}</div>
          <div className="truncate opacity-80">{new Date(shot.modified * 1000).toLocaleString()}</div>
        </div>
        <div className="flex gap-1 shrink-0">
          <IconButton label={t("shots.copy")} onClick={onCopy}><Copy className="size-4" /></IconButton>
          <IconButton label={t("shots.folder")} onClick={onFolder}><FolderOpen className="size-4" /></IconButton>
          <IconButton label={t("shots.delete")} onClick={onDelete}><Trash2 className="size-4" /></IconButton>
        </div>
      </div>
    </div>
  );
}

export default function Shots() {
  const { t, toast } = useApp();
  const playing = useApp((s) => s.running.length > 0);
  const [shots, setShots] = useState<GalleryShot[] | null>(null);
  const [view, setView] = useState<{ shot: GalleryShot; src: string } | null>(null);
  const load = () => api.galleryList().then(setShots).catch((e) => toast("error", errText(e)));
  useEffect(() => {
    load();
  }, []);

  const copy = async (s: GalleryShot) => {
    try {
      const url = await api.screenshotFull(s.path);
      const blob = await (await fetch(url)).blob();
      await navigator.clipboard.write([new ClipboardItem({ [blob.type]: blob })]);
      toast("ok", t("shots.copied"));
    } catch (e) {
      toast("error", errText(e));
    }
  };
  const del = async (s: GalleryShot) => {
    try {
      await api.deleteScreenshot(s.path);
      setShots((l) => l?.filter((x) => x.path !== s.path) ?? null);
      setView(null);
      toast("ok", t("shots.deleted"));
    } catch (e) {
      toast("error", errText(e));
    }
  };
  const folder = (s: GalleryShot) => api.openPath(s.path.replace(/[\/][^\/]*$/, "")).catch((e) => toast("error", errText(e)));
  const open = async (s: GalleryShot) => {
    try {
      setView({ shot: s, src: await api.screenshotFull(s.path) });
    } catch (e) {
      toast("error", errText(e));
    }
  };

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <h1 className="text-2xl font-extrabold mb-4">{t("nav.shots")}</h1>
      {playing && <div className="mb-3 text-sm text-dim">{t("shots.playing")}</div>}
      {shots && shots.length === 0 ? (
        <Empty icon={<Camera className="size-10" />} title={t("shots.empty")} hint={t("shots.emptyHint")} />
      ) : (
        <div className="grid gap-3" style={{ gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))" }}>
          {(playing ? [] : shots ?? []).map((s) => (
            <Thumb key={s.path} shot={s} onOpen={() => open(s)} onCopy={() => copy(s)} onDelete={() => del(s)} onFolder={() => folder(s)} />
          ))}
        </div>
      )}
      <Modal open={!!view} onClose={() => setView(null)} title={view?.shot.fileName ?? ""} width={900}>
        {view && <img src={view.src} alt={view.shot.fileName} className="w-full rounded-[10px]" />}
      </Modal>
    </div>
  );
}
