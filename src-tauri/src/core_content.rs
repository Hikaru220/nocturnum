//! NOCTURNUM: mods, resource packs and shaders of an instance through the Modrinth App core.
//! Same data shapes as before (`instances::ContentFile`), the work is done by `theseus`.

use crate::core::err;
use crate::instances::ContentFile as UiFile;
use crate::{error::R, net};
use anyhow::anyhow;
use tauri::AppHandle;
use theseus::instance::InstallProjectWithDependenciesRequest;
use theseus::prelude::*;

fn project_type(kind: &str) -> R<ProjectType> {
    Ok(match kind {
        "mods" => ProjectType::Mod,
        "resourcepacks" => ProjectType::ResourcePack,
        "shaderpacks" => ProjectType::ShaderPack,
        _ => return Err(anyhow!("Неизвестный вид содержимого").into()),
    })
}

fn content_type(kind: &str) -> ContentType {
    match kind {
        "resourcepack" | "resourcepacks" => ContentType::ResourcePack,
        "shader" | "shaderpacks" => ContentType::Shader,
        "modpack" => ContentType::ModPack,
        _ => ContentType::Mod,
    }
}

/// Files of one kind with the key the core uses for them.
async fn files(id: &str, kind: &str) -> R<Vec<(String, ContentFile)>> {
    let ty = project_type(kind)?;
    let map = theseus::instance::get_projects(id, None).await.map_err(err)?;
    let mut v: Vec<(String, ContentFile)> = map.into_iter().filter(|(_, f)| f.project_type == ty).collect();
    v.sort_by(|a, b| a.1.file_name.to_lowercase().cmp(&b.1.file_name.to_lowercase()));
    Ok(v)
}

async fn key_of(id: &str, kind: &str, file: &str) -> R<String> {
    files(id, kind)
        .await?
        .into_iter()
        .find(|(_, f)| f.file_name == file || f.file_name == format!("{file}.disabled") || f.file_name.trim_end_matches(".disabled") == file.trim_end_matches(".disabled"))
        .map(|(k, _)| k)
        .ok_or_else(|| anyhow!("Файл не найден").into())
}

pub async fn list_content(id: &str, kind: &str) -> R<Vec<UiFile>> {
    let list = files(id, kind).await?;
    let ids: Vec<String> = list.iter().filter_map(|(_, f)| f.metadata.as_ref().map(|m| m.project_id.clone())).collect::<std::collections::HashSet<_>>().into_iter().collect();
    let titles: std::collections::HashMap<String, String> = crate::modrinth::projects(&ids)
        .await
        .unwrap_or_default()
        .into_iter()
        .filter_map(|p| Some((p["id"].as_str()?.to_string(), p["title"].as_str()?.to_string())))
        .collect();
    Ok(list
        .into_iter()
        .map(|(_, f)| {
            let (pid, vid) = f.metadata.as_ref().map(|m| (m.project_id.clone(), m.version_id.clone())).unwrap_or_default();
            UiFile {
                file_name: f.file_name.clone(),
                enabled: f.enabled,
                size: f.size,
                sha1: f.hash.clone(),
                title: titles.get(&pid).cloned().unwrap_or_default(),
                project_id: pid,
                version_id: vid,
            }
        })
        .collect())
}

pub async fn toggle_content(id: &str, kind: &str, file: &str, enable: bool) -> R<()> {
    let key = key_of(id, kind, file).await?;
    theseus::instance::toggle_disable_project(id, &key, Some(enable)).await.map_err(err)?;
    Ok(())
}

pub async fn remove_content(id: &str, kind: &str, file: &str) -> R<()> {
    let key = key_of(id, kind, file).await?;
    theseus::instance::remove_project(id, &key).await.map_err(err)
}

pub async fn add_content_file(id: &str, kind: &str, path: &str) -> R<String> {
    let ty = project_type(kind)?;
    theseus::instance::add_project_from_path(id, std::path::Path::new(path), Some(ty)).await.map_err(err)
}

/// Installs a project from Modrinth with the dependencies the core resolves, then reports `mod:<project>` as done.
pub async fn install_project(app: &AppHandle, id: &str, project_id: &str, version_id: Option<String>, kind: &str) -> R<Vec<String>> {
    let pid = format!("mod:{project_id}");
    net::emit_progress(app, &pid, "Установка…", 30.0, 0.0, false, None);
    let req = InstallProjectWithDependenciesRequest { project_id: project_id.to_string(), version_id, content_type: content_type(kind), selected: Default::default() };
    if let Err(e) = theseus::instance::install_project_with_dependencies(id, req).await {
        let msg = e.to_string();
        net::emit_progress(app, &pid, "Ошибка", 0.0, 0.0, true, Some(msg.clone()));
        return Err(anyhow!(msg).into());
    }
    // the core installs in the background: wait until the project shows up in the instance
    for _ in 0..600 {
        if theseus::instance::get_installed_project_ids(id).await.map(|v| v.iter().any(|p| p == project_id)).unwrap_or(false) {
            net::emit_progress(app, &pid, "Готово", 100.0, 0.0, true, None);
            return Ok(vec![]);
        }
        tokio::time::sleep(std::time::Duration::from_millis(500)).await;
    }
    net::emit_progress(app, &pid, "Ошибка", 0.0, 0.0, true, Some("Установка не завершилась".into()));
    Err(anyhow!("Установка не завершилась").into())
}

/// Mods that have a newer version (the core compares file hashes with Modrinth).
pub async fn check_updates(id: &str) -> R<Vec<crate::modrinth::UpdateInfo>> {
    theseus::instance::refresh_content_updates(id).await.map_err(err)?;
    let list = files(id, "mods").await?;
    let ids: Vec<String> = list.iter().filter(|(_, f)| f.update_version_id.is_some()).filter_map(|(_, f)| f.metadata.as_ref().map(|m| m.project_id.clone())).collect();
    let titles: std::collections::HashMap<String, String> = crate::modrinth::projects(&ids)
        .await
        .unwrap_or_default()
        .into_iter()
        .filter_map(|p| Some((p["id"].as_str()?.to_string(), p["title"].as_str()?.to_string())))
        .collect();
    Ok(list
        .into_iter()
        .filter_map(|(_, f)| {
            let nv = f.update_version_id.clone()?;
            let pid = f.metadata.as_ref().map(|m| m.project_id.clone()).unwrap_or_default();
            Some(crate::modrinth::UpdateInfo { file_name: f.file_name.clone(), title: titles.get(&pid).cloned().unwrap_or_default(), project_id: pid, new_version_id: nv, new_version_number: String::new() })
        })
        .collect())
}

/// Updates every mod that has a newer version in one install job of the core.
pub async fn update_all(app: &AppHandle, id: &str) -> R<usize> {
    let list = files(id, "mods").await?;
    let updates: Vec<theseus::install::ContentUpdateSelection> = list
        .into_iter()
        .filter_map(|(key, f)| Some(theseus::install::ContentUpdateSelection { project_path: key, version_id: f.update_version_id? }))
        .collect();
    let n = updates.len();
    if n == 0 {
        return Ok(0);
    }
    let job = theseus::install::bulk_update_content(id.to_string(), updates).await.map_err(err)?;
    crate::core_instances::follow_job(app, &format!("update:{id}"), &job.job_id, true).await?;
    Ok(n)
}
