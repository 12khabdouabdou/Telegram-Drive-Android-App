use tauri::{AppHandle, Manager};
use jni::objects::{JClass, JString};
use jni::JNIEnv;

#[cfg(target_os = "android")]
#[no_mangle]
pub extern "C" fn Java_com_cameronamer_telegramdrive_AutoBackupService_onFileDiscovered(
    mut env: JNIEnv,
    _class: JClass,
    file_path: JString,
) {
    if let Ok(path_str) = env.get_string(&file_path) {
        let path = path_str.to_string_lossy().into_owned();
        log::info!("JNI: AutoBackupService discovered new file: {}", path);
        
        if let Ok(guard) = crate::share_intent::APP_HANDLE.lock() {
            if let Some(app) = guard.as_ref() {
                let db_pool = app.state::<crate::db::DbConnection>();
                if let Ok(conn) = db_pool.lock() {
                    let query = "INSERT OR IGNORE INTO auto_backups (file_path, status, created_at) VALUES (?, 'pending', strftime('%s', 'now'))";
                    let mut stmt = conn.prepare(query).unwrap();
                    let _ = stmt.bind((1, path.as_str()));
                    if let Ok(_) = stmt.next() {
                        log::info!("AutoBackup: Logged new file for backup: {}", path);
                        // Emit event so foreground React app can queue it instantly if open
                        let _ = app.emit("auto-backup-discovered", vec![path]);
                    }
                }
            }
        }
    }
}

#[tauri::command]
pub fn cmd_toggle_auto_backup(enabled: bool) -> Result<(), String> {
    #[cfg(target_os = "android")]
    {
        let ctx_obj = ndk_context::android_context();
        let vm = unsafe { jni::JavaVM::from_raw(ctx_obj.vm().cast()) }.map_err(|e| e.to_string())?;
        let mut env = vm.attach_current_thread().map_err(|e| e.to_string())?;
        let ctx = unsafe { jni::objects::JObject::from_raw(ctx_obj.context().cast()) };

        let class_loader = env.call_method(&ctx, "getClassLoader", "()Ljava/lang/ClassLoader;", &[])
            .map_err(|e| e.to_string())?.l().map_err(|e| e.to_string())?;

        let class_name = env.new_string("com.cameronamer.telegramdrive.AutoBackupService").unwrap();
        let class_obj = env.call_method(&class_loader, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", &[jni::objects::JValue::from(&class_name)])
            .map_err(|e| e.to_string())?.l().map_err(|e| e.to_string())?;

        let j_class: jni::objects::JClass = class_obj.into();
        
        let intent_class = env.find_class("android/content/Intent").map_err(|e| e.to_string())?;
        let intent = env.new_object(intent_class, "(Landroid/content/Context;Ljava/lang/Class;)V", &[jni::objects::JValue::from(&ctx), jni::objects::JValue::from(&j_class)])
            .map_err(|e| e.to_string())?;

        if enabled {
            // Android 8+ requires startForegroundService
            let _ = env.call_method(&ctx, "startForegroundService", "(Landroid/content/Intent;)Landroid/content/ComponentName;", &[jni::objects::JValue::from(&intent)]);
        } else {
            let _ = env.call_method(&ctx, "stopService", "(Landroid/content/Intent;)Z", &[jni::objects::JValue::from(&intent)]);
        }
        
        if env.exception_check().unwrap_or(false) {
            let _ = env.exception_clear();
        }
    }
    Ok(())
}
