use crate::bandwidth::BandwidthManager;
use crate::utils::{map_error, resolve_peer};
use crate::models::{FileMetadata, FolderMetadata};
use crate::vpn_optimizer::{backoff_ms, NetworkConfig};
use crate::telegram_state::TelegramState;
use grammers_client::types::{Media, Peer};
use grammers_client::InputMessage;
use grammers_tl_types as tl;
use serde::Serialize;
use std::collections::HashMap;
use std::sync::Mutex;
use std::sync::OnceLock;
use tokio::sync::oneshot;

static UPLOAD_CANCELLATIONS: OnceLock<Mutex<HashMap<String, oneshot::Sender<()>>>> =
    OnceLock::new();

fn get_upload_cancellations() -> &'static Mutex<HashMap<String, oneshot::Sender<()>>> {
    UPLOAD_CANCELLATIONS.get_or_init(|| Mutex::new(HashMap::new()))
}

fn url_decode(s: &str) -> String {
    let mut result = Vec::new();
    let bytes = s.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let Ok(hex) = std::str::from_utf8(&bytes[i + 1..i + 3]) {
                if let Ok(byte) = u8::from_str_radix(hex, 16) {
                    result.push(byte);
                    i += 3;
                    continue;
                }
            }
        }
        result.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&result).into_owned()
}

pub fn clean_android_path(raw_path: &str) -> String {
    let decoded = url_decode(raw_path);
    log::info!("URL Decoded path: {}", decoded);
    let mut cleaned = decoded;
    if cleaned.starts_with("raw%3/") {
        cleaned = cleaned.replace("raw%3/", "/");
    }
    if cleaned.starts_with("raw://") {
        cleaned = cleaned.replace("raw://", "/");
    } else if cleaned.starts_with("file://") {
        cleaned = cleaned.replace("file://", "");
    } else if cleaned.starts_with("raw:") {
        cleaned = cleaned.replace("raw:", "");
    }
    if !cleaned.starts_with("content://") {
        cleaned = cleaned.replace("//", "/");
    }
    log::info!("Cleaned absolute path: {}", cleaned);
    cleaned
}




pub async fn cmd_create_folder(
    name: String,
    state: &TelegramState,
) -> Result<FolderMetadata, String> {
    let client_opt = { state.client.lock().await.clone() };

    // --- MOCK ---
    if client_opt.is_none() {
        let mock_id = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;
        log::info!("[MOCK] Created folder '{}' with ID {}", name, mock_id);
        return Ok(FolderMetadata {
            id: mock_id,
            name,
            parent_id: None,
            username: None,
            is_public: false,
        });
    }
    // -----------
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;
    log::info!("Creating Telegram Channel: {}", name);

    let result = client
        .invoke(&tl::functions::channels::CreateChannel {
            broadcast: true,
            megagroup: false,
            title: format!("{} [TD]", name),
            about: "Telegram Drive Storage Folder\n[telegram-drive-folder]".to_string(),
            geo_point: None,
            address: None,
            for_import: false,
            forum: false,
            ttl_period: None, // Initial creation TTL
        })
        .await
        .map_err(map_error)?;

    let (chat_id, access_hash) = match &result {
        tl::enums::Updates::Updates(u) => {
            let chat = u.chats.first().ok_or("No chat in updates")?;
            match chat {
                tl::enums::Chat::Channel(c) => {
                    // Cache the newly created peer immediately so that invite link generation
                    // and other commands don't experience a peer cache miss on warm start.
                    let channel_obj = grammers_client::types::Channel { raw: c.clone() };
                    state
                        .peer_cache
                        .write()
                        .await
                        .insert(c.id, grammers_client::types::Peer::Channel(channel_obj));
                    (c.id, c.access_hash.unwrap_or(0))
                }
                _ => return Err("Created chat is not a channel".to_string()),
            }
        }
        _ => return Err("Unexpected response (not Updates::Updates)".to_string()),
    };

    // Explicitly Disable TTL
    let _input_channel = tl::enums::InputChannel::Channel(tl::types::InputChannel {
        channel_id: chat_id,
        access_hash,
    });

    let _ = client
        .invoke(&tl::functions::messages::SetHistoryTtl {
            peer: tl::enums::InputPeer::Channel(tl::types::InputPeerChannel {
                channel_id: chat_id,
                access_hash,
            }),
            period: 0,
        })
        .await;

    Ok(FolderMetadata {
        id: chat_id,
        name,
        parent_id: None,
        username: None,
        is_public: false,
    })
}

pub async fn cmd_delete_folder(
    folder_id: i64,
    state: &TelegramState,
) -> Result<bool, String> {
    let client_opt = { state.client.lock().await.clone() };

    if client_opt.is_none() {
        log::info!("[MOCK] Deleted folder ID {}", folder_id);
        return Ok(true);
    }
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;
    log::info!("Deleting folder/channel: {}", folder_id);

    let peer = resolve_peer(&client, Some(folder_id), &state.peer_cache).await?;

    let input_channel = match peer {
        Peer::Channel(c) => {
            let chan = &c.raw;
            tl::enums::InputChannel::Channel(tl::types::InputChannel {
                channel_id: chan.id,
                access_hash: chan.access_hash.ok_or("No access hash for channel")?,
            })
        }
        _ => return Err("Only channels (folders) can be deleted.".to_string()),
    };

    client
        .invoke(&tl::functions::channels::DeleteChannel {
            channel: input_channel,
        })
        .await
        .map_err(|e| format!("Failed to delete channel: {}", e))?;

    Ok(true)
}

