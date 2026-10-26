//! Instances (сборки): each lives in its own folder with its own mods/config/saves/options.txt.

use crate::{error::R, paths};
use anyhow::anyhow;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(default, rename_all = "camelCase")]
pub struct Instance {
    pub id: String,
    pub name: String,
    pub mc_version: String,
    /// vanilla | fabric | quilt | neoforge | forge
    pub loader: String,
    pub loader_version: String,
    /// resolved launch profile id (e.g. fabric-loader-0.16.9-1.21.4)
    pub version_id: String,
    pub installed: bool,
    pub ram_mb: u32,
    pub java_path: String,
    pub jvm_args: String,
    pub account_id: String,
    pub icon: String,
    pub hud_enabled: bool,
    pub play_time_secs: u64,
    pub last_played: i64,
    pub created_at: i64,
    pub width: u32,
    pub height: u32,
    pub fullscreen: bool,
    pub server: String,
    /// relative paths that updates must never overwrite
    pub protected_files: Vec<String>,
    pub modpack_project: String,
    pub modpack_version: String,
}

pub fn game_dir(id: &str) -> PathBuf {
    paths::instance_dir(id).join("minecraft")
}

fn meta_path(id: &str) -> PathBuf {
    paths::instance_dir(id).join("instance.json")
}

pub fn slugify(name: &str) -> String {
    let mut s: String = name
        .chars()
        .map(|c| if c.is_alphanumeric() { c.to_ascii_lowercase() } else { '-' })
        .collect();
    while s.contains("--") {
        s = s.replace("--", "-");
    }
    let s = s.trim_matches('-').to_string();
    if s.is_empty() { "instance".into() } else { s }
}

pub fn unique_id(name: &str) -> String {
    let base = slugify(name);
    let mut id = base.clone();
    let mut n = 2;
    while paths::instance_dir(&id).exists() {
        id = format!("{base}-{n}");
        n += 1;
    }
    id
}

pub fn list() -> Vec<Instance> {
    let mut v: Vec<Instance> = Vec::new();
    if let Ok(rd) = std::fs::read_dir(paths::instances_dir()) {
        for e in rd.flatten() {
            if let Ok(s) = std::fs::read_to_string(e.path().join("instance.json")) {
                if let Ok(i) = serde_json::from_str::<Instance>(&s) {
                    v.push(i);
                }
            }
        }
    }
    v.sort_by(|a, b| b.last_played.max(b.created_at).cmp(&a.last_played.max(a.created_at)));
    v
}

pub fn get(id: &str) -> R<Instance> {
    let s = std::fs::read_to_string(meta_path(id)).map_err(|_| anyhow!("Сборка «{id}» не найдена"))?;
    Ok(serde_json::from_str(&s)?)
}

pub fn save(i: &Instance) -> R<()> {
    std::fs::create_dir_all(paths::instance_dir(&i.id))?;
    let tmp = meta_path(&i.id).with_extension("json.tmp");
    std::fs::write(&tmp, serde_json::to_vec_pretty(i)?)?;
    std::fs::rename(tmp, meta_path(&i.id))?;
    Ok(())
}

pub fn delete(id: &str) -> R<()> {
    if id.is_empty() || id.contains("..") || id.contains('/') || id.contains('\\') {
        return Err(anyhow!("Недопустимый id").into());
    }
    let d = paths::instance_dir(id);
    if d.exists() {
        std::fs::remove_dir_all(d)?;
    }
    Ok(())
}

pub fn duplicate(id: &str) -> R<Instance> {
    let mut i = get(id)?;
    let new_id = unique_id(&format!("{} копия", i.name));
    copy_dir(&paths::instance_dir(id), &paths::instance_dir(&new_id))?;
    i.id = new_id;
    i.name = format!("{} (копия)", i.name);
    i.play_time_secs = 0;
    i.last_played = 0;
    i.created_at = now();
    save(&i)?;
    Ok(i)
}

pub fn now() -> i64 {
    chrono::Utc::now().timestamp()
}

pub fn copy_dir(src: &std::path::Path, dst: &std::path::Path) -> std::io::Result<()> {
    std::fs::create_dir_all(dst)?;
    for e in std::fs::read_dir(src)? {
        let e = e?;
        let to = dst.join(e.file_name());
        if e.file_type()?.is_dir() {
            copy_dir(&e.path(), &to)?;
        } else {
            std::fs::copy(e.path(), to)?;
        }
    }
    Ok(())
}

// ---------- options.txt preset (ТЗ 3.2) ----------

pub const PRESET: &[(&str, &str)] = &[
    ("lang", "ru_ru"),
    ("guiScale", "2"),
    ("renderDistance", "12"),
    ("simulationDistance", "8"),
    ("graphicsMode", "1"),
    ("maxFps", "144"),
    ("enableVsync", "false"),
    // music off from the first launch (ТЗ preset: tuned for play, not for the menu theme)
    ("soundCategory_music", "0.0"),
    ("tutorialStep", "none"),
];

