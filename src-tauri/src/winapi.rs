//! Direct Windows API calls (registry, process, shortcuts) instead of spawning powershell / reg / taskkill.
#![cfg(windows)]

use std::path::Path;
use windows::core::{Interface, HSTRING, PCWSTR};
use windows::Win32::Foundation::CloseHandle;
use windows::Win32::System::Com::{CoCreateInstance, CoInitializeEx, IPersistFile, CLSCTX_INPROC_SERVER, COINIT_APARTMENTTHREADED};
use windows::Win32::System::Registry::{
    RegCloseKey, RegCreateKeyExW, RegGetValueW, RegSetValueExW, HKEY, HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE, KEY_SET_VALUE, REG_OPTION_NON_VOLATILE,
    REG_SZ, RRF_RT_REG_SZ,
};
use windows::Win32::System::Threading::{OpenProcess, TerminateProcess, PROCESS_TERMINATE};
use windows::Win32::UI::Shell::{IShellLinkW, ShellLink};


/// Names of the installed video adapters, read from the display-class registry keys.
pub fn gpu_names() -> Vec<String> {
    let mut out = Vec::new();
    for i in 0..8 {
        let sub = HSTRING::from(format!(r"SYSTEM\CurrentControlSet\Control\Class\{{4d36e968-e325-11ce-bfc1-08002be10318}}\{i:04}"));
        let value = HSTRING::from("DriverDesc");
        let mut buf = [0u16; 256];
        let mut size = (buf.len() * 2) as u32;
        let r = unsafe { RegGetValueW(HKEY_LOCAL_MACHINE, &sub, &value, RRF_RT_REG_SZ, None, Some(buf.as_mut_ptr() as *mut _), Some(&mut size)) };
        if r.is_ok() {
            let n = (size as usize / 2).saturating_sub(1).min(buf.len());
            let s = String::from_utf16_lossy(&buf[..n]);
            if !s.is_empty() && !s.to_lowercase().contains("basic render") && !out.contains(&s) {
                out.push(s);
            }
        }
    }
    out
}


/// Creates a .lnk file through the shell COM interface.
pub fn create_shortcut(lnk: &Path, target: &Path, args: &str) -> windows::core::Result<()> {
    unsafe {
        let _ = CoInitializeEx(None, COINIT_APARTMENTTHREADED);
        let link: IShellLinkW = CoCreateInstance(&ShellLink, None, CLSCTX_INPROC_SERVER)?;
        link.SetPath(&HSTRING::from(target.to_string_lossy().as_ref()))?;
        link.SetArguments(&HSTRING::from(args))?;
        link.SetIconLocation(&HSTRING::from(target.to_string_lossy().as_ref()), 0)?;
        let file: IPersistFile = link.cast()?;
        file.Save(&HSTRING::from(lnk.to_string_lossy().as_ref()), true)
    }
}


/// Freezes (or wakes) the hidden WebView2 so the sleeping launcher costs no CPU/RAM while the game runs.
pub fn webview_suspend(w: &tauri::WebviewWindow, on: bool) {
    let _ = w.with_webview(move |wv| unsafe {
        use webview2_com::Microsoft::Web::WebView2::Win32::ICoreWebView2_3;
        use windows_core062::Interface as _;
        let ctl = wv.controller();
        let _ = ctl.SetIsVisible(!on);
        if let Ok(core) = ctl.CoreWebView2() {
            if let Ok(c3) = core.cast::<ICoreWebView2_3>() {
                if on {
                    let h = webview2_com::TrySuspendCompletedHandler::create(Box::new(|_, _| Ok(())));
                    let _ = c3.TrySuspend(&h);
                } else {
                    let _ = c3.Resume();
                }
            }
        }
    });
}