pub async fn cmd_rename_folder(
    folder_id: i64,
    new_name: String,
    state: &TelegramState,
) -> Result<bool, String> {
    let client_opt = { state.client.lock().await.clone() };

    if client_opt.is_none() {
        log::info!("[MOCK] Renamed folder ID {} to {}", folder_id, new_name);
        return Ok(true);
    }
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;
    log::info!("Renaming folder/channel: {} to {}", folder_id, new_name);

    let peer = resolve_peer(&client, Some(folder_id), &state.peer_cache).await?;

    let input_channel = match peer {
        Peer::Channel(c) => {
            let chan = &c.raw;
            tl::enums::InputChannel::Channel(tl::types::InputChannel {
                channel_id: chan.id,
                access_hash: chan.access_hash.ok_or("No access hash for channel")?,
            })
        }
        _ => return Err("Only channels (folders) can be renamed.".to_string()),
    };

    client
        .invoke(&tl::functions::channels::EditTitle {
            channel: input_channel,
            title: format!("{} [TD]", new_name),
        })
        .await
        .map_err(|e| format!("Failed to rename channel: {}", e))?;

    Ok(true)
}

#[derive(Clone, serde::Serialize)]
pub struct ProgressPayload {
    pub id: String,
    pub percent: u8,
    pub uploaded_bytes: u64,
    pub total_bytes: u64,
    pub speed_bytes_per_sec: u64,
}

/// Async reader wrapper that tracks bytes read for progress reporting.
/// Wraps a tokio File and counts how many bytes have been consumed.
struct ProgressReader {
    inner: tokio::io::BufReader<tokio::fs::File>,
    bytes_read: std::sync::Arc<std::sync::atomic::AtomicU64>,
}

impl ProgressReader {
    async fn new(
        file: tokio::fs::File,
    ) -> Result<(Self, u64, std::sync::Arc<std::sync::atomic::AtomicU64>), String> {
        let metadata = file.metadata().await.map_err(|e| e.to_string())?;
        let size = metadata.len();
        let counter = std::sync::Arc::new(std::sync::atomic::AtomicU64::new(0));
        let reader = Self {
            inner: tokio::io::BufReader::new(file),
            bytes_read: counter.clone(),
        };
        Ok((reader, size, counter))
    }
}

impl tokio::io::AsyncRead for ProgressReader {
    fn poll_read(
        mut self: std::pin::Pin<&mut Self>,
        cx: &mut std::task::Context<'_>,
        buf: &mut tokio::io::ReadBuf<'_>,
    ) -> std::task::Poll<std::io::Result<()>> {
        let before = buf.filled().len();
        let result = std::pin::Pin::new(&mut self.inner).poll_read(cx, buf);
        if let std::task::Poll::Ready(Ok(())) = &result {
            let after = buf.filled().len();
            let delta = (after - before) as u64;
            self.bytes_read
                .fetch_add(delta, std::sync::atomic::Ordering::Relaxed);
        }
        result
    }
}

/// Delete a partial file with retries (best-effort cleanup)
fn cleanup_partial_file(path: &str) {
    let path = path.to_string();
    std::thread::spawn(move || {
        for attempt in 0..5 {
            match std::fs::remove_file(&path) {
                Ok(()) => {
                    log::info!("Cleaned up partial file: {}", path);
                    return;
                }
                Err(e) if e.kind() == std::io::ErrorKind::NotFound => return,
                Err(e) => {
                    log::warn!(
                        "Cleanup attempt {}/5 failed for {}: {}",
                        attempt + 1,
                        path,
                        e
                    );
                    std::thread::sleep(std::time::Duration::from_secs(1));
                }
            }
        }
    });
}

pub async fn cmd_cancel_transfer(
    transfer_id: String,
    state: &TelegramState,
) -> Result<bool, String> {
    log::info!("Cancelling transfer: {}", transfer_id);
    state
        .cancelled_transfers
        .write()
        .await
        .insert(transfer_id.clone());
    if let Some(tx) = get_upload_cancellations()
        .lock()
        .unwrap()
        .remove(&transfer_id)
    {
        let _ = tx.send(());
    }
    Ok(true)
}

pub async fn cmd_upload_file(
    mut path: String,
    folder_id: Option<i64>,
    transfer_id: Option<String>,
    progress_tx: Option<tokio::sync::mpsc::Sender<ProgressPayload>>,
    state: &TelegramState,
    bw_state: &BandwidthManager,
    net_config: &NetworkConfig,
) -> Result<String, String> {
    if path.starts_with("fd://") {
        return Err(
            "Security Exception: Arbitrary file descriptor access is forbidden".to_string(),
        );
    }


    let result = cmd_upload_file_inner(
        path.clone(),
        folder_id,
        transfer_id,
        progress_tx,
        state,
        bw_state,
        net_config,
    )
    .await;

    result
}

