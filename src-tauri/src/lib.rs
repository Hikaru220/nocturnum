#![recursion_limit = "512"] // the core (theseus) has deeply nested futures
#[cfg(windows)]
mod winapi;
mod sunshine;
mod auth;
mod core;
mod core_instances;
mod core_content;
mod core_mods;
mod core_packs;
mod core_java;
mod core_skins;
mod error;
mod hardware;
mod hud;
mod instances;
mod java;
mod launch;
mod loaders;
mod media;
mod modrinth;
mod net;
mod paths;
mod rpc;
mod backups;
mod settings;
mod shots;
mod skins;
mod versions;

use error::R;
use instances::Instance;
use serde::Serialize;
use serde_json::Value;
use std::io::Write;
use tauri::{
    menu::{Menu, MenuItem, PredefinedMenuItem},
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    AppHandle, Emitter, Manager, WebviewWindow,
};

fn applog(msg: &str) {
    let _ = std::fs::create_dir_all(paths::logs_dir());
    if let Ok(mut f) = std::fs::OpenOptions::new().create(true).append(true).open(paths::logs_dir().join("launcher.log")) {
        let _ = writeln!(f, "[{}] {}", chrono::Local::now().format("%Y-%m-%d %H:%M:%S"), msg);
    }
}

// ---------- settings / system ----------

#[tauri::command]
fn get_settings() -> settings::Settings {
    settings::load()
}

#[tauri::command]
fn save_settings(settings: settings::Settings) -> R<()> {
    settings::save(&settings)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SystemInfo {
    total_ram_mb: u64,
    os: String,
    portable: bool,
    root_dir: String,
    version: String,
    cpu_threads: usize,
}

#[tauri::command]
fn system_info() -> SystemInfo {
    let mut sys = sysinfo::System::new();
    sys.refresh_memory();
    SystemInfo {
        total_ram_mb: sys.total_memory() / 1024 / 1024,
        os: versions::os_name().into(),
        portable: paths::is_portable(),
        root_dir: paths::root().to_string_lossy().to_string(),
        version: env!("CARGO_PKG_VERSION").into(),
        cpu_threads: std::thread::available_parallelism().map(|n| n.get()).unwrap_or(4),
    }
}

#[tauri::command]
fn set_root_dir(path: String, move_data: bool) -> R<()> {
    let new = std::path::PathBuf::from(path.trim());
    if new.as_os_str().is_empty() {
        return Err(anyhow::anyhow!("Путь не указан").into());
    }
    let old = paths::root();
    if new == old {
        return Ok(());
    }
    std::fs::create_dir_all(&new)?;
    if move_data && old.exists() {
        if new.starts_with(&old) {
            return Err(anyhow::anyhow!("Нельзя переносить данные внутрь самих себя").into());
        }
        instances::copy_dir(&old, &new)?;
    }
    std::fs::create_dir_all(paths::bootstrap_dir())?;
    std::fs::write(paths::bootstrap_dir().join("root.txt"), new.to_string_lossy().as_bytes())?;
    if move_data && old.exists() && old != paths::bootstrap_dir() {
        let _ = std::fs::remove_dir_all(&old);
    }
    Ok(())
}

#[tauri::command]
fn get_launcher_log() -> String {
    std::fs::read_to_string(paths::logs_dir().join("launcher.log"))
        .map(|s| {
            let lines: Vec<&str> = s.lines().collect();
            lines[lines.len().saturating_sub(500)..].join("\n")
        })
        .unwrap_or_default()
}

#[tauri::command]
fn open_path(path: String) -> R<()> {
    let p = std::path::PathBuf::from(&path);
    if !p.exists() {
        std::fs::create_dir_all(&p)?;
    }
    #[cfg(windows)]
    {
        std::process::Command::new("explorer").arg(&p).spawn()?;
    }
    #[cfg(target_os = "macos")]
    {
        std::process::Command::new("open").arg(&p).spawn()?;
    }
    #[cfg(all(unix, not(target_os = "macos")))]
    {
        std::process::Command::new("xdg-open").arg(&p).spawn()?;
    }
    Ok(())
}

#[tauri::command]
fn gallery_list() -> Vec<shots::Shot> {
    shots::list()
}

#[tauri::command]
async fn screenshot_thumb(path: String) -> R<String> {
    tauri::async_runtime::spawn_blocking(move || shots::thumb(&path)).await.map_err(|e| anyhow::anyhow!("{e}"))?
}

#[tauri::command]
async fn screenshot_full(path: String) -> R<String> {
    tauri::async_runtime::spawn_blocking(move || shots::full(&path)).await.map_err(|e| anyhow::anyhow!("{e}"))?
}

#[tauri::command]
fn delete_screenshot(path: String) -> R<()> {
    shots::delete(&path)
}

#[tauri::command]
async fn build_licenses() -> Vec<hud::LicenseRow> {
    hud::licenses().await
}

#[tauri::command]
fn list_backups(id: String) -> Vec<backups::Backup> {
    backups::list(&id)
}

#[tauri::command]
async fn create_backup(id: String) -> R<Option<String>> {
    tauri::async_runtime::spawn_blocking(move || backups::create(&id)).await.map_err(|e| anyhow::anyhow!("{e}"))?
}

#[tauri::command]
async fn restore_backup(id: String, file_name: String) -> R<()> {
    if launch::is_running(&id) {
        return Err(anyhow::anyhow!("Сначала закройте игру").into());
    }
    tauri::async_runtime::spawn_blocking(move || backups::restore(&id, &file_name)).await.map_err(|e| anyhow::anyhow!("{e}"))?
}

#[tauri::command]
fn read_file_data_url(path: String) -> R<String> {
    use base64::Engine;
    let bytes = std::fs::read(&path)?;
    if bytes.len() > 20 * 1024 * 1024 {
        return Err(anyhow::anyhow!("Файл слишком большой").into());
    }
    Ok(format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(bytes)))
}

