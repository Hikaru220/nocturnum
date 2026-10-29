//! Accounts: Microsoft (device code → Xbox Live → XSTS → Minecraft Services) and offline profiles.
//! Refresh tokens live in the OS credential store, never in plain JSON.

use crate::{error::R, net::CLIENT, paths};
use anyhow::{anyhow, Context};
use md5::{Digest, Md5};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::sync::atomic::{AtomicBool, Ordering};
use tauri::{AppHandle, Emitter};

pub static CANCEL: AtomicBool = AtomicBool::new(false);

const SCOPE: &str = "XboxLive.signin offline_access";
const KEYRING_SERVICE: &str = "nocturnum-launcher";

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

pub fn list() -> Vec<Account> {
    load_store().accounts
}

pub fn get(id: &str) -> R<Account> {
    load_store()
        .accounts
        .into_iter()
        .find(|a| a.id == id)
        .ok_or_else(|| anyhow!("Аккаунт не найден").into())
}

pub fn remove(id: &str) -> R<()> {
    let mut s = load_store();
    s.accounts.retain(|a| a.id != id);
    save_store(&s)?;
    if let Ok(e) = keyring::Entry::new(KEYRING_SERVICE, id) {
        let _ = e.delete_credential();
    }
    Ok(())
}

fn store_secret(id: &str, token: &str) -> R<()> {
    keyring::Entry::new(KEYRING_SERVICE, id)
        .context("системное хранилище недоступно")?
        .set_password(token)
        .context("не удалось сохранить токен в системное хранилище")?;
    Ok(())
}

