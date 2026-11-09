//! Modrinth catalogue (API v2): search, project info, install with dependencies, update by hash,
//! .mrpack import/export and CurseForge zip import.

use crate::{
    error::R,
    instances::{self, Instance},
    net::{self, Hash, CLIENT},
    paths,
};
use anyhow::{anyhow, Context};
use serde::Serialize;
use serde_json::{json, Value};
use std::collections::{HashMap, HashSet};
use std::io::Read;
use std::path::{Path, PathBuf};
use tauri::AppHandle;

const API: &str = "https://api.modrinth.com/v2";

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchParams {
    pub query: String,
    pub project_type: String,
    pub game_version: Option<String>,
    pub loader: Option<String>,
    pub category: Option<String>,
    pub sort: String,
    pub offset: u32,
    pub limit: u32,
}

pub fn build_facets(project_type: &str, game_version: &Option<String>, loader: &Option<String>, category: &Option<String>) -> String {
    let mut f: Vec<Vec<String>> = vec![vec![format!("project_type:{project_type}")]];
    if let Some(v) = game_version.as_ref().filter(|v| !v.is_empty()) {
        f.push(vec![format!("versions:{v}")]);
    }
    if let Some(l) = loader.as_ref().filter(|v| !v.is_empty() && project_type != "resourcepack" && project_type != "shader") {
        f.push(vec![format!("categories:{l}")]);
    }
    if let Some(c) = category.as_ref().filter(|v| !v.is_empty()) {
        f.push(vec![format!("categories:{c}")]);
    }
    serde_json::to_string(&f).unwrap()
}

pub async fn search(p: &SearchParams) -> R<Value> {
    let facets = build_facets(&p.project_type, &p.game_version, &p.loader, &p.category);
    let url = format!(
        "{API}/search?query={}&facets={}&index={}&offset={}&limit={}",
        urlencoding::encode(&p.query),
        urlencoding::encode(&facets),
        urlencoding::encode(&p.sort),
        p.offset,
        p.limit
    );
    net::get_json(&url).await
}

pub async fn project(id: &str) -> R<Value> {
    net::get_json(&format!("{API}/project/{}", urlencoding::encode(id))).await
}

pub async fn project_versions(id: &str, loaders: &[String], game_versions: &[String]) -> R<Vec<Value>> {
    let mut url = format!("{API}/project/{}/version", urlencoding::encode(id));
    let mut q: Vec<String> = vec![];
    if !loaders.is_empty() {
        q.push(format!("loaders={}", urlencoding::encode(&serde_json::to_string(loaders)?)));
    }
    if !game_versions.is_empty() {
        q.push(format!("game_versions={}", urlencoding::encode(&serde_json::to_string(game_versions)?)));
    }
    if !q.is_empty() {
        url.push('?');
        url.push_str(&q.join("&"));
    }
    net::get_json(&url).await
}


pub async fn projects(ids: &[String]) -> R<Vec<Value>> {
    if ids.is_empty() {
        return Ok(vec![]);
    }
    net::get_json(&format!("{API}/projects?ids={}", urlencoding::encode(&serde_json::to_string(ids)?))).await
}





#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateInfo {
    pub file_name: String,
    pub project_id: String,
    pub title: String,
    pub new_version_id: String,
    pub new_version_number: String,
}




// ---------- modpacks ----------

fn protected(rel: &str) -> bool {
    let l = rel.to_lowercase();
    l == "options.txt" || l == "optionsof.txt" || l == "optionsshaders.txt" || l == "servers.dat" || l.starts_with("config/") || l.starts_with("saves/")
}

fn extract_overrides(zip: &mut zip::ZipArchive<std::fs::File>, prefix: &str, game_dir: &Path, keep_existing_protected: bool) -> R<()> {
    for i in 0..zip.len() {
        let mut e = zip.by_index(i)?;
        let name = e.name().replace('\\', "/");
        let Some(rel) = name.strip_prefix(prefix) else { continue };
        if rel.is_empty() || e.is_dir() {
            continue;
        }
        if rel.split('/').any(|p| p == "..") || rel.starts_with('/') || rel.contains(':') {
            continue; // path traversal guard
        }
        let out = game_dir.join(rel);
        if keep_existing_protected && protected(rel) && out.exists() {
            continue;
        }
        if let Some(p) = out.parent() {
            std::fs::create_dir_all(p)?;
        }
        let mut f = std::fs::File::create(out)?;
        std::io::copy(&mut e, &mut f)?;
    }
    Ok(())
}





// ---------- CurseForge zip ----------

