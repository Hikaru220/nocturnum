mod auth;
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
mod settings;
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

fn preset_for(id: &str) -> R<()> {
    let st = settings::load();
    let tier = if st.perf_tier == "auto" { hardware::detect().tier } else { st.perf_tier.clone() };
    instances::write_preset_tier(id, &tier)
}

#[tauri::command]
fn list_instances() -> Vec<Instance> {
    instances::list()
}

#[tauri::command]
fn create_instance(req: NewInstance) -> R<Instance> {
    let st = settings::load();
    let name = req.name.trim();
    if name.is_empty() {
        return Err(anyhow::anyhow!("Введите название сборки").into());
    }
    let id = instances::unique_id(name);
    let inst = Instance {
        id: id.clone(),
        name: name.into(),
        mc_version: req.mc_version,
        loader: req.loader,
        loader_version: req.loader_version,
        hud_enabled: req.hud_enabled,
        account_id: req.account_id,
        created_at: instances::now(),
        ram_mb: smart_ram(),
        ..Default::default()
    };
    instances::save(&inst)?;
    std::fs::create_dir_all(instances::game_dir(&id).join("mods"))?;
    if st.apply_preset {
        preset_for(&id)?;
    }
    Ok(inst)
}

#[tauri::command]
fn update_instance(instance: Instance) -> R<()> {
    instances::save(&instance)
}

#[tauri::command]
fn delete_instance(id: String) -> R<()> {
    if launch::RUNNING.lock().unwrap().contains_key(&id) {
        return Err(anyhow::anyhow!("Сначала закройте игру").into());
    }
    instances::delete(&id)
}

#[tauri::command]
fn duplicate_instance(id: String) -> R<Instance> {
    instances::duplicate(&id)
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
        let esc = |s: &str| s.replace('\'', "''");
        let script = format!(
            "$s=(New-Object -ComObject WScript.Shell).CreateShortcut('{}');$s.TargetPath='{}';$s.Arguments='--launch {}';$s.IconLocation='{}';$s.Save()",
            esc(&lnk.to_string_lossy()),
            esc(&exe.to_string_lossy()),
            esc(&id),
            esc(&exe.to_string_lossy())
        );
        use std::os::windows::process::CommandExt;
        let out = std::process::Command::new("powershell")
            .args(["-NoProfile", "-NonInteractive", "-Command", &script])
            .creation_flags(0x08000000)
            .output()?;
        if !out.status.success() {
            return Err(anyhow::anyhow!("Не удалось создать ярлык").into());
        }
        Ok(())
    }
    #[cfg(not(windows))]
    {
        let _ = id;
        Err(anyhow::anyhow!("Ярлыки пока поддерживаются только в Windows").into())
    }
}

#[tauri::command]
fn list_content(id: String, kind: String) -> R<Vec<instances::ContentFile>> {
    instances::list_content(&id, &kind)
}

#[tauri::command]
fn toggle_content(id: String, kind: String, file: String, enable: bool) -> R<()> {
    instances::toggle_content(&id, &kind, &file, enable)
}

#[tauri::command]
fn remove_content(id: String, kind: String, file: String) -> R<()> {
    instances::remove_content(&id, &kind, &file)
}