fn read_secret(id: &str) -> R<String> {
    Ok(keyring::Entry::new(KEYRING_SERVICE, id)
        .context("системное хранилище недоступно")?
        .get_password()
        .map_err(|_| anyhow!("Токен не найден — войдите в аккаунт заново"))?)
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

pub fn add_offline(name: &str) -> R<Account> {
    let name = name.trim();
    let re = regex::Regex::new(r"^[A-Za-z0-9_]{3,16}$").unwrap();
    if !re.is_match(name) {
        return Err(anyhow!("Ник: 3–16 символов, латиница, цифры и _").into());
    }
    let mut s = load_store();
    if !s.accounts.iter().any(|a| a.kind == "microsoft") {
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

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct DeviceCode {
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in: u64,
}

pub struct Session {
    pub access_token: String,
    pub username: String,
    pub uuid: String,
    pub user_type: String,
}

async fn post_json(url: &str, body: &Value) -> R<Value> {
    let r = CLIENT.post(url).json(body).header("Accept", "application/json").send().await?;
    let st = r.status();
    let text = r.text().await?;
    if !st.is_success() {
        return Err(anyhow!("{url}: HTTP {st}: {}", text.chars().take(300).collect::<String>()).into());
    }
    Ok(serde_json::from_str(&text)?)
}

async fn ms_token(client_id: &str, form: &[(&str, &str)]) -> R<Value> {
    let r = CLIENT.post("https://login.microsoftonline.com/consumers/oauth2/v2.0/token").form(form).send().await?;
    let _ = client_id;
    Ok(r.json::<Value>().await?)
}

/// MS access token -> Minecraft access token + profile.
async fn minecraft_login(ms_access: &str) -> R<(String, Value)> {
    let xbl = post_json(
        "https://user.auth.xboxlive.com/user/authenticate",
        &json!({
            "Properties": {"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": format!("d={ms_access}")},
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        }),
    )
    .await
    .map_err(|e| e.ctx("Xbox Live"))?;
    let xbl_token = xbl["Token"].as_str().ok_or_else(|| anyhow!("XBL: нет токена"))?.to_string();
    let xsts_resp = CLIENT
        .post("https://xsts.auth.xboxlive.com/xsts/authorize")
        .json(&json!({
            "Properties": {"SandboxId": "RETAIL", "UserTokens": [xbl_token]},
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        }))
        .send()
        .await?;
    let st = xsts_resp.status();
    let xsts: Value = xsts_resp.json().await.unwrap_or(Value::Null);
    if !st.is_success() {
        let code = xsts["XErr"].as_u64().unwrap_or(0);
        let msg = match code {
            2148916233 => "У этого аккаунта Microsoft нет профиля Xbox. Создайте его на xbox.com.",
            2148916235 => "Xbox Live недоступен в вашей стране.",
            2148916236 | 2148916237 => "Требуется подтверждение возраста (взрослый аккаунт).",
            2148916238 => "Детский аккаунт: добавьте его в семью Microsoft.",
            _ => "Ошибка авторизации Xbox (XSTS).",
        };
        return Err(anyhow!("{msg} (код {code})").into());
    }
    let xsts_token = xsts["Token"].as_str().ok_or_else(|| anyhow!("XSTS: нет токена"))?;
    let uhs = xsts.pointer("/DisplayClaims/xui/0/uhs").and_then(|u| u.as_str()).ok_or_else(|| anyhow!("XSTS: нет uhs"))?;

    let r = CLIENT
        .post("https://api.minecraftservices.com/authentication/login_with_xbox")
        .json(&json!({"identityToken": format!("XBL3.0 x={uhs};{xsts_token}")}))
        .send()
        .await?;
    let st = r.status();
    let text = r.text().await?;
    if !st.is_success() {
        if text.contains("Invalid app registration") {
            return Err(anyhow!(
                "Microsoft-приложение не одобрено Mojang («Invalid app registration»). См. docs/MICROSOFT_AUTH.md."
            )
            .into());
        }
        return Err(anyhow!("Minecraft Services: HTTP {st}: {}", text.chars().take(200).collect::<String>()).into());
    }
    let mc: Value = serde_json::from_str(&text)?;
    let mc_token = mc["access_token"].as_str().ok_or_else(|| anyhow!("нет access_token"))?.to_string();

    let pr = CLIENT
        .get("https://api.minecraftservices.com/minecraft/profile")
        .bearer_auth(&mc_token)
        .send()
        .await?;
    if pr.status().as_u16() == 404 {
        return Err(anyhow!("На этом аккаунте Microsoft нет купленной Minecraft: Java Edition").into());
    }
    if !pr.status().is_success() {
        return Err(anyhow!("Не удалось получить профиль: HTTP {}", pr.status()).into());
    }
    let profile: Value = pr.json().await?;
    Ok((mc_token, profile))
}

fn skin_from_profile(p: &Value) -> String {
    p["skins"]
        .as_array()
        .and_then(|a| a.iter().find(|s| s["state"] == "ACTIVE").or_else(|| a.first()))
        .and_then(|s| s["url"].as_str())
        .unwrap_or("")
        .to_string()
}

fn dashed(id: &str) -> String {
    if id.contains('-') || id.len() != 32 {
        return id.to_string();
    }
    format!("{}-{}-{}-{}-{}", &id[0..8], &id[8..12], &id[12..16], &id[16..20], &id[20..32])
}

/// Full interactive Microsoft sign-in. Emits `auth-code` with the code to show to the user.
pub async fn login_microsoft(app: &AppHandle, client_id: &str) -> R<Account> {
    if client_id.trim().is_empty() {
        return Err(anyhow!(
            "Не задан Azure Client ID. Зарегистрируйте приложение и укажите ID в Настройки → Для опытных (см. docs/MICROSOFT_AUTH.md)."
        )
        .into());
    }
    CANCEL.store(false, Ordering::SeqCst);
    let dc = CLIENT
        .post("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode")
        .form(&[("client_id", client_id), ("scope", SCOPE)])
        .send()
        .await?;
    let st = dc.status();
    let dc: Value = dc.json().await?;
    if !st.is_success() {
        return Err(anyhow!("Microsoft: {}", dc["error_description"].as_str().unwrap_or("ошибка запроса кода")).into());
    }
    let device_code = dc["device_code"].as_str().ok_or_else(|| anyhow!("нет device_code"))?.to_string();
    let interval = dc["interval"].as_u64().unwrap_or(5).max(2);
    let expires = dc["expires_in"].as_u64().unwrap_or(900);
    let _ = app.emit(
        "auth-code",
        DeviceCode {
            user_code: dc["user_code"].as_str().unwrap_or("").into(),
            verification_uri: dc["verification_uri"].as_str().unwrap_or("https://microsoft.com/link").into(),
            expires_in: expires,
        },
    );

    let started = std::time::Instant::now();
    let tok = loop {
        if CANCEL.load(Ordering::SeqCst) {
            return Err(anyhow!("Вход отменён").into());
        }
        if started.elapsed().as_secs() > expires {
            return Err(anyhow!("Код истёк, попробуйте ещё раз").into());
        }
        tokio::time::sleep(std::time::Duration::from_secs(interval)).await;
        let r = ms_token(
            client_id,
            &[
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("client_id", client_id),
                ("device_code", &device_code),
            ],
        )
        .await?;
        match r["error"].as_str() {
            None => break r,
            Some("authorization_pending") | Some("slow_down") => continue,
            Some("authorization_declined") => return Err(anyhow!("Вход отклонён").into()),
            Some("expired_token") => return Err(anyhow!("Код истёк, попробуйте ещё раз").into()),
            Some(_) => return Err(anyhow!("Microsoft: {}", r["error_description"].as_str().unwrap_or("ошибка")).into()),
        }
    };
    let ms_access = tok["access_token"].as_str().ok_or_else(|| anyhow!("нет access_token"))?;
    let refresh = tok["refresh_token"].as_str().unwrap_or("");
    let (_mc, profile) = minecraft_login(ms_access).await?;
    let uuid = dashed(profile["id"].as_str().unwrap_or(""));
    let name = profile["name"].as_str().unwrap_or("Player").to_string();

    let mut s = load_store();
    let existing = s.accounts.iter().position(|a| a.kind == "microsoft" && a.uuid == uuid);
    let acc = Account {
        id: existing.map(|i| s.accounts[i].id.clone()).unwrap_or_else(|| uuid::Uuid::new_v4().to_string()),
        kind: "microsoft".into(),
        username: name,
        uuid,
        skin_url: skin_from_profile(&profile),
    };
    store_secret(&acc.id, refresh)?;
    match existing {
        Some(i) => s.accounts[i] = acc.clone(),
        None => s.accounts.push(acc.clone()),
    }
    save_store(&s)?;
    Ok(acc)
}

/// Produce a fresh session for launching (refreshes Microsoft tokens).
pub async fn session_for(id: &str, client_id: &str) -> R<Session> {
    let acc = get(id)?;
    if acc.kind == "offline" {
        return Ok(Session {
            access_token: "0".into(),
            username: acc.username,
            uuid: acc.uuid.replace('-', ""),
            user_type: "legacy".into(),
        });
    }
    let refresh = read_secret(&acc.id)?;
    let r = ms_token(
        client_id,
        &[
            ("grant_type", "refresh_token"),
            ("client_id", client_id),
            ("refresh_token", &refresh),
            ("scope", SCOPE),
        ],
    )
    .await?;
    if r["error"].is_string() {
        return Err(anyhow!("Сессия истекла — войдите в аккаунт «{}» заново", acc.username).into());
    }
    let new_refresh = r["refresh_token"].as_str().unwrap_or(&refresh).to_string();
    let _ = store_secret(&acc.id, &new_refresh);
    let (mc, profile) = minecraft_login(r["access_token"].as_str().ok_or_else(|| anyhow!("нет access_token"))?).await?;
    let skin = skin_from_profile(&profile);
    if skin != acc.skin_url {
        let mut s = load_store();
        if let Some(a) = s.accounts.iter_mut().find(|a| a.id == acc.id) {
            a.skin_url = skin;
        }
        let _ = save_store(&s);
    }
    Ok(Session {
        access_token: mc,
        username: profile["name"].as_str().unwrap_or(&acc.username).to_string(),
        uuid: profile["id"].as_str().unwrap_or("").to_string(),
        user_type: "msa".into(),
    })
}

/// Upload a skin PNG to a licensed account.
pub async fn upload_skin(id: &str, client_id: &str, png: Vec<u8>, slim: bool) -> R<()> {
    let s = session_for(id, client_id).await?;
    if s.user_type != "msa" {
        return Err(anyhow!("Загрузка скина возможна только для аккаунта Microsoft").into());
    }
    let form = reqwest::multipart::Form::new()
        .text("variant", if slim { "slim" } else { "classic" })
        .part(
            "file",
            reqwest::multipart::Part::bytes(png).file_name("skin.png").mime_str("image/png")?,
        );
    let r = CLIENT
        .post("https://api.minecraftservices.com/minecraft/profile/skins")
        .bearer_auth(&s.access_token)
        .multipart(form)
        .send()
        .await?;
    if !r.status().is_success() {
        let st = r.status();
        let t = r.text().await.unwrap_or_default();
        return Err(anyhow!("Не удалось загрузить скин: HTTP {st} {}", t.chars().take(200).collect::<String>()).into());
    }
    Ok(())
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
