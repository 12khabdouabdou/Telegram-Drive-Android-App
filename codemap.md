# Repository Atlas: Telegram-Drive

## Project Responsibility
Telegram-Drive is an open-source, cross-platform application (Desktop & Android) built with Tauri v2, Rust, and React. It turns a Telegram account into an unlimited, secure cloud storage drive by leveraging Telegram channels for file storage, and provides high-performance file uploading/downloading with local caching, auto-backup, and sharing capabilities.

## System Entry Points
- `app/src/main.tsx`: React frontend initialization and routing to desktop/mobile views.
- `app/src-tauri/src/lib.rs`: Rust backend initialization, database setup, and Actix server spawning.
- `app/src-tauri/gen/android/app/src/main/java/com/cameronamer/telegramdrive/MainActivity.kt`: Android native entry point handling JNI and share intents.
- `app/package.json` & `app/src-tauri/Cargo.toml`: Dependency manifests for frontend and backend.

## Directory Map (Aggregated)
| Directory | Responsibility Summary | Detailed Map |
|-----------|------------------------|--------------|
| `app/` | Wraps the frontend and backend of the Tauri application. | [View Map](app/codemap.md) |
| `app/src/` | SPA React frontend entry, routing, and global stylesheets. | [View Map](app/src/codemap.md) |
| `app/src/components/` | Top-level architecture for platform separation (desktop/mobile/shared). | [View Map](app/src/components/codemap.md) |
| `app/src/hooks/` | Custom business logic, Tauri IPC calls, and event listeners. | [View Map](app/src/hooks/codemap.md) |
| `app/src/context/` | React Context providers for global state (Settings, Theme, Confirm). | [View Map](app/src/context/codemap.md) |
| `app/src-tauri/` | Tauri workspace configuration, capabilities, and native integrations. | [View Map](app/src-tauri/codemap.md) |
| `app/src-tauri/src/` | Core Rust backend services, SQLite DB operations, and Actix web servers. | [View Map](app/src-tauri/src/codemap.md) |
| `app/src-tauri/src/commands/` | Tauri IPC command handlers acting as the controller layer. | [View Map](app/src-tauri/src/commands/codemap.md) |