#[tauri::command]
fn write_png(path: String, data_url: String) -> R<()> {
    use base64::Engine;
    let b64 = data_url.split_once(',').map(|x| x.1).unwrap_or(&data_url);
    let bytes = base64::engine::general_purpose::STANDARD.decode(b64)?;
    skins::check_png(&bytes)?;
    std::fs::write(path, bytes)?;
    Ok(())
}

#[tauri::command]
async fn hardware_info() -> hardware::Hardware {
    tauri::async_runtime::spawn_blocking(hardware::detect).await.unwrap_or_else(|_| hardware::detect())
}

// ---------- versions / loaders ----------

#[tauri::command]
async fn mc_versions() -> R<versions::Manifest> {
    versions::manifest().await
}

#[tauri::command]
async fn loader_versions(loader: String, mc: String) -> R<Vec<loaders::LoaderVersion>> {
    loaders::list_loader_versions(&loader, &mc).await
}

#[tauri::command]
fn hud_status(app: AppHandle, mc: String, loader: String) -> hud::HudStatus {
    hud::status(&app, &mc, &loader)
}

// ---------- instances ----------

#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase")]
struct NewInstance {
    name: String,
    mc_version: String,
    loader: String,
    loader_version: String,
    hud_enabled: bool,
    account_id: String,
}

fn smart_ram() -> u32 {
    let mut sys = sysinfo::System::new();
    sys.refresh_memory();
    hardware::smart_ram_mb(sys.total_memory() / 1024 / 1024, 0)
}

pub(crate) fn preset_for(id: &str) -> R<()> {
    let st = settings::load();
    let tier = if st.perf_tier == "auto" { hardware::detect().tier } else { st.perf_tier.clone() };
    instances::write_preset_tier(id, &tier)
}

#[tauri::command]
async fn list_instances() -> R<Vec<Instance>> {
    core_instances::list_instances().await
}

#[tauri::command]
async fn create_instance(app: AppHandle, req: NewInstance) -> R<Instance> {
    if req.name.trim().is_empty() {
        return Err(anyhow::anyhow!("Введите название сборки").into());
    }
    core_instances::create_instance(&app, &req).await
}

#[tauri::command]
async fn update_instance(instance: Instance) -> R<()> {
    core_instances::update_instance(&instance).await
}

#[tauri::command]
async fn delete_instance(id: String) -> R<()> {
    if core_instances::running().await?.contains(&id) {
        return Err(anyhow::anyhow!("Сначала закройте игру").into());
    }
    core_instances::delete_instance(&id).await
}

#[tauri::command]
async fn duplicate_instance(app: AppHandle, id: String) -> R<Instance> {
    core_packs::duplicate_instance(&app, &id).await
}

#[tauri::command]
fn open_instance_folder(id: String) -> R<()> {
    open_path(instances::game_dir(&id).to_string_lossy().to_string())
}

