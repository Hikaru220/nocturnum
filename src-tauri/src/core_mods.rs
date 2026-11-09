//! NOCTURNUM: the mod set of a new instance (spec chapter 6) installed through the core:
//! Fabric API, our lightweight HUD mod, the optimisation mods and the ready-made mods.
//! A mod that does not exist for the game version is skipped quietly (never breaks the install).

use crate::{hud, net};
use tauri::AppHandle;
use theseus::instance::InstallProjectWithDependenciesRequest;
use theseus::prelude::*;

/// Waits until the core shows `project_id` among the instance's projects.
async fn wait_for(id: &str, project_id: &str) -> bool {
    for _ in 0..240 {
        if theseus::instance::get_installed_project_ids(id).await.map(|v| v.iter().any(|p| p == project_id)).unwrap_or(false) {
            return true;
        }
        tokio::time::sleep(std::time::Duration::from_millis(500)).await;
    }
    false
}

async fn install_slug(id: &str, slug_or_id: &str) -> bool {
    let Ok(p) = crate::modrinth::project(slug_or_id).await else { return false };
    let Some(pid) = p["id"].as_str() else { return false };
    if theseus::instance::get_installed_project_ids(id).await.map(|v| v.iter().any(|x| x == pid)).unwrap_or(false) {
        return true;
    }
    let req = InstallProjectWithDependenciesRequest { project_id: pid.to_string(), version_id: None, content_type: ContentType::Mod, selected: Default::default() };
    if theseus::instance::install_project_with_dependencies(id, req).await.is_err() {
        return false; // no version for this game version / loader
    }
    wait_for(id, pid).await
}

/// Installs the whole set into a freshly created Fabric instance.
pub async fn install_set(app: &AppHandle, id: &str, mc: &str, loader: &str) {
    let ev = format!("install:{id}");
    let st = hud::status(app, mc, loader);
    if !st.supported {
        return; // the HUD mod needs Fabric on a supported version; other instances stay clean
    }
    net::emit_progress(app, &ev, "Установка модов", 5.0, 0.0, false, None);
    install_slug(id, hud::FABRIC_API).await;
    if let Some(jar) = hud::bundled_jar(app, mc) {
        let _ = theseus::instance::add_project_from_path(id, &jar, Some(ProjectType::Mod)).await;
    }
    let s = crate::settings::load();
    let slugs: Vec<&str> = if s.install_sodium || s.install_optimization {
        hud::OPTIMIZATION_SLUGS.iter().chain(hud::READY_MOD_SLUGS.iter()).copied().collect()
    } else {
        hud::READY_MOD_SLUGS.to_vec()
    };
    let total = slugs.len().max(1) as f64;
    for (i, slug) in slugs.iter().enumerate() {
        net::emit_progress(app, &ev, &format!("Установка модов: {slug}"), 5.0 + 90.0 * i as f64 / total, 0.0, false, None);
        install_slug(id, slug).await;
    }
    fix_missing_deps(id).await;
    if let Ok(dir) = theseus::instance::get_full_path(id).await {
        hud::tune_dynamic_fps(&dir.join("mods"));
    }
    net::emit_progress(app, &ev, "Готово", 100.0, 0.0, true, None);
}

/// Up to three passes: required mod ids that no installed jar provides are fetched from Modrinth.
async fn fix_missing_deps(id: &str) {
    let Ok(dir) = theseus::instance::get_full_path(id).await else { return };
    let mods = dir.join("mods");
    let mut tried: std::collections::HashSet<String> = Default::default();
    for _ in 0..3 {
        let mut provided = Default::default();
        let mut required = Default::default();
        if let Ok(rd) = std::fs::read_dir(&mods) {
            for e in rd.flatten() {
                let p = e.path();
                if p.extension().map(|x| x == "jar").unwrap_or(false) {
                    hud::scan_jar(&p, &mut provided, &mut required, 0);
                }
            }
        }
        let missing: Vec<String> = required.iter().filter(|r| !provided.contains(*r) && !hud::builtin_dep(r) && !tried.contains(*r)).cloned().collect();
        if missing.is_empty() {
            return;
        }
        for m in missing {
            tried.insert(m.clone());
            install_slug(id, &m).await;
        }
    }
}
