use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use grammers_client::Client;
use grammers_mtsender::SenderPool;
use grammers_session::storages::SqliteSession;
use grammers_session::Session;
use grammers_tl_types as tl;
use std::sync::atomic::Ordering;
use std::sync::Arc;
use tokio::sync::oneshot;
use tokio::time::Duration;
use std::path::Path;

use crate::utils::map_error;
use crate::models::AuthResult;
use crate::telegram_state::TelegramState;
use grammers_client::SignInError;

/// Ensures the Telegram client is initialized.
///
/// IMPORTANT: This function properly manages runner lifecycle to prevent stack overflow.
/// Before spawning a new runner, it signals the old runner to shutdown.
pub async fn ensure_client_initialized(
    data_dir: &Path,
    state: &TelegramState,
    api_id: i32,
    // Add network config parameters since we can't use AppHandle for State anymore
    vpn_enabled: bool,
    vpn_preferred_dc: String,
    proxy_enabled: bool,
    proxy_type: String,
    proxy_host: String,
    proxy_port: u16,
    proxy_username: String,
    proxy_password: String,
) -> Result<Client, String> {
    let mut client_guard = state.client.lock().await;

    if let Some(client) = client_guard.as_ref() {
        return Ok(client.clone());
    }

    // CRITICAL: Shutdown existing runner before creating a new one
    let did_shutdown_old_runner = {
        let mut guard = state.runner_shutdown.lock().unwrap();
        if let Some(shutdown_tx) = guard.take() {
            log::info!("Signaling old runner to shutdown...");
            let _ = shutdown_tx.send(());
            true
        } else {
            false
        }
    };
    if did_shutdown_old_runner {
        tokio::time::sleep(Duration::from_millis(100)).await;
    }

    let runner_num = state.runner_count.fetch_add(1, Ordering::SeqCst) + 1;
    log::info!("Initializing Telegram Client #{} with API ID: {}", runner_num, api_id);

    if !data_dir.exists() {
        std::fs::create_dir_all(data_dir)
            .map_err(|e| format!("Failed to create data dir: {}", e))?;
    }

    let session_path = data_dir.join("telegram.session");
    let session_path_str = session_path.to_string_lossy().to_string();
    log::info!("Opening session at: {}", session_path_str);

    let mut session_open_result = SqliteSession::open(&session_path_str);

    if session_open_result.is_err() {
        for attempt in 1..=5 {
            log::warn!("Failed to open session on attempt {} (database may be locked). Retrying in 100ms...", attempt);
            tokio::time::sleep(Duration::from_millis(100)).await;
            session_open_result = SqliteSession::open(&session_path_str);
            if session_open_result.is_ok() {
                break;
            }
        }
    }

    let session = match session_open_result.map_err(|e| e.to_string()) {
        Ok(s) => s,
        Err(e) => {
            log::warn!("Session file could not be opened after retries ({}). Recreating...", e);
            let _ = std::fs::remove_file(&session_path);
            let _ = std::fs::remove_file(format!("{}-wal", session_path_str));
            let _ = std::fs::remove_file(format!("{}-shm", session_path_str));

            SqliteSession::open(&session_path_str)
                .map_err(|err| format!("Failed to open session after recreation: {}", err))?
        }
    };

    let preferred_dc = if vpn_enabled {
        vpn_preferred_dc
    } else {
        "auto".to_string()
    };

    if preferred_dc.starts_with("dc") && preferred_dc.len() > 2 {
        if let Ok(dc_id) = preferred_dc[2..].parse::<i32>() {
            log::info!("Setting preferred home DC ID: {}", dc_id);
            session.set_home_dc_id(dc_id);
        }
    }

    let mut connection_params = grammers_mtsender::ConnectionParams::default();
    if proxy_enabled && !proxy_host.is_empty() {
        if proxy_type == "socks5" {
            let url = if !proxy_username.is_empty() {
                format!(
                    "socks5://{}:{}@{}:{}",
                    proxy_username, proxy_password, proxy_host, proxy_port
                )
            } else {
                format!("socks5://{}:{}", proxy_host, proxy_port)
            };
            log::info!("Using SOCKS5 proxy: socks5://{}:{}", proxy_host, proxy_port);
            connection_params.proxy_url = Some(url);
        } else {
            log::warn!("Unsupported proxy type: {}. grammers only supports SOCKS5 proxy.", proxy_type);
        }
    }

    let session = Arc::new(session);
    let pool = SenderPool::with_configuration(session, api_id, connection_params);
    let client = Client::new(&pool);

    let (shutdown_tx, shutdown_rx) = oneshot::channel::<()>();
    *state.runner_shutdown.lock().unwrap() = Some(shutdown_tx);

    let SenderPool { runner, .. } = pool;
    tokio::spawn(async move {
        tokio::select! {
            _ = runner.run() => {
                log::info!("Runner #{} exited normally", runner_num);
            }
            _ = shutdown_rx => {
                log::info!("Runner #{} shutdown requested, exiting", runner_num);
            }
        }
    });

    *client_guard = Some(client.clone());
    Ok(client)
}

