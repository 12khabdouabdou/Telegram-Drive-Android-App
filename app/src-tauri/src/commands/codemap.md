# Codemap: Tauri Commands

**Responsibility**: 
This directory contains Tauri IPC (Inter-Process Communication) command handlers. It acts as the bridge exposing Rust backend functionality to the frontend UI, defining operations like authentication, file system interactions, network settings, and previews.

**Design Patterns**: 
- **Controller/Facade Pattern**: Each module acts as a controller, wrapping core application logic and exposing it via `#[tauri::command]` macros.
- **State Injection**: Relies on Tauri's dependency injection (`tauri::State`) to access shared application state like `TelegramState`.

**Data & Control Flow**: 
- **Input**: Commands receive serialized arguments from the frontend.
- **Process**: Validates inputs, locks necessary state (e.g., `Mutex`, `RwLock`), calls underlying core services (Telegram API, Database).
- **Output**: Returns data as `Result<T, E>` which is serialized to JSON and sent back to the frontend.

**Integration Points**: 
- Consumed directly by the frontend via `invoke(...)`.
- Depends heavily on the shared models and state defined in `app/src-tauri/src/` (e.g., `TelegramState`).
- Integrates with external libraries (`grammers_client`) and local storage (`db.rs`).