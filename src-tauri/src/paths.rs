use std::path::PathBuf;

/// Bootstrap folder; only holds `root.txt` (custom data root pointer).
pub fn bootstrap_dir() -> PathBuf {
    dirs::data_dir().unwrap_or_else(|| PathBuf::from(".")).join("Nocturnum")
}

/// Portable mode: if a file named `portable` lies next to the exe, all data stays there.
pub fn root() -> PathBuf {
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            if dir.join("portable").exists() {
                return dir.join("data");
            }
        }
    }
    if let Ok(custom) = std::env::var("NOCTURNUM_HOME") {
        if !custom.trim().is_empty() {
            return PathBuf::from(custom);
        }
    }
    let base = bootstrap_dir();
    if let Ok(s) = std::fs::read_to_string(base.join("root.txt")) {
        if !s.trim().is_empty() {
            return PathBuf::from(s.trim());
        }
    }
    base
}

pub fn is_portable() -> bool {
    std::env::current_exe()
        .ok()
        .and_then(|e| e.parent().map(|d| d.join("portable").exists()))
        .unwrap_or(false)
}

pub fn settings_file() -> PathBuf { root().join("settings.json") }
pub fn accounts_file() -> PathBuf { root().join("accounts.json") }
pub fn instances_dir() -> PathBuf { root().join("instances") }
pub fn instance_dir(id: &str) -> PathBuf { instances_dir().join(id) }
pub fn libraries_dir() -> PathBuf { root().join("meta").join("libraries") }
pub fn assets_dir() -> PathBuf { root().join("meta").join("assets") }
pub fn versions_dir() -> PathBuf { root().join("meta").join("versions") }
pub fn natives_dir(id: &str) -> PathBuf { root().join("meta").join("natives").join(id) }
pub fn java_dir() -> PathBuf { root().join("java") }
pub fn skins_dir() -> PathBuf { root().join("skins") }
pub fn logs_dir() -> PathBuf { root().join("logs") }
pub fn cache_dir() -> PathBuf { root().join("cache") }

pub fn ensure_dirs() -> std::io::Result<()> {
    for d in [instances_dir(), libraries_dir(), assets_dir(), versions_dir(), java_dir(), skins_dir(), logs_dir(), cache_dir()] {
        std::fs::create_dir_all(d)?;
    }
    Ok(())
}

/// Maven coordinate "group:artifact:version[:classifier][@ext]" -> relative path.
pub fn maven_path(name: &str) -> String {
    let (coord, ext) = match name.split_once('@') {
        Some((c, e)) => (c, e),
        None => (name, "jar"),
    };
    let parts: Vec<&str> = coord.split(':').collect();
    let group = parts[0].replace('.', "/");
    let artifact = parts.get(1).copied().unwrap_or("");
    let version = parts.get(2).copied().unwrap_or("");
    let classifier = parts.get(3).map(|c| format!("-{}", c)).unwrap_or_default();
    format!("{}/{}/{}/{}-{}{}.{}", group, artifact, version, artifact, version, classifier, ext)
}
