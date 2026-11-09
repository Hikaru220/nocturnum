//! NOCTURNUM: modpack update, crash fixes and the diagnostics report on top of the core.

use crate::core::err;
use crate::core_instances::{follow_job, get_instance, update_instance};
use crate::{error::R, modrinth, net};
use anyhow::anyhow;
use tauri::AppHandle;

/// Updates a Modrinth modpack instance to the newest version; false when it is already current.
pub async fn update_modpack(app: &AppHandle, id: &str) -> R<bool> {
    let inst = get_instance(id).await?;
    if inst.modpack_project.is_empty() {
        return Err(anyhow!("Эта сборка не из модпака").into());
    }
    let latest = modrinth::project_versions(&inst.modpack_project, &[], &[]).await?.into_iter().next().ok_or_else(|| anyhow!("Нет версий"))?;
    let vid = latest["id"].as_str().unwrap_or("").to_string();
    if vid == inst.modpack_version {
        return Ok(false);
    }
    let job = theseus::instance::update_managed_modrinth_version(id, &vid).await.map_err(err)?;
    let ev = format!("modpack:update:{id}");
    net::emit_progress(app, &ev, "Обновление модпака", 0.0, 0.0, false, None);
    follow_job(app, &ev, &job.job_id, true).await?;
    Ok(true)
}

/// `ram:<mb>` raises the memory of the instance (hint after an out-of-memory crash).
pub async fn apply_crash_fix(id: &str, action: &str) -> R<()> {
    if let Some(mb) = action.strip_prefix("ram:") {
        let mut inst = get_instance(id).await?;
        inst.ram_mb = mb.parse().unwrap_or(6144);
        update_instance(&inst).await?;
    }
    Ok(())
}

/// Performance report for a complaint; `only_cmd` returns just the launch command of the core.
pub async fn diagnostics(id: &str, only_cmd: bool) -> String {
    let inst = get_instance(id).await.ok();
    let (java, ram) = inst.as_ref().map(|i| (i.java_path.clone(), i.ram_mb)).unwrap_or_default();
    let cmd = format!(
        "Java: {}; память: {} МБ; сборка: {}",
        if java.is_empty() { "авто (ядро)" } else { &java },
        ram,
        inst.as_ref().map(|i| format!("{} {} {}", i.mc_version, i.loader, i.loader_version)).unwrap_or_default()
    );
    if only_cmd {
        return cmd;
    }
    let hw = crate::hardware::detect();
    let log = crate::game_log(id.to_string());
    let renderer = log.iter().find(|l| l.contains("OpenGL") && (l.contains("renderer") || l.contains("Renderer") || l.contains("GL_RENDERER"))).cloned().unwrap_or_default();
    let dir = crate::instances::game_dir(id);
    let opts = std::fs::read_to_string(dir.join("options.txt")).unwrap_or_default();
    let pick = |k: &str| opts.lines().find(|l| l.starts_with(k)).unwrap_or("-").to_string();
    let mods: Vec<String> = std::fs::read_dir(dir.join("mods")).map(|r| r.flatten().map(|e| e.file_name().to_string_lossy().to_string()).collect()).unwrap_or_default();
    let mut out = format!("Nocturnum {} — диагностика\n", env!("CARGO_PKG_VERSION"));
    out.push_str(&format!("Видеокарты ПК: {}\nПроцессор: {} потоков, ОЗУ {} МБ\n", hw.gpu, hw.cpu_threads, hw.ram_mb));
    out.push_str(&format!("Видеокарта игры (из лога): {}\n", if renderer.is_empty() { "нет данных, откройте F3 в игре".to_string() } else { renderer }));
    out.push_str(&format!("options.txt: {} / {} / {}\n", pick("maxFps"), pick("enableVsync"), pick("renderDistance")));
    out.push_str(&format!("Параметры запуска:\n{cmd}\n\nМоды ({}):\n{}\n", mods.len(), mods.join("\n")));
    if hw.integrated_gpu {
        out.push_str("\nПодсказка: на ПК есть встроенная видеокарта; проверьте, что игра идёт на дискретной (F3 → Display).\n");
    }
    out
}

#[derive(serde::Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct CrashHint {
    pub kind: String,
    pub message: String,
    /// machine-readable fix: "ram:<mb>" | "java:auto" | "open-mods" | "open-settings" | "repair"
    pub action: String,
}

/// Reads the crash report written after `since` and the log tail; None when the game ended normally.
pub async fn analyze_crash(id: &str, since: std::time::SystemTime) -> Option<CrashHint> {
    let dir = crate::instances::game_dir(id);
    let mut text = String::new();
    let mut crashed = false;
    if let Ok(rd) = std::fs::read_dir(dir.join("crash-reports")) {
        let mut files: Vec<_> = rd.flatten().filter(|e| e.metadata().and_then(|m| m.modified()).map(|t| t >= since).unwrap_or(false)).collect();
        files.sort_by_key(|e| e.metadata().and_then(|m| m.modified()).ok());
        if let Some(last) = files.last() {
            crashed = true;
            text = std::fs::read_to_string(last.path()).unwrap_or_default();
        }
    }
    let log = crate::game_log(id.to_string()).join("\n");
    let fatal = ["OutOfMemoryError", "Could not reserve enough space", "UnsupportedClassVersionError", "Incompatible mod set", "Mixin apply failed", "Mod resolution failed", "Unable to launch", "Could not find or load main class", "GLFW error"];
    if fatal.iter().any(|f| log.contains(f)) {
        crashed = true;
    }
    if !crashed {
        return None;
    }
    text.push('\n');
    text.push_str(&log);
    let ram = get_instance(id).await.map(|i| if i.ram_mb > 0 { i.ram_mb } else { 4096 }).unwrap_or(4096);
    let has = |s: &str| text.contains(s);
    let hint = |kind: &str, message: String, action: String| Some(CrashHint { kind: kind.into(), message, action });
    if has("OutOfMemoryError") || has("Could not reserve enough space") || has("There is insufficient memory") {
        let new = (ram + 2048).min(16384);
        return hint("memory", format!("Не хватает памяти. Увеличить до {} ГБ?", new / 1024), format!("ram:{new}"));
    }
    if has("UnsupportedClassVersionError") || has("has been compiled by a more recent version of the Java Runtime") {
        return hint("java", "Не та версия Java для этой сборки. Переустановите сборку («Проверить файлы») и запустите снова.".into(), "java:auto".into());
    }
    if has("Incompatible mod set") || has("Mixin apply failed") || has("Duplicate mod") || has("Mod resolution failed") || has("duplicate mod") {
        return hint("mods", "Конфликт или несовместимость модов. Откройте вкладку «Моды» и отключите недавно добавленные.".into(), "open-mods".into());
    }
    if (has("requires version") && has("of mod")) || has("Unmet dependency") || (has("depends on") && has("which is missing")) {
        return hint("dependency", "Не хватает мода-зависимости или его версия не подходит. Установите нужный мод из каталога.".into(), "open-mods".into());
    }
    if has("GLFW error") || has("Pixel format not accelerated") {
        return hint("graphics", "Проблема с видеодрайвером. Обновите драйвер видеокарты или включите дискретную видеокарту в настройках.".into(), "open-settings".into());
    }
    if has("Unable to launch") || has("Error: Could not find or load main class") {
        return hint("install", "Файлы сборки повреждены. Нажмите «Проверить файлы» в настройках сборки.".into(), "repair".into());
    }
    hint("unknown", "Игра завершилась с ошибкой. Скопируйте лог и покажите его в поддержке.".into(), String::new())
}