async fn cmd_upload_file_inner(
    path: String,
    folder_id: Option<i64>,
    transfer_id: Option<String>,
    progress_tx: Option<tokio::sync::mpsc::Sender<ProgressPayload>>,
    state: &TelegramState,
    bw_state: &BandwidthManager,
    net_config: &NetworkConfig,
) -> Result<String, String> {
    // Support Zero-Copy Android Uploads via raw file descriptors
    let file = if path.starts_with("fd://") {
        #[cfg(target_os = "android")]
        {
            let fd_str = path[5..].split('|').next().unwrap_or(&path[5..]);
            let fd: i32 = fd_str.parse().map_err(|e| format!("Invalid fd: {}", e))?;
            unsafe { tokio::fs::File::from_std(std::os::unix::io::FromRawFd::from_raw_fd(fd)) }
        }
        #[cfg(not(target_os = "android"))]
        return Err("fd:// scheme only supported on Android".to_string());
    } else {
        tokio::fs::File::open(&path)
            .await
            .map_err(|e| e.to_string())?
    };

    let size = file.metadata().await.map_err(|e| e.to_string())?.len();
    bw_state.can_transfer(size)?;

    let tid = transfer_id.unwrap_or_default();

    let client_opt = { state.client.lock().await.clone() };
    if client_opt.is_none() {
        log::info!("[MOCK] Uploaded file {} to {:?}", path, folder_id);
        bw_state.add_up(size);
        return Ok("Mock upload successful".to_string());
    }
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;

    // Emit start progress
    if !tid.is_empty() {
        if let Some(tx) = progress_tx.as_ref() { let _ = tx.send(ProgressPayload {
                id: tid.clone(),
                percent: 0,
                uploaded_bytes: 0,
                total_bytes: size,
                speed_bytes_per_sec: 0,
            }).await; }
    }

    // Create progress-tracking reader
    let (mut reader, file_size, bytes_counter) = ProgressReader::new(file).await?;
    let file_name = if path.starts_with("fd://") {
        // If it's an fd, we don't have the original filename in the path,
        // but for Telegram uploads, the frontend should ideally pass the filename or we use a fallback.
        // Usually, the frontend passes `path` as the original filename in another field, but we can extract it if needed.
        // Wait, the frontend doesn't pass the filename separately to cmd_upload_file.
        // We can encode it as `fd://<fd>|<filename>`
        let parts: Vec<&str> = path[5..].splitn(2, '|').collect();
        if parts.len() == 2 {
            parts[1].to_string()
        } else {
            "upload.file".to_string()
        }
    } else {
        std::path::Path::new(&path)
            .file_name()
            .map(|n| n.to_string_lossy().to_string())
            .unwrap_or_else(|| "file".to_string())
    };

    // Spawn a progress reporter task that emits events every 250ms
    let cancelled = state.cancelled_transfers.clone();
    let progress_tid = tid.clone();
    
    let progress_counter = bytes_counter.clone();
    let progress_tx = progress_tx.clone();
    let progress_task = if !tid.is_empty() {
        Some(tokio::spawn(async move {
            let mut last_bytes: u64 = 0;
            let mut last_time = std::time::Instant::now();
            loop {
                tokio::time::sleep(std::time::Duration::from_millis(250)).await;
                let current = progress_counter.load(std::sync::atomic::Ordering::Relaxed);
                let now = std::time::Instant::now();
                let dt = now.duration_since(last_time).as_secs_f64();
                let speed = if dt > 0.0 {
                    ((current - last_bytes) as f64 / dt) as u64
                } else {
                    0
                };
                let percent = if file_size > 0 {
                    ((current as f64 / file_size as f64) * 100.0).min(99.0) as u8
                } else {
                    0
                };

                if let Some(tx) = progress_tx.as_ref() { let _ = tx.send(ProgressPayload {
                        id: progress_tid.clone(),
                        percent,
                        uploaded_bytes: current,
                        total_bytes: file_size,
                        speed_bytes_per_sec: speed,
                    }).await; }

                last_bytes = current;
                last_time = now;

                if current >= file_size {
                    break;
                }
                // Check cancellation
                if cancelled.read().await.contains(&progress_tid) {
                    break;
                }
            }
        }))
    } else {
        None
    };

    // Check cancellation before starting
    if state.cancelled_transfers.read().await.contains(&tid) {
        state.cancelled_transfers.write().await.remove(&tid);
        if let Some(t) = progress_task {
            t.abort();
        }
        return Err("Transfer cancelled".to_string());
    }

    let (cancel_tx, cancel_rx) = tokio::sync::oneshot::channel::<()>();
    if !tid.is_empty() {
        get_upload_cancellations()
            .lock()
            .unwrap()
            .insert(tid.clone(), cancel_tx);
    }

    let client_clone = client.clone();
    let mut upload_task = tokio::spawn(async move {
        client_clone
            .upload_stream(&mut reader, file_size as usize, file_name)
            .await
    });

    let upload_result = {
        tokio::select! {
            res = &mut upload_task => {
                if !tid.is_empty() {
                    get_upload_cancellations().lock().unwrap().remove(&tid);
                }
                res.map_err(|e| format!("Task join error: {}", e))?
            }
            _ = cancel_rx => {
                log::info!("Aborting upload task for transfer ID: {}", tid);
                upload_task.abort();
                state.cancelled_transfers.write().await.remove(&tid);
                if let Some(t) = progress_task { t.abort(); }
                return Err("Transfer cancelled".to_string());
            }
        }
    };

    // Stop progress reporter
    if let Some(t) = progress_task {
        t.abort();
    }

    let uploaded_file = upload_result.map_err(map_error)?;
    let message = InputMessage::new().text("").file(uploaded_file);

    let peer = resolve_peer(&client, folder_id, &state.peer_cache).await?;

    // VPN-aware retry logic for send_message
    let max_retries = net_config.retry_attempts();
    let base_ms = net_config.retry_base_backoff_ms();
    let max_ms = net_config.retry_max_backoff_ms();
    let respect_flood = net_config.should_respect_flood_wait();
    let mut last_err = String::new();

    for attempt in 0..=max_retries {
        match client.send_message(&peer, message.clone()).await {
            Ok(_) => {
                bw_state.add_up(size);
                if !tid.is_empty() {
                    if let Some(tx) = progress_tx.as_ref() { let _ = tx.send(ProgressPayload {
                            id: tid,
                            percent: 100,
                            uploaded_bytes: size,
                            total_bytes: size,
                            speed_bytes_per_sec: 0,
                        }).await; }
                }
                return Ok("File uploaded successfully".to_string());
            }
            Err(e) => {
                let err = map_error(e);
                log::warn!(
                    "send_message attempt {}/{}: {}",
                    attempt + 1,
                    max_retries + 1,
                    err
                );

                // Handle FLOOD_WAIT: sleep the requested time if configured
                if respect_flood && err.starts_with("FLOOD_WAIT_") {
                    if let Ok(secs) = err.trim_start_matches("FLOOD_WAIT_").parse::<u64>() {
                        let wait = secs.min(300); // cap at 5 min
                        log::info!("Respecting FLOOD_WAIT: sleeping {}s", wait);
                        tokio::time::sleep(std::time::Duration::from_secs(wait)).await;
                        last_err = err;
                        continue;
                    }
                }

                last_err = err;
                if attempt < max_retries {
                    let delay = backoff_ms(attempt, base_ms, max_ms);
                    log::info!("Retrying in {}ms...", delay);
                    tokio::time::sleep(std::time::Duration::from_millis(delay)).await;
                }
            }
        }
    }

    Err(format!(
        "Upload failed after {} attempts: {}",
        max_retries + 1,
        last_err
    ))
}

