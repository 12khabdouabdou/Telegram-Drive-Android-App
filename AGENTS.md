# Telegram-Drive Agent Instructions

This file contains crucial context for AI agents working on this codebase. Read this before starting tasks.

## Repository Architecture
- **Framework:** Tauri v2 application (cross-platform desktop & Android).
- **Frontend:** React, TypeScript, TailwindCSS, Vite (located in `app/`).
- **Backend:** Rust, `grammers` (Telegram API client), Tokio, Actix, SQLite (located in `app/src-tauri/`).

## Development Commands
- **Install:** `cd app && npm install`
- **Desktop Dev:** `cd app && npm run tauri dev`
- **Desktop Build:** `cd app && npm run tauri build`
- **Android Dev/Build:** 
  - *Must run first:* `cd app && npm run tauri android init`
  - *Then build:* `cd app && npm run tauri android build`

## Critical Android Quirks
- The `app/src-tauri/gen/android/` directory is explicitly ignored in `.gitignore`. 
- **Native Android Code (Java/Kotlin):** The Rust backend uses JNI (e.g., `upload_service.rs` references `UploadForegroundService`), but the corresponding Java files are missing from the repository because the entire `gen/android` folder is gitignored.
- **When adding Android Native Features (e.g., Auto-Backup, Foreground Services, WorkManager):**
  1. Run `npm run tauri android init` to generate the Android Studio project.
  2. Write your Java/Kotlin files in `app/src-tauri/gen/android/app/src/main/java/com/cameronamer/telegramdrive/`.
  3. **IMPORTANT:** You must modify `.gitignore` to explicitly track your new `.java` or `.kt` files (e.g., `!app/src-tauri/gen/android/app/src/main/java/**/*.java`), or they will be silently dropped in the next commit.

## Backend & Systems Quirks
- **Telegram Client:** Driven by the `grammers-client` crate in the Rust backend.
- **Linux:** `main.rs` forcibly injects `WEBKIT_DISABLE_DMABUF_RENDERER=1` to fix `EGL_BAD_ALLOC` issues on Arch/AppImages.
- **Windows:** `lib.rs` executes custom COM MTA initialization logic on background threads to prevent OLE/RPC errors during async operations.
- **Testing:** There are currently no automated tests configured for either the frontend or backend.