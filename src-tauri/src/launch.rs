//! Game launching: argument building, process supervision, log streaming, crash helper.

use crate::{
    auth,
    error::R,
    hud, instances::{self, Instance},
    java, loaders, net, paths, rpc,
    settings::{self, Settings},
    versions,
};
use anyhow::{anyhow, Context};
use once_cell::sync::Lazy;
use serde::Serialize;
use serde_json::Value;
use std::collections::{HashMap, VecDeque};
use std::path::PathBuf;
use std::sync::Mutex;
use tauri::{AppHandle, Emitter, Manager};
use tokio::io::{AsyncBufReadExt, BufReader};

pub struct Running {
    pub pid: u32,
}

pub static RUNNING: Lazy<Mutex<HashMap<String, Running>>> = Lazy::new(|| Mutex::new(HashMap::new()));
pub static LOGS: Lazy<Mutex<HashMap<String, VecDeque<String>>>> = Lazy::new(|| Mutex::new(HashMap::new()));
const MAX_LOG_LINES: usize = 5000;

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct GameLog {
    pub instance_id: String,
    pub line: String,
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct CrashHint {
    pub kind: String,
    pub message: String,
    /// optional machine-readable fix: "ram:6144" | "java:21" | "open-mods"
    pub action: String,
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct GameExit {
    pub instance_id: String,
    pub code: i32,
    pub hint: Option<CrashHint>,
}

fn push_log(app: &AppHandle, id: &str, line: String) {
    {
        let mut l = LOGS.lock().unwrap();
        let q = l.entry(id.to_string()).or_default();
        if q.len() >= MAX_LOG_LINES {
            q.pop_front();
        }
        q.push_back(line.clone());
    }
    let _ = app.emit("game-log", GameLog { instance_id: id.to_string(), line });
}

pub fn get_log(id: &str) -> Vec<String> {
    LOGS.lock().unwrap().get(id).map(|q| q.iter().cloned().collect()).unwrap_or_default()
}

fn split_args(s: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut cur = String::new();
    let mut quote: Option<char> = None;
    for c in s.chars() {
        match (c, quote) {
            ('"', None) | ('\'', None) => quote = Some(c),
            (q, Some(open)) if q == open => quote = None,
            (c, None) if c.is_whitespace() => {
                if !cur.is_empty() {
                    out.push(std::mem::take(&mut cur));
                }
            }
            (c, _) => cur.push(c),
        }
    }
    if !cur.is_empty() {
        out.push(cur);
    }
    out
}

fn flatten_args(arr: Option<&Value>, features: &[&str]) -> Vec<String> {
    let mut out = Vec::new();
    for a in arr.and_then(|a| a.as_array()).cloned().unwrap_or_default() {
        match a {
            Value::String(s) => out.push(s),
            Value::Object(_) => {
                if let Some(rules) = a.get("rules") {
                    if !versions::rules_allow(rules, features) {
                        continue;
                    }
                }
                match a.get("value") {
                    Some(Value::String(s)) => out.push(s.clone()),
                    Some(Value::Array(v)) => out.extend(v.iter().filter_map(|x| x.as_str().map(String::from))),
                    _ => {}
                }
            }
            _ => {}
        }
    }
    out
}

fn sep() -> &'static str {
    if cfg!(windows) { ";" } else { ":" }
}

pub struct Prepared {
    pub java: PathBuf,
    pub args: Vec<String>,
    pub game_dir: PathBuf,
}

pub async fn prepare(app: &AppHandle, inst: &Instance, st: &Settings, pid: &str) -> R<Prepared> {
    let session = auth::session_for(&inst.account_id, &st.ms_client_id).await?;
    let resolved = versions::resolve(&inst.version_id).await?;
    let major = versions::required_java_major(&resolved);

    let custom = if !inst.java_path.is_empty() { inst.java_path.clone() } else { st.custom_java.clone() };
    let java = java::ensure(app, pid, major, &custom).await?;
    let java_exe = java::game_exe(&java);

    let game_dir = instances::game_dir(&inst.id);
    std::fs::create_dir_all(&game_dir)?;
    let jar_id = resolved["jarId"].as_str().unwrap_or(&inst.version_id).to_string();

    let libs = versions::libs_for(&resolved);
    let mut cp: Vec<String> = Vec::new();
    let mut seen = std::collections::HashSet::new();
    for l in &libs {
        if l.extract {
            continue;
        }
        if !l.path.exists() {
            return Err(anyhow!("Не хватает файла {} — переустановите сборку", l.path.display()).into());
        }
        if seen.insert(l.path.clone()) {
            cp.push(l.path.to_string_lossy().to_string());
        }
    }
    let client_jar = versions::version_jar_path(&jar_id);
    // Modern Forge/NeoForge add the client jar through the module path themselves; always harmless on classpath for others.
    let needs_client_on_cp = !is_modern_forge(&resolved);
    if needs_client_on_cp {
        cp.push(client_jar.to_string_lossy().to_string());
    }
    let classpath = cp.join(sep());

    let natives = paths::natives_dir(&inst.version_id);
    let index_id = resolved.pointer("/assetIndex/id").and_then(|v| v.as_str()).unwrap_or("legacy").to_string();
    let virtual_dir = paths::assets_dir().join("virtual").join(&index_id);
    let width = if inst.width > 0 { inst.width } else { st.window_width };
    let height = if inst.height > 0 { inst.height } else { st.window_height };

    let mut features: Vec<&str> = vec!["has_custom_resolution"];
    let (srv_host, srv_port) = split_server(&inst.server);
    if !inst.server.is_empty() {
        features.push("is_quick_play_multiplayer");
    }

    let version_type = resolved.get("type").and_then(|v| v.as_str()).unwrap_or("release").to_string();
    let mut vars: HashMap<&str, String> = HashMap::new();
    vars.insert("auth_player_name", session.username.clone());
    vars.insert("version_name", inst.version_id.clone());
    vars.insert("game_directory", game_dir.to_string_lossy().to_string());
    vars.insert("assets_root", paths::assets_dir().to_string_lossy().to_string());
    vars.insert("assets_index_name", index_id.clone());
    vars.insert("auth_uuid", session.uuid.clone());
    vars.insert("auth_access_token", session.access_token.clone());
    vars.insert("auth_session", format!("token:{}:{}", session.access_token, session.uuid));
    vars.insert("clientid", String::new());
    vars.insert("auth_xuid", String::new());
    vars.insert("user_type", session.user_type.clone());
    vars.insert("version_type", version_type);
    vars.insert("user_properties", "{}".into());
    vars.insert("natives_directory", natives.to_string_lossy().to_string());
    vars.insert("launcher_name", "Nocturnum".into());
    vars.insert("launcher_version", env!("CARGO_PKG_VERSION").into());
    vars.insert("classpath", classpath.clone());
    vars.insert("classpath_separator", sep().into());
    vars.insert("library_directory", paths::libraries_dir().to_string_lossy().to_string());
    vars.insert("resolution_width", width.to_string());
    vars.insert("resolution_height", height.to_string());
    vars.insert("game_assets", virtual_dir.to_string_lossy().to_string());
    vars.insert("quickPlayMultiplayer", inst.server.clone());
    vars.insert("quickPlaySingleplayer", String::new());
    vars.insert("quickPlayPath", "quickplay.json".into());
    vars.insert("quickPlayRealms", String::new());

    let subst = |s: &str| -> String {
        let mut out = s.to_string();
        for (k, v) in &vars {
            out = out.replace(&format!("${{{k}}}"), v);
        }
        out
    };

    let mut jvm: Vec<String> = Vec::new();
    let ram = if inst.ram_mb > 0 { inst.ram_mb } else { st.default_ram_mb };
    jvm.push(format!("-Xmx{ram}M"));
    jvm.push("-Xms256M".into());
    for a in [
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+UseG1GC",
        "-XX:G1NewSizePercent=20",
        "-XX:G1ReservePercent=20",
        "-XX:MaxGCPauseMillis=50",
        "-XX:G1HeapRegionSize=32M",
    ] {
        jvm.push(a.into());
    }
    jvm.extend(split_args(&st.jvm_args));
    jvm.extend(split_args(&inst.jvm_args));

    let mut version_jvm: Vec<String> = if resolved.get("arguments").is_some() {
        flatten_args(resolved.pointer("/arguments/jvm"), &features).iter().map(|a| subst(a)).collect()
    } else {
        vec![]
    };
    if version_jvm.is_empty() {
        version_jvm = vec![
            format!("-Djava.library.path={}", natives.display()),
            "-cp".into(),
            classpath.clone(),
        ];
    }
    let has_cp = version_jvm.iter().any(|a| a == "-cp" || a == "-classpath" || a == "--class-path");
    let has_natives = version_jvm.iter().any(|a| a.starts_with("-Djava.library.path"));
    jvm.extend(version_jvm);
    if !has_natives {
        jvm.push(format!("-Djava.library.path={}", natives.display()));
    }
    if !has_cp {
        jvm.push("-cp".into());
        jvm.push(classpath.clone());
    }
    if let Some(a) = resolved.pointer("/logging/client/argument").and_then(|a| a.as_str()) {
        if let Some(f) = resolved.pointer("/logging/client/file/id").and_then(|a| a.as_str()) {
            let p = paths::assets_dir().join("log_configs").join(f);
            if p.exists() {
                jvm.push(a.replace("${path}", &p.to_string_lossy()));
            }
        }
    }
    jvm.push(format!("-Dnocturnum.launcher=1"));
    jvm.push(format!("-Dnocturnum.state={}", game_dir.join(".nocturnum").join("state.json").display()));

    let main_class = resolved["mainClass"].as_str().ok_or_else(|| anyhow!("mainClass не указан"))?.to_string();

    let mut game: Vec<String> = if resolved.get("arguments").is_some() {
        flatten_args(resolved.pointer("/arguments/game"), &features).iter().map(|a| subst(a)).collect()
    } else {
        resolved["minecraftArguments"]
            .as_str()
            .unwrap_or("")
            .split_whitespace()
            .map(|a| subst(a))
            .collect()
    };
    if st.fullscreen || inst.fullscreen {
        game.push("--fullscreen".into());
    }
    if !inst.server.is_empty() && !features.contains(&"is_quick_play_multiplayer") {
        game.push("--server".into());
        game.push(srv_host.clone());
        game.push("--port".into());
        game.push(srv_port.to_string());
    }
    // legacy args (<1.13) don't know width/height unless in template
    if resolved.get("arguments").is_none() && !game.iter().any(|a| a == "--width") {
        game.push("--width".into());
        game.push(width.to_string());
        game.push("--height".into());
        game.push(height.to_string());
    }

    let mut args = jvm;
    args.push(main_class);
    args.extend(game);
    Ok(Prepared { java: java_exe, args, game_dir })
}

fn is_modern_forge(resolved: &Value) -> bool {
    resolved
        .pointer("/arguments/jvm")
        .and_then(|a| a.as_array())
        .map(|a| a.iter().any(|x| x.as_str().map(|s| s == "--add-modules" || s == "-p").unwrap_or(false)))
        .unwrap_or(false)
        && resolved["mainClass"].as_str().map(|m| m.contains("bootstraplauncher")).unwrap_or(false)
}

fn split_server(s: &str) -> (String, u16) {
    match s.rsplit_once(':') {
        Some((h, p)) => (h.to_string(), p.parse().unwrap_or(25565)),
        None => (s.to_string(), 25565),
    }
}

/// Install everything the instance needs: vanilla files, loader, Java, HUD mod.
pub async fn ensure_installed(app: &AppHandle, inst: &mut Instance, st: &Settings, pid: &str) -> R<()> {
    if inst.version_id.is_empty() || !versions::version_json_path(&inst.version_id).exists() || inst.loader != "vanilla" && !inst.installed {
        let vid = loaders::install(app, pid, &inst.loader, &inst.mc_version, &inst.loader_version, st.downloads_threads as usize).await?;
        inst.version_id = vid;
    }
    let resolved = versions::install(app, pid, &inst.version_id, st.downloads_threads as usize).await?;
    let major = versions::required_java_major(&resolved);
    let custom = if !inst.java_path.is_empty() { inst.java_path.clone() } else { st.custom_java.clone() };
    java::ensure(app, pid, major, &custom).await?;
    if inst.hud_enabled {
        let _ = hud::install(app, inst).await; // incompatibility is surfaced to the UI by hud_status
    }
    inst.installed = true;
    instances::save(inst)?;
    Ok(())
}

pub async fn launch(app: AppHandle, instance_id: String) -> R<()> {
    if RUNNING.lock().unwrap().contains_key(&instance_id) {
        return Err(anyhow!("Игра уже запущена").into());
    }
    let st = settings::load();
    let mut inst = instances::get(&instance_id)?;
    let pid = format!("launch:{}", inst.id);
    if inst.account_id.is_empty() {
        inst.account_id = st.last_account.clone();
    }
    if inst.account_id.is_empty() {
        return Err(anyhow!("Выберите аккаунт для запуска").into());
    }
    let result = launch_inner(&app, &mut inst, &st, &pid).await;
    if let Err(e) = &result {
        net::emit_progress(&app, &pid, "Ошибка", 0.0, 0.0, true, Some(e.0.clone()));
    }
    result
}

async fn launch_inner(app: &AppHandle, inst: &mut Instance, st: &Settings, pid: &str) -> R<()> {
    rpc::set(rpc::State::Launching { mc: inst.mc_version.clone(), loader: inst.loader.clone(), name: inst.name.clone() });
    ensure_installed(app, inst, st, pid).await?;
    net::emit_progress(app, pid, "Запуск игры", 100.0, 0.0, false, None);
    let prepared = prepare(app, inst, st, pid).await?;

    if st.discrete_gpu {
        set_gpu_preference(&prepared.java);
    }
    let _ = std::fs::remove_file(prepared.game_dir.join(".nocturnum").join("state.json"));
    sync_blacklist(&prepared.game_dir).await;

    let mut cmd = tokio::process::Command::new(&prepared.java);
    cmd.args(&prepared.args)
        .current_dir(&prepared.game_dir)
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .stdin(std::process::Stdio::null());
    if st.discrete_gpu && !cfg!(windows) {
        cmd.env("__NV_PRIME_RENDER_OFFLOAD", "1").env("DRI_PRIME", "1");
    }
    #[cfg(windows)]
    cmd.creation_flags(0x08000000);
    let mut child = cmd.spawn().with_context(|| format!("не удалось запустить {}", prepared.java.display()))?;
    let child_pid = child.id().unwrap_or(0);
    if st.high_priority {
        set_high_priority(child_pid);
    }
    RUNNING.lock().unwrap().insert(inst.id.clone(), Running { pid: child_pid });
    LOGS.lock().unwrap().insert(inst.id.clone(), VecDeque::new());
    net::emit_progress(app, pid, "Игра запущена", 100.0, 0.0, true, None);
    let _ = app.emit("game-started", inst.id.clone());
    if st.minimize_on_launch {
        if let Some(w) = app.get_webview_window("main") {
            let _ = w.hide();
        }
    }

    inst.last_played = instances::now();
    instances::save(inst)?;

    let started = std::time::Instant::now();
    let started_unix = chrono::Utc::now().timestamp();
    let id = inst.id.clone();
    let hide_server = st.rpc_hide_server;
    let hide_name = st.rpc_hide_instance;
    let inst_name = inst.name.clone();
    let mc = inst.mc_version.clone();
    let loader = inst.loader.clone();
    let state_file = prepared.game_dir.join(".nocturnum").join("state.json");

    let stdout = child.stdout.take();
    let stderr = child.stderr.take();
    let mut readers = Vec::new();
    for pipe in [stdout.map(|s| Box::new(s) as Box<dyn tokio::io::AsyncRead + Unpin + Send>), stderr.map(|s| Box::new(s) as Box<dyn tokio::io::AsyncRead + Unpin + Send>)]
        .into_iter()
        .flatten()
    {
        let app2 = app.clone();
        let id2 = id.clone();
        readers.push(tokio::spawn(async move {
            let mut lines = BufReader::new(pipe).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                push_log(&app2, &id2, line);
            }
        }));
    }

    // RPC state poller
    let poll_id = id.clone();
    let poller = tokio::spawn(async move {
        loop {
            tokio::time::sleep(std::time::Duration::from_secs(4)).await;
            let s = std::fs::read_to_string(&state_file).ok().and_then(|s| serde_json::from_str::<Value>(&s).ok());
            let (screen, server) = match s {
                Some(v) => (
                    v["screen"].as_str().unwrap_or("menu").to_string(),
                    v["server"].as_str().unwrap_or("").to_string(),
                ),
                None => ("loading".to_string(), String::new()),
            };
            rpc::set(rpc::State::InGame {
                screen,
                server: if hide_server { String::new() } else { server },
                name: if hide_name { String::new() } else { inst_name.clone() },
                mc: mc.clone(),
                loader: loader.clone(),
                since: started_unix,
            });
            let _ = &poll_id;
        }
    });

    let status = child.wait().await?;
    poller.abort();
    for r in readers {
        let _ = r.await;
    }
    RUNNING.lock().unwrap().remove(&id);
    let code = status.code().unwrap_or(-1);
    let secs = started.elapsed().as_secs();
    if let Ok(mut i) = instances::get(&id) {
        i.play_time_secs += secs;
        let _ = instances::save(&i);
    }
    rpc::set(rpc::State::Launcher);
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.show();
        let _ = w.set_focus();
    }
    let hint = if code != 0 { analyze_crash(&id, &get_log(&id)) } else { None };
    let _ = app.emit("game-exit", GameExit { instance_id: id.clone(), code, hint });
    Ok(())
}

