//! NOCTURNUM: instances, install jobs and launch through the Modrinth App core (`theseus`).
//! The interface keeps its own data shapes (`instances::Instance`) and `progress` / `game-*` events.

use crate::core::{auth_message, err};
use crate::instances::Instance as UiInstance;
use crate::{error::R, net};
use anyhow::anyhow;
use std::collections::HashMap;
use tauri::{Manager, AppHandle, Emitter};
use theseus::install::{InstallJobSnapshot, InstallJobStatus, InstallPhaseId};
use theseus::prelude::*;

fn to_ui(m: &InstanceMetadata) -> UiInstance {
    let i = &m.instance;
    let cs = &m.applied_content_set;
    let lo = &m.launch_overrides;
    let (project, version) = match &m.link {
        InstanceLink::ModrinthModpack { project_id, version_id } => (project_id.clone(), version_id.clone()),
        _ => (String::new(), String::new()),
    };
    UiInstance {
        id: i.id.clone(),
        name: i.name.clone(),
        mc_version: cs.game_version.clone(),
        loader: cs.loader.as_str().to_string(),
        loader_version: cs.loader_version.clone().unwrap_or_default(),
        installed: format!("{:?}", i.install_stage) == "Installed",
        ram_mb: lo.memory.as_ref().map(|m| m.maximum).unwrap_or(0),
        java_path: lo.java_path.clone().unwrap_or_default(),
        jvm_args: lo.extra_launch_args.as_ref().map(|a| a.join(" ")).unwrap_or_default(),
        icon: i.icon_path.clone().unwrap_or_default(),
        hud_enabled: true,
        play_time_secs: i.submitted_time_played + i.recent_time_played,
        last_played: i.last_played.map(|t| t.timestamp()).unwrap_or(0),
        created_at: i.created.timestamp(),
        width: lo.game_resolution.map(|r| r.0 as u32).unwrap_or(0),
        height: lo.game_resolution.map(|r| r.1 as u32).unwrap_or(0),
        fullscreen: lo.force_fullscreen.unwrap_or(false),
        server: server_of(&i.id),
        modpack_project: project,
        modpack_version: version,
        ..Default::default()
    }
}

// The server to join on launch is kept next to our data (the core has no such field).
fn servers_file() -> std::path::PathBuf {
    crate::paths::root().join("instance_servers.json")
}

fn load_servers() -> HashMap<String, String> {
    std::fs::read_to_string(servers_file()).ok().and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default()
}

fn server_of(id: &str) -> String {
    load_servers().get(id).cloned().unwrap_or_default()
}

pub fn set_server(id: &str, server: &str) {
    let mut m = load_servers();
    if server.trim().is_empty() {
        m.remove(id);
    } else {
        m.insert(id.to_string(), server.trim().to_string());
    }
    let _ = std::fs::write(servers_file(), serde_json::to_vec(&m).unwrap_or_default());
}

/// Remembers where the core keeps an instance, so the old helpers (screenshots, backups, folder) find it.
async fn remember_dir(id: &str) {
    if let Ok(p) = theseus::instance::get_full_path(id).await {
        crate::instances::remember_core_dir(id, p);
    }
}

pub async fn list_instances() -> R<Vec<UiInstance>> {
    for m in theseus::instance::list().await.map_err(err)? {
        remember_dir(&m.instance.id).await;
    }
    let mut v: Vec<UiInstance> = theseus::instance::list().await.map_err(err)?.iter().map(to_ui).collect();
    v.sort_by(|a, b| b.last_played.cmp(&a.last_played).then(b.created_at.cmp(&a.created_at)));
    Ok(v)
}

pub async fn get_instance(id: &str) -> R<UiInstance> {
    remember_dir(id).await;
    let m = theseus::instance::get(id).await.map_err(err)?.ok_or_else(|| anyhow!("Сборка не найдена"))?;
    Ok(to_ui(&m))
}

