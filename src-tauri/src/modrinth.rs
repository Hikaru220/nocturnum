//! Modrinth catalogue (API v2): search, project info, install with dependencies, update by hash,
//! .mrpack import/export and CurseForge zip import.

use crate::{
    error::R,
    instances::{self, Instance, InstalledMeta},
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

pub async fn version(id: &str) -> R<Value> {
    net::get_json(&format!("{API}/version/{id}")).await
}

pub async fn projects(ids: &[String]) -> R<Vec<Value>> {
    if ids.is_empty() {
        return Ok(vec![]);
    }
    net::get_json(&format!("{API}/projects?ids={}", urlencoding::encode(&serde_json::to_string(ids)?))).await
}

fn loaders_for(project_type: &str, loader: &str) -> Vec<String> {
    match project_type {
        "mod" => {
            let mut v = vec![loader.to_string()];
            // Quilt runs Fabric mods
            if loader == "quilt" {
                v.push("fabric".into());
            }
            v
        }
        "modpack" => vec![],
        _ => vec![],
    }
}

fn pick_file(v: &Value) -> Option<&Value> {
    let files = v["files"].as_array()?;
    files.iter().find(|f| f["primary"].as_bool().unwrap_or(false)).or_else(|| files.first())
}

fn kind_dir(project_type: &str) -> &'static str {
    match project_type {
        "resourcepack" => "resourcepacks",
        "shader" => "shaderpacks",
        _ => "mods",
    }
}

