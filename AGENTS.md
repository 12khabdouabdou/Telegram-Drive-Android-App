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
- The `app/src-tauri/gen/android/` directory is explicitly ignored in `.gitignore` by default. 
- **Native Android Code (Kotlin):** Custom Kotlin files (e.g., `AutoBackupService.kt`, `MainActivity.kt`) are tracked forcefully. If you add new native Android code, you must `git add -f` those files.
- **AndroidManifest.xml:** The manifest is NOT tracked locally. It is generated dynamically and patched on-the-fly during CI by `sed` commands in `.github/workflows/android.yml`. If you need to add Android permissions or Intent Filters, add the `sed` patches to `android.yml`.
- **Zero-Copy Uploads:** The frontend passes `content://` URIs. Rust converts these to `fd://` using JNI `ParcelFileDescriptor` to upload them without caching to disk. *Security Note:* The frontend is strictly forbidden from passing `fd://` directly to prevent arbitrary file descriptor reading.

## Rust Backend & JNI Quirks
- **JNI Exceptions:** When writing JNI logic, do not use the `?` operator immediately after a JNI call if it might throw a Java exception. You MUST call `env.exception_clear()` before returning `Err` across the FFI boundary, otherwise the entire JVM will crash on the next call.
- **Background Processes:** If Android wakes the app via an Intent or Background Service while the UI is closed, React is not loaded. Rust must cache events (e.g., `share_intent.rs` caches shared files) and React must manually pull them (`cmd_get_pending_shared_files`) on mount.
- **Linux:** `main.rs` forcibly injects `WEBKIT_DISABLE_DMABUF_RENDERER=1` to fix `EGL_BAD_ALLOC` issues on Arch/AppImages.
- **Windows:** `lib.rs` executes custom COM MTA initialization logic on background threads to prevent OLE/RPC errors during async operations.

## React Frontend Quirks
- **Tauri Store I/O Spam:** When listening to `upload-progress` or `download-progress`, do NOT put the raw `queue` array in the dependency list for `store.save()` effects. Progress ticks 4 times a second; saving to disk on every tick causes extreme I/O locking. Filter the dependency array to only trigger when an item's `id` or `status` changes.
- **Routing:** The app uses state-based routing (`activeTab`, `authStatus`) rather than a formal router.
- **Virtualization:** `TouchFileList.tsx` relies on `@tanstack/react-virtual`. The scroll container is hardcoded with `id="mobile-scroll-container"` in `MobileDashboard.tsx`.

## Testing
- There are currently no automated tests configured for either the frontend or backend.