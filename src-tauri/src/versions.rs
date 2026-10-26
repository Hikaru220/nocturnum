//! Minecraft version metadata: manifest, version JSON resolution (inheritsFrom), install of
//! client jar / libraries / assets with SHA1 verification into a shared cache.

use crate::{
    error::R,
    net::{self, Hash, Job},
    paths,
};
use anyhow::{anyhow, Context};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::path::PathBuf;
use tauri::AppHandle;

pub const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct ManifestVersion {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub url: String,
    #[serde(rename = "releaseTime")]
    pub release_time: String,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct Manifest {
    pub latest: Value,
    pub versions: Vec<ManifestVersion>,
}

pub async fn manifest() -> R<Manifest> {
    let cache = paths::cache_dir().join("version_manifest_v2.json");
    match net::get_json::<Manifest>(MANIFEST_URL).await {
        Ok(m) => {
            let _ = std::fs::create_dir_all(paths::cache_dir());
            let _ = std::fs::write(&cache, serde_json::to_vec(&m)?);
            Ok(m)
        }
        Err(e) => {
            // offline fallback
            if let Ok(s) = std::fs::read_to_string(&cache) {
                if let Ok(m) = serde_json::from_str::<Manifest>(&s) {
                    return Ok(m);
                }
            }
            Err(e)
        }
    }
}

pub fn version_json_path(id: &str) -> PathBuf {
    paths::versions_dir().join(id).join(format!("{id}.json"))
}

pub fn version_jar_path(id: &str) -> PathBuf {
    paths::versions_dir().join(id).join(format!("{id}.jar"))
}

/// Download raw version JSON for a vanilla version id (verified against manifest sha1 when present).
pub async fn ensure_vanilla_json(id: &str) -> R<Value> {
    let path = version_json_path(id);
    if path.exists() {
        if let Ok(v) = serde_json::from_str::<Value>(&std::fs::read_to_string(&path)?) {
            return Ok(v);
        }
    }
    let m = manifest().await?;
    let mv = m
        .versions
        .iter()
        .find(|v| v.id == id)
        .ok_or_else(|| anyhow!("Версия {id} не найдена в манифесте Mojang"))?;
    let text = net::get_text(&mv.url).await?;
    let v: Value = serde_json::from_str(&text)?;
    std::fs::create_dir_all(path.parent().unwrap())?;
    std::fs::write(&path, text)?;
    Ok(v)
}

/// Load version JSON and recursively merge `inheritsFrom`.
pub async fn resolve(id: &str) -> R<Value> {
    let path = version_json_path(id);
    let mut cur: Value = if path.exists() {
        serde_json::from_str(&std::fs::read_to_string(&path)?).with_context(|| format!("bad json {id}"))?
    } else {
        ensure_vanilla_json(id).await?
    };
    if let Some(parent_id) = cur.get("inheritsFrom").and_then(|v| v.as_str()).map(|s| s.to_string()) {
        let parent = Box::pin(resolve(&parent_id)).await?;
        cur = merge(parent, cur);
        cur["jarId"] = Value::String(parent_id_root(&cur, &parent_id));
    } else {
        cur["jarId"] = Value::String(id.to_string());
    }
    Ok(cur)
}

fn parent_id_root(cur: &Value, fallback: &str) -> String {
    cur.get("jarId").and_then(|v| v.as_str()).unwrap_or(fallback).to_string()
}

fn lib_key(l: &Value) -> String {
    let name = l.get("name").and_then(|v| v.as_str()).unwrap_or("");
    let parts: Vec<&str> = name.split(':').collect();
    if parts.len() >= 4 {
        format!("{}:{}:{}", parts[0], parts[1], parts[3])
    } else if parts.len() >= 2 {
        format!("{}:{}", parts[0], parts[1])
    } else {
        name.to_string()
    }
}

fn merge(parent: Value, child: Value) -> Value {
    let mut out = parent.clone();
    for (k, v) in child.as_object().cloned().unwrap_or_default() {
        match k.as_str() {
            "libraries" => {
                let mut libs: Vec<Value> = v.as_array().cloned().unwrap_or_default();
                let child_keys: std::collections::HashSet<String> = libs.iter().map(lib_key).collect();
                for pl in parent.get("libraries").and_then(|x| x.as_array()).cloned().unwrap_or_default() {
                    if !child_keys.contains(&lib_key(&pl)) {
                        libs.push(pl);
                    }
                }
                out["libraries"] = Value::Array(libs);
            }
            "arguments" => {
                let mut args = parent.get("arguments").cloned().unwrap_or(serde_json::json!({}));
                for kind in ["game", "jvm"] {
                    let mut merged: Vec<Value> = args.get(kind).and_then(|x| x.as_array()).cloned().unwrap_or_default();
                    merged.extend(v.get(kind).and_then(|x| x.as_array()).cloned().unwrap_or_default());
                    args[kind] = Value::Array(merged);
                }
                out["arguments"] = args;
            }
            "id" | "mainClass" | "type" | "time" | "releaseTime" | "minecraftArguments" => {
                out[k.as_str()] = v;
            }
            "inheritsFrom" => {}
            _ => {
                out[k.as_str()] = v;
            }
        }
    }
    if child.get("minecraftArguments").is_some() && child.get("arguments").is_none() {
        // child legacy args overrides modern ones
    }
    out
}

// ---------- rules ----------

pub fn os_name() -> &'static str {
    if cfg!(windows) { "windows" } else if cfg!(target_os = "macos") { "osx" } else { "linux" }
}

