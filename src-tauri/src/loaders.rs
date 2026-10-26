//! Mod loader installation: Fabric, Quilt (profile JSON), NeoForge, Forge (headless installer).

use crate::{
    error::R,
    java, net, paths,
    versions::{self},
};
use anyhow::anyhow;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashSet;
use std::process::Command;
use tauri::AppHandle;

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct LoaderVersion {
    pub version: String,
    pub stable: bool,
}

pub async fn list_loader_versions(loader: &str, mc: &str) -> R<Vec<LoaderVersion>> {
    match loader {
        "fabric" | "quilt" => {
            let base = if loader == "fabric" { "https://meta.fabricmc.net/v2" } else { "https://meta.quiltmc.org/v3" };
            let v: Value = net::get_json(&format!("{base}/versions/loader/{mc}")).await?;
            let mut out = Vec::new();
            for e in v.as_array().cloned().unwrap_or_default() {
                let ver = e.pointer("/loader/version").and_then(|x| x.as_str()).unwrap_or("").to_string();
                let stable = e.pointer("/loader/stable").and_then(|x| x.as_bool()).unwrap_or(!ver.contains("beta"));
                if !ver.is_empty() {
                    out.push(LoaderVersion { version: ver, stable });
                }
            }
            Ok(out)
        }
        "neoforge" => {
            let v: Value = net::get_json("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge").await?;
            let prefix = neoforge_prefix(mc).ok_or_else(|| anyhow!("NeoForge не поддерживает {mc}"))?;
            let mut out: Vec<LoaderVersion> = v["versions"]
                .as_array()
                .cloned()
                .unwrap_or_default()
                .into_iter()
                .filter_map(|x| x.as_str().map(String::from))
                .filter(|s| s.starts_with(&prefix))
                .map(|s| LoaderVersion { stable: !s.contains("beta") && !s.contains("alpha"), version: s })
                .collect();
            out.reverse();
            Ok(out)
        }
        "forge" => {
            let v: Value = net::get_json("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json").await?;
            let promos = v["promos"].as_object().cloned().unwrap_or_default();
            let mut out = Vec::new();
            for kind in ["recommended", "latest"] {
                if let Some(ver) = promos.get(&format!("{mc}-{kind}")).and_then(|x| x.as_str()) {
                    if !out.iter().any(|l: &LoaderVersion| l.version == ver) {
                        out.push(LoaderVersion { version: ver.into(), stable: kind == "recommended" });
                    }
                }
            }
            if out.is_empty() {
                return Err(anyhow!("Forge не поддерживает {mc}").into());
            }
            Ok(out)
        }
        _ => Ok(vec![]),
    }
}

/// "1.21.4" -> "21.4." ; "1.21" -> "21.0."
fn neoforge_prefix(mc: &str) -> Option<String> {
    let parts: Vec<&str> = mc.split('.').collect();
    if parts.len() < 2 || parts[0] != "1" {
        // new scheme (26.x): neoforge versions follow the same string
        if parts.len() >= 2 {
            return Some(format!("{}.{}.", parts[0], parts[1]));
        }
        return None;
    }
    let minor = parts[1];
    let patch = parts.get(2).copied().unwrap_or("0");
    Some(format!("{minor}.{patch}."))
}

