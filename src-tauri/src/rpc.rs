//! Discord Rich Presence over the local IPC pipe. Silent no-op when Discord is not running.

use crate::{net, paths, settings};
use discord_rich_presence::{activity, DiscordIpc, DiscordIpcClient};
use once_cell::sync::Lazy;
use std::sync::mpsc::{channel, Sender};
use std::sync::Mutex;

#[derive(Clone, Debug)]
pub enum State {
    Off,
    Launcher,
    Launching { mc: String, loader: String, name: String },
    InGame { screen: String, server: String, name: String, mc: String, loader: String, since: i64 },
}

const LINKS_URL: &str = "https://raw.githubusercontent.com/Hikaru220/nocturnum/main/config/links.json";

#[derive(Clone, Debug)]
struct Links {
    buttons: Vec<(String, String)>,
}

fn default_links() -> Links {
    Links { buttons: vec![("Скачать Nocturnum".into(), "https://t.me/Nocturnum_Launcher".into())] }
}

fn parse_links(v: &serde_json::Value) -> Links {
    let mut buttons = Vec::new();
    for key in ["download", "discord"] {
        let label = v[key]["label"].as_str().unwrap_or("");
        let url = v[key]["url"].as_str().unwrap_or("");
        // Discord allows max 2 buttons, label up to 32 chars, http(s) urls only
        if !label.is_empty() && (url.starts_with("https://") || url.starts_with("http://")) {
            buttons.push((label.chars().take(32).collect::<String>(), url.to_string()));
        }
    }
    if buttons.is_empty() { default_links() } else { Links { buttons } }
}

/// Links come from a tiny JSON on GitHub so they can change without a launcher update; cached on disk.
fn load_links() -> Links {
    let cache = paths::cache_dir().join("links.json");
    let fetched = tauri::async_runtime::block_on(async { net::get_json::<serde_json::Value>(LINKS_URL).await });
    match fetched {
        Ok(v) => {
            let _ = std::fs::create_dir_all(paths::cache_dir());
            let _ = std::fs::write(&cache, v.to_string());
            parse_links(&v)
        }
        Err(_) => std::fs::read_to_string(&cache)
            .ok()
            .and_then(|s| serde_json::from_str::<serde_json::Value>(&s).ok())
            .map(|v| parse_links(&v))
            .unwrap_or_else(default_links),
    }
}

static TX: Lazy<Mutex<Option<Sender<State>>>> = Lazy::new(|| Mutex::new(None));

pub fn start() {
    let (tx, rx) = channel::<State>();
    *TX.lock().unwrap() = Some(tx);
    std::thread::spawn(move || {
        let mut client: Option<DiscordIpcClient> = None;
        let mut connected_id = String::new();
        let mut last = State::Off;
        let links = load_links();
        loop {
            // wait for an update, but also retry periodically
            let msg = rx.recv_timeout(std::time::Duration::from_secs(15));
            if let Ok(m) = msg {
                last = m;
            }
            let st = settings::load();
            let want = st.discord_rpc && !st.discord_app_id.trim().is_empty() && !matches!(last, State::Off);
            if !want {
                if let Some(mut c) = client.take() {
                    let _ = c.clear_activity();
                    let _ = c.close();
                }
                continue;
            }
            if client.is_none() || connected_id != st.discord_app_id {
                if let Some(mut c) = client.take() {
                    let _ = c.close();
                }
                match DiscordIpcClient::new(st.discord_app_id.trim()) {
                    Ok(mut c) => {
                        if c.connect().is_ok() {
                            connected_id = st.discord_app_id.clone();
                            client = Some(c);
                        }
                    }
                    Err(_) => {}
                }
            }
            let Some(c) = client.as_mut() else { continue };
            let (details, state, since) = describe(&last);
            let mut act = activity::Activity::new()
                .details(&details)
                .assets(activity::Assets::new().large_image("nocturnum").large_text("Nocturnum"))
                .buttons(links.buttons.iter().map(|(l, u)| activity::Button::new(l, u)).collect());
            if !state.is_empty() {
                act = act.state(&state);
            }
            if let Some(s) = since {
                act = act.timestamps(activity::Timestamps::new().start(s));
            }
            if c.set_activity(act).is_err() {
                // pipe broke (Discord restarted) — reconnect next round
                if let Some(mut c) = client.take() {
                    let _ = c.close();
                }
            }
        }
    });
}

fn loader_title(l: &str) -> String {
    match l {
        "fabric" => "Fabric".into(),
        "quilt" => "Quilt".into(),
        "neoforge" => "NeoForge".into(),
        "forge" => "Forge".into(),
        _ => "Vanilla".into(),
    }
}

fn describe(s: &State) -> (String, String, Option<i64>) {
    match s {
        State::Off => (String::new(), String::new(), None),
        State::Launcher => ("В лаунчере".into(), String::new(), None),
        State::Launching { mc, loader, name } => {
            let _ = name;
            (format!("Запускает {mc} · {}", loader_title(loader)), String::new(), None)
        }
        State::InGame { screen, server, name, mc, loader, since } => {
            let details = match screen.as_str() {
                "singleplayer" => "Одиночная игра".to_string(),
                "multiplayer" => "Играет на сервере".to_string(),
                "loading" => format!("Запускает {mc} · {}", loader_title(loader)),
                _ => "В главном меню".to_string(),
            };
            let mut state = String::new();
            if screen == "multiplayer" && !server.is_empty() {
                state = server.clone();
            } else if !name.is_empty() {
                state = name.clone();
            }
            (details, state, Some(*since))
        }
    }
}

pub fn set(s: State) {
    if let Some(tx) = TX.lock().unwrap().as_ref() {
        let _ = tx.send(s);
    }
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DiscordCheck {
    pub ok: bool,
    pub running: bool,
    pub message: String,
}

/// Verifies the Application ID format and that the local Discord client accepts the connection.
pub fn check(app_id: &str) -> DiscordCheck {
    let id = app_id.trim();
    if id.len() < 17 || id.len() > 20 || !id.chars().all(|c| c.is_ascii_digit()) {
        return DiscordCheck { ok: false, running: false, message: "Application ID — число из 17–20 цифр (Developer Portal → General Information).".into() };
    }
    match DiscordIpcClient::new(id) {
        Ok(mut c) => match c.connect() {
            Ok(()) => {
                let _ = c.close();
                DiscordCheck { ok: true, running: true, message: "Discord найден, подключение к приложению успешно. Статус появится при запуске игры.".into() }
            }
            Err(e) => DiscordCheck {
                ok: false,
                running: false,
                message: format!("Не удалось подключиться к Discord: запустите приложение Discord (не браузерную версию) и повторите. ({e})"),
            },
        },
        Err(e) => DiscordCheck { ok: false, running: false, message: format!("Ошибка клиента Discord: {e}") },
    }
}