/// Install project into instance with required dependencies. Returns installed file names.
pub async fn install_project(
    app: &AppHandle,
    pid: &str,
    inst: &Instance,
    project_id: &str,
    version_id: Option<String>,
    project_type: &str,
) -> R<Vec<String>> {
    let mut installed: Vec<String> = Vec::new();
    let mut seen: HashSet<String> = HashSet::new();
    let mut idx = instances::load_index(&inst.id);
    // existing project ids (avoid reinstalling a dep)
    let existing: HashSet<String> = idx.values().map(|m| m.project_id.clone()).collect();

    let mut queue: Vec<(String, Option<String>, String)> = vec![(project_id.to_string(), version_id, project_type.to_string())];
    while let Some((proj, ver, ptype)) = queue.pop() {
        if !seen.insert(proj.clone()) {
            continue;
        }
        if proj != project_id && existing.contains(&proj) {
            continue;
        }
        net::emit_progress(app, pid, "Установка…", 30.0, 0.0, false, None);
        let version: Value = match ver {
            Some(v) => self::version(&v).await?,
            None => {
                let loaders = loaders_for(&ptype, &inst.loader);
                let versions = project_versions(&proj, &loaders, &[inst.mc_version.clone()]).await?;
                versions
                    .into_iter()
                    .next()
                    .ok_or_else(|| anyhow!("Нет версии, подходящей под {} / {}", inst.mc_version, inst.loader))?
            }
        };
        let file = pick_file(&version).ok_or_else(|| anyhow!("У версии нет файлов"))?;
        let filename = file["filename"].as_str().ok_or_else(|| anyhow!("bad file"))?.to_string();
        if filename.contains('/') || filename.contains('\\') || filename.contains("..") {
            return Err(anyhow!("Небезопасное имя файла в каталоге").into());
        }
        let dir = instances::game_dir(&inst.id).join(kind_dir(&ptype));
        let dest = dir.join(&filename);
        let sha1 = file["hashes"]["sha1"].as_str().unwrap_or("").to_string();
        let hash = file["hashes"]["sha512"].as_str().map(|s| Hash::Sha512(s.into())).unwrap_or(Hash::None);
        // remove old versions of the same project
        for (name, meta) in idx.clone() {
            if meta.project_id == proj && name != filename {
                let _ = std::fs::remove_file(dir.join(&name));
                let _ = std::fs::remove_file(dir.join(format!("{name}.disabled")));
                idx.remove(&name);
            }
        }
        net::download_file(file["url"].as_str().ok_or_else(|| anyhow!("no url"))?, &dest, &hash, None).await?;
        let title = projects(&[proj.clone()]).await.ok().and_then(|p| p.first().and_then(|x| x["title"].as_str().map(String::from))).unwrap_or_default();
        idx.insert(
            filename.clone(),
            InstalledMeta { project_id: proj.clone(), version_id: version["id"].as_str().unwrap_or("").into(), title, sha1 },
        );
        installed.push(filename);

        if ptype == "mod" {
            for d in version["dependencies"].as_array().cloned().unwrap_or_default() {
                if d["dependency_type"] == "required" {
                    if let Some(pid2) = d["project_id"].as_str() {
                        queue.push((pid2.to_string(), d["version_id"].as_str().map(String::from), "mod".into()));
                    }
                }
            }
        }
    }
    instances::save_index(&inst.id, &idx)?;
    net::emit_progress(app, pid, "Готово", 100.0, 0.0, true, None);
    Ok(installed)
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

fn hash_all(inst: &Instance, kinds: &[&str]) -> Vec<(String, String, String)> {
    // (kind, file_name, sha1)
    let mut out = Vec::new();
    for k in kinds {
        if let Ok(list) = instances::list_content(&inst.id, k) {
            for f in list {
                let p = instances::game_dir(&inst.id).join(k).join(&f.file_name);
                if p.is_file() {
                    if let Ok(h) = net::file_sha1(&p) {
                        out.push((k.to_string(), f.file_name, h));
                    }
                }
            }
        }
    }
    out
}

pub async fn check_updates(inst: &Instance) -> R<Vec<UpdateInfo>> {
    let files = hash_all(inst, &["mods"]);
    if files.is_empty() {
        return Ok(vec![]);
    }
    let hashes: Vec<&String> = files.iter().map(|f| &f.2).collect();
    let body = json!({
        "hashes": hashes, "algorithm": "sha1",
        "loaders": loaders_for("mod", &inst.loader), "game_versions": [inst.mc_version]
    });
    let r = CLIENT.post(format!("{API}/version_files/update")).json(&body).send().await?;
    if !r.status().is_success() {
        return Err(anyhow!("Modrinth: HTTP {}", r.status()).into());
    }
    let map: HashMap<String, Value> = r.json().await?;
    let mut out = Vec::new();
    for (_k, file_name, sha1) in files {
        if let Some(v) = map.get(&sha1) {
            let has_same = v["files"].as_array().map(|fs| fs.iter().any(|f| f["hashes"]["sha1"].as_str() == Some(&sha1))).unwrap_or(false);
            if !has_same {
                out.push(UpdateInfo {
                    file_name,
                    project_id: v["project_id"].as_str().unwrap_or("").into(),
                    title: String::new(),
                    new_version_id: v["id"].as_str().unwrap_or("").into(),
                    new_version_number: v["version_number"].as_str().unwrap_or("").into(),
                });
            }
        }
    }
    let ids: Vec<String> = out.iter().map(|u| u.project_id.clone()).collect();
    if let Ok(ps) = projects(&ids).await {
        for u in out.iter_mut() {
            if let Some(p) = ps.iter().find(|p| p["id"] == u.project_id) {
                u.title = p["title"].as_str().unwrap_or("").to_string();
            }
        }
    }
    Ok(out)
}

pub async fn update_all(app: &AppHandle, pid: &str, inst: &Instance) -> R<usize> {
    let ups = check_updates(inst).await?;
    let n = ups.len();
    for (i, u) in ups.iter().enumerate() {
        net::emit_progress(app, pid, &format!("Обновление {}", u.title), (i as f64 / n.max(1) as f64) * 100.0, 0.0, false, None);
        // keep disabled state
        let was_disabled = !instances::list_content(&inst.id, "mods")?.iter().any(|f| f.file_name == u.file_name && f.enabled)
            && instances::list_content(&inst.id, "mods")?.iter().any(|f| f.file_name == u.file_name);
        let new_files = install_project(app, pid, inst, &u.project_id, Some(u.new_version_id.clone()), "mod").await?;
        if was_disabled {
            for nf in new_files {
                let _ = instances::toggle_content(&inst.id, "mods", &nf, false);
            }
        }
    }
    net::emit_progress(app, pid, "Готово", 100.0, 0.0, true, None);
    Ok(n)
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

pub struct MrpackMeta {
    pub name: String,
    pub mc: String,
    pub loader: String,
    pub loader_version: String,
}

pub fn read_mrpack_meta(path: &Path) -> R<(MrpackMeta, Value)> {
    let f = std::fs::File::open(path)?;
    let mut z = zip::ZipArchive::new(f)?;
    let mut s = String::new();
    z.by_name("modrinth.index.json")
        .map_err(|_| anyhow!("Это не .mrpack: нет modrinth.index.json"))?
        .read_to_string(&mut s)?;
    let idx: Value = serde_json::from_str(&s)?;
    let deps = &idx["dependencies"];
    let mc = deps["minecraft"].as_str().ok_or_else(|| anyhow!("В модпаке не указана версия игры"))?.to_string();
    let (loader, lv) = if let Some(v) = deps["fabric-loader"].as_str() {
        ("fabric", v)
    } else if let Some(v) = deps["quilt-loader"].as_str() {
        ("quilt", v)
    } else if let Some(v) = deps["neoforge"].as_str() {
        ("neoforge", v)
    } else if let Some(v) = deps["forge"].as_str() {
        ("forge", v)
    } else {
        ("vanilla", "")
    };
    Ok((
        MrpackMeta { name: idx["name"].as_str().unwrap_or("Модпак").to_string(), mc, loader: loader.into(), loader_version: lv.into() },
        idx,
    ))
}

/// Download the files of a .mrpack and unpack overrides into the instance.
pub async fn apply_mrpack(app: &AppHandle, pid: &str, inst: &Instance, path: &Path, update: bool, threads: usize) -> R<()> {
    let (_meta, idx) = read_mrpack_meta(path)?;
    let game_dir = instances::game_dir(&inst.id);
    std::fs::create_dir_all(&game_dir)?;
    let mut jobs = Vec::new();
    let mut index = instances::load_index(&inst.id);
    for f in idx["files"].as_array().cloned().unwrap_or_default() {
        if f["env"]["client"] == "unsupported" {
            continue;
        }
        let rel = f["path"].as_str().unwrap_or("").replace('\\', "/");
        if rel.is_empty() || rel.split('/').any(|p| p == "..") || rel.starts_with('/') || rel.contains(':') {
            return Err(anyhow!("Небезопасный путь в модпаке: {rel}").into());
        }
        let url = f["downloads"][0].as_str().ok_or_else(|| anyhow!("У файла {rel} нет ссылки"))?.to_string();
        let dest = game_dir.join(&rel);
        if update && protected(&rel) && dest.exists() {
            continue;
        }
        let sha1 = f["hashes"]["sha1"].as_str().unwrap_or("").to_string();
        let hash = f["hashes"]["sha512"].as_str().map(|s| Hash::Sha512(s.into())).unwrap_or_else(|| {
            if sha1.is_empty() { Hash::None } else { Hash::Sha1(sha1.clone()) }
        });
        if let Some(name) = rel.strip_prefix("mods/") {
            index.insert(name.to_string(), InstalledMeta { sha1: sha1.clone(), ..Default::default() });
        }
        jobs.push(net::Job { url, dest, hash, size: f["fileSize"].as_u64().unwrap_or(0) });
    }
    net::download_many(app, pid, "Загрузка модпака", jobs, threads).await?;
    let f = std::fs::File::open(path)?;
    let mut z = zip::ZipArchive::new(f)?;
    extract_overrides(&mut z, "overrides/", &game_dir, update)?;
    extract_overrides(&mut z, "client-overrides/", &game_dir, update)?;
    instances::save_index(&inst.id, &index)?;
    Ok(())
}

pub async fn export_mrpack(inst: &Instance, dest: &Path) -> R<()> {
    use std::io::Write;
    let game_dir = instances::game_dir(&inst.id);
    let files = hash_all(inst, &["mods", "resourcepacks", "shaderpacks"]);
    let hashes: Vec<&String> = files.iter().map(|f| &f.2).collect();
    let found: HashMap<String, Value> = if hashes.is_empty() {
        HashMap::new()
    } else {
        let r = CLIENT.post(format!("{API}/version_files")).json(&json!({"hashes": hashes, "algorithm": "sha1"})).send().await?;
        if r.status().is_success() { r.json().await? } else { HashMap::new() }
    };
    let mut index_files = Vec::new();
    let mut in_index: HashSet<PathBuf> = HashSet::new();
    for (kind, name, sha1) in &files {
        if name.ends_with(".disabled") {
            continue;
        }
        let Some(v) = found.get(sha1) else { continue };
        let Some(vf) = v["files"].as_array().and_then(|fs| fs.iter().find(|f| f["hashes"]["sha1"].as_str() == Some(sha1))) else { continue };
        let rel = format!("{kind}/{name}");
        index_files.push(json!({
            "path": rel,
            "hashes": {"sha1": vf["hashes"]["sha1"], "sha512": vf["hashes"]["sha512"]},
            "env": {"client": "required", "server": "required"},
            "downloads": [vf["url"]],
            "fileSize": vf["size"]
        }));
        in_index.insert(game_dir.join(kind).join(name));
    }
    let mut deps = serde_json::Map::new();
    deps.insert("minecraft".into(), json!(inst.mc_version));
    match inst.loader.as_str() {
        "fabric" => { deps.insert("fabric-loader".into(), json!(inst.loader_version)); }
        "quilt" => { deps.insert("quilt-loader".into(), json!(inst.loader_version)); }
        "neoforge" => { deps.insert("neoforge".into(), json!(inst.loader_version)); }
        "forge" => { deps.insert("forge".into(), json!(inst.loader_version)); }
        _ => {}
    }
    let index = json!({
        "formatVersion": 1, "game": "minecraft", "versionId": "1.0.0", "name": inst.name,
        "files": index_files, "dependencies": deps
    });
    let out = std::fs::File::create(dest)?;
    let mut zw = zip::ZipWriter::new(out);
    let opts = zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated);
    zw.start_file("modrinth.index.json", opts)?;
    zw.write_all(&serde_json::to_vec_pretty(&index)?)?;
    // overrides: configs, options and files not on Modrinth
    let mut stack: Vec<PathBuf> = vec![];
    for sub in ["config", "mods", "resourcepacks", "shaderpacks"] {
        stack.push(game_dir.join(sub));
    }
    while let Some(d) = stack.pop() {
        let Ok(rd) = std::fs::read_dir(&d) else { continue };
        for e in rd.flatten() {
            let p = e.path();
            if p.is_dir() {
                stack.push(p);
            } else if !in_index.contains(&p) && !p.to_string_lossy().ends_with(".disabled") {
                let rel = p.strip_prefix(&game_dir)?.to_string_lossy().replace('\\', "/");
                zw.start_file(format!("overrides/{rel}"), opts)?;
                zw.write_all(&std::fs::read(&p)?)?;
            }
        }
    }
    for f in ["options.txt", "servers.dat"] {
        let p = game_dir.join(f);
        if p.exists() {
            zw.start_file(format!("overrides/{f}"), opts)?;
            zw.write_all(&std::fs::read(&p)?)?;
        }
    }
    zw.finish()?;
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
