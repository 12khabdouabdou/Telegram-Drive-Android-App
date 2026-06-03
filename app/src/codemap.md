# Codemap: `app/src/`

**Responsibility**: 
The root directory of the React frontend application. It orchestrates the entire UI layer of the Telegram-Drive client.

**Design Patterns**:
- **Single Page Application (SPA)**: Built with React, Vite, and TypeScript.
- **Component-Based Architecture**: Separation of concerns across `components/`, `hooks/`, and `context/`.

**Data & Control Flow**:
- Entry point: `main.tsx` mounts the React tree.
- `App.tsx` sets up layout, routing (if any), and core providers.
- Data flows top-down via Contexts and bottom-up via Tauri IPC in hooks.

**Integration Points**:
- Interfaces with Tauri via `@tauri-apps/api` for native OS capabilities.
- Styles are handled via Tailwind CSS (`App.css`).
