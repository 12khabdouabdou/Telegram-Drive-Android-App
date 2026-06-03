use tauri::{Manager, Emitter};
use jni::objects::{JClass, JString};
use jni::JNIEnv;

#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_cameronamer_telegramdrive_AutoBackupService_onFileDiscovered(
    mut env: JNIEnv,
    _this: jni::objects::JObject,
    file_path: JString,
) {
    if let Ok(path) = env.get_string(&file_path).map(|s| s.to_string_lossy().into_owned()) {
        log::info!("JNI: AutoBackupService discovered new file: {}", path);
        
        if let Ok(guard) = crate::share_intent::APP_HANDLE.lock() {
            if let Some(app) = guard.as_ref() {
                if let Some(db_pool) = app.try_state::<crate::db::DbConnection>() {
                    let conn = db_pool.blocking_lock();
                    {
                        let query = "INSERT OR IGNORE INTO auto_backups (file_path, status, created_at) VALUES (?, 'pending', strftime('%s', 'now'))";
                        if let Ok(mut stmt) = conn.prepare(query) {
                            let _ = stmt.bind((1, path.as_str()));
                            if let Ok(_) = stmt.next() {
                                log::info!("AutoBackup: Logged new file for backup: {}", path);
                                let _ = app.emit("auto-backup-discovered", vec![path]);
                            }
                        }
                    }
                }
            }
        }
    }
}

#[derive(serde::Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct AutoBackupConfig {
    pub enabled: bool,
    pub wifi_only: bool,
    pub battery_safe: bool,
    pub night_mode: bool,
    pub destination: String,
    pub mode: String,
    pub folders: Vec<String>,
}

