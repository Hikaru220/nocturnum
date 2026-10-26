import { useCallback, useEffect, useRef, useState } from "react";
import { save as saveDialog } from "@tauri-apps/plugin-dialog";
import { Download, Eraser, FlipHorizontal2, PaintBucket, Pencil, Pipette, Redo2, Save, Trash2, Undo2, Upload, UserSearch } from "lucide-react";
import { IdleAnimation, SkinViewer } from "skinview3d";
import { Raycaster, Vector2, type Mesh } from "three";
import { api, errText, type SavedSkin } from "../lib/api";
import { useApp } from "../lib/store";
import { Button, Card, cx, Field, IconButton, Input, Toggle } from "../components/ui";
import { pickFiles } from "../components/shared";
import { detectSlim, drawDefaultSkin, hexToRgba, makeMirror, rgbaToHex, upgradeLegacy } from "../lib/skin";

type Tool = "pencil" | "eraser" | "fill" | "picker";
const PALETTE = ["#000000", "#ffffff", "#7f7f7f", "#c8102e", "#ff8c00", "#ffd23f", "#2ecc71", "#00a86b", "#1e90ff", "#8b7cff", "#ff69b4", "#8b5a2b", "#f1c9a5", "#5a3825", "#2b2b3a", "#e0e0f0"];

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((res, rej) => {
    const i = new Image();
    i.onload = () => res(i);
    i.onerror = () => rej(new Error("image"));
    i.src = src;
  });
}

function makeCapeTexture(): HTMLCanvasElement {
  const c = document.createElement("canvas");
  c.width = 64;
  c.height = 32;
  const g = c.getContext("2d")!;
  g.fillStyle = "#17172a";
  g.fillRect(0, 0, 64, 32);
  g.fillStyle = "#8b7cff";
  g.fillRect(1, 1, 10, 16); // back face
  g.fillStyle = "#ffffff";
  g.fillRect(8, 3, 1, 1);
  g.fillRect(4, 6, 3, 1);
  return c;
}

