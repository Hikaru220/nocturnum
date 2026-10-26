//! Java discovery and automatic Temurin download.

use crate::{error::R, net, paths};
use anyhow::anyhow;
use serde::Serialize;
use std::path::{Path, PathBuf};
use std::process::Command;
use tauri::AppHandle;

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct JavaInfo {
    pub path: String,
    pub major: u32,
    pub version: String,
    pub managed: bool,
}

fn exe_name() -> &'static str {
    if cfg!(windows) { "javaw.exe" } else { "java" }
}
fn console_exe() -> &'static str {
    if cfg!(windows) { "java.exe" } else { "java" }
}

#[cfg(windows)]
fn no_window(cmd: &mut Command) {
    use std::os::windows::process::CommandExt;
    cmd.creation_flags(0x08000000);
}
#[cfg(not(windows))]
fn no_window(_cmd: &mut Command) {}

/// Run `java -version` and parse the major version. Returns None if the binary is unusable.
pub fn probe(java: &Path) -> Option<JavaInfo> {
    let console = if java.file_name().map(|n| n == "javaw.exe").unwrap_or(false) {
        java.with_file_name("java.exe")
    } else {
        java.to_path_buf()
    };
    let mut cmd = Command::new(&console);
    cmd.arg("-version");
    no_window(&mut cmd);
    let out = cmd.output().ok()?;
    let text = String::from_utf8_lossy(&out.stderr).to_string() + &String::from_utf8_lossy(&out.stdout);
    let re = regex::Regex::new(r#"version "([^"]+)""#).ok()?;
    let ver = re.captures(&text)?.get(1)?.as_str().to_string();
    let major = parse_major(&ver)?;
    Some(JavaInfo { path: java.to_string_lossy().to_string(), major, version: ver, managed: false })
}

fn parse_major(v: &str) -> Option<u32> {
    let mut it = v.split(|c| c == '.' || c == '_' || c == '-' || c == '+');
    let first: u32 = it.next()?.parse().ok()?;
    if first == 1 {
        it.next()?.parse().ok()
    } else {
        Some(first)
    }
}

fn candidate_roots() -> Vec<PathBuf> {
    let mut v: Vec<PathBuf> = Vec::new();
    if let Ok(h) = std::env::var("JAVA_HOME") {
        v.push(PathBuf::from(h));
    }
    if cfg!(windows) {
        for base in [
            "C:\\Program Files\\Java",
            "C:\\Program Files\\Eclipse Adoptium",
            "C:\\Program Files\\Microsoft",
            "C:\\Program Files\\Zulu",
            "C:\\Program Files\\BellSoft",
            "C:\\Program Files\\Amazon Corretto",
            "C:\\Program Files (x86)\\Java",
        ] {
            if let Ok(rd) = std::fs::read_dir(base) {
                for e in rd.flatten() {
                    v.push(e.path());
                }
            }
        }
        if let Some(home) = dirs::home_dir() {
            for sub in [".jdks", ".gradle/jdks"] {
                if let Ok(rd) = std::fs::read_dir(home.join(sub)) {
                    for e in rd.flatten() {
                        v.push(e.path());
                    }
                }
            }
        }
        if let Some(la) = dirs::data_local_dir() {
            if let Ok(rd) = std::fs::read_dir(la.join("Programs").join("Eclipse Adoptium")) {
                for e in rd.flatten() {
                    v.push(e.path());
                }
            }
        }
        if let Some(ad) = dirs::data_dir() {
            // official launcher runtimes
            if let Ok(rd) = std::fs::read_dir(ad.join(".minecraft").join("runtime")) {
                for e in rd.flatten() {
                    if let Ok(rd2) = std::fs::read_dir(e.path().join("windows-x64")) {
                        for e2 in rd2.flatten() {
                            v.push(e2.path());
                        }
                    }
                }
            }
        }
    } else {
        for base in ["/usr/lib/jvm", "/Library/Java/JavaVirtualMachines"] {
            if let Ok(rd) = std::fs::read_dir(base) {
                for e in rd.flatten() {
                    v.push(e.path());
                    v.push(e.path().join("Contents/Home"));
                }
            }
        }
    }
    v
}

fn java_in(root: &Path) -> Option<PathBuf> {
    let p = root.join("bin").join(console_exe());
    if p.exists() { Some(p) } else { None }
}

/// All usable Java installations on the machine (plus managed ones).
pub fn detect_all() -> Vec<JavaInfo> {
    let mut found: Vec<JavaInfo> = Vec::new();
    let mut seen = std::collections::HashSet::new();
    let mut push = |info: JavaInfo, found: &mut Vec<JavaInfo>| {
        if seen.insert(info.path.to_lowercase()) {
            found.push(info);
        }
    };
    // managed
    if let Ok(rd) = std::fs::read_dir(paths::java_dir()) {
        for e in rd.flatten() {
            if let Some(j) = find_java_exe(&e.path()) {
                if let Some(mut info) = probe(&j) {
                    info.managed = true;
                    push(info, &mut found);
                }
            }
        }
    }
    for r in candidate_roots() {
        if let Some(j) = java_in(&r) {
            if let Some(info) = probe(&j) {
                push(info, &mut found);
            }
        }
    }
    if let Some(info) = probe(Path::new(console_exe())) {
        push(info, &mut found);
    }
    found.sort_by_key(|j| j.major);
    found
}

fn find_java_exe(root: &Path) -> Option<PathBuf> {
    if let Some(p) = java_in(root) {
        return Some(p);
    }
    // one level deep (zip extracts to jdk-xx-jre/)
    if let Ok(rd) = std::fs::read_dir(root) {
        for e in rd.flatten() {
            if e.path().is_dir() {
                if let Some(p) = java_in(&e.path()) {
                    return Some(p);
                }
                let mac = e.path().join("Contents/Home");
                if let Some(p) = java_in(&mac) {
                    return Some(p);
                }
            }
        }
    }
    None
}

pub fn validate(path: &str) -> R<JavaInfo> {
    let p = PathBuf::from(path);
    if !p.exists() {
        return Err(anyhow!("Файл не найден: {path}").into());
    }
    probe(&p).ok_or_else(|| anyhow!("Это не рабочая Java (не удалось выполнить java -version)").into())
}

fn adoptium_os() -> &'static str {
    if cfg!(windows) { "windows" } else if cfg!(target_os = "macos") { "mac" } else { "linux" }
}
fn adoptium_arch() -> &'static str {
    if cfg!(target_arch = "aarch64") { "aarch64" } else { "x64" }
}

