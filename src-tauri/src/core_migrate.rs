//! NOCTURNUM: one-time move of the instances of the old launcher into the core.
//! A new instance is created through the core (game files, loader), then worlds, options, servers, packs,
//! configs, screenshots and mods are copied over. The old files are never deleted or changed.

use crate::core_instances::{create_instance, update_instance, wait_installed};
use crate::instances::Instance as UiInstance;
use crate::paths;
use std::collections::HashSet;
use std::path::Path;
use tauri::AppHandle;

fn marker() -> std::path::PathBuf {
    paths::root().join("core_migrated.json")
}

fn done() -> HashSet<String> {
    std::fs::read_to_string(marker()).ok().and_then(|s| serde_json::from_str::<Vec<String>>(&s).ok()).unwrap_or_default().into_iter().collect()
}

fn mark(id: &str) {
    let mut d = done();
    d.insert(id.to_string());
    let v: Vec<String> = d.into_iter().collect();
    let _ = std::fs::write(marker(), serde_json::to_vec(&v).unwrap_or_default());
}

fn copy_dir(src: &Path, dst: &Path) -> std::io::Result<()> {
    std::fs::create_dir_all(dst)?;
    for e in std::fs::read_dir(src)?.flatten() {
        let (from, to) = (e.path(), dst.join(e.file_name()));
        if from.is_dir() {
            copy_dir(&from, &to)?;
        } else if !to.exists() {
            std::fs::copy(&from, &to)?;
        }
    }
    Ok(())
}

/// What is carried over (the core keeps its own mods, they are copied too and picked up by its content scan).
const ITEMS: &[&str] = &["saves", "resourcepacks", "shaderpacks", "config", "screenshots", "mods", "options.txt", "servers.dat"];

async fn migrate_one(app: &AppHandle, old: &UiInstance) -> Result<(), String> {
    let req = crate::NewInstance {
        name: old.name.clone(),
        mc_version: old.mc_version.clone(),
        loader: old.loader.clone(),
        loader_version: old.loader_version.clone(),
        hud_enabled: false,
        account_id: String::new(),
    };
    let new = create_instance(app, &req).await.map_err(|e| e.0)?;
    wait_installed(&new.id).await.map_err(|e| e.0)?;
    let src = paths::instance_dir(&old.id).join("minecraft");
    let dst = theseus::instance::get_full_path(&new.id).await.map_err(|e| e.to_string())?;
    for item in ITEMS {
        let from = src.join(item);
        let to = dst.join(item);
        if from.is_dir() {
            copy_dir(&from, &to).map_err(|e| e.to_string())?;
        } else if from.is_file() {
            // the player's own options win over the ones the core wrote on creation
            std::fs::copy(&from, &to).map_err(|e| e.to_string())?;
        }
    }
    let _ = theseus::instance::sync_content_files(&new.id).await;
    // memory, Java, arguments, window and the server to join
    let mut settings = new.clone();
    settings.ram_mb = old.ram_mb;
    settings.java_path = old.java_path.clone();
    settings.jvm_args = old.jvm_args.clone();
    settings.width = old.width;
    settings.height = old.height;
    settings.fullscreen = old.fullscreen;
    settings.server = old.server.clone();
    let _ = update_instance(&settings).await;
    Ok(())
}

/// Runs once per old instance (marker file); errors leave the instance unmarked so the next start tries again.
pub async fn migrate_all(app: &AppHandle) {
    let finished = done();
    for old in crate::instances::list() {
        if finished.contains(&old.id) {
            continue;
        }
        match migrate_one(app, &old).await {
            Ok(()) => mark(&old.id),
            Err(e) => crate::applog(&format!("migration of {} failed: {e}", old.id)),
        }
    }
}
