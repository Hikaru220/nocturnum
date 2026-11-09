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

/// Folders of instances that live in the core (filled by `core_instances`); old-layout instances fall back to the old path.
static CORE_DIRS: once_cell::sync::Lazy<std::sync::Mutex<std::collections::HashMap<String, PathBuf>>> = once_cell::sync::Lazy::new(Default::default);

pub fn remember_core_dir(id: &str, dir: PathBuf) {
    CORE_DIRS.lock().unwrap().insert(id.to_string(), dir);
}

pub fn game_dir(id: &str) -> PathBuf {
    if let Some(p) = CORE_DIRS.lock().unwrap().get(id) {
        return p.clone();
    }
    paths::instance_dir(id).join("minecraft")
}

fn meta_path(id: &str) -> PathBuf {
    paths::instance_dir(id).join("instance.json")
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
    ("maxFps", "260"),
    ("enableVsync", "false"),
    // music off from the first launch (ТЗ preset: tuned for play, not for the menu theme)
    ("soundCategory_music", "0.0"),
    ("tutorialStep", "none"),
];

/// Graphics values for a hardware tier (ТЗ пакет 2, 7.2 "Автонастройка под ПК").
pub fn tier_values(tier: &str) -> Vec<(&'static str, &'static str)> {
    match tier {
        "low" => vec![("renderDistance", "6"), ("simulationDistance", "5"), ("graphicsMode", "0"), ("particles", "2"), ("maxFps", "260"), ("entityDistanceScaling", "0.75")],
        "high" => vec![("renderDistance", "16"), ("simulationDistance", "10"), ("graphicsMode", "1"), ("particles", "0"), ("maxFps", "260")],
        _ => vec![("renderDistance", "10"), ("simulationDistance", "8"), ("graphicsMode", "1"), ("particles", "1"), ("maxFps", "260")],
    }
}


pub fn write_preset_tier(id: &str, tier: &str) -> R<()> {
    let dir = game_dir(id);
    std::fs::create_dir_all(&dir)?;
    let file = dir.join("options.txt");
    let existing = std::fs::read_to_string(&file).unwrap_or_default();
    let mut lines: Vec<String> = existing.lines().map(String::from).collect();
    let mut values: Vec<(&str, &str)> = PRESET.to_vec();
    if !tier.is_empty() {
        for (k, v) in tier_values(tier) {
            if let Some(e) = values.iter_mut().find(|(kk, _)| *kk == k) { e.1 = v; } else { values.push((k, v)); }
        }
    }
    for (k, v) in values {
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