#[tauri::command]
fn apply_preset_now(id: String) -> R<()> {
    preset_for(&id)
}

#[tauri::command]
fn create_shortcut(id: String) -> R<()> {
    #[cfg(windows)]
    {
        let inst = instances::get(&id)?;
        let exe = std::env::current_exe()?;
        let desktop = dirs::desktop_dir().ok_or_else(|| anyhow::anyhow!("Рабочий стол не найден"))?;
        let safe: String = inst.name.chars().filter(|c| !"\\/:*?\"<>|".contains(*c)).collect();
        let lnk = desktop.join(format!("{safe}.lnk"));
        crate::winapi::create_shortcut(&lnk, &exe, &format!("--launch {id}")).map_err(|_| anyhow::anyhow!("Не удалось создать ярлык"))?;
        Ok(())
    }
    #[cfg(not(windows))]
    {
        let _ = id;
        Err(anyhow::anyhow!("Ярлыки пока поддерживаются только в Windows").into())
    }
}

#[tauri::command]
async fn list_content(id: String, kind: String) -> R<Vec<instances::ContentFile>> {
    core_content::list_content(&id, &kind).await
}

#[tauri::command]
async fn toggle_content(id: String, kind: String, file: String, enable: bool) -> R<()> {
    core_content::toggle_content(&id, &kind, &file, enable).await
}

#[tauri::command]
async fn remove_content(id: String, kind: String, file: String) -> R<()> {
    core_content::remove_content(&id, &kind, &file).await
}

#[tauri::command]
async fn add_content_file(id: String, kind: String, path: String) -> R<String> {
    core_content::add_content_file(&id, &kind, &path).await
}

#[tauri::command]
fn list_screenshots(id: String) -> Vec<String> {
    instances::list_screenshots(&id)
}

#[tauri::command]
fn read_options(id: String) -> String {
    std::fs::read_to_string(instances::game_dir(&id).join("options.txt")).unwrap_or_default()
}

// ---------- install / launch ----------

#[tauri::command]
async fn install_instance(_app: AppHandle, id: String) -> R<()> {
    // the core installs the instance right after it is created (progress arrives as `install:<id>`)
    core_instances::wait_installed(&id).await
}

#[tauri::command]
async fn repair_instance(app: AppHandle, id: String) -> R<()> {
    core_packs::repair_instance(&app, &id).await
}

#[tauri::command]
async fn launch_instance(app: AppHandle, id: String, account_id: Option<String>) -> R<()> {
    let mut st = settings::load();
    st.last_instance = id.clone();
    let _ = settings::save(&st);
    let minimize = st.minimize_on_launch;
    let app2 = app.clone();
    tauri::async_runtime::spawn(async move {
        let pid = format!("launch:{id}");
        match core_instances::launch(&app2, &id, account_id).await {
            Ok(()) => {
                if minimize {
                    if let Some(w) = app2.get_webview_window("main") {
                        let _ = w.hide();
                        #[cfg(windows)]
                        crate::winapi::webview_suspend(&w, true);
                    }
                }
            }
            Err(e) => {
                applog(&format!("launch {id} failed: {e}"));
                net::emit_progress(&app2, &pid, "Ошибка", 0.0, 0.0, true, Some(e.0));
                if let Some(w) = app2.get_webview_window("main") {
                    let _ = w.show();
                }
            }
        }
    });
    Ok(())
}

#[tauri::command]
fn cancel_launch(_id: String) -> R<()> {
    Ok(())
}

#[tauri::command]
async fn kill_instance(id: String) -> R<()> {
    core_instances::kill(&id).await
}

#[tauri::command]
async fn running_instances() -> R<Vec<String>> {
    core_instances::running().await
}

#[tauri::command]
fn diagnostics(id: String, only_cmd: bool) -> String {
    launch::diagnostics(&id, only_cmd)
}

#[tauri::command]
async fn sunshine_status() -> Option<sunshine::Status> {
    sunshine::ping().await
}

#[tauri::command]
async fn project_icons(ids: Vec<String>) -> R<std::collections::HashMap<String, String>> {
    modrinth::icons(ids).await
}