#[tauri::command]
pub fn cmd_toggle_auto_backup(config: AutoBackupConfig) -> Result<(), String> {
    #[cfg(target_os = "android")]
    {
        let ctx_obj = ndk_context::android_context();
        let vm = unsafe { jni::JavaVM::from_raw(ctx_obj.vm().cast()) }.map_err(|e| e.to_string())?;
        let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
        
        let result: Result<(), String> = (|| {
            let ctx = unsafe { jni::objects::JObject::from_raw(ctx_obj.context().cast()) };

            let class_loader = env.call_method(&ctx, "getClassLoader", "()Ljava/lang/ClassLoader;", &[])
                .map_err(|e| e.to_string())?.l().map_err(|e| e.to_string())?;

            let class_name = env.new_string("com.cameronamer.telegramdrive.AutoBackupService")
                .map_err(|e| e.to_string())?;
            let class_obj = env.call_method(&class_loader, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", &[jni::objects::JValue::from(&class_name)])
                .map_err(|e| e.to_string())?.l().map_err(|e| e.to_string())?;

            let j_class: jni::objects::JClass = class_obj.into();
            
            let intent_class = env.find_class("android/content/Intent").map_err(|e| e.to_string())?;
            let intent = env.new_object(intent_class, "(Landroid/content/Context;Ljava/lang/Class;)V", &[jni::objects::JValue::from(&ctx), jni::objects::JValue::from(&j_class)])
                .map_err(|e| e.to_string())?;

            // Put extras
            let ext_wifi = env.new_string("wifi_only").map_err(|e| e.to_string())?;
            let _ = env.call_method(&intent, "putExtra", "(Ljava/lang/String;Z)Landroid/content/Intent;", &[jni::objects::JValue::from(&ext_wifi), jni::objects::JValue::Bool(config.wifi_only as jni::sys::jboolean)]);
            
            let ext_battery = env.new_string("battery_safe").map_err(|e| e.to_string())?;
            let _ = env.call_method(&intent, "putExtra", "(Ljava/lang/String;Z)Landroid/content/Intent;", &[jni::objects::JValue::from(&ext_battery), jni::objects::JValue::Bool(config.battery_safe as jni::sys::jboolean)]);

            let ext_night = env.new_string("night_mode").map_err(|e| e.to_string())?;
            let _ = env.call_method(&intent, "putExtra", "(Ljava/lang/String;Z)Landroid/content/Intent;", &[jni::objects::JValue::from(&ext_night), jni::objects::JValue::Bool(config.night_mode as jni::sys::jboolean)]);

            let ext_dest = env.new_string("destination").map_err(|e| e.to_string())?;
            let dest_val = env.new_string(&config.destination).map_err(|e| e.to_string())?;
            let _ = env.call_method(&intent, "putExtra", "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;", &[jni::objects::JValue::from(&ext_dest), jni::objects::JValue::from(&dest_val)]);

            let ext_mode = env.new_string("mode").map_err(|e| e.to_string())?;
            let mode_val = env.new_string(&config.mode).map_err(|e| e.to_string())?;
            let _ = env.call_method(&intent, "putExtra", "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;", &[jni::objects::JValue::from(&ext_mode), jni::objects::JValue::from(&mode_val)]);

            // Pass folders as String array
            let string_class = env.find_class("java/lang/String").map_err(|e| e.to_string())?;
            let initial_str = env.new_string("").map_err(|e| e.to_string())?;
            let folder_array = env.new_object_array(config.folders.len() as i32, string_class, initial_str).map_err(|e| e.to_string())?;
            for (i, folder) in config.folders.iter().enumerate() {
                let j_folder = env.new_string(folder).map_err(|e| e.to_string())?;
                env.set_object_array_element(&folder_array, i as i32, j_folder).map_err(|e| e.to_string())?;
            }
            let ext_folders = env.new_string("folders").map_err(|e| e.to_string())?;
            let _ = env.call_method(&intent, "putExtra", "(Ljava/lang/String;[Ljava/lang/String;)Landroid/content/Intent;", &[jni::objects::JValue::from(&ext_folders), jni::objects::JValue::from(&folder_array)]);

            if config.enabled {
                let _ = env.call_method(&ctx, "startForegroundService", "(Landroid/content/Intent;)Landroid/content/ComponentName;", &[jni::objects::JValue::from(&intent)]);
            } else {
                let _ = env.call_method(&ctx, "stopService", "(Landroid/content/Intent;)Z", &[jni::objects::JValue::from(&intent)]);
            }
            Ok(())
        })();

        if env.exception_check().unwrap_or(false) {
            let _ = env.exception_clear();
        }
        
        return result;
    }
    
    #[cfg(not(target_os = "android"))]
    Ok(())
}

pub fn start_auto_backup_processor(app_handle: tauri::AppHandle) {
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(tokio::time::Duration::from_secs(10)).await;
            
            let mut pending_files = Vec::new();
            
            // Read pending
            if let Some(db_pool) = app_handle.try_state::<crate::db::DbConnection>() {
                let result: Result<Vec<(i64, String)>, String> = {
                    let conn = db_pool.lock().await;
                    let mut stmt = conn.prepare("SELECT id, file_path FROM auto_backups WHERE status = 'pending' LIMIT 5").map_err(|e| e.to_string())?;
                    let mut files = Vec::new();
                    while let Ok(sqlite::State::Row) = stmt.next() {
                        let id = stmt.read::<i64, _>(0).unwrap_or(0);
                        let path = stmt.read::<String, _>(1).unwrap_or_default();
                        files.push((id, path));
                    }
                    Ok(files)
                };
                
                if let Ok(files) = result {
                    pending_files = files;
                }
            }

            if pending_files.is_empty() {
                continue;
            }

            for (id, path) in pending_files {
                let tid = format!("autobackup_{}", id);
                let state = app_handle.state::<crate::TelegramState>();
                let bw_state = app_handle.state::<crate::bandwidth::BandwidthManager>();
                let net_config = app_handle.state::<std::sync::Arc<crate::vpn_optimizer::NetworkConfig>>();
                
                log::info!("AutoBackup: processing {}", path);
                let upload_res = crate::commands::fs::cmd_upload_file(
                    path.clone(),
                    None,
                    Some(tid),
                    app_handle.clone(),
                    state,
                    bw_state,
                    net_config,
                ).await;

                let new_status = if upload_res.is_ok() { "completed" } else { "failed" };
                log::info!("AutoBackup: finished {}: {}", path, new_status);

                if let Some(db_pool) = app_handle.try_state::<crate::db::DbConnection>() {
                    let conn = db_pool.lock().await;
                    if let Ok(mut stmt) = conn.prepare("UPDATE auto_backups SET status = ? WHERE id = ?") {
                        let _ = stmt.bind((1, new_status));
                        let _ = stmt.bind((2, id));
                        let _ = stmt.next();
                    }
                }
            }
        }
    });
}
