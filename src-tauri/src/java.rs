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


#[cfg(not(windows))]
fn no_window(_cmd: &mut Command) {}











