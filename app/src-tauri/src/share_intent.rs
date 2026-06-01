use tauri::{AppHandle, Manager};
use std::ffi::CStr;
use jni::objects::{JClass, JObjectArray, JString};
use jni::JNIEnv;

// Global AppHandle storage so JNI can emit events
lazy_static::lazy_static! {
    pub static ref APP_HANDLE: std::sync::Mutex<Option<AppHandle>> = std::sync::Mutex::new(None);
    pub static ref PENDING_SHARED_FILES: std::sync::Mutex<Vec<String>> = std::sync::Mutex::new(Vec::new());
}

#[tauri::command]
pub fn cmd_get_pending_shared_files() -> Vec<String> {
    if let Ok(mut pending) = PENDING_SHARED_FILES.lock() {
        let files = pending.clone();
        pending.clear();
        return files;
    }
    Vec::new()
}

#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_cameronamer_telegramdrive_MainActivity_onSharedFilesReceived(
    mut env: JNIEnv,
    _class: JClass,
    uris: JObjectArray,
) {
    let count = env.get_array_length(&uris).unwrap_or(0);
    let mut file_uris = Vec::new();
    
    for i in 0..count {
        if let Ok(element) = env.get_object_array_element(&uris, i) {
            let jstr: JString = element.into();
            if let Ok(rust_str) = env.get_string(&jstr) {
                file_uris.push(rust_str.to_string_lossy().into_owned());
            }
        }
    }
    
    log::info!("JNI: MainActivity received {} shared files", file_uris.len());
    
    // Store in cache for pull
    if let Ok(mut pending) = PENDING_SHARED_FILES.lock() {
        pending.extend(file_uris.clone());
    }
    
    // Also try to emit directly if the app is already open
    if let Ok(guard) = APP_HANDLE.lock() {
        if let Some(app) = guard.as_ref() {
            let _ = app.emit("incoming-shared-files", file_uris);
        } else {
            log::warn!("JNI: AppHandle not set, cached shared files for later");
        }
    }
}
