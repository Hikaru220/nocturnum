//! Accounts: Microsoft (device code → Xbox Live → XSTS → Minecraft Services) and offline profiles.
//! Refresh tokens live in the OS credential store, never in plain JSON.

use crate::{error::R, net::CLIENT, paths};
use anyhow::{anyhow, Context};
use md5::{Digest, Md5};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::sync::atomic::{AtomicBool, Ordering};
use tauri::{AppHandle, Emitter};


const SCOPE: &str = "XboxLive.signin offline_access";

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(default, rename_all = "camelCase")]
pub struct Account {
    pub id: String,
    /// microsoft | offline
    pub kind: String,
    pub username: String,
    pub uuid: String,
    pub skin_url: String,
}

#[derive(Serialize, Deserialize, Default)]
struct Store {
    accounts: Vec<Account>,
}

fn load_store() -> Store {
    std::fs::read_to_string(paths::accounts_file())
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

fn save_store(s: &Store) -> R<()> {
    std::fs::create_dir_all(paths::root())?;
    std::fs::write(paths::accounts_file(), serde_json::to_vec_pretty(s)?)?;
    Ok(())
}






pub fn offline_uuid(name: &str) -> String {
    let mut h = Md5::new();
    h.update(format!("OfflinePlayer:{name}").as_bytes());
    let mut b: [u8; 16] = h.finalize().into();
    b[6] = (b[6] & 0x0f) | 0x30;
    b[8] = (b[8] & 0x3f) | 0x80;
    let s = hex::encode(b);
    format!("{}-{}-{}-{}-{}", &s[0..8], &s[8..12], &s[12..16], &s[16..20], &s[20..32])
}

pub fn add_offline(name: &str, allow_unlicensed: bool) -> R<Account> {
    let name = name.trim();
    let re = regex::Regex::new(r"^[A-Za-z0-9_]{3,16}$").unwrap();
    if !re.is_match(name) {
        return Err(anyhow!("Ник: 3–16 символов, латиница, цифры и _").into());
    }
    let mut s = load_store();
    if !allow_unlicensed && !s.accounts.iter().any(|a| a.kind == "microsoft") {
        return Err(anyhow!("Офлайн-профили доступны только при наличии лицензионного аккаунта Microsoft").into());
    }
    let acc = Account {
        id: uuid::Uuid::new_v4().to_string(),
        kind: "offline".into(),
        username: name.into(),
        uuid: offline_uuid(name),
        skin_url: String::new(),
    };
    s.accounts.push(acc.clone());
    save_store(&s)?;
    Ok(acc)
}











#[derive(Serialize)]
pub struct Check {
    pub ok: bool,
    pub message: String,
}

/// Asks Microsoft whether this client id exists and allows the device-code flow (no sign-in happens).
pub async fn check_client_id(client_id: &str) -> Check {
    let id = client_id.trim();
    let re = regex::Regex::new(r"^[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$").unwrap();
    if !re.is_match(id) {
        return Check { ok: false, message: "ID должен выглядеть как 00000000-0000-0000-0000-000000000000 (Application (client) ID из Azure).".into() };
    }
    let resp = CLIENT
        .post("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode")
        .form(&[("client_id", id), ("scope", SCOPE)])
        .send()
        .await;
    let resp = match resp {
        Ok(r) => r,
        Err(e) => return Check { ok: false, message: format!("Нет связи с Microsoft: {e}") },
    };
    let status = resp.status();
    let body: Value = resp.json().await.unwrap_or(Value::Null);
    if status.is_success() && body["device_code"].is_string() {
        return Check { ok: true, message: "Azure-приложение найдено, вход по коду включён. Осталось одобрение Mojang (см. docs/MICROSOFT_AUTH.md).".into() };
    }
    let desc = body["error_description"].as_str().unwrap_or("").to_string();
    let hint = if desc.contains("AADSTS700016") {
        "Приложение с таким ID не найдено. Проверьте ID и что регистрация создана для личных аккаунтов Microsoft."
    } else if desc.contains("AADSTS70002") || desc.contains("AADSTS7000218") || body["error"] == "invalid_client" || body["error"] == "unauthorized_client" {
        "В Azure включите Authentication → Allow public client flows = Yes."
    } else if desc.contains("AADSTS70011") {
        "Приложению не разрешён нужный scope (XboxLive.signin)."
    } else {
        "Microsoft отклонил запрос."
    };
    Check { ok: false, message: format!("{hint} ({})", desc.lines().next().unwrap_or("").chars().take(140).collect::<String>()) }
}
