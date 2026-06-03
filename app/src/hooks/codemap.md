# Codemap: `app/src/hooks/`

**Responsibility**: 
Encapsulates business logic, API communication, Tauri integration, and complex UI behaviors into reusable React hooks.

**Design Patterns**:
- **Custom Hooks**: Functional logic extraction from components.
- **State Management**: Uses local state/refs combined with async Tauri Rust commands (`invoke`).
- **Event Listeners**: Sets up and tears down Tauri event listeners (e.g., download/upload progress).

**Data & Control Flow**:
- Components call hooks (e.g., `useFileOperations`, `useFileUpload`, `useTelegramConnection`).
- Hooks invoke Rust backend via Tauri IPC (`@tauri-apps/api/core`).
- Hooks manage loading/error states and progress indicators and return them to the UI.
- Hooks read from `SettingsContext` to adjust behavior (e.g., proxy/VPN settings).

**Integration Points**:
- Acts as the primary bridge between React UI (`app/src/components/`) and the Rust backend (`app/src-tauri/`).
- Consumes global settings from `app/src/context/`.