fn phase_label(p: InstallPhaseId) -> &'static str {
    match p {
        InstallPhaseId::PreparingInstance => "Подготовка сборки",
        InstallPhaseId::ResolvingPack => "Поиск сборки",
        InstallPhaseId::DownloadingPackFile => "Загрузка модпака",
        InstallPhaseId::ReadingPackManifest => "Чтение модпака",
        InstallPhaseId::DownloadingContent => "Загрузка модов",
        InstallPhaseId::ExtractingOverrides => "Распаковка файлов",
        InstallPhaseId::ResolvingMinecraft => "Загрузка метаданных версии",
        InstallPhaseId::ResolvingLoader => "Загрузка загрузчика",
        InstallPhaseId::PreparingJava => "Подготовка Java",
        InstallPhaseId::DownloadingMinecraft => "Загрузка Minecraft",
        InstallPhaseId::RunningLoaderProcessors => "Настройка загрузчика",
        InstallPhaseId::Finalizing => "Завершение",
        InstallPhaseId::RollingBack => "Откат изменений",
    }
}

/// Follows an install job of the core and reports it to the interface as `progress` events.
pub async fn follow_job(app: &AppHandle, event_id: &str, job_id: &str, emit_done: bool) -> R<()> {
    let job_id = uuid::Uuid::parse_str(job_id).map_err(|e| anyhow!("{e}"))?;
    loop {
        let s = theseus::install::get_job(job_id).await.map_err(err)?;
        let percent = s.progress.as_ref().filter(|p| p.total > 0).map(|p| p.current as f64 / p.total as f64 * 100.0).unwrap_or(0.0);
        match s.status {
            InstallJobStatus::Succeeded => {
                if emit_done {
                    net::emit_progress(app, event_id, "Готово", 100.0, 0.0, true, None);
                }
                return Ok(());
            }
            InstallJobStatus::Failed | InstallJobStatus::Interrupted | InstallJobStatus::Canceled => {
                let msg = s.error.as_ref().map(|e| format!("{e:?}")).unwrap_or_else(|| "Установка прервана".to_string());
                net::emit_progress(app, event_id, "Ошибка", 0.0, 0.0, true, Some(msg.clone()));
                return Err(anyhow!(msg).into());
            }
            _ => net::emit_progress(app, event_id, phase_label(s.phase), percent, 0.0, false, None),
        }
        tokio::time::sleep(std::time::Duration::from_millis(400)).await;
    }
}

pub async fn create_instance(app: &AppHandle, req: &crate::NewInstance) -> R<UiInstance> {
    let loader = match req.loader.as_str() {
        "fabric" => ModLoader::Fabric,
        "quilt" => ModLoader::Quilt,
        "forge" => ModLoader::Forge,
        "neoforge" => ModLoader::NeoForge,
        _ => ModLoader::Vanilla,
    };
    let lv = if req.loader_version.trim().is_empty() { None } else { Some(req.loader_version.clone()) };
    let job: InstallJobSnapshot = theseus::install::create_instance(req.name.clone(), req.mc_version.clone(), loader, lv, None, None, InstanceLink::Unmanaged).await.map_err(err)?;
    // the instance id is known as soon as the core has prepared the instance
    let job_uuid = uuid::Uuid::parse_str(&job.job_id).map_err(|e| anyhow!("{e}"))?;
    let mut id = job.instance_id.clone();
    for _ in 0..150 {
        if id.is_some() {
            break;
        }
        tokio::time::sleep(std::time::Duration::from_millis(200)).await;
        id = theseus::install::get_job(job_uuid).await.map_err(err)?.instance_id;
    }
    let id = id.ok_or_else(|| anyhow!("Сборка не создалась"))?;
    // keep following the install in the background: the interface shows it as `install:<id>`
    let app2 = app.clone();
    let ev = format!("install:{id}");
    let jid = job.job_id.clone();
    let (mc, loader, with_mods) = (req.mc_version.clone(), req.loader.clone(), req.hud_enabled);
    let id2 = id.clone();
    tauri::async_runtime::spawn(async move {
        // the interface is told "done" only after the mod set is in place
        if follow_job(&app2, &ev, &jid, false).await.is_ok() {
            // first-run game settings by hardware tier (spec 3.6), written once, the player's changes are never touched later
            if crate::settings::load().apply_preset {
                let _ = crate::preset_for(&id2);
            }
            // memory by the table of spec 3.6 (8 GB -> 3, 16 -> 4, 32+ -> 6; never more than half of the RAM)
            let mut lo = InstanceLaunchOverridesPatch::default();
            lo.memory = Some(Some(MemorySettings { maximum: crate::smart_ram() }));
            let mut patch = EditInstance::default();
            patch.launch_overrides = Some(lo);
            let _ = theseus::instance::edit(&id2, patch).await;
            if with_mods {
                crate::core_mods::install_set(&app2, &id2, &mc, &loader).await;
            } else {
                net::emit_progress(&app2, &ev, "Готово", 100.0, 0.0, true, None);
            }
        }
    });
    get_instance(&id).await
}