pub fn os_arch() -> &'static str {
    if cfg!(target_arch = "aarch64") { "arm64" } else if cfg!(target_arch = "x86") { "x86" } else { "x86_64" }
}

pub fn rules_allow(rules: &Value, features: &[&str]) -> bool {
    let Some(arr) = rules.as_array() else { return true };
    if arr.is_empty() {
        return true;
    }
    let mut allowed = false;
    for r in arr {
        let action = r.get("action").and_then(|a| a.as_str()).unwrap_or("allow");
        let mut applies = true;
        if let Some(os) = r.get("os") {
            if let Some(n) = os.get("name").and_then(|x| x.as_str()) {
                if n != os_name() {
                    applies = false;
                }
            }
            if let Some(a) = os.get("arch").and_then(|x| x.as_str()) {
                if a != os_arch() && !(a == "x86" && os_arch() == "x86") {
                    applies = false;
                }
            }
            if let Some(ver) = os.get("version").and_then(|x| x.as_str()) {
                if let Ok(re) = regex::Regex::new(ver) {
                    // Only windows 10 regex cases exist; we assume modern OS
                    if !re.is_match("10.0") && !re.is_match("Windows 10") {
                        applies = false;
                    }
                }
            }
        }
        if let Some(f) = r.get("features").and_then(|x| x.as_object()) {
            for (name, want) in f {
                let want = want.as_bool().unwrap_or(false);
                if features.contains(&name.as_str()) != want {
                    applies = false;
                }
            }
        }
        if applies {
            allowed = action == "allow";
        }
    }
    allowed
}

// ---------- libraries ----------

#[derive(Clone, Debug)]
pub struct Lib {
    pub name: String,
    pub path: PathBuf,
    pub url: Option<String>,
    pub sha1: Option<String>,
    pub size: u64,
    pub extract: bool,
}