/// Make sure a Java with at least `major` (exact match preferred, newer accepted for old versions
/// only when the exact one is missing) is available. Downloads Temurin if needed.
pub async fn ensure(app: &AppHandle, progress_id: &str, major: u32, custom: &str) -> R<PathBuf> {
    if !custom.trim().is_empty() {
        let info = validate(custom.trim())?;
        if info.major >= major {
            return Ok(PathBuf::from(info.path));
        }
        // custom too old -> fall through to find a better one
    }
    let managed_dir = paths::java_dir().join(format!("{major}"));
    if let Some(j) = find_java_exe(&managed_dir) {
        if probe(&j).is_some() {
            return Ok(j);
        }
    }
    let all = detect_all();
    // exact match first
    if let Some(j) = all.iter().find(|j| j.major == major) {
        return Ok(PathBuf::from(&j.path));
    }
    // Old Minecraft (<=1.16, Java 8) does not run well on newer Java, so download exact.
    // For modern versions accept a newer installed Java.
    if major >= 17 {
        if let Some(j) = all.iter().find(|j| j.major > major && j.major <= major + 8) {
            return Ok(PathBuf::from(&j.path));
        }
    }
    download_temurin(app, progress_id, major).await
}

pub async fn download_temurin(app: &AppHandle, progress_id: &str, major: u32) -> R<PathBuf> {
    net::emit_progress(app, progress_id, &format!("Загрузка Java {major}"), 0.0, 0.0, false, None);
    let url = format!(
        "https://api.adoptium.net/v3/binary/latest/{major}/ga/{}/{}/jre/hotspot/normal/eclipse",
        adoptium_os(),
        adoptium_arch()
    );
    let ext = if cfg!(windows) { "zip" } else { "tar.gz" };
    let archive = paths::cache_dir().join(format!("temurin-{major}.{ext}"));
    net::download_file(&url, &archive, &net::Hash::None, None)
        .await
        .map_err(|e| e.ctx(&format!("Не удалось скачать Java {major}")))?;
    let dest = paths::java_dir().join(format!("{major}"));
    let _ = std::fs::remove_dir_all(&dest);
    std::fs::create_dir_all(&dest)?;
    let archive2 = archive.clone();
    let dest2 = dest.clone();
    tokio::task::spawn_blocking(move || -> anyhow::Result<()> {
        if cfg!(windows) {
            let f = std::fs::File::open(&archive2)?;
            let mut z = zip::ZipArchive::new(f)?;
            z.extract(&dest2)?;
        } else {
            let st = Command::new("tar").arg("-xzf").arg(&archive2).arg("-C").arg(&dest2).status()?;
            if !st.success() {
                return Err(anyhow!("tar failed"));
            }
        }
        Ok(())
    })
    .await??;
    let _ = std::fs::remove_file(&archive);
    find_java_exe(&dest).ok_or_else(|| anyhow!("Java не найдена в архиве").into())
}

/// Path of javaw/java used to launch the game (GUI subsystem on Windows).
pub fn game_exe(java_console: &Path) -> PathBuf {
    if cfg!(windows) {
        let w = java_console.with_file_name(exe_name());
        if w.exists() {
            return w;
        }
    }
    java_console.to_path_buf()
}
