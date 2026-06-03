#[derive(Debug, serde::Deserialize)]
pub struct ProxySettingsRequest {
    pub enabled: bool,
    pub proxy_type: String,
    pub host: String,
    pub port: u16,
    pub username: String,
    pub password: String,
    pub secret: String,
}

#[derive(Debug, serde::Deserialize)]
pub struct VpnSettingsRequest {
    pub enabled: bool,
    pub timeout_multiplier: u32,
    pub retry_attempts: u32,
    pub retry_base_backoff_ms: u64,
    pub retry_max_backoff_ms: u64,
    pub adaptive_polling: bool,
    pub polling_min_sec: u32,
    pub polling_max_sec: u32,
    pub preferred_dc: String,
    pub dc_fallback_attempts: u32,
    pub flood_wait_respect: bool,
    pub peer_cache_size: usize,
    pub bandwidth_limit_up_kbs: u32,
    pub bandwidth_limit_down_kbs: u32,
    pub chunk_size_kb: u32,
    pub keep_alive_interval_sec: u32,
    pub auto_detect_vpn: bool,
}

// Note: To remain independent of `vpn_optimizer` types (if they are not moved yet), 
// this file extracts the parameter clamping and formatting logic. 
// Alternatively, if `vpn_optimizer` types are accessible, they could be constructed here.

pub fn validate_proxy_settings(req: ProxySettingsRequest) -> ProxySettingsRequest {
    // Basic validation could be placed here
    req
}

pub fn validate_vpn_settings(req: VpnSettingsRequest) -> VpnSettingsRequest {
    VpnSettingsRequest {
        enabled: req.enabled,
        timeout_multiplier: req.timeout_multiplier.clamp(1, 5),
        retry_attempts: req.retry_attempts.clamp(0, 5),
        retry_base_backoff_ms: req.retry_base_backoff_ms.clamp(500, 5000),
        retry_max_backoff_ms: req.retry_max_backoff_ms.clamp(8000, 60000),
        adaptive_polling: req.adaptive_polling,
        polling_min_sec: req.polling_min_sec.clamp(10, 30),
        polling_max_sec: req.polling_max_sec.clamp(45, 120),
        preferred_dc: req.preferred_dc,
        dc_fallback_attempts: req.dc_fallback_attempts.clamp(1, 4),
        flood_wait_respect: req.flood_wait_respect,
        peer_cache_size: req.peer_cache_size.clamp(100, 2000),
        bandwidth_limit_up_kbs: req.bandwidth_limit_up_kbs,
        bandwidth_limit_down_kbs: req.bandwidth_limit_down_kbs,
        chunk_size_kb: req.chunk_size_kb.clamp(64, 512),
        keep_alive_interval_sec: if req.keep_alive_interval_sec == 0 {
            0
        } else {
            req.keep_alive_interval_sec.clamp(30, 120)
        },
        auto_detect_vpn: req.auto_detect_vpn,
    }
}
