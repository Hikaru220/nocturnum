//! Mod loader installation: Fabric, Quilt (profile JSON), NeoForge, Forge (headless installer).

use crate::{
    error::R,
    net,
};
use anyhow::anyhow;
use serde::{Deserialize, Serialize};
use serde_json::Value;

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