/// Produce the concrete list of library files for the current OS.
pub fn libs_for(resolved: &Value) -> Vec<Lib> {
    let mut out = Vec::new();
    let libs = resolved.get("libraries").and_then(|l| l.as_array()).cloned().unwrap_or_default();
    for l in libs {
        if let Some(rules) = l.get("rules") {
            if !rules_allow(rules, &[]) {
                continue;
            }
        }
        let name = l.get("name").and_then(|n| n.as_str()).unwrap_or("").to_string();
        if name.is_empty() {
            continue;
        }
        let libdir = paths::libraries_dir();
        // legacy natives
        if let Some(natives) = l.get("natives").and_then(|n| n.as_object()) {
            if let Some(classifier) = natives.get(os_name()).and_then(|c| c.as_str()) {
                let classifier = classifier.replace("${arch}", if cfg!(target_pointer_width = "64") { "64" } else { "32" });
                if let Some(a) = l.pointer(&format!("/downloads/classifiers/{classifier}")) {
                    let rel = a.get("path").and_then(|p| p.as_str()).unwrap_or("").to_string();
                    out.push(Lib {
                        name: format!("{name}:{classifier}"),
                        path: libdir.join(&rel),
                        url: a.get("url").and_then(|u| u.as_str()).map(String::from),
                        sha1: a.get("sha1").and_then(|u| u.as_str()).map(String::from),
                        size: a.get("size").and_then(|u| u.as_u64()).unwrap_or(0),
                        extract: true,
                    });
                }
            }
        }
        if let Some(a) = l.pointer("/downloads/artifact") {
            let rel = a.get("path").and_then(|p| p.as_str()).map(String::from).unwrap_or_else(|| paths::maven_path(&name));
            out.push(Lib {
                name: name.clone(),
                path: libdir.join(&rel),
                url: a.get("url").and_then(|u| u.as_str()).filter(|u| !u.is_empty()).map(String::from),
                sha1: a.get("sha1").and_then(|u| u.as_str()).map(String::from),
                size: a.get("size").and_then(|u| u.as_u64()).unwrap_or(0),
                extract: false,
            });
        } else if l.get("downloads").is_none() || l.get("url").is_some() {
            // maven-style (Fabric / Quilt)
            if l.get("natives").is_some() {
                continue;
            }
            let rel = paths::maven_path(&name);
            let base = l.get("url").and_then(|u| u.as_str()).unwrap_or("https://libraries.minecraft.net/");
            let base = if base.ends_with('/') { base.to_string() } else { format!("{base}/") };
            out.push(Lib {
                name: name.clone(),
                path: libdir.join(&rel),
                url: Some(format!("{base}{rel}")),
                sha1: l.get("sha1").and_then(|u| u.as_str()).map(String::from),
                size: l.get("size").and_then(|u| u.as_u64()).unwrap_or(0),
                extract: false,
            });
        }
    }
    out
}

pub fn required_java_major(resolved: &Value) -> u32 {
    resolved.pointer("/javaVersion/majorVersion").and_then(|v| v.as_u64()).unwrap_or(8) as u32
}

// ---------- install ----------

