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

/// Layer 1 of the optimisation stack (ТЗ пакет 2, п. 7.1): original files from Modrinth, never bundled.
/// ТЗ 7.2: ready-made mods that replace our own functions (minimap, zoom, chat, F3 info, skins, status effects).
pub const READY_MOD_SLUGS: &[&str] = &["fabric-language-kotlin", "yacl", "cloth-config", "xaeros-minimap", "xaeros-world-map", "zoomify", "chatpatches", "chat-heads", "appleskin", "status-effect-bars", "minihud", "betterf3", "3dskinlayers", "not-enough-animations", "wavey-capes", "modmenu"];

pub const OPTIMIZATION_SLUGS: &[&str] = &["sodium", "sodium-extra", "lithium", "ferrite-core", "modernfix", "immediatelyfast", "entityculling", "moreculling", "badoptimizations", "dynamic-fps", "spark"];

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

/// Path of the bundled HUD jar for a game version (for the core, which copies it into the instance itself).
pub(crate) fn bundled_jar(app: &AppHandle, mc: &str) -> Option<PathBuf> {
    let (dir, m) = manifest(app)?;
    let file = m["versions"][mc].as_str()?;
    let p = dir.join(file);
    if p.exists() { Some(p) } else { None }
}


/// Dynamic FPS defaults throttle a window that is merely not focused (1 FPS) or only hovered (60 FPS), which makes the
/// game crawl while a browser is open next to it. Keep full speed there; a minimised window (0 FPS) and a long idle
/// (10 FPS) still save resources. A config the player has already changed is never touched.
pub(crate) fn tune_dynamic_fps(mods: &std::path::Path) {
    let has = std::fs::read_dir(mods).map(|rd| rd.flatten().any(|e| e.file_name().to_string_lossy().starts_with("dynamic-fps"))).unwrap_or(false);
    if !has {
        return;
    }
    let Some(game) = mods.parent() else { return };
    let cfg = game.join("config").join("dynamic_fps.json");
    let current = std::fs::read_to_string(&cfg).unwrap_or_default();
    if !current.trim().is_empty() && current.trim() != "{}" {
        return;
    }
    let state = |fps: i32, vol: f64| serde_json::json!({
        "frame_rate_target": fps, "enable_vsync": "false", "volume_multipliers": if vol < 1.0 { serde_json::json!({"master": vol}) } else { serde_json::json!({}) },
        "graphics_state": "default", "show_toasts": false, "run_garbage_collector": false
    });
    let v = serde_json::json!({
        "enabled": true, "uncap_menu_frame_rate": true, "ignore_initial_click": "disabled",
        "idle": {"timeout": 300, "condition": "vanilla"},
        "states": {
            "hovered": state(-1, 1.0),
            "unfocused": state(-1, 1.0),
            "invisible": state(0, 0.0),
            "unplugged": state(-1, 1.0),
            "abandoned": state(10, 1.0)
        },
        "download_natives": false, "mock_battery_data": false
    });
    let _ = std::fs::create_dir_all(cfg.parent().unwrap());
    let _ = std::fs::write(&cfg, serde_json::to_vec_pretty(&v).unwrap_or_default());
}

/// Mod ids a jar provides (own id, `provides`, nested jars) and ids it requires.
pub(crate) fn scan_jar(path: &std::path::Path, provided: &mut std::collections::HashSet<String>, required: &mut std::collections::HashSet<String>, depth: u8) {
    let Ok(f) = std::fs::File::open(path) else { return };
    let Ok(mut z) = zip::ZipArchive::new(f) else { return };
    scan_zip(&mut z, provided, required, depth);
}

fn scan_zip<R: std::io::Read + std::io::Seek>(z: &mut zip::ZipArchive<R>, provided: &mut std::collections::HashSet<String>, required: &mut std::collections::HashSet<String>, depth: u8) {
    use std::io::Read;
    let json: Option<Value> = z.by_name("fabric.mod.json").ok().and_then(|mut e| {
        let mut s = String::new();
        e.read_to_string(&mut s).ok()?;
        serde_json::from_str(s.trim_start_matches('\u{feff}')).ok()
    });
    if let Some(j) = json {
        if let Some(id) = j["id"].as_str() {
            provided.insert(id.to_string());
        }
        for p in j["provides"].as_array().into_iter().flatten().filter_map(|x| x.as_str()) {
            provided.insert(p.to_string());
        }
        if let Some(d) = j["depends"].as_object() {
            for k in d.keys() {
                required.insert(k.clone());
            }
        }
        if depth < 2 {
            let nested: Vec<String> = j["jars"].as_array().into_iter().flatten().filter_map(|x| x["file"].as_str().map(String::from)).collect();
            for n in nested {
                let bytes = z.by_name(&n).ok().and_then(|mut e| {
                    let mut b = Vec::new();
                    e.read_to_end(&mut b).ok()?;
                    Some(b)
                });
                if let Some(b) = bytes {
                    if let Ok(mut nz) = zip::ZipArchive::new(std::io::Cursor::new(b)) {
                        scan_zip(&mut nz, provided, required, depth + 1);
                    }
                }
            }
        }
    }
}

/// Missing ids that are not real downloadable mods (platform / bundled API modules).
pub(crate) fn builtin_dep(id: &str) -> bool {
    matches!(id, "minecraft" | "java" | "fabricloader" | "fabric" | "quilt_loader" | "fabric-api") || (id.starts_with("fabric-") && !id.starts_with("fabric-language-"))
}


#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LicenseRow {
    pub slug: String,
    pub title: String,
    pub license: String,
    pub url: String,
}

/// "About the build": title and licence of every bundled third-party mod, straight from Modrinth.
pub async fn licenses() -> Vec<LicenseRow> {
    let all: Vec<&str> = OPTIMIZATION_SLUGS.iter().chain(READY_MOD_SLUGS.iter()).copied().collect();
    let futs = all.iter().map(|slug| async move {
        let p = modrinth::project(slug).await.ok()?;
        Some(LicenseRow {
            slug: slug.to_string(),
            title: p["title"].as_str().unwrap_or(slug).to_string(),
            license: p["license"]["name"].as_str().or(p["license"]["id"].as_str()).unwrap_or("?").to_string(),
            url: format!("https://modrinth.com/mod/{slug}"),
        })
    });
    futures_util::future::join_all(futs).await.into_iter().flatten().collect()
}