#[tauri::command]
fn game_log(id: String) -> Vec<String> {
    // spec 3.4: the game writes its own log file; the interface only reads the tail when the console is open
    use std::io::{Read, Seek, SeekFrom};
    let path = instances::game_dir(&id).join("logs").join("latest.log");
    let Ok(mut f) = std::fs::File::open(path) else { return vec![] };
    let len = f.metadata().map(|m| m.len()).unwrap_or(0);
    let start = len.saturating_sub(512 * 1024);
    let _ = f.seek(SeekFrom::Start(start));
    let mut buf = Vec::new();
    let _ = f.read_to_end(&mut buf);
    let text = String::from_utf8_lossy(&buf);
    let mut lines: Vec<String> = text.lines().map(String::from).collect();
    if start > 0 && !lines.is_empty() {
        lines.remove(0); // the first line is cut in the middle
    }
    let n = lines.len();
    lines.split_off(n.saturating_sub(5000))
}

#[tauri::command]
fn apply_crash_fix(id: String, action: String) -> R<()> {
    let mut inst = instances::get(&id)?;
    if let Some(mb) = action.strip_prefix("ram:") {
        inst.ram_mb = mb.parse().unwrap_or(6144);
        instances::save(&inst)?;
    }
    Ok(())
}

// ---------- accounts ----------

#[tauri::command]
async fn list_accounts() -> R<Vec<auth::Account>> {
    core::list_accounts().await
}

#[tauri::command]
async fn login_microsoft(app: AppHandle) -> R<auth::Account> {
    let r = core::login_microsoft(&app).await;
    if let Err(e) = &r {
        applog(&format!("ms login failed: {e}"));
    }
    r
}

#[tauri::command]
async fn check_ms_client(client_id: String) -> auth::Check {
    auth::check_client_id(&client_id).await
}

#[tauri::command]
async fn check_discord(app_id: String) -> rpc::DiscordCheck {
    tauri::async_runtime::spawn_blocking(move || rpc::check(&app_id))
        .await
        .unwrap_or(rpc::DiscordCheck { ok: false, running: false, message: "internal error".into() })
}

#[tauri::command]
fn cancel_login() {
    core::cancel_login();
}

#[tauri::command]
fn add_offline_account(name: String) -> R<auth::Account> {
    auth::add_offline(&name, settings::load().allow_offline_unlicensed)
}

#[tauri::command]
async fn remove_account(id: String) -> R<()> {
    core::remove_account(&id).await
}

// ---------- java ----------

#[tauri::command]
async fn detect_java() -> R<Vec<java::JavaInfo>> {
    core_java::detect().await
}

#[tauri::command]
async fn validate_java(path: String) -> R<java::JavaInfo> {
    core_java::validate(&path).await
}

// ---------- modrinth ----------

#[tauri::command]
async fn mr_search(
    query: String,
    project_type: String,
    game_version: Option<String>,
    loader: Option<String>,
    category: Option<String>,
    sort: String,
    offset: u32,
) -> R<Value> {
    modrinth::search(&modrinth::SearchParams { query, project_type, game_version, loader, category, sort, offset, limit: 20 }).await
}

#[tauri::command]
async fn mr_project(id: String) -> R<Value> {
    modrinth::project(&id).await
}

#[tauri::command]
async fn mr_versions(id: String, loaders: Vec<String>, game_versions: Vec<String>) -> R<Vec<Value>> {
    modrinth::project_versions(&id, &loaders, &game_versions).await
}

#[tauri::command]
async fn mr_install(app: AppHandle, instance_id: String, project_id: String, version_id: Option<String>, project_type: String) -> R<Vec<String>> {
    core_content::install_project(&app, &instance_id, &project_id, version_id, &project_type).await
}

#[tauri::command]
async fn mr_check_updates(instance_id: String) -> R<Vec<modrinth::UpdateInfo>> {
    core_content::check_updates(&instance_id).await
}

#[tauri::command]
async fn mr_update_all(app: AppHandle, instance_id: String) -> R<usize> {
    let bid = instance_id.clone();
    let _ = tauri::async_runtime::spawn_blocking(move || backups::create(&bid)).await; // safety copy first
    core_content::update_all(&app, &instance_id).await
}

// ---------- modpacks ----------