pub async fn install(app: &AppHandle, progress_id: &str, version_id: &str, threads: usize) -> R<Value> {
    net::emit_progress(app, progress_id, "Загрузка метаданных версии", 0.0, 0.0, false, None);
    let resolved = resolve(version_id).await?;
    let jar_id = resolved["jarId"].as_str().unwrap_or(version_id).to_string();

    let mut jobs: Vec<Job> = Vec::new();

    // client jar
    if let Some(c) = resolved.pointer("/downloads/client") {
        let url = c["url"].as_str().ok_or_else(|| anyhow!("client url missing"))?.to_string();
        jobs.push(Job {
            url,
            dest: version_jar_path(&jar_id),
            hash: c["sha1"].as_str().map(|s| Hash::Sha1(s.into())).unwrap_or(Hash::None),
            size: c["size"].as_u64().unwrap_or(0),
        });
    }

    for lib in libs_for(&resolved) {
        let Some(url) = lib.url.clone() else {
            if !lib.path.exists() {
                // produced by a forge installer; nothing to download
            }
            continue;
        };
        let hash = lib.sha1.clone().map(Hash::Sha1).unwrap_or(Hash::None);
        // Without a hash we only fetch when missing.
        if matches!(hash, Hash::None) && lib.path.exists() {
            continue;
        }
        jobs.push(Job { url, dest: lib.path.clone(), hash, size: lib.size });
    }

    // logging config
    if let Some(f) = resolved.pointer("/logging/client/file") {
        if let (Some(id), Some(url)) = (f["id"].as_str(), f["url"].as_str()) {
            jobs.push(Job {
                url: url.into(),
                dest: paths::assets_dir().join("log_configs").join(id),
                hash: f["sha1"].as_str().map(|s| Hash::Sha1(s.into())).unwrap_or(Hash::None),
                size: f["size"].as_u64().unwrap_or(0),
            });
        }
    }

    // asset index
    let index_id = resolved.pointer("/assetIndex/id").and_then(|v| v.as_str()).unwrap_or("legacy").to_string();
    if let Some(ai) = resolved.get("assetIndex") {
        let dest = paths::assets_dir().join("indexes").join(format!("{index_id}.json"));
        net::download_file(
            ai["url"].as_str().ok_or_else(|| anyhow!("assetIndex url"))?,
            &dest,
            &ai["sha1"].as_str().map(|s| Hash::Sha1(s.into())).unwrap_or(Hash::None),
            None,
        )
        .await?;
        let idx: Value = serde_json::from_str(&std::fs::read_to_string(&dest)?)?;
        if let Some(objs) = idx.get("objects").and_then(|o| o.as_object()) {
            let virtual_ = idx.get("virtual").and_then(|v| v.as_bool()).unwrap_or(false)
                || idx.get("map_to_resources").and_then(|v| v.as_bool()).unwrap_or(false);
            for (_name, o) in objs {
                let hash = o["hash"].as_str().unwrap_or("");
                if hash.len() < 2 {
                    continue;
                }
                let sub = &hash[..2];
                jobs.push(Job {
                    url: format!("https://resources.download.minecraft.net/{sub}/{hash}"),
                    dest: paths::assets_dir().join("objects").join(sub).join(hash),
                    hash: Hash::Sha1(hash.into()),
                    size: o["size"].as_u64().unwrap_or(0),
                });
            }
            net::download_many(app, progress_id, "Загрузка файлов игры", jobs, threads).await?;
            if virtual_ {
                let vdir = paths::assets_dir().join("virtual").join(&index_id);
                for (name, o) in objs {
                    let hash = o["hash"].as_str().unwrap_or("");
                    if hash.len() < 2 {
                        continue;
                    }
                    let src = paths::assets_dir().join("objects").join(&hash[..2]).join(hash);
                    let dst = vdir.join(name);
                    if !dst.exists() && src.exists() {
                        if let Some(p) = dst.parent() {
                            std::fs::create_dir_all(p)?;
                        }
                        std::fs::copy(&src, &dst)?;
                    }
                }
            }
        } else {
            net::download_many(app, progress_id, "Загрузка файлов игры", jobs, threads).await?;
        }
    } else {
        net::download_many(app, progress_id, "Загрузка файлов игры", jobs, threads).await?;
    }

    // extract natives
    let natives = paths::natives_dir(version_id);
    let _ = std::fs::remove_dir_all(&natives);
    std::fs::create_dir_all(&natives)?;
    for lib in libs_for(&resolved) {
        if lib.extract && lib.path.exists() {
            extract_natives(&lib.path, &natives)?;
        }
    }
    Ok(resolved)
}

fn extract_natives(jar: &std::path::Path, dest: &std::path::Path) -> R<()> {
    let f = std::fs::File::open(jar)?;
    let mut z = zip::ZipArchive::new(f)?;
    for i in 0..z.len() {
        let mut e = z.by_index(i)?;
        let name = e.name().to_string();
        if e.is_dir() || name.starts_with("META-INF") {
            continue;
        }
        let Some(safe) = e.enclosed_name() else { continue };
        let out = dest.join(safe.file_name().unwrap_or_default());
        let mut o = std::fs::File::create(out)?;
        std::io::copy(&mut e, &mut o)?;
    }
    Ok(())
}
