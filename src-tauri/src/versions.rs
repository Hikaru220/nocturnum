//! Minecraft version manifest (the core installs the game itself).

use crate::{
    error::R,
    net,
    paths,
};
use serde::{Deserialize, Serialize};
use serde_json::Value;

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

pub fn os_name() -> &'static str {
    if cfg!(windows) { "windows" } else if cfg!(target_os = "macos") { "osx" } else { "linux" }
}
