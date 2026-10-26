//! HUD mod installer (ТЗ 3.8): puts the bundled Nocturnum HUD jar, Fabric API (and Sodium/Lithium)
//! into an instance, with a clear compatibility message instead of a broken game.

use crate::{
    error::R,
    instances::{self, Instance},
    modrinth, settings,
};
use anyhow::anyhow;
use serde::Serialize;
use serde_json::Value;
use std::path::PathBuf;
use tauri::{AppHandle, Manager};

pub const FABRIC_API: &str = "P7dR8mSH";
pub const SODIUM: &str = "AANobbMI";
pub const LITHIUM: &str = "gvQqBUqZ";

/// Layer 1 of the optimisation stack (ТЗ пакет 2, п. 7.1): original files from Modrinth, never bundled.
pub const OPTIMIZATION_SLUGS: &[&str] = &["sodium", "lithium", "ferrite-core", "modernfix", "immediatelyfast", "entityculling", "moreculling", "krypton"];

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct HudStatus {
    pub supported: bool,
    pub reason: String,
    pub versions: Vec<String>,
}

fn resource_dir(app: &AppHandle) -> Vec<PathBuf> {
    let mut v = Vec::new();
    if let Ok(r) = app.path().resource_dir() {
        v.push(r.join("resources").join("hud"));
        v.push(r.join("hud"));
    }
    v.push(PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("resources").join("hud"));
    v
}

fn manifest(app: &AppHandle) -> Option<(PathBuf, Value)> {
    for d in resource_dir(app) {
        if let Ok(s) = std::fs::read_to_string(d.join("manifest.json")) {
            if let Ok(v) = serde_json::from_str::<Value>(&s) {
                return Some((d, v));
            }
        }
    }
    None
}

pub fn status(app: &AppHandle, mc: &str, loader: &str) -> HudStatus {
    let Some((_, m)) = manifest(app) else {
        return HudStatus { supported: false, reason: "HUD-мод не найден в составе лаунчера".into(), versions: vec![] };
    };
    let versions: Vec<String> = m["versions"].as_object().map(|o| o.keys().cloned().collect()).unwrap_or_default();
    if loader != "fabric" {
        return HudStatus {
            supported: false,
            reason: "HUD-мод работает только с загрузчиком Fabric. Создайте сборку на Fabric.".into(),
            versions,
        };
    }
    if !versions.iter().any(|v| v == mc) {
        return HudStatus {
            supported: false,
            reason: format!("HUD-мод пока не поддерживает версию {mc}. Доступно: {}.", versions.join(", ")),
            versions,
        };
    }
    HudStatus { supported: true, reason: String::new(), versions }
}

pub async fn install(app: &AppHandle, inst: &Instance) -> R<()> {
    let st = status(app, &inst.mc_version, &inst.loader);
    if !st.supported {
        return Err(anyhow!(st.reason).into());
    }
    let (dir, m) = manifest(app).ok_or_else(|| anyhow!("manifest HUD не найден"))?;
    let file = m["versions"][&inst.mc_version].as_str().ok_or_else(|| anyhow!("нет файла HUD"))?;
    let src = dir.join(file);
    if !src.exists() {
        return Err(anyhow!("Файл HUD-мода {file} отсутствует").into());
    }
    let mods = instances::game_dir(&inst.id).join("mods");
    std::fs::create_dir_all(&mods)?;
    // remove older builds
    if let Ok(rd) = std::fs::read_dir(&mods) {
        for e in rd.flatten() {
            let n = e.file_name().to_string_lossy().to_string();
            if n.starts_with("nocturnum-hud") && n != file && !n.ends_with(".disabled") {
                let _ = std::fs::remove_file(e.path());
            }
        }
    }
    let dest = mods.join(file);
    let need = !dest.exists() || std::fs::metadata(&dest).map(|m| m.len()).unwrap_or(0) != std::fs::metadata(&src)?.len();
    if need {
        std::fs::copy(&src, &dest)?;
    }
    let idx = instances::load_index(&inst.id);
    let has = |p: &str| idx.values().any(|m| m.project_id == p);
    let pid = format!("hud:{}", inst.id);
    if !has(FABRIC_API) {
        modrinth::install_project(app, &pid, inst, FABRIC_API, None, "mod").await?;
    }
    let s = settings::load();
    if s.install_sodium || s.install_optimization {
        for slug in OPTIMIZATION_SLUGS {
            // resolve slug -> project id so the "already installed" check works
            let Ok(p) = modrinth::project(slug).await else { continue };
            let Some(pid2) = p["id"].as_str() else { continue };
            if idx.values().any(|m| m.project_id == pid2) {
                continue;
            }
            // incompatible with this game version -> skipped quietly, never crashes the launch
            if let Err(e) = modrinth::install_project(app, &pid, inst, pid2, None, "mod").await {
                crate::net::emit_progress(app, &pid, &format!("{slug}: пропущен ({e})"), 0.0, 0.0, false, None);
            }
        }
    }
    Ok(())
}
