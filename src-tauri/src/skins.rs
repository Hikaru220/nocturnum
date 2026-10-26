//! Skin helpers: fetch by nickname, local library, upload.

use crate::{error::R, net::CLIENT, paths};
use anyhow::anyhow;
use base64::Engine;
use serde::Serialize;
use serde_json::Value;

/// Fetch skin PNG by player name via Mojang API. Returns (png bytes, slim).
pub async fn fetch_by_name(name: &str) -> R<(Vec<u8>, bool)> {
    let re = regex::Regex::new(r"^[A-Za-z0-9_]{1,16}$").unwrap();
    if !re.is_match(name) {
        return Err(anyhow!("Неверный ник").into());
    }
    let r = CLIENT.get(format!("https://api.mojang.com/users/profiles/minecraft/{name}")).send().await?;
    if r.status().as_u16() == 404 || r.status().as_u16() == 204 {
        return Err(anyhow!("Игрок «{name}» не найден").into());
    }
    let v: Value = r.error_for_status()?.json().await?;
    let id = v["id"].as_str().ok_or_else(|| anyhow!("нет id"))?;
    fetch_by_uuid(id).await
}

pub async fn fetch_by_uuid(uuid: &str) -> R<(Vec<u8>, bool)> {
    let r = CLIENT
        .get(format!("https://sessionserver.mojang.com/session/minecraft/profile/{}", uuid.replace('-', "")))
        .send()
        .await?
        .error_for_status()?;
    let v: Value = r.json().await?;
    let prop = v["properties"]
        .as_array()
        .and_then(|a| a.iter().find(|p| p["name"] == "textures"))
        .and_then(|p| p["value"].as_str())
        .ok_or_else(|| anyhow!("У игрока нет текстур"))?;
    let decoded = base64::engine::general_purpose::STANDARD.decode(prop)?;
    let t: Value = serde_json::from_slice(&decoded)?;
    let skin = &t["textures"]["SKIN"];
    let url = skin["url"].as_str().ok_or_else(|| anyhow!("Скин не задан"))?;
    if !url.starts_with("http://textures.minecraft.net/") && !url.starts_with("https://textures.minecraft.net/") {
        return Err(anyhow!("Неожиданный адрес текстуры").into());
    }
    let slim = skin["metadata"]["model"] == "slim";
    let bytes = fetch_png(url).await?;
    Ok((bytes, slim))
}

pub async fn fetch_png(url: &str) -> R<Vec<u8>> {
    if !url.starts_with("http://textures.minecraft.net/") && !url.starts_with("https://textures.minecraft.net/") {
        return Err(anyhow!("Недопустимый адрес скина").into());
    }
    Ok(CLIENT.get(url).send().await?.error_for_status()?.bytes().await?.to_vec())
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SavedSkin {
    pub name: String,
    pub slim: bool,
    pub data_url: String,
}

fn safe(name: &str) -> String {
    name.chars().map(|c| if c.is_alphanumeric() || c == '-' || c == '_' || c == ' ' { c } else { '_' }).collect()
}

pub fn save_to_library(name: &str, png: &[u8], slim: bool) -> R<()> {
    check_png(png)?;
    std::fs::create_dir_all(paths::skins_dir())?;
    let n = safe(name);
    let file = paths::skins_dir().join(format!("{n}{}.png", if slim { ".slim" } else { "" }));
    // keep only one variant per name
    let _ = std::fs::remove_file(paths::skins_dir().join(format!("{n}.png")));
    let _ = std::fs::remove_file(paths::skins_dir().join(format!("{n}.slim.png")));
    std::fs::write(file, png)?;
    Ok(())
}

pub fn list_library() -> Vec<SavedSkin> {
    let mut out = Vec::new();
    if let Ok(rd) = std::fs::read_dir(paths::skins_dir()) {
        for e in rd.flatten() {
            let fname = e.file_name().to_string_lossy().to_string();
            if !fname.ends_with(".png") {
                continue;
            }
            let stem = fname.trim_end_matches(".png");
            let (name, slim) = match stem.strip_suffix(".slim") {
                Some(n) => (n.to_string(), true),
                None => (stem.to_string(), false),
            };
            if let Ok(bytes) = std::fs::read(e.path()) {
                out.push(SavedSkin {
                    name,
                    slim,
                    data_url: format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(bytes)),
                });
            }
        }
    }
    out.sort_by(|a, b| a.name.cmp(&b.name));
    out
}

pub fn delete_from_library(name: &str) -> R<()> {
    let n = safe(name);
    let _ = std::fs::remove_file(paths::skins_dir().join(format!("{n}.png")));
    let _ = std::fs::remove_file(paths::skins_dir().join(format!("{n}.slim.png")));
    Ok(())
}

/// Validate PNG signature + dimensions (64x64 or legacy 64x32).
pub fn check_png(b: &[u8]) -> R<()> {
    if b.len() < 24 || &b[0..8] != b"\x89PNG\r\n\x1a\n" {
        return Err(anyhow!("Это не PNG").into());
    }
    let w = u32::from_be_bytes([b[16], b[17], b[18], b[19]]);
    let h = u32::from_be_bytes([b[20], b[21], b[22], b[23]]);
    if !(w == 64 && (h == 64 || h == 32)) {
        return Err(anyhow!("Скин должен быть 64×64 (получилось {w}×{h})").into());
    }
    Ok(())
}