pub async fn initiate_upload(
    path: String,
    folder_id: Option<i64>,
    transfer_id: Option<String>,
    progress_tx: Option<tokio::sync::mpsc::Sender<ProgressPayload>>,
    state: &TelegramState,
    bw_state: &BandwidthManager,
    net_config: &NetworkConfig,
) -> Result<String, String> {
    cmd_upload_file(
        path,
        folder_id,
        transfer_id,
        progress_tx,
        state,
        bw_state,
        net_config,
    )
    .await
}

pub async fn cmd_delete_file(
    message_id: i32,
    folder_id: Option<i64>,
    state: &TelegramState,
) -> Result<bool, String> {
    let client_opt = { state.client.lock().await.clone() };
    if client_opt.is_none() {
        log::info!(
            "[MOCK] Deleted message {} from folder {:?}",
            message_id,
            folder_id
        );
        return Ok(true);
    }
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;

    let peer = resolve_peer(&client, folder_id, &state.peer_cache).await?;
    client
        .delete_messages(&peer, &[message_id])
        .await
        .map_err(|e| e.to_string())?;
    Ok(true)
}

#[derive(Debug, serde::Deserialize)]
pub struct DownloadFileRequest {
    pub message_id: i32,
    pub save_path: String,
    pub folder_id: Option<i64>,
    pub transfer_id: Option<String>,
}

pub async fn cmd_download_file(
    req: DownloadFileRequest,
    progress_tx: Option<tokio::sync::mpsc::Sender<ProgressPayload>>,
    state: &TelegramState,
    bw_state: &BandwidthManager,
    net_config: &NetworkConfig,
) -> Result<String, String> {
    let tid = req.transfer_id.unwrap_or_default();
    let save_path = req.save_path;
    let folder_id = req.folder_id;
    let message_id = req.message_id;

    let actual_save_path = save_path.clone();

    let client_opt = { state.client.lock().await.clone() };
    if client_opt.is_none() {
        log::info!(
            "[MOCK] Downloaded message {} from {:?} to {}",
            message_id,
            folder_id,
            actual_save_path
        );
        if let Err(e) = tokio::fs::write(&actual_save_path, b"Mock Content").await {
            return Err(e.to_string());
        }
        return Ok("Download successful".to_string());
    }
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;

    let peer = resolve_peer(&client, folder_id, &state.peer_cache).await?;

    // Use get_messages_by_id for efficient message lookup (same as server.rs)
    let messages = client
        .get_messages_by_id(&peer, &[message_id])
        .await
        .map_err(|e| e.to_string())?;

    let msg = messages
        .into_iter()
        .flatten()
        .next()
        .ok_or_else(|| "Message not found".to_string())?;

    let media = msg
        .media()
        .ok_or_else(|| "No media in message".to_string())?;

    let expected_file_size = match &media {
        Media::Document(d) => Some(d.size() as u64),
        _ => None,
    };
    let total_size = expected_file_size.unwrap_or(match &media {
        Media::Photo(_) => 1024 * 1024,
        _ => 0,
    });

    bw_state.can_transfer(total_size)?;

    // Emit start
    if !tid.is_empty() {
        if let Some(tx) = progress_tx.as_ref() { let _ = tx.send(ProgressPayload {
                id: tid.clone(),
                percent: 0,
                uploaded_bytes: 0,
                total_bytes: total_size,
                speed_bytes_per_sec: 0,
            }).await; }
    }

    // Stream download with per-chunk progress
    let mut download_iter = client.iter_download(&media);
    let mut file = tokio::fs::File::create(&actual_save_path)
        .await
        .map_err(|e| e.to_string())?;
    let mut downloaded: u64 = 0;
    let mut last_emit_time = std::time::Instant::now();
    let mut last_emit_bytes: u64 = 0;
    let mut chunk_retry_budget = net_config.retry_attempts();

    while let Some(chunk) = download_iter.next().await.transpose() {
        // Check cancellation
        if state.cancelled_transfers.read().await.contains(&tid) {
            state.cancelled_transfers.write().await.remove(&tid);
            drop(file);
            cleanup_partial_file(&actual_save_path);
            return Err("Transfer cancelled".to_string());
        }

        let bytes: Vec<u8> = match chunk {
            Ok(b) => {
                chunk_retry_budget = net_config.retry_attempts(); // reset on success
                b
            }
            Err(e) => {
                let err = map_error(&e);
                if chunk_retry_budget > 0 {
                    chunk_retry_budget -= 1;
                    log::warn!(
                        "Download chunk error (retries left: {}): {}",
                        chunk_retry_budget,
                        err
                    );
                    let delay = backoff_ms(
                        0,
                        net_config.retry_base_backoff_ms(),
                        net_config.retry_max_backoff_ms(),
                    );
                    tokio::time::sleep(std::time::Duration::from_millis(delay)).await;
                    continue;
                }
                return Err(format!("Download chunk error: {}", err));
            }
        };
        tokio::io::AsyncWriteExt::write_all(&mut file, &bytes)
            .await
            .map_err(|e| e.to_string())?;
        downloaded += bytes.len() as u64;

        // Time-based progress emission (every 250ms)
        if !tid.is_empty() {
            let now = std::time::Instant::now();
            let dt = now.duration_since(last_emit_time).as_secs_f64();
            if dt >= 0.25 || downloaded >= total_size {
                let speed = if dt > 0.0 {
                    ((downloaded - last_emit_bytes) as f64 / dt) as u64
                } else {
                    0
                };
                let percent = if total_size > 0 {
                    ((downloaded as f64 / total_size as f64) * 100.0).min(100.0) as u8
                } else {
                    0
                };
                if let Some(tx) = progress_tx.as_ref() { let _ = tx.send(ProgressPayload {
                        id: tid.clone(),
                        percent,
                        uploaded_bytes: downloaded,
                        total_bytes: total_size,
                        speed_bytes_per_sec: speed,
                    }).await; }
                last_emit_time = now;
                last_emit_bytes = downloaded;
            }
        }

        // Bandwidth throttle: if download limit is set, sleep to maintain rate
        let dl_limit = net_config.download_limit_bytes_per_sec();
        if dl_limit > 0 {
            let elapsed = last_emit_time.elapsed().as_secs_f64().max(0.001);
            let current_rate = (downloaded - last_emit_bytes) as f64 / elapsed;
            if current_rate > dl_limit as f64 {
                let sleep_ms = ((current_rate / dl_limit as f64 - 1.0) * elapsed * 1000.0) as u64;
                if sleep_ms > 0 && sleep_ms < 5000 {
                    tokio::time::sleep(std::time::Duration::from_millis(sleep_ms)).await;
                }
            }
        }
    }

    bw_state.add_down(total_size);

    // Explicitly flush, sync, and close the file before JNI/MediaStore copies it.
    tokio::io::AsyncWriteExt::flush(&mut file)
        .await
        .map_err(|e| format!("Failed to flush downloaded file: {}", e))?;
    file.sync_all()
        .await
        .map_err(|e| format!("Failed to sync downloaded file: {}", e))?;
    drop(file);

    let actual_written = tokio::fs::metadata(&actual_save_path)
        .await
        .map_err(|e| format!("Downloaded file missing before save: {}", e))?
        .len();
    if actual_written == 0 {
        cleanup_partial_file(&actual_save_path);
        return Err("Downloaded file was empty before saving".to_string());
    }
    if actual_written != downloaded {
        cleanup_partial_file(&actual_save_path);
        return Err(format!(
            "Downloaded file size mismatch before saving: streamed {} bytes, file has {} bytes",
            downloaded, actual_written
        ));
    }
    if let Some(expected) = expected_file_size {
        if expected > 0 && downloaded != expected {
            cleanup_partial_file(&actual_save_path);
            return Err(format!(
                "Incomplete download before saving: expected {} bytes, received {} bytes",
                expected, downloaded
            ));
        }
    }
    log::info!(
        "Download completed to cache path {} ({} bytes)",
        actual_save_path,
        actual_written
    );

    // Emit completion
    if !tid.is_empty() {
        if let Some(tx) = progress_tx.as_ref() { let _ = tx.send(ProgressPayload {
                id: tid,
                percent: 100,
                uploaded_bytes: downloaded,
                total_bytes: total_size,
                speed_bytes_per_sec: 0,
            }).await; }
    }


    Ok("Download successful".to_string())
}

