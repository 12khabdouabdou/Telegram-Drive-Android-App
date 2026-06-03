# Codemap: `app/`

**Responsibility**: 
The main desktop application wrapper, containing both the Vite/React frontend and the Tauri/Rust backend configuration.

**Design Patterns**:
- **Tauri Application Architecture**: 
  - `src/` (Frontend): Web technologies (HTML, React, Vite) responsible for the GUI.
  - `src-tauri/` (Backend): Rust environment that handles the heavy lifting, local system access, and core Telegram MTProto protocol logic.

**Data & Control Flow**:
- Vite builds the frontend.
- Tauri CLI compiles the Rust backend and embeds the built frontend.
- Runtime communication happens over Tauri's IPC bridge using asynchronous command invocation and event listening.

**Integration Points**:
- `package.json` configures Node/Vite scripts and dependencies.
- `vite.config.ts` handles the frontend build pipeline.
- Integrates deeply with the underlying OS through Tauri plugins.