async fn create_from_mrpack(app: &AppHandle, path: &std::path::Path, pid: &str) -> R<Instance> {
    let st = settings::load();
    let (meta, _) = modrinth::read_mrpack_meta(path)?;
    let loader_version = if meta.loader_version.is_empty() && meta.loader != "vanilla" {
        loaders::list_loader_versions(&meta.loader, &meta.mc).await?.into_iter().next().map(|v| v.version).unwrap_or_default()
    } else {
        meta.loader_version.clone()
    };
    let id = instances::unique_id(&meta.name);
    let inst = Instance {
        id: id.clone(),
        name: meta.name,
        mc_version: meta.mc,
        loader: meta.loader,
        loader_version,
        account_id: st.last_account.clone(),
        hud_enabled: false,
        created_at: instances::now(),
        ..Default::default()
    };
    instances::save(&inst)?;
    if st.apply_preset {
        preset_for(&id)?;
    }
    modrinth::apply_mrpack(app, pid, &inst, path, false, st.downloads_threads as usize).await?;
    Ok(inst)
}

#[tauri::command]
async fn import_mrpack(app: AppHandle, path: String) -> R<Instance> {
    core_packs::import_mrpack(&app, &path).await
}

#[tauri::command]
async fn install_modpack(app: AppHandle, project_id: String, version_id: Option<String>) -> R<Instance> {
    core_packs::install_modpack(&app, &project_id, version_id).await
}

#[tauri::command]
async fn update_modpack(app: AppHandle, id: String) -> R<bool> {
    let mut inst = instances::get(&id)?;
    if inst.modpack_project.is_empty() {
        return Err(anyhow::anyhow!("Эта сборка не из модпака").into());
    }
    let latest = modrinth::project_versions(&inst.modpack_project, &[], &[]).await?.into_iter().next().ok_or_else(|| anyhow::anyhow!("Нет версий"))?;
    let vid = latest["id"].as_str().unwrap_or("").to_string();
    if vid == inst.modpack_version {
        return Ok(false);
    }
    let file = latest["files"].as_array().and_then(|f| f.first()).cloned().ok_or_else(|| anyhow::anyhow!("Нет файла"))?;
    let tmp = paths::cache_dir().join(format!("{}.mrpack", inst.modpack_project));
    net::download_file(file["url"].as_str().unwrap_or(""), &tmp, &net::Hash::None, None).await?;
    let bid = id.clone();
    let _ = tauri::async_runtime::spawn_blocking(move || backups::create(&bid)).await; // safety copy first
    let st = settings::load();
    let pid = format!("modpack:update:{id}");
    modrinth::apply_mrpack(&app, &pid, &inst, &tmp, true, st.downloads_threads as usize).await?;
    inst.modpack_version = vid;
    instances::save(&inst)?;
    let _ = std::fs::remove_file(&tmp);
    net::emit_progress(&app, &pid, "Модпак обновлён", 100.0, 0.0, true, None);
    Ok(true)
}

#[tauri::command]
async fn export_mrpack(id: String, dest: String) -> R<()> {
    core_packs::export_mrpack(&id, &dest).await
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct CfResult {
    instance: Instance,
    missing: usize,
}

#[tauri::command]
async fn import_curseforge(app: AppHandle, path: String) -> R<CfResult> {
    let st = settings::load();
    let p = std::path::PathBuf::from(&path);
    let meta = modrinth::read_cf_meta(&p)?;
    let loader = match meta.loader.as_str() {
        "fabric" | "quilt" | "forge" | "neoforge" => meta.loader.clone(),
        _ => "vanilla".to_string(),
    };
    let id = instances::unique_id(&meta.name);
    let inst = Instance {
        id: id.clone(),
        name: meta.name.clone(),
        mc_version: meta.mc.clone(),
        loader,
        loader_version: meta.loader_version.clone(),
        account_id: st.last_account.clone(),
        created_at: instances::now(),
        ..Default::default()
    };
    instances::save(&inst)?;
    if st.apply_preset {
        preset_for(&id)?;
    }
    let pid = "modpack:cf".to_string();
    let missing = modrinth::apply_cf(&app, &pid, &inst, &p, &meta, st.downloads_threads as usize).await?;
    net::emit_progress(&app, &pid, "Готово", 100.0, 0.0, true, None);
    Ok(CfResult { instance: inst, missing })
}

// ---------- skins ----------

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SkinData {
    data_url: String,
    slim: bool,
}

fn png_data_url(b: &[u8]) -> String {
    use base64::Engine;
    format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(b))
}

#[tauri::command]
async fn skin_by_name(name: String) -> R<SkinData> {
    let (b, slim) = skins::fetch_by_name(name.trim()).await?;
    Ok(SkinData { data_url: png_data_url(&b), slim })
}

