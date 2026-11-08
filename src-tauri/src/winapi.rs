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

/// "Settings > Display > Graphics" for one exe: GpuPreference=2 = high performance (HKCU, a single value).
pub fn set_gpu_preference(exe: &Path) {
    unsafe {
        let mut key = HKEY::default();
        let sub = HSTRING::from(r"Software\Microsoft\DirectX\UserGpuPreferences");
        if RegCreateKeyExW(HKEY_CURRENT_USER, &sub, None, PCWSTR::null(), REG_OPTION_NON_VOLATILE, KEY_SET_VALUE, None, &mut key, None).is_err() {
            return;
        }
        let name = HSTRING::from(exe.to_string_lossy().as_ref());
        let val: Vec<u8> = "GpuPreference=2;\0".encode_utf16().flat_map(|c| c.to_le_bytes()).collect();
        let _ = RegSetValueExW(key, &name, None, REG_SZ, Some(&val));
        let _ = RegCloseKey(key);
    }
}

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

pub fn kill_process(pid: u32) {
    unsafe {
        if let Ok(h) = OpenProcess(PROCESS_TERMINATE, false, pid) {
            let _ = TerminateProcess(h, 1);
            let _ = CloseHandle(h);
        }
    }
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

/// Windows 11 "efficiency mode" (EcoQoS) for one process; pid 0 = this process. on=false asks for full speed.
pub fn set_eco(pid: u32, on: bool) {
    use windows::Win32::System::Threading::{
        GetCurrentProcess, ProcessPowerThrottling, SetProcessInformation, PROCESS_POWER_THROTTLING_CURRENT_VERSION,
        PROCESS_POWER_THROTTLING_EXECUTION_SPEED, PROCESS_POWER_THROTTLING_STATE, PROCESS_SET_INFORMATION,
    };
    unsafe {
        let (h, own) = if pid == 0 { (GetCurrentProcess(), false) } else {
            match OpenProcess(PROCESS_SET_INFORMATION, false, pid) {
                Ok(h) => (h, true),
                Err(_) => return,
            }
        };
        let st = PROCESS_POWER_THROTTLING_STATE {
            Version: PROCESS_POWER_THROTTLING_CURRENT_VERSION,
            ControlMask: PROCESS_POWER_THROTTLING_EXECUTION_SPEED,
            StateMask: if on { PROCESS_POWER_THROTTLING_EXECUTION_SPEED } else { 0 },
        };
        let _ = SetProcessInformation(h, ProcessPowerThrottling, &st as *const _ as *const core::ffi::c_void, std::mem::size_of::<PROCESS_POWER_THROTTLING_STATE>() as u32);
        if own {
            let _ = CloseHandle(h);
        }
    }
}
