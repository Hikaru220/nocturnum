//! Backups of worlds and settings before a build is updated (last 3 kept, one-click restore).

use crate::{error::R, instances, paths};
use anyhow::anyhow;
use serde::Serialize;
use std::io::Write;
use std::path::{Path, PathBuf};

const KEEP: usize = 3;
/// What gets saved: worlds, game options, server list, mod configs.
const ITEMS: &[&str] = &["saves", "options.txt", "servers.dat", "config"];

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Backup {
    pub file_name: String,
    pub created: u64,
    pub size: u64,
}

fn dir(id: &str) -> PathBuf {
    paths::root().join("backups").join(id)
}

fn add_path(zw: &mut zip::ZipWriter<std::fs::File>, base: &Path, p: &Path) -> R<()> {
    use zip::write::SimpleFileOptions;
    let rel = p.strip_prefix(base).map_err(|e| anyhow!("{e}"))?.to_string_lossy().replace('\\', "/");
    if p.is_dir() {
        for e in std::fs::read_dir(p)?.flatten() {
            add_path(zw, base, &e.path())?;
        }
    } else if p.is_file() {
        // region files are already compressed: store them as they are (fast, no CPU burst)
        let stored = rel.ends_with(".mca") || rel.ends_with(".mcc") || rel.ends_with(".png") || rel.ends_with(".jar");
        let opts = SimpleFileOptions::default().compression_method(if stored { zip::CompressionMethod::Stored } else { zip::CompressionMethod::Deflated }).large_file(true);
        // a file locked by something else is simply skipped
        let Ok(mut f) = std::fs::File::open(p) else { return Ok(()) };
        zw.start_file(rel, opts)?;
        std::io::copy(&mut f, zw)?;
    }
    Ok(())
}

pub fn list(id: &str) -> Vec<Backup> {
    let mut out = Vec::new();
    if let Ok(rd) = std::fs::read_dir(dir(id)) {
        for e in rd.flatten() {
            let n = e.file_name().to_string_lossy().to_string();
            if !n.ends_with(".zip") {
                continue;
            }
            if let Ok(md) = e.metadata() {
                let created = md.modified().ok().and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok()).map(|d| d.as_secs()).unwrap_or(0);
                out.push(Backup { file_name: n, created, size: md.len() });
            }
        }
    }
    out.sort_by(|a, b| b.created.cmp(&a.created));
    out
}

pub fn create(id: &str) -> R<Option<String>> {
    let game = instances::game_dir(id);
    if !ITEMS.iter().any(|i| game.join(i).exists()) {
        return Ok(None);
    }
    let d = dir(id);
    std::fs::create_dir_all(&d)?;
    let name = format!("{}.zip", chrono::Local::now().format("%Y-%m-%d_%H-%M-%S"));
    let tmp = d.join(format!("{name}.part"));
    {
        let mut zw = zip::ZipWriter::new(std::fs::File::create(&tmp)?);
        for i in ITEMS {
            let p = game.join(i);
            if p.exists() {
                add_path(&mut zw, &game, &p)?;
            }
        }
        zw.finish()?.flush()?;
    }
    std::fs::rename(&tmp, d.join(&name))?;
    // keep the last KEEP
    for old in list(id).into_iter().skip(KEEP) {
        let _ = std::fs::remove_file(d.join(old.file_name));
    }
    Ok(Some(name))
}

pub fn restore(id: &str, file_name: &str) -> R<()> {
    if file_name.contains('/') || file_name.contains('\\') || file_name.contains("..") {
        return Err(anyhow!("Неверное имя копии").into());
    }
    let src = dir(id).join(file_name);
    let game = instances::game_dir(id);
    let mut z = zip::ZipArchive::new(std::fs::File::open(&src).map_err(|_| anyhow!("Копия не найдена"))?)?;
    // worlds are replaced as a whole, so the restored state is exactly the saved one
    let has_saves = (0..z.len()).any(|i| z.name_for_index(i).map(|n| n.starts_with("saves/")).unwrap_or(false));
    if has_saves {
        let _ = std::fs::remove_dir_all(game.join("saves"));
    }
    for i in 0..z.len() {
        let mut e = z.by_index(i)?;
        let name = e.name().replace('\\', "/");
        if e.is_dir() || name.split('/').any(|p| p == "..") || name.starts_with('/') || name.contains(':') {
            continue;
        }
        if !ITEMS.iter().any(|it| name == *it || name.starts_with(&format!("{it}/"))) {
            continue;
        }
        let out = game.join(&name);
        if let Some(p) = out.parent() {
            std::fs::create_dir_all(p)?;
        }
        let mut f = std::fs::File::create(out)?;
        std::io::copy(&mut e, &mut f)?;
    }
    Ok(())
}