export default function Skins() {
  const { t, toast, accounts, settings, pendingSkin } = useApp();
  const acc = useApp((s) => s.currentAccount());
  const viewerHost = useRef<HTMLCanvasElement>(null);
  const wrap = useRef<HTMLDivElement>(null);
  const viewer = useRef<SkinViewer | null>(null);
  const tex = useRef<HTMLCanvasElement>(document.createElement("canvas"));
  const uv = useRef<HTMLCanvasElement>(null);
  const undo = useRef<ImageData[]>([]);
  const redo = useRef<ImageData[]>([]);
  const drawing = useRef(false);
  const raf = useRef(0);

  const [slim, setSlim] = useState(false);
  const [tool, setTool] = useState<Tool>("pencil");
  const [color, setColor] = useState("#8b7cff");
  const [size, setSize] = useState(1);
  const [mirror, setMirror] = useState(false);
  const [layer, setLayer] = useState<"base" | "over">("base");
  const [showOver, setShowOver] = useState(true);
  const [cape, setCape] = useState(false);
  const [paint3d, setPaint3d] = useState(false);
  const [nick, setNick] = useState("");
  const [name, setName] = useState("");
  const [library, setLibrary] = useState<SavedSkin[]>([]);
  const [, bump] = useState(0);

  const ctx = () => tex.current.getContext("2d", { willReadFrequently: true })!;

  const refresh3d = useCallback(() => {
    cancelAnimationFrame(raf.current);
    raf.current = requestAnimationFrame(() => {
      viewer.current?.loadSkin(tex.current, { model: slim ? "slim" : "default" });
      const u = uv.current;
      if (u) {
        const g = u.getContext("2d")!;
        g.imageSmoothingEnabled = false;
        g.clearRect(0, 0, 64, 64);
        // checkerboard so transparent pixels are visible
        for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
          g.fillStyle = (x + y) % 2 ? "#2a2a46" : "#222239";
          g.fillRect(x, y, 1, 1);
        }
        g.drawImage(tex.current, 0, 0);
      }
    });
  }, [slim]);

  // viewer setup
  useEffect(() => {
    const canvas = viewerHost.current!;
    const box = wrap.current!;
    const v = new SkinViewer({ canvas, width: box.clientWidth || 360, height: box.clientHeight || 480, zoom: 0.85 });
    v.animation = new IdleAnimation();
    v.autoRotate = false;
    viewer.current = v;
    // blank default skin
    const c = tex.current;
    c.width = 64;
    c.height = 64;
    drawDefaultSkin(c, false);
    refresh3d();
    const ro = new ResizeObserver(() => box.clientWidth > 0 && v.setSize(box.clientWidth, box.clientHeight));
    ro.observe(box);
    return () => {
      ro.disconnect();
      v.dispose();
      viewer.current = null;
    };
  }, []);

  useEffect(refresh3d, [slim]);
  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    v.playerObject.skin.setOuterLayerVisible(showOver);
  }, [showOver, slim]);
  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    if (cape) v.loadCape(makeCapeTexture(), { backEquipment: "cape" });
    else v.resetCape();
  }, [cape]);
  useEffect(() => {
    const v = viewer.current;
    if (!v) return;
    // paint mode: right mouse rotates, left paints; model stands still
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (v.controls as any).mouseButtons = paint3d ? { LEFT: -1, MIDDLE: 1, RIGHT: 0 } : { LEFT: 0, MIDDLE: 1, RIGHT: 2 };
    v.animation = paint3d ? null : new IdleAnimation();
    if (paint3d) v.playerObject.skin.resetJoints?.();
  }, [paint3d]);

  const loadLibrary = () => api.skinLibrary().then(setLibrary).catch(() => {});
  useEffect(() => void loadLibrary(), []);

  const snapshot = () => {
    undo.current.push(ctx().getImageData(0, 0, 64, 64));
    if (undo.current.length > 60) undo.current.shift();
    redo.current = [];
    bump((n) => n + 1);
  };
  const doUndo = () => {
    const s = undo.current.pop();
    if (!s) return;
    redo.current.push(ctx().getImageData(0, 0, 64, 64));
    ctx().putImageData(s, 0, 0);
    refresh3d();
    bump((n) => n + 1);
  };
  const doRedo = () => {
    const s = redo.current.pop();
    if (!s) return;
    undo.current.push(ctx().getImageData(0, 0, 64, 64));
    ctx().putImageData(s, 0, 0);
    refresh3d();
    bump((n) => n + 1);
  };
  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if ((e.target as HTMLElement)?.tagName === "INPUT") return;
      if (e.ctrlKey && e.key.toLowerCase() === "z") (e.preventDefault(), e.shiftKey ? doRedo() : doUndo());
      if (e.ctrlKey && e.key.toLowerCase() === "y") (e.preventDefault(), doRedo());
    };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  });

  const setSkinFrom = async (dataUrl: string, isSlim?: boolean) => {
    const img = await loadImage(dataUrl);
    if (img.width !== 64 || (img.height !== 64 && img.height !== 32)) throw new Error(`${t("skin.loadError")}: ${img.width}×${img.height}`);
    snapshot();
    const c = upgradeLegacy(img, img.height);
    const g = ctx();
    g.clearRect(0, 0, 64, 64);
    g.drawImage(c, 0, 0);
    setSlim(isSlim ?? detectSlim(c));
    refresh3d();
  };

  useEffect(() => {
    if (pendingSkin) {
      setSkinFrom(pendingSkin).catch((e) => toast("error", errText(e)));
      useApp.setState({ pendingSkin: null });
    }
  }, [pendingSkin]);

  /* ---- painting ---- */
  const putPixel = (x: number, y: number, rgba: [number, number, number, number]) => {
    if (x < 0 || y < 0 || x > 63 || y > 63) return;
    const g = ctx();
    const id = g.getImageData(x, y, 1, 1);
    id.data.set(rgba);
    g.putImageData(id, x, y);
  };
  const eraser: [number, number, number, number] = [0, 0, 0, 0];
  const stamp = (cx: number, cy: number) => {
    const rgba = tool === "eraser" ? eraser : hexToRgba(color);
    const mir = mirror ? makeMirror(slim) : null;
    const half = Math.floor(size / 2);
    for (let dy = 0; dy < size; dy++)
      for (let dx = 0; dx < size; dx++) {
        const x = cx - half + dx;
        const y = cy - half + dy;
        putPixel(x, y, rgba);
        const m = mir?.(x, y);
        if (m) putPixel(m[0], m[1], rgba);
      }
  };
  const flood = (sx: number, sy: number) => {
    const g = ctx();
    const img = g.getImageData(0, 0, 64, 64);
    const d = img.data;
    const idx = (x: number, y: number) => (y * 64 + x) * 4;
    const t0 = [d[idx(sx, sy)], d[idx(sx, sy) + 1], d[idx(sx, sy) + 2], d[idx(sx, sy) + 3]];
    const rgba = tool === "eraser" ? eraser : hexToRgba(color);
    if (t0.every((v, i) => v === rgba[i])) return;
    const same = (x: number, y: number) => [0, 1, 2, 3].every((k) => d[idx(x, y) + k] === t0[k]);
    const stack = [[sx, sy]];
    const mir = mirror ? makeMirror(slim) : null;
    const mirStart = mir?.(sx, sy);
    const run = (stackIn: number[][]) => {
      while (stackIn.length) {
        const [x, y] = stackIn.pop()!;
        if (x < 0 || y < 0 || x > 63 || y > 63 || !same(x, y)) continue;
        d.set(rgba, idx(x, y));
        stackIn.push([x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1]);
      }
    };
    run(stack);
    if (mirStart && same(mirStart[0], mirStart[1])) run([[mirStart[0], mirStart[1]]]);
    g.putImageData(img, 0, 0);
  };
  const pick = (x: number, y: number) => {
    const d = ctx().getImageData(x, y, 1, 1).data;
    if (d[3] > 0) setColor(rgbaToHex(d[0], d[1], d[2]));
    setTool("pencil");
  };

  const apply = (x: number, y: number, first: boolean) => {
    if (tool === "picker") return first ? pick(x, y) : undefined;
    if (tool === "fill") return first ? (flood(x, y), refresh3d()) : undefined;
    stamp(x, y);
    refresh3d();
  };

  // 2D editor pointer handling
  const uvPoint = (e: React.PointerEvent) => {
    const r = (e.currentTarget as HTMLCanvasElement).getBoundingClientRect();
    return [Math.floor(((e.clientX - r.left) / r.width) * 64), Math.floor(((e.clientY - r.top) / r.height) * 64)] as const;
  };
  const down2d = (e: React.PointerEvent) => {
    if (e.button !== 0) return;
    (e.currentTarget as HTMLCanvasElement).setPointerCapture(e.pointerId);
    snapshot();
    drawing.current = true;
    const [x, y] = uvPoint(e);
    apply(x, y, true);
  };
  const move2d = (e: React.PointerEvent) => {
    if (!drawing.current) return;
    const [x, y] = uvPoint(e);
    apply(x, y, false);
  };
  const up = () => {
    drawing.current = false;
  };

  // 3D painting through raycasting on the player model
  const ray = useRef(new Raycaster());
  const hit3d = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const v = viewer.current;
    if (!v) return null;
    const r = e.currentTarget.getBoundingClientRect();
    const ndc = new Vector2(((e.clientX - r.left) / r.width) * 2 - 1, -(((e.clientY - r.top) / r.height) * 2 - 1));
    ray.current.setFromCamera(ndc, v.camera);
    const hits = ray.current.intersectObject(v.playerObject.skin, true).filter((h) => {
      const n = (h.object as Mesh).name;
      return layer === "over" ? n === "outer" : n === "inner";
    });
    const h = hits[0];
    if (!h?.uv) return null;
    return [Math.min(63, Math.max(0, Math.floor(h.uv.x * 64))), Math.min(63, Math.max(0, Math.floor((1 - h.uv.y) * 64)))] as const;
  };
  const down3d = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!paint3d || e.button !== 0) return;
    const p = hit3d(e);
    if (!p) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    snapshot();
    drawing.current = true;
    apply(p[0], p[1], true);
  };
  const move3d = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!paint3d || !drawing.current) return;
    const p = hit3d(e);
    if (p) apply(p[0], p[1], false);
  };

  const dataUrl = () => tex.current.toDataURL("image/png");

  /* ---- actions ---- */
  const guard = async (fn: () => Promise<unknown>, ok?: string) => {
    try {
      await fn();
      if (ok) toast("ok", ok);
    } catch (e) {
      toast("error", errText(e));
    }
  };
  const importPng = () =>
    guard(async () => {
      const [f] = await pickFiles([{ name: "PNG", extensions: ["png"] }]);
      if (f) await setSkinFrom(await api.readFileDataUrl(f));
    });
  const byName = () =>
    guard(async () => {
      const r = await api.skinByName(nick.trim());
      await setSkinFrom(r.dataUrl, r.slim);
    });
  const mine = () =>
    guard(async () => {
      if (!acc) throw new Error(t("acc.empty"));
      const r = await api.skinOfAccount(acc.id);
      await setSkinFrom(r.dataUrl, r.slim);
    });
  const exportPng = () =>
    guard(async () => {
      const dest = await saveDialog({ defaultPath: `${name || "skin"}.png`, filters: [{ name: "PNG", extensions: ["png"] }] });
      if (dest) await api.writePng(dest, dataUrl());
    }, t("skin.exported"));
  const saveLib = () =>
    guard(async () => {
      await api.skinSave(name.trim() || "skin", dataUrl(), slim);
      await loadLibrary();
    }, t("skin.saved"));
  const upload = () =>
    guard(async () => {
      if (!acc || acc.kind !== "microsoft") throw new Error(t("skin.needMicrosoft"));
      await api.skinUpload(acc.id, dataUrl(), slim);
    }, t("skin.uploaded"));

  const tools: { id: Tool; icon: React.ReactNode; label: string }[] = [
    { id: "pencil", icon: <Pencil className="size-4" />, label: t("skin.tool.pencil") },
    { id: "eraser", icon: <Eraser className="size-4" />, label: t("skin.tool.eraser") },
    { id: "fill", icon: <PaintBucket className="size-4" />, label: t("skin.tool.fill") },
    { id: "picker", icon: <Pipette className="size-4" />, label: t("skin.tool.picker") },
  ];
  void accounts;
  void settings;

  return (
    <div className="anim-page p-6 grid xl:grid-cols-[360px_1fr_240px] lg:grid-cols-[320px_1fr] gap-6 h-full overflow-y-auto">
      <div className="flex flex-col gap-3 min-h-[480px]">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-extrabold">{t("skin.title")}</h1>
          <label className="flex items-center gap-2 text-xs text-dim cursor-pointer">
            <Toggle checked={paint3d} onChange={setPaint3d} label={t("skin.paint3d")} />
            {t("skin.paint3d")}
          </label>
        </div>
        <div ref={wrap} className="relative w-full flex-1 min-h-[420px] rounded-[12px] bg-surface border border-line overflow-hidden">
          <canvas
            ref={viewerHost}
            onPointerDown={down3d}
            onPointerMove={move3d}
            onPointerUp={up}
            className={cx("absolute inset-0 !w-full !h-full", paint3d && "cursor-crosshair")}
            aria-label={t("skin.view3d")}
          />
        </div>
        <div className="grid grid-cols-2 gap-2">
          <Button variant={!slim ? "primary" : "secondary"} onClick={() => setSlim(false)}>{t("skin.steve")}</Button>
          <Button variant={slim ? "primary" : "secondary"} onClick={() => setSlim(true)}>{t("skin.alex")}</Button>
        </div>
        <div className="flex gap-4 text-sm">
          <label className="flex items-center gap-2 cursor-pointer"><Toggle checked={showOver} onChange={setShowOver} label={t("skin.layer2")} />{t("skin.layer2")}</label>
          <label className="flex items-center gap-2 cursor-pointer"><Toggle checked={cape} onChange={setCape} label={t("skin.cape")} />{t("skin.cape")}</label>
        </div>
      </div>

      <div className="min-w-0 space-y-4">
        <Card className="p-3 flex flex-wrap items-center gap-2">
          {tools.map((tl) => (
            <IconButton key={tl.id} label={tl.label} onClick={() => setTool(tl.id)} className={tool === tl.id ? "!bg-accent !text-[var(--on-accent)]" : ""}>
              {tl.icon}
            </IconButton>
          ))}
          <IconButton label={t("skin.mirror")} onClick={() => setMirror(!mirror)} className={mirror ? "!bg-accent !text-[var(--on-accent)]" : ""}>
            <FlipHorizontal2 className="size-4" />
          </IconButton>
          <span className="w-px h-6 bg-line mx-1" />
          <IconButton label={t("skin.undo")} onClick={doUndo} disabled={!undo.current.length}><Undo2 className="size-4" /></IconButton>
          <IconButton label={t("skin.redo")} onClick={doRedo} disabled={!redo.current.length}><Redo2 className="size-4" /></IconButton>
          <span className="w-px h-6 bg-line mx-1" />
          <input type="color" aria-label={t("skin.color")} value={color} onChange={(e) => setColor(e.target.value)} className="size-10 rounded-[10px] bg-transparent border border-line cursor-pointer" />
          <Input className="!w-28" value={color} onChange={(e) => setColor(e.target.value)} aria-label="HEX" maxLength={7} />
          <div className="flex items-center gap-2 ml-2 text-xs text-dim">
            {t("skin.size")}
            {[1, 2, 3, 4].map((n) => (
              <button key={n} onClick={() => setSize(n)} className={cx("size-8 rounded-md text-xs font-bold", size === n ? "bg-accent text-[var(--on-accent)]" : "bg-surface-2")}>{n}</button>
            ))}
          </div>
          <div className="flex items-center gap-2 ml-2 text-xs">
            <button onClick={() => setLayer("base")} className={cx("h-8 px-3 rounded-md font-semibold", layer === "base" ? "bg-accent text-[var(--on-accent)]" : "bg-surface-2 text-dim")}>{t("skin.layer.base")}</button>
            <button onClick={() => setLayer("over")} className={cx("h-8 px-3 rounded-md font-semibold", layer === "over" ? "bg-accent text-[var(--on-accent)]" : "bg-surface-2 text-dim")}>{t("skin.layer.over")}</button>
          </div>
        </Card>
        <div className="flex flex-wrap gap-1.5">
          {PALETTE.map((c) => (
            <button key={c} aria-label={c} onClick={() => setColor(c)} style={{ background: c }} className={cx("size-7 rounded-md border", color === c ? "border-fg scale-110" : "border-line")} />
          ))}
        </div>
        <div className="flex justify-center">
          <canvas
            ref={uv}
            width={64}
            height={64}
            aria-label={t("skin.uv")}
            onPointerDown={down2d}
            onPointerMove={move2d}
            onPointerUp={up}
            className="rounded-[12px] border border-line cursor-crosshair touch-none"
            style={{ width: "min(100%, 440px)", imageRendering: "pixelated", aspectRatio: "1" }}
          />
        </div>
      </div>

      <div className="space-y-4 lg:col-span-2 xl:col-span-1">
        <Field label={t("skin.byName")}>
          <div className="flex gap-2">
            <Input value={nick} placeholder={t("skin.nickPlaceholder")} onChange={(e) => setNick(e.target.value)} onKeyDown={(e) => e.key === "Enter" && nick && byName()} maxLength={16} />
            <IconButton label={t("skin.byName")} onClick={byName} disabled={!nick}><UserSearch className="size-5" /></IconButton>
          </div>
        </Field>
        <div className="grid grid-cols-2 gap-2">
          <Button icon={<Upload className="size-4" />} onClick={importPng}>{t("skin.import")}</Button>
          <Button onClick={mine} disabled={!acc || acc.kind !== "microsoft"}>{t("skin.fromAccount")}</Button>
        </div>
        <Field label={t("skin.name")}>
          <Input value={name} onChange={(e) => setName(e.target.value)} maxLength={32} placeholder={t("skin.new")} />
        </Field>
        <div className="grid gap-2">
          <Button icon={<Save className="size-4" />} onClick={saveLib}>{t("skin.saveLib")}</Button>
          <Button icon={<Download className="size-4" />} onClick={exportPng}>{t("skin.export")}</Button>
          <Button variant="primary" onClick={upload} disabled={!acc || acc.kind !== "microsoft"}>{t("skin.upload")}</Button>
        </div>
        <div>
          <div className="font-semibold text-sm mb-2">{t("skin.library")}</div>
          <div className="grid grid-cols-3 gap-2">
            {library.map((s) => (
              <div key={s.name + s.slim} className="relative group">
                <button onClick={() => { setName(s.name); setSkinFrom(s.dataUrl, s.slim).catch((e) => toast("error", errText(e))); }} className="w-full aspect-square rounded-[10px] bg-surface-2 border border-line overflow-hidden hover:border-accent" title={s.name}>
                  <SkinPreview src={s.dataUrl} />
                </button>
                <button aria-label={t("common.delete")} onClick={() => api.skinDelete(s.name).then(loadLibrary)} className="absolute top-1 right-1 size-6 rounded bg-black/60 text-white hidden group-hover:flex items-center justify-center">
                  <Trash2 className="size-3" />
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

/** Front-face preview (head+body) of a skin for the library grid. */
function SkinPreview({ src }: { src: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    loadImage(src).then((img) => {
      const c = ref.current;
      if (!c) return;
      const g = c.getContext("2d")!;
      g.imageSmoothingEnabled = false;
      g.clearRect(0, 0, 16, 32);
      // head, body, arms, legs front faces (+ overlay head)
      const d = (sx: number, sy: number, w: number, h: number, dx: number, dy: number) => g.drawImage(img, sx, sy, w, h, dx, dy, w, h);
      d(8, 8, 8, 8, 4, 0);
      d(20, 20, 8, 12, 4, 8);
      d(44, 20, 4, 12, 0, 8);
      d(36, 52, 4, 12, 12, 8);
      d(4, 20, 4, 12, 4, 20);
      d(20, 52, 4, 12, 8, 20);
      d(40, 8, 8, 8, 4, 0);
    }).catch(() => {});
  }, [src]);
  return <canvas ref={ref} width={16} height={32} className="w-full h-full p-1" style={{ imageRendering: "pixelated", objectFit: "contain" }} />;
}
