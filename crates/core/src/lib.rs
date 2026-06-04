pub mod models;
pub mod telegram_state;
pub mod utils;
pub mod auth;
pub mod api_settings;
pub mod settings;
pub mod sharing;
pub mod db;
pub mod bandwidth;
pub mod vpn_optimizer;
pub mod network;
pub mod preview;
pub mod fs;

uniffi::include_scaffolding!("telegram_drive");

use std::sync::OnceLock;
use std::sync::Arc;
use tokio::runtime::Runtime;
use crate::telegram_state::TelegramState;
use std::path::PathBuf;

static RUNTIME: OnceLock<Runtime> = OnceLock::new();
static STATE: OnceLock<Arc<TelegramState>> = OnceLock::new();
static DATA_DIR: OnceLock<String> = OnceLock::new();

pub fn get_runtime() -> &'static Runtime {
    RUNTIME.get_or_init(|| Runtime::new().unwrap())
}

pub fn get_state() -> Arc<TelegramState> {
    STATE.get().expect("Core not initialized").clone()
}

pub fn get_data_dir() -> PathBuf {
    PathBuf::from(DATA_DIR.get().expect("Core not initialized"))
}

pub fn init_core(data_dir: String) {
    let _ = RUNTIME.get_or_init(|| Runtime::new().unwrap());
    let state = Arc::new(TelegramState::new());
    let _ = STATE.set(state);
    let _ = DATA_DIR.set(data_dir);
}

pub fn request_code(phone: String, api_id: i32, api_hash: String) -> String {
    get_runtime().block_on(async {
        let state = get_state();
        let data_dir = get_data_dir();
        crate::auth::auth_request_code(
            &data_dir,
            phone,
            api_id,
            api_hash,
            &state,
            false, String::new(), false, String::new(), String::new(), 0, String::new(), String::new()
        ).await.unwrap_or_else(|e| format!("Error: {}", e))
    })
}

pub fn sign_in(code: String) -> String {
    get_runtime().block_on(async {
        let state = get_state();
        match crate::auth::auth_sign_in(code, &state).await {
            Ok(res) => {
                if res.success {
                    "SUCCESS".to_string()
                } else if res.next_step.as_deref() == Some("password") {
                    "PASSWORD_REQUIRED".to_string()
                } else {
                    format!("Error: Unknown state")
                }
            }
            Err(e) => format!("Error: {}", e)
        }
    })
}

pub fn check_password(password: String) -> bool {
    get_runtime().block_on(async {
        let state = get_state();
        crate::auth::auth_check_password(password, &state).await.is_ok()
    })
}

pub fn logout() -> bool {
    get_runtime().block_on(async {
        let state = get_state();
        let data_dir = get_data_dir();
        crate::auth::logout(&data_dir, &state).await.is_ok()
    })
}

pub fn list_files(folder_id: Option<i64>) -> Vec<String> {
    vec![]
}

pub fn delete_file(message_id: i32, folder_id: Option<i64>) {
}

pub fn is_network_available() -> bool {
    true
}

pub fn check_latency() -> i64 {
    0
}