#[tauri::command]
fn add_content_file(id: String, kind: String, path: String) -> R<String> {
    instances::add_content_file(&id, &kind, std::path::Path::new(&path))
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
async fn install_instance(app: AppHandle, id: String) -> R<()> {
    let st = settings::load();
    let mut inst = instances::get(&id)?;
    let pid = format!("install:{id}");
    net::emit_progress(&app, &pid, "Подготовка", 0.0, 0.0, false, None);
    match launch::ensure_installed(&app, &mut inst, &st, &pid).await {
        Ok(()) => {
            net::emit_progress(&app, &pid, "Готово", 100.0, 0.0, true, None);
            Ok(())
        }
        Err(e) => {
            applog(&format!("install {id} failed: {e}"));
            net::emit_progress(&app, &pid, "Ошибка", 0.0, 0.0, true, Some(e.0.clone()));
            Err(e)
        }
    }
}

#[tauri::command]
async fn repair_instance(app: AppHandle, id: String) -> R<()> {
    let mut inst = instances::get(&id)?;
    inst.installed = false;
    instances::save(&inst)?;
    let _ = std::fs::remove_dir_all(paths::natives_dir(&inst.version_id));
    install_instance(app, id).await
}

#[tauri::command]
async fn launch_instance(app: AppHandle, id: String) -> R<()> {
    let mut st = settings::load();
    st.last_instance = id.clone();
    let _ = settings::save(&st);
    let app2 = app.clone();
    tauri::async_runtime::spawn(async move {
        let pid = format!("launch:{id}");
        let res = launch::launch(app2.clone(), id.clone()).await;
        if let Err(e) = res {
            applog(&format!("launch {id} failed: {e}"));
            net::emit_progress(&app2, &pid, "Ошибка", 0.0, 0.0, true, Some(e.0));
        }
        if let Some(w) = app2.get_webview_window("main") {
            let _ = w.show();
        }
    });
    Ok(())
}

#[tauri::command]
fn kill_instance(id: String) -> R<()> {
    launch::kill(&id)
}

#[tauri::command]
fn running_instances() -> Vec<String> {
    launch::RUNNING.lock().unwrap().keys().cloned().collect()
}

#[tauri::command]
fn game_log(id: String) -> Vec<String> {
    launch::get_log(&id)
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
fn list_accounts() -> Vec<auth::Account> {
    auth::list()
}

#[tauri::command]
async fn login_microsoft(app: AppHandle) -> R<auth::Account> {
    let st = settings::load();
    let r = auth::login_microsoft(&app, &st.ms_client_id).await;
    if let Err(e) = &r {
        applog(&format!("ms login failed: {e}"));
    }
    r
}

#[tauri::command]
fn cancel_login() {
    auth::CANCEL.store(true, std::sync::atomic::Ordering::SeqCst);
}

#[tauri::command]
fn add_offline_account(name: String) -> R<auth::Account> {
    auth::add_offline(&name)
}

#[tauri::command]
fn remove_account(id: String) -> R<()> {
    auth::remove(&id)
}

// ---------- java ----------

#[tauri::command]
async fn detect_java() -> R<Vec<java::JavaInfo>> {
    Ok(tauri::async_runtime::spawn_blocking(java::detect_all).await?)
}

#[tauri::command]
fn validate_java(path: String) -> R<java::JavaInfo> {
    java::validate(&path)
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
    let inst = instances::get(&instance_id)?;
    let pid = format!("mod:{project_id}");
    let r = modrinth::install_project(&app, &pid, &inst, &project_id, version_id, &project_type).await;
    if let Err(e) = &r {
        net::emit_progress(&app, &pid, "Ошибка", 0.0, 0.0, true, Some(e.0.clone()));
    }
    r
}

#[tauri::command]
async fn mr_check_updates(instance_id: String) -> R<Vec<modrinth::UpdateInfo>> {
    modrinth::check_updates(&instances::get(&instance_id)?).await
}

#[tauri::command]
async fn mr_update_all(app: AppHandle, instance_id: String) -> R<usize> {
    let inst = instances::get(&instance_id)?;
    modrinth::update_all(&app, &format!("update:{instance_id}"), &inst).await
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
    let pid = "modpack:import".to_string();
    let r = create_from_mrpack(&app, std::path::Path::new(&path), &pid).await;
    match &r {
        Ok(_) => net::emit_progress(&app, &pid, "Модпак установлен", 100.0, 0.0, true, None),
        Err(e) => net::emit_progress(&app, &pid, "Ошибка", 0.0, 0.0, true, Some(e.0.clone())),
    }
    r
}

#[tauri::command]
async fn install_modpack(app: AppHandle, project_id: String, version_id: Option<String>) -> R<Instance> {
    let pid = format!("modpack:{project_id}");
    net::emit_progress(&app, &pid, "Загрузка модпака", 0.0, 0.0, false, None);
    let res: R<Instance> = async {
        let v = match version_id {
            Some(v) => modrinth::version(&v).await?,
            None => modrinth::project_versions(&project_id, &[], &[]).await?.into_iter().next().ok_or_else(|| anyhow::anyhow!("У модпака нет версий"))?,
        };
        let file = v["files"]
            .as_array()
            .and_then(|f| f.iter().find(|x| x["primary"].as_bool().unwrap_or(false)).or_else(|| f.first()))
            .ok_or_else(|| anyhow::anyhow!("Нет файла модпака"))?
            .clone();
        let tmp = paths::cache_dir().join(format!("{project_id}.mrpack"));
        let hash = file["hashes"]["sha512"].as_str().map(|s| net::Hash::Sha512(s.into())).unwrap_or(net::Hash::None);
        net::download_file(file["url"].as_str().ok_or_else(|| anyhow::anyhow!("нет ссылки"))?, &tmp, &hash, None).await?;
        let mut inst = create_from_mrpack(&app, &tmp, &pid).await?;
        inst.modpack_project = project_id.clone();
        inst.modpack_version = v["id"].as_str().unwrap_or("").to_string();
        instances::save(&inst)?;
        let _ = std::fs::remove_file(&tmp);
        Ok(inst)
    }
    .await;
    match &res {
        Ok(_) => net::emit_progress(&app, &pid, "Модпак установлен", 100.0, 0.0, true, None),
        Err(e) => net::emit_progress(&app, &pid, "Ошибка", 0.0, 0.0, true, Some(e.0.clone())),
    }
    res
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
    let inst = instances::get(&id)?;
    modrinth::export_mrpack(&inst, std::path::Path::new(&dest)).await
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
    let a = auth::get(&id)?;
    if a.kind != "microsoft" {
        return Err(anyhow::anyhow!("У офлайн-профиля нет скина на сервере").into());
    }
    let (b, slim) = skins::fetch_by_uuid(&a.uuid).await?;
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
    let st = settings::load();
    auth::upload_skin(&account_id, &st.ms_client_id, png, slim).await
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
            #[cfg(debug_assertions)]
            if let Some((mc, loader)) = selftest.clone() {
                let h = handle.clone();
                tauri::async_runtime::spawn(async move {
                    let code = selftest_run(h.clone(), mc, loader).await;
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
            running_instances,
            game_log,
            apply_crash_fix,
            list_accounts,
            login_microsoft,
            cancel_login,
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
    // offline account (bypasses licence check on purpose, test only)
    let acc = auth::Account { id: "selftest".into(), kind: "offline".into(), username: "Selftest".into(), uuid: auth::offline_uuid("Selftest"), skin_url: String::new() };
    let _ = std::fs::create_dir_all(paths::root());
    std::fs::write(paths::accounts_file(), serde_json::to_vec(&serde_json::json!({"accounts": [acc]})).unwrap()).unwrap();
    let hud_on = std::env::args().any(|a| a == "hud");
    let inst = match create_instance(NewInstance { name: format!("test {mc} {loader}"), mc_version: mc.clone(), loader: loader.clone(), loader_version: lv, hud_enabled: hud_on, account_id: "selftest".into() }) {
        Ok(i) => i,
        Err(e) => { say!("SELFTEST FAIL create: {}", e); return 1; }
    };
    say!("instance {}", inst.id);
    if let Some(b) = media::bridge() { say!("MEDIA {} {}", b.port, b.token); }
    if let Ok(dbg) = std::env::var("NOCTURNUM_DEBUG") {
        if let Ok(mut i) = instances::get(&inst.id) {
            i.jvm_args = format!("-Dnocturnum.debug={dbg}");
            i.width = 1600;
            i.height = 900;
            let _ = instances::save(&i);
        }
    }
    let opts = read_options(inst.id.clone());
    say!("options.txt:
{opts}");
    let id = inst.id.clone();
    let app2 = app.clone();
    let finished = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
    let fin2 = finished.clone();
    tauri::async_runtime::spawn(async move {
        if let Err(e) = launch::launch(app2, id).await {
            println!("LAUNCH ERROR: {}", e);
        }
        fin2.store(true, std::sync::atomic::Ordering::SeqCst);
    });
    // wait for game window creation signs in log or exit
    let start = std::time::Instant::now();
    let mut printed = 0usize;
    let mut joined_at: Option<std::time::Instant> = None;
    loop {
        tokio::time::sleep(std::time::Duration::from_millis(1000)).await;
        let log = launch::get_log(&inst.id);
        while printed < log.len() { say!("GAME| {}", log[printed]); printed += 1; }
        let running = launch::RUNNING.lock().unwrap().contains_key(&inst.id);
        let joined = log.iter().any(|l| l.contains("Backend library") || l.contains("Reloading ResourceManager") || l.contains("Narrator library") || l.contains("OpenAL initialized") || l.contains("Sound engine started"));
        let hold = std::env::var("NOCTURNUM_HOLD").ok().and_then(|v| v.parse::<u64>().ok()).unwrap_or(0);
        if joined && joined_at.is_none() { joined_at = Some(std::time::Instant::now()); }
        if joined && hold > 0 && joined_at.map(|t| t.elapsed().as_secs() < hold).unwrap_or(true) { continue; }
        if joined { say!("SELFTEST OK: game reached resource loading"); let _ = launch::kill(&inst.id); tokio::time::sleep(std::time::Duration::from_secs(2)).await; return 0; }
        if !running && finished.load(std::sync::atomic::Ordering::SeqCst) { say!("SELFTEST FAIL: game not running / exited"); return 1; }
        if start.elapsed().as_secs() > 900 { say!("SELFTEST FAIL: timeout"); let _ = launch::kill(&inst.id); return 1; }
    }
}