pub async fn delete_instance(id: &str) -> R<()> {
    theseus::instance::remove(id).await.map_err(err)
}

pub async fn running() -> R<Vec<String>> {
    let mut v: Vec<String> = theseus::process::get_all().await.map_err(err)?.into_iter().map(|p| p.instance_id).collect();
    v.sort();
    v.dedup();
    Ok(v)
}

pub async fn kill(id: &str) -> R<()> {
    theseus::instance::kill(id).await.map_err(err)
}

/// Starts the game (optionally with another account for a second client) and reports start and exit.
pub async fn launch(app: &AppHandle, id: &str, account_id: Option<String>) -> R<()> {
    let pid = format!("launch:{id}");
    net::emit_progress(app, &pid, "Запуск игры", 50.0, 0.0, false, None);
    let server = server_of(id);
    let quick = if server.is_empty() {
        theseus::instance::QuickPlayType::None
    } else {
        theseus::instance::QuickPlayType::Server(theseus::server_address::ServerAddress::Unresolved(server))
    };
    let res = match account_id.as_deref().and_then(|a| uuid::Uuid::parse_str(a).ok()) {
        Some(acc) => theseus::instance::run_with_account(id, acc, quick).await,
        None => theseus::instance::run(id, quick).await,
    };
    let meta = match res {
        Ok(m) => m,
        Err(e) => {
            let msg = auth_message(&e.to_string());
            net::emit_progress(app, &pid, "Ошибка", 0.0, 0.0, true, Some(msg.clone()));
            return Err(anyhow!(msg).into());
        }
    };
    net::emit_progress(app, &pid, "Игра запущена", 100.0, 0.0, true, None);
    let _ = app.emit("game-started", id.to_string());
    let app2 = app.clone();
    let id2 = id.to_string();
    let started = std::time::SystemTime::now();
    tauri::async_runtime::spawn(async move {
        let _ = theseus::process::wait_for(meta.uuid).await;
        let still = running().await.map(|v| v.contains(&id2)).unwrap_or(false);
        if !still {
            let hint = crate::core_misc::analyze_crash(&id2, started).await;
            let code = if hint.is_some() { 1 } else { 0 };
            let _ = app2.emit("game-exit", serde_json::json!({ "instanceId": id2, "code": code, "hint": hint }));
            // the sleeping launcher wakes up: window shown and WebView2 resumed
            if let Some(w) = app2.get_webview_window("main") {
                #[cfg(windows)]
                crate::winapi::webview_suspend(&w, false);
                let _ = w.show();
            }
        }
    });
    Ok(())
}

/// Saves what the settings tab can change: name, memory, Java, arguments, window, server to join.
pub async fn update_instance(i: &UiInstance) -> R<()> {
    set_server(&i.id, &i.server);
    let mut lo = InstanceLaunchOverridesPatch::default();
    lo.java_path = Some(if i.java_path.trim().is_empty() { None } else { Some(i.java_path.trim().to_string()) });
    lo.extra_launch_args = Some(if i.jvm_args.trim().is_empty() { None } else { Some(i.jvm_args.split_whitespace().map(String::from).collect()) });
    lo.memory = Some(if i.ram_mb > 0 { Some(MemorySettings { maximum: i.ram_mb }) } else { None });
    lo.force_fullscreen = Some(Some(i.fullscreen));
    lo.game_resolution = Some(if i.width > 0 && i.height > 0 { Some(WindowSize(i.width as u16, i.height as u16)) } else { None });
    let mut patch = EditInstance::default();
    patch.name = Some(i.name.clone());
    patch.launch_overrides = Some(lo);
    theseus::instance::edit(&i.id, patch).await.map_err(err)?;
    Ok(())
}

/// Waits until the install of an instance has finished (the interface awaits this before it offers Play).
pub async fn wait_installed(id: &str) -> R<()> {
    for _ in 0..4500 {
        let m = theseus::instance::get(id).await.map_err(err)?.ok_or_else(|| anyhow!("Сборка не найдена"))?;
        if format!("{:?}", m.instance.install_stage) == "Installed" {
            return Ok(());
        }
        tokio::time::sleep(std::time::Duration::from_millis(500)).await;
    }
    Err(anyhow!("Установка не завершилась").into())
}