const BLACKLIST_URL: &str = "https://raw.githubusercontent.com/Hikaru220/nocturnum/main/config/server-blacklist.json";

/// Download the server blacklist for the Anarchy category (falls back to the cached copy offline).
async fn sync_blacklist(game_dir: &std::path::Path) {
    let cache = paths::cache_dir().join("server-blacklist.json");
    let body = match tokio::time::timeout(std::time::Duration::from_secs(4), net::get_text(BLACKLIST_URL)).await {
        Ok(Ok(t)) if serde_json::from_str::<serde_json::Value>(&t).is_ok() => {
            let _ = std::fs::create_dir_all(paths::cache_dir());
            let _ = std::fs::write(&cache, &t);
            Some(t)
        }
        _ => std::fs::read_to_string(&cache).ok(),
    };
    if let Some(t) = body {
        let dir = game_dir.join("config").join("nocturnum");
        let _ = std::fs::create_dir_all(&dir);
        let _ = std::fs::write(dir.join("blacklist.json"), t);
    }
}

#[cfg(windows)]
fn set_high_priority(pid: u32) {
    use windows_sys::Win32::Foundation::CloseHandle;
    use windows_sys::Win32::System::Threading::{OpenProcess, SetPriorityClass, ABOVE_NORMAL_PRIORITY_CLASS, PROCESS_SET_INFORMATION};
    unsafe {
        let h = OpenProcess(PROCESS_SET_INFORMATION, 0, pid);
        if !h.is_null() {
            SetPriorityClass(h, ABOVE_NORMAL_PRIORITY_CLASS);
            CloseHandle(h);
        }
    }
}
#[cfg(not(windows))]
fn set_high_priority(_pid: u32) {}