pub async fn cmd_move_files(
    message_ids: Vec<i32>,
    source_folder_id: Option<i64>,
    target_folder_id: Option<i64>,
    state: &TelegramState,
) -> Result<bool, String> {
    if source_folder_id == target_folder_id {
        return Ok(true);
    }
    let client_opt = { state.client.lock().await.clone() };
    if client_opt.is_none() {
        log::info!(
            "[MOCK] Moved msgs {:?} from {:?} to {:?}",
            message_ids,
            source_folder_id,
            target_folder_id
        );
        return Ok(true);
    }
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;

    let source_peer = resolve_peer(&client, source_folder_id, &state.peer_cache).await?;
    let target_peer = resolve_peer(&client, target_folder_id, &state.peer_cache).await?;

    match client
        .forward_messages(&target_peer, &message_ids, &source_peer)
        .await
    {
        Ok(forwarded_msgs) => {
            if forwarded_msgs.is_empty() {
                return Err("Failed to forward any messages. Originals preserved.".to_string());
            }
            let forwarded_count = forwarded_msgs.len();
            let ids_to_delete: Vec<i32> = message_ids.into_iter().take(forwarded_count).collect();
            let _ = client.delete_messages(&source_peer, &ids_to_delete).await;
            Ok(true)
        }
        Err(e) => Err(format!("Forward failed: {}. Originals preserved.", e)),
    }
}

pub async fn cmd_get_files(
    folder_id: Option<i64>,
    offset_id: Option<i32>,
    limit: Option<usize>,
    state: &TelegramState,
) -> Result<Vec<FileMetadata>, String> {
    let client_opt = { state.client.lock().await.clone() };
    if client_opt.is_none() {
        log::info!("[MOCK] Returning mock files for folder {:?}", folder_id);
        return Ok(Vec::new()); // No mock files for now
    }
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;
    let mut files = Vec::new();

    let peer = resolve_peer(&client, folder_id, &state.peer_cache).await?;

    let mut msgs = client.iter_messages(&peer);
    if let Some(oid) = offset_id {
        msgs = msgs.offset_id(oid);
    }
    msgs = msgs.limit(limit.unwrap_or(100));

    while let Some(msg) = msgs.next().await.map_err(|e| e.to_string())? {
        if let Some(doc) = msg.media() {
            let (name, size, mime, ext) = match doc {
                Media::Document(d) => {
                    let n = d.name().to_string();
                    let s = d.size();
                    let m = d.mime_type().map(|s| s.to_string());
                    let e = std::path::Path::new(&n)
                        .extension()
                        .map(|os| os.to_str().unwrap_or("").to_string());
                    (n, s, m, e)
                }
                Media::Photo(_) => (
                    "Photo.jpg".to_string(),
                    0,
                    Some("image/jpeg".into()),
                    Some("jpg".into()),
                ),
                _ => ("Unknown".to_string(), 0, None, None),
            };
            files.push(FileMetadata {
                id: msg.id() as i64,
                folder_id,
                name,
                size: size as u64,
                mime_type: mime,
                file_ext: ext,
                created_at: msg.date().to_string(),
                icon_type: "file".into(),
            });
        }
    }

    Ok(files)
}

