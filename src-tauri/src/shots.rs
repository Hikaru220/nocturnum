//! Screenshot gallery: lists `screenshots/*.png` of every instance, 256 px thumbnails (cached), delete to the recycle bin.

use crate::{error::R, instances, paths};
use anyhow::anyhow;
use serde::Serialize;
use std::path::{Path, PathBuf};

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Shot {
    pub instance_id: String,
    pub instance_name: String,
    pub file_name: String,
    pub path: String,
    pub modified: u64,
    pub size: u64,
}

fn shots_dir(id: &str) -> PathBuf {
    instances::game_dir(id).join("screenshots")
}

pub fn list() -> Vec<Shot> {
    let mut out = Vec::new();
    for inst in instances::list() {
        let Ok(rd) = std::fs::read_dir(shots_dir(&inst.id)) else { continue };
        for e in rd.flatten() {
            let p = e.path();
            let ext = p.extension().map(|x| x.to_string_lossy().to_lowercase()).unwrap_or_default();
            if ext != "png" && ext != "jpg" && ext != "jpeg" {
                continue;
            }
            let Ok(md) = e.metadata() else { continue };
            let modified = md.modified().ok().and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok()).map(|d| d.as_secs()).unwrap_or(0);
            out.push(Shot {
                instance_id: inst.id.clone(),
                instance_name: inst.name.clone(),
                file_name: e.file_name().to_string_lossy().to_string(),
                path: p.to_string_lossy().to_string(),
                modified,
                size: md.len(),
            });
        }
    }
    out.sort_by(|a, b| b.modified.cmp(&a.modified));
    out
}

/// Only files directly inside some instance's `screenshots` folder may be touched.
fn check(path: &str) -> R<PathBuf> {
    let p = std::fs::canonicalize(path).map_err(|_| anyhow!("Файл не найден"))?;
    for inst in instances::list() {
        if let Ok(d) = std::fs::canonicalize(shots_dir(&inst.id)) {
            if p.parent() == Some(d.as_path()) {
                return Ok(p);
            }
        }
    }
    Err(anyhow!("Это не скриншот сборки").into())
}

fn thumb_path(src: &Path) -> PathBuf {
    let md = std::fs::metadata(src).ok();
    let mt = md.as_ref().and_then(|m| m.modified().ok()).and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok()).map(|d| d.as_secs()).unwrap_or(0);
    let len = md.map(|m| m.len()).unwrap_or(0);
    // FNV-1a over path + mtime + size
    let mut h: u64 = 0xcbf29ce484222325;
    for b in format!("{}|{}|{}", src.to_string_lossy(), mt, len).bytes() {
        h ^= b as u64;
        h = h.wrapping_mul(0x100000001b3);
    }
    paths::cache_dir().join("thumbs").join(format!("{h:016x}.jpg"))
}

/// 256 px JPEG thumbnail as a data URL (cached on disk).
pub fn thumb(path: &str) -> R<String> {
    use base64::Engine;
    let src = check(path)?;
    let t = thumb_path(&src);
    if !t.exists() {
        let img = image::open(&src).map_err(|e| anyhow!("не удалось открыть картинку: {e}"))?;
        let small = img.thumbnail(256, 256).to_rgb8();
        if let Some(d) = t.parent() {
            std::fs::create_dir_all(d)?;
        }
        let mut f = std::fs::File::create(&t)?;
        let enc = image::codecs::jpeg::JpegEncoder::new_with_quality(&mut f, 80);
        small.write_with_encoder(enc).map_err(|e| anyhow!("jpeg: {e}"))?;
    }
    let bytes = std::fs::read(&t)?;
    Ok(format!("data:image/jpeg;base64,{}", base64::engine::general_purpose::STANDARD.encode(bytes)))
}

/// Full image as a data URL (for copying to the clipboard / the preview).
pub fn full(path: &str) -> R<String> {
    use base64::Engine;
    let src = check(path)?;
    let bytes = std::fs::read(&src)?;
    if bytes.len() > 40 * 1024 * 1024 {
        return Err(anyhow!("Файл слишком большой").into());
    }
    let mime = if src.extension().map(|e| e.to_string_lossy().to_lowercase() != "png").unwrap_or(false) { "image/jpeg" } else { "image/png" };
    Ok(format!("data:{mime};base64,{}", base64::engine::general_purpose::STANDARD.encode(bytes)))
}

/// Recycle bin, not a permanent delete.
pub fn delete(path: &str) -> R<()> {
    let src = check(path)?;
    let _ = std::fs::remove_file(thumb_path(&src));
    trash::delete(&src).map_err(|e| anyhow!("не удалось переместить в корзину: {e}"))?;
    Ok(())
}
