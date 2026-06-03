# Codemap: Core Rust Backend

**Responsibility**: 
The core Rust backend for the Telegram-Drive application. It manages the Telegram MTProto client connection, local SQLite database operations, local HTTP servers (for API, streaming, and sharing), and background services like auto-backup and VPN optimization.

**Design Patterns**: 
- **Service Layer**: Organizes business logic into distinct services (`upload_service.rs`, `auto_backup.rs`, `vpn_optimizer.rs`).
- **Concurrent State Management**: Heavy use of thread-safe synchronization (`Arc<Mutex<T>>`, `RwLock`) to share state across commands and HTTP servers.
- **REST/HTTP Server**: Uses web frameworks (like Axum or Actix) for serving local API routes, share links, and media streaming endpoints.

**Data & Control Flow**: 
- **Initialization**: `lib.rs`/`main.rs` bootstrap the application, set up state structures, start local servers, and register Tauri commands.
- **Data Flow**: Core logic orchestrates data between the Telegram API, local disk/SQLite, and the Tauri application window. The HTTP servers process external or local web requests and route them to Telegram client functions.

**Integration Points**: 
- Exposes functionality to the frontend via the `commands/` module.
- Interacts with local databases via `db.rs` and the filesystem.
- Connects to the Telegram network using the `grammers` crate.
- Provides HTTP endpoints via `server.rs`, `api_routes.rs`, and `share_routes.rs`.