pub async fn cmd_search_global(
    query: String,
    state: &TelegramState,
) -> Result<Vec<FileMetadata>, String> {
    let client_opt = { state.client.lock().await.clone() };
    if client_opt.is_none() {
        return Ok(Vec::new());
    }
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;
    let mut files = Vec::new();

    log::info!("Searching global for: {}", query);

    let result = client
        .invoke(&tl::functions::messages::SearchGlobal {
            q: query,
            filter: tl::enums::MessagesFilter::InputMessagesFilterDocument,
            min_date: 0,
            max_date: 0,
            offset_rate: 0,
            offset_peer: tl::enums::InputPeer::Empty,
            offset_id: 0,
            limit: 50,
            folder_id: None,
            broadcasts_only: false,
            groups_only: false,
            users_only: false,
        })
        .await
        .map_err(map_error)?;

    if let tl::enums::messages::Messages::Messages(msgs) = result {
        for msg in msgs.messages {
            if let tl::enums::Message::Message(m) = msg {
                if let Some(tl::enums::MessageMedia::Document(d)) = m.media {
                    if let Some(tl::enums::Document::Document(doc)) = d.document {
                        let name = doc
                            .attributes
                            .iter()
                            .find_map(|a| match a {
                                tl::enums::DocumentAttribute::Filename(f) => {
                                    Some(f.file_name.clone())
                                }
                                _ => None,
                            })
                            .unwrap_or("Unknown".to_string());
                        let size = doc.size as u64;
                        let mime = doc.mime_type.clone();
                        let ext = std::path::Path::new(&name)
                            .extension()
                            .map(|os| os.to_str().unwrap_or("").to_string());
                        let folder_id = match m.peer_id {
                            tl::enums::Peer::Channel(c) => Some(c.channel_id),
                            tl::enums::Peer::User(u) => Some(u.user_id),
                            tl::enums::Peer::Chat(c) => Some(c.chat_id),
                        };
                        files.push(FileMetadata {
                            id: m.id as i64,
                            folder_id,
                            name,
                            size,
                            mime_type: Some(mime),
                            file_ext: ext,
                            created_at: m.date.to_string(),
                            icon_type: "file".into(),
                        });
                    }
                }
            }
        }
    } else if let tl::enums::messages::Messages::Slice(msgs) = result {
        for msg in msgs.messages {
            if let tl::enums::Message::Message(m) = msg {
                if let Some(tl::enums::MessageMedia::Document(d)) = m.media {
                    if let Some(tl::enums::Document::Document(doc)) = d.document {
                        let name = doc
                            .attributes
                            .iter()
                            .find_map(|a| match a {
                                tl::enums::DocumentAttribute::Filename(f) => {
                                    Some(f.file_name.clone())
                                }
                                _ => None,
                            })
                            .unwrap_or("Unknown".to_string());
                        let size = doc.size as u64;
                        let mime = doc.mime_type.clone();
                        let ext = std::path::Path::new(&name)
                            .extension()
                            .map(|os| os.to_str().unwrap_or("").to_string());
                        let folder_id = match m.peer_id {
                            tl::enums::Peer::Channel(c) => Some(c.channel_id),
                            tl::enums::Peer::User(u) => Some(u.user_id),
                            tl::enums::Peer::Chat(c) => Some(c.chat_id),
                        };
                        files.push(FileMetadata {
                            id: m.id as i64,
                            folder_id,
                            name,
                            size,
                            mime_type: Some(mime),
                            file_ext: ext,
                            created_at: m.date.to_string(),
                            icon_type: "file".into(),
                        });
                    }
                }
            }
        }
    }

    Ok(files)
}

pub async fn cmd_scan_folders(
    state: &TelegramState,
) -> Result<Vec<FolderMetadata>, String> {
    let client_opt = { state.client.lock().await.clone() };
    if client_opt.is_none() {
        return Ok(Vec::new());
    }
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;

    let mut folders = Vec::new();
    let mut dialogs = client.iter_dialogs();
    let mut discovered = HashMap::new();

    log::info!("Starting Folder Scan...");

    while let Some(dialog) = dialogs.next().await.map_err(|e| e.to_string())? {
        // Populate peer cache for every dialog we encounter (free priming)
        match &dialog.peer {
            Peer::Channel(c) => {
                let id = c.raw.id;
                discovered.insert(id, dialog.peer.clone());

                let name = c.raw.title.clone();
                let access_hash = c.raw.access_hash.unwrap_or(0);

                log::debug!("[SCAN] Processing Channel: '{}' (ID: {})", name, id);

                // Strategy 1: Title
                if name.to_lowercase().contains("[td]") {
                    log::info!(" -> MATCH via Title: {}", name);
                    let display_name = name
                        .replace(" [TD]", "")
                        .replace(" [td]", "")
                        .replace("[TD]", "")
                        .replace("[td]", "")
                        .trim()
                        .to_string();
                    let username = c.raw.username.clone();
                    let is_public = username.is_some();
                    folders.push(FolderMetadata {
                        id,
                        name: display_name,
                        parent_id: None,
                        username,
                        is_public,
                    });
                    continue;
                }

                // Strategy 2: About (Only if we are the creator to avoid rate limits on third-party channels)
                if c.raw.creator {
                    let input_chan = tl::enums::InputChannel::Channel(tl::types::InputChannel {
                        channel_id: c.raw.id,
                        access_hash,
                    });

                    match client
                        .invoke(&tl::functions::channels::GetFullChannel {
                            channel: input_chan,
                        })
                        .await
                    {
                        Ok(tl::enums::messages::ChatFull::Full(f)) => {
                            if let tl::enums::ChatFull::Full(cf) = f.full_chat {
                                if cf.about.contains("[telegram-drive-folder]") {
                                    log::info!(" -> MATCH via About: {}", name);
                                    let username = c.raw.username.clone();
                                    let is_public = username.is_some();
                                    folders.push(FolderMetadata {
                                        id,
                                        name: name.clone(),
                                        parent_id: None,
                                        username,
                                        is_public,
                                    });
                                }
                            }
                        }
                        Err(e) => log::warn!(" -> Failed to get full info: {}", e),
                    }
                }
            }
            Peer::User(u) => {
                discovered.insert(u.raw.id(), dialog.peer.clone());
                log::debug!("[SCAN] Cached User Peer: {}", u.raw.id());
            }
            peer => {
                log::debug!("[SCAN] Skipped Peer: {:?}", peer);
            }
        }
    }

    {
        let mut cache = state.peer_cache.write().await;
        cache.extend(discovered);
    }

    let cache_len = state.peer_cache.read().await.len();
    log::info!(
        "Scan complete. Found {} folders. Peer cache size: {}.",
        folders.len(),
        cache_len
    );
    Ok(folders)
}

