use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};

/// Persisted API settings (written to api_settings.json in the app data dir)
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ApiSettingsFile {
    pub enabled: bool,
    pub port: u16,
    pub key_hash: Option<String>,
}

impl Default for ApiSettingsFile {
    fn default() -> Self {
        Self {
            enabled: false,
            port: 8550,
            key_hash: None,
        }
    }
}

/// What the frontend sees (never exposes the hash)
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ApiSettingsResponse {
    pub enabled: bool,
    pub port: u16,
    pub key_set: bool,
    pub running: bool,
}

fn settings_path(data_dir: &Path) -> PathBuf {
    data_dir.join("api_settings.json")
}

pub fn load_settings(data_dir: &Path) -> ApiSettingsFile {
    let path = settings_path(data_dir);
    match std::fs::read_to_string(&path) {
        Ok(contents) => {
            match serde_json::from_str(&contents) {
                Ok(s) => s,
                Err(e) => {
                    log::error!(
                        "api_settings.json is corrupted! Reverting to defaults. Error: {}",
                        e
                    );
                    // Rename corrupted file instead of silently dropping it
                    let _ = std::fs::rename(&path, path.with_extension("json.bak"));
                    ApiSettingsFile::default()
                }
            }
        }
        Err(_) => ApiSettingsFile::default(),
    }
}

pub fn save_settings(data_dir: &Path, settings: &ApiSettingsFile) -> Result<(), String> {
    let path = settings_path(data_dir);
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    let json = serde_json::to_string_pretty(settings).map_err(|e| e.to_string())?;
    std::fs::write(path, json).map_err(|e| e.to_string())
}

fn hash_key(key: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(key.as_bytes());
    format!("{:x}", hasher.finalize())
}

/// Verify a plaintext key against a stored hash
pub fn verify_key(plaintext: &str, stored_hash: &str) -> bool {
    hash_key(plaintext) == stored_hash
}

pub fn get_api_settings(data_dir: &Path, running: bool) -> Result<ApiSettingsResponse, String> {
    let settings = load_settings(data_dir);
    Ok(ApiSettingsResponse {
        enabled: settings.enabled,
        port: settings.port,
        key_set: settings.key_hash.is_some(),
        running,
    })
}

pub fn update_api_settings(
    data_dir: &Path,
    enabled: bool,
    port: u16,
    running: bool,
    stream_port: u16,
) -> Result<(ApiSettingsResponse, bool), String> {
    // Validate port range
    if port < 1024 {
        return Err("Port must be 1024 or higher".to_string());
    }

    // Prevent collision with streaming server
    if port == stream_port {
        return Err(format!(
            "Port {} is used by the media streaming server",
            port
        ));
    }

    let mut settings = load_settings(data_dir);
    let port_changed = settings.port != port;
    let enabled_changed = settings.enabled != enabled;

    settings.enabled = enabled;
    settings.port = port;
    save_settings(data_dir, &settings)?;

    let changed = port_changed || enabled_changed;

    Ok((
        ApiSettingsResponse {
            enabled: settings.enabled,
            port: settings.port,
            key_set: settings.key_hash.is_some(),
            running,
        },
        changed,
    ))
}

pub fn regenerate_api_key(data_dir: &Path) -> Result<(String, bool), String> {
    let mut settings = load_settings(data_dir);

    // Generate a secure 32-byte random key as hex
    let mut rng = rand::thread_rng();
    let bytes: Vec<u8> = (0..32).map(|_| rand::Rng::gen(&mut rng)).collect();
    let plaintext_key: String = bytes.iter().map(|b| format!("{:02x}", b)).collect();

    // Store only the hash
    settings.key_hash = Some(hash_key(&plaintext_key));
    save_settings(data_dir, &settings)?;

    Ok((plaintext_key, true))
}
