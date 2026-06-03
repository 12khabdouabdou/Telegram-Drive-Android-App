# Codemap: `app/src/context/`

**Responsibility**: 
Provides global React context providers for the application's UI state, user settings, and generic UI utilities (like modals and theming).

**Design Patterns**:
- **Context API & Hooks**: Exposes standard React context providers (`ConfirmProvider`, `SettingsProvider`, `ThemeProvider`) and custom consumer hooks (`useConfirm`, `useSettings`, `useTheme`).
- **Tauri Store Integration**: `SettingsContext` persists data via `@tauri-apps/plugin-store`.

**Data & Control Flow**:
- Providers wrap the main application. State is updated via context setter functions.
- `SettingsContext` reads/writes to local storage and syncs proxy/VPN configurations down to hooks or API layers.
- `ConfirmContext` triggers modal UI overlays synchronously awaiting user input via Promises.

**Integration Points**:
- Consumed by `app/src/App.tsx` and nested components to render correct views and behavior.
- Interfaces with Tauri OS plugins for persistence.