/// Zip a folder's contents into a temp file and return the path.
/// The resulting zip preserves the relative directory structure.
pub async fn cmd_zip_folder(folder_path: String) -> Result<String, String> {
    let folder_path = if cfg!(target_os = "android") {
        clean_android_path(&folder_path)
    } else {
        folder_path
    };

    let src = std::path::Path::new(&folder_path)
        .canonicalize()
        .map_err(|e| format!("Invalid folder path: {}", e))?;
    if !src.is_dir() {
        return Err(format!("'{}' is not a directory", folder_path));
    }

    let folder_name = src
        .file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_else(|| "folder".to_string());

    let zip_path = std::env::temp_dir().join(format!("{}.zip", folder_name));
    let src_owned = src.clone();
    let out_path = zip_path.clone();

    // Run blocking I/O on a dedicated thread so we don't stall the async runtime
    let (zip_path_str, zip_size) = tokio::task::spawn_blocking(move || {
        let file = std::fs::File::create(&out_path)
            .map_err(|e| format!("Failed to create zip file: {}", e))?;
        let mut zip_writer = zip::ZipWriter::new(file);
        let options = zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated);

        for entry in walkdir::WalkDir::new(&src_owned)
            .into_iter()
            .filter_map(|e| e.ok())
        {
            let path = entry.path();
            let relative = path.strip_prefix(&src_owned).unwrap_or(path);

            if path.is_file() {
                let name = relative.to_string_lossy().to_string();
                zip_writer
                    .start_file(&name, options)
                    .map_err(|e| format!("Failed to add '{}': {}", name, e))?;
                let mut f = std::fs::File::open(path)
                    .map_err(|e| format!("Failed to open '{}': {}", name, e))?;
                std::io::copy(&mut f, &mut zip_writer)
                    .map_err(|e| format!("Failed to write '{}': {}", name, e))?;
            } else if path.is_dir() && path != src_owned {
                let dir_name = format!("{}/", relative.to_string_lossy());
                zip_writer
                    .add_directory(&dir_name, options)
                    .map_err(|e| format!("Failed to add dir '{}': {}", dir_name, e))?;
            }
        }

        zip_writer
            .finish()
            .map_err(|e| format!("Failed to finalize zip: {}", e))?;
        let size = std::fs::metadata(&out_path).map(|m| m.len()).unwrap_or(0);
        Ok::<(String, u64), String>((out_path.to_string_lossy().to_string(), size))
    })
    .await
    .map_err(|e| format!("Zip task panicked: {}", e))?
    .map_err(|e: String| e)?;

    log::info!(
        "Zipped '{}' -> '{}' ({} bytes)",
        folder_name,
        zip_path_str,
        zip_size
    );

    Ok(zip_path_str)
}

/// Delete a temporary zip file created by cmd_zip_folder.
pub async fn cmd_delete_temp_zip(path: String) -> Result<(), String> {
    let path_clone = path.clone();
    tokio::task::spawn_blocking(move || {
        let p = std::path::Path::new(&path_clone);
        if !p.exists() {
            return Ok(());
        }
        let canonical_p = p
            .canonicalize()
            .map_err(|e| format!("Invalid path: {}", e))?;
        let tmp = std::env::temp_dir()
            .canonicalize()
            .map_err(|e| format!("Could not resolve temp directory: {}", e))?;
        if !canonical_p.starts_with(&tmp) {
            return Err("Refusing to delete file outside temp directory".to_string());
        }
        std::fs::remove_file(&canonical_p).map_err(|e| e.to_string())?;
        log::info!("Cleaned up temp zip: {}", path_clone);
        Ok(())
    })
    .await
    .map_err(|e| format!("Task panicked: {}", e))?
}

