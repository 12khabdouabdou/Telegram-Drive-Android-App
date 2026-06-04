use grammers_client::types::{LoginToken, PasswordToken, Peer};
use grammers_client::Client;
use std::collections::{HashMap, HashSet};
use std::sync::Arc;
use tokio::sync::Mutex;

/// Tracks the lifecycle of the Telegram connection
///
/// IMPORTANT: The `runner_shutdown` field is critical for preventing stack overflow.
/// When reconnecting, we MUST shutdown the old runner before spawning a new one.
/// Without this, runner tasks accumulate and exhaust the thread stack.
#[derive(Clone)]
pub struct TelegramState {
    pub client: Arc<Mutex<Option<Client>>>,
    pub login_token: Arc<Mutex<Option<LoginToken>>>,
    pub password_token: Arc<Mutex<Option<PasswordToken>>>,
    pub api_id: Arc<Mutex<Option<i32>>>,
    /// Send to this channel to request runner shutdown.
    /// Uses std::sync::Mutex (not tokio) so it can be locked from synchronous
    /// contexts like the RunEvent::Exit handler.
    pub runner_shutdown: Arc<std::sync::Mutex<Option<tokio::sync::oneshot::Sender<()>>>>,
    /// Counter for debugging runner lifecycle
    pub runner_count: Arc<std::sync::atomic::AtomicU32>,
    /// Cache of folder_id → Peer to avoid O(N) dialog scanning on every operation.
    /// Populated lazily on first resolve_peer call, eagerly during cmd_scan_folders.
    /// Cleared on logout.
    pub peer_cache: Arc<tokio::sync::RwLock<HashMap<i64, Peer>>>,
    /// Set of transfer IDs that have been cancelled. Checked cooperatively
    /// in upload/download chunk loops. Cleared on logout.
    pub cancelled_transfers: Arc<tokio::sync::RwLock<HashSet<String>>>,
}

impl TelegramState {
    /// Constructs a new, empty TelegramState. All mutex-guarded fields start as `None`,
    /// the peer cache and cancelled-transfer set start empty, and the runner counter
    /// starts at zero. Mirrors the inline construction previously used in
    /// `app/src-tauri/src/lib.rs` so the new core crate can own the lifecycle.
    pub fn new() -> Self {
        Self {
            client: Arc::new(Mutex::new(None)),
            login_token: Arc::new(Mutex::new(None)),
            password_token: Arc::new(Mutex::new(None)),
            api_id: Arc::new(Mutex::new(None)),
            runner_shutdown: Arc::new(std::sync::Mutex::new(None)),
            runner_count: Arc::new(std::sync::atomic::AtomicU32::new(0)),
            peer_cache: Arc::new(tokio::sync::RwLock::new(HashMap::new())),
            cancelled_transfers: Arc::new(tokio::sync::RwLock::new(HashSet::new())),
        }
    }
}

impl Default for TelegramState {
    fn default() -> Self {
        Self::new()
    }
}