pub fn kill(id: &str) -> R<()> {
    let pid = RUNNING.lock().unwrap().get(id).map(|r| r.pid);
    if let Some(pid) = pid {
        #[cfg(windows)]
        {
            let _ = std::process::Command::new("taskkill").args(["/PID", &pid.to_string(), "/T", "/F"]).output();
        }
        #[cfg(not(windows))]
        {
            let _ = std::process::Command::new("kill").args(["-9", &pid.to_string()]).output();
        }
    }
    Ok(())
}

#[cfg(windows)]
fn set_gpu_preference(java: &std::path::Path) {
    let _ = std::process::Command::new("reg")
        .args([
            "add",
            "HKCU\\Software\\Microsoft\\DirectX\\UserGpuPreferences",
            "/v",
            &java.to_string_lossy(),
            "/t",
            "REG_SZ",
            "/d",
            "GpuPreference=2;",
            "/f",
        ])
        .output();
}
#[cfg(not(windows))]
fn set_gpu_preference(_java: &std::path::Path) {}

/// Crash helper (ТЗ 3.8): recognises frequent failures and proposes a fix in plain words.
pub fn analyze_crash(id: &str, log: &[String]) -> Option<CrashHint> {
    let mut text = log.join("\n");
    let reports = instances::game_dir(id).join("crash-reports");
    if let Ok(rd) = std::fs::read_dir(&reports) {
        let mut files: Vec<_> = rd.flatten().collect();
        files.sort_by_key(|e| e.metadata().and_then(|m| m.modified()).ok());
        if let Some(last) = files.last() {
            if let Ok(t) = std::fs::read_to_string(last.path()) {
                text.push('\n');
                text.push_str(&t);
            }
        }
    }
    let inst = instances::get(id).ok();
    let ram = inst.as_ref().map(|i| if i.ram_mb > 0 { i.ram_mb } else { settings::load().default_ram_mb }).unwrap_or(4096);
    let has = |s: &str| text.contains(s);
    if has("OutOfMemoryError") || has("Could not reserve enough space") || has("There is insufficient memory") {
        let new = (ram + 2048).min(16384);
        return Some(CrashHint {
            kind: "memory".into(),
            message: format!("Не хватает памяти. Увеличить до {} ГБ?", new / 1024),
            action: format!("ram:{new}"),
        });
    }
    if has("UnsupportedClassVersionError") || has("has been compiled by a more recent version of the Java Runtime") {
        return Some(CrashHint {
            kind: "java".into(),
            message: "Не та версия Java для этой сборки. Лаунчер скачает подходящую — переустановите сборку и запустите снова.".into(),
            action: "java:auto".into(),
        });
    }
    if has("Incompatible mod set") || has("Mixin apply failed") || has("Duplicate mod") || has("Mod resolution failed") || has("duplicate mod") {
        return Some(CrashHint {
            kind: "mods".into(),
            message: "Конфликт или несовместимость модов. Откройте вкладку «Моды» и отключите недавно добавленные.".into(),
            action: "open-mods".into(),
        });
    }
    if has("requires version") && has("of mod") || has("Unmet dependency") || has("depends on") && has("which is missing") {
        return Some(CrashHint {
            kind: "dependency".into(),
            message: "Не хватает мода-зависимости или его версия не подходит. Установите нужный мод из каталога.".into(),
            action: "open-mods".into(),
        });
    }
    if has("GLFW error") || has("Pixel format not accelerated") || has("OpenGL") && has("failed") {
        return Some(CrashHint {
            kind: "graphics".into(),
            message: "Проблема с видеодрайвером. Обновите драйвер видеокарты или включите дискретную видеокарту в настройках.".into(),
            action: "open-settings".into(),
        });
    }
    if has("Unable to launch") || has("Error: Could not find or load main class") {
        return Some(CrashHint {
            kind: "install".into(),
            message: "Файлы сборки повреждены. Нажмите «Проверить файлы» в настройках сборки.".into(),
            action: "repair".into(),
        });
    }
    Some(CrashHint {
        kind: "unknown".into(),
        message: "Игра завершилась с ошибкой. Скопируйте лог и покажите его в поддержке.".into(),
        action: String::new(),
    })
}