/// Toggle a folder (channel) between private and public.
/// When making public, a username is generated from the channel title.
/// When making private, the username is removed.
pub async fn cmd_toggle_folder_visibility(
    folder_id: i64,
    make_public: bool,
    desired_username: Option<String>,
    state: &TelegramState,
) -> Result<FolderMetadata, String> {
    let client_opt = { state.client.lock().await.clone() };

    if client_opt.is_none() {
        log::info!(
            "[MOCK] Toggle visibility for folder {}. Public: {}",
            folder_id,
            make_public
        );
        return Ok(FolderMetadata {
            id: folder_id,
            name: "Mock Folder".to_string(),
            parent_id: None,
            username: if make_public { desired_username } else { None },
            is_public: make_public,
        });
    }
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;

    let peer = resolve_peer(&client, Some(folder_id), &state.peer_cache).await?;
    let (channel_id, access_hash) = match &peer {
        Peer::Channel(c) => (
            c.raw.id,
            c.raw.access_hash.ok_or("No access hash for channel")?,
        ),
        _ => return Err("Only channels (folders) can be toggled.".to_string()),
    };

    let input_channel = tl::enums::InputChannel::Channel(tl::types::InputChannel {
        channel_id,
        access_hash,
    });

    // Extract channel name from the resolved peer for the return value
    let channel_name = match &peer {
        Peer::Channel(c) => c
            .raw
            .title
            .replace(" [TD]", "")
            .replace(" [td]", "")
            .trim()
            .to_string(),
        _ => "Folder".to_string(),
    };

    if make_public {
        // Generate a username from the desired_username or channel title.
        // If desired_username is provided AND non-empty, use it directly;
        // otherwise auto-generate from the channel title.
        let username = if let Some(ref u) = desired_username {
            if !u.is_empty() {
                Some(u.clone())
            } else {
                None // empty string → fall through to auto-generation below
            }
        } else {
            None
        };

        let username = match username {
            Some(given) => {
                // User-provided username: check availability first
                let available = client
                    .invoke(&tl::functions::channels::CheckUsername {
                        channel: tl::enums::InputChannel::Channel(tl::types::InputChannel {
                            channel_id,
                            access_hash,
                        }),
                        username: given.clone(),
                    })
                    .await
                    .map_err(|e| {
                        format!("Failed to check username availability: {}", map_error(e))
                    })?;
                if !available {
                    return Err(format!(
                        "Username '{}' is not available. Try a different one.",
                        given
                    ));
                }
                given
            }
            None => {
                // Auto-generate username from channel title
                // channel_name already has [TD] stripped above
                let mut base = channel_name
                    .clone()
                    .to_lowercase()
                    .chars()
                    .filter(|c| c.is_alphanumeric() || *c == '_')
                    .take(30)
                    .collect::<String>();
                if base.len() < 5 {
                    let suffix: String = (0..6)
                        .map(|_| char::from(b'a' + (rand::random::<u8>() % 26)))
                        .collect();
                    base = format!("{}_{}", base, suffix);
                }
                // Try to find an available username
                let mut candidate = base.clone();
                for attempt in 1..=10 {
                    match client
                        .invoke(&tl::functions::channels::CheckUsername {
                            channel: tl::enums::InputChannel::Channel(tl::types::InputChannel {
                                channel_id,
                                access_hash,
                            }),
                            username: candidate.clone(),
                        })
                        .await
                    {
                        Ok(true) => break,
                        _ => {
                            candidate = format!("{}{}", base, attempt);
                            if attempt == 10 {
                                return Err(
                                    "Could not find an available username after 10 attempts"
                                        .to_string(),
                                );
                            }
                        }
                    }
                }
                candidate
            }
        };

        log::info!("Setting channel {} username to '{}'", channel_id, username);
        client
            .invoke(&tl::functions::channels::UpdateUsername {
                channel: input_channel,
                username: username.clone(),
            })
            .await
            .map_err(|e| format!("Failed to set username: {}", map_error(e)))?;

        Ok(FolderMetadata {
            id: channel_id,
            name: channel_name,
            parent_id: None,
            username: Some(username),
            is_public: true,
        })
    } else {
        // Make private: remove username
        log::info!("Removing username from channel {}", channel_id);
        client
            .invoke(&tl::functions::channels::UpdateUsername {
                channel: input_channel,
                username: String::new(),
            })
            .await
            .map_err(|e| format!("Failed to remove username: {}", map_error(e)))?;

        Ok(FolderMetadata {
            id: channel_id,
            name: channel_name,
            parent_id: None,
            username: None,
            is_public: false,
        })
    }
}

/// Export a Telegram invite link for a folder (channel).
/// For public channels, returns the t.me/username link directly.
/// For private channels, exports a hash-based invite link via the API.
#[derive(Debug, Serialize)]
pub struct FolderInviteInfo {
    pub link: String,
    pub is_public: bool,
    pub username: Option<String>,
}

pub async fn cmd_export_folder_invite(
    folder_id: i64,
    state: &TelegramState,
) -> Result<FolderInviteInfo, String> {
    let client_opt = { state.client.lock().await.clone() };

    if client_opt.is_none() {
        log::info!("[MOCK] Export invite for folder {}", folder_id);
        return Ok(FolderInviteInfo {
            link: "https://t.me/joinchat/mock-invite-hash".to_string(),
            is_public: false,
            username: None,
        });
    }
    let client = client_opt.ok_or_else(|| "Client not connected".to_string())?;

    let peer = resolve_peer(&client, Some(folder_id), &state.peer_cache).await?;
    let (channel_id, access_hash) = match &peer {
        Peer::Channel(c) => (
            c.raw.id,
            c.raw.access_hash.ok_or("No access hash for channel")?,
        ),
        _ => return Err("Only channels (folders) can have invite links.".to_string()),
    };

    // Check if channel already has a public username (use the resolved peer directly)
    let username: Option<String> = match &peer {
        Peer::Channel(c) => c.raw.username.clone(),
        _ => None,
    };

    if let Some(ref uname) = username {
        // Public channel: return the t.me/username link
        Ok(FolderInviteInfo {
            link: format!("https://t.me/{}", uname),
            is_public: true,
            username: Some(uname.clone()),
        })
    } else {
        // Private channel: export an invite link
        let result = client
            .invoke(&tl::functions::messages::ExportChatInvite {
                peer: tl::enums::InputPeer::Channel(tl::types::InputPeerChannel {
                    channel_id,
                    access_hash,
                }),
                legacy_revoke_permanent: false,
                request_needed: false,
                expire_date: None,
                usage_limit: None,
                title: None,
                subscription_pricing: None,
            })
            .await
            .map_err(|e| format!("Failed to export invite: {}", map_error(e)))?;

        let link = match result {
            tl::enums::ExportedChatInvite::ChatInviteExported(c) => c.link,
            tl::enums::ExportedChatInvite::ChatInvitePublicJoinRequests => {
                return Err("Public join request channels do not have a custom private invite link. Share the public username directly instead.".to_string());
            }
        };

        Ok(FolderInviteInfo {
            link,
            is_public: false,
            username: None,
        })
    }
}