/// Write preset into options.txt, keeping any other keys already there.
pub fn write_preset(id: &str) -> R<()> {
    let dir = game_dir(id);
    std::fs::create_dir_all(&dir)?;
    let file = dir.join("options.txt");
    let existing = std::fs::read_to_string(&file).unwrap_or_default();
    let mut lines: Vec<String> = existing.lines().map(String::from).collect();
    for (k, v) in PRESET {
        let prefix = format!("{k}:");
        if let Some(l) = lines.iter_mut().find(|l| l.starts_with(&prefix)) {
            *l = format!("{k}:{v}");
        } else {
            lines.push(format!("{k}:{v}"));
        }
    }
    std::fs::write(&file, lines.join("\n") + "\n")?;
    Ok(())
}

// ---------- content listing ----------

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ContentFile {
    pub file_name: String,
    pub enabled: bool,
    pub size: u64,
    pub sha1: String,
    pub project_id: String,
    pub version_id: String,
    pub title: String,
}

fn content_dir(id: &str, kind: &str) -> R<PathBuf> {
    let sub = match kind {
        "mods" => "mods",
        "resourcepacks" => "resourcepacks",
        "shaderpacks" => "shaderpacks",
        _ => return Err(anyhow!("Неизвестный тип контента").into()),
    };
    Ok(game_dir(id).join(sub))
}

fn index_path(id: &str) -> PathBuf {
    paths::instance_dir(id).join("installed.json")
}

#[derive(Serialize, Deserialize, Clone, Default, Debug)]
#[serde(default, rename_all = "camelCase")]
pub struct InstalledMeta {
    pub project_id: String,
    pub version_id: String,
    pub title: String,
    pub sha1: String,
}

pub fn load_index(id: &str) -> std::collections::HashMap<String, InstalledMeta> {
    std::fs::read_to_string(index_path(id)).ok().and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default()
}

pub fn save_index(id: &str, idx: &std::collections::HashMap<String, InstalledMeta>) -> R<()> {
    std::fs::write(index_path(id), serde_json::to_vec_pretty(idx)?)?;
    Ok(())
}

pub fn list_content(id: &str, kind: &str) -> R<Vec<ContentFile>> {
    let dir = content_dir(id, kind)?;
    let idx = load_index(id);
    let mut out = Vec::new();
    if let Ok(rd) = std::fs::read_dir(&dir) {
        for e in rd.flatten() {
            let name = e.file_name().to_string_lossy().to_string();
            let enabled = !name.ends_with(".disabled");
            let base = name.trim_end_matches(".disabled").to_string();
            let is_file = e.path().is_file();
            let is_dir_pack = e.path().is_dir() && kind != "mods";
            if !(is_file || is_dir_pack) {
                continue;
            }
            let ext_ok = base.ends_with(".jar") || base.ends_with(".zip") || is_dir_pack;
            if !ext_ok {
                continue;
            }
            let meta = idx.get(&base).cloned().unwrap_or_default();
            out.push(ContentFile {
                file_name: name,
                enabled,
                size: e.metadata().map(|m| m.len()).unwrap_or(0),
                sha1: meta.sha1,
                project_id: meta.project_id,
                version_id: meta.version_id,
                title: meta.title,
            });
        }
    }
    out.sort_by(|a, b| a.file_name.to_lowercase().cmp(&b.file_name.to_lowercase()));
    Ok(out)
}

fn safe_name(name: &str) -> R<()> {
    if name.contains('/') || name.contains('\\') || name.contains("..") || name.is_empty() {
        return Err(anyhow!("Недопустимое имя файла").into());
    }
    Ok(())
}

pub fn toggle_content(id: &str, kind: &str, file: &str, enable: bool) -> R<()> {
    safe_name(file)?;
    let dir = content_dir(id, kind)?;
    let cur = dir.join(file);
    let target = if enable {
        dir.join(file.trim_end_matches(".disabled"))
    } else if file.ends_with(".disabled") {
        cur.clone()
    } else {
        dir.join(format!("{file}.disabled"))
    };
    if cur != target {
        std::fs::rename(cur, target)?;
    }
    Ok(())
}

pub fn remove_content(id: &str, kind: &str, file: &str) -> R<()> {
    safe_name(file)?;
    let p = content_dir(id, kind)?.join(file);
    if p.is_dir() {
        std::fs::remove_dir_all(p)?;
    } else if p.exists() {
        std::fs::remove_file(p)?;
    }
    let mut idx = load_index(id);
    idx.remove(file.trim_end_matches(".disabled"));
    save_index(id, &idx)?;
    Ok(())
}

pub fn add_content_file(id: &str, kind: &str, src: &std::path::Path) -> R<String> {
    let dir = content_dir(id, kind)?;
    std::fs::create_dir_all(&dir)?;
    let name = src.file_name().ok_or_else(|| anyhow!("bad path"))?.to_string_lossy().to_string();
    std::fs::copy(src, dir.join(&name))?;
    Ok(name)
}

pub fn list_screenshots(id: &str) -> Vec<String> {
    let dir = game_dir(id).join("screenshots");
    let mut v: Vec<String> = std::fs::read_dir(dir)
        .map(|rd| {
            rd.flatten()
                .filter(|e| e.path().extension().map(|x| x == "png").unwrap_or(false))
                .map(|e| e.path().to_string_lossy().to_string())
                .collect()
        })
        .unwrap_or_default();
    v.sort();
    v.reverse();
    v
}
