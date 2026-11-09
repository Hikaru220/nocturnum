//! NOCTURNUM: command layer over the Modrinth App core (`theseus`, fork branch "nocturnum").
//! The React interface keeps calling the same command names; the work is done by the core.
//! Tokens never leave this process: the interface only gets the player profile.

use crate::{auth::Account, error::R};
use anyhow::anyhow;
use serde::Serialize;
use std::sync::Mutex;
use tauri::{AppHandle, Emitter};
use theseus::prelude::*;

/// Start the core state (SQLite database, settings, Java and version metadata). Data folder: the "Nocturnum" folder.
pub async fn init() -> R<()> {
    theseus::State::init("Nocturnum".to_string()).await.map_err(|e| anyhow!("{e}"))?;
    Ok(())
}

fn err(e: theseus::Error) -> crate::error::AppError {
    crate::error::AppError(auth_message(&e.to_string()))
}

/// XSTS / Mojang errors in plain Russian (spec 3.1).
fn auth_message(raw: &str) -> String {
    let pairs: &[(&str, &str)] = &[
        ("2148916233", "У этого аккаунта Microsoft нет профиля Xbox. Создайте его на xbox.com."),
        ("2148916235", "Xbox Live недоступен в стране этого аккаунта."),
        ("2148916236", "Нужно подтвердить возраст аккаунта Microsoft."),
        ("2148916237", "Нужно подтвердить возраст аккаунта Microsoft."),
        ("2148916238", "Детский аккаунт: добавьте его в семью Microsoft."),
        ("2148916227", "Аккаунт Xbox заблокирован."),
        ("Invalid app registration", "Вход с лицензией пока недоступен: Mojang ещё не одобрил приложение Nocturnum. Как только заявку одобрят, вход заработает без обновления."),
        ("expired_token", "Код истёк, попробуйте ещё раз."),
        ("authorization_declined", "Вход отклонён."),
    ];
    for (k, v) in pairs {
        if raw.contains(k) {
            return (*v).to_string();
        }
    }
    raw.to_string()
}

fn to_account(c: &Credentials) -> Account {
    let p = &c.offline_profile;
    let skin = p.skins.first(); // Mojang returns a single, active skin per player
    Account {
        id: p.id.to_string(),
        kind: "microsoft".into(),
        username: p.name.clone(),
        uuid: p.id.to_string(),
        skin_url: skin.map(|s| s.url.to_string()).unwrap_or_default(),
    }
}

pub async fn list_accounts() -> R<Vec<Account>> {
    let users = theseus::minecraft_auth::users().await.map_err(err)?;
    Ok(users.iter().map(to_account).collect())
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct DeviceCodeEvent {
    user_code: String,
    verification_uri: String,
    expires_in: u64,
}

static CANCEL: Mutex<Option<std::sync::Arc<tokio::sync::Notify>>> = Mutex::new(None);

pub fn cancel_login() {
    if let Some(n) = CANCEL.lock().unwrap().as_ref() {
        n.notify_one();
    }
}

/// Device code sign-in: shows the code through the `auth-code` event, then waits until it is entered.
pub async fn login_microsoft(app: &AppHandle) -> R<Account> {
    let flow = theseus::minecraft_auth::begin_login().await.map_err(err)?;
    let _ = app.emit(
        "auth-code",
        DeviceCodeEvent { user_code: flow.user_code.clone(), verification_uri: flow.verification_uri.clone(), expires_in: flow.expires_in },
    );
    let stop = std::sync::Arc::new(tokio::sync::Notify::new());
    *CANCEL.lock().unwrap() = Some(stop.clone());
    let cred = tokio::select! {
        r = theseus::minecraft_auth::finish_login(flow) => r.map_err(err)?,
        _ = stop.notified() => return Err(anyhow!("Вход отменён").into()),
    };
    *CANCEL.lock().unwrap() = None;
    Ok(to_account(&cred))
}

pub async fn remove_account(id: &str) -> R<()> {
    let uuid = uuid::Uuid::parse_str(id).map_err(|e| anyhow!("{e}"))?;
    theseus::minecraft_auth::remove_user(uuid).await.map_err(err)
}