#[tauri::command]
async fn skin_of_account(id: String) -> R<SkinData> {
    let uuid = core_skins::uuid_of(&id).await?;
    let (b, slim) = skins::fetch_by_uuid(&uuid).await?;
    Ok(SkinData { data_url: png_data_url(&b), slim })
}

#[tauri::command]
fn skin_library() -> Vec<skins::SavedSkin> {
    skins::list_library()
}

fn decode_png(data_url: &str) -> R<Vec<u8>> {
    use base64::Engine;
    let b64 = data_url.split_once(',').map(|x| x.1).unwrap_or(data_url);
    Ok(base64::engine::general_purpose::STANDARD.decode(b64)?)
}

#[tauri::command]
fn skin_save(name: String, data_url: String, slim: bool) -> R<()> {
    skins::save_to_library(&name, &decode_png(&data_url)?, slim)
}

#[tauri::command]
fn skin_delete(name: String) -> R<()> {
    skins::delete_from_library(&name)
}

#[tauri::command]
async fn skin_upload(account_id: String, data_url: String, slim: bool) -> R<()> {
    let png = decode_png(&data_url)?;
    skins::check_png(&png)?;
    core_skins::upload(&account_id, png, slim).await
}

// ---------- startup / tray ----------

struct Startup(std::sync::Mutex<Option<String>>);

#[tauri::command]
fn take_startup_launch(state: tauri::State<Startup>) -> Option<String> {
    state.0.lock().unwrap().take()
}

fn build_tray_menu(app: &AppHandle) -> tauri::Result<Menu<tauri::Wry>> {
    let menu = Menu::new(app)?;
    menu.append(&MenuItem::with_id(app, "show", "Открыть лаунчер", true, None::<&str>)?)?;
    let recent: Vec<Instance> = instances::list().into_iter().take(5).collect();
    if !recent.is_empty() {
        menu.append(&PredefinedMenuItem::separator(app)?)?;
        for i in recent {
            menu.append(&MenuItem::with_id(app, format!("launch:{}", i.id), format!("Играть: {}", i.name), true, None::<&str>)?)?;
        }
    }
    menu.append(&PredefinedMenuItem::separator(app)?)?;
    menu.append(&MenuItem::with_id(app, "quit", "Выход", true, None::<&str>)?)?;
    Ok(menu)
}

#[tauri::command]
fn rebuild_tray(app: AppHandle) {
    if let Some(t) = app.tray_by_id("main") {
        if let Ok(m) = build_tray_menu(&app) {
            let _ = t.set_menu(Some(m));
        }
    }
}

fn show_main(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.show();
        let _ = w.unminimize();
        let _ = w.set_focus();
    }
}

#[tauri::command]
fn window_hide(window: WebviewWindow) {
    let _ = window.hide();
}

