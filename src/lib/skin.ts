// Skin texture geometry (64x64, Minecraft 1.8+ layout) and mirroring helpers.

export type Face = "top" | "bottom" | "right" | "front" | "left" | "back";
export type Part = "head" | "body" | "rArm" | "lArm" | "rLeg" | "lLeg";

export interface FaceRect {
  part: Part;
  overlay: boolean;
  face: Face;
  x: number;
  y: number;
  w: number;
  h: number;
}

const ORIGIN: Record<Part, [number, number]> = {
  head: [0, 0],
  body: [16, 16],
  rArm: [40, 16],
  lArm: [32, 48],
  rLeg: [0, 16],
  lLeg: [16, 48],
};
const OVERLAY_ORIGIN: Record<Part, [number, number]> = {
  head: [32, 0],
  body: [16, 32],
  rArm: [40, 32],
  lArm: [48, 48],
  rLeg: [0, 32],
  lLeg: [0, 48],
};

function dims(part: Part, slim: boolean): [number, number, number] {
  switch (part) {
    case "head":
      return [8, 8, 8];
    case "body":
      return [8, 12, 4];
    case "rArm":
    case "lArm":
      return [slim ? 3 : 4, 12, 4];
    default:
      return [4, 12, 4];
  }
}

export function faceRects(slim: boolean): FaceRect[] {
  const out: FaceRect[] = [];
  const parts: Part[] = ["head", "body", "rArm", "lArm", "rLeg", "lLeg"];
  for (const overlay of [false, true]) {
    for (const part of parts) {
      const [u, v] = (overlay ? OVERLAY_ORIGIN : ORIGIN)[part];
      const [w, h, d] = dims(part, slim);
      const add = (face: Face, x: number, y: number, fw: number, fh: number) =>
        out.push({ part, overlay, face, x: u + x, y: v + y, w: fw, h: fh });
      add("top", d, 0, w, d);
      add("bottom", d + w, 0, w, d);
      add("right", 0, d, d, h);
      add("front", d, d, w, h);
      add("left", d + w, d, d, h);
      add("back", 2 * d + w, d, w, h);
    }
  }
  return out;
}

const MIRROR_PART: Record<Part, Part> = { head: "head", body: "body", rArm: "lArm", lArm: "rArm", rLeg: "lLeg", lLeg: "rLeg" };
const MIRROR_FACE: Record<Face, Face> = { top: "top", bottom: "bottom", front: "front", back: "back", right: "left", left: "right" };

export interface Mirror {
  (x: number, y: number): [number, number] | null;
}

/** Build a function mapping a texel to its left/right mirrored counterpart. */
export function makeMirror(slim: boolean): Mirror {
  const rects = faceRects(slim);
  const find = (x: number, y: number) => rects.find((r) => x >= r.x && x < r.x + r.w && y >= r.y && y < r.y + r.h);
  return (x, y) => {
    const r = find(x, y);
    if (!r) return null;
    const mp = MIRROR_PART[r.part];
    const mf = r.part === "head" || r.part === "body" ? (r.face === "left" || r.face === "right" ? MIRROR_FACE[r.face] : r.face) : MIRROR_FACE[r.face];
    const target = rects.find((t) => t.part === mp && t.overlay === r.overlay && t.face === mf);
    if (!target) return null;
    const lx = x - r.x;
    const ly = y - r.y;
    // horizontal flip inside the face; vertical position is kept
    const tx = target.x + (target.w - 1 - Math.min(lx, target.w - 1));
    return [tx, target.y + Math.min(ly, target.h - 1)];
  };
}

/** Convert a legacy 64x32 skin to 64x64 by mirroring right limbs into left ones. */
export function upgradeLegacy(src: HTMLCanvasElement | HTMLImageElement, srcH: number): HTMLCanvasElement {
  const c = document.createElement("canvas");
  c.width = 64;
  c.height = 64;
  const ctx = c.getContext("2d", { willReadFrequently: true })!;
  ctx.imageSmoothingEnabled = false;
  ctx.drawImage(src, 0, 0);
  if (srcH === 32) {
    const mir = makeMirror(false);
    const img = ctx.getImageData(0, 0, 64, 64);
    const d = img.data;
    for (const r of faceRects(false)) {
      if (r.overlay || (r.part !== "rArm" && r.part !== "rLeg")) continue;
      for (let y = r.y; y < r.y + r.h; y++) {
        for (let x = r.x; x < r.x + r.w; x++) {
          const m = mir(x, y);
          if (!m) continue;
          const si = (y * 64 + x) * 4;
          const di = (m[1] * 64 + m[0]) * 4;
          d[di] = d[si];
          d[di + 1] = d[si + 1];
          d[di + 2] = d[si + 2];
          d[di + 3] = d[si + 3];
        }
      }
    }
    ctx.putImageData(img, 0, 0);
  }
  return c;
}

/** Guess slim model: the 4th arm column is fully transparent in Alex skins. */
export function detectSlim(c: HTMLCanvasElement): boolean {
  const ctx = c.getContext("2d")!;
  const d = ctx.getImageData(54, 20, 2, 12).data;
  for (let i = 3; i < d.length; i += 4) if (d[i] !== 0) return false;
  return true;
}

export function hexToRgba(hex: string): [number, number, number, number] {
  const m = /^#?([0-9a-f]{6})$/i.exec(hex);
  const n = m ? parseInt(m[1], 16) : 0;
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255, 255];
}

export function rgbaToHex(r: number, g: number, b: number): string {
  return "#" + [r, g, b].map((v) => v.toString(16).padStart(2, "0")).join("");
}

/** Simple procedural starter skin (no copyrighted assets). */
export function drawDefaultSkin(c: HTMLCanvasElement, slim: boolean) {
  const g = c.getContext("2d")!;
  g.clearRect(0, 0, 64, 64);
  const colors: Record<Part, string> = { head: "#e3b58f", body: "#6f5cf0", rArm: "#e3b58f", lArm: "#e3b58f", rLeg: "#2b2b4a", lLeg: "#2b2b4a" };
  for (const r of faceRects(slim)) {
    if (r.overlay) continue;
    g.fillStyle = colors[r.part];
    g.fillRect(r.x, r.y, r.w, r.h);
    if (r.part === "head" && r.face === "front") {
      g.fillStyle = "#2b2b3a";
      g.fillRect(r.x + 1, r.y + 3, 2, 1);
      g.fillRect(r.x + 5, r.y + 3, 2, 1);
      g.fillStyle = "#b36b5b";
      g.fillRect(r.x + 3, r.y + 6, 2, 1);
    }
    if (r.part === "head" && (r.face === "top" || r.face === "back" || r.face === "left" || r.face === "right")) {
      g.fillStyle = "#3b2a22";
      g.fillRect(r.x, r.y, r.w, r.face === "top" || r.face === "back" ? r.h : 2);
    }
    if (r.part === "head" && r.face === "front") {
      g.fillStyle = "#3b2a22";
      g.fillRect(r.x, r.y, r.w, 2);
    }
  }
}