pub struct CfMeta {
    pub name: String,
    pub mc: String,
    pub loader: String,
    pub loader_version: String,
    pub file_ids: Vec<u64>,
    pub overrides: String,
}

pub fn read_cf_meta(path: &Path) -> R<CfMeta> {
    let f = std::fs::File::open(path)?;
    let mut z = zip::ZipArchive::new(f)?;
    let mut s = String::new();
    z.by_name("manifest.json").map_err(|_| anyhow!("Это не сборка CurseForge: нет manifest.json"))?.read_to_string(&mut s)?;
    let m: Value = serde_json::from_str(&s)?;
    let mc = m["minecraft"]["version"].as_str().ok_or_else(|| anyhow!("нет версии игры"))?.to_string();
    let loader_id = m["minecraft"]["modLoaders"]
        .as_array()
        .and_then(|a| a.iter().find(|l| l["primary"].as_bool().unwrap_or(false)).or_else(|| a.first()))
        .and_then(|l| l["id"].as_str())
        .unwrap_or("")
        .to_string();
    let (loader, lv) = match loader_id.split_once('-') {
        Some((l, v)) => (l.to_string(), v.to_string()),
        None => ("vanilla".to_string(), String::new()),
    };
    Ok(CfMeta {
        name: m["name"].as_str().unwrap_or("Сборка CurseForge").to_string(),
        mc,
        loader,
        loader_version: lv,
        file_ids: m["files"].as_array().map(|a| a.iter().filter_map(|f| f["fileID"].as_u64()).collect()).unwrap_or_default(),
        overrides: m["overrides"].as_str().unwrap_or("overrides").to_string(),
    })
}

/// Returns the number of files that could not be downloaded (no API key).
pub async fn apply_cf(app: &AppHandle, pid: &str, inst: &Instance, path: &Path, meta: &CfMeta, threads: usize) -> R<usize> {
    let game_dir = instances::game_dir(&inst.id);
    std::fs::create_dir_all(&game_dir)?;
    {
        let f = std::fs::File::open(path)?;
        let mut z = zip::ZipArchive::new(f)?;
        extract_overrides(&mut z, &format!("{}/", meta.overrides), &game_dir, false)?;
    }
    let key = std::env::var("CURSEFORGE_API_KEY").unwrap_or_default();
    if key.is_empty() || meta.file_ids.is_empty() {
        return Ok(meta.file_ids.len());
    }
    let r = CLIENT
        .post("https://api.curseforge.com/v1/mods/files")
        .header("x-api-key", &key)
        .json(&json!({"fileIds": meta.file_ids}))
        .send()
        .await
        .context("CurseForge API")?;
    let v: Value = r.json().await?;
    let mut jobs = Vec::new();
    let mut missing = 0usize;
    for f in v["data"].as_array().cloned().unwrap_or_default() {
        match (f["downloadUrl"].as_str(), f["fileName"].as_str()) {
            (Some(url), Some(name)) if !name.contains('/') && !name.contains("..") => {
                jobs.push(net::Job { url: url.into(), dest: game_dir.join("mods").join(name), hash: Hash::None, size: f["fileLength"].as_u64().unwrap_or(0) });
            }
            _ => missing += 1,
        }
    }
    net::download_many(app, pid, "Загрузка модов CurseForge", jobs, threads).await?;
    let _ = paths::root();
    Ok(missing)
}

static ICONS: once_cell::sync::Lazy<std::sync::Mutex<std::collections::HashMap<String, String>>> = once_cell::sync::Lazy::new(|| std::sync::Mutex::new(std::collections::HashMap::new()));

/// Project icons for a list of Modrinth project ids: one batched request, cached for the session.
pub async fn icons(ids: Vec<String>) -> R<std::collections::HashMap<String, String>> {
    let missing: Vec<String> = {
        let c = ICONS.lock().unwrap();
        ids.iter().filter(|i| !i.is_empty() && !c.contains_key(*i)).cloned().collect()
    };
    if !missing.is_empty() {
        let url = format!("{API}/projects?ids={}", urlencoding::encode(&serde_json::to_string(&missing)?));
        let list: Vec<Value> = net::get_json(&url).await?;
        let mut c = ICONS.lock().unwrap();
        for p in &list {
            if let (Some(id), Some(u)) = (p["id"].as_str(), p["icon_url"].as_str()) {
                c.insert(id.to_string(), u.to_string());
            }
        }
        for m in missing {
            c.entry(m).or_default();
        }
    }
    let c = ICONS.lock().unwrap();
    Ok(ids.into_iter().filter_map(|i| c.get(&i).filter(|u| !u.is_empty()).map(|u| (i.clone(), u.clone()))).collect())
}