#[tauri::command]
fn window_show(app: AppHandle) {
    show_main(&app);
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    theseus::install_crypto_provider(); // TLS provider the core needs before any connection
    let _ = paths::ensure_dirs();
    let launch_arg: Option<String> = {
        let a: Vec<String> = std::env::args().collect();
        a.iter().position(|x| x == "--launch").and_then(|i| a.get(i + 1).cloned())
    };
    rpc::start();
    media::start();
    rpc::set(rpc::State::Launcher);
    #[cfg(debug_assertions)]
    let selftest: Option<(String, String)> = {
        let a: Vec<String> = std::env::args().collect();
        a.iter().position(|x| x == "--selftest").and_then(|i| Some((a.get(i + 1)?.clone(), a.get(i + 2)?.clone())))
    };

    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_window_state::Builder::default().build())
        .manage(Startup(std::sync::Mutex::new(launch_arg)))
        .setup(move |app| {
            let handle = app.handle().clone();
            // the core (Modrinth App library) starts in the background; commands wait for it
            tauri::async_runtime::spawn(async {
                if let Err(e) = core::init().await {
                    applog(&format!("core init failed: {e}"));
                }
            });
            #[cfg(debug_assertions)]
            if let Some((mc, loader)) = selftest.clone() {
                let h = handle.clone();
                tauri::async_runtime::spawn(async move {
                    let code = selftest_run(h.clone(), mc, loader).await;
                    std::process::exit(code); // the core keeps background tasks alive: leave at once
                    h.exit(code);
                });
            }
            let menu = build_tray_menu(&handle)?;
            let icon = app.default_window_icon().cloned();
            let mut tb = TrayIconBuilder::with_id("main").menu(&menu).tooltip("Nocturnum Launcher").show_menu_on_left_click(false);
            if let Some(i) = icon {
                tb = tb.icon(i);
            }
            tb.on_menu_event(|app, ev| {
                let id = ev.id().as_ref().to_string();
                match id.as_str() {
                    "show" => show_main(app),
                    "quit" => app.exit(0),
                    other => {
                        if let Some(inst) = other.strip_prefix("launch:") {
                            show_main(app);
                            let _ = app.emit("tray-launch", inst.to_string());
                        }
                    }
                }
            })
            .on_tray_icon_event(|tray, ev| {
                if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = ev {
                    show_main(tray.app_handle());
                }
            })
            .build(app)?;
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_settings,
            gallery_list,
            list_backups,
            build_licenses,
            create_backup,
            restore_backup,
            screenshot_thumb,
            screenshot_full,
            delete_screenshot,
            save_settings,
            system_info,
            hardware_info,
            set_root_dir,
            get_launcher_log,
            open_path,
            read_file_data_url,
            write_png,
            mc_versions,
            loader_versions,
            hud_status,
            list_instances,
            create_instance,
            update_instance,
            delete_instance,
            duplicate_instance,
            open_instance_folder,
            apply_preset_now,
            create_shortcut,
            list_content,
            toggle_content,
            remove_content,
            add_content_file,
            list_screenshots,
            read_options,
            install_instance,
            repair_instance,
            launch_instance,
            kill_instance,
            project_icons,
            sunshine_status,
            diagnostics,
            cancel_launch,
            running_instances,
            game_log,
            apply_crash_fix,
            list_accounts,
            login_microsoft,
            cancel_login,
            check_ms_client,
            check_discord,
            add_offline_account,
            remove_account,
            detect_java,
            validate_java,
            mr_search,
            mr_project,
            mr_versions,
            mr_install,
            mr_check_updates,
            mr_update_all,
            import_mrpack,
            install_modpack,
            update_modpack,
            export_mrpack,
            import_curseforge,
            skin_by_name,
            skin_of_account,
            skin_library,
            skin_save,
            skin_delete,
            skin_upload,
            take_startup_launch,
            rebuild_tray,
            window_hide,
            window_show,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Nocturnum Launcher");
}

#[cfg(debug_assertions)]
async fn selftest_run(app: AppHandle, mc: String, loader: String) -> i32 {
    use std::io::Write as _;
    macro_rules! say { ($($a:tt)*) => {{ println!($($a)*); let _ = std::io::stdout().flush(); }} }
    let lv = match loaders::list_loader_versions(&loader, &mc).await {
        Ok(v) => v.into_iter().find(|v| v.stable).map(|v| v.version).unwrap_or_default(),
        Err(e) => { say!("SELFTEST FAIL loader versions: {}", e); return 1; }
    };
    say!("loader version: {lv}");
    if std::env::var("NOCTURNUM_HOME").map(|v| v.trim().is_empty()).unwrap_or(true) {
        say!("SELFTEST FAIL: set NOCTURNUM_HOME to an empty folder first (the test uses its own database)");
        return 1;
    }
    // the core starts in the background; the test account exists in debug builds only
    if let Err(e) = theseus::State::get().await {
        say!("SELFTEST FAIL core init: {e}");
        return 1;
    }
    if let Err(e) = theseus::minecraft_auth::add_test_account("Selftest", uuid::Uuid::new_v3(&uuid::Uuid::NAMESPACE_OID, b"OfflinePlayer:Selftest")).await {
        say!("SELFTEST FAIL test account: {e}");
        return 1;
    }
    let hud_on = std::env::args().any(|a| a == "hud");
    // NOCTURNUM_REUSE=1: launch the instance a previous run left behind (skips the long install)
    let reuse = if std::env::var("NOCTURNUM_REUSE").is_ok() {
        match core_instances::list_instances().await {
            Ok(v) => v.into_iter().find(|i| i.name == format!("test {mc} {loader}")),
            Err(_) => None,
        }
    } else { None };
    let inst = match if let Some(i) = reuse { Ok(i) } else { create_instance(app.clone(), NewInstance { name: format!("test {mc} {loader}"), mc_version: mc.clone(), loader: loader.clone(), loader_version: lv, hud_enabled: hud_on, account_id: "selftest".into() }).await } {
        Ok(i) => i,
        Err(e) => { say!("SELFTEST FAIL create: {}", e); return 1; }
    };
    say!("instance {}", inst.id);
    if let Some(b) = media::bridge() { say!("MEDIA {} {}", b.port, b.token); }
    if let Err(e) = core_instances::wait_installed(&inst.id).await {
        say!("SELFTEST FAIL install: {e}");
        return 1;
    }
    say!("installed");
    if hud_on {
        // the mod set is installed in the background after the game files: wait for it (HUD jar and Fabric API)
        let dir = theseus::instance::get_full_path(&inst.id).await.ok();
        let t0 = std::time::Instant::now();
        let (mut last, mut stable_since) = (0usize, std::time::Instant::now());
        loop {
            let n = dir.as_ref().and_then(|d| std::fs::read_dir(d.join("mods")).ok()).map(|rd| rd.flatten().filter(|e| e.file_name().to_string_lossy().ends_with(".jar")).count()).unwrap_or(0);
            if n != last { last = n; stable_since = std::time::Instant::now(); }
            // done when the number of jars has not changed for a minute (and at least the HUD and Fabric API are there)
            if last >= 3 && stable_since.elapsed().as_secs() > 60 { break; }
            if t0.elapsed().as_secs() > 900 { break; }
            tokio::time::sleep(std::time::Duration::from_secs(2)).await;
        }
        let mods: Vec<String> = dir.as_ref().and_then(|d| std::fs::read_dir(d.join("mods")).ok()).map(|rd| rd.flatten().map(|e| e.file_name().to_string_lossy().to_string()).collect()).unwrap_or_default();
        say!("mods ({}): {}", mods.len(), mods.join(", "));
    }
    let id = inst.id.clone();
    let app2 = app.clone();
    let finished = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
    let fin2 = finished.clone();
    tauri::async_runtime::spawn(async move {
        if let Err(e) = core_instances::launch(&app2, &id, None).await {
            println!("LAUNCH ERROR: {}", e);
        }
        fin2.store(true, std::sync::atomic::Ordering::SeqCst);
    });
    let log_path = match theseus::instance::get_full_path(&inst.id).await {
        Ok(p) => { say!("log dir {}", p.display()); Some(p.join("logs").join("latest.log")) }
        Err(e) => { say!("SELFTEST: no instance path: {e}"); None }
    };
    // wait for game window creation signs in log or exit
    let start = std::time::Instant::now();
    let mut printed = 0usize;
    let mut joined_at: Option<std::time::Instant> = None;
    loop {
        tokio::time::sleep(std::time::Duration::from_millis(1000)).await;
        let text = log_path.as_ref().and_then(|p| std::fs::read(p).ok()).map(|b| String::from_utf8_lossy(&b).to_string()).unwrap_or_default();
        let log: Vec<&str> = text.lines().collect();
        while printed < log.len() { say!("GAME| {}", log[printed]); printed += 1; }
        let running = core_instances::running().await.map(|v| v.contains(&inst.id)).unwrap_or(false);
        let joined = log.iter().any(|l| l.contains("Backend library") || l.contains("Reloading ResourceManager") || l.contains("Narrator library") || l.contains("OpenAL initialized") || l.contains("Sound engine started"));
        let hold = std::env::var("NOCTURNUM_HOLD").ok().and_then(|v| v.parse::<u64>().ok()).unwrap_or(0);
        if joined && joined_at.is_none() { joined_at = Some(std::time::Instant::now()); }
        if joined && hold > 0 && joined_at.map(|t| t.elapsed().as_secs() < hold).unwrap_or(true) { continue; }
        if joined { say!("SELFTEST OK: game reached resource loading"); say!("killing"); let k = tokio::time::timeout(std::time::Duration::from_secs(20), core_instances::kill(&inst.id)).await; say!("kill result: {:?}", k.as_ref().map(|r| r.is_ok()).map_err(|_| "TIMEOUT")); tokio::time::sleep(std::time::Duration::from_secs(2)).await; return 0; }
        if !running && finished.load(std::sync::atomic::Ordering::SeqCst) { say!("SELFTEST FAIL: game not running / exited"); return 1; }
        if start.elapsed().as_secs() > 900 { say!("SELFTEST FAIL: timeout"); let _ = core_instances::kill(&inst.id).await; return 1; }
    }
}
