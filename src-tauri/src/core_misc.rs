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
