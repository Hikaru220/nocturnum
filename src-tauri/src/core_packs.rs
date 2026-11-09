//! NOCTURNUM: modpacks (.mrpack import and export, install from Modrinth), duplicate and repair through the core.

use crate::core::err;
use crate::core_instances::{follow_job, get_instance};
use crate::instances::Instance as UiInstance;
use crate::{error::R, net};
use anyhow::anyhow;
use tauri::AppHandle;
use theseus::pack::install_from::CreatePackLocation;

/// Waits until the core has created the instance of a job, then follows the install in the background.
async fn instance_of_job(app: &AppHandle, event_id: &str, job: theseus::install::InstallJobSnapshot) -> R<UiInstance> {
    let job_uuid = uuid::Uuid::parse_str(&job.job_id).map_err(|e| anyhow!("{e}"))?;
    let mut id = job.instance_id.clone();
    for _ in 0..300 {
        if id.is_some() {
            break;
        }
        tokio::time::sleep(std::time::Duration::from_millis(200)).await;
        id = theseus::install::get_job(job_uuid).await.map_err(err)?.instance_id;
    }
    let id = id.ok_or_else(|| anyhow!("Сборка не создалась"))?;
    let (app2, ev, jid) = (app.clone(), event_id.to_string(), job.job_id.clone());
    tauri::async_runtime::spawn(async move {
        let _ = follow_job(&app2, &ev, &jid, true).await;
    });
    get_instance(&id).await
}

pub async fn import_mrpack(app: &AppHandle, path: &str) -> R<UiInstance> {
    let job = theseus::install::create_modpack_instance(CreatePackLocation::FromFile { path: path.into() }, None).await.map_err(err)?;
    instance_of_job(app, "modpack:import", job).await
}

pub async fn install_modpack(app: &AppHandle, project_id: &str, version_id: Option<String>) -> R<UiInstance> {
    let project = crate::modrinth::project(project_id).await?;
    let version_id = match version_id {
        Some(v) => v,
        None => crate::modrinth::project_versions(project_id, &[], &[])
            .await?
            .into_iter()
            .next()
            .and_then(|v| v["id"].as_str().map(String::from))
            .ok_or_else(|| anyhow!("У модпака нет версий"))?,
    };
    let loc = CreatePackLocation::FromVersionId {
        project_id: project["id"].as_str().unwrap_or(project_id).to_string(),
        version_id,
        title: project["title"].as_str().unwrap_or("Модпак").to_string(),
        icon_url: project["icon_url"].as_str().map(String::from),
    };
    let job = theseus::install::create_modpack_instance(loc, None).await.map_err(err)?;
    instance_of_job(app, &format!("modpack:{project_id}"), job).await
}

pub async fn export_mrpack(id: &str, dest: &str) -> R<()> {
    let cands = theseus::instance::get_pack_export_candidates(id).await.map_err(err)?;
    let included: Vec<String> = cands.iter().map(|c| c.path.to_string()).collect();
    theseus::instance::export_mrpack(id, dest.into(), included, vec![], None, None, None).await.map_err(err)
}

pub async fn duplicate_instance(app: &AppHandle, id: &str) -> R<UiInstance> {
    let job = theseus::install::duplicate_instance(id.to_string()).await.map_err(err)?;
    instance_of_job(app, &format!("install:{id}"), job).await
}

/// Reinstalls the game files of an instance (the core verifies and re-downloads what is missing).
pub async fn repair_instance(app: &AppHandle, id: &str) -> R<()> {
    let job = theseus::install::install_existing_instance(id.to_string(), true).await.map_err(err)?;
    net::emit_progress(app, &format!("install:{id}"), "Проверка файлов", 0.0, 0.0, false, None);
    follow_job(app, &format!("install:{id}"), &job.job_id, true).await
}