pub async fn connect(
    data_dir: &Path,
    state: &TelegramState,
    api_id: i32,
    vpn_enabled: bool,
    vpn_preferred_dc: String,
    proxy_enabled: bool,
    proxy_type: String,
    proxy_host: String,
    proxy_port: u16,
    proxy_username: String,
    proxy_password: String,
) -> Result<bool, String> {
    *state.api_id.lock().await = Some(api_id);
    ensure_client_initialized(
        data_dir,
        state,
        api_id,
        vpn_enabled,
        vpn_preferred_dc,
        proxy_enabled,
        proxy_type,
        proxy_host,
        proxy_port,
        proxy_username,
        proxy_password,
    ).await?;
    Ok(true)
}

pub async fn check_connection(
    data_dir: &Path,
    state: &TelegramState,
    vpn_enabled: bool,
    vpn_preferred_dc: String,
    proxy_enabled: bool,
    proxy_type: String,
    proxy_host: String,
    proxy_port: u16,
    proxy_username: String,
    proxy_password: String,
) -> Result<bool, String> {
    let client_msg_opt = {
        let guard = state.client.lock().await;
        guard.as_ref().cloned()
    };

    if let Some(client) = client_msg_opt {
        if client.get_me().await.is_ok() {
            return Ok(true);
        }
        log::warn!("Connection check failed (get_me). Attempting reconnect...");
    } else {
        log::warn!("Connection check: No client found. Checking for saved API ID...");
    }

    let api_id_opt = *state.api_id.lock().await;
    if let Some(api_id) = api_id_opt {
        *state.client.lock().await = None;

        match ensure_client_initialized(
            data_dir,
            state,
            api_id,
            vpn_enabled,
            vpn_preferred_dc,
            proxy_enabled,
            proxy_type,
            proxy_host,
            proxy_port,
            proxy_username,
            proxy_password,
        ).await {
            Ok(c) => {
                if c.get_me().await.is_ok() {
                    log::info!("Auto-reconnect successful.");
                    return Ok(true);
                } else {
                    return Err("Reconnect succeeded but ping failed.".to_string());
                }
            }
            Err(e) => return Err(format!("Auto-reconnect failed: {}", e)),
        }
    }

    Ok(false)
}

pub async fn logout(
    data_dir: &Path,
    state: &TelegramState,
) -> Result<bool, String> {
    log::info!("Logging out...");

    {
        let mut shutdown_guard = state.runner_shutdown.lock().unwrap();
        if let Some(shutdown_tx) = shutdown_guard.take() {
            log::info!("Signaling runner shutdown for logout...");
            let _ = shutdown_tx.send(());
        }
    }

    let client_opt = { state.client.lock().await.clone() };
    if let Some(client) = client_opt {
        let _ = client.sign_out().await;
    }

    *state.client.lock().await = None;
    *state.login_token.lock().await = None;
    *state.password_token.lock().await = None;
    *state.api_id.lock().await = None;
    crate::utils::clear_peer_cache(&state.peer_cache).await;
    state.cancelled_transfers.write().await.clear();

    let session_path = data_dir.join("telegram.session");
    let _ = std::fs::remove_file(&session_path);
    let _ = std::fs::remove_file(data_dir.join("telegram.session-wal"));
    let _ = std::fs::remove_file(data_dir.join("telegram.session-shm"));

    log::info!(
        "Logout complete. Runner count: {}",
        state.runner_count.load(Ordering::SeqCst)
    );
    Ok(true)
}

pub async fn auth_request_code(
    data_dir: &Path,
    phone: String,
    api_id: i32,
    api_hash: String,
    state: &TelegramState,
    vpn_enabled: bool,
    vpn_preferred_dc: String,
    proxy_enabled: bool,
    proxy_type: String,
    proxy_host: String,
    proxy_port: u16,
    proxy_username: String,
    proxy_password: String,
) -> Result<String, String> {
    if api_hash.trim().is_empty() {
        return Err("API Hash cannot be empty.".to_string());
    }

    *state.api_id.lock().await = Some(api_id);

    let client_handle = ensure_client_initialized(
        data_dir,
        state,
        api_id,
        vpn_enabled,
        vpn_preferred_dc,
        proxy_enabled,
        proxy_type,
        proxy_host,
        proxy_port,
        proxy_username,
        proxy_password,
    ).await?;

    log::info!("Requesting code for {}", phone);

    let mut last_error = String::new();

    for i in 1..=2 {
        match client_handle.request_login_code(&phone, &api_hash).await {
            Ok(token) => {
                let mut token_guard = state.login_token.lock().await;
                *token_guard = Some(token);
                return Ok("code_sent".to_string());
            }
            Err(e) => {
                let err_msg = e.to_string();
                log::warn!("Error requesting code (Attempt {}): {}", i, err_msg);

                if err_msg.contains("AUTH_RESTART") || err_msg.contains("500") {
                    log::info!("AUTH_RESTART error detected. Retrying...");
                    last_error = err_msg;
                    continue;
                }

                return Err(map_error(e));
            }
        }
    }

    Err(format!("Telegram Error after retry: {}", last_error))
}

