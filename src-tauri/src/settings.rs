use crate::{error::R, paths};
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(default, rename_all = "camelCase")]
pub struct Settings {
    pub language: String,
    pub theme: String,
    pub accent: String,
    pub ui_scale: u32,
    pub default_ram_mb: u32,
    pub jvm_args: String,
    pub custom_java: String,
    pub window_width: u32,
    pub window_height: u32,
    pub fullscreen: bool,
    pub discrete_gpu: bool,
    pub discord_rpc: bool,
    pub rpc_hide_server: bool,
    pub rpc_hide_instance: bool,
    pub apply_preset: bool,
    pub install_sodium: bool,
    pub install_hud: bool,
    pub minimize_on_launch: bool,
    pub downloads_threads: u32,
    pub first_run_done: bool,
    pub last_instance: String,
    pub last_account: String,
    pub background: String,
    pub reduce_motion: bool,
    pub ms_client_id: String,
    pub discord_app_id: String,
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            language: "ru".into(),
            theme: "dark".into(),
            accent: "#8B7CFF".into(),
            ui_scale: 100,
            default_ram_mb: 4096,
            jvm_args: String::new(),
            custom_java: String::new(),
            window_width: 854,
            window_height: 480,
            fullscreen: false,
            discrete_gpu: false,
            discord_rpc: true,
            rpc_hide_server: false,
            rpc_hide_instance: false,
            apply_preset: true,
            install_sodium: true,
            install_hud: true,
            minimize_on_launch: true,
            downloads_threads: 12,
            first_run_done: false,
            last_instance: String::new(),
            last_account: String::new(),
            background: String::new(),
            reduce_motion: false,
            ms_client_id: String::new(),
            discord_app_id: String::new(),
        }
    }
}

pub fn load() -> Settings {
    std::fs::read_to_string(paths::settings_file())
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_default()
}

pub fn save(s: &Settings) -> R<()> {
    std::fs::create_dir_all(paths::root())?;
    std::fs::write(paths::settings_file(), serde_json::to_vec_pretty(s)?)?;
    Ok(())
}