/// Install the loader and return the resulting launch version id.
pub async fn install(app: &AppHandle, progress_id: &str, loader: &str, mc: &str, loader_version: &str, threads: usize) -> R<String> {
    // vanilla json first (inheritsFrom base + java version)
    let vanilla = versions::ensure_vanilla_json(mc).await?;
    match loader {
        "vanilla" => Ok(mc.to_string()),
        "fabric" | "quilt" => {
            let base = if loader == "fabric" { "https://meta.fabricmc.net/v2" } else { "https://meta.quiltmc.org/v3" };
            net::emit_progress(app, progress_id, "Установка загрузчика", 0.0, 0.0, false, None);
            let url = format!("{base}/versions/loader/{mc}/{loader_version}/profile/json");
            let text = net::get_text(&url).await.map_err(|e| e.ctx(&format!("профиль {loader} {loader_version} для {mc}")))?;
            let v: Value = serde_json::from_str(&text)?;
            let id = v["id"].as_str().ok_or_else(|| anyhow!("profile without id"))?.to_string();
            let p = versions::version_json_path(&id);
            std::fs::create_dir_all(p.parent().unwrap())?;
            std::fs::write(&p, text)?;
            Ok(id)
        }
        "neoforge" | "forge" => {
            let (url, jar_name) = if loader == "neoforge" {
                (
                    format!("https://maven.neoforged.net/releases/net/neoforged/neoforge/{v}/neoforge-{v}-installer.jar", v = loader_version),
                    format!("neoforge-{loader_version}-installer.jar"),
                )
            } else {
                (
                    format!(
                        "https://maven.minecraftforge.net/net/minecraftforge/forge/{mc}-{v}/forge-{mc}-{v}-installer.jar",
                        v = loader_version
                    ),
                    format!("forge-{mc}-{loader_version}-installer.jar"),
                )
            };
            let installer = paths::cache_dir().join(&jar_name);
            net::emit_progress(app, progress_id, "Загрузка установщика", 0.0, 0.0, false, None);
            net::download_file(&url, &installer, &net::Hash::None, None).await?;

            // the installer needs vanilla fully present in the target dir
            versions::install(app, progress_id, mc, threads).await?;

            let meta = paths::root().join("meta");
            std::fs::create_dir_all(&meta)?;
            let profiles = meta.join("launcher_profiles.json");
            if !profiles.exists() {
                std::fs::write(&profiles, r#"{"profiles":{},"settings":{},"version":3}"#)?;
            }
            let before: HashSet<String> = list_version_dirs();
            let major = versions::required_java_major(&vanilla);
            let java = java::ensure(app, progress_id, major, "").await?;
            net::emit_progress(app, progress_id, "Запуск установщика загрузчика", 50.0, 0.0, false, None);
            let java2 = java.clone();
            let installer2 = installer.clone();
            let meta2 = meta.clone();
            let out = tokio::task::spawn_blocking(move || {
                let mut cmd = Command::new(&java2);
                cmd.arg("-jar").arg(&installer2).arg("--installClient").arg(&meta2);
                #[cfg(windows)]
                {
                    use std::os::windows::process::CommandExt;
                    cmd.creation_flags(0x08000000);
                }
                cmd.output()
            })
            .await??;
            if !out.status.success() {
                let tail: String = String::from_utf8_lossy(&out.stdout)
                    .lines()
                    .chain(String::from_utf8_lossy(&out.stderr).lines())
                    .rev()
                    .take(8)
                    .collect::<Vec<_>>()
                    .into_iter()
                    .rev()
                    .collect::<Vec<_>>()
                    .join("\n");
                return Err(anyhow!("Установщик {loader} завершился с ошибкой:\n{tail}").into());
            }
            let after = list_version_dirs();
            let want_prefix = if loader == "neoforge" { "neoforge" } else { mc };
            let new_id = after
                .iter()
                .filter(|d| !before.contains(*d))
                .find(|d| d.contains(loader_version) || d.starts_with(want_prefix))
                .cloned()
                .or_else(|| after.iter().find(|d| d.to_lowercase().contains(loader) && d.contains(loader_version)).cloned())
                .ok_or_else(|| anyhow!("Установщик не создал профиль версии"))?;
            Ok(new_id)
        }
        other => Err(anyhow!("Неизвестный загрузчик: {other}").into()),
    }
}

fn list_version_dirs() -> HashSet<String> {
    let mut s = HashSet::new();
    if let Ok(rd) = std::fs::read_dir(paths::versions_dir()) {
        for e in rd.flatten() {
            if e.path().is_dir() {
                s.insert(e.file_name().to_string_lossy().to_string());
            }
        }
    }
    s
}