pub async fn auth_sign_in(
    code: String,
    state: &TelegramState,
) -> Result<AuthResult, String> {
    log::info!("Signing in with code...");

    let client = {
        let guard = state.client.lock().await;
        guard.as_ref().ok_or("Client not initialized")?.clone()
    };

    let token_guard = state.login_token.lock().await;
    let login_token = token_guard
        .as_ref()
        .ok_or("No login session found (restart flow)")?;

    match client.sign_in(login_token, &code).await {
        Ok(_user) => {
            log::info!("Successfully logged in.");
            Ok(AuthResult {
                success: true,
                next_step: Some("dashboard".to_string()),
                error: None,
            })
        }
        Err(SignInError::PasswordRequired(token)) => {
            let mut pw_guard = state.password_token.lock().await;
            *pw_guard = Some(token);

            Ok(AuthResult {
                success: false,
                next_step: Some("password".to_string()),
                error: None,
            })
        }
        Err(e) => {
            log::error!("Sign in error: {}", e);
            Err(format!("Sign in failed: {}", e))
        }
    }
}

pub async fn auth_check_password(
    password: String,
    state: &TelegramState,
) -> Result<AuthResult, String> {
    let client = {
        let guard = state.client.lock().await;
        guard.as_ref().ok_or("Client not initialized")?.clone()
    };

    let mut pw_guard = state.password_token.lock().await;
    let pw_token = pw_guard.take().ok_or("No password session found")?;

    match client.check_password(pw_token, password.as_str()).await {
        Ok(_user) => {
            log::info!("2FA Success.");
            Ok(AuthResult {
                success: true,
                next_step: Some("dashboard".to_string()),
                error: None,
            })
        }
        Err(e) => Err(format!("2FA Failed: {}", e)),
    }
}

pub async fn auth_qr_login(
    data_dir: &Path,
    api_id: i32,
    api_hash: String,
    state: &TelegramState,
    vpn_enabled: bool,
    vpn_preferred_dc: String,
    proxy_enabled: bool,
    proxy_type: String,
    proxy_host: String,
    proxy_port: u16,
    proxy_username: String,
    proxy_password: String,
) -> Result<String, String> {
    if api_hash.trim().is_empty() {
        return Err("API Hash cannot be empty.".to_string());
    }

    *state.api_id.lock().await = Some(api_id);

    let client = ensure_client_initialized(
        data_dir,
        state,
        api_id,
        vpn_enabled,
        vpn_preferred_dc,
        proxy_enabled,
        proxy_type,
        proxy_host,
        proxy_port,
        proxy_username,
        proxy_password,
    ).await?;

    log::info!("Requesting QR login token...");

    let result = client
        .invoke(&tl::functions::auth::ExportLoginToken {
            api_id,
            api_hash: api_hash.clone(),
            except_ids: vec![],
        })
        .await
        .map_err(|e| format!("ExportLoginToken failed: {}", e))?;

    match result {
        tl::enums::auth::LoginToken::Token(t) => {
            let encoded = URL_SAFE_NO_PAD.encode(&t.token);
            let url = format!("tg://login?token={}", encoded);
            log::info!("QR login URL generated, expires at {}", t.expires);
            Ok(url)
        }
        tl::enums::auth::LoginToken::Success(_s) => {
            log::info!("QR login: already authorized");
            Ok("__authorized__".to_string())
        }
        tl::enums::auth::LoginToken::MigrateTo(m) => {
            log::info!("QR login: need to migrate to DC {}", m.dc_id);
            let encoded = URL_SAFE_NO_PAD.encode(&m.token);
            let url = format!("tg://login?token={}", encoded);
            Ok(url)
        }
    }
}

pub async fn auth_qr_poll(state: &TelegramState) -> Result<AuthResult, String> {
    let client = {
        let guard = state.client.lock().await;
        guard.as_ref().ok_or("Client not initialized")?.clone()
    };

    match client.is_authorized().await {
        Ok(true) => {
            log::info!("QR login: session authorized!");
            Ok(AuthResult {
                success: true,
                next_step: Some("dashboard".to_string()),
                error: None,
            })
        }
        Ok(false) => {
            Ok(AuthResult {
                success: false,
                next_step: Some("waiting".to_string()),
                error: None,
            })
        }
        Err(e) => {
            log::warn!("QR poll auth check failed: {}", e);
            Ok(AuthResult {
                success: false,
                next_step: Some("waiting".to_string()),
                error: None,
            })
        }
    }
}
