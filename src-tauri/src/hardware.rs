//! Hardware detection for auto-tuning (graphics tier, memory).

use serde::Serialize;

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Hardware {
    pub gpu: String,
    pub cpu_threads: usize,
    pub ram_mb: u64,
    pub tier: String,
    pub integrated_gpu: bool,
}

#[cfg(windows)]
fn gpu_names() -> Vec<String> {
    use std::os::windows::process::CommandExt;
    let out = std::process::Command::new("powershell")
        .args(["-NoProfile", "-NonInteractive", "-Command", "(Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name) -join '|'"])
        .creation_flags(0x08000000)
        .output();
    match out {
        Ok(o) => String::from_utf8_lossy(&o.stdout).trim().split('|').filter(|s| !s.is_empty()).map(String::from).collect(),
        Err(_) => vec![],
    }
}

#[cfg(not(windows))]
fn gpu_names() -> Vec<String> {
    vec![]
}

fn is_integrated(name: &str) -> bool {
    let n = name.to_lowercase();
    (n.contains("intel") && !n.contains("arc")) || n.contains("uhd") || n.contains("vega") && n.contains("graphics") || n.contains("radeon(tm) graphics") || n.contains("radeon graphics")
}

fn is_high_end(name: &str) -> bool {
    let n = name.to_lowercase();
    ["rtx", "rx 6", "rx 7", "rx 9", "arc a7", "arc b5", "arc b7"].iter().any(|k| n.contains(k)) || n.contains("gtx 16") && false || n.contains("gtx 10") && (n.contains("1070") || n.contains("1080"))
}

pub fn detect() -> Hardware {
    let mut sys = sysinfo::System::new();
    sys.refresh_memory();
    let ram_mb = sys.total_memory() / 1024 / 1024;
    let cpu_threads = std::thread::available_parallelism().map(|n| n.get()).unwrap_or(4);
    let gpus = gpu_names();
    // prefer a discrete GPU if the machine has two
    let best = gpus.iter().find(|g| !is_integrated(g)).or_else(|| gpus.first()).cloned().unwrap_or_default();
    let integrated = gpus.is_empty() || gpus.iter().all(|g| is_integrated(g));
    let tier = if integrated || ram_mb < 6000 {
        "low"
    } else if is_high_end(&best) && ram_mb >= 12000 && cpu_threads >= 8 {
        "high"
    } else {
        "mid"
    };
    Hardware { gpu: best, cpu_threads, ram_mb, tier: tier.into(), integrated_gpu: integrated }
}

/// Recommended -Xmx in MB from system RAM and number of mods (ТЗ 7.2 "Умная память").
pub fn smart_ram_mb(total_mb: u64, mods: usize) -> u32 {
    let base: u64 = if total_mb < 5000 { 2048 } else if total_mb < 7000 { 3072 } else if total_mb < 10000 { 4096 } else { 6144 };
    let want = if mods > 150 { base.max(8192) } else if mods > 60 { base.max(6144) } else { base };
    // never more than half of the system memory
    let cap = (total_mb / 2).max(1536);
    (want.min(cap) / 512 * 512) as u32
}
