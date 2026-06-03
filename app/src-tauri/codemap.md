# Codemap: Tauri Project Root

**Responsibility**: 
This is the root configuration and packaging directory for the Tauri backend. It defines the Rust project, its dependencies, the Tauri application configuration, security capabilities, and build scripts required to compile the desktop application.

**Design Patterns**: 
- **Standard Cargo Workspace**: Standard Rust project structure (`Cargo.toml`, `Cargo.lock`, `build.rs`).
- **Configuration-Driven Application**: Uses `tauri.conf.json` to define application metadata, window settings, and build parameters.
- **Security Enclaves**: Uses the `capabilities/` directory to manage IPC permissions, restricting frontend access to only permitted backend commands.

**Data & Control Flow**: 
- The build process is driven by Tauri CLI (`tauri build` / `tauri dev`), which reads `tauri.conf.json` and invokes `cargo`.
- The runtime application starts here (via `src/main.rs`), binding the compiled Rust binary to the Webview window.

**Integration Points**: 
- Bridges the frontend application (typically in `app/`) with the Rust backend (`src/`).
- Manages build-time integrations via `build.rs`.
- Contains `tests/` for backend integration testing.